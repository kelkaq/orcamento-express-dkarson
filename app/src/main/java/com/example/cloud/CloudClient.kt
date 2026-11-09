package com.example.cloud

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import com.example.data.entity.QuoteWithItems
import com.example.data.repository.QuoteRepository
import com.example.util.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PrivateCloudAccess(val label: String, val recoveryKey: String? = null)

data class CloudSnapshot(
    val revision: Long,
    val exportDate: Long,
    val quotes: Int,
    val clients: Int,
    val backup: String
)

/**
 * Serviço opt-in: acesso pelo e-mail + código, TLS e token criptografado pelo
 * Android Keystore. Nunca sobrescreve nuvem ou banco local silenciosamente.
 */
class CloudClient(private val context: Context) {
    val baseUrl = BuildConfig.CLOUD_API_URL.trim().trimEnd('/')
    val configured = baseUrl.startsWith("https://")
    val googleLoginAvailable = configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()
    private val prefs = context.applicationContext
        .getSharedPreferences("dkarson_cloud_v1", Context.MODE_PRIVATE)
    private val alias = "dkarson_cloud_token"

    fun isLoggedIn(): Boolean = readToken() != null

    fun logout() {
        prefs.edit().remove("token").remove("email").remove("revision").apply()
    }

    fun email(): String = prefs.getString("email", "").orEmpty()

    /**
     * Conta independente de e-mail, com chave de recuperação criptograficamente aleatória.
     * O segredo aparece UMA VEZ; nunca é salvo em texto claro nas preferências.
     */
    suspend fun createPrivateAccount(): PrivateCloudAccess = withContext(Dispatchers.IO) {
        check(prefs.getString("workspace_email", null).isNullOrBlank()) {
            "Este aparelho já pertence a outra conta. Salve um backup antes de mudar."
        }
        val result = post("/v1/cloud/create-private-account", JSONObject(), authenticated = false)
        val identity = "account:" + result.getString("accountId")
        val label = result.getString("accountLabel")
        storeToken(result.getString("token"))
        rememberWorkspace(identity)
        prefs.edit().putString("email", label).remove("revision").apply()
        PrivateCloudAccess(label, result.getString("recoveryKey"))
    }

    suspend fun recoverPrivateAccount(recoveryKey: String): PrivateCloudAccess =
        withContext(Dispatchers.IO) {
            require(recoveryKey.trim().matches(Regex("[A-Za-z0-9_-]{43}"))) {
                "Informe sua chave de recuperação completa (43 caracteres)."
            }
            val result = post("/v1/cloud/recover-private-account",
                JSONObject().put("recoveryKey", recoveryKey.trim()), authenticated = false)
            val identity = "account:" + result.getString("accountId")
            requireCurrentWorkspace(identity)
            storeToken(result.getString("token"))
            rememberWorkspace(identity)
            val label = result.getString("accountLabel")
            prefs.edit().putString("email", label).remove("revision").apply()
            PrivateCloudAccess(label)
        }

    suspend fun rotatePrivateKey(): PrivateCloudAccess = withContext(Dispatchers.IO) {
        val result = post("/v1/cloud/rotate-recovery-key", JSONObject())
        storeToken(result.getString("token"))
        PrivateCloudAccess(email(), result.getString("recoveryKey"))
    }

    /** Banco local é um único espaço por instalação: não misturar dados de contas. */
    private fun requireCurrentWorkspace(email: String) {
        val previous = prefs.getString("workspace_email", prefs.getString("email", null))
        if (!previous.isNullOrBlank() && !previous.equals(email.trim(), ignoreCase = true)) {
            error("Este aparelho já tem um espaço de trabalho de outra conta. " +
                "Exporte seus dados e utilize outra instalação limpa antes de trocar de usuário.")
        }
    }

    private fun rememberWorkspace(email: String) {
        prefs.edit().putString("workspace_email", email.trim().lowercase()).apply()
    }

