package spam.blocker.ui.setting.misc

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.BuildConfig
import spam.blocker.G
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LongPressButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.rememberFileWriteChooser
import spam.blocker.util.Algorithm.compressString
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.TAG
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter


fun appLog(): String {
    val process = Runtime.getRuntime().exec("logcat -d")
    val reader = BufferedReader(InputStreamReader(process.inputStream))
    val logBuilder = StringBuilder()
    var line: String?

    while (reader.readLine().also { line = it } != null) {
        logBuilder.append(line).append("\n")
    }
    reader.close()

    return logBuilder.toString()
}


@Composable
fun AdbLog() {
    SettingRow {
        val fileWriter = rememberFileWriteChooser()
        fileWriter.Compose()

        StrokeButton(
            label = "Generate ADB Log",
            color = DarkOrange,
        ) {
            val fn = "SpamBlocker.log.gz"
            val content = compressString(appLog())

            fileWriter.popup(
                filename = fn,
                content = content,
            )
        }
    }
}