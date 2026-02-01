package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.service.resetPushAlertCache
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.quick.PopupChooseApps
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RegexInputBox
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.AppIcon
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

object PushAlertViewModel {
    val records = mutableStateListOf<PushAlertRecord>()
    val listCollapsed = mutableStateOf(false)
    val table = PushAlertTable

    fun reloadDb(ctx: Context) {
        records.clear()
        var all = table.listAll(ctx)
        records.addAll(all)
    }

    fun toggleCollapse(ctx: Context) {
        // don't collapse if it's empty
        if (records.isEmpty() && !listCollapsed.value) {
            return
        }
        listCollapsed.value = !listCollapsed.value
        val spf = spf.PushAlert(ctx)
        spf.isCollapsed = listCollapsed.value
    }

//    fun reloadOptions(ctx: Context) {
//        val spf = spf.PushAlert(ctx)
//    }

    fun reloadDbAndOptions(ctx: Context) {
//        reloadOptions(ctx)
        reloadDb(ctx)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PushAlertEditDialog(
    trigger: MutableState<Boolean>,
    initRecord: PushAlertRecord = PushAlertRecord(), // use default when "Add" new rule
    onSave: Lambda1<PushAlertRecord>,
) {
    if (!trigger.value) {
        return
    }

    val ctx = LocalContext.current

    // id
    val id by rememberSaveable { mutableLongStateOf(initRecord.id) }

    // Enabled
    var enabled by rememberSaveable { mutableStateOf(initRecord.enabled) }

    // Body Regex
    var body by rememberSaveable { mutableStateOf(initRecord.body) }
    val bodyFlags = rememberSaveable { mutableIntStateOf(initRecord.bodyFlags) }

    // Package Name
    var pkgName by rememberSaveable { mutableStateOf(initRecord.pkgName) }
    val chooseAppTrigger = rememberSaveable { mutableStateOf(false) }

    // Duration
    var duration by rememberSaveable { mutableIntStateOf(initRecord.duration) }

    // Choose App Dialog
    PopupChooseApps(
        popupTrigger = chooseAppTrigger,
        finder = { pkg ->
            if (pkgName == pkg) true else null
        },
        onCheckChange = { pkg, isChecked ->
            pkgName = if (isChecked) pkg else ""
            chooseAppTrigger.value = false
        },
    )

    // Config Dialog
    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(percentage = 0.9f, minWidth = 340, maxWidth = 600),
        buttons = {
            StrokeButton(
                label = Str(R.string.save),
                color = Teal200,
                onClick = {
                    G.permissionChain.ask(
                        ctx,
                        listOf(
                            // The battery optimization can be either `Unrestricted` or `Optimized`,
                            //  but not `Restricted`, otherwise it would stop working after a reboot.
                            // The `isOptional = true` here doesn't mean this permission is optional,
                            //  it means it can be manually set to `Optimized` by the user.
                            PermissionWrapper(Permission.batteryUnRestricted, isOptional = true),
                            PermissionWrapper(Permission.notificationAccess)
                        )
                    ) { granted ->
                        if (granted) {
                            trigger.value = false

                            onSave(
                                PushAlertRecord(
                                    id = id,
                                    enabled = enabled,
                                    pkgName = pkgName,
                                    body = body,
                                    bodyFlags = bodyFlags.intValue,
                                    duration = duration,
                                )
                            )

                            // Clear the cache in service.
                            resetPushAlertCache()
                        }
                    }
                }
            )
        },
        content = {
            Column {
                // enable
                LabeledRow(R.string.enable) {
                    SwitchBox(checked = enabled, onCheckedChange = { isOn ->
                        enabled = isOn
                    })
                }

                // App Package
                LabeledRow(R.string.app) {
                    if (pkgName.isEmpty()) {
                        GreyButton(Str(R.string.choose)) {
                            chooseAppTrigger.value = true
                        }
                    } else {
                        AppIcon(pkgName, modifier = M.clickable {
                            chooseAppTrigger.value = true
                        })
                    }
                }

                // body
                RegexInputBox(
                    label = {
                        Text(Str(R.string.notification_content))
                    },
                    regexStr = body,
                    regexFlags = bodyFlags,
                    onRegexStrChange = { newVal, hasErr ->
                        if (!hasErr)
                            body = newVal
                    },
                    onFlagsChange = {
                        bodyFlags.intValue = it
                    },
                    testable = true,
                    leadingIcon = {
                        ResIcon(iconId = R.drawable.ic_notification)
                    }
                )

                // Duration
                NumberInputBox(
                    intValue = duration,

                    label = {
                        Text("${Str(R.string.duration)} (${Str(R.string.min)})")
                    },
                    onValueChange = { newVal, hasErr ->
                        if (newVal != null)
                            duration = newVal
                    },
                    leadingIcon = { ResIcon(R.drawable.ic_duration, modifier = M.size(18.dp)) },
                )
            }
        }
    )
}

@Composable
fun PushAlertHeader() {
    val ctx = LocalContext.current
    val vm = PushAlertViewModel

    val addTrigger = rememberSaveable { mutableStateOf(false) }

    if (addTrigger.value) {
        PushAlertEditDialog(
            trigger = addTrigger,
            initRecord = PushAlertRecord(),
            onSave = { newRecord ->
                // 1. add to db
                vm.table.add(ctx, newRecord)

                // 2. reload from db
                vm.reloadDb(ctx)
            }
        )
    }

    LabeledRow(
        labelId = R.string.push_alert,
        helpTooltip = Str(R.string.help_push_alert),
        isCollapsed = vm.listCollapsed.value,
        toggleCollapse = { vm.toggleCollapse(ctx) },
    ) {
        StrokeButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            onClick = {
                addTrigger.value = true
            }
        )
    }
}


