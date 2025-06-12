package spam.blocker.util

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.ROLE_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Process
import android.provider.CallLog.Calls
import android.provider.Settings
import android.provider.Settings.Secure
import android.provider.Settings.SettingNotFoundException
import android.provider.Telephony
import android.provider.Telephony.Sms
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import spam.blocker.def.Def

object Permission {
    // These two launchers will be initialized in PermissionChain.kt.
    // for non-protected permission, e.g.: CALL_LOG
    lateinit var launcherNormal: ManagedActivityResultLauncher<String, Boolean>

    // for protected permission, e.g.: USAGE_STATS
    lateinit var launcherProtected: ManagedActivityResultLauncher<Intent, ActivityResult>


    abstract class Type {
        var isGranted by mutableStateOf(false)

        // For saving in shared prefs as `ask_once_$name`
        abstract fun name(): String
        // Check if this permission has been granted.
        abstract fun check(ctx: Context) : Boolean
        // Show a system dialog asking for this permission, or go to corresponding system settings.
        abstract fun ask(ctx: Context)
        // After a permission is granted or revoked, this callback function will be called.
        abstract fun onResult(isGranted: Boolean)
    }

    // Regular permissions like file_read/contacts/receive_sms
    open class Regular(
        val name: String,
    ) : Type() {
        override fun name(): String { return name }
        override fun check(ctx: Context): Boolean {
            return ContextCompat.checkSelfPermission(ctx, name) == PERMISSION_GRANTED
        }
        override fun ask(ctx: Context) { launcherNormal.launch(name) }
        override fun onResult(granted: Boolean) { isGranted = granted }
    }
    // All Regular permissions
    class Contacts(): Regular(Manifest.permission.READ_CONTACTS)
    class ReceiveSMS: Regular(Manifest.permission.RECEIVE_SMS)
    class ReceiveMMS: Regular(Manifest.permission.RECEIVE_MMS)
    class AnswerCalls: Regular(Manifest.permission.ANSWER_PHONE_CALLS)
    class CallLog: Regular(Manifest.permission.READ_CALL_LOG)
    class PhoneState: Regular(Manifest.permission.READ_PHONE_STATE)
    class ReadSMS: Regular(Manifest.permission.READ_SMS)
    open class FileAccess(name: String): Regular(name) {
        override fun check(ctx: Context): Boolean {
            return if (Build.VERSION.SDK_INT == Def.ANDROID_10) {
                super.check(ctx)
            } else {
                Environment.isExternalStorageManager()
            }
        }
        override fun ask(ctx: Context) {
            if (Build.VERSION.SDK_INT == Def.ANDROID_10) {
                super.ask(ctx)
            } else {
                launcherProtected.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .apply {
                            data = Uri.fromParts("package", ctx.packageName, null)
                        }
                )
            }
        }

