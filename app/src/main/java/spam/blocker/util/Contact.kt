package spam.blocker.util

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.PhoneLookup

class ContactInfo {
    var name = ""
    var iconUri: String? = null // icon is Bitmap

    fun loadAvatar(ctx: Context): Bitmap? {
        if (iconUri == null) {
            return null
        }
        try {
            val source = ImageDecoder.createSource(ctx.contentResolver, Uri.parse(iconUri))

            val icon = ImageDecoder.decodeBitmap(source)
            // convert hardware bitmap to software bitmap, otherwise it shows error:
            //   "Software rendering doesn't support hardware bitmaps"
            return icon.copy(Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            return null
        }
    }
}


object Contacts {

    fun findByRawNumber(ctx: Context, rawNumber: String): ContactInfo? {
        if (!Permissions.isContactsPermissionGranted(ctx)) {
            return null
        }

        val uri = Uri.withAppendedPath(
            PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )

        val cursor: Cursor? = try {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    Contacts.DISPLAY_NAME,
                    Contacts.PHOTO_URI
                ),
                null,
                null,
                null
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val nameIndex = it.getColumnIndex(Contacts.DISPLAY_NAME)
            val iconIndex = it.getColumnIndex(Contacts.PHOTO_URI)

            while (it.moveToNext()) {

                val ci = ContactInfo()

                ci.name = it.getString(nameIndex)
                ci.iconUri = it.getString(iconIndex)

                logd("---- contact matches, name: ${ci.name}, icon: ${ci.iconUri}")
                return ci
            }
        }
        return null
    }
}