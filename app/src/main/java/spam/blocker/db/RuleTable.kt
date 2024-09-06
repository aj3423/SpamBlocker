package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import spam.blocker.def.Def
import spam.blocker.ui.theme.CustomColorsPalette
import spam.blocker.ui.theme.DodgeBlue
import spam.blocker.ui.theme.LightMagenta
import spam.blocker.ui.theme.Salmon
import spam.blocker.util.Schedule
import spam.blocker.util.Util
import spam.blocker.util.Util.Companion.truncate
import spam.blocker.util.hasFlag
import spam.blocker.util.loge
import spam.blocker.util.setFlag
import spam.blocker.util.toFlagStr

/*
A deserializer that allows both format for history compatibility.
    The old format:            flags: { value: 5 }
    The new format in v2.0:    flags: 5

Maybe this can be removed later(after 2026).
 */
object CompatibleIntSerializer : JsonTransformingSerializer<Int>(Int.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return when (element) {
            is JsonPrimitive -> element // Case where the value is a direct integer
            is JsonObject -> {
                element["value"] ?: throw SerializationException("Missing 'value' field")
            }
            else -> throw SerializationException("Unexpected JSON format for Int")
        }
    }
}

@Serializable
data class RegexRule(
    var id: Long = 0,
    var pattern: String = "",

    // for now, this is only used as ParticularNumber
    var patternExtra: String = "",

    @Serializable(with = CompatibleIntSerializer::class)
    var patternFlags: Int = Def.FLAG_REGEX_IGNORE_CASE or Def.FLAG_REGEX_DOT_MATCH_ALL,
    @Serializable(with = CompatibleIntSerializer::class)
    var patternExtraFlags: Int = Def.FLAG_REGEX_IGNORE_CASE or Def.FLAG_REGEX_DOT_MATCH_ALL,

    var description: String = "",
    var priority: Int = 1,
    var isBlacklist: Boolean = true,

    @Serializable(with = CompatibleIntSerializer::class)
    var flags: Int = Def.FLAG_FOR_SMS or Def.FLAG_FOR_CALL, // it applies to SMS or Call or both

    var importance: Int = Def.DEF_SPAM_IMPORTANCE, // notification importance
    var schedule: String = "",
    var blockType: Int = Def.DEF_BLOCK_TYPE,
) {

    override fun toString(): String {
        return "id: $id, pattern: $pattern, patternExtra: $patternExtra, patternFlags: $patternFlags, patternExtraFlags: $patternExtraFlags, desc: $description, priority: $priority, flagCallSms: $flags, isBlacklist: $isBlacklist, importance: $importance, schedule: $schedule, blockType: $blockType"
    }

    fun isForCall(): Boolean {
        return flags.hasFlag(Def.FLAG_FOR_CALL)
    }

    fun patternStr(): String {
        return if (patternExtra != "")
            "${truncate(pattern)}   <-   $patternExtra"
        else
            truncate(pattern)
    }

    fun colorfulRegexStr(
        ctx: Context,
        forType: Int,
        palette: CustomColorsPalette,
    ): AnnotatedString {
        val regexColor = if (forType == Def.ForQuickCopy) {
            // QuickCopy rule color is based on flags(passed/blocked)
            val passed = flags.hasFlag(Def.FLAG_FOR_PASSED)
            val blocked = flags.hasFlag(Def.FLAG_FOR_BLOCKED)
            if (passed && blocked)
                DodgeBlue
            else if (!passed && !blocked)
                palette.textGrey
            else
                if (passed) palette.textGreen else Salmon
        } else
            if (isBlacklist) Salmon else palette.textGreen

        return buildAnnotatedString {
            // 1. Time schedule
            val sch = Schedule.parseFromStr(schedule)
            if (sch.enabled) {
                withStyle(style = SpanStyle(fontSize = 12.sp, color = palette.schedule)) {
                    append(sch.toDisplayStr(ctx))
                    append("\n")
                }
            }

            // 2. imdlc
            // format:
            //   imdl .*   <-   imdl particular.*
            val imdlc = patternFlags.toFlagStr()
            withStyle(
                style = SpanStyle(
                    fontSize = 12.sp,
                    color = Color.Magenta
                )
            ) {
                append(if (imdlc.isEmpty()) "" else "$imdlc ")
            }

            // 3. regex
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = regexColor)) {
                append(
                    // For old xml layout, when the TextView has maxLines=10, it will truncate the
                    //  rest content when it exceeds 10 lines, but the performance is very low for
                    //  super long string. So manually truncate it first.
                    // For jetpack compose Text, not tested yet ,
                    truncate(pattern)
                )
            }

            // 4. Particular Number
            if (patternExtra != "") {
                withStyle(style = SpanStyle(color = palette.textGrey/*old: LightGrey*/)) {
                    append("   <-   ")
                }

                val imdlcEx = patternExtraFlags.toFlagStr()
                withStyle(
                    style = SpanStyle(
                        fontSize = 12.sp,
                        color = Color.Magenta
                    )
                ) {
                    append(if (imdlcEx.isEmpty()) "" else "$imdlcEx ")
                }
                withStyle(style = SpanStyle(color = regexColor)) {
                    append(patternExtra)
                }
            }
        }
    }

    fun isForSms(): Boolean {
        return flags.hasFlag(Def.FLAG_FOR_SMS)
    }

    fun isWhitelist(): Boolean {
        return !isBlacklist
    }

    companion object {
        fun fromMap(attrs: Map<String, String>) : RegexRule {
            return RegexRule().apply {
                if (attrs.contains("pattern")) pattern = Util.clearNumber(attrs["pattern"]!!)
                if (attrs.contains("description")) description = attrs["description"]!!
                if (attrs.contains("priority")) priority = attrs["priority"]!!.toInt()
                if (attrs.contains("flags")) flags = attrs["flags"]!!.toInt()
                if (attrs.contains("isBlacklist")) isBlacklist = attrs["isBlacklist"]!!.toBoolean()
                if (attrs.contains("blockType")) blockType = attrs["blockType"]!!.toInt()
                if (attrs.contains("importance")) importance = attrs["importance"]!!.toInt()
            }
        }
    }
}

