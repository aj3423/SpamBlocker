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
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.provider.CallLog.Calls
import android.provider.Telephony.Sms
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Util.doOnce

open class Permissions {
    companion object {
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
            ) == PackageManager.PERMISSION_GRANTED
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
    private val ctx: Context,
    private val permissions: List<Permission>,
) {
    // for non-protected permission, e.g.: CALL_LOG
    private lateinit var launcherNormal: ManagedActivityResultLauncher<String, Boolean>

    // for protected permission, e.g.: USAGE_STATS
    private lateinit var launcherProtected: ManagedActivityResultLauncher<Intent, ActivityResult>


    // final callback
    private lateinit var onResult: (Boolean) -> Unit

    private lateinit var currList: MutableList<Permission>
    private lateinit var curr: Permission

    private lateinit var popupTrigger: MutableState<Boolean>

    @Composable
    fun Compose() {
        popupTrigger = remember { mutableStateOf(false) }
        if (popupTrigger.value) {
            PopupDialog(
                trigger = popupTrigger,
                content = {
                    Text(curr.prompt!!, color = LocalPalette.current.textGrey)
                },
                buttons = {
                    GreyButton("cancel") {
                        popupTrigger.value = false
                        onResult(false)
                    }
                    Spacer(modifier = M.width(10.dp))

                    StrokeButton("ok", Teal200) {
                        popupTrigger.value = false
                        handle()
                    }
                }
            )
        }

        launcherNormal = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted || curr.isOptional) {
                checkNext()
            } else {
                onResult(false)
            }
        }

        launcherProtected = rememberLauncherForActivityResult(
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

    fun ask(onResult: (Boolean) -> Unit) {
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
            val isFirstTime = doOnce(ctx, "ask_once_${curr.name}") {
                handleCurrPermission()
            }
            if (!isFirstTime) {
                checkNext()
            }
            return
        }

        handleCurrPermission()
    }

    private fun handleCurrPermission() {
        if (curr.prompt == null) {
            handle()
        } else { // show prompt dialog
            popupTrigger.value = true
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

