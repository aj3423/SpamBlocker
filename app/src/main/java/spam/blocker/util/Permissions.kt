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
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Process
import android.provider.CallLog.Calls
import android.provider.Telephony.Sms
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import spam.blocker.def.Def

object Permissions {
    fun requestReceiveSmsPermission(activity: ComponentActivity) {
        activity.requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 0)
    }

    fun isCallScreeningEnabled(ctx: Context): Boolean {
        val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    // must be initialized in MainActivity
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

    fun isPermissionGranted(ctx: Context, permission: String): Boolean {
        val ret = ContextCompat.checkSelfPermission(
            ctx, permission
        ) == PERMISSION_GRANTED
        return ret
    }

    fun isFileReadPermissionGranted(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT == Def.ANDROID_10) {
            isPermissionGranted(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            Environment.isExternalStorageManager()
        }
    }

    fun isFileWritePermissionGranted(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT == Def.ANDROID_10) {
            isPermissionGranted(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            Environment.isExternalStorageManager()
        }
    }

    fun isContactsPermissionGranted(ctx: Context): Boolean {
        return isPermissionGranted(ctx, Manifest.permission.READ_CONTACTS)
    }

    fun isReceiveSmsPermissionGranted(ctx: Context): Boolean {
        return isPermissionGranted(ctx, Manifest.permission.RECEIVE_SMS)
    }

    fun isCallLogPermissionGranted(ctx: Context): Boolean {
        return isPermissionGranted(ctx, Manifest.permission.READ_CALL_LOG)
    }

    fun isPhoneStatePermissionGranted(ctx: Context): Boolean {
        return isPermissionGranted(ctx, Manifest.permission.READ_PHONE_STATE)
    }

    fun isReadSmsPermissionGranted(ctx: Context): Boolean {
        return isPermissionGranted(ctx, Manifest.permission.READ_SMS)
    }

    fun isAppOpsPermissionGranted(ctx: Context, permission: String): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            permission,
            Process.myUid(),
            ctx.packageName
        )
        return (mode == AppOpsManager.MODE_ALLOWED)
    }

    fun isUsagePermissionGranted(ctx: Context): Boolean {
        return isAppOpsPermissionGranted(ctx, AppOpsManager.OPSTR_GET_USAGE_STATS)
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
