package spam.blocker.ui.setting.api

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    callback: Lambda? = null, // This function is asynchronous because it asks for permission
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

            // 4. trigger the callback
            callback?.let { it() }
        }
    }
}

@Composable
fun ApiHeader(
    vm: ApiViewModel,
    presets: List<ApiPreset>,
) {
    val C = G.palette
    val ctx = LocalContext.current

    val tappedPreset = remember { mutableStateOf<ApiPreset?>(null) }
    val initialApi = remember { mutableStateOf<IApi?>(null) }

    val addTrigger = rememberSaveable { mutableStateOf(false) }
    if (addTrigger.value) {
        EditApiDialog(
            trigger = addTrigger,
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



    val authFormTrigger = remember { mutableStateOf(false) }
    if (authFormTrigger.value) {
        val authConfig = remember(tappedPreset.value) { tappedPreset.value!!.newAuthConfig()!! }
        val reportApi = remember(tappedPreset.value) { tappedPreset.value!!.newReportApi?.let { it(ctx) } }

        ApiAuthConfigDialog(
            trigger = authFormTrigger,
            authConfig = authConfig,
            hasReportApi = reportApi != null,
        ) { alsoReport, formFields ->
            // Replace the api_key in the http request.
            authConfig.preProcessor(initialApi.value!!.actions, formFields.map { it.value })
            addApiToDB(ctx, vm, initialApi.value!!) {
                // Also add the "report API" after the "query API" is added
                if (alsoReport) {
                    authConfig.preProcessor(reportApi!!.actions, formFields.map { it.value })
                    addApiToDB(ctx, G.apiReportVM, reportApi)
                }
            }
        }
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

                addTrigger.value = true
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
            val desc = preset.newInstance(ctx).desc
            LabelItem(
                label = desc,
                tooltip = ctx.getString(preset.tooltipId),
                leadingIcon = preset.leadingIconId?.let{ iconId-> { GreyIcon16(iconId) } }
            ) {
                tappedPreset.value = preset
                initialApi.value = preset.newInstance(ctx)

                // If the preset requires authorization, such as API_KEY/username/password,
                //  show a dialog asking for it.
                // Otherwise, create the actions directly.
                val authConfig = preset.newAuthConfig()
                if (authConfig == null) {
                    addApiToDB(ctx, vm, preset.newInstance(ctx))
                } else {
                    // If it requires authorization, show a config dialog
                    authFormTrigger.value = true
                }
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
