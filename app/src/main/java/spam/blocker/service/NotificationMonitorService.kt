package spam.blocker.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.util.regexMatches
import spam.blocker.util.spf


private var cache: HashMap<String, MutableList<PushAlertRecord>>? = null

fun resetPushAlertCache() {
    cache = null
}

// Build a map of:
//   <packageName, listOf PushAlertRecord>
// to prevent database access on every notification.
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

class NotificationMonitorService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
//        logi("accessibility event: $event")
        if (event.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
            return

        val pd = event.parcelableData
        if (pd == null)
            return

        val extras = (pd as? Notification)?.extras
        if (extras == null)
            return

        val pkgName = event.packageName.toString()
        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")
        val body = listOf(title, text).joinToString("\n").trim()

        ensureCache(this)

        // All records for this package name
        val recs = cache?.get(pkgName)
        if (recs == null)
            return

//        logi("found pkg match: $pkgName, records size: ${recs.size}")

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

//        logi("push alert match, regex = ${max.body}, duration: ${max.duration}")

        val spf = spf.PushAlert(this)

        // Ignore if the new expire time is less than the previous time
        val prevExpireTime = spf.getExpireTime()
        val newExpireTime = System.currentTimeMillis() + max.duration.toLong() * 60 * 1000
        if (newExpireTime <= prevExpireTime)
            return

        spf.apply {
            setPkgName(pkgName)
            setBody(body)
            setExpireTime(newExpireTime)
        }
    }

    override fun onServiceConnected() {
//        logi("on connected")
        super.onServiceConnected()
    }

    override fun onInterrupt() { }
}