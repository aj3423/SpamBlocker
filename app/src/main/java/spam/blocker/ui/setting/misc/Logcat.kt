package spam.blocker.ui.setting.misc

import java.io.BufferedReader
import java.io.InputStreamReader

object Logcat {
    fun collect(): String {
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
}