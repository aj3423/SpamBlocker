package spam.blocker.service

import android.content.Context
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import spam.blocker.db.CallTable
import spam.blocker.db.SmsTable
import spam.blocker.util.SharedPref.HistoryOptions
import spam.blocker.util.Time
import spam.blocker.util.Util
import spam.blocker.util.logi
import java.util.concurrent.TimeUnit

class Cleaner(
    private val ctx: Context,
    private val workerParams: WorkerParameters
) : Worker(ctx, workerParams) {

    override fun doWork(): Result {

        val expireDays = HistoryOptions(ctx).getHistoryTTL()
        val now = Time.currentMillis()
        val expireMs = now - expireDays * 24 * 3600 * 1000

        logi("run scheduled cleaner job, clearing db, expireMs: $expireMs")

        CallTable().clearRecordsBeforeTimestamp(ctx, expireMs)
        SmsTable().clearRecordsBeforeTimestamp(ctx, expireMs)

        CleanerSchedule.scheduleNext(ctx)

        return Result.success()
    }
}

object CleanerSchedule {
    fun cancelPrevious(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWork()
    }

    fun scheduleNext(ctx: Context) {
        val nextMidnight = Util.nextMidnightTimestamp()
        val delay = nextMidnight - System.currentTimeMillis()

        val nextWorkRequest = OneTimeWorkRequest.Builder(Cleaner::class.java)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()

        logi("db cleanup task scheduled after $delay ms")
        WorkManager.getInstance(ctx).enqueue(nextWorkRequest)
    }
}