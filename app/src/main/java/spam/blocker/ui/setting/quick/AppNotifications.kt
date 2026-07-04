package spam.blocker.ui.setting.quick

import android.content.Context
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.service.resetNotificationScreeningCache
import spam.blocker.ui.M
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.Str
import spam.blocker.util.AppInfo
import spam.blocker.util.Util
import spam.blocker.util.Util.isPackageInstalled
import spam.blocker.util.spf

private fun defaultAppsPinnedFirst(ctx: Context): List<String> {
    val smsPkg = Telephony.Sms.getDefaultSmsPackage(ctx)
    val dialerPkg = (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
        ?.defaultDialerPackage

    return listOfNotNull(dialerPkg, smsPkg)
}

// Clear packages that are no longer installed
private fun clearUninstalledApps(ctx: Context) {
    val spf = spf.AppNotifications(ctx)

    val cleared = spf.getList().filter {
        isPackageInstalled(ctx, it)
    }
    spf.setList(cleared)
}

// The app-picker for choosing which apps' notifications get screened, rendered as
// a compact icon row (or a "Choose" button when empty) to sit to the left of the
// "Notifications" toggle switch in the Screening section.
@Composable
fun AppNotificationsPicker(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val spf = spf.AppNotifications(ctx)

    val appsPopupTrigger = remember { mutableStateOf(false) }

    val enabledPkgNames = remember {
        mutableStateListOf<String>().apply {
            clearUninstalledApps(ctx)
            addAll(spf.getList())
        }
    }

    PopupChooseApps(
        popupTrigger = appsPopupTrigger,
        finder = { pkgName ->
            if (enabledPkgNames.contains(pkgName)) true else null
        },
        onCheckChange = { pkgName, isChecked ->
            if (isChecked) {
                spf.addPackage(pkgName)
                enabledPkgNames.add(pkgName)
            } else {
                spf.removePackage(pkgName)
                enabledPkgNames.remove(pkgName)
            }
            resetNotificationScreeningCache()
        },
        appListProvider = Util::listAllApps,
        pinnedPackages = defaultAppsPinnedFirst(ctx),
    )

    if (enabledPkgNames.isEmpty()) {
        GreyButton(Str(R.string.choose), modifier = modifier) {
            appsPopupTrigger.value = true
        }
    } else {
        LazyRow(
            modifier = modifier
                .padding(0.dp)
                .clickable {
                    appsPopupTrigger.value = true
                },
        ) {
            items(enabledPkgNames) { pkgName ->
                DrawableImage(
                    AppInfo.fromPackage(ctx, pkgName).icon,
                    modifier = M
                        .size(20.dp)
                        .padding(horizontal = 2.dp)
                )
            }
        }
    }
}
