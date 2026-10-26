package spam.blocker.ui.setting.misc

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.config.Category
import spam.blocker.config.CategorySelection
import spam.blocker.config.Configs
import spam.blocker.config.emptyCategorySelection
import spam.blocker.db.Bot
import spam.blocker.db.SpamTable
import spam.blocker.service.bot.FileAction
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.FileChooser
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.InitFile
import spam.blocker.ui.widgets.MIME_GZ
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.A
import spam.blocker.util.FileUtils.readDataFromUri
import spam.blocker.util.FileUtils.writeDataToUri
import spam.blocker.util.Launcher
import spam.blocker.util.Permission
import spam.blocker.util.PermissionType
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFolderAccess
import spam.blocker.ui.widgets.CheckBox
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.ResIcon18
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.util.Algorithm
import spam.blocker.util.BackupUtils
import spam.blocker.util.Lambda
import spam.blocker.util.spf
import spam.blocker.util.toFolderDisplayName
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val Backup_Last_Dir_Tag = "backup_last_dir_tag"

fun missingBotSafUris(bots: List<Bot>) : List<Uri> {
    return bots.flatMap { bot ->
        bot.actions
            .filterIsInstance<FileAction>() // Only keep FileActions
            .mapNotNull { it.uriStr?.toUri() } // Convert to Uri, skip if null
    }
}


