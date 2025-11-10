package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.ISchedule
import spam.blocker.service.bot.ITriggerAction
import spam.blocker.service.bot.Manual
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.Schedule
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.defaultSchedules
import spam.blocker.service.bot.parseActions
import spam.blocker.service.bot.parseSchedule
import spam.blocker.service.bot.parseTrigger
import spam.blocker.service.bot.serialize
import spam.blocker.service.bot.triggerSaver
import spam.blocker.util.InterfaceJson


// This is for migrating from v4.20 -> v4.21 only
//  (remove this after 2027-01-01)
@Serializable
data class OldBot(
    val id: Long = 0,
    val desc: String = "",
    val actions: List<IAction> = listOf(),

    val enabled: Boolean = false,
    val schedule: ISchedule? = defaultSchedules[0].clone(),
    val workUUID: String = "",
    @Transient
    val lastLog: String = "",
    @Transient
    val lastLogTime: Long = 0,
)

// The word "Workflow" is too long, so it's called "Bot".
@Serializable
data class Bot(
    val id: Long = 0,
    val desc: String = "",
    val trigger: ITriggerAction = Manual(),
    val actions: List<IAction> = listOf(),

    @Transient
    val lastLog: String = "",
    @Transient
    val lastLogTime: Long = 0,
)


// It returns the new workUUID if it's enabled
fun reScheduleBot(ctx: Context, bot: Bot) {

    if (bot.trigger !is Schedule)
        return

    val schedule = bot.trigger

    // 1. Stop previous schedule
    MyWorkManager.cancelByTag(ctx, schedule.workUUID)

    // 2. Start new schedule
    if (schedule.enabled) {
        MyWorkManager.schedule(
            ctx, schedule.serialize(), bot.actions.serialize(),
            workTag = schedule.workUUID
        )
    }
}

// Serialize self to json string
fun Bot.serialize(): String {
    return InterfaceJson.encodeToString(this)
}

// Generate a *concrete* ITriggerAction from json string.
fun String.parseBot(): Bot {
    return InterfaceJson.decodeFromString<Bot>(this)
}
val botSaver = Saver<MutableState<Bot>, String>(
    save = { it.value.serialize() },
    restore = { mutableStateOf(it.parseBot()) }
)
@Composable
fun rememberSaveableBotState(bot: Bot): MutableState<Bot> {
    return rememberSaveable(saver = botSaver) {
        mutableStateOf(bot)
    }
}


object BotTable {

    // This is for migrating from v4.20 -> v4.21 only
    //  (remove this after 2027-01-01)
    @SuppressLint("Range")
    fun listAllOldBot(ctx: Context, db: SQLiteDatabase): List<OldBot> {

        val sql = "SELECT * FROM ${Db.TABLE_BOT} "

        val ret: MutableList<OldBot> = mutableListOf()

        val cursor = db.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val schConfig = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_SCHEDULE)) ?: ""
                    val sch = schConfig.parseSchedule()

                    val actionsConfig =
                        it.getStringOrNull(it.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
                    val actions = actionsConfig.parseActions()

                    val rec = OldBot(
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
    @SuppressLint("Range")
    fun listAll(ctx: Context, where: String = ""): List<Bot> {

        val sql = "SELECT * FROM ${Db.TABLE_BOT} $where ORDER BY ${Db.COLUMN_DESC}"

        val ret: MutableList<Bot> = mutableListOf()

        val cursor = Db.getInstance(ctx).readableDatabase.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val triggerConfig = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_TRIGGER)) ?: ""
                    val trigger = triggerConfig.parseTrigger()

                    val actionsConfig =
                        it.getStringOrNull(it.getColumnIndex(Db.COLUMN_ACTIONS)) ?: ""
                    val actions = actionsConfig.parseActions()

                    val rec = Bot(
                        id = it.getLong(it.getColumnIndex(Db.COLUMN_ID)),
                        desc = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_DESC)) ?: "",
                        trigger = trigger,
                        actions = actions,
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

        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_TRIGGER, r.trigger.serialize())
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_LAST_LOG, r.lastLog)
        cv.put(Db.COLUMN_LAST_LOG_TIME, r.lastLogTime)
        return db.insert(Db.TABLE_BOT, null, cv)
    }

    fun addRecordWithId(ctx: Context, r: Bot) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, r.id)
        cv.put(Db.COLUMN_DESC, r.desc)
        cv.put(Db.COLUMN_TRIGGER, r.trigger.serialize())
        cv.put(Db.COLUMN_ACTIONS, r.actions.serialize())
        cv.put(Db.COLUMN_LAST_LOG, r.lastLog)
        cv.put(Db.COLUMN_LAST_LOG_TIME, r.lastLogTime)
        db.insert(Db.TABLE_BOT, null, cv)
    }

    fun updateById(
        ctx: Context,
        id: Long,
        desc: String? = null,
        trigger: ITriggerAction? = null,
        actions: List<IAction>? = null,
    ): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        desc?.let { cv.put(Db.COLUMN_DESC, it) }
        trigger?.let { cv.put(Db.COLUMN_TRIGGER, it.serialize()) }
        actions?.let { cv.put(Db.COLUMN_ACTIONS, it.serialize()) }
        // no need to update log

        return db.update(Db.TABLE_BOT, cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }

    fun findById(ctx: Context, id: Long) : Bot? {
        val found = listAll(ctx, "${Db.COLUMN_ID} = $id")
        return if (found.isEmpty())
            null
        else
            found[0]
    }
    // These log getter/setter functions will be called repeatedly without refreshing bot list
    // Return: Pair<logJson, logTime>
    fun getLastLog(ctx: Context, id: Long): Pair<String, Long>? {
        val bot = findById(ctx, id)
        return bot?.let {
            Pair(bot.lastLog, bot.lastLogTime)
        }
    }

    // Logging from scheduled workflows
    fun setLastLog(ctx: Context, id: Long, log: String) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_LAST_LOG, log)
        cv.put(Db.COLUMN_LAST_LOG_TIME, System.currentTimeMillis())
        db.update(Db.TABLE_BOT, cv, "${Db.COLUMN_ID} = $id", null)
    }

    fun deleteById(ctx: Context, id: Long): Boolean {
        val sql = "DELETE FROM ${Db.TABLE_BOT} WHERE ${Db.COLUMN_ID} = $id"
        val cursor = Db.getInstance(ctx).writableDatabase.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }
    fun findByWorkUuid(ctx: Context, workUUID: String) : Bot? {
        val found = listAll(ctx).filter {
            (it.trigger is Schedule) && it.trigger.workUUID == workUUID
        }
        return if (found.isEmpty())
            null
        else
            found[0]
    }

    fun isWorkUuidExist(ctx: Context, workUUID: String) : Boolean {
        return findByWorkUuid(ctx, workUUID) != null
    }
    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${Db.TABLE_BOT}"
        db.execSQL(sql)
    }
}
