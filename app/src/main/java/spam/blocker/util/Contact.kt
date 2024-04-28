package spam.blocker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import spam.blocker.def.Def

class ContactInfo {
    var name = ""
    var rawPhone = ""
    var iconUri: String? = null // icon is Bitmap

    fun clearedPhone(): String {
        return Util.clearNumber(rawPhone)
    }

    fun loadAvatar(ctx: Context): Bitmap? {
        if (iconUri == null) {
            return null
        }
        val source = ImageDecoder.createSource(ctx.contentResolver, Uri.parse(iconUri))

        val icon = ImageDecoder.decodeBitmap(source)
        // convert hardware bitmap to software bitmap, otherwise it shows error:
        //   "Software rendering doesn't support hardware bitmaps"
        return icon.copy(Bitmap.Config.ARGB_8888, false)
    }
}


class Contacts {
    companion object {
        // regex for splitting the cc and the rest, e.g.:
        //   +1 23-45
        // ->
        //   1 and 23-45
        private val patternCCPhone = "^\\+(\\d+)\\s(.+)".toRegex()

        /*
        The logic:
            1. if number contains CC:
                - search CC+phone first
                - if not found, remove CC and search again
            2. if number doesn't contain CC:
                - search it

            The compare logic is checking whether a contact number ends with the target number,
            for example: a call number 123 with match both +66123 and +77123.
         */
        fun findByRawNumberAuto(ctx: Context, rawNumber: String): ContactInfo? {
            if (!Permission.isContactsPermissionGranted(ctx)) {
                return null
            }

            val m = patternCCPhone.find(rawNumber)
            return if (m != null) { // contains CC
                var contact = findByExactNumber(ctx, Util.clearNumber(rawNumber))
                if (contact == null) {
                    val restPart = m.groupValues[2]

                    contact = findByExactNumber(ctx, Util.clearNumber(restPart))
                }
                contact
            } else { // doesn't contain CC
                findByExactNumber(ctx, Util.clearNumber(rawNumber))
            }
        }

        private fun findByExactNumber(ctx: Context, number: String): ContactInfo? {
            val all = findAllEndWith(ctx, number)
            val filtered = all.filter {
                if (number == it.clearedPhone()) { // exact match
                    true
                } else { // contact_number is longer than incoming number
                    // Split contact number into cc and phone using regex,
                    // then check if the parsed phone matches incoming number
                    val ccPhone = Util.checkNum(it.clearedPhone())
                    if (ccPhone == null) { // the contact's number can't be parsed to cc+phone
                        false
                    } else {
                        ccPhone.second == number
                    }
                }
            }
            return if (filtered.isEmpty())
                null
            else
                all.first()
        }

        private fun findAllEndWith(ctx: Context, number: String): ArrayList<ContactInfo> {

            val ret = arrayListOf<ContactInfo>()

            val cursor = ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.Contacts.PHOTO_URI
                ),
                "REPLACE(REPLACE(REPLACE(${ContactsContract.CommonDataKinds.Phone.NUMBER}, '-', ''), ' ', ''), '+', '') LIKE ?",
                arrayOf("%$number"),
                null
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val iconIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)

                while (it.moveToNext()) {

                    val ci = ContactInfo()

                    ci.name = it.getString(nameIndex)
                    ci.rawPhone = it.getString(numberIndex)
                    ci.iconUri = it.getString(iconIndex)

                    Log.d(
                        Def.TAG,
                        "---- contact matches, name: ${ci.name}, raw: ${ci.rawPhone}, icon: ${ci.iconUri}"
                    )

                    ret.add(ci)
                }
            }
            return ret
        }

    }
}