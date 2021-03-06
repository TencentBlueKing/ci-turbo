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
            if (turboPublicPath.isNullOrBlank()) {
                logger.error("[turbo plugin] ????????????public?????????!")
                throw TurboException(errorMsg = "?????????BK_TURBO_PUBLIC_URL????????????!")
            }
            if (turboPrivatePath.isNullOrBlank()) {
                logger.error("[turbo plugin] ????????????private?????????!")
                throw TurboException(errorMsg = "?????????BK_TURBO_PRIVATE_URL????????????!")
            }
            val turboParam = turboParamContext.param
            if (turboParam.turboPlanId.isNullOrBlank()) {
                logger.error("[turbo plugin] ??????????????????id?????????!")
                throw TurboException(errorMsg = "invalid input param")
            }
            // ??????turbo????????????????????????????????????????????????id
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
                logger.error("[turbo plugin] ????????????id??????! message: ${buildIdResponse.message}")
                throw TurboException(errorMsg = "get build id fail!")
            }
            logger.info("[turbo plugin] response build id: $buildId")
            if (buildId.isNullOrBlank()) {
                logger.error("[turbo plugin] ??????id??????!")
                throw TurboException(errorMsg = "build id is null!")
            }
            val workspace = File(turboParam.bkWorkspace)
            val scriptList = mutableListOf<String>()

            // ??????????????????????????????
            val turboPlanResponseStr = TurboSdkApi.getTurboPlanDetailById(turboParam.turboPlanId!!)
            val turboPlanResponse = JsonUtil.to(turboPlanResponseStr, object : TypeReference<Response<TurboPlanDetailModel>>() {})
            val turboPlanModel = if (turboPlanResponse.isOk())
                turboPlanResponse.data ?: throw TurboException(errorMsg = "get turbo plan fail!")
            else {
                logger.error("[turbo plugin] ??????????????????????????????! message: ${turboPlanResponse.message}")
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
            //???????????????
            scriptGenerator.installClient()
            //??????????????????
            scriptGenerator.preProcessScript(scriptList)

            logger.info("///////////////////////execute turbo script//////////////////////")
            val result = ScriptUtils.execute(
                    script = scriptList.joinToString(" "),
                    dir = workspace,
                    prefix = "[turbo client] ",
                    printLog = true,
                    failExit = true
            )

            // ??????????????????????????????????????????????????????????????????
            turboParamContext.result.data.putAll(getOutputVariabled(result))
            logger.info("[turbo plugin] turbo compile finish, time cost ${(System.currentTimeMillis() - startTime).div(1000)} seconds")
            logger.info("=====================================================")
        } catch (e: TurboException) {
            logger.error("[turbo plugin] ????????????, ????????????: ${e.errorMsg}")
            logger.info("[turbo plugin] turbo compile failed")
            turboParamContext.result.errorType = ErrorType.USER.num
            turboParamContext.result.message = e.errorMsg
            turboParamContext.result.status = Status.error
        } catch (t: Throwable) {
            logger.error("[turbo plugin] ??????????????????, ????????????: ${t.message}")
            logger.info("[turbo plugin] turbo compile failed")
            turboParamContext.result.errorType = ErrorType.USER.num
            turboParamContext.result.message = t.message
            turboParamContext.result.status = Status.error
        } finally {
            logger.info("///////////////////////complete turbo script//////////////////////")
        }
    }


    /**
     * ??????????????????
     */
    private fun getOutputVariabled(result: String): Map<String, DataField> {
        val resultMap = mutableMapOf<String, DataField>()
        Regex(turboOutputRegex).findAll(result).forEach {
            resultMap[it.groupValues[2]] = StringData(it.groupValues[3])
        }
        return resultMap
    }

    /**
     * ?????????????????????????????????
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
