package com.tencent.bk.devops.atom.task.script


abstract class AbstractScriptGenerator {

    /**
     * 客户端安装逻辑
     */
    abstract fun installClient()
    /**
     * 脚本预处理逻辑
     */
    abstract fun preProcessScript(scriptList: MutableList<String>)
}
