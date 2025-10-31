package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import spam.blocker.db.Notification.CHANNEL_HIGH
import spam.blocker.db.Notification.CHANNEL_LOW
import spam.blocker.db.Notification.CHANNEL_MEDIUM
import spam.blocker.db.Notification.CHANNEL_NONE
import spam.blocker.def.Def
import spam.blocker.util.Notification.deleteAllChannels
import spam.blocker.util.Notification.ensureBuiltInChannels
import spam.blocker.util.Notification.isChannelDisabled
import spam.blocker.util.Util.isFreshInstall
import spam.blocker.util.logi
import spam.blocker.util.spf

class Db private constructor(
    val ctx: Context
) : SQLiteOpenHelper(ctx, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 41
        const val DB_NAME = "spam_blocker.db"

        // ---- filter table ----
        const val TABLE_NUMBER_RULE = "number_filter"
        const val TABLE_CONTENT_RULE = "content_filter"
        const val TABLE_QUICK_COPY_RULE = "quick_copy"

        const val COLUMN_ID = "id"
        const val COLUMN_PATTERN = "pattern"
        const val COLUMN_PATTERN_EXTRA = "pattern_extra"
        const val COLUMN_PATTERN_FLAGS = "pattern_flag"
        const val COLUMN_PATTERN_EXTRA_FLAGS = "pattern_extra_flag"
        const val COLUMN_DESC = "description"
        const val COLUMN_FLAGS = "flag_call_sms" // the column name should be just "flags", but android<12 doesn't support renaming column
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_IS_BLACK = "blacklist"
        const val COLUMN_IMPORTANCE = "importance"
        const val COLUMN_SCHEDULE = "schedule"
        const val COLUMN_BLOCK_TYPE = "block_type"
        const val COLUMN_BLOCK_TYPE_CONFIG = "block_type_config"

        // ---- notification channel table ----
        const val TABLE_NOTIFICATION_CHANNEL = "notification_channel"
        const val COLUMN_CHANNEL_ID = "channel_id"
        const val COLUMN_ICON = "icon"
        const val COLUMN_ICON_COLOR = "icon_color"
        const val COLUMN_GROUP = "grouping"
        const val COLUMN_MUTE = "mute"
        const val COLUMN_SOUND = "sound"
        const val COLUMN_LED = "led"
        const val COLUMN_LED_COLOR = "led_color"

        // ---- spam table ----
        const val TABLE_SPAM = "spam"
        const val COLUMN_REASON_EXTRA = "reason_extra"

        // ---- push alert table ----
        const val TABLE_PUSH_ALERT = "push_alert"
        const val COLUMN_PKG_NAME = "package_name"
        const val COLUMN_BODY = "body"
        const val COLUMN_BODY_FLAGS = "body_flags"
        const val COLUMN_DURATION = "duration"

        // ---- bot table ----
        const val TABLE_BOT = "bot"
//        const val COLUMN_DESC
//        const val COLUMN_SCHEDULE
        const val COLUMN_ACTIONS = "actions"
        const val COLUMN_ENABLED = "enabled"
        const val COLUMN_WORK_UUID = "work_uuid"
        const val COLUMN_LAST_LOG = "last_log"
        const val COLUMN_LAST_LOG_TIME = "last_log_time"


        // ---- api table ----
        const val TABLE_API_QUERY = "api_query"
        const val TABLE_API_REPORT = "api_report"
        const val COLUMN_AUTO_REPORT_TYPES = "auto_report_types"

        // ---- call table ----
        const val TABLE_CALL = "call"
        // ---- sms table ----
        const val TABLE_SMS = "sms"

        // ---- history table ----
        const val COLUMN_PEER = "peer" // peer number
        const val COLUMN_TIME = "time"
        const val COLUMN_RESULT = "result" // Int, as RESULT_... below
        const val COLUMN_REASON = "reason" // Long, by which filter id is this blocked/whitelisted
        const val COLUMN_READ = "read" // Boolean
        const val COLUMN_EXTRA_INFO = "extra_info" // text
        const val COLUMN_EXPANDED = "expanded" // Boolean


        @Volatile
        private var instance: Db? = null

        fun getInstance(context: Context): Db {
            return instance ?: synchronized(this) {
                instance ?: Db(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // number rules / content rules / quick copy
        fun createPatternTable(tableName: String) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                        "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "$COLUMN_PATTERN TEXT, " +
                        "$COLUMN_PATTERN_EXTRA TEXT, " +
                        "$COLUMN_PATTERN_FLAGS INTEGER DEFAULT 0, " +
                        "$COLUMN_PATTERN_EXTRA_FLAGS INTEGER DEFAULT 0, " +
                        "$COLUMN_DESC TEXT, " +
                        "$COLUMN_PRIORITY INTEGER, " +
                        "$COLUMN_IS_BLACK INTEGER, " +
                        "$COLUMN_FLAGS INTEGER, " +
                        "$COLUMN_CHANNEL_ID TEXT DEFAULT ${Def.DEF_SPAM_CHANNEL}, " +
                        "$COLUMN_SCHEDULE TEXT DEFAULT '', " +
                        "$COLUMN_BLOCK_TYPE INTEGER DEFAULT ${Def.DEF_BLOCK_TYPE}, " +
                        "$COLUMN_BLOCK_TYPE_CONFIG TEXT DEFAULT '' " +
                        ")"
            )
        }
        createPatternTable(TABLE_NUMBER_RULE)
        createPatternTable(TABLE_CONTENT_RULE)
        createPatternTable(TABLE_QUICK_COPY_RULE)

        // notification channel database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NOTIFICATION_CHANNEL (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_CHANNEL_ID TEXT UNIQUE, " +
                    "$COLUMN_IMPORTANCE INTEGER, " +
                    "$COLUMN_MUTE INTEGER, " +
                    "$COLUMN_SOUND TEXT, " +
                    "$COLUMN_ICON TEXT, " +
                    "$COLUMN_ICON_COLOR INTEGER, " +
                    "$COLUMN_LED INTEGER, " +
                    "$COLUMN_LED_COLOR INTEGER, " +
                    "$COLUMN_GROUP TEXT " +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_channel_id ON $TABLE_NOTIFICATION_CHANNEL($COLUMN_CHANNEL_ID)")

        if (isFreshInstall(ctx)) {
            ensureBuiltInChannels(ctx, db)
        }

        // spam database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_SPAM (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_PEER TEXT UNIQUE, " +
                    "$COLUMN_REASON INTEGER, " +
                    "$COLUMN_REASON_EXTRA TEXT, " +
                    "$COLUMN_TIME INTEGER " +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_peer ON $TABLE_SPAM($COLUMN_PEER)")

        // push alert database
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_PUSH_ALERT (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_ENABLED INTEGER, " +
                    "$COLUMN_PKG_NAME TEXT, " +
                    "$COLUMN_BODY TEXT, " +
                    "$COLUMN_BODY_FLAGS INTEGER, " +
                    "$COLUMN_DURATION INTEGER " +
                    ")"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_pkg_name ON $TABLE_PUSH_ALERT($COLUMN_PKG_NAME)")

        // api query
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_API_QUERY (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DESC TEXT, " +
                    "$COLUMN_ACTIONS TEXT, " +
                    "$COLUMN_ENABLED INTEGER" +
                    ")"
        )
        // api report
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_API_REPORT (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DESC TEXT, " +
                    "$COLUMN_ACTIONS TEXT, " +
                    "$COLUMN_ENABLED INTEGER, " +
                    "$COLUMN_AUTO_REPORT_TYPES INTEGER" +
                    ")"
        )
        // bot
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_BOT (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_DESC TEXT, " +
                    "$COLUMN_SCHEDULE TEXT, " +
                    "$COLUMN_ACTIONS TEXT, " +
                    "$COLUMN_ENABLED INTEGER, " +
                    "$COLUMN_WORK_UUID TEXT UNIQUE, " +
                    "$COLUMN_LAST_LOG TEXT, " +
                    "$COLUMN_LAST_LOG_TIME INTEGER " +
                    ")"
        )

        // call/sms history
        fun createHistoryTable(tableName: String) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS $tableName (" +
                        "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "$COLUMN_PEER TEXT, " +
                        "$COLUMN_TIME INTEGER, " +
                        "$COLUMN_RESULT INTEGER, " +
                        "$COLUMN_REASON LONG, " +
                        "$COLUMN_READ INTEGER, " +
                        "$COLUMN_EXTRA_INFO TEXT, " +
                        "$COLUMN_EXPANDED INTEGER " +
                        ")"
            )
        }
        createHistoryTable(TABLE_CALL)
        createHistoryTable(TABLE_SMS)
    }

    fun addColumnIfNotExist(
        db: SQLiteDatabase,
        tableName: String,
        columnName: String,
        columnType: String
    ) {
        val cursor = db.rawQuery(
            "SELECT count(*) FROM pragma_table_info('${tableName}') WHERE name = '$columnName'",
            arrayOf()
        )
        cursor.moveToFirst()
        val count = cursor.getInt(0)
        cursor.close()

        if (count == 0) {
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnType")
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        logi("upgrading db $oldVersion -> $newVersion")

        if ((newVersion >= 21) && (oldVersion < 21)) {
            db.execSQL("ALTER TABLE $TABLE_NUMBER_RULE ADD COLUMN $COLUMN_PATTERN_EXTRA TEXT")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_RULE ADD COLUMN $COLUMN_PATTERN_EXTRA TEXT")
        }
        if ((newVersion >= 22) && (oldVersion < 22)) {
            db.execSQL("ALTER TABLE $TABLE_NUMBER_RULE ADD COLUMN $COLUMN_IMPORTANCE INTEGER")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_RULE ADD COLUMN $COLUMN_IMPORTANCE INTEGER")
        }
        if ((newVersion >= 23) && (oldVersion < 23)) {
            db.execSQL("ALTER TABLE $TABLE_NUMBER_RULE ADD COLUMN $COLUMN_PATTERN_FLAGS INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_NUMBER_RULE ADD COLUMN $COLUMN_PATTERN_EXTRA_FLAGS INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_RULE ADD COLUMN $COLUMN_PATTERN_FLAGS INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_RULE ADD COLUMN $COLUMN_PATTERN_EXTRA_FLAGS INTEGER DEFAULT 0")
        }
        if ((newVersion >= 24) && (oldVersion < 24)) {
            onCreate(db)
        }
        if ((newVersion >= 25) && (oldVersion < 25)) {
            db.execSQL("ALTER TABLE $TABLE_NUMBER_RULE ADD COLUMN $COLUMN_SCHEDULE TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE_NUMBER_RULE ADD COLUMN $COLUMN_BLOCK_TYPE INTEGER DEFAULT ${Def.BLOCK_TYPE_REJECT}")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_RULE ADD COLUMN $COLUMN_SCHEDULE TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_RULE ADD COLUMN $COLUMN_BLOCK_TYPE INTEGER DEFAULT ${Def.BLOCK_TYPE_REJECT}")
        }
        // v1.16 improved QuickCopy, upgrade all records in QuickCopy table to:
        // - unset FLAG_FOR_CALL
        // - set FLAG_FOR_CONTENT, FLAG_FOR_PASSED
        if ((newVersion >= 26) && (oldVersion < 26)) {
            // - unset FLAG_FOR_CALL
            db.execSQL("UPDATE $TABLE_QUICK_COPY_RULE SET $COLUMN_FLAGS = $COLUMN_FLAGS & ~${Def.FLAG_FOR_CALL}")
            // - set FLAG_FOR_CONTENT, FLAG_FOR_PASSED
            db.execSQL("UPDATE $TABLE_QUICK_COPY_RULE SET $COLUMN_FLAGS = $COLUMN_FLAGS | ${Def.FLAG_FOR_CONTENT or Def.FLAG_FOR_PASSED}")
        }
        // v2.0 introduced displaying sms content in sms history tab.
        if ((newVersion >= 27) && (oldVersion < 27)) {
            db.execSQL("ALTER TABLE $TABLE_CALL ADD COLUMN $COLUMN_EXTRA_INFO TEXT")
            db.execSQL("ALTER TABLE $TABLE_CALL ADD COLUMN $COLUMN_EXPANDED INTEGER")
            db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COLUMN_EXTRA_INFO TEXT")
            db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COLUMN_EXPANDED INTEGER")
        }

        // v3.0 introduces spam db and bot db
        if ((newVersion >= 28) && (oldVersion < 28)) {
            onCreate(db)
        }

        // v3.1 uses Bot.taskUUID as tag, it may have null value in v3.0.
        // Set all bots' taskUUID to random uuid string
        if ((newVersion >= 29) && (oldVersion < 29)) {
            db.execSQL("UPDATE $TABLE_BOT SET $COLUMN_WORK_UUID = REPLACE(HEX(RANDOMBLOB(16)), '-', '') WHERE $COLUMN_WORK_UUID IS NULL;")
        }
        // v4.0 introduced:
        // 1. api
        if ((newVersion >= 30) && (oldVersion < 30)) {
            onCreate(db)
        }
        // 2. the history api records are incompatible, delete all of them
        if ((newVersion >= 31) && (oldVersion < 31)) {
            db.execSQL("DELETE FROM $TABLE_CALL")
            db.execSQL("DELETE FROM $TABLE_SMS")
        }
        // 3. fix "no such table: api_query"
        if ((newVersion >= 32) && (oldVersion < 32)) {
            onCreate(db)
        }
        // 4. add column to spam db (reason, reasonDetail)
        if ((newVersion >= 33) && (oldVersion < 33)) {
            addColumnIfNotExist(db, TABLE_SPAM, COLUMN_REASON, "INTEGER")
            addColumnIfNotExist(db, TABLE_SPAM, COLUMN_REASON_EXTRA, "TEXT")
        }
        // 5. add column extraInfo
        if ((newVersion >= 34) && (oldVersion < 34)) {
            db.execSQL("DELETE FROM $TABLE_CALL")
            db.execSQL("DELETE FROM $TABLE_SMS")
            addColumnIfNotExist(db, TABLE_CALL, COLUMN_EXTRA_INFO, "TEXT")
            addColumnIfNotExist(db, TABLE_SMS, COLUMN_EXTRA_INFO, "TEXT")
        }
        // v4.2 added BlockTypeConfig for the answer+hangup delay.
        if ((newVersion >= 35) && (oldVersion < 35)) {
            addColumnIfNotExist(db, TABLE_NUMBER_RULE, COLUMN_BLOCK_TYPE_CONFIG, "TEXT")
            addColumnIfNotExist(db, TABLE_CONTENT_RULE, COLUMN_BLOCK_TYPE_CONFIG, "TEXT")
            addColumnIfNotExist(db, TABLE_QUICK_COPY_RULE, COLUMN_BLOCK_TYPE_CONFIG, "TEXT")
        }
        // v4.3 fix v4.2 forgot to add these
        if ((newVersion >= 36) && (oldVersion < 36)) {
            addColumnIfNotExist(db, TABLE_CONTENT_RULE, COLUMN_BLOCK_TYPE_CONFIG, "TEXT")
            addColumnIfNotExist(db, TABLE_QUICK_COPY_RULE, COLUMN_BLOCK_TYPE_CONFIG, "TEXT")
        }
        // v4.5 added last log for bots
        if ((newVersion >= 37) && (oldVersion < 37)) {
            addColumnIfNotExist(db, TABLE_BOT, COLUMN_LAST_LOG, "TEXT")
            addColumnIfNotExist(db, TABLE_BOT, COLUMN_LAST_LOG_TIME, "INTEGER")
        }
        // v4.12 added push alert
        if ((newVersion >= 38) && (oldVersion < 38)) {
            onCreate(db)
        }
        // v4.15 added custom notification
        if ((newVersion >= 39) && (oldVersion < 39)) {
            onCreate(db)

            // 0. Set the default Call/SMS channel to `None` if the previous channels
            //   are disabled in system settings
            if(isChannelDisabled(ctx, "Default spam call")) {
                spf.Notification(ctx).setSpamCallChannelId(CHANNEL_NONE)
            }
            if(isChannelDisabled(ctx, "Default spam SMS")) {
                spf.Notification(ctx).setSpamSmsChannelId(CHANNEL_NONE)
            }

            // 1. delete all previous channels
            deleteAllChannels(ctx)
            // 2. create 4 built-in channels
            ensureBuiltInChannels(ctx, db)

            // Upgrade old column `importance` -> `channel`
            // 3. add column `channel`
            addColumnIfNotExist(db, TABLE_NUMBER_RULE, COLUMN_CHANNEL_ID, "TEXT")
            addColumnIfNotExist(db, TABLE_CONTENT_RULE, COLUMN_CHANNEL_ID, "TEXT")

            // 4. Migrate data from `importance` to `channel`
            db.execSQL("""
                    UPDATE $TABLE_NUMBER_RULE
                    SET $COLUMN_CHANNEL_ID = CASE
                        WHEN $COLUMN_IMPORTANCE = 0 THEN '$CHANNEL_NONE'
                        WHEN $COLUMN_IMPORTANCE IN (1, 2) THEN '$CHANNEL_LOW'
                        WHEN $COLUMN_IMPORTANCE = 3 THEN '$CHANNEL_MEDIUM'
                        ELSE '$CHANNEL_HIGH'
                    END
                """.trimIndent())
            db.execSQL("""
                    UPDATE $TABLE_CONTENT_RULE
                    SET $COLUMN_CHANNEL_ID = CASE
                        WHEN $COLUMN_IMPORTANCE = 0 THEN '$CHANNEL_NONE'
                        WHEN $COLUMN_IMPORTANCE IN (1, 2) THEN '$CHANNEL_LOW'
                        WHEN $COLUMN_IMPORTANCE = 3 THEN '$CHANNEL_MEDIUM'
                        ELSE '$CHANNEL_HIGH'
                    END
                """.trimIndent())

            // 5. Force update all `whitelist` rules, previously their `importance` are ignored,
            //   now they should be set to channel `Allowed`
            db.execSQL("UPDATE $TABLE_NUMBER_RULE SET $COLUMN_CHANNEL_ID = '$CHANNEL_HIGH' WHERE $COLUMN_IS_BLACK IS NOT 1;")
            db.execSQL("UPDATE $TABLE_CONTENT_RULE SET $COLUMN_CHANNEL_ID = '$CHANNEL_HIGH' WHERE $COLUMN_IS_BLACK IS NOT 1;")

            // 6. Drop old column `importance`
            // Nope, just ignore it, there's no `DROP COLUMN` in sqlite.

        }
        // v4.16 changed regex flag IgnoreCase -> CaseSensitive
        if ((newVersion >= 40) && (oldVersion < 40)) {
            // number rules
            db.execSQL("UPDATE $TABLE_NUMBER_RULE SET $COLUMN_PATTERN_FLAGS = $COLUMN_PATTERN_FLAGS | 16 WHERE ($COLUMN_PATTERN_FLAGS & 1) = 0;")
            db.execSQL("UPDATE $TABLE_NUMBER_RULE SET $COLUMN_PATTERN_EXTRA_FLAGS = $COLUMN_PATTERN_EXTRA_FLAGS | 16 WHERE ($COLUMN_PATTERN_EXTRA_FLAGS & 1) = 0;")
            // content rules
            db.execSQL("UPDATE $TABLE_CONTENT_RULE SET $COLUMN_PATTERN_FLAGS = $COLUMN_PATTERN_FLAGS | 16 WHERE ($COLUMN_PATTERN_FLAGS & 1) = 0;")
            db.execSQL("UPDATE $TABLE_CONTENT_RULE SET $COLUMN_PATTERN_EXTRA_FLAGS = $COLUMN_PATTERN_EXTRA_FLAGS | 16 WHERE ($COLUMN_PATTERN_EXTRA_FLAGS & 1) = 0;")
            // quick copy
            db.execSQL("UPDATE $TABLE_QUICK_COPY_RULE SET $COLUMN_PATTERN_FLAGS = $COLUMN_PATTERN_FLAGS | 16 WHERE ($COLUMN_PATTERN_FLAGS & 1) = 0;")
            db.execSQL("UPDATE $TABLE_QUICK_COPY_RULE SET $COLUMN_PATTERN_EXTRA_FLAGS = $COLUMN_PATTERN_EXTRA_FLAGS | 16 WHERE ($COLUMN_PATTERN_EXTRA_FLAGS & 1) = 0;")
        }

        // v4.20 added auto-report category option
        if ((newVersion >= 41) && (oldVersion < 41)) {
            addColumnIfNotExist(db, TABLE_API_REPORT, COLUMN_AUTO_REPORT_TYPES, "INTEGER")
        }
    }
}
