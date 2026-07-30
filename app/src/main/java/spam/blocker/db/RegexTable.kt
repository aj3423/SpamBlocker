package spam.blocker.db

import android.annotation.SuppressLint
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import spam.blocker.G
import spam.blocker.db.Db.Companion.COLUMN_BLOCK_TYPE
import spam.blocker.db.Db.Companion.COLUMN_BLOCK_TYPE_CONFIG
import spam.blocker.db.Db.Companion.COLUMN_CHANNEL_ID
import spam.blocker.db.Db.Companion.COLUMN_DESC
import spam.blocker.db.Db.Companion.COLUMN_FLAGS
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_IS_BLACK
import spam.blocker.db.Db.Companion.COLUMN_PATTERN
import spam.blocker.db.Db.Companion.COLUMN_PATTERN_EXTRA
import spam.blocker.db.Db.Companion.COLUMN_PATTERN_EXTRA_FLAGS
import spam.blocker.db.Db.Companion.COLUMN_PATTERN_EXTRA_MODE_TYPE
import spam.blocker.db.Db.Companion.COLUMN_PATTERN_FLAGS
import spam.blocker.db.Db.Companion.COLUMN_PATTERN_MODE_TYPE
import spam.blocker.db.Db.Companion.COLUMN_PRIORITY
import spam.blocker.db.Db.Companion.COLUMN_SCHEDULE
import spam.blocker.db.Db.Companion.COLUMN_SIM_SLOT
import spam.blocker.db.Notification.CHANNEL_HIGH
import spam.blocker.db.Notification.CHANNEL_LOW
import spam.blocker.db.Notification.CHANNEL_NONE
import spam.blocker.def.Def
import spam.blocker.ui.lighten
import spam.blocker.ui.setting.regex.RegexMode
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.ui.setting.regex.RegexMode.isForNumberRegexMode
import spam.blocker.ui.setting.regex.RegexMode.regexModeByType
import spam.blocker.util.PermissiveJson
import spam.blocker.util.TimeSchedule
import spam.blocker.util.Util
import spam.blocker.util.enabledRegexFlagsStr
import spam.blocker.util.hasFlag
import spam.blocker.util.regexMatches
import spam.blocker.util.setFlag
import spam.blocker.util.truncate


// v4.15 changed `importance`(Int) to `channel`(String), use this class for history compatibility
// (Remove this after 2027-01-01)
object CompatibleChannelSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("channel", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("This serializer can only be used with JSON")

        val element = jsonDecoder.decodeJsonElement()
        return when {
            // Handle new format: channel as String
            element is JsonPrimitive && element.isString -> element.content
            // Handle old format: importance as Int in parent object
            element is JsonObject && element.containsKey("importance") -> {
                val importance = element["importance"]?.jsonPrimitive?.intOrNull ?: IMPORTANCE_HIGH
                when (importance) {
                    0 -> CHANNEL_NONE
                    1,2 -> CHANNEL_LOW
                    else -> CHANNEL_HIGH
                }
            }
            // Fallback to default
            else -> Def.DEF_SPAM_CHANNEL
        }
    }
}

