package spam.blocker.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.ROLE_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.service.NotificationListenerService
import spam.blocker.util.Permission.fileRead
import spam.blocker.util.Permission.fileWrite
import spam.blocker.util.PermissionLauncher.launcherProtected
import spam.blocker.util.PermissionLauncher.launcherRegular
import spam.blocker.util.PermissionType.AnswerCalls
import spam.blocker.util.PermissionType.Basic
import spam.blocker.util.PermissionType.BatteryUnRestricted
import spam.blocker.util.PermissionType.Calendar
import spam.blocker.util.PermissionType.CallLog
import spam.blocker.util.PermissionType.CallScreening
import spam.blocker.util.PermissionType.Contacts
import spam.blocker.util.PermissionType.FileRead
import spam.blocker.util.PermissionType.FileWrite
import spam.blocker.util.PermissionType.NotificationAccess
import spam.blocker.util.PermissionType.PhoneState
import spam.blocker.util.PermissionType.ReadSMS
import spam.blocker.util.PermissionType.ReceiveMMS
import spam.blocker.util.PermissionType.ReceiveSMS
import spam.blocker.util.PermissionType.UsageStats

object PermissionType {
    abstract class Basic {
        var isGranted by mutableStateOf(false)

        // "Read File", "Call Screening", ...
        abstract var descId: Int
        fun desc(ctx: Context): String { return ctx.getString(descId) }
        // Check if this permission has been granted.
        abstract fun check(ctx: Context) : Boolean
        // Show a system dialog asking for this permission, or go to corresponding system settings.
        abstract fun ask(ctx: Context)
        // After a permission is granted or revoked, this callback function will be called.
        abstract fun onResult(ctx: Context, isGranted: Boolean)
    }

    // Regular permissions like file_read/contacts/receive_sms
    open class Regular(
        val name: String,
        override var descId: Int,
    ) : Basic() {
        override fun check(ctx: Context): Boolean {
            return ContextCompat.checkSelfPermission(ctx, name) == PERMISSION_GRANTED
        }
        override fun ask(ctx: Context) { launcherRegular.launch(name) }
        override fun onResult(ctx: Context, granted: Boolean) { isGranted = granted }
    }

    // ---------------------------------------
    // WARNING:
    // Never rename these class names, they are used to recover permissions after a backup restore.
    // ---------------------------------------

    // All Regular permissions
    class Contacts: Regular(Manifest.permission.READ_CONTACTS, R.string.perm_contacts)
    class ReceiveSMS: Regular(Manifest.permission.RECEIVE_SMS, R.string.perm_receive_sms)
    class ReceiveMMS: Regular(Manifest.permission.RECEIVE_MMS, R.string.perm_receive_mms)
    class AnswerCalls: Regular(Manifest.permission.ANSWER_PHONE_CALLS, R.string.perm_answer_calls)
    class CallLog: Regular(Manifest.permission.READ_CALL_LOG, R.string.perm_call_logs)
    class PhoneState: Regular(Manifest.permission.READ_PHONE_STATE, R.string.perm_phone_state)
    class ReadSMS: Regular(Manifest.permission.READ_SMS, R.string.perm_read_sms)
    class Calendar: Regular(Manifest.permission.READ_CALENDAR, R.string.perm_read_calendar)

    open class FileAccess(name: String, descId: Int): Regular(name, descId) {
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

        override fun onResult(ctx: Context, granted: Boolean) {
            super.onResult(ctx, granted)
            // On android 11+, read/write share same permission: ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            // if any is granted, grant the other as well.
            if (Build.VERSION.SDK_INT > Def.ANDROID_10) {
                fileWrite.isGranted = granted
                fileRead.isGranted = granted
            }
        }
    }
    class FileRead(): FileAccess(Manifest.permission.READ_EXTERNAL_STORAGE, R.string.perm_file_read)
    class FileWrite(): FileAccess(Manifest.permission.WRITE_EXTERNAL_STORAGE, R.string.perm_file_write)

    // It will launch an Intent when asking for the permission.
    abstract class LaunchByIntent() : Basic() {
        abstract fun launcherIntent(ctx: Context): Intent
        override fun check(ctx: Context): Boolean {
            throw Exception("unimplemented AskByIntent.check")
            return false
        }
        override fun ask(ctx: Context) { launcherProtected.launch(launcherIntent(ctx)) }
        // The param only indicates whether this Intent was displayed correctly, call the `check()`
        //  to get if this permission is granted or not.
        override fun onResult(ctx: Context, granted: Boolean) {
            isGranted = granted
        }
    }
    // AppOps permissions
    open class AppOps(
        val name: String,
        override var descId: Int,
        val intent: Intent,
    ) : LaunchByIntent() {
        override fun launcherIntent(ctx: Context): Intent {
            return intent
        }
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
        descId = R.string.perm_usage_stats,
        intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    )

