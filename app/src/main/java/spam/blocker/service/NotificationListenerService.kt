package spam.blocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.util.logi
import spam.blocker.util.regexMatches
import spam.blocker.util.spf

// A map of:
//   <packageName, listOf PushAlertRecord>
// to prevent database access on every notification.
private var cache: HashMap<String, MutableList<PushAlertRecord>>? = null

fun resetPushAlertCache() {
    cache = null
}

private fun ensureCache(ctx: Context) {
    if (cache == null) {
//        logi("rebuild push alert cache")
        cache = hashMapOf()
        val records = PushAlertTable.listAll(ctx)
            .filter {
                it.enabled && it.isValid()
            }
        records.forEach {
            val list = cache!!.getOrPut(it.pkgName) { mutableListOf() }
            list.add(it)
        }
    }
}

class NotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val pkgName = sbn.packageName
        val postTime = sbn.postTime

        val extras = sbn.notification.extras
        if (extras == null)
            return

        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")
        val body = listOf(title, text).joinToString("\n").trim()

        ensureCache(this)

        // All records for this package name
        val recs = cache?.get(pkgName)
        if (recs == null)
            return

        // Keep all records that match the notification content
        val records = recs.filter {
            it.body.regexMatches(body, it.bodyFlags)
        }

        if (records.isEmpty())
            return

        // Get the record with max duration
        var max = records.maxBy {
            it.duration
        }


        val spf = spf.PushAlert(this)

        // Ignore if the new expire time is less than the previous time
        val prevExpireTime = spf.getExpireTime()
        val newExpireTime = postTime + max.duration.toLong() * 60 * 1000
        if (newExpireTime <= prevExpireTime)
            return

//        logi("push alert update, regex = ${max.body}, content: $body, expire: $newExpireTime")

        spf.apply {
            setPkgName(pkgName)
            setBody(body)
            setExpireTime(newExpireTime)
        }
    }
}