package spam.blocker.util

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.CallLog.Calls
import android.provider.Settings
import android.provider.Telephony.Sms
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import spam.blocker.def.Def

open class Permission {
    companion object {

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun requestAllManifestPermissions(activity: AppCompatActivity) {
            try {
                val packageInfo = activity.packageManager.getPackageInfo(
                    activity.packageName, PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_PERMISSIONS.toLong()
                    )
                )

                val set = mutableSetOf(*packageInfo.requestedPermissions ?: emptyArray())
                val permissions = set.toTypedArray()
                activity.requestPermissions(permissions, 0)

            } catch (t: Throwable) {
                Log.w(Def.TAG, t)
            }
        }

        fun requestReceiveSmsPermission(activity: AppCompatActivity) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 0)
        }

        fun promptSetAsDefaultCallScreeningApp(activity: AppCompatActivity): () -> Unit {
            val roleManager =
                activity.getSystemService(AppCompatActivity.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            val startForRequestRoleResult = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result: androidx.activity.result.ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    bindCallScreeningService(activity)
                }
            }
            return fun() {
                startForRequestRoleResult.launch(intent)
            }
        }

        private fun bindCallScreeningService(activity: AppCompatActivity) {
            val mCallServiceIntent = Intent("android.telecom.CallScreeningService")
            mCallServiceIntent.setPackage(activity.applicationContext.packageName)
            val mServiceConnection: ServiceConnection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {}
                override fun onServiceDisconnected(componentName: ComponentName) {}
                override fun onBindingDied(name: ComponentName) {}
            }
            activity.bindService(
                mCallServiceIntent,
                mServiceConnection,
                AppCompatActivity.BIND_AUTO_CREATE
            )
        }

        private fun isPermissionGranted(ctx: Context, permission: String): Boolean {
            val ret = ContextCompat.checkSelfPermission(
                ctx, permission
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(Def.TAG, "$permission Granted: $ret")
            return ret
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

        fun isReadSmsPermissionGranted(ctx: Context): Boolean {
            return isPermissionGranted(ctx, Manifest.permission.READ_SMS)
        }

        fun isUsagePermissionGranted(ctx: Context): Boolean {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName
            )
            return (mode == AppOpsManager.MODE_ALLOWED)
        }

        fun goToUsagePermissionSetting(ctx: Context) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            ctx.startActivity(intent)
        }

        fun listUsedAppWithinXSecond(ctx: Context, sec: Int): List<String> {
            val ret = mutableListOf<String>()

            val usageStatsManager =
                ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = Time.currentTimeMillis()
            val events = usageStatsManager.queryEvents(currentTime - sec * 1000, currentTime)

            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)

                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                    event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                ) {
                    ret += event.packageName
                }
            }

            return ret.distinct()
        }


        fun countHistoryCallByNumber(
            ctx: Context,
            rawNumber: String,
            direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
            withinMillis: Long
        ): Int {
            val selection = mutableListOf(
                "REPLACE(REPLACE(REPLACE(${Calls.NUMBER}, '-', ''), ' ', ''), '+', '') LIKE '${
                    Util.clearNumber(
                        rawNumber
                    )
                }'",
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

            val cursor = ctx.contentResolver.query(
                Calls.CONTENT_URI,
                null,
                selection.joinToString(" AND "),
                null,
                null
            )
            val count = cursor?.count ?: 0

            cursor?.close()
            return count
        }

        fun countHistorySMSByNumber(
            ctx: Context,
            rawNumber: String,
            direction: Int, // Def.DIRECTION_INCOMING, Def.DIRECTION_OUTGOING
            withinMillis: Long
        ): Int {
            val selection = mutableListOf(
                "REPLACE(REPLACE(REPLACE(${Sms.ADDRESS}, '-', ''), ' ', ''), '+', '') LIKE '${
                    Util.clearNumber(
                        rawNumber
                    )
                }'",
                "${Sms.DATE} >= ${Time.currentTimeMillis() - withinMillis}"
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

            val cursor = ctx.contentResolver.query(
                Sms.CONTENT_URI,
                null,
                selection.joinToString(" AND "),
                null,
                null
            )
            val count = cursor?.count ?: 0

            cursor?.close()
            return count
        }

    }
}

/*
    Convenient class for asking multiple permissions,
    execute success callback when all permissions granted,
    execute failure callback when any of them fails.

    Must be created during the creation of the fragment
 */
class PermissionChain(
    fragment: Fragment,
    private val permissions: List<String>,
    private val onResult: (Boolean) -> Unit
) {
    private val launcher: ActivityResultLauncher<String>

    private lateinit var curr: MutableList<String>

    init {
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->

            if (isGranted) {
                checkNext()
            } else {
                onResult(false)
            }
        }
    }

    fun ask() {
        curr = ArrayList(permissions) // make a copy
        checkNext()
    }

    private fun checkNext() {
        if (curr.isEmpty()) {
            onResult(true)
        } else {
            val p0 = curr.first()
            curr.removeAt(0)
            launcher.launch(p0)
        }
    }
}