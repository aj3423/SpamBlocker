package spam.blocker.service.bot

import android.content.Context
import android.os.Build
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import spam.blocker.db.BotTable
import spam.blocker.def.Def
import spam.blocker.util.AdbLogger
import spam.blocker.util.Util
import spam.blocker.util.logd
import spam.blocker.util.logi
import java.util.concurrent.TimeUnit

// The Android built-in WorkManager doesn't support schedule like: "at 00:00:00 every day",
// this is a workaround, using a one-off task, when it triggers, schedule a new one.

private const val ScheduleConfig = "scheduleConfig"
private const val ActionsConfig = "actionsConfig"
private const val WorkTag = "workTag"


class MyWorker(
    private val ctx: Context,
    private val workerParams: WorkerParameters
) : Worker(ctx, workerParams) {

    override fun doWork(): Result {
        scheduleNext()

        runActions()

        return Result.success()
    }

    private fun runActions() {
        val actions = getActionsConfig().parseActions()

        logi("execute actions: ${actions.map { it.label(ctx) }}")

        val aCtx = ActionContext(
            logger = AdbLogger(),
            workTag = getWorkTag(),
        )
        actions.executeAll(ctx, aCtx)
    }

    private fun getWorkTag(): String {
        return workerParams.inputData.keyValueMap[WorkTag] as String
    }
    private fun getActionsConfig(): String {
        return workerParams.inputData.keyValueMap[ActionsConfig] as String
    }
    private fun getScheduleConfig(): String {
        return workerParams.inputData.keyValueMap[ScheduleConfig] as String
    }

    private fun scheduleNext() {
        val workTag = getWorkTag()

        // This check can be removed later, it's a temporary fix for the previous bug
        //   that a workflow is not stopped after being deleted.
        // Don't re-schedule it if the work uuid no longer exists in the database.
        if (workTag.isEmpty())
            return
        if (Util.isUUID(workTag) && !BotTable.isWorkUuidExist(ctx, workTag))
            return


        MyWorkManager.schedule(
            ctx,
            scheduleConfig = getScheduleConfig(),
            actionsConfig = getActionsConfig(),
            workTag = workTag,
        )
    }
}

object MyWorkManager {
    fun cancelByTag(ctx: Context, workTag: String) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(workTag)
    }

    fun cancelAll(ctx: Context) {
        WorkManager.getInstance(ctx).cancelAllWork()
    }

    fun getWorkInfoByTag(ctx: Context, tag: String): WorkInfo? {
        val workManager = WorkManager.getInstance(ctx)
        return workManager.getWorkInfosByTag(tag).get().firstOrNull {
            !it.state.isFinished
        }
    }

    // Schedule a recurring task
    // Return `false` if the scheduleConfig is invalid
    fun schedule(
        ctx: Context,
        scheduleConfig: String,
        actionsConfig: String,
        workTag: String,
    ): Boolean {
        val schedule = scheduleConfig.parseSchedule() ?: return false
        if (!schedule.isValid())
            return false

        // `null` means no need to run anymore, e.g.: Delay(one-off schedule)
        val delay = schedule.nextOccurrence()
        if (delay == null) {
            logd("skip finished task: $scheduleConfig")
            return false
        }

        // add parameters to the worker
        val data = Data.Builder().apply {
            putString(ScheduleConfig, schedule.serialize())
            putString(ActionsConfig, actionsConfig)
            putString(WorkTag, workTag)
        }.build()

        val nextWorkRequest = OneTimeWorkRequest.Builder(MyWorker::class.java)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(workTag)

            .build()

        if (Build.VERSION.SDK_INT >= Def.ANDROID_12) {
            logi("schedule task <${workTag ?: actionsConfig}> after: ${delay.toDaysPart()} days, ${delay.toHoursPart()} hours, ${delay.toMinutesPart()} minutes, ${delay.toSecondsPart()} seconds")
        } else {
            logi("schedule task <${workTag ?: actionsConfig}> after: ${delay.toMillis()} milliseconds")
        }
        WorkManager.getInstance(ctx).enqueue(nextWorkRequest)

        return true
    }
}