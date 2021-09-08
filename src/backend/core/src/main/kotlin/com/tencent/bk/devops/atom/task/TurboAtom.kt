package com.tencent.bk.devops.atom.task

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.bk.devops.atom.AtomContext
import com.tencent.bk.devops.atom.common.Status
import com.tencent.bk.devops.atom.pojo.DataField
import com.tencent.bk.devops.atom.pojo.StringData
import com.tencent.bk.devops.atom.spi.AtomService
import com.tencent.bk.devops.atom.spi.TaskAtom
import com.tencent.bk.devops.atom.task.api.TurboSdkApi
import com.tencent.bk.devops.atom.task.exception.TurboException
import com.tencent.bk.devops.atom.task.pojo.Response
import com.tencent.bk.devops.atom.task.pojo.TurboParam
import com.tencent.bk.devops.atom.task.pojo.TurboPlanDetailModel
import com.tencent.bk.devops.atom.task.script.AbstractScriptGenerator
import com.tencent.bk.devops.atom.task.script.LinuxScriptGenerator
import com.tencent.bk.devops.atom.task.script.WindowsScriptGenerator
import com.tencent.bk.devops.atom.utils.http.SdkUtils
import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.bk.devops.plugin.script.ScriptUtils
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.bk.devops.plugin.utils.MachineEnvUtils
import org.slf4j.LoggerFactory
import java.io.File

@AtomService(paramClass = TurboParam::class)
class TurboAtom : TaskAtom<TurboParam> {

    companion object {
        private const val turboOutputRegex ="""(\s+|\n|^)::set-output name=(\S+?)::(\S+?)(\s+|\n|$)"""
        private val logger = LoggerFactory.getLogger(TurboAtom::class.java)
    }

    override fun execute(turboParamContext: AtomContext<TurboParam>) {
        try {
            logger.info("=====================================================")
            logger.info("[turbo plugin] turbo compile start")
            val startTime = System.currentTimeMillis()
            val turboPublicPath = turboParamContext.getSensitiveConfParam("BK_TURBO_PUBLIC_URL")
                        ?.replace("http://", "")?.replace("https://", "")
            val turboPrivatePath = turboParamContext.getSensitiveConfParam("BK_TURBO_PRIVATE_URL")
            ?.replace("http://", "")?.replace("https://", "")
            if (turboPublicPath.isNullOrBlank() || turboPrivatePath.isNullOrBlank()) {
                logger.error("[turbo plugin] 没有配置对应的根路径!")
                throw TurboException(errorMsg = "no turbo public path or turbo private path set!")
            }
            val turboParam = turboParamContext.param
            if (turboParam.turboPlanId.isNullOrBlank()) {
                logger.error("[turbo plugin] 编译加速方案id未选择!")
                throw TurboException(errorMsg = "invalid input param")
            }
            // 调用turbo接口，用于生成编译加速实例和构建id
            val buildIdResponseStr = TurboSdkApi.createTurboPlanInstance(
                    projectId = turboParam.projectName,
                    turboPlanId = turboParam.turboPlanId!!,
                    pipelineId = turboParam.pipelineId,
                    pipelineElementId = turboParam.pipelineTaskId,
                    pipelineName = turboParam.pipelineName,
                    userId = turboParam.pipelineStartUserName,
                    buildId = turboParam.pipelineBuildId
            )
            val buildIdResponse = JsonUtil.to(buildIdResponseStr, object : TypeReference<Response<String>>() {})
            val buildId = if (buildIdResponse.isOk()) buildIdResponse.data else {
                logger.error("[turbo plugin] 获取构建id失败! message: ${buildIdResponse.message}")
                throw TurboException(errorMsg = "get build id fail!")
            }
            logger.info("[turbo plugin] response build id: $buildId")
            if (buildId.isNullOrBlank()) {
                logger.error("[turbo plugin] 构建id为空!")
                throw TurboException(errorMsg = "build id is null!")
            }
            val workspace = File(turboParam.bkWorkspace)
            val scriptList = mutableListOf<String>()

            // 获取编译加速方案信息
            val turboPlanResponseStr = TurboSdkApi.getTurboPlanDetailById(turboParam.turboPlanId!!)
            val turboPlanResponse = JsonUtil.to(turboPlanResponseStr, object : TypeReference<Response<TurboPlanDetailModel>>() {})
            val turboPlanModel = if (turboPlanResponse.isOk())
                turboPlanResponse.data ?: throw TurboException(errorMsg = "get turbo plan fail!")
            else {
                logger.error("[turbo plugin] 获取编译加速方案失败! message: ${turboPlanResponse.message}")
                throw TurboException(errorMsg = "get turbo plan fail!")
            }
            val engineCode = turboPlanModel.engineCode.replace("disttask-", "")
            val scriptGenerator = MachineEnvUtils.getScriptGenerator(
                engineCode = engineCode,
                turboParam = turboParam,
                buildId = buildId,
                turboPublicPath = turboPublicPath,
                turboPrivatePath = turboPrivatePath
            )
            scriptGenerator.preProcessScript(scriptList)

            logger.info("///////////////////////execute turbo script//////////////////////")
            val result = ScriptUtils.execute(
                    script = scriptList.joinToString(" "),
                    dir = workspace,
                    prefix = "[turbo client] ",
                    printLog = true,
                    failExit = true
            )

            // 判断脚本输出流，如果有对应的正则，则输出变量
            turboParamContext.result.data.putAll(getOutputVariabled(result))
            logger.info("[turbo plugin] turbo compile finish, time cost ${(System.currentTimeMillis() - startTime).div(1000)} seconds")
            logger.info("=====================================================")
        } catch (e: TurboException) {
            logger.error("[turbo plugin] 业务错误, 错误信息: ${e.errorMsg}")
            logger.info("[turbo plugin] turbo compile failed")
            turboParamContext.result.errorType = ErrorType.USER.num
            turboParamContext.result.message = e.errorMsg
            turboParamContext.result.status = Status.error
        } catch (t: Throwable) {
            logger.error("[turbo plugin] 脚本执行报错, 错误信息: ${t.message}")
            logger.info("[turbo plugin] turbo compile failed")
            turboParamContext.result.errorType = ErrorType.USER.num
            turboParamContext.result.message = t.message
            turboParamContext.result.status = Status.error
        } finally {
            logger.info("///////////////////////complete turbo script//////////////////////")
        }
    }


    /**
     * 获取输出变量
     */
    private fun getOutputVariabled(result: String): Map<String, DataField> {
        val resultMap = mutableMapOf<String, DataField>()
        Regex(turboOutputRegex).findAll(result).forEach {
            resultMap[it.groupValues[2]] = StringData(it.groupValues[3])
        }
        return resultMap
    }

    /**
     * 获取脚本生成器扩展方法
     */
    private fun MachineEnvUtils.getScriptGenerator(
        engineCode: String,
        turboParam: TurboParam,
        buildId: String,
        turboPublicPath: String,
        turboPrivatePath: String
    ): AbstractScriptGenerator {
        return if (getOS() == MachineEnvUtils.OSType.WINDOWS) {
            WindowsScriptGenerator(
                engineCode = engineCode,
                turboParam = turboParam,
                buildId = buildId
            )
        } else {
            LinuxScriptGenerator(
                engineCode = engineCode,
                turboParam = turboParam,
                buildId = buildId,
                turboPublicPath = turboPublicPath,
                turboPrivatePath = turboPrivatePath
            )
        }
    }
}