@Serializable
data class RegexRule(
    var id: Long = 0,

    var pattern: String = "",

    var patternFlags: Int = Def.DefaultRegexFlags, // regex flags, e.g. Case Sensitive
    var patternModeType: Int = ModeType.PhoneNumber, // PhoneNumber/ContactPrefix/Geolocation/...

    // for now, this is only used for ParticularNumber
    var patternExtra: String = "",
    var patternExtraFlags: Int = Def.DefaultRegexFlags, // regex flags, e.g. Case Sensitive
    var patternExtraModeType: Int = ModeType.PhoneNumber, // PhoneNumber/ContactPrefix/Geolocation/...

    var description: String = "",
    var priority: Int = 0,
    var isBlacklist: Boolean = true,

    var flags: Int = Def.FLAG_FOR_SMS or Def.FLAG_FOR_CALL, // applies to SMS or Call or both

    @Serializable(with = CompatibleChannelSerializer::class)
    var channel: String = if(isBlacklist) Def.DEF_SPAM_CHANNEL else CHANNEL_HIGH, // notification channel

    var schedule: String = "",
    var blockType: Int = Def.DEF_BLOCK_TYPE,
    var blockTypeConfig: String = "", // for block type "Answer+HangUp"

    var simSlot: Int? = null,
) {

    fun descOrPattern(): String {
        return description.ifEmpty {
            patternStr().truncate(limit = 40)
        }
    }

    // This function is only used for matching normal text like sms content or anything other than
    // phone number, for matching phone number, use the extension String.regexMatchesNumber, it checks
    //  other regex flags like RawMode.
    fun matches(targetStr: String): Boolean {
        return pattern.regexMatches(targetStr, patternFlags)
    }

    fun extraMatches(targetStr: String): Boolean {
        val opts = Util.flagsToRegexOptions(patternExtraFlags)
        return patternExtra.toRegex(opts).matches(targetStr)
    }

    fun isForCall(): Boolean {
        return flags.hasFlag(Def.FLAG_FOR_CALL)
    }

    fun isForSms(): Boolean {
        return flags.hasFlag(Def.FLAG_FOR_SMS)
    }

    fun isWhitelist(): Boolean {
        return !isBlacklist
    }

    fun patternStr(): String {
        return if (patternExtra != "")
            "${pattern.truncate()}   <-   $patternExtra"
        else
            pattern.truncate()
    }

    fun colorfulRegexStr(
        ctx: Context,
        forType: Int,
    ): AnnotatedString {
        val C = G.palette

        val regexColor = if (forType == Def.ForQuickCopy) {
            // QuickCopy rule color is based on flags(passed/blocked)
            val passed = flags.hasFlag(Def.FLAG_FOR_PASSED)
            val blocked = flags.hasFlag(Def.FLAG_FOR_BLOCKED)
            if (passed && blocked)
                lerp(C.success, C.error, 0.5f) // mix green + red -> poop yellow
            else if (!passed && !blocked)
                C.textGrey
            else
                if (passed) C.success else C.error
        } else
            if (isBlacklist) C.error else C.success

        return buildAnnotatedString {
            // 1. Time schedule
            val sch = TimeSchedule.parseFromStr(schedule)
            if (sch.enabled) {
                withStyle(style = SpanStyle(fontSize = 12.sp, color = C.infoBlue.lighten(0.2f))) {
                    append(sch.toDisplayStr(ctx))
                    append("\n")
                }
            }

            // 2. Regex modes
            if (patternModeType.isForNumberRegexMode() && patternModeType != ModeType.PhoneNumber) {
                val m = regexModeByType(patternModeType) as RegexMode.NumberMode
                appendInlineContent(id = m.textPlaceholder)
            }


            // 3. Regex flags
            // format:
            //   imdl .*   <-   imdl particular.*
            val imdlc = patternFlags.enabledRegexFlagsStr()
            withStyle(
                style = SpanStyle(
                    fontSize = 12.sp,
                    color = C.regexFlags
                )
            ) {
                append(if (imdlc.isEmpty()) "" else "$imdlc ")
            }

            // 4. regex
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = regexColor)) {
                append(
                    // For old xml layout, when the TextView has maxLines=10, it will truncate the
                    //  rest content when it exceeds 10 lines, but the performance is very low for
                    //  super long string. So manually truncate it first.
                    // For jetpack compose Text, not tested yet ,
                    pattern.truncate()
                )
            }

            // 5. Particular Number
            if (patternExtra != "") {
                withStyle(style = SpanStyle(color = G.palette.textGrey/*old: LightGrey*/)) {
                    append("   <-   ")
                }

                // 6. Regex mode (particular number)
                if(patternExtraModeType != ModeType.PhoneNumber) {
                    if (patternExtraModeType.isForNumberRegexMode() && patternExtraModeType != ModeType.PhoneNumber) {
                        val m = regexModeByType(patternExtraModeType) as RegexMode.NumberMode
                        appendInlineContent(id = m.textPlaceholder)
                    }
                }

                // Regex Flags (particular number)
                val imdlcEx = patternExtraFlags.enabledRegexFlagsStr()
                withStyle(
                    style = SpanStyle(
                        fontSize = 12.sp,
                        color = C.regexFlags
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

    companion object {
        fun fromMap(attrs: Map<String, String>): RegexRule {
            return PermissiveJson.decodeFromString<RegexRule>(JSONObject(attrs).toString())
        }
    }
}

fun defaultRegexRuleByType(forType: Int): RegexRule {
    return RegexRule().apply {
        if (forType == Def.ForQuickCopy) { // set it for copying sms content by default
            flags = flags.setFlag(Def.FLAG_FOR_CALL, false)
            flags = flags.setFlag(Def.FLAG_FOR_PASSED, true)
            flags = flags.setFlag(Def.FLAG_FOR_CONTENT, true)
            isBlacklist = false
        }
    }
}



abstract class RegexTable(
    private val tableNameStr: String
) : BasicTable<RegexRule>(tableNameStr) {

    fun tableName(): String = tableNameStr

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): RegexRule {
        return RegexRule(
            id = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_ID)),
            pattern = cursor.getString(cursor.getColumnIndex(Db.COLUMN_PATTERN)),
            patternExtra = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_PATTERN_EXTRA)) ?: "",
            patternFlags = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_PATTERN_FLAGS)),
            patternExtraFlags = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_PATTERN_EXTRA_FLAGS)),
            patternModeType = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_PATTERN_MODE_TYPE)),
            patternExtraModeType = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_PATTERN_EXTRA_MODE_TYPE)),
            description = cursor.getString(cursor.getColumnIndex(Db.COLUMN_DESC)),
            priority = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_PRIORITY)),
            isBlacklist = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_IS_BLACK)) == 1,
            flags = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_FLAGS)),
            channel = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_CHANNEL_ID))
                ?: Def.DEF_SPAM_CHANNEL,
            schedule = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_SCHEDULE)) ?: "",
            blockType = cursor.getIntOrNull(cursor.getColumnIndex(Db.COLUMN_BLOCK_TYPE))
                ?: Def.DEF_BLOCK_TYPE,
            blockTypeConfig = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_BLOCK_TYPE_CONFIG))
                ?: "",
            simSlot = cursor.getIntOrNull(cursor.getColumnIndex(Db.COLUMN_SIM_SLOT)),
        )
    }

    override fun toContentValues(item: RegexRule, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_PATTERN, item.pattern)
        cv.put(COLUMN_PATTERN_EXTRA, item.patternExtra)
        cv.put(COLUMN_PATTERN_FLAGS, item.patternFlags)
        cv.put(COLUMN_PATTERN_EXTRA_FLAGS, item.patternExtraFlags)
        cv.put(COLUMN_PATTERN_MODE_TYPE, item.patternModeType)
        cv.put(COLUMN_PATTERN_EXTRA_MODE_TYPE, item.patternExtraModeType)
        cv.put(COLUMN_DESC, item.description)
        cv.put(COLUMN_PRIORITY, item.priority)
        cv.put(COLUMN_FLAGS, item.flags)
        cv.put(COLUMN_IS_BLACK, if (item.isBlacklist) 1 else 0)
        cv.put(COLUMN_CHANNEL_ID, item.channel)
        cv.put(COLUMN_SCHEDULE, item.schedule)
        cv.put(COLUMN_BLOCK_TYPE, item.blockType)
        cv.put(COLUMN_BLOCK_TYPE_CONFIG, item.blockTypeConfig)
        cv.put(COLUMN_SIM_SLOT, item.simSlot)
        return cv
    }

    fun findByPattern(ctx: Context, pattern: String): RegexRule? {
        return listAll(
            ctx,
            whereClause = "$COLUMN_PATTERN = ?",
            whereParams = arrayOf(pattern),
            limit = 1
        ).firstOrNull()
    }

    fun findByDesc(
        ctx: Context,
        descPattern: String,
        descFlags: Int = Def.DefaultRegexFlags
    ): List<RegexRule> {
        val regEx = descPattern.toRegex(Util.flagsToRegexOptions(descFlags))
        return listAll(ctx).filter {
            regEx.matches(it.description)
        }
    }

    // The returned list is ordered by:
    //   Priority desc -> Description desc -> Regex pattern desc
    fun listAll(
        ctx: Context,
    ): List<RegexRule> {
        return listAll(
            ctx,
            orderBy = "$COLUMN_PRIORITY DESC, $COLUMN_DESC ASC, $COLUMN_PATTERN ASC"
        )
    }


    @SuppressLint("Range")
    fun listDuplicated(ctx: Context): List<RegexRule> {
        val ret: MutableList<RegexRule> = mutableListOf()

        val db = Db.getInstance(ctx).readableDatabase

        val sql = "SELECT * FROM ${tableName()}" +
                " GROUP BY ${COLUMN_PATTERN}, ${COLUMN_PATTERN_EXTRA}, ${COLUMN_PATTERN_FLAGS}, ${COLUMN_PATTERN_EXTRA_FLAGS}, ${Db.COLUMN_SCHEDULE}" +
                " HAVING COUNT(*) > 1"

        val cursor = db.rawQuery(sql, null)

        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val first = fromCursor(it)

                    val sql2 = """
                        SELECT * FROM ${tableName()}
                        WHERE $COLUMN_PATTERN = ? AND $COLUMN_PATTERN_EXTRA = ? AND $COLUMN_PATTERN_FLAGS = ? AND $COLUMN_PATTERN_EXTRA_FLAGS = ? AND $COLUMN_SCHEDULE = ?
                        AND id != ?
                    """
                    val cursor2 = db.rawQuery(sql2, arrayOf(
                        first.pattern,
                        first.patternExtra,
                        first.patternFlags.toString(),
                        first.patternExtraFlags.toString() ,
                        first.schedule,
                        first.id.toString()
                    ))
                    cursor2.use {
                        if (cursor2.moveToFirst()) {
                            do {
                                ret += fromCursor(cursor2)
                            } while (cursor2.moveToNext())
                        }
                    }
                } while (it.moveToNext())
            }
        }
        return ret
    }

}

open class NumberRegexTable : RegexTable(Db.TABLE_NUMBER_RULE)
open class ContentRegexTable : RegexTable(Db.TABLE_CONTENT_RULE)
open class QuickCopyRegexTable : RegexTable(Db.TABLE_QUICK_COPY_RULE)

fun ruleTableForType(forType: Int): RegexTable {
    return when (forType) {
        Def.ForNumber -> NumberRegexTable()
        Def.ForSms -> ContentRegexTable()
        else -> QuickCopyRegexTable()
    }
}
