package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import spam.blocker.R

@Immutable
class AppInfo {
    lateinit var pkgName: String
    lateinit var label: String
    lateinit var icon: Drawable

    companion object {
        @SuppressLint("UseCompatLoadingForDrawables")
        fun fromPackage(ctx: Context, packageName: String): AppInfo {
            val ret = AppInfo().apply {
                pkgName = packageName
            }

            val pm = ctx.packageManager

            val applicationInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                ret.label = ""
                ret.icon = ctx.getDrawable(R.drawable.ic_unknown_app_icon)!!
                return ret
            }

            ret.label = pm.getApplicationLabel(applicationInfo) as String

            ret.icon = pm.getApplicationIcon(packageName)

            return ret
        }
    }
}