    /** ID token verificado pelo servidor Google; sem autenticação local simulada. */
    suspend fun googleLogin(idToken: String): String = withContext(Dispatchers.IO) {
        check(googleLoginAvailable) { "Configure o login Google no servidor." }
        val result = post("/v1/cloud/google-sign-in",
            JSONObject().put("idToken", idToken), authenticated = false)
        val verifiedEmail = result.getString("email")
        requireCurrentWorkspace(verifiedEmail)
        storeToken(result.getString("token"))
        rememberWorkspace(verifiedEmail)
        prefs.edit().putString("email", verifiedEmail).remove("revision").apply()
        "Conta Google conectada com segurança."
    }

    suspend fun linkGoogleAccount(idToken: String): String = withContext(Dispatchers.IO) {
        check(googleLoginAvailable) { "Login Google indisponível." }
        post("/v1/cloud/link-google", JSONObject().put("idToken", idToken))
        "Conta Google vinculada ao seu e-mail."
    }

    suspend fun signOut(): String = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/v1/cloud/session", null, authenticated = true)
        } finally { logout() }
        "Você saiu da sua conta neste aparelho."
    }

    suspend fun deleteOnlineAccount(): String = withContext(Dispatchers.IO) {
        request("DELETE", "/v1/cloud/account",
            JSONObject().put("confirm", "EXCLUIR MINHA CONTA"), authenticated = true)
        logout()
        "Conta e backups online excluídos. Os dados deste celular não foram apagados."
    }

    suspend fun requestCode(email: String): String = withContext(Dispatchers.IO) {
        require(email.contains('@') && email.length <= 254) { "E-mail inválido." }
        requireCurrentWorkspace(email)
        post("/v1/cloud/request-code", JSONObject().put("email", email.trim().lowercase()),
            authenticated = false)
        "Enviamos um código de acesso para o e-mail informado."
    }

    suspend fun confirmCode(email: String, code: String): String = withContext(Dispatchers.IO) {
        require(code.matches(Regex("[0-9]{6}"))) { "Informe o código com seis números." }
        requireCurrentWorkspace(email)
        val result = post(
            "/v1/cloud/verify-code",
            JSONObject().put("email", email.trim().lowercase()).put("code", code),
            authenticated = false
        )
        val token = result.getString("token")
        storeToken(token)
        rememberWorkspace(email)
        prefs.edit().putString("email", email.trim().lowercase())
            .remove("revision").apply()
        "Acesso à nuvem conectado."
    }

    suspend fun linkSubscription(purchaseToken: String): String =
        withContext(Dispatchers.IO) {
            require(purchaseToken.isNotBlank()) { "Assinatura não localizada pela Google Play." }
            val result = post("/v1/cloud/link-subscription",
                JSONObject().put("purchaseToken", purchaseToken))
            if (!result.optBoolean("active")) error("Assinatura online não confirmada.")
            "Assinatura online vinculada à sua conta."
        }

    suspend fun uploadBackup(repository: QuoteRepository): Long = withContext(Dispatchers.IO) {
        val backup = BackupManager.exportBackup(context, repository, isPreventive = true)
        val raw = backup.readText(Charsets.UTF_8)
        require(raw.toByteArray(Charsets.UTF_8).size <= 6_000_000) {
            "Backup maior que 6 MB. Exporte o arquivo manualmente."
        }
        val expected = prefs.getLong("revision", 0L)
        val result = post(
            "/v1/cloud/backup",
            JSONObject().put("expectedRevision", expected).put("backup", raw)
        )
        val revision = result.getLong("revision")
        prefs.edit().putLong("revision", revision).apply()
        revision
    }

    suspend fun downloadPreview(): CloudSnapshot = withContext(Dispatchers.IO) {
        val json = request("GET", "/v1/cloud/backup", null, authenticated = true)
        val raw = json.getString("backup")
        val data = JSONObject(raw)
        CloudSnapshot(
            revision = json.getLong("revision"),
            exportDate = data.optLong("exportDate"),
            quotes = data.optJSONArray("quotes")?.length() ?: 0,
            clients = data.optJSONArray("clients")?.length() ?: 0,
            backup = raw
        )
    }

    /** Usar só após confirmação visual. O BackupManager cria backup preventivo. */
    suspend fun restoreSnapshot(repository: QuoteRepository, snapshot: CloudSnapshot): String =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile("dkarson-cloud-", ".json", context.cacheDir)
            try {
                file.writeText(snapshot.backup, Charsets.UTF_8)
                val outcome = BackupManager.restoreFromUri(
                    context, Uri.fromFile(file), repository, replaceExisting = true
                )
                if (!outcome.success) error(outcome.errorMessage ?: "Falha na restauração.")
                prefs.edit().putLong("revision", snapshot.revision).apply()
                "Histórico recuperado. ${outcome.quotesCount} orçamento(s) restaurados."
            } finally { file.delete() }
        }

    suspend fun createApprovalLink(data: QuoteWithItems): String = withContext(Dispatchers.IO) {
        val q = data.quote
        require(q.status == "PENDING") { "Somente orçamentos pendentes podem ser enviados para aprovação." }
        val json = JSONObject().apply {
            put("quoteNumber", q.quoteNumber)
            put("clientName", q.clientName)
            put("companyReference", "Orçamento Express")
            put("total", q.total)
            put("validUntil", q.validUntil)
            put("items", JSONArray().also { arr ->
                data.items.forEach { item ->
                    arr.put(JSONObject().apply {
                        put("description", item.description)
                        put("quantity", item.quantity)
                        put("unit", item.unit)
                        put("unitPrice", item.unitPrice)
                        put("totalPrice", item.totalPrice)
                    })
                }
            })
        }
        post("/v1/cloud/approval-links", json).getString("url")
    }

    /** Recebe respostas, sem tentar alterar propostas que já mudaram de status. */
    suspend fun applyApprovedDecisions(repository: QuoteRepository): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            val response = request("GET", "/v1/cloud/decisions", null, true)
            val decisions = response.optJSONArray("decisions") ?: JSONArray()
            val local = repository.allQuotes.first().associateBy { it.quote.quoteNumber }
            var updated = 0
            var skipped = 0
            for (i in 0 until decisions.length()) {
                val decision = decisions.getJSONObject(i)
                val value = decision.optString("status", "")
                val quote = local[decision.optString("quoteNumber")]?.quote
                if (quote != null && quote.status == "PENDING" &&
                    value in setOf("APPROVED", "REJECTED")) {
                    runCatching { repository.applyOnlineDecision(quote.id, value) }
                        .onSuccess { updated++ }
                        .onFailure { skipped++ }
                } else if (value in setOf("APPROVED", "REJECTED")) skipped++
            }
            updated to skipped
        }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }

    private fun storeToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token", Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
    }

    private fun readToken(): String? {
        val stored = prefs.getString("token", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size > 12) { "Token inválido" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun post(route: String, payload: JSONObject, authenticated: Boolean = true): JSONObject =
        request("POST", route, payload, authenticated)

    private fun request(method: String, route: String, body: JSONObject?, authenticated: Boolean): JSONObject {
        check(configured) { "Configure o servidor online HTTPS antes de utilizar a nuvem." }
        val connection = (URL(baseUrl + route).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                setRequestProperty("Authorization", "Bearer " +
                    (readToken() ?: error("Entre com seu e-mail e código antes de continuar.")))
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            body?.let { connection.outputStream.use { out ->
                out.write(it.toString().toByteArray(Charsets.UTF_8))
            } }
            val code = connection.responseCode
            if (code !in 200..299) {
                val serverMessage = runCatching {
                    JSONObject(connection.errorStream?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() } ?: "{}").optString("message")
                }.getOrDefault("")
                val reason = when(code) {
                    401 -> "Sua sessão expirou. Entre novamente."
                    402 -> "Plano Online não confirmado no servidor."
                    404 -> "Nenhum backup disponível nesta conta."
                    409 -> serverMessage.ifBlank {
                        "Conflito: a nuvem tem uma versão mais recente. Confira antes de substituir."
                    }
                    429 -> "Muitas tentativas. Aguarde e tente novamente."
                    else -> "Serviço online indisponível (HTTP $code)."
                }
                error(reason)
            }
            JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        } finally { connection.disconnect() }
    }
}
