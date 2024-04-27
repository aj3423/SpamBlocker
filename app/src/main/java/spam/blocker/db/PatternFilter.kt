package spam.blocker.db

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.util.Log
import spam.blocker.def.Def

class Flag(var value: Int) {

    // check if it has a flag
    fun Has(f: Int): Boolean {
        return value and f == f
    }

    // add or remove a flag
    fun set(f: Int, enabled: Boolean) {
        value = if (enabled) { // add flag
            value or f
        } else { // clear flag
            value and f.inv()
        }
    }
}

class PatternFilter {

    var id: Long = 0
    var pattern: String = ""
    var patternExtra: String = ""
    var description: String = ""
    var priority: Int = 1
    var isBlacklist = true
    var flagCallSms = Flag(Db.FLAG_FOR_BOTH_SMS_CALL)
    var importance = Def.DEF_SPAM_IMPORTANCE

    override fun toString(): String {
        return "id: $id, pattern: $pattern, patternExtra: $patternExtra, desc: $description, priority: $priority, flagCallSms: $flagCallSms, isBlacklist: $isBlacklist, importance: $importance"
    }

    fun isForCall(): Boolean {
        return flagCallSms.Has(Db.FLAG_FOR_CALL)
    }

    fun patternStr(): String {
        return if (patternExtra != "")
            "$pattern   <-   $patternExtra"
        else
            pattern
    }

    fun isForSms(): Boolean {
        return flagCallSms.Has(Db.FLAG_FOR_SMS)
    }

    fun isWhitelist(): Boolean {
        return !isBlacklist
    }

    fun setForCall(enabled: Boolean) {
        flagCallSms.set(Db.FLAG_FOR_CALL, enabled)
    }

    fun setForSms(enabled: Boolean) {
        flagCallSms.set(Db.FLAG_FOR_SMS, enabled)
    }

}


abstract class PatternTable {

    abstract fun tableName(): String

