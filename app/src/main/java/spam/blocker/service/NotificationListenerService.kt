package spam.blocker.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import spam.blocker.db.PushAlertTable
import spam.blocker.util.regexMatches
import spam.blocker.util.spf


class NotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val pkgName = sbn.packageName

        val extras = sbn.notification.extras

        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")

        // body == title + \n + text
        val body = listOf(title, text).joinToString("\n").trim()

        val records = PushAlertTable.listForPackage(this, pkgName) // for this package && `enabled`
            .filter {
                it.isValid() && it.body.regexMatches(body, it.bodyFlags)
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
        val newExpireTime = System.currentTimeMillis() + max.duration.toLong() * 60 * 1000
        if (newExpireTime <= prevExpireTime)
            return

        spf.apply {
            setPkgName(pkgName)
            setBody(body)
            setExpireTime(newExpireTime)
        }
    }
}