@Composable
fun PushAlertCard(
    record: PushAlertRecord,
    modifier: Modifier,
) {
    val C = LocalPalette.current

    OutlineCard(
        containerBg = MaterialTheme.colorScheme.background
    ) {
        RowVCenterSpaced(
            space = 10,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = M.weight(1f)
            ) {
                RowVCenterSpaced(10) {
                    // App Icon
                    if (record.pkgName.isNotEmpty()) {
                        AppIcon(record.pkgName)
                    }

                    // Body
                    Text(
                        text = record.body,
                        modifier = M.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        color = if (record.enabled && record.isValid()) C.textGreen else C.textGrey,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Duration
                    GreyLabel(
                        text = "${record.duration} ${Str(R.string.min)}",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PushAlertList() {
    val ctx = LocalContext.current

    val vm = PushAlertViewModel
    val coroutineScope = rememberCoroutineScope()

    val editTrigger = rememberSaveable { mutableStateOf(false) }

    var clickedIndex by rememberSaveable { mutableIntStateOf(-1) }

    if (editTrigger.value) {
        PushAlertEditDialog(
            trigger = editTrigger,
            initRecord = vm.records[clickedIndex],
            onSave = { updatedRecord ->
                // 1. update in db
                vm.table.updateById(ctx, updatedRecord.id, updatedRecord)

                // 2. reload UI
                vm.reloadDb(ctx)
            }
        )
    }

    Column(
        modifier = M.nestedScroll(DisableNestedScrolling()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        vm.records.forEachIndexed { index, rec ->
            key(rec.id) {
                LeftDeleteSwipeWrapper(
                    left = SwipeInfo(
                        onSwipe = {
                            val index = vm.records.indexOfFirst { it.id == rec.id }
                            val rec = vm.records[index]

                            // 1. delete from db
                            vm.table.deleteById(ctx, rec.id)
                            // 2. remove from UI
                            vm.records.removeAt(index)
                            // 3. show snackbar
                            SnackBar.show(
                                coroutineScope,
                                rec.body,
                                ctx.getString(R.string.undelete),
                            ) {
                                // 1. add to db
                                vm.table.addWithId(ctx, rec)
                                // 2. add to UI
                                vm.records.add(index, rec)
                            }
                        }
                    )
                ) {
                    PushAlertCard(
                        rec,
                        modifier = M.clickable {
                            clickedIndex = index
                            editTrigger.value = true
                        }
                    )
                }
            }
        }
    }
}
