package spam.blocker.service

import android.content.Intent
import android.telecom.Call as TelecomCall
import android.telecom.CallScreeningService
import android.util.Log
import spam.blocker.R
import spam.blocker.db.CallTable
import spam.blocker.db.Db
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.Record
import spam.blocker.db.SmsTable
import spam.blocker.def.Def
import spam.blocker.ui.main.MainActivity
import spam.blocker.util.Notification
import spam.blocker.util.Permission
import spam.blocker.util.SharedPref
import spam.blocker.util.Util

class CallService : CallScreeningService() {
    override fun onScreenCall(callDetails: TelecomCall.Details) {
        Log.d(Def.TAG, String.format("new call"))

        if (!SharedPref(this).isGloballyEnabled()) {
            return
        }

        if (
            callDetails.callDirection != TelecomCall.Details.DIRECTION_INCOMING
        ) return

        val builder = CallResponse.Builder()
        try {
            val phone = callDetails.handle.schemeSpecificPart
            Log.d(Def.TAG, phone)

            val r = shouldBlock(phone)

            // 1. log to db
            val call = Record()
            call.peer = phone
            call.time = System.currentTimeMillis()
            call.result = r.result
            call.reason = r.reason()
            val id = CallTable().addNewRecord(this, call)

            // 2. block
            if (r.shouldBlock) {
                builder.apply {
                    setRejectCall(true)
                    setDisallowCall(true)
                    setSkipCallLog(false)
                }
                Log.d(Def.TAG, String.format("Reject call %s", phone))

                val importance = if (r.result == Db.RESULT_BLOCKED_BLACKLIST)
                    r.byFilter!!.importance
                else
                    Def.DEF_SPAM_IMPORTANCE

                // click the notification to launch this app
                val intent = Intent(this, MainActivity::class.java)
                Notification.show(this, 0, resources.getString(R.string.spam_call_blocked), phone, importance, intent)
            }
            broadcastNewCall(r.shouldBlock, id)

        } catch (t: Throwable) {
            Log.w(Def.TAG, t)
        } finally {
            respondToCall(callDetails, builder.build())
        }
    }

    private fun broadcastNewCall(blocked: Boolean, id: Long) {
        val intent = Intent(Def.ON_NEW_CALL)
        intent.putExtra("type", "call")
        intent.putExtra("blocked", blocked)
        intent.putExtra("record_id", id)

        sendBroadcast(intent)
    }
    // returns <should_block, result, reason>
    private fun shouldBlock(phone: String): CheckResult {

        val spf = SharedPref(this)

        // 1. check contacts
        if (spf.isContactsAllowed()) {
            val contact = Util.findContact(this, phone)
            if (contact != null) {
                Log.i(Def.TAG, "is contact")
                return CheckResult(false, Db.RESULT_ALLOWED_AS_CONTACT).setContactName(contact.name)
            }
        }

        // 2. check for repeated call
        if(spf.isRepeatedAllowed()) {
            val cfg = spf.getRepeatedConfig()
            val times = cfg.first
            val durationMinutes = cfg.second * 60 * 1000

            // repeated count of call/sms, sms also counts
            val nCalls = CallTable().countRepeatedRecordsWithin(this, phone, durationMinutes)
            val nSMSs = SmsTable().countRepeatedRecordsWithin(this, phone, durationMinutes)
            if (nCalls + nSMSs >= times) {
                Log.d(Def.TAG, "allowed by repeated call")
                return CheckResult(false, Db.RESULT_ALLOWED_BY_REPEATED)
            }
        }

        // 3. check for app used recently
        run {
            val enabledPackages = spf.getRecentAppList()
            val inXmin = spf.getRecentAppConfig()
            val usedApps = Permission.listUsedAppWithinXSecond(this, inXmin * 60)
            val intersection = enabledPackages.intersect(usedApps.toSet())

            Log.i(Def.TAG, "--- enabled: $enabledPackages, used: $usedApps, intersection: $intersection")

            if (intersection.isNotEmpty()) {
                Log.d(Def.TAG, "allowed by recent used app")
                return CheckResult(false, Db.RESULT_ALLOWED_BY_RECENT_APP).setRecentApp(intersection.first())
            }
        }

        // 4. check number filters
        val filters = NumberFilterTable().listFilters(this, Db.FLAG_FOR_CALL, Db.FLAG_BOTH_WHITE_BLACKLIST)
        for (f in filters) {
            Log.i(Def.TAG, "checking filter: $f: ${f.pattern.toRegex().matches(phone)}")

            if (f.pattern.toRegex().matches(phone)) {
                val block = f.isBlacklist
                return CheckResult(
                    block,
                    if (block) Db.RESULT_BLOCKED_BLACKLIST else Db.RESULT_ALLOWED_WHITELIST
                ).setFilter(f)
            }
        }

        // 5. pass by default
        return CheckResult(false, Db.RESULT_ALLOWED_BY_DEFAULT)
    }


}
