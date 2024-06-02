package spam.blocker.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.TELEPHONY_SUBSCRIPTION_SERVICE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.CallLog.Calls
import android.provider.Telephony.Sms
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.Global

open class Permissions {
    companion object {
        fun requestAllManifestPermissions(activity: AppCompatActivity) {
            val permissions = mutableListOf(
                "android.permission.READ_CALL_LOG",
                "android.permission.READ_SMS",
                "android.permission.READ_PHONE_STATE",
                "android.permission.ANSWER_PHONE_CALLS",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.READ_CONTACTS",
                "android.permission.RECEIVE_SMS",
                "android.permission.QUERY_ALL_PACKAGES",
            )

            activity.requestPermissions(permissions.toTypedArray(), 0)
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

        fun isPermissionGranted(ctx: Context, permission: String): Boolean {
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

            return try {
                val cursor = ctx.contentResolver.query(
                    Sms.CONTENT_URI,
                    null,
                    selection.joinToString(" AND "),
                    null,
                    null
                )
                val count = cursor?.count ?: 0

                cursor?.close()
                count
            } catch (e: Exception) {
                0
            }
        }
    }
}

class Permission(
    val name: String,
    val isOptional: Boolean = false,
) { }

/*
    Convenient class for asking multiple permissions,
    execute success callback when all permissions granted,
    execute failure callback when any of them fails.

    Must be created during the creation of the fragment
 */
class PermissionChain(
    private val fragment: Fragment,
    private val permissions: List<Permission>
) {
    private val launcher: ActivityResultLauncher<String>
    private lateinit var onResult: (Boolean) -> Unit

    private lateinit var currList: MutableList<Permission>
    private lateinit var curr: Permission

    init {
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted || curr.isOptional) {
                checkNext()
            } else {
                onResult(false)
            }
        }
    }

    fun ask(onResult: (Boolean)->Unit) {
        this.onResult = onResult
        currList = ArrayList(permissions) // make a copy
        checkNext()
    }

    private fun checkNext() {
        if (currList.isEmpty()) {
            onResult(true)
        } else {
            val p0 = currList.first()
            currList.removeAt(0)

            curr = p0

            if (!p0.isOptional) { // a required permission, just launch the launcher
                launcher.launch(p0.name)
            } else { // optional permission, only ask for the first time
                val spf = Global(fragment.requireContext())
                val attr = "ask_once_${p0.name}"
                val askedAlready = spf.readBoolean(attr, false)
                if (!askedAlready) {
                    spf.writeBoolean(attr, true)
                    launcher.launch(p0.name)
                } else {
                    checkNext()
                }
            }
        }
    }
}