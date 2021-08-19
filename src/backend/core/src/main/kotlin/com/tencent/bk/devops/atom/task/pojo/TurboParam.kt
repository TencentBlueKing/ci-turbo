package com.tencent.bk.devops.atom.task.pojo

import com.tencent.bk.devops.atom.pojo.AtomBaseParam

class TurboParam : AtomBaseParam() {
    var desc: String? = null
    var turboPlanId: String? = null
    var scriptFrom: String? = "FILE"
    var turboScriptFile: String? = null
    var turboScript: String? = null
    var continueNoneZero: Boolean? = false
}
