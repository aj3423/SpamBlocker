package spam.blocker.util

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.Permission.Companion.isContactsPermissionGranted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

        private var cacheContacts: ArrayList<ContactInfo>? = null
        @SuppressLint("Range")
        fun readContacts(ctx: Context): ArrayList<ContactInfo> {

            if (cacheContacts == null) {
                if (!isContactsPermissionGranted(ctx)) {
                    return arrayListOf()
                }

                val ret = arrayListOf<ContactInfo>()

                val cursor = ctx.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.Contacts.PHOTO_URI
                    ),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val iconIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                    while (it.moveToNext()) {
                        val number = it.getString(numberIndex)
                        val name = it.getString(nameIndex)
                        val iconUri = it.getString(iconIndex)

                        val ci = ContactInfo()
                        ci.name = name
                        ci.phone = clearNumber(number)
                        Log.d(Def.TAG, "read contact ${ci.phone}")

                        if (iconUri != null) {
                            val source = ImageDecoder.createSource(ctx.contentResolver, Uri.parse(iconUri))
                            ci.icon = ImageDecoder.decodeBitmap(source)
                            // convert hardware bitmap to software bitmap, otherwise it shows error:
                            //   "Software rendering doesn't support hardware bitmaps"
                            ci.icon = ci.icon!!.copy(Bitmap.Config.ARGB_8888, false)
                        }
                        ret.add(ci)
                    }
                }
                cacheContacts = ret
            }
            return cacheContacts!!
        }
        fun findContact(ctx: Context, phone: String): ContactInfo? {
            return readContacts(ctx).find { it.phone == clearNumber(phone) }
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

        fun clearNumber(number: String): String {
            return number.replace("-", "")
                .replace("+", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "")
        }

        fun isRegexValid(regex: String): Boolean {
            return try {
                Regex(regex)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun showNotification(ctx: Context, notificationId: Int, title: String, body: String, silent: Boolean, callbackIntent: Intent) {
            val builder = NotificationCompat.Builder(ctx, "spam.blocker")
            if (silent) {
                builder.setSilent(true) // disable the notification sound
            }
            val channel = NotificationChannel("spam.blocker", "spam.blocker", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager

            manager.createNotificationChannel(channel)
            builder.setChannelId("spam.blocker").
            setContentTitle(title).
            setSmallIcon(R.drawable.bell_filter).
            setContentText(body)


            val pendingIntent = PendingIntent.getActivity(ctx, 0, callbackIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.setContentIntent(pendingIntent)

            val notification = builder.build()
            manager.notify(notificationId, notification)
        }
        fun cancelNotification(ctx: Context) {
            val manager = ctx.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
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

        fun isPatternValid(pattern: String): Boolean {
            return try {
                Regex(pattern)
                true
            } catch (e: Exception) {
                false
            }
        }


        // setup the hint from the imgView.tooltipText
        fun setupImgHint(ctx: Context, viewLifecycleOwner: LifecycleOwner, imgView: ImageView) {
            imgView.setOnClickListener {
                Balloon.Builder(ctx)
                    .setWidthRatio(0.8f)
                    .setHeight(BalloonSizeSpec.WRAP)
                    .setText(imgView.tooltipText.toString())
                    .setTextColorResource(R.color.white)
                    .setTextSize(15f)
                    .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                    .setArrowSize(10)
                    .setArrowPosition(0.5f)
                    .setPadding(12)
                    .setCornerRadius(8f)
                    .setBackgroundColorResource(R.color.dodger_blue)
                    .setBalloonAnimation(BalloonAnimation.ELASTIC)
                    .setLifecycleOwner(viewLifecycleOwner)
                    .build().showAlignBottom(imgView)
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
    }
}