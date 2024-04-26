package spam.blocker.service

import android.content.Context
import android.util.Log
import spam.blocker.db.CallTable
import spam.blocker.db.ContentFilterTable
import spam.blocker.db.Db
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.PatternFilter
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.util.Permission
import spam.blocker.util.SharedPref
import spam.blocker.util.Util

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

class SpamChecker {
    companion object {

        fun checkCall(ctx: Context, phone: String): CheckResult {
            val spf = SharedPref(ctx)

            // 1. check contacts
            if (spf.isContactsAllowed()) {
                val contact = Util.findContact(ctx, phone)
                if (contact != null) {
                    Log.i(Def.TAG, "is contact")
                    return CheckResult(false, Def.RESULT_ALLOWED_AS_CONTACT).setContactName(contact.name)
                }
            }

            // 2. check for repeated call
            if(spf.isRepeatedAllowed()) {
                val cfg = spf.getRepeatedConfig()
                val times = cfg.first
                val durationMinutes = cfg.second * 60 * 1000

                // repeated count of call/sms, sms also counts
                val nCalls = CallTable().countRepeatedRecordsWithin(ctx, phone, durationMinutes)
                val nSMSs = SmsTable().countRepeatedRecordsWithin(ctx, phone, durationMinutes)
                if (nCalls + nSMSs >= times) {
                    Log.d(Def.TAG, "allowed by repeated call")
                    return CheckResult(false, Def.RESULT_ALLOWED_BY_REPEATED)
                }
            }

            // 3. check for app used recently
            run {
                val enabledPackages = spf.getRecentAppList()
                val inXmin = spf.getRecentAppConfig()
                val usedApps = Permission.listUsedAppWithinXSecond(ctx, inXmin * 60)
                val intersection = enabledPackages.intersect(usedApps.toSet())

                Log.i(Def.TAG, "--- enabled: $enabledPackages, used: $usedApps, intersection: $intersection")

                if (intersection.isNotEmpty()) {
                    Log.d(Def.TAG, "allowed by recent used app")
                    return CheckResult(false, Def.RESULT_ALLOWED_BY_RECENT_APP).setRecentApp(intersection.first())
                }
            }

            // 4. check number filters
            val filters = NumberFilterTable().listFilters(ctx, Db.FLAG_FOR_CALL, Db.FLAG_BOTH_WHITE_BLACKLIST)
            for (f in filters) {
                Log.i(Def.TAG, "checking filter: $f: ${f.pattern.toRegex().matches(phone)}")

                if (f.pattern.toRegex().matches(phone)) {
                    val block = f.isBlacklist
                    return CheckResult(
                        block,
                        if (block) Def.RESULT_BLOCKED_BLACKLIST else Def.RESULT_ALLOWED_WHITELIST
                    ).setFilter(f)
                }
            }

            // 5. pass by default
            return CheckResult(false, Def.RESULT_ALLOWED_BY_DEFAULT)
        }

        fun checkSms(
            ctx: Context,
            phone: String,
            messageBody: String
        ): CheckResult {

            val spf = SharedPref(ctx)

            // 1. check contacts
            if (spf.isContactsAllowed()) {
                val contact = Util.findContact(ctx, phone)
                if (contact != null) {
                    Log.i(Def.TAG, "is contact")
                    return CheckResult(false, Def.RESULT_ALLOWED_AS_CONTACT)
                        .setContactName(contact.name)
                }
            }


            class Wrapper(val filter: PatternFilter, val isNumberFilter: Boolean) {
            }

            // 2. check number/content filters
            run {
                val numberFilters = NumberFilterTable().listFilters(ctx, Db.FLAG_FOR_SMS, Db.FLAG_BOTH_WHITE_BLACKLIST)
                    .map { Wrapper(it, true) }
                val contentFilters = ContentFilterTable().listFilters(
                    ctx, 0/* doesn't care */, Db.FLAG_BOTH_WHITE_BLACKLIST)
                    .map { Wrapper(it, false) }

                // join them together and sort by priority desc
                val all = (numberFilters + contentFilters).sortedByDescending {
                    it.filter.priority
                }

                for (wrapper in all) {
                    val f = wrapper.filter

                    val matches = if (wrapper.isNumberFilter) {
                        f.pattern.toRegex().matches(phone)
                    } else { // sms content filter
                        if (f.patternExtra != "") {
                            f.pattern.toRegex().matches(messageBody) && f.patternExtra.toRegex().matches(phone)
                        } else {
                            f.pattern.toRegex().matches(messageBody)
                        }
                    }

                    if (matches) {
                        Log.i(Def.TAG, "filter matches: $f")

                        val block = f.isBlacklist
                        val result = if (wrapper.isNumberFilter) {
                            if (block) Def.RESULT_BLOCKED_BLACKLIST else Def.RESULT_ALLOWED_WHITELIST
                        } else {
                            if (block) Def.RESULT_BLOCKED_BY_CONTENT else Def.RESULT_ALLOWED_BY_CONTENT
                        }

                        return CheckResult(block, result).setFilter(f)
                    }
                }
            }

            // 4. pass by default
            return CheckResult(false, Def.RESULT_ALLOWED_BY_DEFAULT)
        }
    }
}