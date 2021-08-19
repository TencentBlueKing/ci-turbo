package com.tencent.bk.devops.atom.task.pojo

import com.fasterxml.jackson.annotation.JsonIgnore

data class Response<out T>(
        val code: Int,
        val message: String? = null,
        val data: T? = null
) {
    /**
     * 根据返回码判断是否成功
     */
    @JsonIgnore
    fun isOk(): Boolean {
        return code == SUCCESS
    }

    /**
     * 根据返回码判断是否失败
     */
    @JsonIgnore
    fun isNotOk(): Boolean {
        return !isOk()
    }

    companion object {

        /**
         * 成功返回码
         */
        private const val SUCCESS = 0

        /**
         * 成功响应体，返回数据为空
         */
        fun success() = Response(SUCCESS, null, null)

        /**
         * 成功响应体
         * @param data 数据
         */
        fun <T> success(data: T) = Response(SUCCESS, null, data)

        /**
         *  错误响应体
         *  @param code 错误返回码
         *  @param message 错误提示信息
         */
        fun fail(code: Int, message: String) = Response<Void>(code, message, null)
    }
}
