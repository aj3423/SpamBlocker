package spam.blocker.ui.setting.quick

import android.app.NotificationManager.IMPORTANCE_DEFAULT
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.NotificationManager.IMPORTANCE_NONE
import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.AnimatedVisibleV
import spam.blocker.ui.widgets.ColorPickerButton
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.FooterButton
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RingtonePicker
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Section
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.ui.widgets.rememberFileReadChooser
import spam.blocker.util.Lambda2
import spam.blocker.util.Notification
import spam.blocker.util.Notification.createChannel
import spam.blocker.util.Notification.isBuiltInChannel
import spam.blocker.util.Notification.manager
import spam.blocker.util.Notification.openChannelSettings
import spam.blocker.util.Notification.reloadChannels
import spam.blocker.util.RingtoneUtil
import spam.blocker.util.spf
import androidx.compose.foundation.Image as ComposeImage


@Composable
fun ChannelIcons(
    importance: Int?,
    mute: Boolean?,
    color: Color = LocalPalette.current.textGrey,
) {

    G.notificationChannels

    if (importance != null) {
        when(importance) {
            IMPORTANCE_NONE -> ResIcon(iconId = R.drawable.ic_bell_mute, modifier = M.size(16.dp), color = color)
            IMPORTANCE_LOW -> ResIcon(iconId = R.drawable.ic_statusbar_shade, modifier = M.size(16.dp), color = color)
            IMPORTANCE_DEFAULT -> {
                RowVCenterSpaced(2) {
                    if (!mute!!) {
                        ResIcon(R.drawable.ic_bell_ringing, modifier = M.size(16.dp), color = color)
                    }
                    ResIcon(R.drawable.ic_statusbar_shade, modifier = M.size(16.dp), color = color)
                }
            }
            IMPORTANCE_HIGH -> {
                RowVCenterSpaced(2) {
                    if (!mute!!) {
                        ResIcon(R.drawable.ic_bell_ringing, modifier = M.size(16.dp), color = color)
                    }
                    ResIcon(R.drawable.ic_statusbar_shade, modifier = M.size(16.dp), color = color)
                    ResIcon(R.drawable.ic_heads_up, modifier = M.size(16.dp), color = color)
                }
            }
        }
    } else {
        ResIcon(R.drawable.ic_question_circle, modifier = M.size(16.dp), color = DarkOrange)
    }
}