fun defaultRegexRuleByType(forType: Int) : RegexRule {
    return RegexRule().apply {
        if (forType == Def.ForQuickCopy) { // set it for copying sms content by default
            flags = flags.setFlag(Def.FLAG_FOR_CALL, false)
            flags = flags.setFlag(Def.FLAG_FOR_PASSED, true)
            flags = flags.setFlag(Def.FLAG_FOR_CONTENT, true)
            isBlacklist = false
        }
    }
}

fun newRegexRule(
    id : Long,
    pattern: String,
    patternExtra: String,
    patternFlags: Int,
    patternExtraFlags: Int,
    description: String,
    priority: Int,
    isBlacklist: Boolean,
    flags: Int,
    importance: Int,
    schedule: String,
    blockType: Int,
): RegexRule {
    return RegexRule(
        id = id,
        pattern = pattern,
        patternExtra = patternExtra,
        patternFlags = patternFlags,
        patternExtraFlags = patternExtraFlags,
        description = description,
        priority = priority,
        isBlacklist = isBlacklist,
        flags = flags,
        importance = importance,
        schedule = schedule,
        blockType = blockType,
    )
}


abstract class RuleTable {

    abstract fun tableName(): String

    @SuppressLint("Range")
    private fun listByFilter(ctx: Context, filterSql: String): List<RegexRule> {
        val ret: MutableList<RegexRule> = mutableListOf()

        val db = Db.getInstance(ctx).readableDatabase
        val cursor = db.rawQuery(filterSql, null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val f = RegexRule()

                    f.id = it.getLong(it.getColumnIndex(Db.COLUMN_ID))
                    f.pattern = it.getString(it.getColumnIndex(Db.COLUMN_PATTERN))
                    f.patternExtra =
                        it.getStringOrNull(it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA)) ?: ""
                    f.patternFlags = it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_FLAGS))
                    f.patternExtraFlags =
                        it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA_FLAGS))
                    f.description = it.getString(it.getColumnIndex(Db.COLUMN_DESC))
                    f.priority = it.getInt(it.getColumnIndex(Db.COLUMN_PRIORITY))
                    f.isBlacklist = it.getInt(it.getColumnIndex(Db.COLUMN_IS_BLACK)) == 1
                    f.flags = it.getInt(it.getColumnIndex(Db.COLUMN_FLAGS))
                    f.importance = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_IMPORTANCE))
                        ?: Def.DEF_SPAM_IMPORTANCE
                    f.schedule = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_SCHEDULE)) ?: ""
                    f.blockType = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_BLOCK_TYPE))
                        ?: Def.DEF_BLOCK_TYPE
                    ret += f
                } while (it.moveToNext())
            }
            return ret
        }
    }

    @SuppressLint("Range")
    fun findPatternRuleById(ctx: Context, id: Long): RegexRule? {
        val db = Db.getInstance(ctx).readableDatabase
        val sql = "SELECT * FROM ${tableName()} WHERE ${Db.COLUMN_ID} = $id"

        val cursor = db.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                val f = RegexRule()
                f.id = it.getLong(it.getColumnIndex(Db.COLUMN_ID))
                f.pattern = it.getString(it.getColumnIndex(Db.COLUMN_PATTERN))
                f.patternExtra =
                    it.getStringOrNull(it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA)) ?: ""
                f.patternFlags = it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_FLAGS))
                f.patternExtraFlags =
                    it.getInt(it.getColumnIndex(Db.COLUMN_PATTERN_EXTRA_FLAGS))
                f.description = it.getString(it.getColumnIndex(Db.COLUMN_DESC))
                f.priority = it.getInt(it.getColumnIndex(Db.COLUMN_PRIORITY))
                f.isBlacklist = it.getInt(it.getColumnIndex(Db.COLUMN_IS_BLACK)) == 1
                f.flags = it.getInt(it.getColumnIndex(Db.COLUMN_FLAGS))
                f.importance = it.getIntOrNull(it.getColumnIndex(Db.COLUMN_IMPORTANCE))
                    ?: Def.DEF_SPAM_IMPORTANCE
                f.schedule = it.getStringOrNull(it.getColumnIndex(Db.COLUMN_SCHEDULE)) ?: ""
                f.blockType =
                    it.getIntOrNull(it.getColumnIndex(Db.COLUMN_BLOCK_TYPE)) ?: Def.DEF_BLOCK_TYPE

                return f
            } else {
                return null
            }
        }
    }

    // The returned list is ordered by:
    //   Priority desc -> Description desc -> Regex pattern desc
    fun listAll(ctx: Context): List<RegexRule> {
        return listRules(ctx, Def.FLAG_FOR_SMS or Def.FLAG_FOR_CALL)
    }

    fun listRules(
        ctx: Context,
        flagCallSms: Int
    ): List<RegexRule> {
        val where = arrayListOf<String>()

        // call/sms
        val sms = flagCallSms.hasFlag(Def.FLAG_FOR_SMS)
        val call = flagCallSms.hasFlag(Def.FLAG_FOR_CALL)
        if (sms and !call) {
            where.add("(${Db.COLUMN_FLAGS} & ${Def.FLAG_FOR_SMS}) = ${Def.FLAG_FOR_SMS}")
        } else if (call and !sms) {
            where.add("(${Db.COLUMN_FLAGS} & ${Def.FLAG_FOR_CALL}) = ${Def.FLAG_FOR_CALL}")
        }

        // build where clause
        var whereStr = ""
        if (where.size > 0) {
            whereStr = " WHERE (${where.joinToString(separator = " and ") { "($it)" }})"
        }

        val sql =
            "SELECT * FROM ${tableName()} $whereStr ORDER BY ${Db.COLUMN_PRIORITY} DESC, ${Db.COLUMN_DESC} ASC, ${Db.COLUMN_PATTERN} ASC"

        return listByFilter(ctx, sql)
    }

    fun addNewRule(ctx: Context, f: RegexRule): Long {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_PATTERN_FLAGS, f.patternFlags)
        cv.put(Db.COLUMN_PATTERN_EXTRA_FLAGS, f.patternExtraFlags)
        cv.put(Db.COLUMN_DESC, f.description)
        cv.put(Db.COLUMN_PRIORITY, f.priority)
        cv.put(Db.COLUMN_FLAGS, f.flags)
        cv.put(Db.COLUMN_IS_BLACK, if (f.isBlacklist) 1 else 0)
        cv.put(Db.COLUMN_IMPORTANCE, f.importance)
        cv.put(Db.COLUMN_SCHEDULE, f.schedule)
        cv.put(Db.COLUMN_BLOCK_TYPE, f.blockType)

        return db.insert(tableName(), null, cv)
    }

    fun addRuleWithId(ctx: Context, f: RegexRule) {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_ID, f.id)
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_PATTERN_FLAGS, f.patternFlags)
        cv.put(Db.COLUMN_PATTERN_EXTRA_FLAGS, f.patternExtraFlags)
        cv.put(Db.COLUMN_DESC, f.description)
        cv.put(Db.COLUMN_PRIORITY, f.priority)
        cv.put(Db.COLUMN_FLAGS, f.flags)
        cv.put(Db.COLUMN_IS_BLACK, if (f.isBlacklist) 1 else 0)
        cv.put(Db.COLUMN_IMPORTANCE, f.importance)
        cv.put(Db.COLUMN_SCHEDULE, f.schedule)
        cv.put(Db.COLUMN_BLOCK_TYPE, f.blockType)

        db.insert(tableName(), null, cv)
    }

    fun updateRuleById(ctx: Context, id: Long, f: RegexRule): Boolean {
        val db = Db.getInstance(ctx).writableDatabase
        val cv = ContentValues()
        cv.put(Db.COLUMN_PATTERN, f.pattern)
        cv.put(Db.COLUMN_PATTERN_EXTRA, f.patternExtra)
        cv.put(Db.COLUMN_PATTERN_FLAGS, f.patternFlags)
        cv.put(Db.COLUMN_PATTERN_EXTRA_FLAGS, f.patternExtraFlags)
        cv.put(Db.COLUMN_DESC, f.description)
        cv.put(Db.COLUMN_PRIORITY, f.priority)
        cv.put(Db.COLUMN_FLAGS, f.flags)
        cv.put(Db.COLUMN_IS_BLACK, if (f.isBlacklist) 1 else 0)
        cv.put(Db.COLUMN_IMPORTANCE, f.importance)
        cv.put(Db.COLUMN_SCHEDULE, f.schedule)
        cv.put(Db.COLUMN_BLOCK_TYPE, f.blockType)

        return db.update(tableName(), cv, "${Db.COLUMN_ID} = $id", null) >= 0
    }

    fun delById(ctx: Context, id: Long): Boolean {
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

open class NumberRuleTable : RuleTable() {
    override fun tableName(): String {
        return Db.TABLE_NUMBER_RULE
    }
}

open class ContentRuleTable : RuleTable() {
    override fun tableName(): String {
        return Db.TABLE_CONTENT_RULE
    }
}

open class QuickCopyRuleTable : RuleTable() {
    override fun tableName(): String {
        return Db.TABLE_QUICK_COPY_RULE
    }
}

fun ruleTableForType(forType: Int) : RuleTable {
    return when(forType) {
        Def.ForNumber -> NumberRuleTable()
        Def.ForSms -> ContentRuleTable()
        else -> QuickCopyRuleTable()
    }
}