    class NotificationAccess(
        override var descId: Int = R.string.perm_notification_access
    ): LaunchByIntent() {
        override fun launcherIntent(ctx: Context): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

        override fun check(ctx: Context): Boolean {

            // This approach will crash on some devices: getString() can return null
//            val ret = Secure.getString(
//                ctx.applicationContext.contentResolver,
//                "enabled_notification_listeners"
//            )?.contains(ctx.applicationContext.packageName) == true

            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return notificationManager.isNotificationListenerAccessGranted(
                ComponentName(ctx, NotificationListenerService::class.java)
            )
        }
    }
//    class ExactAlarm: LaunchByIntent() {
//        override fun launcherIntent(ctx: Context): Intent {
//            return if (Build.VERSION.SDK_INT >= Def.ANDROID_12) {
//                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
//            } else {
//                return Intent()
//            }
//        }
//
//        override fun check(ctx: Context): Boolean {
//            val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
//
//            // Check if the app can schedule exact alarms (Android 12+)
//            return if (Build.VERSION.SDK_INT >= Def.ANDROID_12) {
//                alarmManager.canScheduleExactAlarms()
//            } else {
//                true // Permission not required on Android 11 or below
//            }
//        }
//    }
//    class Accessibility: LaunchByIntent() {
//        override fun launcherIntent(ctx: Context): Intent {
//            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
//        }
//        override fun check(ctx: Context): Boolean {
//            return try {
//                val enabled = Secure.getInt(
//                    ctx.contentResolver,
//                    Secure.ACCESSIBILITY_ENABLED
//                )
//                enabled != 0
//            } catch (_: SettingNotFoundException) {
//                false
//            }
//        }
//    }
    // This permission should always be optional because "Optimized" also works, not necessarily to be "Unrestricted".
    class BatteryUnRestricted(
        override var descId: Int = R.string.perm_battery_unrestricted
    ): LaunchByIntent() {
        @SuppressLint("BatteryLife")
        override fun launcherIntent(ctx: Context): Intent {
            return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${ctx.packageName}".toUri()
            }
        }

        override fun check(ctx: Context): Boolean {

            // This only checks if it's "Unrestricted", not including "Optimized"
            val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val unRestricted = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
            return unRestricted

            // This works for both "Unrestricted" and "Optimized",
            // but there is a 5-second delay for this to take effect, can't use this.
//            val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
//            val isRestricted =  activityManager.isBackgroundRestricted
//            return !isRestricted
        }
    }

    class CallScreening(
        override var descId: Int = R.string.perm_call_screening
    ): LaunchByIntent() {
        override fun launcherIntent(ctx: Context): Intent {
            val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            return intent
        }
        override fun check(ctx: Context): Boolean {
            val roleManager = ctx.getSystemService(ROLE_SERVICE) as RoleManager
            return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }

        override fun onResult(ctx: Context, granted: Boolean) {
            super.onResult(ctx, granted)
            if (granted)
                bindCallScreeningService(ctx)
        }
        private fun bindCallScreeningService(ctx: Context) {
            val mCallServiceIntent = Intent("android.telecom.CallScreeningService")
            mCallServiceIntent.setPackage(ctx.applicationContext.packageName)
            val mServiceConnection: ServiceConnection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {}
                override fun onServiceDisconnected(componentName: ComponentName) {}
            }
            ctx.bindService(
                mCallServiceIntent,
                mServiceConnection,
                Activity.BIND_AUTO_CREATE
            )
        }
    }
}

// These two launchers will be initialized in PermissionChain.kt.
object PermissionLauncher {
    // for non-protected regular permissions, e.g.: CALL_LOG
    lateinit var launcherRegular: ManagedActivityResultLauncher<String, Boolean>

    // for protected permissions, e.g.: USAGE_STATS
    lateinit var launcherProtected: ManagedActivityResultLauncher<Intent, ActivityResult>
}

object Permission {
    val callScreening = CallScreening()
    val fileRead = FileRead()
    val fileWrite = FileWrite()
    val contacts = Contacts()
    val receiveSMS = ReceiveSMS()
    val receiveMMS = ReceiveMMS()
    val answerCalls = AnswerCalls()
    val callLog = CallLog()
    val phoneState = PhoneState()
    val readSMS = ReadSMS()
    val calendar = Calendar()
    val notificationAccess = NotificationAccess()
    val usageStats = UsageStats()
    val batteryUnRestricted = BatteryUnRestricted()

    fun all(): List<Basic> {
        return listOf(
            callScreening,
            fileRead,
            fileWrite,
            contacts,
            receiveSMS,
            receiveMMS,
            answerCalls,
            callLog,
            phoneState,
            readSMS,
            calendar,
            notificationAccess,
            usageStats,
            batteryUnRestricted,
        )
    }

    fun allEnabled() : List<Basic> {
        return all().filter { it.isGranted }
    }
    // Initialized once when process starts (in App.kt)
    fun init(ctx: Context) {
        all().forEach { permission ->
            permission.isGranted = permission.check(ctx)
        }
    }
}
