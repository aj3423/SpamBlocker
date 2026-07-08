package spam.blocker.ui.setting.misc

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import spam.blocker.config.defaultCategorySelection
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
import spam.blocker.util.Lambda1
import spam.blocker.util.Launcher
import spam.blocker.util.Permission
import spam.blocker.util.PermissionType
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFolderAccess
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
fun ChooseBackupCategoriesDialog(
    trigger: MutableState<Boolean>,
    initialCategories: CategorySelection,
    okLabelId: Int,
    disabledCategories: CategorySelection = emptyCategorySelection,
    onOk: Lambda1<CategorySelection>,
) {
    var categories by retain(initialCategories) { mutableStateOf(initialCategories) }

    PopupDialog(
        trigger,
        buttons = {
            StrokeButton(Str(okLabelId), color = G.palette.teal200) {
                onOk(categories)
            }
        }
    ) {

        Text(Str(R.string.include_settings), color = G.palette.teal200, modifier = M.padding(bottom = 12.dp).align(Alignment.Start))

        // Included Category buttons
        FlowRowSpaced (
            space = 20,
            vSpace = 30,
        ) {
            categories.allSelected().forEach { cat ->
                StrokeButton(
                    label = Str(cat.labelId),
                    color = Color(cat.name.hashCode().toLong() or 0xff808080),
                ) {
                    categories = categories.toggle(cat)
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = G.palette.disabled, modifier = M.padding(top = 20.dp, bottom = 10.dp))

        Text(Str(R.string.exclude_settings), color = G.palette.textGrey, modifier = M.padding(bottom = 12.dp).align(Alignment.Start))

        // Show "this category is missing from the backup and can't be restored"
        val errorTrigger = retain { mutableStateOf(false) }
        PopupDialog(errorTrigger) {
            Text(Str(R.string.backup_missing_category), color = G.palette.error)
        }

        // Excluded Category buttons
        FlowRowSpaced (
            space = 20,
            vSpace = 30,
        ) {
            categories.allUnselected().forEach { cat ->
                StrokeButton(
                    label = Str(cat.labelId),
                    color = if (disabledCategories.contains(cat))
                        G.palette.disabled
                    else
                        Color(cat.name.hashCode().toLong() or 0xff808080)
                    ,
                ) {
                    if (disabledCategories.contains(cat))
                        errorTrigger.value = true
                    else
                        categories = categories.toggle(cat)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportButton() {
    val C = G.palette
    val ctx = LocalContext.current
    val coroutine = rememberCoroutineScope()

    var succeeded by remember { mutableStateOf(false) }
    var errorStr by remember { mutableStateOf<String?>(null) }
    val resultTrigger = rememberSaveable { mutableStateOf(false) }

    if (resultTrigger.value) {
        PopupDialog(
            trigger = resultTrigger,
            icon = {
                ResIcon(
                    iconId = if (succeeded) R.drawable.ic_check_green else R.drawable.ic_fail_red,
                    color = if (succeeded) C.success else C.error,
                )
            },
            content = {
                Text(
                    Str(
                        if (succeeded)
                            R.string.exported_successfully
                        else
                            R.string.export_fail
                    ),
                    color = C.textGrey,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!succeeded) {
                    Text(errorStr?: "", color = C.error)
                }
            }
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
        val compressed = curr.toByteArray()

        FileChooser.popupWrite(
            init = InitFile(
                filename = fn,
                mimeType = MIME_GZ,
                rememberDirTag = Backup_Last_Dir_Tag,
            ),
            onResult = { uri ->
                errorStr = uri?.let {
                    if (includeSpamDB) progressTrigger.value = false

                    writeDataToUri(ctx, uri, compressed)
                }
                succeeded = errorStr == null
                resultTrigger.value = true
            }
        )
    }


    val categoryTrigger = retain { mutableStateOf(false)}

    ChooseBackupCategoriesDialog(
        trigger = categoryTrigger,
        initialCategories = CategorySelection(),
        okLabelId = R.string.export
    ) { selectedCategories ->
        coroutine.launch(IO) {
            chooseExportFile(selectedCategories)
        }
    }

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
        PopupDialog(
            trigger = resultTrigger,
            icon = {
                ResIcon(
                    iconId = if (succeeded) R.drawable.ic_check_green else R.drawable.ic_fail_red,
                    color = if (succeeded) C.success else C.error,
                )
            },
            content = {
                Text(
                    Str(
                        if (succeeded)
                            R.string.imported_successfully
                        else
                            R.string.import_fail
                    ),
                    color = C.textGrey,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!succeeded) {
                    Text(errorStr, color = C.error)
                }

                if (succeeded &&
                    (missingGlobalPermissions.isNotEmpty() || missingBotSafUris.isNotEmpty())
                ) {

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
                        .filter { !it.hasFolderAccess(ctx)  }
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
            },
            onDismiss = {
                if (succeeded) {
                    Launcher.restartProcess(ctx)
                }
            }
        )
    }

    val categoryTrigger = retain { mutableStateOf(false)}


    var newCfg by remember { mutableStateOf(Configs()) }

    ChooseBackupCategoriesDialog(
        trigger = categoryTrigger,
        initialCategories = newCfg.categories ?: defaultCategorySelection,
        disabledCategories = newCfg.categories?.negate() ?: emptyCategorySelection,
        okLabelId = R.string.import_
    ) { selectedCategories ->

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

                    try {
                        newCfg = Configs.fromByteArray(bytes)

                        categoryTrigger.value = true

                    } catch (e: Exception) {
                        succeeded = false
                        errorStr = e.message ?: ""
                        resultTrigger.value = true
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
