package spam.blocker.ui.setting.api

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.IApi
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.ConfigImportDialog
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.Str
import spam.blocker.util.BotJson
import spam.blocker.util.Lambda

fun addApiToDB(
    ctx: Context,
    vm: ApiViewModel,
    newApi: IApi,
    onSuccess: Lambda = {}
) {
    val requiredPermissions = newApi.actions.flatMap { it.requiredPermissions(ctx) }

    G.permissionChain.ask(ctx, requiredPermissions) { isGranted ->
        if (isGranted) {
            // 1. add to db
            vm.table.addNewRecord(ctx, newApi)

            // 2. reload UI
            vm.reloadDb(ctx)

            // 3. expand the list
            vm.listCollapsed.value = false

            // 4. close popup dialogs
            onSuccess()
        }
    }
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ApiHeader(
    vm: ApiViewModel,
    presets: List<ApiPreset>,
) {
    val C = G.palette
    val ctx = LocalContext.current

    var setupDialog by remember { mutableStateOf<ApiSetupDialog?>(null) }

    val initialApi = remember { mutableStateOf<IApi?>(null) }

    val costomizeTrigger = rememberSaveable { mutableStateOf(false) }
    if (costomizeTrigger.value) {
        EditApiDialog(
            trigger = costomizeTrigger,
            initial = initialApi.value!!,
            onSave = { newApi ->
                addApiToDB(ctx, vm, newApi)
            }
        )
    }

    val importTrigger = remember { mutableStateOf(false) }
    if (importTrigger.value) {
        ConfigImportDialog(
            trigger = importTrigger,
        ) { configJson ->
            val newApi = if (vm.forType == Def.ForApiQuery) {
                BotJson.decodeFromString<QueryApi>(configJson).copy(id = 0)
            } else {
                BotJson.decodeFromString<ReportApi>(configJson).copy(id = 0)
            }

            // 1. add to db
            vm.table.addNewRecord(ctx, newApi)
            // 2. reload UI
            vm.reloadDb(ctx)
        }
    }



    val setupTrigger = remember { mutableStateOf(false) }
    if (setupTrigger.value) {
        setupDialog?.Compose(setupTrigger)
    }

    val dropdownItems = remember {
        val ret = mutableListOf(
            LabelItem(
                label = ctx.getString(R.string.customize),
                leadingIcon = { GreyIcon(R.drawable.ic_note) }
            ) {
                initialApi.value = if(vm.forType == Def.ForApiQuery) {
                    QueryApi(actions = defApiQueryActions)
                } else {
                    ReportApi(actions = defApiReportActions)
                }

                costomizeTrigger.value = true
            },
            LabelItem(
                label = ctx.getString(R.string.import_),
                leadingIcon = { GreyIcon(R.drawable.ic_import) }
            ) {
                importTrigger.value = true
            },
            DividerItem(),
        )

        // Api Presets: PhoneBlock, Groq, ...
        ret += presets.map { preset ->
            LabelItem(
                label = preset.desc(ctx),
                tooltip = ctx.getString(preset.tooltipId),
                leadingIcon = preset.leadingIconId?.let{ iconId-> { GreyIcon16(iconId) } }
            ) {
                setupDialog = preset.setupDialog

                if (setupDialog != null)
                    setupTrigger.value = true
                else
                    preset.onClick?.let { it(ctx) }
            }
        }
        ret
    }

    LabeledRow(
        labelId = if (vm.forType == Def.ForApiQuery) R.string.query_api else R.string.report_api,
        modifier = M.clickable { vm.toggleCollapse(ctx) },
        isCollapsed = vm.listCollapsed.value,
        toggleCollapse = { vm.toggleCollapse(ctx) },
        helpTooltip = if (vm.forType == Def.ForApiQuery)
            Str(R.string.help_instant_query)
        else
            Str(R.string.help_report_number),
    ) {
        MenuButton(
            label = Str(R.string.new_),
            color = C.infoBlue,
            items = dropdownItems,
        )
    }
}
