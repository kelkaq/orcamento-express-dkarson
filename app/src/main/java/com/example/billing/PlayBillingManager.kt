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
    val isPro: Boolean = false,
    val isLoading: Boolean = true,
    val canSubscribe: Boolean = false,
    val price: String? = null,
    val message: String = "Consultando a Google Play...",
    val purchasePending: Boolean = false
)

/**
 * Cobrança real pela Google Play. O APK de debug tem package diferente e não
 * consegue realizar compras de produção: use o AAB release distribuído pelo Play.
 */
class PlayBillingManager(
    context: Context,
    private val entitlement: EntitlementStore
) : PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val verifyUrl = BuildConfig.BILLING_VERIFY_URL
    private val _state = MutableStateFlow(BillingDisplayState(isPro = entitlement.isProNow()))
    val state: StateFlow<BillingDisplayState> = _state

    private var details: ProductDetails? = null
    private var offerToken: String? = null
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    init {
        connect()
    }

    private fun update(message: String? = null, loading: Boolean? = null, pending: Boolean? = null) {
        val current = _state.value
        _state.value = current.copy(
            isPro = entitlement.isProNow(),
            isLoading = loading ?: current.isLoading,
            canSubscribe = details != null && offerToken != null &&
                verifyUrl.startsWith("https://"),
            message = message ?: current.message,
            purchasePending = pending ?: current.purchasePending
        )
    }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restorePurchases()
                } else {
                    update("Google Play indisponível: ${result.debugMessage}", loading = false)
                }
            }
            override fun onBillingServiceDisconnected() {
                update("Sem conexão com a Google Play. Tente atualizar novamente.", loading = false)
            }
        })
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PlanPolicy.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { result, response ->
            val found = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                response.productDetailsList.firstOrNull { it.productId == PlanPolicy.PRODUCT_ID }
            else null
            val offer = found?.subscriptionOfferDetails
                ?.firstOrNull { it.basePlanId == PlanPolicy.BASE_PLAN_ID }
            details = if (offer != null) found else null
            offerToken = offer?.offerToken
            val visiblePrice = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
            _state.value = _state.value.copy(
                isPro = entitlement.isProNow(),
                price = visiblePrice,
                canSubscribe = offerToken != null && verifyUrl.startsWith("https://"),
                isLoading = false,
                message = when {
                    !verifyUrl.startsWith("https://") ->
                        "A verificação de pagamentos ainda precisa ser ativada pelo desenvolvedor."
                    details == null -> "O plano Pro ainda não está disponível para esta conta da Google Play."
                    else -> "Preço e período apresentados pela Google Play."
                }
            )
        }
    }

    fun buy(activity: Activity) {
        val d = details
        val offer = offerToken
        if (d == null || offer == null || !verifyUrl.startsWith("https://")) {
            update("O plano não está habilitado. Confira as configurações da Google Play.")
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d).setOfferToken(offer).build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            update("Não foi possível abrir a compra: ${result.debugMessage}")
        }
    }

    fun restorePurchases() {
        entitlement.refreshLocalState()
        update("Conferindo sua assinatura com a Google Play...", loading = true)
        if (!client.isReady) {
            // A conexão iniciada no construtor fará esta consulta ao finalizar.
            // Não iniciar outra conexão simultaneamente durante onResume.
            update("Conectando ao Google Play...", loading = true)
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                update("Não foi possível consultar as compras. Verifique a internet.", loading = false)
                return@queryPurchasesAsync
            }
            val relevant = purchases.filter { it.products.contains(PlanPolicy.PRODUCT_ID) }
            val purchased = relevant.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (purchased.isEmpty()) {
                // Uma resposta bem-sucedida sem compra ativa revoga o cache local.
                entitlement.revoke()
                update(
                    if (relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING })
                        "Pagamento pendente. O Pro será liberado após confirmação."
                    else "Plano Gratuito ativo.",
                    loading = false,
                    pending = relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING }
                )
            } else {
                scope.launch { verifyPurchases(purchased) }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) restorePurchases()
                else scope.launch { verifyPurchases(purchases) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                update("Compra cancelada. O plano Gratuito continua disponível.", loading = false)
            else -> update("Compra não concluída: ${result.debugMessage}", loading = false)
        }
    }

    private suspend fun verifyPurchases(purchases: List<Purchase>) {
        val completed = purchases.filter {
            it.products.contains(PlanPolicy.PRODUCT_ID) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        if (completed.isEmpty()) {
            update("Pagamento pendente. Aguarde a confirmação da Google Play.",
                loading = false, pending = pending)
            return
        }
        if (!verifyUrl.startsWith("https://")) {
            update("A verificação segura ainda não foi configurada.", loading = false)
            return
        }
        update("Validando assinatura com a Google Play...", loading = true)
        var verifiedUntil = 0L
        var error: String? = null
        for (purchase in completed) {
            try {
                val expiry = withContext(Dispatchers.IO) { verifyWithBackend(purchase.purchaseToken) }
                if (expiry > System.currentTimeMillis()) {
                    verifiedUntil = maxOf(verifiedUntil, expiry)
                    // O servidor reconhece (acknowledge) a compra após validá-la
                    // pela Google Play Developer API. Não confirmar antecipadamente no APK.
                }
            } catch (exception: Exception) {
                error = exception.localizedMessage
            }
        }
        if (verifiedUntil > System.currentTimeMillis()) {
            entitlement.grantVerified(verifiedUntil)
            update("Orçamento Express Pro ativo. Obrigado!", loading = false, pending = false)
        } else {
            // Não concede Pro sem validação; mantém cache verificado anterior apenas
            // enquanto ainda estiver dentro da janela offline documentada.
            entitlement.refreshLocalState()
            update(error ?: "Assinatura não confirmada pela Google Play.", loading = false,
                pending = pending)
        }
    }

    private fun verifyWithBackend(purchaseToken: String): Long {
        val connection = (URL(verifyUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val body = JSONObject().put("purchaseToken", purchaseToken).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Servidor de validação indisponível (HTTP $status).")
            }
            val json = JSONObject(response)
            if (!json.optBoolean("active", false)) {
                throw IllegalStateException("Assinatura não ativa no Google Play.")
            }
            json.getLong("expiresAtMillis")
        } finally {
            connection.disconnect()
        }
    }

    fun close() {
        client.endConnection()
        scope.cancel()
    }
}
