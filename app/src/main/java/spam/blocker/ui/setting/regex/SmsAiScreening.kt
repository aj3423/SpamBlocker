package spam.blocker.ui.setting.regex

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.SmsAiCategory
import spam.blocker.db.SmsAiCategoryTable
import spam.blocker.def.Def
import spam.blocker.service.ai.SmsAiEngine
import spam.blocker.service.ai.SmsAiModels
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.SnackBar
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda1
import spam.blocker.util.Launcher
import spam.blocker.util.spf

object SmsAiViewModel {
    val records = mutableStateListOf<SmsAiCategory>()
    val listCollapsed = mutableStateOf(false)
    val isEnabled = mutableStateOf(false)
    val modelDownloaded = mutableStateOf(false)
    val table = SmsAiCategoryTable

    fun reloadDb(ctx: Context) {
        table.ensureDefaults(ctx)
        records.clear()
        records.addAll(table.listAll(ctx))
    }

    fun toggleCollapse(ctx: Context) {
        if (records.isEmpty() && !listCollapsed.value) {
            return
        }
        listCollapsed.value = !listCollapsed.value
        spf.SmsAi(ctx).isCollapsed = listCollapsed.value
    }

    fun reloadDbAndOptions(ctx: Context) {
        val spf = spf.SmsAi(ctx)
        listCollapsed.value = spf.isCollapsed
        isEnabled.value = spf.isEnabled
        modelDownloaded.value = SmsAiEngine.isDownloaded(ctx, SmsAiModels.byId(spf.modelId))
        reloadDb(ctx)
    }
}

@Composable
fun SmsAiSettingsDialog(
    trigger: MutableState<Boolean>,
) {
    if (!trigger.value) {
        return
    }

    val C = G.palette
    val ctx = LocalContext.current
    val spf = spf.SmsAi(ctx)
    val scope = rememberCoroutineScope()
    val vm = SmsAiViewModel

    var prompt by rememberSaveable { mutableStateOf(spf.prompt.ifEmpty { Def.DEFAULT_SMS_AI_PROMPT }) }
    var hfToken by rememberSaveable { mutableStateOf(spf.hfToken) }
    var modelId by rememberSaveable { mutableStateOf(SmsAiModels.byId(spf.modelId).id) }

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("") }
    val resetConfirm = remember { mutableStateOf(false) }

    val model = SmsAiModels.byId(modelId)

    fun refreshStatus() {
        val selected = SmsAiModels.byId(spf.modelId)
        val downloaded = SmsAiEngine.isDownloaded(ctx, selected)
        vm.modelDownloaded.value = downloaded
        status = when {
            !SmsAiEngine.isAbiSupported() -> ctx.getString(R.string.sms_ai_unsupported_abi)
            downloaded -> ctx.getString(R.string.sms_ai_model_ready)
            else -> ctx.getString(R.string.sms_ai_model_missing)
        }
    }
    LaunchedEffect(modelId) { refreshStatus() }

    PopupDialog(
        trigger = resetConfirm,
        buttons = {
            StrokeButton(label = Str(R.string.reset), color = C.error) {
                resetConfirm.value = false
                SmsAiCategoryTable.resetToDefaults(ctx)
                prompt = Def.DEFAULT_SMS_AI_PROMPT
                vm.reloadDb(ctx)
            }
        },
    ) {
        GreyText(Str(R.string.confirm_to_reset))
    }

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 340, maxWidthDp = 600),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StrInputBox(
                    text = hfToken,
                    label = { Text(Str(R.string.sms_ai_hf_token)) },
                    leadingIconId = R.drawable.ic_lock,
                    singleLine = true,
                    helpTooltip = Str(R.string.help_sms_ai_hf_token),
                    onValueChange = {
                        hfToken = it.trim()
                        spf.hfToken = hfToken
                    },
                )
                RowVCenterSpaced(8) {
                    StrokeButton(
                        label = Str(R.string.get_a_new_token),
                        color = C.infoBlue,
                    ) {
                        Launcher.openUrl(ctx, "https://huggingface.co/settings/tokens")
                    }
                    StrokeButton(
                        label = Str(R.string.sms_ai_accept_license),
                        color = C.textGrey,
                    ) {
                        Launcher.openUrl(ctx, model.licenseUrl)
                    }
                }

                StrInputBox(
                    text = prompt,
                    label = { Text(Str(R.string.sms_ai_prompt)) },
                    leadingIconId = R.drawable.ic_note,
                    maxLines = 8,
                    helpTooltip = Str(R.string.help_sms_ai_prompt),
                    onValueChange = {
                        prompt = it
                        spf.prompt = it
                    },
                )

                LabeledRow(labelId = R.string.reset) {
                    StrokeButton(
                        label = Str(R.string.sms_ai_reset_defaults),
                        color = C.error,
                    ) {
                        resetConfirm.value = true
                    }
                }

                LabeledRow(
                    labelId = R.string.sms_ai_model,
                    helpTooltip = Str(R.string.help_sms_ai_model),
                ) {
                    val selectedIndex = SmsAiModels.all.indexOfFirst { it.id == modelId }
                        .coerceAtLeast(0)
                    ComboBox(
                        items = SmsAiModels.all.map { m ->
                            LabelItem(
                                id = m.id,
                                label = m.label,
                                onClick = {
                                    if (spf.modelId != m.id) {
                                        SmsAiEngine.close()
                                        spf.modelId = m.id
                                        modelId = m.id
                                        if (vm.isEnabled.value) {
                                            if (SmsAiEngine.isDownloaded(ctx, m)) {
                                                SmsAiEngine.preload(ctx)
                                            } else {
                                                spf.isEnabled = false
                                                vm.isEnabled.value = false
                                            }
                                        }
                                        refreshStatus()
                                    }
                                },
                            )
                        },
                        selected = selectedIndex,
                        enabled = !downloading,
                    )
                }

                val downloaded = SmsAiEngine.isDownloaded(ctx, model)

                RowVCenterSpaced(8) {
                    if (!downloaded) {
                        StrokeButton(
                            label = Str(R.string.download),
                            color = C.infoBlue,
                            enabled = !downloading,
                        ) {
                            downloading = true
                            progress = 0f
                            scope.launch {
                                val err = withContext(IO) {
                                    SmsAiEngine.download(ctx, model) { received, total ->
                                        if (total > 0) {
                                            val p = (received.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                            scope.launch(Dispatchers.Main) {
                                                progress = p
                                            }
                                        }
                                    }
                                }
                                downloading = false
                                if (err == null) {
                                    refreshStatus()
                                    if (vm.isEnabled.value) {
                                        SmsAiEngine.preload(ctx)
                                    }
                                } else {
                                    status = err
                                }
                            }
                        }
                    } else {
                        StrokeButton(
                            label = Str(R.string.delete),
                            color = C.error,
                            enabled = !downloading,
                        ) {
                            SmsAiEngine.deleteModel(ctx, model)
                            SmsAiEngine.close()
                            spf.isEnabled = false
                            vm.isEnabled.value = false
                            refreshStatus()
                        }
                    }
                }

                if (downloading) {
                    if (progress <= 0f) {
                        LinearProgressIndicator(modifier = M.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = M.fillMaxWidth(),
                        )
                    }
                }

                if (status.isNotEmpty()) {
                    GreyText(status)
                }
            }
        }
    )
}

