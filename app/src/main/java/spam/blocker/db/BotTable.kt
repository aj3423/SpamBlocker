package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import spam.blocker.R
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ISchedule
import spam.blocker.service.bot.ITriggerAction
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.defaultSchedules
import spam.blocker.service.bot.parseActions
import spam.blocker.service.bot.parseSchedule
import spam.blocker.service.bot.serialize
import spam.blocker.ui.widgets.GreyLabel
import java.util.UUID

/*
  The word "Workflow" is too long, so this class is called "Bot".

  TODO: refactor:
   Remove fields `enabled`, `schedule`, `workUUID` and add field `trigger`, as now there are more
     different triggers like "CalendarEvent", "Call", etc. `schedule` is just one of them.
*/
@Serializable
data class Bot(
    val id: Long = 0,
    val desc: String = "",
    val actions: List<IAction> = listOf(),

    // For schedule only
    val enabled: Boolean = false, // Scheduled or not, disabled by default because most workflows are not scheduled.
    val schedule: ISchedule? = defaultSchedules[0].clone(), // nullable for historical reason
    val workUUID: String = UUID.randomUUID().toString(), // the schedule UUID tag

    @Transient
    val lastLog: String = "",
    @Transient
    val lastLogTime: Long = 0,
) {
    // This is displayed as the summary(the second row) in a bot card.
    // types: Scheduled / Manual / CalendarEvent / SmsEvent / CallEvent
    @Composable
    fun TriggerType(modifier: Modifier) {
        val ctx = LocalContext.current

        // Scheduled (in the future, Schedule will be migrated to ITriggerAction instead of an bot attribute)
        val isScheduled = enabled && schedule != null
        if (isScheduled) {
            GreyLabel(
                text = schedule.summary(ctx),
                modifier = modifier
            )
        } else {
            val firstAction = actions.firstOrNull()

            if (firstAction is ITriggerAction) {
                firstAction.TriggerType(modifier)
            } else {
                // Manual
                GreyLabel(
                    text = ctx.getString(R.string.manual),
                    modifier = modifier
                )
            }
        }
    }
    fun isActivated(ctx: Context): Boolean {
        val isScheduled = enabled && schedule != null

        if (isScheduled)
            return true

        val firstAction = actions.firstOrNull()
        if (firstAction is ITriggerAction) {
            return firstAction.isActivated()
        }
        return false // manually trigger
    }
}


// It returns the new workUUID if it's enabled
fun reScheduleBot(ctx: Context, bot: Bot) {

    // 1. Stop previous schedule
    MyWorkManager.cancelByTag(ctx, bot.workUUID)

    // 2. Start new schedule
    if (bot.enabled) {
        MyWorkManager.schedule(
            ctx, bot.schedule!!.serialize(), bot.actions.serialize(),
            workTag = bot.workUUID
        )
    }
}

object BotTable {
    @SuppressLint("Range")
    fun listAll(ctx: Context, where: String = ""): List<Bot> {

        val sql = "SELECT * FROM ${Db.TABLE_BOT} $where ORDER BY ${Db.COLUMN_DESC}"

        val ret: MutableList<Bot> = mutableListOf()

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val schConfig = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_SCHEDULE)) ?: ""
                    val sch = schConfig.parseSchedule()

                    val actionsConfig =
                        it.getStringOrNull(it.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
                    val actions = actionsConfig.parseActions()

                    val rec = Bot(
                        id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
                        desc = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_DESC)) ?: "",
                        schedule = sch,
                        actions = actions,
                        enabled = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_ENABLED)) == 1,
                        workUUID = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_WORK_UUID)) ?: "",
                        lastLog = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_LAST_LOG)) ?: "",
                        lastLogTime = it.getLongOrNull(it.getColumnIndex(Db.COLUMN_LAST_LOG_TIME)) ?: 0,
                    )

                    ret += rec
                } while (it.moveToNext())
            }
            return ret
        }
    }

    fun addNewRecord(ctx: Context, r: Bot): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_SCHEDULE, if (r.schedule == null) "" else r.schedule.serialize())
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        cv.put(Db.COLUMN_WORK_UUID, r.workUUID)
        cv.put(Db.COLUMN_LAST_LOG, r.lastLog)
        cv.put(Db.COLUMN_LAST_LOG_TIME, r.lastLogTime)
        return db.insert(Db.TABLE_BOT, null, cv)
    }

    fun addRecordWithId(ctx: Context, r: Bot) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_SCHEDULE, if (r.schedule == null) "" else r.schedule.serialize())
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
        cv.put(Db.COLUMN_WORK_UUID, r.workUUID)
        cv.put(Db.COLUMN_LAST_LOG, r.lastLog)
        cv.put(Db.COLUMN_LAST_LOG_TIME, r.lastLogTime)
        db.insert(Db.TABLE_BOT, null, cv)
    }

    fun updateById(ctx: Context, id: Long, r: Bot): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_SCHEDULE, if (r.schedule == null) "" else r.schedule.serialize())
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_ENABLED, if (r.enabled) 1 else 0)
//        cv.put(Db.COLUMN_WORK_UUID, r.workUUID) // UUID never changes
        // no need to update log

        return db.update(Db.TABLE_BOT, cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }

    // These log getter/setter functions will be called repeatedly without refreshing bot list
    // Return: Pair<logJson, logTime>
    fun getLastLog(ctx: Context, workUUID: String): Pair<String, Long>? {
        val bot = findByWorkUuid(ctx, workUUID)
        return bot?.let {
            Pair(bot.lastLog, bot.lastLogTime)
        }
    }

    // Logging from scheduled workflows
    fun setLastLog(ctx: Context, workUUID: String, log: String): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_LAST_LOG, log)
        cv.put(Db.COLUMN_LAST_LOG_TIME, System.currentTimeMillis())
        return db.update(Db.TABLE_BOT, cv, "${Db.COLUMN_WORK_UUID} = '$workUUID'", null) >= 0
    }

    fun deleteById(ctx: Context, id: Long): Boolean {
        val sql = "DELETE FROM ${Db.TABLE_BOT} WHERE ${Db.COLUMN_ID} = $id"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
    fun findByWorkUuid(ctx: Context, workUUID: String) : Bot? {
        val found = listAll(ctx, " WHERE ${Db.COLUMN_WORK_UUID} = '$workUUID'")
        return if (found.isEmpty())
            null
        else
            found[0]
    }

    fun isWorkUuidExist(ctx: Context, workUUID: String) : Boolean {
        val db = Db.getInstance(ctx).readableDatabase

        val cursor = db.rawQuery("SELECT * FROM ${Db.TABLE_BOT} WHERE ${Db.COLUMN_WORK_UUID} = '$workUUID'", null)

        return cursor.use {
            it.moveToFirst()
        }
    }
    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${Db.TABLE_BOT}"
        db.execSQL(sql)
    }

}