        override fun onResult(granted: Boolean) {
            super.onResult(granted)
            // On android 11+, read/write share same permission: ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            // if any is granted, grant the other as well.
            if (Build.VERSION.SDK_INT > Def.ANDROID_10) {
                fileWrite.isGranted = granted
                fileRead.isGranted = granted
            }
        }
    }
    class FileRead(): FileAccess(Manifest.permission.READ_EXTERNAL_STORAGE)
    class FileWrite(): FileAccess(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    // It will launch an Intent for asking the permission.
    open class AskByIntent(
        val intent: Intent,
    ) : Type() {
        override fun name(): String { return intent.action!! }
        override fun check(ctx: Context): Boolean {
            throw Exception("unimplemented AskByIntent.check")
            return false
        }
        override fun ask(ctx: Context) { launcherProtected.launch(intent) }
        // The param only indicates whether this Intent was displayed correctly, call the `check()`
        //  to get if this permission is granted or not.
        override fun onResult(granted: Boolean) { isGranted = granted }
    }
    // AppOps permissions
    open class AppOps(
        val name: String,
        intent: Intent,
    ) : AskByIntent(intent) {
        override fun check(ctx: Context): Boolean {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                name,
                Process.myUid(),
                ctx.packageName
            )
            return (mode == AppOpsManager.MODE_ALLOWED)
        }
    }
    class UsageStats: AppOps(
        name = AppOpsManager.OPSTR_GET_USAGE_STATS,
        intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    )

    class Accessibility: AskByIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) {
        override fun check(ctx: Context): Boolean {
            return try {
                val enabled = Secure.getInt(
                    ctx.contentResolver,
                    Secure.ACCESSIBILITY_ENABLED
                )
                enabled != 0
            } catch (_: SettingNotFoundException) {
                false
            }
        }
    }


    val fileRead = FileRead()
    val fileWrite = FileWrite()
    val contacts = Contacts()
    val receiveSMS = ReceiveSMS()
    val receiveMMS = ReceiveMMS()
    val answerCalls = AnswerCalls()
    val callLog = CallLog()
    val phoneState = PhoneState()
    val readSMS = ReadSMS()
    val accessibility = Accessibility()
    val usageStats = UsageStats()

    // Initialized once when process starts (in App.kt)
    fun init(ctx: Context) {
        listOf(fileRead, fileWrite, contacts, receiveSMS, receiveMMS, callLog, phoneState, readSMS, accessibility, usageStats)
            .forEach {
                it.isGranted = it.check(ctx)
            }
    }

    fun isCallScreeningEnabled(ctx: Context): Boolean {
        val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    // initialized once in MainActivity
    lateinit var launcherSetAsCallScreeningApp: Lambda1<Lambda1<Boolean>?>

    fun initLauncherSetAsCallScreeningApp(activity: ComponentActivity) {

        var callback: Lambda1<Boolean>? = null

        val launcher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val granted = result.resultCode == Activity.RESULT_OK
            if (granted) {
                bindCallScreeningService(activity)
            }
            callback?.invoke(granted)
        }

        launcherSetAsCallScreeningApp = fun(cb: Lambda1<Boolean>?) {
            callback = cb

            val roleManager = activity.getSystemService(ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)

            launcher.launch(intent)
        }
    }

    private fun bindCallScreeningService(ctx: Context) {
        val mCallServiceIntent = Intent("android.telecom.CallScreeningService")
        mCallServiceIntent.setPackage(ctx.applicationContext.packageName)
        val mServiceConnection: ServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {}
            override fun onServiceDisconnected(componentName: ComponentName) {}
            override fun onBindingDied(name: ComponentName) {}
        }
        ctx.bindService(
            mCallServiceIntent,
            mServiceConnection,
            Activity.BIND_AUTO_CREATE
        )
    }

    fun listUsedAppWithinXSecond(ctx: Context, sec: Int): List<String> {
        val mapApps = mutableMapOf<String, Boolean>()

        val usageStatsManager =
            ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = Now.currentMillis()
        val events = usageStatsManager.queryEvents(currentTime - sec * 1000, currentTime)

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
            ) {
                mapApps[event.packageName] = true
            }
        }

        return mapApps.keys.toList()
    }

    // Get all events of a list of apps.
    // Returns a Map<pkgName, List<Event>>
    fun getAppsEvents(
        ctx: Context,
        pkgNames: Set<String>,
        withinMillis: Long = 24 * 3600 * 1000, // last 24 hours
    ): Map<String, List<UsageEvents.Event>> {
        val ret = mutableMapOf<String, MutableList<UsageEvents.Event>>()

        val usageStatsManager =
            ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = Now.currentMillis()
        val events = usageStatsManager.queryEvents(currentTime - withinMillis, currentTime)

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            val pkg = event.packageName
            if (pkgNames.contains(pkg)) {
                ret.getOrPut(pkg) { mutableListOf() }.add(event)
            }
        }
        return ret
    }

    fun isAppInForeground(ctx: Context, targetPackageName: String): Boolean {
        val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager?
            ?: return false // Usage stats service not available

        val now = System.currentTimeMillis()
        // Query stats for a short period (e.g., last 10 seconds)
        // Adjust the interval as needed, but keep it short for efficiency.
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 1000 * 10, // 10 seconds ago
            now
        )

        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return false
        }

        // Sort stats by last time used in descending order
        usageStatsList.sortByDescending { it.lastTimeUsed }

        // The first element in the sorted list is the most recently used app
        val mostRecentApp = usageStatsList[0]

        // Check if the most recently used app's package name matches the target
        return mostRecentApp.packageName == targetPackageName
    }

    fun isSmsAppInForeground(ctx: Context) : Boolean {
        val defaultSmsApp = Telephony.Sms.getDefaultSmsPackage(ctx)
        return isAppInForeground(ctx, defaultSmsApp)
    }

    // List all foreground service names that have started but not stopped yet.
    fun listRunningForegroundServiceNames(
        appEvents: List<UsageEvents.Event>?,
    ): List<String> {

        val startedServices = mutableMapOf<String, Boolean>()
        appEvents
            ?.filter {
                it.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START
                        || it.eventType == UsageEvents.Event.FOREGROUND_SERVICE_STOP
            }
            ?.forEach {
                // Set to `true` if it's START, set to `false` if it's STOP
                startedServices[it.className] =
                    it.eventType == UsageEvents.Event.FOREGROUND_SERVICE_START
            }

        return startedServices.filterValues { it  }.keys.toList()
    }

    fun getPackagesHoldingPermissions(
        pm: PackageManager,
        permissions: Array<String>
    ): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Def.ANDROID_14) {
            pm.getPackagesHoldingPermissions(
                permissions,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            pm.getPackagesHoldingPermissions(permissions, 0)
        }
    }

    fun getHistoryCallsByNumber(
        ctx: Context,
        phoneNumber: PhoneNumber,
        direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
        withinMillis: Long
    ): List<Int> {
        val selection = mutableListOf(
            "${Calls.DATE} >= ${System.currentTimeMillis() - withinMillis}"
        )

        if (direction == Def.DIRECTION_INCOMING) {
            selection.add(
                "${Calls.TYPE} IN (${Calls.INCOMING_TYPE}, ${Calls.MISSED_TYPE}, ${Calls.VOICEMAIL_TYPE}, ${Calls.REJECTED_TYPE}, ${Calls.BLOCKED_TYPE}, ${Calls.ANSWERED_EXTERNALLY_TYPE})"
            )
        } else {
            selection.add(
                "${Calls.TYPE} = ${Calls.OUTGOING_TYPE}"
            )
        }

        var ret = listOf<Int>()
        ctx.contentResolver.query(
            Calls.CONTENT_URI,
            arrayOf(Calls.NUMBER, Calls.TYPE),
            selection.joinToString(" AND "),
            null,
            null
        )?.use {
            if (it.moveToFirst()) {
                do {
                    val calledNumber = it.getString(0)
                    if (phoneNumber.isSame(calledNumber)) {
                        ret += it.getInt(1)
                    }
                } while (it.moveToNext())
            }
        }
        return ret
    }

    fun countHistorySMSByNumber(
        ctx: Context,
        phoneNumber: PhoneNumber,
        direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
        withinMillis: Long
    ): Int {
        val selection = mutableListOf(
            "${Sms.DATE} >= ${Now.currentMillis() - withinMillis}"
        )

        if (direction == Def.DIRECTION_INCOMING) {
            selection.add(
                "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}"
            )
        } else {
            selection.add(
                "${Sms.TYPE} IN (${Sms.MESSAGE_TYPE_SENT}, ${Sms.MESSAGE_TYPE_OUTBOX}, ${Sms.MESSAGE_TYPE_FAILED})"
            )
        }

        var count = 0
        try {
            ctx.contentResolver.query(
                Sms.CONTENT_URI,
                arrayOf(Sms.ADDRESS),
                selection.joinToString(" AND "),
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    do {
                        val messagedNumber = it.getString(0)
                        if (phoneNumber.isSame(messagedNumber)) {
                            count++
                        }
                    } while (it.moveToNext())
                }
            }
        } catch (ignore: Exception) {
        }
        return count
    }

}
