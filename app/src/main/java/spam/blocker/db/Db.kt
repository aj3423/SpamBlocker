package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import spam.blocker.def.Def
import spam.blocker.util.logi

class Db private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 34
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

        // ---- spam table ----
        const val TABLE_SPAM = "spam"
        const val COLUMN_REASON_EXTRA = "reason_extra"

        // ---- bot table ----
        const val TABLE_BOT = "bot"
//        const val COLUMN_DESC
//        const val COLUMN_SCHEDULE
        const val COLUMN_ACTIONS = "actions"
        const val COLUMN_ENABLED = "enabled"
        const val COLUMN_WORK_UUID = "work_uuid"

        // ---- api table ----
        const val TABLE_API_QUERY = "api_query"
        const val TABLE_API_REPORT = "api_report"

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
        // number filters
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
                        "$COLUMN_IMPORTANCE INTEGER DEFAULT ${Def.DEF_SPAM_IMPORTANCE}, " +
                        "$COLUMN_SCHEDULE TEXT DEFAULT '', " +
                        "$COLUMN_BLOCK_TYPE INTEGER DEFAULT ${Def.DEF_BLOCK_TYPE}" +
                        ")"
            )
        }
        createPatternTable(TABLE_NUMBER_RULE)
        createPatternTable(TABLE_CONTENT_RULE)
        createPatternTable(TABLE_QUICK_COPY_RULE)

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
                    "$COLUMN_ENABLED INTEGER" +
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
                    "$COLUMN_WORK_UUID TEXT UNIQUE" +
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
            db.execSQL("ALTER TABLE $TABLE_SPAM ADD COLUMN $COLUMN_REASON INTEGER")
            db.execSQL("ALTER TABLE $TABLE_SPAM ADD COLUMN $COLUMN_REASON_EXTRA TEXT")
        }
        // 5. add column extraInfo
        if ((newVersion >= 34) && (oldVersion < 34)) {
            db.execSQL("DELETE FROM $TABLE_CALL")
            db.execSQL("DELETE FROM $TABLE_SMS")
            db.execSQL("ALTER TABLE $TABLE_CALL ADD COLUMN $COLUMN_EXTRA_INFO TEXT")
            db.execSQL("ALTER TABLE $TABLE_SMS ADD COLUMN $COLUMN_EXTRA_INFO TEXT")
        }
    }
}