    @SuppressLint("Range")
    fun _listAllPatternFiltersByFilter(ctx: Context, filterSql: String): List<PatternFilter> {
        val ret: MutableList<PatternFilter> = mutableListOf()

        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery(filterSql, null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val f = PatternFilter()

                    f.id = it.getLong(it.getColumnIndex(Db.COLUMN_ID))
                    f.pattern = it.getString(it.getColumnIndex(Db.COLUMN_PATTERN))
                    val columnExtra = it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA)
                    f.patternExtra = if(it.isNull(columnExtra)) "" else it.getString(columnExtra) // for historical compatibility
                    f.description = it.getString(it.getColumnIndex(Db.COLUMN_DESC))
                    f.priority = it.getInt(it.getColumnIndex(Db.COLUMN_PRIORITY))
                    f.isBlacklist = it.getInt(it.getColumnIndex(Db.COLUMN_IS_BLACK)) == 1
                    f.flagCallSms = Flag(it.getInt(it.getColumnIndex(Db.COLUMN_FLAG_CALL_SMS)))
                    val columnImportance = it.getColumnIndex(Db.COLUMN_IMPORTANCE)
                    f.importance = if(it.isNull(columnImportance)) Def.DEF_SPAM_IMPORTANCE else it.getInt(columnImportance) // for historical compatibility

                    ret += f
                } while (it.moveToNext())
            }
            return ret
        }
    }

    @SuppressLint("Range")
    fun findPatternFilterById(ctx: Context, id: Long): PatternFilter? {
        val db = Db.getInstance(ctx).readableDatabase
        val sql = "SELECT * FROM ${tableName()} WHERE ${Db.COLUMN_ID} = $id"

        val cursor = db.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                val f = PatternFilter()
                f.id = it.getLong(it.getColumnIndex(Db.COLUMN_ID))
                f.pattern = it.getString(it.getColumnIndex(Db.COLUMN_PATTERN))
                val columnExtra = it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA)
                f.patternExtra = if(it.isNull(columnExtra)) "" else it.getString(columnExtra) // for historical compatibility
                f.description = it.getString(it.getColumnIndex(Db.COLUMN_DESC))
                f.priority = it.getInt(it.getColumnIndex(Db.COLUMN_PRIORITY))
                f.isBlacklist = it.getInt(it.getColumnIndex(Db.COLUMN_IS_BLACK)) == 1
                f.flagCallSms = Flag(it.getInt(it.getColumnIndex(Db.COLUMN_FLAG_CALL_SMS)))
                val columnImportance = it.getColumnIndex(Db.COLUMN_IMPORTANCE)
                f.importance = if(it.isNull(columnImportance)) Def.DEF_SPAM_IMPORTANCE else it.getInt(columnImportance) // for historical compatibility

                return f
            } else {
                return null
            }
        }
    }

    fun listAll(ctx: Context): List<PatternFilter> {
        return listFilters(
            ctx,
            Db.FLAG_FOR_BOTH_SMS_CALL,
            Db.FLAG_BOTH_WHITE_BLACKLIST
        )
    }

    fun listFilters(
        ctx: Context,
        flagCallSms: Int,
        flagBlackWhite: Int
    ): List<PatternFilter> {
        val where = arrayListOf<String>()

        // 1. call/sms
        val sms = Flag(flagCallSms).Has(Db.FLAG_FOR_SMS)
        val call = Flag(flagCallSms).Has(Db.FLAG_FOR_CALL)
        if (sms and !call) {
            where.add("(${Db.COLUMN_FLAG_CALL_SMS} & ${Db.FLAG_FOR_SMS}) = ${Db.FLAG_FOR_SMS}")
        } else if (call and !sms) {
            where.add("(${Db.COLUMN_FLAG_CALL_SMS} & ${Db.FLAG_FOR_CALL}) = ${Db.FLAG_FOR_CALL}")
        }

        // 2. black/white
        val black = Flag(flagBlackWhite).Has(Db.FLAG_BLACKLIST)
        val white = Flag(flagBlackWhite).Has(Db.FLAG_WHITELIST)
        if (black and !white) {
            where.add("(${Db.COLUMN_FLAG_CALL_SMS} & ${Db.FLAG_BLACKLIST}) = ${Db.FLAG_BLACKLIST}")
        } else if (white and !black) {
            where.add("(${Db.COLUMN_FLAG_CALL_SMS} & ${Db.FLAG_WHITELIST}) = ${Db.FLAG_WHITELIST}")
        }

        // 3. build where clause
        var whereStr = ""
        if (where.size > 0) {
            whereStr = " WHERE (${where.joinToString(separator = " and ") { "($it)" }})"
        }

        val sql =
            "SELECT * FROM ${tableName()} $whereStr ORDER BY ${Db.COLUMN_PRIORITY} DESC"

//        Log.d(Def.TAG, sql)

        return _listAllPatternFiltersByFilter(ctx, sql)
    }

    fun addNewPatternFilter(ctx: Context, f: PatternFilter): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_DESC, f.description)
        cv.put(Db.COLUMN_PRIORITY, f.priority)
        cv.put(Db.COLUMN_FLAG_CALL_SMS, f.flagCallSms.value)
        cv.put(Db.COLUMN_IS_BLACK, if (f.isBlacklist) 1 else 0)
        cv.put(Db.COLUMN_IMPORTANCE, f.importance)

        return db.insert(tableName(), null, cv)
    }

    fun addPatternFilterWithId(ctx: Context, f: PatternFilter) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, f.id)
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_DESC, f.description)
        cv.put(Db.COLUMN_PRIORITY, f.priority)
        cv.put(Db.COLUMN_FLAG_CALL_SMS, f.flagCallSms.value)
        cv.put(Db.COLUMN_IS_BLACK, if (f.isBlacklist) 1 else 0)
        cv.put(Db.COLUMN_IMPORTANCE, f.importance)

        db.insert(tableName(), null, cv)
    }

    fun updatePatternFilter(ctx: Context, id: Long, f: PatternFilter): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_DESC, f.description)
        cv.put(Db.COLUMN_PRIORITY, f.priority)
        cv.put(Db.COLUMN_FLAG_CALL_SMS, f.flagCallSms.value)
        cv.put(Db.COLUMN_IS_BLACK, if (f.isBlacklist) 1 else 0)
        cv.put(Db.COLUMN_IMPORTANCE, f.importance)

        return db.update(tableName(), cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }

    fun delPatternFilter(ctx: Context, id: Long): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${tableName()} WHERE ${Db.COLUMN_ID} = $id"
        val cursor = db.rawQuery(sql, null)

        return cursor.use {
            it.moveToFirst()
        }
    }

    fun delAllPatternFilters(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${tableName()}"
        db.rawQuery(sql, null)
    }
}

open class NumberFilterTable : PatternTable() {
    override fun tableName(): String {
        return Db.TABLE_NUMBER_FILTER
    }
}

open class ContentFilterTable : PatternTable() {
    override fun tableName(): String {
        return Db.TABLE_CONTENT_FILTER
    }
}