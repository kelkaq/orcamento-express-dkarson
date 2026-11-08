package com.example.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * O Pro só é concedido após validação na API Google Play pelo backend seguro.
 * Cache de no máximo 72h para uso offline, sem ultrapassar o vencimento confirmado.
 * Nenhum sinal de compra local, flag de debug ou preferência do usuário ativa o Pro.
 */
class EntitlementStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("dkarson_verified_entitlement_v1", Context.MODE_PRIVATE)

    private val _pro = MutableStateFlow(isProNow())
    val pro: StateFlow<Boolean> = _pro

    @Synchronized
    fun isProNow(now: Long = System.currentTimeMillis()): Boolean {
        val expiry = preferences.getLong("expiry", 0L)
        val checkedAt = preferences.getLong("checked", 0L)
        val cacheAge = now - checkedAt
        return expiry > now && checkedAt > 0L &&
            cacheAge >= -300_000L && cacheAge <= 72L * 60L * 60L * 1_000L
    }

    @Synchronized
    fun grantVerified(expiry: Long, now: Long = System.currentTimeMillis()) {
        if (expiry <= now) {
            revoke()
            return
        }
        preferences.edit().putLong("expiry", expiry).putLong("checked", now).apply()
        _pro.value = isProNow(now)
    }

    @Synchronized
    fun revoke() {
        preferences.edit().remove("expiry").remove("checked").apply()
        _pro.value = false
    }

    fun refreshLocalState() {
        _pro.value = isProNow()
    }
}
