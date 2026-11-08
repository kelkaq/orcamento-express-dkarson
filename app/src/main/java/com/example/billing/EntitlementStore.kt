package com.example.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PlanTier { FREE, OFFLINE, ONLINE }

/**
 * Aquisições só são concedidas por resultado validado no backend via Google Play API.
 * Compra única é vitalícia (salvo reembolso detectado em restauração).
 * Assinatura online mantém cache offline por até 72 horas e nunca além da expiração.
 */
class EntitlementStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("dkarson_entitlement_v2", Context.MODE_PRIVATE)
    private val _tier = MutableStateFlow(tierNow())
    val tier: StateFlow<PlanTier> = _tier

    @Synchronized
    fun hasOfflinePurchase(): Boolean = prefs.getBoolean("one_time_verified", false)

    @Synchronized
    fun isOnlineNow(now: Long = System.currentTimeMillis()): Boolean {
        val expiry = prefs.getLong("online_expiry", 0L)
        val verifiedAt = prefs.getLong("online_checked", 0L)
        val age = now - verifiedAt
        return expiry > now && verifiedAt > 0 &&
            age >= -300_000L && age <= 72L * 60L * 60L * 1_000L
    }

    @Synchronized
    fun isProNow(): Boolean = hasOfflinePurchase() || isOnlineNow()

    @Synchronized
    fun tierNow(): PlanTier = when {
        isOnlineNow() -> PlanTier.ONLINE
        hasOfflinePurchase() -> PlanTier.OFFLINE
        else -> PlanTier.FREE
    }

    private fun refresh() { _tier.value = tierNow() }

    @Synchronized
    fun grantOfflineVerified() {
        prefs.edit().putBoolean("one_time_verified", true).apply()
        refresh()
    }

    @Synchronized
    fun revokeOffline() {
        prefs.edit().remove("one_time_verified").apply()
        refresh()
    }

    @Synchronized
    fun grantOnlineVerified(expiry: Long, now: Long = System.currentTimeMillis()) {
        if (expiry <= now) { revokeOnline(); return }
        prefs.edit()
            .putLong("online_expiry", expiry)
            .putLong("online_checked", now)
            .apply()
        refresh()
    }

    @Synchronized
    fun revokeOnline() {
        prefs.edit().remove("online_expiry").remove("online_checked").apply()
        refresh()
    }

    fun refreshLocalState() = refresh()
}