@Composable
fun SmsAiCategoryDialog(
    trigger: MutableState<Boolean>,
    initRecord: SmsAiCategory = SmsAiCategory(),
    onSave: Lambda1<SmsAiCategory>,
) {
    if (!trigger.value) {
        return
    }

    val C = G.palette

    val id by rememberSaveable { mutableLongStateOf(initRecord.id) }
    var name by rememberSaveable { mutableStateOf(initRecord.name) }
    var description by rememberSaveable { mutableStateOf(initRecord.description) }
    var allowEnabled by rememberSaveable { mutableStateOf(initRecord.allowEnabled) }
    var allowPriority by rememberSaveable { mutableIntStateOf(initRecord.allowPriority) }
    var blockEnabled by rememberSaveable { mutableStateOf(initRecord.blockEnabled) }
    var blockPriority by rememberSaveable { mutableIntStateOf(initRecord.blockPriority) }

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 340, maxWidthDp = 600),
        buttons = {
            StrokeButton(
                label = Str(R.string.save),
                color = if (name.isBlank()) C.disabled else C.teal200,
                enabled = name.isNotBlank(),
            ) {
                trigger.value = false
                onSave(
                    SmsAiCategory(
                        id = id,
                        name = name.trim(),
                        description = description.trim(),
                        allowEnabled = allowEnabled,
                        allowPriority = allowPriority,
                        blockEnabled = blockEnabled,
                        blockPriority = blockPriority,
                    )
                )
            }
        },
        content = {
            Column {
                StrInputBox(
                    text = name,
                    label = { Text(Str(R.string.sms_ai_category_name)) },
                    leadingIconId = R.drawable.ic_category,
                    singleLine = true,
                    onValueChange = { name = it },
                )

                StrInputBox(
                    text = description,
                    label = { Text(Str(R.string.sms_ai_category_description)) },
                    leadingIconId = R.drawable.ic_note,
                    maxLines = 3,
                    helpTooltip = Str(R.string.help_sms_ai_category_description),
                    onValueChange = { description = it },
                )

                Section(
                    title = Str(R.string.allow),
                    bgColor = C.dialogBg,
                ) {
                    Column {
                        LabeledRow(R.string.allow) {
                            SwitchBox(allowEnabled) { isTurningOn ->
                                allowEnabled = isTurningOn
                                if (isTurningOn) {
                                    blockEnabled = false
                                }
                            }
                        }
                        AnimatedVisibleV(allowEnabled) {
                            PriorityBox(allowPriority) { newValue, hasError ->
                                if (!hasError && newValue != null) {
                                    allowPriority = newValue
                                }
                            }
                        }
                    }
                }

                Section(
                    title = Str(R.string.block),
                    bgColor = C.dialogBg,
                ) {
                    Column {
                        LabeledRow(R.string.block) {
                            SwitchBox(blockEnabled) { isTurningOn ->
                                blockEnabled = isTurningOn
                                if (isTurningOn) {
                                    allowEnabled = false
                                }
                            }
                        }
                        AnimatedVisibleV(blockEnabled) {
                            PriorityBox(blockPriority) { newValue, hasError ->
                                if (!hasError && newValue != null) {
                                    blockPriority = newValue
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun SmsAiCard(
    record: SmsAiCategory,
    modifier: Modifier = Modifier,
) {
    val C = G.palette

    OutlineCard {
        RowVCenterSpaced(
            space = 10,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(modifier = M.weight(1f)) {
                Text(
                    text = record.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (record.isActive()) {
                        if (record.allowEnabled) C.success else C.error
                    } else {
                        C.textGrey
                    },
                    overflow = TextOverflow.Ellipsis,
                )
                if (record.description.isNotEmpty()) {
                    GreyLabel(
                        text = record.description,
                        modifier = M.padding(top = 2.dp),
                    )
                }
            }
            RowVCenterSpaced(6) {
                if (record.allowEnabled) {
                    GreyIcon16(R.drawable.ic_check_green)
                    GreyLabel("${record.allowPriority}")
                }
                if (record.blockEnabled) {
                    GreyIcon16(R.drawable.ic_fail_red)
                    GreyLabel("${record.blockPriority}")
                }
            }
        }
    }
}

@Composable
fun SmsAiHeader() {
    val C = G.palette
    val ctx = LocalContext.current
    val vm = SmsAiViewModel
    val spf = spf.SmsAi(ctx)

    val addTrigger = rememberSaveable { mutableStateOf(false) }
    val settingsTrigger = rememberSaveable { mutableStateOf(false) }
    val needModelTrigger = remember { mutableStateOf(false) }
    val abiTrigger = remember { mutableStateOf(false) }

    SmsAiSettingsDialog(settingsTrigger)

    PopupDialog(trigger = needModelTrigger) {
        GreyText(Str(R.string.sms_ai_model_missing))
    }
    PopupDialog(trigger = abiTrigger) {
        GreyText(Str(R.string.sms_ai_unsupported_abi))
    }

    if (addTrigger.value) {
        SmsAiCategoryDialog(
            trigger = addTrigger,
            initRecord = SmsAiCategory(),
            onSave = { newRecord ->
                vm.table.addNew(ctx, newRecord)
                vm.reloadDb(ctx)
            }
        )
    }

    LabeledRow(
        labelId = R.string.sms_ai_screening,
        helpTooltip = Str(R.string.help_sms_ai_screening),
        isCollapsed = vm.listCollapsed.value,
        toggleCollapse = { vm.toggleCollapse(ctx) },
    ) {
        RowVCenterSpaced(8) {
            StrokeButton(
                color = C.textGrey,
                icon = { GreyIcon20(R.drawable.ic_settings) },
            ) {
                settingsTrigger.value = true
            }
            StrokeButton(
                label = Str(R.string.new_),
                color = C.infoBlue,
            ) {
                addTrigger.value = true
            }
            SwitchBox(vm.isEnabled.value) { turningOn ->
                if (turningOn) {
                    if (!SmsAiEngine.isAbiSupported()) {
                        abiTrigger.value = true
                        return@SwitchBox
                    }
                    if (!vm.modelDownloaded.value) {
                        needModelTrigger.value = true
                        return@SwitchBox
                    }
                    spf.isEnabled = true
                    vm.isEnabled.value = true
                    SmsAiEngine.preload(ctx)
                } else {
                    spf.isEnabled = false
                    vm.isEnabled.value = false
                    SmsAiEngine.close()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmsAiList() {
    val ctx = LocalContext.current
    val vm = SmsAiViewModel
    val coroutineScope = rememberCoroutineScope()

    val editTrigger = rememberSaveable { mutableStateOf(false) }
    var clickedIndex by rememberSaveable { mutableIntStateOf(-1) }

    if (editTrigger.value && clickedIndex in vm.records.indices) {
        SmsAiCategoryDialog(
            trigger = editTrigger,
            initRecord = vm.records[clickedIndex],
            onSave = { updatedRecord ->
                vm.table.updateById(ctx, updatedRecord.id, updatedRecord)
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
                            val i = vm.records.indexOfFirst { it.id == rec.id }
                            if (i < 0) return@SwipeInfo
                            val removed = vm.records[i]
                            vm.table.deleteById(ctx, removed.id)
                            vm.records.removeAt(i)
                            SnackBar.show(
                                coroutineScope,
                                removed.name,
                                ctx.getString(R.string.undelete),
                            ) {
                                vm.table.addWithId(ctx, removed)
                                vm.records.add(i, removed)
                            }
                        }
                    )
                ) {
                    SmsAiCard(
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
