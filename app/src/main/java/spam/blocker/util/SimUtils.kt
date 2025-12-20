package spam.blocker.util

import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import spam.blocker.def.Def.ANDROID_11
import spam.blocker.def.Def.ANDROID_12


data class SimInfo(
    val slotIndex: Int,          // Physical slot (0, 1, ...)
    val subscriptionId: Int
)


object SimUtils {
    fun simCount(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < ANDROID_12) {
            return 0
        }
        val telephony = ctx.getSystemService<TelephonyManager>()!!
        return telephony.activeModemCount
    }

    fun listSimCards(ctx: Context): List<SimInfo> {
        if (Build.VERSION.SDK_INT < ANDROID_12)
            return listOf()
        if (!Permission.phoneState.isGranted)
            return listOf()

        val simList = mutableListOf<SimInfo>()

        val subsManager = ctx.getSystemService<SubscriptionManager>()

        subsManager?.activeSubscriptionInfoList?.forEach { info ->
            simList.add(
                SimInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId
                )
            )
        }

        return simList
    }

    fun isSimSlotRinging(ctx: Context, slotIndex: Int): Boolean {
        if (Build.VERSION.SDK_INT < ANDROID_12)
            return false
        if (!Permission.phoneState.isGranted)
            return false

        val telManager = ctx.getSystemService<TelephonyManager>()
        val subsManager = ctx.getSystemService<SubscriptionManager>()

        return try {
            telManager
                ?.createForSubscriptionId(subsManager
                    ?.getActiveSubscriptionInfoForSimSlotIndex(slotIndex)
                    ?.subscriptionId ?: return false)
                ?.callStateForSubscription == TelephonyManager.CALL_STATE_RINGING
        } catch (_: SecurityException) {
            false
        }
    }
    fun getRingingSimSlot(ctx: Context) : Int? {
        return listSimCards(ctx).firstOrNull {
            isSimSlotRinging(ctx, it.slotIndex)
        }?.slotIndex
    }
}