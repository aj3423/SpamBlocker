package spam.blocker.service

import spam.blocker.db.PatternFilter

class CheckResult(
    val shouldBlock: Boolean,
    val result: Int,
) {
    var byContactName: String? = null // allowed by contact
    var byFilter: PatternFilter? = null // allowed or blocked by this filter rule
    var byRecentApp: String? = null // allowed by recent app

    fun reason(): String {
        if (byContactName != null) return byContactName!!
        if (byFilter != null) return byFilter!!.id.toString()
        if (byRecentApp != null) return byRecentApp!!
        return ""
    }
    fun setContactName(name: String): CheckResult {
        byContactName = name
        return this
    }
    fun setFilter(f: PatternFilter) : CheckResult {
        byFilter = f
        return this
    }
    fun setRecentApp(pkg: String) : CheckResult {
        byRecentApp = pkg
        return this
    }
}