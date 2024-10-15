package spam.blocker

import android.app.Application
import android.content.Context
import android.content.Intent
import spam.blocker.db.BotTable
import spam.blocker.db.reScheduleBot
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.ui.crash.CrashReportActivity
import spam.blocker.ui.history.reScheduleHistoryCleanup
import spam.blocker.ui.setting.quick.reScheduleSpamDBCleanup
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class App : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // Re-schedule all tasks after backup-import.
        // The Launcher.selfRestart will launch another MainActivity, which causes events being
        // triggered twice, so bind events here instead of in the MainActivity to prevent double triggering.
        Events.configImported.listen {
            // cancel all
            MyWorkManager.cancelAll(base)

            // Re-schedule history cleanup task
            reScheduleHistoryCleanup(base)

            // Re-schedule spam db cleanup task
            reScheduleSpamDBCleanup(base)

            // Re-schedule all bots
            val bots = BotTable.listAll(base)
            bots.forEach {
                reScheduleBot(base, it)
            }
        }

        // Set the default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(thread, throwable)
        }
    }

    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        throwable.printStackTrace()

        // Restart the application with an intent to spam.blocker.ui.crash.CrashReportActivity
        val intent = Intent(this, CrashReportActivity::class.java)
            .apply {

                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                putExtra("stackTrace", sw.toString())

                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

        startActivity(intent)

        // Kill the process
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }
}