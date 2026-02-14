package spam.blocker.ui.setting.bot

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.Schedule
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.api.ApiAuthConfigDialog
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.widgets.ConfigImportDialog
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.Str
import spam.blocker.util.BotJson
import spam.blocker.util.Lambda1
import java.util.UUID

@Composable
fun BotHeader(
    vm: BotViewModel,
) {
    val ctx = LocalContext.current

    val initialBotToEdit = remember { mutableStateOf(Bot()) }
    val afterCreated = remember{ mutableStateOf<Lambda1<Context>?> (null) }

    fun addBotToDB(ctx: Context, newBot: Bot) {
        // 1. add to db
        BotTable.addNewRecord(ctx, newBot)

        // 2. reload UI
        G.botVM.reload(ctx)

        // 3. expand the list
        if(vm.listCollapsed.value)
            vm.toggleCollapse(ctx)

        // 4. re-schedule it
        reScheduleBot(ctx, newBot)
    }

    val customizeTrigger = rememberSaveable { mutableStateOf(false) }
    if (customizeTrigger.value) {
        EditBotDialog(
            popupTrigger = customizeTrigger,
            initialBot = initialBotToEdit.value,
            onDismiss = { G.botVM.reload(ctx) },
            onSave = { newBot ->
                addBotToDB(ctx, newBot)
            }
        )
    }

    val importTrigger = remember { mutableStateOf(false) }
    if (importTrigger.value) {
        ConfigImportDialog(
            trigger = importTrigger,
        ) { configJson ->
            val bot = BotJson.decodeFromString<Bot>(configJson)
            // clear `workUUID` from imported bot
            val newBot = bot.copy(
                trigger = if(bot.trigger is Schedule)
                    bot.trigger.copy(workUUID = UUID.randomUUID().toString())
                else
                    bot.trigger,
            )

            val requiredPermissions = bot.triggerAndActions().map {
                it.requiredPermissions(ctx)
            }.flatten()
            G.permissionChain.ask(ctx, requiredPermissions) { isGranted ->
                if (isGranted) {
                    addBotToDB(ctx, newBot)
                }
            }
        }
    }

    val tappedPreset = remember { mutableStateOf<BotPreset?>(null) }

    val authFormTrigger = remember { mutableStateOf(false) }
    if (authFormTrigger.value) {
        ApiAuthConfigDialog(
            trigger = authFormTrigger,
            authConfig = tappedPreset.value!!.newAuthConfig!!(),
            actions = initialBotToEdit.value.actions,
            reportApi = tappedPreset.value!!.newReportApi?.let { it(ctx) },
        ) {
            addBotToDB(ctx, initialBotToEdit.value)
            afterCreated.value?.let { it(ctx) }
        }
    }

    val dropdownItems = remember {
        val ret = mutableListOf(
            LabelItem(
                label = ctx.getString(R.string.customize),
                leadingIcon = { GreyIcon(R.drawable.ic_note) }
            ) {
                tappedPreset.value = null
                initialBotToEdit.value = Bot()
                afterCreated.value = null

                customizeTrigger.value = true
            },
            LabelItem(
                label = ctx.getString(R.string.import_),
                leadingIcon = { GreyIcon(R.drawable.ic_import) }
            ) {
                importTrigger.value = true
            },
            DividerItem(),
        )
        ret += BotPresets.map { preset ->
            val bot = preset.newInstance(ctx)
            LabelItem(
                label = bot.desc,
                tooltip = preset.tooltip(ctx)
            ) {
                tappedPreset.value = preset
                initialBotToEdit.value = bot
                afterCreated.value = preset.afterCreated

                val requiredPermissions = bot.triggerAndActions().map {
                    it.requiredPermissions(ctx)
                }.flatten() + preset.requiredPermissions

                G.permissionChain.ask(ctx, requiredPermissions) { isGranted ->
                    if (isGranted) {
                        // If the preset requires authorization, such as API_KEY/username/password,
                        //  show a dialog asking for it.
                        // Otherwise, create the actions directly.
                        val authConfig = preset.newAuthConfig
                        if (authConfig == null) {
                            addBotToDB(ctx, bot)
                            preset.afterCreated?.let { it(ctx) }
                        } else {
                            // If it requires authorization, show a config dialog
                            authFormTrigger.value = true
                        }
                    }
                }
            }
        }
        ret
    }

    LabeledRow(
        labelId = R.string.workflows,
        modifier = M.clickable{ vm.toggleCollapse(ctx) },
        isCollapsed = vm.listCollapsed.value,
        toggleCollapse = { vm.toggleCollapse(ctx) },
        helpTooltip = Str(R.string.help_workflows),
    ) {
        MenuButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            items = dropdownItems,
        )
    }
}
