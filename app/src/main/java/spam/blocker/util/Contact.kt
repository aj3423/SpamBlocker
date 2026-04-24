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


class ContactsCache {
    private val cache = mutableMapOf<String, ContactInfo?>()

    fun findContactByRawNumber(ctx: Context, rawNumber: String): ContactInfo? {
        return if (cache.containsKey(rawNumber))  {
            cache[rawNumber]
        } else {
            val result = Contacts.findContactByRawNumber(ctx, rawNumber)
            cache[rawNumber] = result  // store even if null
            result
        }
    }
}

object Contacts {
    val cache = ContactsCache()

    fun findContactByRawNumber(ctx: Context, rawNumber: String): ContactInfo? {
        if (!Permission.contacts.isGranted) {
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

    fun findContactByNumberPrefix(
        ctx: Context,
        rawNumber: String, // This function assumes the `incomingNumber` matches the `pattern`
        pattern: String,
        patternFlags: Int,
    ): ContactInfo? {
        if (!Permission.contacts.isGranted) {
            return null
        }
        val tolerance = pattern.takeLastWhile { it == '.' }.length

        // 1. Process `Ignore Country Code` and `Raw Number` first
        val number = rawNumber.applyRegexFlags(patternFlags)

        if (number.length <= tolerance) {
            return null
        }

        val prefixLength = number.length - tolerance
        val prefix = number.substring(0, prefixLength)

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val cursor = try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,   // No selection → fetch all (usually fast enough)
                null,
                null
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (it.moveToNext()) {
                val contactNumber = it.getString(numberIndex) ?: continue
                // Remove these characters: ( ) -
                //   "+1 (555) 999-0000" -> "+15559990000"
                val cleanContactNumber = contactNumber.replace(Regex("[^0-9]+"), "")

                // The number must match the regex
                if (!pattern.regexMatchesNumber(cleanContactNumber, patternFlags)) {
                    continue
                }

                // Check if the contact number starts with the prefix AND has 'tolerance' more digits
                if (cleanContactNumber.startsWith(prefix) &&
                    cleanContactNumber.length == prefix.length + tolerance) {

                    return ContactInfo(
                        id = it.getString(idIndex) ?: "",
                        name = it.getString(nameIndex) ?: "",
                        iconUri = it.getString(photoIndex)
                    )
                }
            }
        }

        return null
    }

    // Find a list of groups that contain this number,
    // returns the group names
    @SuppressLint("Range")
    fun findGroupsContainNumber(ctx: Context, rawNumber: String): List<String> {
        val groupNames = mutableListOf<String>()

        if (!Permission.contacts.isGranted) {
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