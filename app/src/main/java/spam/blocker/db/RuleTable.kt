package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.SpannableUtil

class Flag(var value: Int) {

    // check if it has a flag
    fun Has(f: Int): Boolean {
        return value and f == f
    }

    fun isClean(): Boolean {
        return value == 0
    }
    // add or remove a flag
    fun set(f: Int, enabled: Boolean) {
        value = if (enabled) { // add flag
            value or f
        } else { // clear flag
            value and f.inv()
        }
    }
    fun toStr(attrMap: Map<Int, String>): String {
        var ret = ""
        attrMap.forEach { (k, v) ->
            if (Has(k))
                ret += v
        }
        return ret
    }
}

class PatternFilter {

    var id: Long = 0
    var pattern: String = ""

    // for now, this is only used as ParticularNumber
    var patternExtra: String = ""

    var patternFlags = Flag(0)
    var patternExtraFlags = Flag(0)
    var description: String = ""
    var priority: Int = 1
    var isBlacklist = true
    var flagCallSms = Flag(Def.FLAG_FOR_BOTH_SMS_CALL) // it applies to SMS or Call or both
    var importance = Def.DEF_SPAM_IMPORTANCE // notification importance

    override fun toString(): String {
        return "id: $id, pattern: $pattern, patternExtra: $patternExtra, patternFlags: $patternFlags, patternExtraFlags: $patternExtraFlags, desc: $description, priority: $priority, flagCallSms: $flagCallSms, isBlacklist: $isBlacklist, importance: $importance"
    }

    fun isForCall(): Boolean {
        return flagCallSms.Has(Def.FLAG_FOR_CALL)
    }

    fun patternStr(): String {
        return if (patternExtra != "")
            "$pattern   <-   $patternExtra"
        else
            pattern
    }
    fun patternStrColorful(ctx: Context): SpannableStringBuilder {
        val green = ctx.resources.getColor(R.color.dark_sea_green, null)
        val red = ctx.resources.getColor(R.color.salmon, null)
        val flagsColor = ctx.resources.getColor(R.color.regex_flags, null)

        val ratioFlags = 0.9f

        val patternColor = if (isBlacklist) red else green

        // format:
        //   imdl .*   <-   imdl particular.*
        val sb = SpannableStringBuilder()

        if (!patternFlags.isClean()) {
            SpannableUtil.append(sb, patternFlags.toStr(Def.MAP_REGEX_FLAGS) + " ", flagsColor, relativeSize = ratioFlags)
        }
        SpannableUtil.append(sb, pattern, patternColor, bold = true)
        if (patternExtra != "") {
            SpannableUtil.append(sb, "   <-   ", Color.LTGRAY)
            if (!patternExtraFlags.isClean()) {
                SpannableUtil.append(sb, patternExtraFlags.toStr(Def.MAP_REGEX_FLAGS) + " ", flagsColor, relativeSize = ratioFlags)
            }
            SpannableUtil.append(sb, patternExtra, patternColor)
        }

        return sb
    }


    fun isForSms(): Boolean {
        return flagCallSms.Has(Def.FLAG_FOR_SMS)
    }

    fun isWhitelist(): Boolean {
        return !isBlacklist
    }

    fun setForCall(enabled: Boolean) {
        flagCallSms.set(Def.FLAG_FOR_CALL, enabled)
    }

    fun setForSms(enabled: Boolean) {
        flagCallSms.set(Def.FLAG_FOR_SMS, enabled)
    }

}


abstract class RuleTable {

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
                    f.patternFlags = Flag(it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_FLAGS)))
                    f.patternExtraFlags = Flag(it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA_FLAGS)))
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
                f.patternFlags = Flag(it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_FLAGS)))
                f.patternExtraFlags = Flag(it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA_FLAGS)))
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
        return listFilters(ctx, Def.FLAG_FOR_BOTH_SMS_CALL)
    }

    fun listFilters(
        ctx: Context,
        flagCallSms: Int
    ): List<PatternFilter> {
        val where = arrayListOf<String>()

        // 1. call/sms
        val sms = Flag(flagCallSms).Has(Def.FLAG_FOR_SMS)
        val call = Flag(flagCallSms).Has(Def.FLAG_FOR_CALL)
        if (sms and !call) {
            where.add("(${Db.COLUMN_FLAG_CALL_SMS} & ${Def.FLAG_FOR_SMS}) = ${Def.FLAG_FOR_SMS}")
        } else if (call and !sms) {
            where.add("(${Db.COLUMN_FLAG_CALL_SMS} & ${Def.FLAG_FOR_CALL}) = ${Def.FLAG_FOR_CALL}")
        }

        // 3. build where clause
        var whereStr = ""
        if (where.size > 0) {
            whereStr = " WHERE (${where.joinToString(separator = " and ") { "($it)" }})"
        }

        val sql =
            "SELECT * FROM ${tableName()} $whereStr ORDER BY ${Db.COLUMN_PRIORITY} DESC, ${Db.COLUMN_DESC} ASC, ${Db.COLUMN_PATTERN} ASC"

//        Log.d(Def.TAG, sql)

        return _listAllPatternFiltersByFilter(ctx, sql)
    }

    fun addNewPatternFilter(ctx: Context, f: PatternFilter): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_PATTERN_FLAGS, f.patternFlags.value)
        cv.put(Db.COLUMN_PATTERN_EXTRA_FLAGS, f.patternExtraFlags.value)
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
        cv.put(Db.COLUMN_PATTERN_FLAGS, f.patternFlags.value)
        cv.put(Db.COLUMN_PATTERN_EXTRA_FLAGS, f.patternExtraFlags.value)
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
        cv.put(Db.COLUMN_PATTERN_FLAGS, f.patternFlags.value)
        cv.put(Db.COLUMN_PATTERN_EXTRA_FLAGS, f.patternExtraFlags.value)
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

    fun clearAll(ctx: Context) {
        val db = Db.getInstance(ctx).writableDatabase
        val sql = "DELETE FROM ${tableName()}"
        db.execSQL(sql)
    }
}

open class NumberFilterTable : RuleTable() {
    override fun tableName(): String {
        return Db.TABLE_NUMBER_FILTER
    }
}

open class ContentFilterTable : RuleTable() {
    override fun tableName(): String {
        return Db.TABLE_CONTENT_FILTER
    }
}