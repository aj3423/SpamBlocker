package spam.blocker.ui.main

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import spam.blocker.util.loge

fun debug(ctx: Context) {
    val cc = 86
    val clearedNumber = "49211123456"
    val rest = clearedNumber.substring(cc.toString().length)

    val pnu = PhoneNumberUtil.getInstance()
    val n = Phonenumber.PhoneNumber().apply {
        countryCode = cc
        nationalNumber = rest.toLong()
    }
    if (pnu.isValidNumber(n)) { // the number start with CC
        loge("yes")
    } else {
        loge("invalid...")
    }
}