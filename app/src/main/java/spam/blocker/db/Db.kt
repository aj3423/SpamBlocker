package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import spam.blocker.def.Def

class Db private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 23
        const val DB_NAME = "spam_blocker.db"

        // ---- filter tables ----
        const val TABLE_NUMBER_FILTER = "number_filter"
        const val TABLE_CONTENT_FILTER = "content_filter"

        const val COLUMN_ID = "id"
        const val COLUMN_PATTERN = "pattern"
        const val COLUMN_PATTERN_EXTRA = "pattern_extra"
        const val COLUMN_PATTERN_FLAGS = "pattern_flag"
        const val COLUMN_PATTERN_EXTRA_FLAGS = "pattern_extra_flag"
        const val COLUMN_DESC = "description"
        const val COLUMN_FLAG_CALL_SMS = "flag_call_sms"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_IS_BLACK = "blacklist"
        const val COLUMN_IMPORTANCE = "importance"


        // ---- call ----
        const val TABLE_CALL = "call"
        // ---- sms ----
        const val TABLE_SMS = "sms"

        // "id"
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
                        "$COLUMN_FLAG_CALL_SMS INTEGER, " +
                        "$COLUMN_IMPORTANCE INTEGER DEFAULT ${Def.DEF_SPAM_IMPORTANCE}" +
                        ")"
            )
        }
        createPatternTable(TABLE_NUMBER_FILTER)
        createPatternTable(TABLE_CONTENT_FILTER)

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
            db.execSQL("ALTER TABLE $TABLE_NUMBER_FILTER ADD COLUMN $COLUMN_PATTERN_EXTRA TEXT")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_FILTER ADD COLUMN $COLUMN_PATTERN_EXTRA TEXT")
        }
        if ((newVersion >= 22) && (oldVersion < 22)) {
            db.execSQL("ALTER TABLE $TABLE_NUMBER_FILTER ADD COLUMN $COLUMN_IMPORTANCE INTEGER")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_FILTER ADD COLUMN $COLUMN_IMPORTANCE INTEGER")
        }
        if ((newVersion >= 23) && (oldVersion < 23)) {
            db.execSQL("ALTER TABLE $TABLE_NUMBER_FILTER ADD COLUMN $COLUMN_PATTERN_FLAGS INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_NUMBER_FILTER ADD COLUMN $COLUMN_PATTERN_EXTRA_FLAGS INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_FILTER ADD COLUMN $COLUMN_PATTERN_FLAGS INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_CONTENT_FILTER ADD COLUMN $COLUMN_PATTERN_EXTRA_FLAGS INTEGER DEFAULT 0")
        }
    }
}
