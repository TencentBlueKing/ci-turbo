package com.tencent.bk.devops.atom.task.pojo

data class TurboPlanDetailModel(
    //方案id
    var planId: String = "",

    //方案名称
    var planName: String = "",

    //项目id
    var projectId: String = "",

    //加速模式
    var engineCode: String = "",

    //方案说明
    var desc: String? = "",

    //加速参数
    var configParam: Map<String, Any?>? = null,

    //IP白名单
    var whiteList: String = "",

    //编译加速任务启用状态
    var openStatus: Boolean = true
)
