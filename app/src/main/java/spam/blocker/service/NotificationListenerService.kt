package spam.blocker.service

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.db.RegexRule
import spam.blocker.util.Permission
import spam.blocker.util.regexFind
import spam.blocker.util.regexMatches
import spam.blocker.util.regexMatchesNumber
import spam.blocker.util.spf

// A generic phone-number-shaped substring, e.g. "555-123-4567", "(555) 123-4567", "+15551234567".
// Used to find number candidates embedded anywhere within a notification's title,
//  which are then normalized (stripping formatting) and matched against each rule's
//  own pattern via `regexMatchesNumber`, the same way a real call/SMS number is matched.
private val phoneNumberCandidateRegex = Regex("""\+?[\s().-]*(?:\d[\s().-]*){3,15}""")

private fun findNumberCandidates(title: String): List<String> {
    return phoneNumberCandidateRegex.findAll(title)
        .map { it.value }
        .filter { candidate -> candidate.count { it.isDigit() } in 3..15 }
        .toList()
}

// The set of package names to screen (App Notifications), cached to avoid
//  reading SharedPref on every notification.
private var notificationScreeningPkgs: Set<String>? = null

// Rules with "Notification Title"/"Notification Body" enabled in their "Apply to",
//  cached to avoid DB access on every notification.
private var notifTitleNumberRules: List<RegexRule>? = null // Number Rules, checked against the title
private var notifBodyNumberRules: List<RegexRule>? = null // Number Rules, checked against the body
private var notifTitleContentRules: List<RegexRule>? = null // Content/Message Rules, checked against the title
private var notifBodyContentRules: List<RegexRule>? = null // Content/Message Rules, checked against the body

fun resetNotificationScreeningCache() {
    notificationScreeningPkgs = null
    notifTitleNumberRules = null
    notifBodyNumberRules = null
    notifTitleContentRules = null
    notifBodyContentRules = null
}

private fun ensureNotificationScreeningCache(ctx: Context) {
    if (notificationScreeningPkgs == null) {
        notificationScreeningPkgs = spf.AppNotifications(ctx).getList().toSet()
    }
    if (notifTitleNumberRules == null || notifBodyNumberRules == null) {
        val numberRules = NumberRegexTable().listAll(ctx)
        notifTitleNumberRules = numberRules.filter { it.isForNotifTitle() }
        notifBodyNumberRules = numberRules.filter { it.isForNotifBody() }
    }
    if (notifTitleContentRules == null || notifBodyContentRules == null) {
        val contentRules = ContentRegexTable().listAll(ctx)
        notifTitleContentRules = contentRules.filter { it.isForNotifTitle() }
        notifBodyContentRules = contentRules.filter { it.isForNotifBody() }
    }
}

// A set of "pkgName|title|text" already screened, to avoid re-screening the same
//  notification content multiple times. Some apps assign a new sbn.key/postTime
//  on every repost (e.g. an unrelated group/summary refresh), even though the
//  actual title+text content is unchanged, so identity is tracked by content instead.
private var screenedNotifications: MutableSet<String> = mutableSetOf()

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

        // Notification Screening: works independently of Push Alert and of the SMS switch,
        //  only requires Notification Access permission (already granted for this service to run at all).
        val spfGlobal = spf.Global(this)
        val contentKey = "$pkgName|$title|$text"
        val alreadyScreened = screenedNotifications.contains(contentKey)

        ensureNotificationScreeningCache(this)

        if (spfGlobal.isNotificationScreeningEnabled &&
            Permission.notificationAccess.isGranted &&
            notificationScreeningPkgs?.contains(pkgName) == true &&
            !alreadyScreened
        ) {
            // Extract phone-number-shaped substrings from the title/body (tolerating
            //  dashes/spaces/parens/+), then test each against every Number Rule using
            //  the same matching logic used for real numbers, which normalizes formatting
            //  per the rule's own Raw Number/Ignore Country Code flags before comparing.
            // A Number Rule can independently target the title and/or the body, e.g. a
            //  voicemail notification like "New voicemail from: 5551234567" has the
            //  number in the body, not the title.
            val titleCandidates = findNumberCandidates(title)
            val bodyCandidates = findNumberCandidates(text)

            val matchedNumber = notifTitleNumberRules?.firstNotNullOfOrNull { rule ->
                titleCandidates.firstOrNull { candidate ->
                    rule.pattern.regexMatchesNumber(candidate, rule.patternFlags)
                }
            } ?: notifBodyNumberRules?.firstNotNullOfOrNull { rule ->
                bodyCandidates.firstOrNull { candidate ->
                    rule.pattern.regexMatchesNumber(candidate, rule.patternFlags)
                }
            }

            // Or each Content/Message Rule (applies to Notification Title and/or Body)
            //  against the corresponding field. Since `Checker.checkSms` re-validates
            //  Content Rules with a full-string match against whatever `messageBody` it's
            //  given, the field that actually matched must be the one passed onward,
            //  otherwise the downstream re-check fails against the wrong field.
            val titleContentMatches = notifTitleContentRules
                ?.any { rule -> rule.pattern.regexFind(title, rule.patternFlags) != null } == true
            val bodyContentMatches = notifBodyContentRules
                ?.any { rule -> rule.pattern.regexFind(text, rule.patternFlags) != null } == true

            if (matchedNumber != null || titleContentMatches || bodyContentMatches) {
                screenedNotifications.add(contentKey)

                // Prefer the number extracted from the title/body; if only the message
                //  content matched, fall back to the raw title as the number passed onward.
                val rawNumber = matchedNumber ?: title

                // Pass whichever field triggered the Content Rule match as `messageBody`,
                //  so the downstream full-match re-check succeeds against the same text.
                // If it matched via the title, use the title; otherwise use the body as usual.
                val messageBodyForCheck = if (titleContentMatches) title else text

                val result = SmsReceiver.processSms(
                    ctx = this,
                    rawNumber = rawNumber,
                    messageBody = messageBodyForCheck,
                    simSlot = null,
                    isTest = false,
                    showNotification = false,
                )
                if (result.shouldBlock()) {
                    cancelNotification(sbn.key)
                }
            }
        }

        ensureCache(this)

        // All records for this package name
        val recs = cache?.get(pkgName)
        if (recs == null)
            return

        val body = listOf(title, text).joinToString("\n").trim()

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

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        super.onNotificationRemoved(sbn)

        val extras = sbn.notification.extras ?: return
        val title = extras.getString("android.title", "")
        val text = extras.getString("android.text", "")
        screenedNotifications.remove("${sbn.packageName}|$title|$text")
    }
}