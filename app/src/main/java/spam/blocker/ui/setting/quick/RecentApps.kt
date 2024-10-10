package spam.blocker.ui.setting.quick

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.SwissCoffee
import spam.blocker.ui.widgets.DrawableImage
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.AppInfo
import spam.blocker.util.AppOpsPermission
import spam.blocker.util.Permissions
import spam.blocker.util.SharedPref.RecentAppInfo
import spam.blocker.util.SharedPref.RecentApps
import spam.blocker.util.Util
import spam.blocker.util.Util.listApps

@Composable
private fun PopupChooseApps(
    ctx: Context,
    popupTrigger: MutableState<Boolean>,
    enabledPkgs: SnapshotStateList<RecentAppInfo>,
) {
    // popup for choosing apps
    PopupDialog(
        trigger = popupTrigger,
        scrollEnabled = false,
        content = {
            var searchFilter by remember { mutableStateOf("") }

            val sortedApps = remember(searchFilter) {
                // 0. get all installed apps that have INTERNET permission
                var all = listApps(ctx)

                // 1. filter by input
                if (searchFilter != "") {
                    all = all.filter {
                        it.pkgName.lowercase().contains(searchFilter, true)
                                || it.label.lowercase().contains(searchFilter, true)
                    }
                }
                // 2. sort by: selected, then by package label
                all.sortedWith(compareBy<AppInfo> { appInfo ->
                    enabledPkgs.find { it.pkgName == appInfo.pkgName } == null
                }.thenBy {
                    it.label
                }).toMutableList()
            }

            Column {
                StrInputBox(
                    text = "",
                    label = { Text(Str(R.string.find_apps)) },
                    leadingIconId = R.drawable.ic_find,
                    onValueChange = {
                        searchFilter = it
                    }
                )
                LazyColumn(modifier = M.padding(top = 4.dp)) {
                    itemsIndexed(sortedApps) { index, it ->
                        val pkgName = it.pkgName
                        val info = enabledPkgs.find { it.pkgName == pkgName }
                        val isEnabled = info != null

                        RowVCenterSpaced(
                            space = 10,
                            modifier = M.height(48.dp),
                        ) {
                            // icon
                            DrawableImage(it.icon, modifier = M.size(24.dp))

                            // label & package
                            Column(modifier = M.weight(1f)) {
                                Text(
                                    it.label,
                                    color = SkyBlue,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                    lineHeight = 14.sp,
                                )

                                Text(
                                    it.pkgName,
                                    color = LocalPalette.current.textGrey,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                )
                            }

                            // duration icon
                            if (info != null) {
                                val popupTrigger1 = rememberSaveable { mutableStateOf(false) }

                                if (popupTrigger1.value) {
                                    PopupDialog(
                                        trigger = popupTrigger1,
                                        content = {
                                            LabeledRow(
                                                labelId = R.string.duration,
                                                helpTooltipId = R.string.help_recent_apps_individual_duration
                                            ) {
                                                NumberInputBox(
                                                    intValue = info.duration,
                                                    allowEmpty = true,
                                                    label = { Text(Str(R.string.min)) },
                                                    leadingIconId = R.drawable.ic_duration,
                                                    onValueChange = { newValue, hasError ->
                                                        val i =
                                                            enabledPkgs.indexOfFirst { it.pkgName == pkgName }

                                                        // 1. update gui
                                                        enabledPkgs[i] =
                                                            RecentAppInfo(pkgName, newValue)

                                                        // 2. save to SharedPref
                                                        RecentApps(ctx).setList(enabledPkgs)
                                                    })
                                            }
                                        }
                                    )
                                }
                                if (info.duration == null) {
                                    GreyIcon16(
                                        iconId = R.drawable.ic_duration,
                                        modifier = M
                                            .clickable {
                                                popupTrigger1.value = true
                                            }
                                    )
                                } else {
                                    GreyButton("${info.duration} ${Str(R.string.min)}") {
                                        popupTrigger1.value = true
                                    }
                                }
                            }

                            // checkbox
                            SwitchBox(checked = isEnabled) { isChecked ->
                                if (isChecked) {
                                    // 1. add to SharedPref
                                    RecentApps(ctx).addPackage(it.pkgName)

                                    // 2. trigger recompose
                                    enabledPkgs.add(RecentAppInfo(pkgName))
                                } else {
                                    // 1. remove from SharedPref
                                    RecentApps(ctx).removePackage(it.pkgName)

                                    // 2. trigger recompose
                                    enabledPkgs.removeAt(
                                        enabledPkgs.indexOfFirst { it.pkgName == pkgName }
                                    )
                                }
                            }
                        }

                        // divider
                        if (index < sortedApps.lastIndex) // don't show for the last item
                            HorizontalDivider(thickness = 1.dp, color = SwissCoffee)
                    }
                }
            }
        })
}

