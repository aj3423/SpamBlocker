package spam.blocker.db

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.serialization.Serializable
import spam.blocker.db.Db.Companion.COLUMN_ALLOW_ENABLED
import spam.blocker.db.Db.Companion.COLUMN_ALLOW_PRIORITY
import spam.blocker.db.Db.Companion.COLUMN_BLOCK_ENABLED
import spam.blocker.db.Db.Companion.COLUMN_BLOCK_PRIORITY
import spam.blocker.db.Db.Companion.COLUMN_DESC
import spam.blocker.db.Db.Companion.COLUMN_ID
import spam.blocker.db.Db.Companion.COLUMN_NAME
import spam.blocker.db.Db.Companion.TABLE_SMS_AI_CATEGORY
import spam.blocker.def.Def
import spam.blocker.util.spf

@Serializable
data class SmsAiCategory(
    val id: Long = 0,
    val name: String = "",
    val description: String = "",
    val allowEnabled: Boolean = false,
    val allowPriority: Int = 10,
    val blockEnabled: Boolean = true,
    val blockPriority: Int = 0,
) {
    fun isActive(): Boolean = name.isNotBlank() && (allowEnabled || blockEnabled)
}

object SmsAiCategoryTable : BasicTable<SmsAiCategory>(TABLE_SMS_AI_CATEGORY) {

    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): SmsAiCategory {
        val descIdx = cursor.getColumnIndex(COLUMN_DESC)
        return SmsAiCategory(
            id = cursor.getLong(cursor.getColumnIndex(COLUMN_ID)),
            name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME)) ?: "",
            description = if (descIdx >= 0) cursor.getString(descIdx) ?: "" else "",
            allowEnabled = cursor.getInt(cursor.getColumnIndex(COLUMN_ALLOW_ENABLED)) == 1,
            allowPriority = cursor.getInt(cursor.getColumnIndex(COLUMN_ALLOW_PRIORITY)),
            blockEnabled = cursor.getInt(cursor.getColumnIndex(COLUMN_BLOCK_ENABLED)) == 1,
            blockPriority = cursor.getInt(cursor.getColumnIndex(COLUMN_BLOCK_PRIORITY)),
        )
    }

    override fun toContentValues(item: SmsAiCategory, includeId: Boolean): ContentValues {
        val cv = ContentValues()
        if (includeId) {
            cv.put(COLUMN_ID, item.id)
        }
        cv.put(COLUMN_NAME, item.name)
        cv.put(COLUMN_DESC, item.description)
        cv.put(COLUMN_ALLOW_ENABLED, if (item.allowEnabled) 1 else 0)
        cv.put(COLUMN_ALLOW_PRIORITY, item.allowPriority)
        cv.put(COLUMN_BLOCK_ENABLED, if (item.blockEnabled) 1 else 0)
        cv.put(COLUMN_BLOCK_PRIORITY, item.blockPriority)
        return cv
    }

    fun defaultCategories(): List<SmsAiCategory> {
        fun blocked(name: String, description: String) = SmsAiCategory(
            name = name,
            description = description,
            allowEnabled = false,
            blockEnabled = true,
            blockPriority = 0,
        )
        fun allowed(name: String, description: String) = SmsAiCategory(
            name = name,
            description = description,
            allowEnabled = true,
            allowPriority = 10,
            blockEnabled = false,
        )
        return listOf(
            blocked(
                "Scam",
                "fake bank, IRS, locked account, click to pay. e.g. \"Your account is suspended. Verify now: http://bit.ly/xx\"",
            ),
            blocked(
                "Ad",
                "sale, coupon, survey, or campaign. e.g. \"50% off this weekend only. Shop: https://deals.example\"",
            ),
            blocked(
                "Order",
                "package tracking or a booked appointment. e.g. \"Your package 8821 is out for delivery today\"",
            ),
            allowed(
                "OTP",
                "only a passcode of digits to type in. e.g. \"123456 is your verification code\"",
            ),
            allowed(
                "Chat",
                "a person texting you. e.g. \"On my way, 10 min\"",
            ),
        )
    }

    fun ensureDefaults(ctx: Context) {
        val spf = spf.SmsAi(ctx)
        if (spf.defaultsSeeded) {
            return
        }
        if (count(ctx) == 0) {
            defaultCategories().forEach { addNew(ctx, it) }
        }
        if (spf.prompt.isEmpty()) {
            spf.prompt = Def.DEFAULT_SMS_AI_PROMPT
        }
        spf.defaultsSeeded = true
    }

    fun resetToDefaults(ctx: Context) {
        val spf = spf.SmsAi(ctx)
        clearAll(ctx)
        defaultCategories().forEach { addNew(ctx, it) }
        spf.prompt = Def.DEFAULT_SMS_AI_PROMPT
        spf.defaultsSeeded = true
    }
}
