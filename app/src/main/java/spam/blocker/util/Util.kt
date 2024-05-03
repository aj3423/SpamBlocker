package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LifecycleOwner
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import spam.blocker.R
import spam.blocker.db.ContentFilterTable
import spam.blocker.db.NumberFilterTable
import spam.blocker.db.PatternTable
import spam.blocker.def.Def
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class Util {
    companion object {
        fun fullDateString(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd\nHH:mm", Locale.getDefault())
            val date = Date(timestamp)
            return dateFormat.format(date)
        }

        fun hourMin(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = Date(timestamp)
            return dateFormat.format(date)
        }

        fun isToday(timestamp: Long): Boolean {
            val calendar = Calendar.getInstance()
            val currentDate = calendar.get(Calendar.DAY_OF_MONTH)
            calendar.timeInMillis = timestamp
            val date = calendar.get(Calendar.DAY_OF_MONTH)
            return currentDate == date
        }

        fun getDayOfWeek(timestamp: Long): String {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = timestamp
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val daysArray = arrayOf(
                "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
            )
            return daysArray[dayOfWeek - 1]
        }

        fun isWithinAWeek(timestamp: Long): Boolean {
            val currentTimeMillis = System.currentTimeMillis()
            val difference = currentTimeMillis - timestamp
            val millisecondsInWeek = 7 * 24 * 60 * 60 * 1000 // 7 days in milliseconds
            return difference <= millisecondsInWeek
        }

        fun applyTheme(dark: Boolean) {
            if (dark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }



        // for round avatar
        fun setRoundImage(imageView: ImageView, bitmap: Bitmap) {
            val circularBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(circularBitmap)
            val paint = Paint().apply {
                shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                isAntiAlias = true
                isDither = true
                isFilterBitmap = true
            }
            val radius = bitmap.width.coerceAtMost(bitmap.height) / 2f
            canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, radius, paint)
            imageView.setImageBitmap(circularBitmap)
        }


        val pattern = "^[0-9\\s\\+\\-\\(\\)]*\$".toRegex()
        fun clearNumber(number: String): String {
            // check it's a phone number or some caller like "Microsoft"
            if (!pattern.matches(number)) { // don't clear for "Microsoft"
                return number
            }

            return number.replace("-", "")
                .replace("+", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "")
        }

        private fun isRegexValid(regex: String): Boolean {
            return try {
                Regex(regex)
                true
            } catch (e: Exception) {
                false
            }
        }
        private fun hasTrailingSpacesOrNewlines(regexStr: String): Boolean {
            return regexStr.isNotEmpty() && regexStr.trim() != regexStr
        }
        fun validateRegex(ctx: Context, regexStr: String) : Pair<Boolean, String> {
            if (hasTrailingSpacesOrNewlines(regexStr))
                return Pair(false, ctx.getString(R.string.pattern_contain_trailing_space))
            return if (isRegexValid(regexStr))
                Pair(true, "")
            else
                Pair(false, ctx.getString(R.string.invalid_regex_pattern))
        }


        fun isInt(str: String): Boolean {
            return str.toIntOrNull() != null
        }

        private var cacheAppList : List<AppInfo>? = null
        @SuppressLint("UseCompatLoadingForDrawables")
        fun listApps(ctx: Context): List<AppInfo> {
            if (cacheAppList == null) {
                val packageManager = ctx.packageManager

                cacheAppList = packageManager.getInstalledApplications(
                    PackageManager.MATCH_DISABLED_COMPONENTS or
                            PackageManager.MATCH_UNINSTALLED_PACKAGES or
                            PackageManager.GET_META_DATA
                ).filter { appInfo ->
                    (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }.map {
                    val ret = AppInfo()
                    ret.pkgName = it.packageName
                    ret.label = packageManager.getApplicationLabel(it).toString()
                    try {
                        ret.icon = packageManager.getApplicationIcon(it)
                    } catch (e: PackageManager.NameNotFoundException) {
                        ret.icon = ctx.getDrawable(R.drawable.android_24)!!
                    }
                    ret
                }
            }

            return cacheAppList!!
        }

        private var cacheAppMap : Map<String, AppInfo>? = null
        fun getAppsMap(ctx: Context): Map<String, AppInfo> {
            if (cacheAppMap == null) {
                cacheAppMap = listApps(ctx).associateBy { it.pkgName }
            }
            return cacheAppMap!!
        }

        // setup the hint from the imgView.tooltipText
        fun setupImageTooltip(ctx: Context, viewLifecycleOwner: LifecycleOwner, imgView: ImageView, strId: Int, alignBottom: Boolean = true) {
            imgView.setOnClickListener {
                val balloon = Balloon.Builder(ctx)
//                    .setWidthRatio(0.9f)
                    .setHeight(BalloonSizeSpec.WRAP)
                    .setText(ctx.resources.getText(strId))
                    .setTextIsHtml(true)
                    .setTextColorResource(R.color.white)
                    .setTextSize(15f)
                    .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                    .setArrowSize(10)
                    .setArrowPosition(0.5f)
                    .setPadding(8)
                    .setTextGravity(Gravity.START)
                    .setCornerRadius(8f)
                    .setBackgroundColor(ctx.resources.getColor(R.color.tooltip_blue, null))
                    .setBalloonAnimation(BalloonAnimation.ELASTIC)
                    .setLifecycleOwner(viewLifecycleOwner)
                    .build()

                balloon.setIsAttachedInDecor(false)
                if (alignBottom) {
                    balloon.showAlignBottom(imgView)
                } else {
                    balloon.showAlignTop(imgView)
                }
            }
        }
        fun preventMenuClosingWhenItemClicked(ctx: Context, item: MenuItem) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
            item.actionView = View(ctx)
            item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return false
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    return false
                }
            })
        }

        private fun reasonStr(ctx: Context, filterTable: PatternTable, reason: String) : String {
            val f = filterTable.findPatternFilterById(ctx, reason.toLong())

            val reasonStr = if (f != null) {
                if (f.description != "") f.description else f.patternStr()
            } else {
                ctx.resources.getString(R.string.deleted_filter)
            }
            return reasonStr
        }
        fun resultStr(ctx: Context, result: Int, reason: String): String {
            return when (result) {
                Def.RESULT_ALLOWED_BY_CONTACT ->  ctx.resources.getString(R.string.contact)
                Def.RESULT_BLOCKED_BY_NON_CONTACT ->  ctx.resources.getString(R.string.non_contact)

                Def.RESULT_ALLOWED_BY_RECENT_APP ->  ctx.resources.getString(R.string.recent_app) + ": "
                Def.RESULT_ALLOWED_BY_REPEATED ->  ctx.resources.getString(R.string.repeated_call)
                Def.RESULT_ALLOWED_BY_NUMBER ->  ctx.resources.getString(R.string.whitelist) + ": " + reasonStr(
                    ctx, NumberFilterTable(), reason)
                Def.RESULT_BLOCKED_BY_NUMBER ->  ctx.resources.getString(R.string.blacklist) + ": " + reasonStr(
                    ctx, NumberFilterTable(), reason)
                Def.RESULT_ALLOWED_BY_CONTENT ->  ctx.resources.getString(R.string.content) + ": " + reasonStr(
                    ctx, ContentFilterTable(), reason)
                Def.RESULT_BLOCKED_BY_CONTENT ->  ctx.resources.getString(R.string.content) + ": " + reasonStr(
                    ctx, ContentFilterTable(), reason)

                else -> ctx.resources.getString(R.string.passed_by_default)
            }
        }

        fun splitCcPhone(str: String): Pair<String, String>? {
            val matcher = Pattern.compile("^([17]|2[07]|3[0123469]|4[013456789]|5[12345678]|6[0123456]|8[1246]|9[0123458]|\\d{3})\\d*?(\\d{4,6})$").matcher(str);
            if (!matcher.find()) {
                return null
            }
            val cc = matcher.group(1) ?: return null

            val phone = str.substring(cc.length)

//            Log.d(Def.TAG, "cc: $cc, g2: $phone")
            return Pair(cc, phone)
        }

    }
}