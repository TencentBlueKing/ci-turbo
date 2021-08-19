package com.tencent.bk.devops.atom.task.utils

import com.tencent.bk.devops.atom.api.SdkEnv
import com.tencent.bk.devops.atom.task.exception.TurboException
import com.tencent.bk.devops.atom.task.pojo.BuildType
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.util.*

object AgentEnv {

    private val logger = LoggerFactory.getLogger(AgentEnv::class.java)


    private var property: Properties? = null

    private val propertyFile = File(getLandun(), ".agent.properties")

    fun isProd() = true

    fun isTest() = false

    fun isDev() = false

    fun getLandun() = File(".")

    private fun getProperty(prop: String): String? {
        val buildType = getBuildType()
        if (buildType == BuildType.DOCKER.name) {
            return getEnv(prop)
        }

        if (property == null) {
            if (!propertyFile.exists()) {
                throw TurboException(errorMsg = "The property file(${propertyFile.absolutePath}) is not exist")
            }
            property = Properties()
            property!!.load(FileInputStream(propertyFile))
        }
        return property!!.getProperty(prop, null)
    }

    private fun getEnv(prop: String): String? {
        var value = System.getenv(prop)
        if (value.isNullOrBlank()) {
            // Get from java properties
            value = System.getProperty(prop)
        }
        return value
    }

    fun isThirdParty() = getBuildType() == BuildType.AGENT.name

    fun getBuildType(): String {
        val buildType = SdkEnv.getSdkHeader()["X-DEVOPS-BUILD-TYPE"]
        if (buildType == null || !buildTypeContains(buildType)) {
            return BuildType.AGENT.name
        }
        return buildType
    }

    private fun buildTypeContains(env: String): Boolean {
        BuildType.values().forEach {
            if (it.name == env) {
                return true
            }
        }
        return false
    }


    fun isDockerEnv(): Boolean {
        return getBuildType() == BuildType.DOCKER.name
    }

    fun is32BitSystem() = System.getProperty("sun.arch.data.model") == "32"
}
