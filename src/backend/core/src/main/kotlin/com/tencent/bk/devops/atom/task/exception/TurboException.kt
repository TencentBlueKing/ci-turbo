package com.tencent.bk.devops.atom.task.exception

import java.lang.RuntimeException

open class TurboException constructor(
        open val errorCode: Int = TurboException.errorCode,
        open val errorMsg: String,
        override val cause: Throwable? = null
): RuntimeException(errorMsg) {
    companion object {
        const val errorCode = 2199001
        const val errorMsg = "插件异常"
    }
}
