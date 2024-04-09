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
import spam.blocker.def.Def
import spam.blocker.ui.main.MainActivity
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
            val block = r.first
            val result = r.second
            val reason = r.third

            // 1. log to db
            val call = Record()
            call.peer = phone
            call.time = System.currentTimeMillis()
            call.result = result
            call.reason = reason
            val id = CallTable().addNewRecord(this, call)

            // 2. block
            if (block) {
                builder.apply {
                    setRejectCall(true)
                    setDisallowCall(true)
                    setSkipCallLog(false)
                }
                Log.e(Def.TAG, String.format("Reject call %s", phone))

                // click the notification to launch this app
                val intent = Intent(this, MainActivity::class.java)
                Util.showNotification(this, 0, resources.getString(R.string.spam_call_blocked), "", true, intent)
            }
            broadcastNewCall(block, id)

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
    private fun shouldBlock(phone: String): Triple<Boolean, Int, String> {

        val spf = SharedPref(this)

        // 1. check contacts
        if (spf.isContactsAllowed()) {
            val contact = Util.findContact(this, phone)
            if (contact != null) {
                Log.i(Def.TAG, "is contact")
                return Triple(false, Db.RESULT_ALLOWED_AS_CONTACT, contact.name)
            }
        }

        // 2. check for app used recently
        run {
            val enabledPackages = spf.getRecentAppList()
            val usedApps = Permission.listUsedAppWithinXSecond(this, 5 * 60)
            val intersection = enabledPackages.intersect(usedApps.toSet())

            Log.i(Def.TAG, "--- enabled: $enabledPackages, used: $usedApps, intersection: $intersection")

            if (intersection.isNotEmpty()) {
                Log.d(Def.TAG, "allowed by recent used app")
                return Triple(false, Db.RESULT_ALLOWED_BY_RECENT_APP, intersection.first())
            }
        }

        // 3. check for repeated call
        if(spf.isRepeatedAllowed()) {
            val fiveMin = 5*60*1000
            if (CallTable().hasRepeatedRecordsWithin(this, phone, fiveMin)) {
                Log.d(Def.TAG, "allowed by repeated call")
                return Triple(false, Db.RESULT_ALLOWED_BY_REPEATED_CALL, "")
            }
        }


        // 4. check number filters
        val filters = NumberFilterTable().listFilters(this, Db.FLAG_FOR_CALL, Db.FLAG_BOTH_WHITE_BLACKLIST)
        for (f in filters) {
            Log.i(Def.TAG, "checking filter: $f: ${f.pattern.toRegex().matches(phone)}")

            if (f.pattern.toRegex().matches(phone)) {
                val block = f.isBlacklist
                return Triple(
                    block,
                    if (block) Db.RESULT_BLOCKED_BLACKLIST else Db.RESULT_ALLOWED_WHITELIST,
                    f.id.toString()
                )
            }
        }

        // 5. pass by default
        return Triple(false, Db.RESULT_ALLOWED_BY_DEFAULT, "")
    }


}
