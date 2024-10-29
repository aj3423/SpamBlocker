package spam.blocker.ui.setting.quick

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.AppInfo
import spam.blocker.util.Permissions
import spam.blocker.util.SharedPref.MeetingMode
import spam.blocker.util.Util


@Composable
private fun PopupMeetingConfig(
    ctx: Context,
    popupTrigger: MutableState<Boolean>,
    priority: MutableState<Int>,
) {
    // popup for priority
    PopupDialog(
        trigger = popupTrigger,
        content = {
            NumberInputBox(
                intValue = priority.value,
                onValueChange = { newValue, hasError ->
                    if (!hasError) {
                        priority.value = newValue!!
                        MeetingMode(ctx).setPriority(newValue)
                    }
                },
                label = { Text(Str(R.string.priority)) },
                leadingIconId = R.drawable.ic_priority,
            )
        })
}

@Composable
fun Meeting() {
    val ctx = LocalContext.current
    val spf = MeetingMode(ctx)

    val priority = remember { mutableIntStateOf(spf.getPriority()) }

    val buttonPopupTrigger = rememberSaveable { mutableStateOf(false) }
    val appsPopupTrigger = rememberSaveable { mutableStateOf(false) }

    val enabledPkgs = remember {
        mutableStateListOf<String>()
    }

    SideEffect {
        clearUninstalledMeetingApps(ctx)
        enabledPkgs.clear()
        enabledPkgs.addAll(spf.getList())
    }

    PopupChooseApps(
        ctx = ctx,
        popupTrigger = appsPopupTrigger,
        finder = { pkgName ->
            enabledPkgs.find { it == pkgName }
        },
        onCheckChange = { pkgName, isChecked ->
            if (isChecked) {
                // 1. add to SharedPref
                MeetingMode(ctx).addPackage(pkgName)

                // 2. trigger recompose
                enabledPkgs.add(pkgName)
            } else {
                // 1. remove from SharedPref
                MeetingMode(ctx).removePackage(pkgName)

                // 2. trigger recompose
                enabledPkgs.removeAt(
                    enabledPkgs.indexOfFirst { it == pkgName }
                )
            }
        },
    )
    PopupMeetingConfig(ctx = ctx, popupTrigger = buttonPopupTrigger, priority = priority)

    LabeledRow(
        R.string.in_meeting,
        helpTooltipId = R.string.help_meeting_mode,
        content = {
            Row(
                modifier = M.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                // Priority Button
                if (enabledPkgs.isNotEmpty() && Permissions.isUsagePermissionGranted(ctx)) {
                    StrokeButton(
                        label = "${priority.intValue}",
                        color = LightMagenta
                    ) {
                        buttonPopupTrigger.value = true
                    }
                }

                // Recycler list view
                LazyRow(
                    modifier = M
                        .padding(start = 8.dp)
                        .clickable {
                            appsPopupTrigger.value = true
                        },
                ) {
                    items(enabledPkgs) {
                        DrawableImage(
                            AppInfo.fromPackage(ctx, it).icon,
                            modifier = M
                                .size(24.dp)
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }
            AppChooserIcon(popupTrigger = appsPopupTrigger)
        }
    )
}

// Clear uninstalled apps from the meeting apps list
private fun clearUninstalledMeetingApps(ctx: Context) {
    val spf = MeetingMode(ctx)

    val cleared = spf.getList().filter {
        Util.isPackageInstalled(ctx, it)
    }
    spf.setList(cleared)
}
