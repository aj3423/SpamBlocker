package spam.blocker.util

import android.Manifest
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.def.Def.ANDROID_13


data class SimInfo(
    val slotIndex: Int,          // Physical slot (0, 1, ...)
    val subscriptionId: Int,
    val number: String? = null
)


object SimUtils {
    fun simCount(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < ANDROID_12) {
            return 0
        }
        if (!Permission.phoneState.isGranted) {
            return 0
        }
        return try {
            val subscriptionManager = ctx.getSystemService<SubscriptionManager>()!!
            return subscriptionManager.activeSubscriptionInfoList?.size ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun listSimCards(ctx: Context): List<SimInfo> {
        if (Build.VERSION.SDK_INT < ANDROID_12)
            return listOf()
        if (!Permission.phoneState.isGranted)
            return listOf()

        val simList = mutableListOf<SimInfo>()

        val subsManager = ctx.getSystemService<SubscriptionManager>()

        subsManager?.activeSubscriptionInfoList?.forEach { info ->
            val number = try {
                when {
                    Build.VERSION.SDK_INT >= ANDROID_13 ->
                        subsManager.getPhoneNumber(info.subscriptionId).ifEmpty { null }

                    Build.VERSION.SDK_INT == ANDROID_12 ->
                        info.number?.ifEmpty { null }

                    else -> null
                }
            } catch (e: Exception) {
                null
            }

            simList.add(
                SimInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    number = number
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