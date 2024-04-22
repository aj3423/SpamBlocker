package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import spam.blocker.def.Def

class Db private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 21
        const val DB_NAME = "spam_blocker.db"

        // ---- filter tables ----
        const val TABLE_NUMBER_FILTER = "number_filter"
        const val TABLE_CONTENT_FILTER = "content_filter"

        const val COLUMN_ID = "id"
        const val COLUMN_PATTERN = "pattern"
        const val COLUMN_PATTERN_EXTRA = "pattern_extra"
        const val COLUMN_DESC = "description"
        const val COLUMN_FLAG_CALL_SMS = "flag_call_sms"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_IS_BLACK = "blacklist"

        // flags
        // for call/sms
        const val FLAG_FOR_CALL = 1
        const val FLAG_FOR_SMS = 2
        const val FLAG_FOR_BOTH_SMS_CALL = 3
        // for black/white
        const val FLAG_WHITELIST = 1
        const val FLAG_BLACKLIST = 2
        const val FLAG_BOTH_WHITE_BLACKLIST = 3


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


        // allowed
        const val RESULT_ALLOWED_BY_DEFAULT = 1
        const val RESULT_ALLOWED_WHITELIST = 2
        const val RESULT_ALLOWED_AS_CONTACT = 3
        const val RESULT_ALLOWED_BY_RECENT_APP = 4
        const val RESULT_ALLOWED_BY_REPEATED_CALL = 5
        const val RESULT_ALLOWED_BY_CONTENT = 6

        // blocked
        const val RESULT_BLOCKED_BLACKLIST = 10
        const val RESULT_BLOCKED_BY_CONTENT = 11



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
                        "$COLUMN_DESC TEXT, " +
                        "$COLUMN_PRIORITY INTEGER, " +
                        "$COLUMN_IS_BLACK INTEGER, " +
                        "$COLUMN_FLAG_CALL_SMS INTEGER" +
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
    }
}
