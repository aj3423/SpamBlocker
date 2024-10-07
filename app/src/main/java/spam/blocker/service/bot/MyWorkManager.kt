package spam.blocker.service.bot

import android.content.Context
import android.os.Build
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import spam.blocker.def.Def
import spam.blocker.util.logi
import java.util.UUID
import java.util.concurrent.TimeUnit

// The Android built-in WorkManager doesn't support schedule like: "at 00:00:00 every day",
// this is a workaround, using a one-off task, when it triggers, schedule a new one.

private const val Param_ScheduleConfig = "scheduleConfig"
private const val Param_ActionsConfig = "actionsConfig"
private const val Param_WorkUUID = "workUuid"
private const val Param_WorkTag = "workTag"


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
        val actionsConfig = workerParams.inputData.getString(Param_ActionsConfig)!!
        val actions = actionsConfig.parseActions()

        actions.executeAll(ctx)
    }

    private fun scheduleNext() {
        val data = workerParams.inputData.keyValueMap

        MyWorkManager.schedule(
            ctx,
            scheduleConfig = data[Param_ScheduleConfig] as String,
            actionsConfig = data[Param_ActionsConfig] as String,
            workUUID = data[Param_WorkUUID] as String?,
            workTag = data[Param_WorkTag] as String?
        )
    }
}

object MyWorkManager {
    fun cancelByTag(ctx: Context, workTag: String) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(workTag)
    }

    fun cancelById(ctx: Context, uuid: String) {
        WorkManager.getInstance(ctx).cancelWorkById(UUID.fromString(uuid))
    }

    // Schedule a recurring task
    // Return `false` if the scheduleConfig is invalid
    fun schedule(
        ctx: Context,
        scheduleConfig: String,
        actionsConfig: String,
        workUUID: String? = null, // must have either uuid or tag
        workTag: String? = null,
    ): Boolean {
        val schedule = scheduleConfig.parseSchedule() ?: return false
        if (!schedule.isValid())
            return false

        val delay = schedule.nextOccurrence()

        // add parameters to the worker
        val data = Data.Builder().apply {
            putString(Param_ScheduleConfig, scheduleConfig)
            putString(Param_ActionsConfig, actionsConfig)

            if (workUUID != null)
                putString(Param_WorkUUID, workUUID)

            if (workTag != null)
                putString(Param_WorkTag, workTag)
        }.build()

        val nextWorkRequest = OneTimeWorkRequest.Builder(MyWorker::class.java)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(data)
            .apply {
                if (workUUID != null)
                    setId(UUID.fromString(workUUID))

                if (workTag != null)
                    addTag(workTag)
            }
            .build()

        if (Build.VERSION.SDK_INT >= Def.ANDROID_12) {
            logi("scheduled task <${workTag?:actionsConfig}> after: ${delay.toDaysPart()} days, ${delay.toHoursPart()} hours, ${delay.toMinutesPart()} minutes, ${delay.toSecondsPart()} seconds")
        } else {
            logi("scheduled task <${workTag?:actionsConfig}> after: ${delay.toMillis()} milliseconds")
        }
        WorkManager.getInstance(ctx).enqueue(nextWorkRequest)

        return true
    }
}