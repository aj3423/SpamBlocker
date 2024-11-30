package spam.blocker.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.getSystemService
import spam.blocker.def.Def.ANDROID_11
import spam.blocker.def.Def.ANDROID_12

class PhoneNumber(private val ctx: Context, private val rawNumber: String) {
    private val candidateCountries: Set<String> by lazy {
        determinePossibleCountries()
    }

    fun isSame(otherNumber: String): Boolean {
        if (rawNumber == otherNumber) { // short circuit
            return true
        }
        if (Build.VERSION.SDK_INT >= ANDROID_12) {
            candidateCountries.forEach {
                if(PhoneNumberUtils.areSamePhoneNumber(rawNumber, otherNumber, it)) {
                    return true
                }
            }
        } else {
            @Suppress("deprecation") // deliberately using old API for old devices
            if(PhoneNumberUtils.compare(ctx, rawNumber, otherNumber)) {
                return true
            }
        }
        return false
    }

    @SuppressLint("MissingPermission")
    private fun determinePossibleCountries(): Set<String> {
        val codes = mutableSetOf<String?>()
        // Determine country for each mobile network the device is currently in:
        val telephony = ctx.getSystemService<TelephonyManager>()!!
        codes += telephony.networkCountryIso
        if (Build.VERSION.SDK_INT >= ANDROID_11) {
            val slots = telephony.activeModemCount
            for (slot in 0..<slots) {
                try {
                    codes += telephony.getNetworkCountryIso(slot)
                } catch (e: IllegalArgumentException) {
                    // Prevent failing due to race condition:
                    Log.d("slot", "Rejected slot $slot (of total $slots)", e)
                }
            }
        }
        // Determine the country for each of the SIM cards:
        if (Permissions.isPhoneStatePermissionGranted(ctx)) {
            val subscription = ctx.getSystemService<SubscriptionManager>()
            subscription?.activeSubscriptionInfoList?.forEach { sub ->
                codes += sub.countryIso
            }
        }

        return codes
            .distinct()
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .map { it.uppercase() }
            .toSet()
            .ifEmpty { setOf("ZZ") } // ZZ = unknown country
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as PhoneNumber
        return rawNumber == other.rawNumber
    }

    override fun hashCode(): Int {
        return rawNumber.hashCode()
    }
}
