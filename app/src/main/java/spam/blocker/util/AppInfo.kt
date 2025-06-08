package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.widgets.DrawableImage

@Composable
fun AppIcon(
    pkgName: String,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current

    DrawableImage(
        AppInfo.fromPackage(ctx, pkgName).icon,
        modifier = modifier
            .size(24.dp)
            .padding(start = 2.dp)
    )
}

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