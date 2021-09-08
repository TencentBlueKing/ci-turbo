package com.tencent.bk.devops.atom.task.script

import com.tencent.bk.devops.atom.task.constant.tbsBuildIdEnv
import com.tencent.bk.devops.atom.task.constant.tbsProjectIdEnv
import com.tencent.bk.devops.atom.task.constant.turboPlanBuildIdEnv
import com.tencent.bk.devops.atom.task.constant.turboPlanIdEnv
import com.tencent.bk.devops.atom.task.constant.turboPlanBoosterTypeEnv
import com.tencent.bk.devops.atom.task.constant.tbsBoosterTypeEnv
import com.tencent.bk.devops.atom.task.exception.TurboException
import com.tencent.bk.devops.atom.task.pojo.TurboParam
import org.slf4j.LoggerFactory
import java.io.File

class WindowsScriptGenerator(
    private val engineCode: String,
    private val turboParam: TurboParam,
    private val buildId: String
) : AbstractScriptGenerator() {

    companion object {
        private val logger = LoggerFactory.getLogger(LinuxScriptGenerator::class.java)
    }

    override fun preProcessScript(scriptList: MutableList<String>) {
        // 导入环境变量
        scriptList.add("set $turboPlanIdEnv=${turboParam.turboPlanId}\n")
        scriptList.add("set $turboPlanBuildIdEnv=${buildId}\n")
        scriptList.add("set $tbsProjectIdEnv=${turboParam.turboPlanId}\n")
        scriptList.add("set $tbsBuildIdEnv=${buildId}\n")
        scriptList.add("set $turboPlanBoosterTypeEnv=$engineCode\n")
        scriptList.add("set $tbsBoosterTypeEnv=$engineCode\n")

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
            scriptList.add("cmd.exe /C ${turboParam.turboScriptFile}")
        } else {
            if (turboParam.turboScript.isNullOrBlank()) {
                logger.error("[turbo plugin] 编译加速脚本为空!")
                throw TurboException(errorMsg = "invalid input param")
            }
            scriptList.add(turboParam.turboScript!!)
        }
    }
}
