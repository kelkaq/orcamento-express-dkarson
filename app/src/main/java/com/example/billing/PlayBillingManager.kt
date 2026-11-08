package com.example.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class BillingDisplayState(
    val tier: PlanTier = PlanTier.FREE,
    val isPro: Boolean = false,
    val isOnline: Boolean = false,
    val isLoading: Boolean = true,
    val canBuyOffline: Boolean = false,
    val canSubscribeOnline: Boolean = false,
    val offlinePrice: String? = null,
    val onlinePrice: String? = null,
    val message: String = "Consultando a Google Play...",
    val purchasePending: Boolean = false
)

/**
 * Os produtos estão definidos na Google Play, não no APK.
 * Nunca libera uma compra apenas pelo callback de compra: o verificador privado
 * consulta a API Android Publisher e reconhece a transação com segurança.
 */
class PlayBillingManager(
    context: Context,
    private val entitlement: EntitlementStore
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val verifyUrl = BuildConfig.BILLING_VERIFY_URL
    private val _state = MutableStateFlow(
        BillingDisplayState(
            tier = entitlement.tierNow(),
            isPro = entitlement.isProNow(),
            isOnline = entitlement.isOnlineNow()
        )
    )
    val state: StateFlow<BillingDisplayState> = _state

    private var offlineDetails: ProductDetails? = null
    private var offlineOffer: String? = null
    private var onlineDetails: ProductDetails? = null
    private var onlineOffer: String? = null
    @Volatile private var confirmedOnlinePurchaseToken: String? = null
    fun onlinePurchaseToken(): String? = confirmedOnlinePurchaseToken

    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    private fun refresh(
        message: String? = null,
        loading: Boolean? = null,
        pending: Boolean? = null
    ) {
        entitlement.refreshLocalState()
        val old = _state.value
        _state.value = old.copy(
            tier = entitlement.tierNow(),
            isPro = entitlement.isProNow(),
            isOnline = entitlement.isOnlineNow(),
            isLoading = loading ?: old.isLoading,
            canBuyOffline = offlineDetails != null && offlineOffer != null &&
                verifyUrl.startsWith("https://") && !entitlement.hasOfflinePurchase(),
            canSubscribeOnline = onlineDetails != null && onlineOffer != null &&
                verifyUrl.startsWith("https://") && !entitlement.isOnlineNow(),
            message = message ?: old.message,
            purchasePending = pending ?: old.purchasePending
        )
    }

    private fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restorePurchases()
                } else refresh("Google Play indisponível: ${result.debugMessage}", loading = false)
            }
            override fun onBillingServiceDisconnected() {
                refresh("A Google Play desconectou. Abra Planos para tentar novamente.", loading = false)
            }
        })
    }

    private fun queryProducts() {
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PlanPolicy.OFFLINE_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PlanPolicy.PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS).build()
        )
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        ) { result, response ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                response.productDetailsList else emptyList()

            val offline = found.firstOrNull { it.productId == PlanPolicy.OFFLINE_PRODUCT_ID }
            val off = offline?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            offlineDetails = if (off != null) offline else null
            offlineOffer = off?.offerToken

            val online = found.firstOrNull { it.productId == PlanPolicy.PRODUCT_ID }
            val sub = online?.subscriptionOfferDetails
                ?.firstOrNull { it.basePlanId == PlanPolicy.BASE_PLAN_ID }
            onlineDetails = if (sub != null) online else null
            onlineOffer = sub?.offerToken

            _state.value = _state.value.copy(
                offlinePrice = off?.formattedPrice,
                onlinePrice = sub?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            )
            refresh(
                if (!verifyUrl.startsWith("https://"))
                    "O desenvolvedor ainda não habilitou a verificação de pagamentos."
                else if (offlineDetails == null && onlineDetails == null)
                    "Planos ainda não cadastrados ou não disponíveis para este usuário da Google Play."
                else "Preços oficiais consultados na Google Play.",
                loading = false
            )
        }
    }

    fun buyOffline(activity: Activity) = launchPurchase(activity, offlineDetails, offlineOffer)
    fun buyOnline(activity: Activity) = launchPurchase(activity, onlineDetails, onlineOffer)

    private fun launchPurchase(activity: Activity, details: ProductDetails?, token: String?) {
        if (details == null || token == null || !verifyUrl.startsWith("https://")) {
            refresh("Compra indisponível. Confira produtos e verificador na Google Play.")
            return
        }
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details).setOfferToken(token).build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            refresh("Não foi possível iniciar a compra: ${result.debugMessage}")
        }
    }

    fun restorePurchases() {
        refresh("Verificando compras na Google Play...", loading = true)
        if (!client.isReady) {
            // A conexão inicial faz a consulta, sem startConnection concorrente.
            return
        }
        checkOwned(BillingClient.ProductType.INAPP)
        checkOwned(BillingClient.ProductType.SUBS)
    }

    private fun checkOwned(type: String) {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(type).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                refresh("Sem acesso à Google Play. Direitos já verificados mantidos por seu período válido.",
                    loading = false)
                return@queryPurchasesAsync
            }
            val product = if (type == BillingClient.ProductType.INAPP)
                PlanPolicy.OFFLINE_PRODUCT_ID else PlanPolicy.PRODUCT_ID
            val relevant = purchases.filter { product in it.products }
            val confirmed = relevant.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (confirmed.isEmpty()) {
                if (type == BillingClient.ProductType.INAPP) entitlement.revokeOffline()
                else {
                    entitlement.revokeOnline()
                    confirmedOnlinePurchaseToken = null
                }
                refresh(if (relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING })
                    "Compra pendente de aprovação pela Google Play."
                else "Consulta de compras concluída.", loading = false,
                    pending = relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING })
            } else scope.launch { verifyPurchases(confirmed) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) restorePurchases()
                else scope.launch { verifyPurchases(purchases) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                refresh("Compra não realizada. Seu plano atual continua ativo.", loading = false)
            else -> refresh("Compra não concluída: ${result.debugMessage}", loading = false)
        }
    }

    private suspend fun verifyPurchases(purchases: List<Purchase>) {
        var anyValidated = false
        var pending = false
        var error: String? = null
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                pending = true
                continue
            }
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            val kind = when {
                PlanPolicy.PRODUCT_ID in purchase.products -> "subscription"
                PlanPolicy.OFFLINE_PRODUCT_ID in purchase.products -> "one_time"
                else -> continue
            }
            if (!verifyUrl.startsWith("https://")) {
                error = "O verificador de pagamentos ainda não foi configurado."
                continue
            }
            try {
                val response = withContext(Dispatchers.IO) {
                    verifyWithBackend(purchase.purchaseToken, kind)
                }
                if (response.optBoolean("active", false)) {
                    if (kind == "one_time") {
                        entitlement.grantOfflineVerified()
                        anyValidated = true
                    } else {
                        val expiry = response.optLong("expiresAtMillis", 0)
                        if (expiry > System.currentTimeMillis()) {
                            entitlement.grantOnlineVerified(expiry)
                            confirmedOnlinePurchaseToken = purchase.purchaseToken
                            anyValidated = true
                        }
                    }
                } else error = "Compra não ativa no Google Play."
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Falha de verificação."
            }
        }
        refresh(
            if (anyValidated) "Compra validada pela Google Play. Obrigado!"
            else if (pending) "Pagamento pendente. Aguarde a confirmação."
            else error ?: "Não foi encontrada compra válida.",
            loading = false, pending = pending
        )
    }

    private fun verifyWithBackend(token: String, kind: String): JSONObject {
        val connection = (URL(verifyUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val request = JSONObject()
                .put("purchaseToken", token)
                .put("type", kind)
                .toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) error("Verificação indisponível (HTTP $status)")
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }

    fun close() {
        client.endConnection()
        scope.cancel()
    }
}
