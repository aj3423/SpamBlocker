package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import spam.blocker.def.Def

class Db private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 26
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


        // ---- call ----
        const val TABLE_CALL = "call"
        // ---- sms ----
        const val TABLE_SMS = "sms"

        // ---- history table ----
        const val COLUMN_PEER = "peer" // peer number
        const val COLUMN_TIME = "time"
        const val COLUMN_RESULT = "result" // Int, as RESULT_... below
        const val COLUMN_REASON = "reason" // Long, by which filter id is this blocked/whitelisted
        const val COLUMN_READ = "read" // Boolean


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

        // call history
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_CALL (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_PEER TEXT, " +
                    "$COLUMN_TIME INTEGER, " +
                    "$COLUMN_RESULT INTEGER, " +
                    "$COLUMN_REASON LONG, " +
                    "$COLUMN_READ INTEGER" +
                    ")"
        )
        // sms history
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_SMS (" +
                    "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COLUMN_PEER TEXT, " +
                    "$COLUMN_TIME INTEGER, " +
                    "$COLUMN_RESULT INTEGER, " +
                    "$COLUMN_REASON LONG, " +
                    "$COLUMN_READ INTEGER" +
                    ")"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.i(Def.TAG, "upgrading db $oldVersion -> $newVersion")

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
    }
}
