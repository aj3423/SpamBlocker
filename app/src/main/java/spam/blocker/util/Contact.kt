package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.PhoneLookup

data class ContactInfo(
    val id: String,
    val name: String,
    val iconUri: String? = null
) {
    // icon is Bitmap
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
    fun findContactByRawNumber(ctx: Context, rawNumber: String): ContactInfo? {
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
                    PhoneLookup.CONTACT_ID,
                    PhoneLookup.DISPLAY_NAME,
                    PhoneLookup.PHOTO_URI
                ),
                null,
                null,
                null
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val idIndex = it.getColumnIndex(PhoneLookup.CONTACT_ID)
            val nameIndex = it.getColumnIndex(PhoneLookup.DISPLAY_NAME)
            val iconIndex = it.getColumnIndex(PhoneLookup.PHOTO_URI)

            while (it.moveToNext()) {
                val ci = ContactInfo(
                    id = it.getString(idIndex),
                    name = it.getString(nameIndex),
                    iconUri = it.getString(iconIndex)
                )
//                logd("---- contact matches, name: ${ci.name}, icon: ${ci.iconUri}")
                return ci
            }
        }
        return null
    }

    // Find a list of groups that contain this number,
    // returns the group names
    @SuppressLint("Range")
    fun findGroupsByRawNumber(ctx: Context, rawNumber: String): List<String> {
        val groupNames = mutableListOf<String>()

        if (!Permissions.isContactsPermissionGranted(ctx)) {
            return groupNames
        }

        val contactId = findContactByRawNumber(ctx, rawNumber)?.id

        if (contactId != null) {

            // Query to get the group IDs this contact belongs to
            val groupCursor = ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(GroupMembership.GROUP_ROW_ID),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId, GroupMembership.CONTENT_ITEM_TYPE),
                null
            )

            // iterate all groups
            groupCursor?.use { gc ->
                while (gc.moveToNext()) {
                    val groupId = gc.getString(gc.getColumnIndex(GroupMembership.GROUP_ROW_ID))

                    // Query the group details to get the group name
                    val cursor = ctx.contentResolver.query(
                        ContactsContract.Groups.CONTENT_URI,
                        arrayOf(ContactsContract.Groups.TITLE),
                        "${ContactsContract.Groups._ID} = ?",
                        arrayOf(groupId),
                        null
                    )

                    cursor?.use { gcDetail ->
                        if (gcDetail.moveToFirst()) {
                            val groupName =
                                gcDetail.getString(gcDetail.getColumnIndex(ContactsContract.Groups.TITLE))
                            groupNames.add(groupName)
                        }
                    }
                }
            }
        }

        return groupNames
    }
}