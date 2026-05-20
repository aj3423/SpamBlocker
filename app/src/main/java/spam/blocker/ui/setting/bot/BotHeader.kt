package spam.blocker.ui.setting.bot

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
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.InterceptSms
import spam.blocker.service.bot.Schedule
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.ConfigImportDialog
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.Str
import spam.blocker.util.BotJson
import java.util.UUID


fun addBotToDB(ctx: Context, newBot: Bot) {
    // 1. add to db
    BotTable.addNewRecord(ctx, newBot)

    // 2. reload UI
    G.botVM.reload(ctx)

    // 3. expand the list
    G.botVM.listCollapsed.value = false

    // 4. re-schedule it
    reScheduleBot(ctx, newBot)
}

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun BotHeader(
    vm: BotViewModel,
) {
    val C = G.palette
    val ctx = LocalContext.current

    val initialBotToEdit = remember { mutableStateOf(Bot()) }

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

            // Show error prompt if user try to import API json (copied from wiki)
            when (bot.actions.firstOrNull()) {
                is InterceptCall, is InterceptSms -> {
                    throw Exception(ctx.getString(R.string.should_import_as_instant_query))
                }
            }

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

    var tappedPreset by remember { mutableStateOf<BotPreset?>(null) }

    var setupDialog by remember(tappedPreset) {
        mutableStateOf(
            tappedPreset?.setupDialog
        )
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
                initialBotToEdit.value = Bot()

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
            LabelItem(
                label = ctx.getString(preset.descId),
                tooltip = preset.tooltip(ctx)
            ) {
                tappedPreset = preset

                G.permissionChain.ask(ctx, preset.requiredPermissions) { isGranted ->
                    if (isGranted) {
                        // If the preset requires authentication, such as PhoneBlock OAuth,
                        //  show an OAuth dialog.
                        // Otherwise, just create the bot.
                        if(preset.setupDialog == null) {
                            preset.doAdd!!(ctx)
                        } else {
                            setupTrigger.value = true
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
            color = C.infoBlue,
            items = dropdownItems,
        )
    }
}
