package spam.blocker.util

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Process
import android.provider.CallLog.Calls
import android.provider.Settings
import android.provider.Telephony.Sms
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.SharedPref.SharedPref

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

        fun isCallScreeningEnabled(ctx: Context): Boolean {
            val roleManager = ctx.getSystemService(Context.ROLE_SERVICE) as RoleManager
            return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }

        // must be initialized in MainActivity
        lateinit var askAsScreeningApp: (((isGranted: Boolean)->Unit)?) -> Unit

        fun initSetAsCallScreeningApp(activity: AppCompatActivity) {
            var callback: ((isGranted: Boolean)->Unit)? = null

            val roleManager = activity.getSystemService(AppCompatActivity.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            val startForRequestRoleResult = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result: androidx.activity.result.ActivityResult ->
                val granted = result.resultCode == Activity.RESULT_OK
                if (granted) {
                    bindCallScreeningService(activity)
                }
                callback?.invoke(granted)
            }
            askAsScreeningApp = fun(cb: ((isGranted: Boolean)->Unit)?) {
                callback = cb
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
//            Log.d(Def.TAG, "$permission Granted: $ret")
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

        fun isProtectedPermissionGranted(ctx: Context, permission: String): Boolean {
            val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                permission,
                Process.myUid(),
                ctx.packageName
            )
            return (mode == AppOpsManager.MODE_ALLOWED)
        }
        fun isUsagePermissionGranted(ctx: Context): Boolean {
            return isProtectedPermissionGranted(ctx, AppOpsManager.OPSTR_GET_USAGE_STATS)
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
                    rawNumber
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("+", "")
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
                    rawNumber
                        .replace(" ", "")
                        .replace("-", "")
                        .replace("+", "")
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

abstract class PermissionChecker {
    abstract fun isGranted(ctx: Context): Boolean
}
open class Permission(
    val name: String,
    val isOptional: Boolean = false,
    val prompt: String? = null // show a prompt dialog before asking for permission
) : PermissionChecker() {
    override fun isGranted(ctx: Context): Boolean {
        return Permissions.isPermissionGranted(ctx, name)
    }
}

class ProtectedPermission(
    name: String,
    isOptional: Boolean = false,
    prompt: String? = null,

    // intent for navigating to system setting page
    val intentName: String? = null,
) : Permission(name, isOptional, prompt) {

    override fun isGranted(ctx: Context): Boolean {
        return Permissions.isProtectedPermissionGranted(ctx, name)
    }
}

/*
    Convenient class for asking for multiple permissions,

    callback is triggered with param:
    - true: all required permissions are granted
    - false: at least one of required permissions failed.

    Must be created during the creation of the fragment
 */
class PermissionChain(
    private val fragment: Fragment,
    private val permissions: List<Permission>
) {
    // for non-protected permission
    private val launcherNormal: ActivityResultLauncher<String>
    private val launcherProtected: ActivityResultLauncher<Intent>


    // final callback
    private lateinit var onResult: (Boolean) -> Unit

    private lateinit var currList: MutableList<Permission>
    private lateinit var curr: Permission
    private val ctx = fragment.requireContext()

    init {
        launcherNormal = fragment.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted || curr.isOptional) {
                checkNext()
            } else {
                onResult(false)
            }
        }

        launcherProtected = fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { _ ->
            val isGranted = Permissions.isProtectedPermissionGranted(ctx, curr.name)
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
            return
        }

        curr = currList.first()
        currList.removeAt(0)

        if (curr.isGranted(ctx)) { // already granted
            checkNext()
            return
        }

        if (curr.isOptional) {
            val spf = SharedPref(ctx)
            val attr = "ask_once_${curr.name}"
            val alreadyAsked = spf.readBoolean(attr, false)

            if (alreadyAsked) {
                checkNext()
            } else {
                spf.writeBoolean(attr, true)
                handleCurrPermission()
            }
            return
        }

        handleCurrPermission()
    }
    private fun handleCurrPermission() {
        if (curr.prompt == null) {
            handle()
        } else { // show prompt dialog
            AlertDialog.Builder(ctx)
                .setMessage(curr.prompt)
                .setPositiveButton(ctx.resources.getString(R.string.ok)) { dialog, _ ->
                    dialog.dismiss()

                    handle()
                }
                .setNegativeButton(ctx.resources.getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                    onResult(false)
                }
                .show()
        }
    }
    private fun handle() {
        if (curr is ProtectedPermission) {
            val protected = curr as ProtectedPermission
            launcherProtected.launch(Intent(protected.intentName))
        } else {
            launcherNormal.launch(curr.name)
        }
    }
}