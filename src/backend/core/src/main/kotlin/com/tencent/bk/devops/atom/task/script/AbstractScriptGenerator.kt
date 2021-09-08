package com.tencent.bk.devops.atom.task.script


abstract class AbstractScriptGenerator {

    /**
     * 脚本预处理逻辑
     */
    abstract fun preProcessScript(scriptList: MutableList<String>)
}