@Composable
private fun OperationResultDialog(
    trigger: MutableState<Boolean>,
    succeeded: Boolean,
    successMsgId: Int,
    failMsgId: Int,
    errorStr: String?,
    extraContent: (@Composable () -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val C = G.palette
    PopupDialog(
        trigger = trigger,
        icon = {
            ResIcon(
                iconId = if (succeeded) R.drawable.ic_check_green else R.drawable.ic_fail_red,
                color = if (succeeded) C.success else C.error,
            )
        },
        content = {
            Text(
                Str(if (succeeded) successMsgId else failMsgId),
                color = C.textGrey,
                fontWeight = FontWeight.SemiBold,
            )
            if (!succeeded && !errorStr.isNullOrEmpty()) {
                Text(errorStr, color = C.error)
            }
            if (succeeded && extraContent != null) {
                extraContent()
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun CategoryButtons(
    categories: List<Category>,
    disabledCategories: CategorySelection = emptyCategorySelection,
    onClick: (Category) -> Unit,
) {
    FlowRowSpaced(space = 20, vSpace = 30) {
        categories.forEach { cat ->
            val isDisabled = disabledCategories.contains(cat)
            StrokeButton(
                label = Str(cat.labelId),
                color = if (isDisabled) G.palette.disabled else Color(cat.name.hashCode().toLong() or 0xff808080),
            ) {
                onClick(cat)
            }
        }
    }
}

@Composable
fun ChooseBackupCategoriesDialog(
    trigger: MutableState<Boolean>,
    initialCategories: CategorySelection,
    disabledCategories: CategorySelection = emptyCategorySelection,
    onDismiss: Lambda? = null,
    buttons: @Composable (CategorySelection) -> Unit,
) {
    var categories by retain(initialCategories) { mutableStateOf(initialCategories) }

    // Reset to the latest initial value whenever the dialog opens, otherwise it always shows
    //  the last categories instead of the initialCategories
    LaunchedEffect(trigger.value) {
        if (trigger.value) {
            categories = initialCategories
        }
    }

    PopupDialog(
        trigger,
        buttons = {
            Spacer(modifier = M.height(66.dp))
            buttons(categories)
        },
        onDismiss = onDismiss
    ) {

        Text(Str(R.string.include_settings), color = G.palette.teal200, modifier = M.padding(bottom = 12.dp).align(Alignment.Start))

        // Included Category buttons
        CategoryButtons(categories.allSelected()) { cat ->
            categories = categories.toggle(cat)
        }

        HorizontalDivider(thickness = 1.dp, color = G.palette.disabled, modifier = M.padding(top = 20.dp, bottom = 10.dp))

        Text(Str(R.string.exclude_settings), color = G.palette.textGrey, modifier = M.padding(bottom = 12.dp).align(Alignment.Start))

        // Show "this category is missing from the backup and can't be restored"
        val errorTrigger = retain { mutableStateOf(false) }
        PopupDialog(errorTrigger) {
            Text(Str(R.string.backup_missing_category), color = G.palette.error)
        }

        // Excluded Category buttons
        CategoryButtons(categories.allUnselected(), disabledCategories) { cat ->
            if (disabledCategories.contains(cat))
                errorTrigger.value = true
            else
                categories = categories.toggle(cat)
        }
    }
}

@Composable
private fun EncryptionConfigDialog(
    trigger: MutableState<Boolean>,
    onOk: () -> Unit,
) {
    val ctx = LocalContext.current
    val spfTemporary = remember { spf.Temporary(ctx) }

    var isEncrypted by remember { mutableStateOf(spfTemporary.encryptBackup) }
    val hasSavedKey = remember(trigger.value) { spfTemporary.backupKey.isNotEmpty() }
    var isEditingKey by remember { mutableStateOf(!hasSavedKey) }
    var keyText by remember { mutableStateOf("") }

    LaunchedEffect(trigger.value) {
        if (trigger.value) {
            isEncrypted = spfTemporary.encryptBackup
            val saved = spfTemporary.backupKey.isNotEmpty()
            isEditingKey = !saved
            keyText = ""
        }
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            StrokeButton(Str(R.string.ok), color = G.palette.teal200) {
                spfTemporary.encryptBackup = isEncrypted && keyText.isNotEmpty()
                if (isEncrypted && isEditingKey && keyText.isNotEmpty()) {
                    spfTemporary.backupKey = BackupUtils.hashKey(keyText)
                }
                trigger.value = false
                onOk()
            }
        }
    ) {
        RowVCenterSpaced(space = 8, modifier = M.padding(bottom = 12.dp).align(Alignment.Start)) {
            CheckBox(
                checked = isEncrypted,
                onCheckChange = { isEncrypted = it },
                label = {
                    Text(
                        Str(R.string.encryption),
                        color = G.palette.teal200,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            )
        }

        if (isEncrypted) {
            RowVCenterSpaced(space = 8, modifier = M.padding(top = 8.dp)) {
                StrInputBox(
                    text = if (isEditingKey) keyText else "********",
                    onValueChange = { if (isEditingKey) keyText = it },
                    label = { Text(Str(R.string.key)) },
                    enabled = isEditingKey,
                    modifier = M.weight(1f)
                )
                if (!isEditingKey) {
                    StrokeButton(
                        label = Str(R.string.change),
                        color = G.palette.teal200,
                    ) {
                        isEditingKey = true
                        keyText = ""
                    }
                }
            }
        }
    }
}

@Composable
private fun DecryptKeyDialog(
    trigger: MutableState<Boolean>,
    onOk: (String) -> Unit,
) {
    var keyText by remember { mutableStateOf("") }

    LaunchedEffect(trigger.value) {
        if (trigger.value) {
            keyText = ""
        }
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            StrokeButton(Str(R.string.ok), color = G.palette.teal200) {
                trigger.value = false
                onOk(keyText)
            }
        }
    ) {
        GreyText(Str(R.string.backup_is_encrypted))
        StrInputBox(
            text = keyText,
            onValueChange = { keyText = it },
            label = { Text(Str(R.string.key)) },
        )
    }
}

@Composable
fun ExportButton() {
    val C = G.palette
    val ctx = LocalContext.current
    val coroutine = rememberCoroutineScope()
    val spfTemporary = spf.Temporary(ctx)

    var isEncryptionEnabled by remember { mutableStateOf(spfTemporary.encryptBackup) }

    var succeeded by remember { mutableStateOf(false) }
    var errorStr by remember { mutableStateOf<String?>(null) }
    val resultTrigger = rememberSaveable { mutableStateOf(false) }

    if (resultTrigger.value) {
        OperationResultDialog(
            trigger = resultTrigger,
            succeeded = succeeded,
            successMsgId = R.string.exported_successfully,
            failMsgId = R.string.export_fail,
            errorStr = errorStr,
        )
    }

    // Show a dialog for exporting database numbers, it can take long
    val progressTrigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = progressTrigger,
        // the dialog cannot be dismissed by tapping around, it will only disappear when the export is done
        manuallyDismissable = false
    ) {
        val total = remember { SpamTable.count(ctx) }
        val estimate = remember { total/100_000 } // 100k numbers per second

        Text(Str(R.string.exporting_database).formatAnnotated(
            "$total".A(C.infoBlue), "$estimate".A(C.infoBlue)
        ))
    }

    // Show a system file chooser

    @SuppressLint("LocalContextGetResourceValueCall")
    fun chooseExportFile(categories: CategorySelection) {
        val includeSpamDB = categories.isSelected(Category.SPAM_NUMBERS)

        // Prepare file name
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
        val ymd = LocalDate.now().format(formatter)
        val fn = if (categories.all.size == 1) { // only exporting 1 category, use the category name
            "SpamBlocker.${ymd}.${ctx.getString(categories.all.first().labelId)}.gz"
        } else {
            "SpamBlocker.${ymd}.gz"
        }

        if (includeSpamDB) progressTrigger.value = true

        // prepare file content
        val curr = Configs()
        curr.load(ctx, categories)
        var data = curr.compressToBytes()

        if (spfTemporary.encryptBackup) {
            val keyBytes = Algorithm.b64Decode(spfTemporary.backupKey)
            data = BackupUtils.encryptBackup(data, keyBytes)
        }

        FileChooser.popupWrite(
            init = InitFile(
                filename = fn,
                mimeType = MIME_GZ,
                rememberDirTag = Backup_Last_Dir_Tag,
            ),
            onResult = { uri ->
                if (uri != null) {
                    if (includeSpamDB) progressTrigger.value = false
                    errorStr = writeDataToUri(ctx, uri, data)
                    succeeded = errorStr == null
                    resultTrigger.value = true
                }
            }
        )
    }


    val encryptionTrigger = remember { mutableStateOf(false) }

    EncryptionConfigDialog(
        trigger = encryptionTrigger,
        onOk = {
            isEncryptionEnabled = spfTemporary.encryptBackup
        }
    )

    val categoryTrigger = retain { mutableStateOf(false)}

    ChooseBackupCategoriesDialog(
        trigger = categoryTrigger,
        initialCategories = CategorySelection(),
        buttons = { selectedCategories ->
            RowVCenterSpaced(8) {
                StrokeButton(
                    label = Str(R.string.encryption),
                    color = C.textGrey,
                    icon = if (isEncryptionEnabled) { { ResIcon18(R.drawable.ic_lock, color = C.warning) } } else null
                ) {
                    encryptionTrigger.value = true
                }
                StrokeButton(Str(R.string.export), color = C.teal200) {
                    coroutine.launch(IO) {
                        chooseExportFile(selectedCategories)
                    }
                }
            }
        }
    )

    StrokeButton(
        label = Str(R.string.export),
        color = C.teal200,
    ) {
        categoryTrigger.value = true
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ImportButton() {
    val ctx = LocalContext.current
    val C = G.palette

    var succeeded by remember { mutableStateOf(false) }
    var errorStr by remember { mutableStateOf("") }

    val resultTrigger = rememberSaveable { mutableStateOf(false) }
    var missingGlobalPermissions by remember { mutableStateOf("") }
    val missingBotSafUris = remember { mutableStateListOf<Uri>() }

    if (resultTrigger.value) {
        OperationResultDialog(
            trigger = resultTrigger,
            succeeded = succeeded,
            successMsgId = R.string.imported_successfully,
            failMsgId = R.string.import_fail,
            errorStr = errorStr,
            onDismiss = {
                if (succeeded) {
                    Launcher.restartProcess(ctx)
                }
            },
            extraContent = {
                if (missingGlobalPermissions.isNotEmpty() || missingBotSafUris.isNotEmpty()) {
                    // 1. System permissions
                    val prevNames = missingGlobalPermissions
                        .split(",")
                        .filter { it.isNotEmpty() }

                    val missingPermissions = Permission.all()
                        // all previous permissions
                        .filter {
                            prevNames.contains(it::class.java.simpleName.substringAfterLast('$'))
                        }
                        // not granted
                        .filter {
                            !it.isGranted
                        }
                        // map to Wrapper
                        .map {
                            PermissionWrapper(it)
                        }.toMutableList()

                    // 2. Workflow dir permissions
                    missingBotSafUris
                        .filter { !it.hasFolderAccess(ctx) }
                        .forEach {
                            val perm = PermissionType.SafDirAccess(uri = it)
                            missingPermissions += PermissionWrapper(
                                perm,
                                prompt = ctx.getString(R.string.grant_access_to_dir).format("${it.toFolderDisplayName()}")
                            )
                        }

                    if (missingPermissions.isNotEmpty()) {
                        Text(
                            text = Str(R.string.missing_permissions),
                            color = C.warning,
                        )

                        missingPermissions.forEach {
                            GreyLabel(it.perm.desc(ctx))
                        }

                        StrokeButton(
                            label = Str(R.string.grant_permissions),
                            color = C.teal200,
                        ) {
                            G.permissionChain.ask(ctx, missingPermissions) {
                                // After finished, refresh `SAF permissions`.
                                // No need to refresh global permissions, they trigger recomposition, but SAF doesn't.
                                missingBotSafUris.apply {
                                    clear()
                                    addAll(missingBotSafUris.filter {
                                        !it.hasFolderAccess(ctx)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    val categoryTrigger = retain { mutableStateOf(false)}


    var newCfg by remember { mutableStateOf(Configs()) }

    ChooseBackupCategoriesDialog(
        trigger = categoryTrigger,
        initialCategories = newCfg.categories ?: CategorySelection(),
        disabledCategories = newCfg.categories?.negate() ?: emptyCategorySelection,
        buttons = { selectedCategories ->
            StrokeButton(Str(R.string.import_), color = C.teal200) {
                CoroutineScope(IO).launch { // don't freeze the main UI, it can cause ANR and get killed
                    newCfg.apply(ctx, selectedCategories)

                    missingGlobalPermissions = newCfg.permissions?.allEnabledNames ?: ""
                    missingBotSafUris.apply {
                        clear()
                        addAll(missingBotSafUris(newCfg.bots?.bots ?: emptyList()))
                    }
                    succeeded = true
                    resultTrigger.value = true

                    // Fire an event to notify the configuration has changed,
                    // for example, the history cleanup schedule should restart
                    Events.configImported.fire()
                }
            }
        }
    )


    var encryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    val decryptTrigger = remember { mutableStateOf(false) }

    fun processImportBytes(bytes: ByteArray) {
        try {
            newCfg = Configs.decompressFromBytes(bytes)

            categoryTrigger.value = true

        } catch (e: Exception) {
            succeeded = false
            errorStr = e.message ?: ""
            resultTrigger.value = true
        }
    }

    DecryptKeyDialog(
        trigger = decryptTrigger,
        onOk = { key ->
            val bytes = encryptedBytes
            if (bytes != null) {
                try {
                    val decryptedBytes = BackupUtils.decryptBackup(bytes, key)
                    processImportBytes(decryptedBytes)
                } catch (e: Exception) {
                    succeeded = false
                    errorStr = e.message?.let { ctx.getString(R.string.decrypt_backup_fail).format(it) } ?: ""
                    resultTrigger.value = true
                }
            }
        }
    )

    fun chooseImportFile() {

        FileChooser.popupRead(
            init = InitFile(
                filename = "",
                mimeType = MIME_GZ,
                rememberDirTag = Backup_Last_Dir_Tag,
            ),
            onResult = { uri ->
                if (uri != null) {
                    val bytes = readDataFromUri(ctx, uri)
                        ?: return@popupRead

                    if (BackupUtils.isEncrypted(bytes)) {
                        encryptedBytes = bytes
                        decryptTrigger.value = true
                    } else {
                        processImportBytes(bytes)
                    }
                }
            }

        )
    }

    StrokeButton(
        label = Str(R.string.import_),
        color = C.infoBlue,
    ) {
        chooseImportFile()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupRestore() {
    LabeledRow(
        R.string.backup,
        helpTooltip = Str(R.string.help_backup),
        content = {
            FlowRowSpaced(8) {
                ExportButton()
                ImportButton()
            }
        }
    )
}