@Composable
private fun PopupConfig(
    ctx: Context,
    popupTrigger: MutableState<Boolean>,
    inXMin: MutableState<Int?>,
) {
    // popup for inXMin
    PopupDialog(
        trigger = popupTrigger,
        content = {
            NumberInputBox(
                intValue = inXMin.value,
                onValueChange = { newValue, hasError ->
                    if (!hasError) {
                        inXMin.value = newValue
                        RecentApps(ctx).setDefaultMin(newValue!!)
                    }
                },
                label = { Text(Str(R.string.within_minutes)) },
                leadingIconId = R.drawable.ic_duration,
            )
        })
}

@Composable
fun RecentApps() {
    val ctx = LocalContext.current
    val spf = RecentApps(ctx)

    val defaultInXMin = remember { mutableStateOf<Int?>(spf.getDefaultMin()) }

    val buttonPopupTrigger = rememberSaveable { mutableStateOf(false) }
    val appsPopupTrigger = rememberSaveable { mutableStateOf(false) }

    val enabledPkgs = remember {
        mutableStateListOf<RecentAppInfo>()
    }

    SideEffect {
        clearUninstalledRecentApps(ctx)
        enabledPkgs.clear()
        enabledPkgs.addAll(spf.getList())
    }

    PopupChooseApps(ctx = ctx, popupTrigger = appsPopupTrigger, enabledPkgs = enabledPkgs)
    PopupConfig(ctx = ctx, popupTrigger = buttonPopupTrigger, inXMin = defaultInXMin)

    LabeledRow(
        R.string.allow_recent_apps,
        helpTooltipId = R.string.help_recent_apps,
        content = {
            Row(
                modifier = M.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (enabledPkgs.isNotEmpty() && Permissions.isUsagePermissionGranted(ctx)) {
                    GreyButton(
                        label = "${defaultInXMin.value} ${Str(R.string.min)}",
                    ) {
                        buttonPopupTrigger.value = true
                    }
                }
                // and the recycler list view
                LazyRow(
                    modifier = M
                        .padding(start = 8.dp)
                        .clickable {
                            appsPopupTrigger.value = true
                        },
                ) {
                    items(enabledPkgs) {
                        DrawableImage(
                            AppInfo.fromPackage(ctx, it.pkgName).icon,
                            modifier = M
                                .size(24.dp)
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }
            ResImage(
                resId = R.drawable.ic_right_arrow,
                color = SkyBlue,
                modifier = M
                    .fillMaxHeight()
                    .padding(start = 4.dp)
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .clickable {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                AppOpsPermission(
                                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                                    intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                                    prompt = ctx.getString(R.string.prompt_go_to_usage_permission_setting)
                                )
                            )
                        ) { granted ->
                            if (granted) {
                                appsPopupTrigger.value = true
                            }
                        }
                    }
            )
        }
    )
}

// Clear uninstalled apps from the recent apps list
private fun clearUninstalledRecentApps(ctx: Context) {
    val spf = RecentApps(ctx)

    val cleared = spf.getList().filter {
        Util.isPackageInstalled(ctx, it.pkgName)
    }
    spf.setList(cleared)
}
