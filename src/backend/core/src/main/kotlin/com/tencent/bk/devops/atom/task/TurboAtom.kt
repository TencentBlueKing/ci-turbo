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
import com.tencent.bk.devops.atom.task.utils.AgentEnv
import com.tencent.bk.devops.atom.utils.http.SdkUtils
import com.tencent.bk.devops.plugin.pojo.ErrorType
import com.tencent.bk.devops.plugin.script.ScriptUtils
import com.tencent.bk.devops.plugin.utils.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File

@AtomService(paramClass = TurboParam::class)
class TurboAtom : TaskAtom<TurboParam>{

    companion object{
        private const val turboPlanIdEnv = "TURBO_PLAN_ID"
        private const val turboPlanBuildIdEnv = "TURBO_PLAN_BUILD_ID"
        private const val turboOutputRegex ="""(\s+|\n|^)::set-output name=(\S+?)::(\S+?)(\s+|\n|$)"""
        private val logger = LoggerFactory.getLogger(TurboAtom::class.java)
    }

    override fun execute(turboParamContext: AtomContext<TurboParam>) {
        try{
            logger.info("=====================================================")
            logger.info("[turbo plugin] turbo compile start")
            val startTime = System.currentTimeMillis()
            //先取插件的私有配置项，用于配置api调用根路径
            val turboRootPath = turboParamContext.getSensitiveConfParam("BK_CI_PUBLIC_URL")
            if(turboRootPath.isNullOrBlank()){
                logger.error("[turbo plugin] 没有配置对应的根路径!")
                throw TurboException(errorMsg = "no turbo root path set!")
            }
            TurboSdkApi.init(turboRootPath)
            val turboParam = turboParamContext.param
            if(turboParam.turboPlanId.isNullOrBlank()){
                logger.error("[turbo plugin] 编译加速方案id未选择!")
                throw TurboException(errorMsg = "invalid input param")
            }
            //调用turbo接口，用于生成编译加速实例和构建id
            val buildIdResponseStr = TurboSdkApi.createTurboPlanInstance(
                    projectId = turboParam.projectName,
                    turboPlanId = turboParam.turboPlanId!!,
                    pipelineId = turboParam.pipelineId,
                    pipelineElementId = turboParam.pipelineTaskId,
                    pipelineName = turboParam.pipelineName,
                    userId = turboParam.pipelineStartUserName,
                    buildId = turboParam.pipelineBuildId
            )
            val buildIdResponse = JsonUtil.to(buildIdResponseStr, object : TypeReference<Response<String>>(){})
            val buildId = if(buildIdResponse.isOk()) buildIdResponse.data else {
                logger.error("[turbo plugin] 获取构建id失败! message: ${buildIdResponse.message}")
                throw TurboException(errorMsg = "get build id fail!")
            }
            logger.info("[turbo plugin] response build id: $buildId")
            if(buildId.isNullOrBlank()){
                logger.error("[turbo plugin] 构建id为空!")
                throw TurboException(errorMsg = "build id is null!")
            }
            val workspace = File(turboParam.bkWorkspace)
            val scriptList = mutableListOf<String>()
            scriptList.add("export $turboPlanIdEnv=${turboParam.turboPlanId!!}\n")
            scriptList.add("export $turboPlanBuildIdEnv=${buildId}\n")
            //根据构建环境进行不同的处理，具体如下:
            if (null != turboParam.continueNoneZero && turboParam.continueNoneZero!!) {
                scriptList.add("set +e\n")
            } else {
                scriptList.add("set -e\n")
            }

            // 按照选项区分是执行编译文件还是执行编译脚本
            if (!turboParam.scriptFrom.isNullOrBlank() && turboParam.scriptFrom == "FILE") {
                if(turboParam.turboScriptFile.isNullOrBlank()) {
                    logger.error("[turbo plugin] 编译加速脚本文件为空!")
                    throw TurboException(errorMsg = "invalid input param")
                }
                logger.info("[turbo plugin] turbo script file path: ${turboParam.turboScriptFile}")
                val filePath = "${turboParam.bkWorkspace}/${turboParam.turboScriptFile!!.trimStart('/')}"
                if(!File(filePath).exists()) {
                    logger.error("[turbo plugin] 编译脚本文件不存在!")
                    throw TurboException(errorMsg = "invalid input param")
                }
                scriptList.add("sh ${turboParam.turboScriptFile}")
            } else {
                if(turboParam.turboScript.isNullOrBlank()){
                    logger.error("[turbo plugin] 编译加速脚本为空!")
                    throw TurboException(errorMsg = "invalid input param")
                }
                scriptList.add(turboParam.turboScript!!)
            }
            logger.info("///////////////////////execute turbo script//////////////////////")
            val result = ScriptUtils.execute(
                    script = scriptList.joinToString(" "),
                    dir = workspace,
                    runtimeVariables = getRuntimeVariable(turboParam.turboPlanId!!, buildId),
                    prefix = "[turbo client] ",
                    printLog = true,
                    failExit = true
            )

            //判断脚本输出流，如果有对应的正则，则输出变量
            turboParamContext.result.data.putAll(getOutputVariabled(result))
            logger.info("[turbo plugin] turbo compile finish, time cost ${(System.currentTimeMillis() - startTime).div(1000)} seconds")
            logger.info("=====================================================")
        } catch (e : TurboException) {
            logger.error("[turbo plugin] 业务错误, 错误信息: ${e.errorMsg}")
            logger.info("[turbo plugin] turbo compile failed")
            turboParamContext.result.errorType = ErrorType.USER.num
            turboParamContext.result.message = e.errorMsg
            turboParamContext.result.status = Status.error
        } catch (t : Throwable) {
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
     * 获取给脚本传递的运行时变量
     */
    private fun getRuntimeVariable(turboPlanId: String, buildId: String): Map<String, String>{
        val map = JsonUtil.to(File(SdkUtils.getInputFile()).readText(), object : TypeReference<MutableMap<String, Any>>(){})
        map[turboPlanIdEnv] = turboPlanId
        map[turboPlanBuildIdEnv] = buildId
        return map.map { it.key to it.value.toString() }.toMap()
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
}
