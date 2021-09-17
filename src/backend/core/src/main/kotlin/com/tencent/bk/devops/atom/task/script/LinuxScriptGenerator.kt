package com.tencent.bk.devops.atom.task.script

import com.tencent.bk.devops.atom.task.constant.tbsBuildIdEnv
import com.tencent.bk.devops.atom.task.constant.tbsProjectIdEnv
import com.tencent.bk.devops.atom.task.constant.turboPlanBuildIdEnv
import com.tencent.bk.devops.atom.task.constant.turboPlanIdEnv
import com.tencent.bk.devops.atom.task.constant.turboPlanBoosterTypeEnv
import com.tencent.bk.devops.atom.task.constant.tbsBoosterTypeEnv
import com.tencent.bk.devops.atom.task.exception.TurboException
import com.tencent.bk.devops.atom.task.pojo.TurboParam
import com.tencent.bk.devops.atom.task.utils.AgentEnv
import org.slf4j.LoggerFactory
import java.io.File

class LinuxScriptGenerator(
    private val engineCode: String,
    private val turboParam: TurboParam,
    private val buildId: String,
    private val turboPublicPath: String,
    private val turboPrivatePath: String
) : AbstractScriptGenerator() {
    companion object {
        private val logger = LoggerFactory.getLogger(LinuxScriptGenerator::class.java)
    }
    override fun preProcessScript(scriptList: MutableList<String>) {
        // 导入环境变量
        scriptList.add("export $turboPlanIdEnv=${turboParam.turboPlanId}\n")
        scriptList.add("export $turboPlanBuildIdEnv=${buildId}\n")
        scriptList.add("export $tbsProjectIdEnv=${turboParam.turboPlanId}\n")
        scriptList.add("export $tbsBuildIdEnv=${buildId}\n")
        scriptList.add("export $turboPlanBoosterTypeEnv=$engineCode\n")
        scriptList.add("export $tbsBoosterTypeEnv=$engineCode\n")

        // 第三方构建机，采用public的占位符进行下载
        if (AgentEnv.isThirdParty()) {
            scriptList.add("curl -sSf http://$turboPublicPath/clients/install.sh | bash -s -- -r public")
        } else {
            // 私有构建机，采用private的占位符进行下载
            scriptList.add("curl -sSf http://$turboPrivatePath/clients/install.sh | bash -s -- -r private")
        }

        // 根据构建环境进行不同的处理，具体如下:
        if (null != turboParam.continueNoneZero && turboParam.continueNoneZero!!) {
            scriptList.add("set +e\n")
        } else {
            scriptList.add("set -e\n")
        }

        // 按照选项区分是执行编译文件还是执行编译脚本
        if (!turboParam.scriptFrom.isNullOrBlank() && turboParam.scriptFrom == "FILE") {
            if (turboParam.turboScriptFile.isNullOrBlank()) {
                logger.error("[turbo plugin] 编译加速脚本文件为空!")
                throw TurboException(errorMsg = "invalid input param")
            }
            logger.info("[turbo plugin] turbo script file path: ${turboParam.turboScriptFile}")
            val filePath = "${turboParam.bkWorkspace}/${turboParam.turboScriptFile!!.trimStart('/')}"
            if (!File(filePath).exists()) {
                logger.error("[turbo plugin] 编译脚本文件不存在!")
                throw TurboException(errorMsg = "invalid input param")
            }
            scriptList.add("sh ${turboParam.turboScriptFile}")
        } else {
            if (turboParam.turboScript.isNullOrBlank()) {
                logger.error("[turbo plugin] 编译加速脚本为空!")
                throw TurboException(errorMsg = "invalid input param")
            }
            scriptList.add(turboParam.turboScript!!)
        }
    }
}
