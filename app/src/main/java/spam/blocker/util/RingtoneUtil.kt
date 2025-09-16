package spam.blocker.util

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.MediaStore
import spam.blocker.R

object RingtoneUtil {
    fun getCurrent(ctx: Context) : Uri {
        return RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
    }

    fun setDefaultUri(ctx: Context, uri: Uri) {
        logi("set default ringtone to: $uri")

        RingtoneManager.setActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE, uri)
    }

    fun getName(ctx: Context, uri: Uri): String {
        return try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    // Prefer TITLE for system ringtones, fall back to DISPLAY_NAME
                    if (titleIndex >= 0 && !cursor.isNull(titleIndex)) {
                        cursor.getString(titleIndex)
                    } else if (displayNameIndex >= 0 && !cursor.isNull(displayNameIndex)) {
                        cursor.getString(displayNameIndex).removeSuffix(".wav")
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        } ?: ctx.getString(R.string.unknown)
    }
}