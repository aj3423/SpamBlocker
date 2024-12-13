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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.AppInfo
import spam.blocker.util.Permissions
import spam.blocker.util.Util
import spam.blocker.util.spf
import spam.blocker.util.spf.MeetingAppInfo


@Composable
private fun PopupMeetingConfig(
    popupTrigger: MutableState<Boolean>,
    priority: MutableState<Int>,
) {
    val ctx = LocalContext.current

    // popup for priority
    PopupDialog(
        trigger = popupTrigger,
        content = {
            NumberInputBox(
                intValue = priority.value,
                onValueChange = { newValue, hasError ->
                    if (!hasError) {
                        priority.value = newValue!!
                        spf.MeetingMode(ctx).setPriority(newValue)
                    }
                },
                label = { Text(Str(R.string.priority)) },
                leadingIconId = R.drawable.ic_priority,
            )
        })
}


@Composable
fun MeetingMode() {
    val ctx = LocalContext.current
    val spf = spf.MeetingMode(ctx)

    val priority = remember { mutableIntStateOf(spf.getPriority()) }

    val buttonPopupTrigger = rememberSaveable { mutableStateOf(false) }
    val appsPopupTrigger = rememberSaveable { mutableStateOf(false) }

    val enabledAppInfos = remember {
        mutableStateListOf<MeetingAppInfo>()
    }

    SideEffect {
        clearUninstalledMeetingApps(ctx)
        enabledAppInfos.clear()
        enabledAppInfos.addAll(spf.getList())
    }

    PopupChooseApps(
        popupTrigger = appsPopupTrigger,
        finder = { pkgName ->
            enabledAppInfos.find { it.pkgName == pkgName }
        },
        extra = { appInfo ->
            if (appInfo != null) {
                val appInfoTrigger = rememberSaveable { mutableStateOf(false) }

                var text by remember {
                    mutableStateOf(
                        appInfo.exclusions.joinToString(",")
                    )
                }

                PopupDialog(
                    trigger = appInfoTrigger,
                    onDismiss = {
                        val pkgName = appInfo.pkgName
                        val exclusions = text.split(",").filter { it.isNotEmpty() }

                        val i = enabledAppInfos.indexOfFirst { it.pkgName == pkgName }

                        // 1. update gui
                        enabledAppInfos[i] =
                            MeetingAppInfo(pkgName, exclusions)

                        // 2. save to SharedPref
                        spf.setList(enabledAppInfos)
                    }
                ) {
                    StrInputBox(
                        text = text,
                        label = { Text(Str(R.string.exclude_services)) },
                        helpTooltip = Str(R.string.help_exclude_bg_service),
                        onValueChange = { text = it }
                    )

                    // Label "Running Foreground Services"
                    Text(Str(R.string.running_services), color = SkyBlue)

                    // The list
                    val services = remember { mutableStateListOf<String>() }
                    services.forEach { serviceName ->
                        GreyLabel(
                            serviceName,
                            modifier = M.clickable { // click service name to append
                                if (!text.contains(serviceName)) {
                                    text = if (text.isEmpty()) serviceName else "$text,$serviceName"
                                }
                            }
                        )
                    }

                    val coroutineScope = rememberCoroutineScope()
                    DisposableEffect(true) {
                        val job = coroutineScope.launch {
                            withContext(IO) {
                                while (true) {

                                    val eventsMap = Permissions.getAppsEvents(
                                        ctx,
                                        listOf(appInfo.pkgName).toSet()
                                    )

                                    services.clear()
                                    services.addAll(
                                        Permissions.listRunningForegroundServiceNames(
                                            appEvents = eventsMap[appInfo.pkgName],
                                        )
                                    )
                                    delay(1000) // Delay for 1 second
                                }
                            }
                        }
                        onDispose {
                            job.cancel()
                        }
                    }
                }

                if (appInfo.exclusions.isEmpty()) {
                    GreyIcon16(
                        iconId = R.drawable.ic_exclude,
                        modifier = M
                            .clickable {
                                appInfoTrigger.value = true
                            }
                    )
                } else {
                    GreyButton("${appInfo.exclusions.size}") {
                        appInfoTrigger.value = true
                    }
                }
            }
        },
        onCheckChange = { pkgName, isChecked ->
            if (isChecked) {
                // 1. add to SharedPref
                spf.addPackage(pkgName)

                // 2. trigger recompose
                enabledAppInfos.add(MeetingAppInfo(pkgName))
            } else {
                // 1. remove from SharedPref
                spf.removePackage(pkgName)

                // 2. trigger recompose
                enabledAppInfos.removeAt(
                    enabledAppInfos.indexOfFirst { it.pkgName == pkgName }
                )
            }
        }
    )
    PopupMeetingConfig(popupTrigger = buttonPopupTrigger, priority = priority)

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
                if (enabledAppInfos.isNotEmpty() && Permissions.isUsagePermissionGranted(ctx)) {
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
                    items(enabledAppInfos) {
                        DrawableImage(
                            AppInfo.fromPackage(ctx, it.pkgName).icon,
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
    val spf = spf.MeetingMode(ctx)

    val cleared = spf.getList().filter {
        Util.isPackageInstalled(ctx, it.pkgName)
    }
    spf.setList(cleared)
}
