package com.tencent.bk.devops.atom.task.api

import com.tencent.bk.devops.atom.api.BaseApi
import com.tencent.bk.devops.atom.task.exception.TurboException
import com.tencent.bk.devops.plugin.utils.JsonUtil
import com.tencent.bk.devops.plugin.utils.OkhttpUtils
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

object TurboSdkApi: BaseApi(){

    private val logger = LoggerFactory.getLogger(TurboSdkApi::class.java)
    private const val turboPlanInstancePath = "/ms/turbo/api/build/turboPlanInstance"
    private const val turboPlanPath = "/ms/turbo/api/build/turboPlan/{turboPlanId}"
    private val objectMapper = JsonUtil.getObjectMapper()
    private var rootPath: String?= null

    fun init(rootPath: String){
        if(this.rootPath.isNullOrBlank()){
            synchronized(this){
                if(this.rootPath.isNullOrBlank()){
                    this.rootPath = rootPath
                }
            }
        }
    }

    /**
     * 创建编译加速方案实例
     */
    fun createTurboPlanInstance(
            projectId: String,
            turboPlanId: String,
            pipelineId: String,
            pipelineElementId: String,
            pipelineName: String,
            buildId: String,
            userId: String
    ): String {
        val jsonBody = objectMapper.writeValueAsString(mapOf(
                "projectId" to projectId,
                "turboPlanId" to turboPlanId,
                "pipelineId" to pipelineId,
                "pipelineElementId" to pipelineElementId,
                "pipelineName" to pipelineName,
                "buildId" to buildId
        ))
        val headers = mutableMapOf(
                "X-DEVOPS-UID" to userId
        )
        return taskExecution(
                headers = headers,
                jsonBody = jsonBody,
                path = turboPlanInstancePath,
                method = "POST"
        )
    }

    /**
     * 通过编译加速方案id获取详情
     */
    fun getTurboPlanDetailById(
            turboPlanId: String
    ): String {
        val finalTurboPlanPath = turboPlanPath.replace("{turboPlanId}", turboPlanId)
        return taskExecution(
                path = finalTurboPlanPath,
                method = "GET"
        )
    }

    /**
     * 调用接口公共方法
     */
    @Throws
    private fun taskExecution(
            jsonBody: String = "",
            path: String,
            headers: MutableMap<String, String> = mutableMapOf(),
            method: String = "GET",
            printLog: Boolean = true
    ): String {
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = when (method) {
            "GET" -> {
                val r = buildGet(path)
                val builder = r.newBuilder()
                headers.forEach { (t, u) -> builder.addHeader(t, u) }
                builder.build()
            }
            "POST" -> {
                buildPost(path, requestBody, headers)
            }
            "DELETE" -> {
                buildDelete(path, requestBody, headers)
            }
            "PUT" -> {
                buildPut(path, requestBody, headers)
            }
            else -> {
                throw TurboException(errorMsg = "error method to execute: $method")
            }
        }

        val backendRequest = request.newBuilder()
                .url("$rootPath$path")
                .build()


        OkhttpUtils.doHttp(backendRequest).use { response ->
            val responseBody = response.body!!.string()
            if (!response.isSuccessful) {
                logger.error(
                        "[turbo plugin] 调用接口($path), 返回信息为(${response.message}), 返回体为($responseBody)"
                )
                throw TurboException(errorMsg = "Fail to invoke Turbo request")
            }
            return responseBody
        }
    }
}
