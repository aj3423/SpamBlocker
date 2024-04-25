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
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import spam.blocker.def.Def
import android.os.Process

class Permission {
    companion object {

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
        fun requestSmsPermissions(activity: AppCompatActivity) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 0)
        }
        fun promptSetAsDefaultCallScreeningApp(activity: AppCompatActivity) : ()->Unit{
            val roleManager = activity.getSystemService(AppCompatActivity.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            val startForRequestRoleResult = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result: androidx.activity.result.ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    bindCallScreeningService(activity)
                }
            }
            return fun()  {
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
            activity.bindService(mCallServiceIntent, mServiceConnection, AppCompatActivity.BIND_AUTO_CREATE)
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
        fun isSmsPermissionGranted(ctx: Context): Boolean {
            return isPermissionGranted(ctx, Manifest.permission.RECEIVE_SMS)
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
        fun listUsedAppWithinXSecond(ctx: Context, sec: Int): List<String>{
            val ret = mutableListOf<String>()

            val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val currentTime = System.currentTimeMillis()
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
    }
}