@Composable
fun EditChannelDialog(
    editTrigger: MutableState<Boolean>,
    initChannel: Channel,
) {
    val ctx = LocalContext.current
    if (!editTrigger.value) {
        return
    }
    val C = LocalPalette.current

    var chId by remember { mutableStateOf(initChannel.channelId) }
    var importance by remember { mutableIntStateOf(initChannel.importance) }
    var group by remember { mutableStateOf(initChannel.group) }
    var mute by remember { mutableStateOf(initChannel.mute) }
    var sound by remember { mutableStateOf(initChannel.sound) }
    var soundName by remember(sound) { mutableStateOf(RingtoneUtil.getName(ctx, sound.toUri())) }
    var icon by remember { mutableStateOf(initChannel.icon) }
    var iconColor by remember { mutableStateOf<Int?>(initChannel.iconColor) }
    var led by remember { mutableStateOf(initChannel.led) }
    var ledColor by remember { mutableIntStateOf(initChannel.ledColor) }


    val isCreatingNewChannel by remember { mutableStateOf(initChannel.channelId == "") }
    val isBuiltin by remember(chId) { mutableStateOf(isBuiltInChannel(chId)) }

    var anyError by remember(chId) {
        mutableStateOf(
            chId.isEmpty()
        )
    }
    PopupDialog(
        trigger = editTrigger,
        buttons = {
            RowVCenterSpaced(8) {
                // Delete
                val deleteConfirm = remember { mutableStateOf(false) }
                PopupDialog(
                    trigger = deleteConfirm,
                    buttons = {
                        StrokeButton(label = Str(R.string.delete), color = Salmon) {
                            Notification.deleteChannel(ctx, chId)
                            ChannelTable.deleteByChannelId(ctx, chId)
                            reloadChannels(ctx)
                            deleteConfirm.value = false
                            editTrigger.value = false
                        }
                    }
                ) {
                    // Show a warning: this channel is currently used by following rules...
                    val usedByRules = (NumberRegexTable().listAll(ctx) + ContentRegexTable().listAll(ctx))
                        .filter { it.channel == chId }
                        .map { ctx.getString(R.string.regex_pattern) + " " + it.summary() }
                        .toMutableList()
                    val spf = spf.Notification(ctx)
                    if (spf.spamCallChannelId == chId) {
                        usedByRules += ctx.getString(R.string.call)
                    }
                    if (spf.spamSmsChannelId == chId || spf.validSmsChannelId == chId) {
                        usedByRules += ctx.getString(R.string.sms)
                    }
                    if (usedByRules.isNotEmpty()) {
                        HtmlText(ctx.getString(R.string.warning_delete_channel)
                            .format(usedByRules.joinToString ("<br>")))
                    }

                    GreyText(Str(R.string.confirm_to_delete))
                }
                StrokeButton(
                    label = Str(R.string.delete),
                    enabled = !isBuiltin && chId.isNotEmpty(),
                    color = if (!isBuiltin && chId.isNotEmpty()) Salmon else C.disabled
                ) {
                    deleteConfirm.value = true
                }

                // Save
                StrokeButton(
                    label = Str(R.string.save),
                    enabled = !anyError,
                    color = if (anyError) C.disabled else Teal200
                ) {
                    val newCh = Channel(
                        channelId = chId,
                        importance = importance,
                        group = group,
                        mute = mute,
                        sound = sound,
                        icon = icon,
                        iconColor = iconColor,
                        led = led,
                        ledColor = ledColor,
                    )
                    // 1. create notification channel
                    createChannel(ctx, newCh)
                    // 2. update db
                    ChannelTable.addOrReplace(ctx, newCh)
                    // 3. refresh the channel list
                    reloadChannels(ctx)

                    editTrigger.value = false
                }
            }
        }
    ) {
        Column {
            // Sync sound/ledColor with system channel, user might have manually changed it in system settings.
            // Don't sync other attributes, as they are supposed to be edited within this app,
            //  sound and ledColor can only be modified in system settings.
            LifecycleResumeEffect(true) {
                // Do not sync when creating a new channel
                if (!isCreatingNewChannel) {
                    val sysCh = manager(ctx).getNotificationChannel(chId)
                    if (sysCh != null) {
                        sound = sysCh.sound?.toString() ?: ""
                        led = sysCh.shouldShowLights()
                        ledColor = sysCh.lightColor
                        importance = sysCh.importance
                    }
                }

                onPauseOrDispose { }
            }


            // Channel Id
            StrInputBox(
                label = { GreyLabel(Str(R.string.channel_id)) },
                text = chId,
                helpTooltip = Str(R.string.help_channel_id),
                enabled = isCreatingNewChannel, // only enable for creating channels
                onValueChange = {
                    if (it.isNotEmpty()) {
                        chId = it
                    }
                }
            )

            // Group
            StrInputBox(
                label = { GreyLabel(Str(R.string.channel_group)) },
                text = group,
                helpTooltip = Str(R.string.help_channel_group),
                onValueChange = {
                    group = it
                }
            )

            // Importance
            val ids = remember {
                listOf(
                    IMPORTANCE_NONE, IMPORTANCE_LOW, IMPORTANCE_DEFAULT, IMPORTANCE_HIGH
                )
            }
            val names = remember {
                listOf(
                    ctx.getString(R.string.none),
                    ctx.getString(R.string.low),
                    ctx.getString(R.string.medium),
                    ctx.getString(R.string.high)
                )
            }
            val importanceItems = remember {
                ids.mapIndexed { index, impo ->
                    LabelItem(
                        label = names[index],
                        leadingIcon = {
                            ChannelIcons(impo, false)
                        },
                    ) {
                        importance = impo
                    }
                }
            }
            LabeledRow(
                labelId = R.string.channel_importance,
                helpTooltip = Str(R.string.help_channel_importance)
            ) {
                RowVCenterSpaced(6) {
                    ComboBox(
                        items = importanceItems,
                        selected = ids.indexOf(importance),
                        enabled = isCreatingNewChannel,
                    )
                    if (!isCreatingNewChannel) {
                        ResIcon(R.drawable.ic_note, modifier = M.clickable {
                            openChannelSettings(ctx, chId)
                        })
                    }
                }
            }

            // Mute + Sound
            AnimatedVisibleV(importance >= IMPORTANCE_DEFAULT) {
                Column {
                    // Mute + Sound
                    val soundTrigger = remember { mutableStateOf(false) }
                    RingtonePicker(soundTrigger) { uri, name ->
                        uri?.let {
                            sound = uri
                        }
                    }
                    // Mute
                    LabeledRow(
                        R.string.mute,
                        helpTooltip = Str(R.string.help_mute_channel),
                    ) {
                        SwitchBox(mute) { isTurningOn ->
                            mute = isTurningOn
                        }
                    }

                    // Sound
                    AnimatedVisibleV(!mute) {
                        LabeledRow(
                            labelId = R.string.sound,
                            helpTooltip = Str(R.string.help_sound),
                        ) {
                            RowVCenterSpaced(6) {
                                GreyButton(
                                    if (sound.isEmpty() || sound == "content://settings/system/notification_sound")
                                        ctx.getString(R.string.default_)
                                    else
                                        soundName,
                                    enabled = isCreatingNewChannel,
                                ) {
                                    soundTrigger.value = true
                                }
                                if (!isCreatingNewChannel) {
                                    ResIcon(R.drawable.ic_note, modifier = M.clickable {
                                        openChannelSettings(ctx, chId)
                                    })
                                }
                            }
                        }
                    }
                }
            }

            // Icon
            LabeledRow(R.string.icon) {
                val fileReader = rememberFileReadChooser()
                fileReader.Compose()
                fun choose() {
                    fileReader.popup(
                        mimeTypes = arrayOf(
                            "image/png", "image/jpeg", "image/x-icon", "image/vnd.microsoft.icon",
                            "image/bmp", "mage/x-bmp"
                        )
                    ) { _, raw ->
                        if (raw == null)
                            return@popup

                        icon = raw
                    }
                }

                if (icon == null) {
                    // Auto Icon button
                    GreyButton(Str(R.string.automatic)) {
                        choose()
                    }
                } else {
                    RowVCenterSpaced(6) {
                        // Icon image
                        ComposeImage(
                            BitmapFactory.decodeByteArray(icon, 0, icon!!.size).asImageBitmap(),
                            "",
                            modifier = M.size(30.dp).clickable {
                                choose()
                            }
                        )
                        // Clear icon
                        GreyButton(Str(R.string.clear)) {
                            icon = null
                        }
                    }
                }
            }

            // Icon Color

            LabeledRow(
                R.string.icon_color,
            ) {
                RowVCenterSpaced(4) {
                    ColorPickerButton(
                        color = iconColor,
                        defaultText = Str(R.string.automatic),
                        clearable = true,
                    ) {
                        iconColor = it
                    }
                }
            }

            // LED
            LabeledRow(
                R.string.led,
            ) {
                SwitchBox(led) { isTurningOn ->
                    led = isTurningOn
                }
            }

            // LED Color
            AnimatedVisibleV(led) {
                LabeledRow(
                    R.string.led_color,
                    helpTooltip = Str(R.string.help_led_color)
                ) {
                    RowVCenterSpaced(6) {
                        ColorPickerButton(
                            color = ledColor,
                            enabled = isCreatingNewChannel,
                        ) {
                            it?.let {
                                ledColor = it
                            }
                        }

                        if (!isCreatingNewChannel) {
                            ResIcon(R.drawable.ic_note, modifier = M.clickable {
                                openChannelSettings(ctx, chId)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelPicker(
    selectedChannelId: String,
    onSelected: Lambda2<Int, Channel>,
) {
    val ctx = LocalContext.current

    val selectedIndex = remember(selectedChannelId) {
        derivedStateOf {
            G.notificationChannels.indexOfFirst {
                selectedChannelId == it.channelId
            }
        }
    }

    var editingChannel by remember { mutableStateOf<Channel?>(null) }
    val editTrigger = remember { mutableStateOf(false) }

    val items = remember {
        derivedStateOf {
            G.notificationChannels.mapIndexed { index, channel ->
                LabelItem(
                    label = channel.displayName(ctx),
                    leadingIcon = {
                        ChannelIcons(channel.importance, channel.mute)
                    },
                    onClick = {
                        onSelected(index, channel)
                    },
                    onLongClick = {
                        editingChannel = channel
                        editTrigger.value = true
                    }
                )
            } +
                    // Customize
                    LabelItem(
                        label = ctx.getString(R.string.customize),
                        leadingIcon = { GreyIcon18(R.drawable.ic_note) },
                        dismissOnClick = false,
                        tooltip = ctx.getString(R.string.help_create_notification_channel)
                    ) {
                        editingChannel = null
                        editTrigger.value = true
                    }
        }
    }

    EditChannelDialog(
        editTrigger = editTrigger,
        initChannel = editingChannel ?: Channel(),
    )
    ComboBox(
        items = items.value,
        selected = selectedIndex.value,

        onLongClick = {
            editingChannel = G.notificationChannels[selectedIndex.value]
            editTrigger.value = true
        }
    )
}


@Composable
fun Notification() {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    val spf = spf.Notification(ctx)

    // Call
    var spamCallChannelId by remember { mutableStateOf(spf.spamCallChannelId) }

    // SMS
    var spamSmsChannelId by remember { mutableStateOf(spf.spamSmsChannelId) }
    var validSmsChannelId by remember { mutableStateOf(spf.validSmsChannelId) }
    var activeSmsChatChannelId by remember { mutableStateOf(spf.smsChatChannelId) }

    // Call config popup
    val configTrigger = rememberSaveable { mutableStateOf(false) }

    PopupDialog(
        trigger = configTrigger,
        content = {
            Column {
                if (G.callEnabled.value) {
                    Section(
                        title = Str(R.string.call_channel),
                        bgColor = C.dialogBg
                    ) {
                        // 1. Blocked Call
                        LabeledRow(
                            R.string.blocked,
                        ) {
                            ChannelPicker(
                                spamCallChannelId,
                            ) { _, newCh ->
                                spf.spamCallChannelId = newCh.channelId
                                spamCallChannelId = newCh.channelId
                            }
                        }
                    }
                }

                if (G.smsEnabled.value) {
                    Section(
                        title = Str(R.string.sms_channel),
                        bgColor = C.dialogBg
                    ) {
                        Column {
                            // 1. Allowed SMS
                            LabeledRow(
                                R.string.allowed,
                            ) {
                                ChannelPicker(
                                    validSmsChannelId,
                                ) { _, ch ->
                                    spf.validSmsChannelId = ch.channelId
                                    validSmsChannelId = ch.channelId
                                }
                            }
                            // 2. Blocked SMS
                            LabeledRow(
                                R.string.blocked,
                            ) {
                                ChannelPicker(
                                    spamSmsChannelId,
                                ) { _, ch ->
                                    spf.spamSmsChannelId = ch.channelId
                                    spamSmsChannelId = ch.channelId
                                }
                            }
                            // 3. Active SMS Chat
                            LabeledRow(
                                R.string.sms_chat,
                            ) {
                                ChannelPicker(
                                    activeSmsChatChannelId,
                                ) { _, ch ->
                                    spf.smsChatChannelId = ch.channelId
                                    activeSmsChatChannelId = ch.channelId
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    LabeledRow(
        R.string.notification,
        helpTooltip = Str(R.string.help_notification),
        content = {
            RowVCenterSpaced(4) {
                // Call Button
                if (G.callEnabled.value) {
                    val ch = G.notificationChannels.find {
                        it.channelId == spamCallChannelId
                    }
                    FooterButton(
                        footerIconId = R.drawable.ic_call,
                        footerSize = 10,
                        footerOffset = Pair(-2, -2),
                        icon = {
                            ChannelIcons(ch?.importance, ch?.mute, color = Salmon)
                        }
                    ) {
                        configTrigger.value = true
                    }
                }

                // SMS Button
                if (G.smsEnabled.value) {
                    val chValid = G.notificationChannels.find {
                        it.channelId == validSmsChannelId
                    }
                    val chSpam = G.notificationChannels.find {
                        it.channelId == spamSmsChannelId
                    }

                    FooterButton(
                        footerIconId = R.drawable.ic_sms,
                        footerSize = 8,
                        footerOffset = Pair(-3, -2),
                        icon = {
                            RowVCenterSpaced(4) {
                                // Valid SMS icon
                                ChannelIcons(chValid?.importance, chValid?.mute)
                                // Vertical Divider
                                VerticalDivider(thickness = 1.dp, color = C.disabled)
                                // Spam SMS icon
                                ChannelIcons(chSpam?.importance, chSpam?.mute, color = Salmon)
                            }
                        }
                    ) {
                        configTrigger.value = true
                    }
                }
            }
        }
    )
}