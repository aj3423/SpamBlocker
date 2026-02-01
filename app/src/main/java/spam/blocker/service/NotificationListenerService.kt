package spam.blocker.service

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
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

/*
How it works:

Limitation by Android:

- In doze mode(screen is off), notifications will not be pushed to `NotificationListenerService`,
 they are cached and will be delivered to the service when the screen is turned on.
- On an incoming call, the OS invokes `CallScreeningService.onScreenCall()`, which "activates" the app process.
- This in turn activates the `NotificationListenerService`, the OS will push all cached notifications to it.
- The execution order of `CallScreeningService` and `NotificationListenerService` is unpredictable, e.g.:
  1. `NotificationListenerService` receives notification 1
  2. `CallScreeningService` executes  <----  this blocks the whole process, following steps will not get executed before this returns.
  3. `NotificationListenerService` receives notification 2, 3...

To solve this, make `CallScreeningService` asynchronous using a coroutine and delay it by 500ms,
  so that all notifications would've been processed during this period.
*/

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
        val max = records.maxBy {
            it.duration
        }


        val spf = spf.PushAlert(this)

        // Ignore if the new expire time is less than the previous time
        val prevExpireTime = spf.expireTime
        val newExpireTime = postTime + max.duration.toLong() * 60 * 1000
        if (newExpireTime <= prevExpireTime)
            return

//        logi("push alert update, regex = ${max.body}, content: $body, expire: $newExpireTime")

        spf.pkgName = pkgName
        spf.body = body
        spf.expireTime = newExpireTime
    }
}