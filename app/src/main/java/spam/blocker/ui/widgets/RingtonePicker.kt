package spam.blocker.ui.widgets

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.media.RingtoneManager.TYPE_NOTIFICATION
import android.media.RingtoneManager.TYPE_RINGTONE
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R

@Composable
fun RingtonePicker(
    trigger: MutableState<Boolean>,
    onRingtoneSelected: (String?, String?) -> Unit
) {
    val ctx = LocalContext.current

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        trigger.value = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // Get the display name for the selected URI
            val displayName = uri?.let { getRingtoneName(ctx, it) }
            onRingtoneSelected(uri?.toString(), displayName)
        } else {
            onRingtoneSelected(null, null)
        }
    }

    // Launch the ringtone picker when trigger changes to true
    if (trigger.value) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, TYPE_NOTIFICATION or TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, ctx.getString(R.string.select_notification_sound))
        }
        ringtonePickerLauncher.launch(intent)
    }
}

fun getRingtoneName(ctx: Context, uri: Uri): String {
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
