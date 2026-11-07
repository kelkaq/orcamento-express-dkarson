package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.entity.ClientEntity
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupValidationResult(
    val isValid: Boolean,
    val exportDate: Long = 0,
    val companyName: String = "",
    val clientsCount: Int = 0,
    val productsCount: Int = 0,
    val quotesCount: Int = 0,
    val itemsCount: Int = 0,
    val errorMessage: String? = null
)

data class RestoreResult(
    val success: Boolean,
    val clientsCount: Int = 0,
    val productsCount: Int = 0,
    val quotesCount: Int = 0,
    val preventiveBackupPath: String? = null,
    val errorMessage: String? = null
)

object BackupManager {

    suspend fun exportBackup(
        context: Context,
        repository: QuoteRepository,
        isPreventive: Boolean = false
    ): File {
        val snapshot = repository.getDatabaseRawData()
        val root = JSONObject()
        root.put("app", "Orçamento Express")
        root.put("version", 1)
        root.put("exportDate", System.currentTimeMillis())
        root.put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        root.put("isPreventive", isPreventive)

        // Metadata Counts
        val countsJson = JSONObject().apply {
            put("clients", snapshot.clients.size)
            put("products", snapshot.products.size)
            put("quotes", snapshot.quotes.size)
            put("items", snapshot.items.size)
        }
        root.put("counts", countsJson)

        // Company Profile + Logo Base64
        val prof = snapshot.profile
        var logoBase64: String? = null
        if (!prof.logoPath.isNullOrBlank()) {
            try {
                val logoFile = File(prof.logoPath)
                if (logoFile.exists() && logoFile.length() > 0) {
                    val bytes = logoFile.readBytes()
                    logoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (_: Exception) {
                logoBase64 = null
            }
        }

        val profJson = JSONObject().apply {
            put("tradeName", prof.tradeName)
            put("legalName", prof.legalName)
            put("document", prof.document)
            put("phone", prof.phone)
            put("email", prof.email)
            put("address", prof.address)
            put("pixKey", prof.pixKey)
            put("pixKeyType", prof.pixKeyType)
            put("defaultNotes", prof.defaultNotes)
            put("defaultPaymentTerms", prof.defaultPaymentTerms)
            put("defaultValidityDays", prof.defaultValidityDays)
            put("logoBase64", logoBase64 ?: JSONObject.NULL)
        }
        root.put("profile", profJson)

        // Clients
        val clientsArr = JSONArray()
        snapshot.clients.forEach { c ->
            clientsArr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("document", c.document)
                put("phone", c.phone)
                put("email", c.email)
                put("address", c.address)
                put("notes", c.notes)
                put("createdAt", c.createdAt)
            })
        }
        root.put("clients", clientsArr)

        // Products & Services
        val prodArr = JSONArray()
        snapshot.products.forEach { p ->
            prodArr.put(JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("type", p.type)
                put("unit", p.unit)
                put("unitPrice", p.unitPrice)
                put("category", p.category)
                put("description", p.description)
                put("createdAt", p.createdAt)
            })
        }
        root.put("products", prodArr)

        // Quotes
        val quotesArr = JSONArray()
        snapshot.quotes.forEach { q ->
            quotesArr.put(JSONObject().apply {
                put("id", q.id)
                put("quoteNumber", q.quoteNumber)
                put("clientId", q.clientId ?: JSONObject.NULL)
                put("clientName", q.clientName)
                put("clientDocument", q.clientDocument)
                put("clientPhone", q.clientPhone)
                put("clientEmail", q.clientEmail)
                put("clientAddress", q.clientAddress)
                put("issueDate", q.issueDate)
                put("validUntil", q.validUntil)
                put("status", q.status)
                put("subtotal", q.subtotal)
                put("discountType", q.discountType)
                put("discountValue", q.discountValue)
                put("discountCalculated", q.discountCalculated)
                put("shippingFee", q.shippingFee)
                put("total", q.total)
                put("paymentTerms", q.paymentTerms)
                put("paymentMethod", q.paymentMethod)
                put("notes", q.notes)
                put("createdAt", q.createdAt)
                put("updatedAt", q.updatedAt)
            })
        }
        root.put("quotes", quotesArr)

        // Items
        val itemsArr = JSONArray()
        snapshot.items.forEach { it ->
            itemsArr.put(JSONObject().apply {
                put("id", it.id)
                put("quoteId", it.quoteId)
                put("productId", it.productId ?: JSONObject.NULL)
                put("description", it.description)
                put("type", it.type)
                put("unit", it.unit)
                put("quantity", it.quantity)
                put("unitPrice", it.unitPrice)
                put("totalPrice", it.totalPrice)
            })
        }
        root.put("items", itemsArr)

        // Save file
        val targetDir = if (isPreventive) {
            File(context.filesDir, "preventive_backups").apply { mkdirs() }
        } else {
            File(context.cacheDir, "docs").apply { mkdirs() }
        }

        val prefix = if (isPreventive) "backup_preventivo_" else "backup_orcamento_express_"
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(targetDir, "${prefix}${timeStamp}.json")
        try {
            FileOutputStream(backupFile).use { output ->
                output.write(root.toString(2).toByteArray(Charsets.UTF_8))
                output.flush()
                // Confirma a gravação física, especialmente importante no backup preventivo.
                output.fd.sync()
            }
        } catch (error: Exception) {
            backupFile.delete() // Nunca apresentar arquivo parcial como backup válido.
            throw error
        }

        return backupFile
    }

    fun validateBackup(context: Context, uri: Uri): BackupValidationResult {
        return try {
            val jsonContent = readContentFromUri(context, uri)
            if (jsonContent.isBlank()) {
                return BackupValidationResult(isValid = false, errorMessage = "Arquivo vazio ou ilegível.")
            }

            val root = JSONObject(jsonContent)
            if (!root.has("app") || root.optString("app") != "Orçamento Express") {
                return BackupValidationResult(isValid = false, errorMessage = "Arquivo incompatível com o Orçamento Express.")
            }

            if (root.optInt("version", -1) != 1) {
                return BackupValidationResult(isValid = false, errorMessage = "Versão de backup incompatível.")
            }
            // Falhar de forma segura: arrays ausentes não devem ser tratados como listas vazias.
            if (listOf("clients", "products", "quotes", "items").any { root.optJSONArray(it) == null }) {
                return BackupValidationResult(
                    isValid = false,
                    errorMessage = "Backup incompleto: faltam seções obrigatórias de dados."
                )
            }
            val exportDate = root.optLong("exportDate", 0)
            val profileJson = root.optJSONObject("profile")
            val compName = profileJson?.optString("tradeName", "") ?: ""

            val clientsArr = root.getJSONArray("clients")
            val prodArr = root.getJSONArray("products")
            val quotesArr = root.getJSONArray("quotes")
            val itemsArr = root.getJSONArray("items")

            fun hasUniquePositiveIds(array: JSONArray): Boolean {
                val ids = mutableSetOf<Long>()
                for (i in 0 until array.length()) {
                    val id = array.getJSONObject(i).optLong("id", 0)
                    if (id <= 0L || !ids.add(id)) return false
                }
                return true
            }
            if (!hasUniquePositiveIds(clientsArr) || !hasUniquePositiveIds(prodArr) ||
                !hasUniquePositiveIds(quotesArr) || !hasUniquePositiveIds(itemsArr)) {
                return BackupValidationResult(
                    isValid = false,
                    errorMessage = "Backup inválido: registros com identificadores duplicados ou ausentes."
                )
            }

            // Referential integrity check: every item's quoteId should exist in quotes if quotes present
            val quoteIds = mutableSetOf<Long>()
            for (i in 0 until quotesArr.length()) {
                quoteIds.add(quotesArr.getJSONObject(i).optLong("id", -1L))
            }

            for (i in 0 until itemsArr.length()) {
                    val qId = itemsArr.getJSONObject(i).optLong("quoteId", -1L)
                    if (!quoteIds.contains(qId)) {
                        // Notice: integrity issue found
                        return BackupValidationResult(
                            isValid = false,
                            errorMessage = "Falha de integridade referencial: item #$i faz referência a orçamento inexistente ($qId)."
                        )
                    }
                }

            BackupValidationResult(
                isValid = true,
                exportDate = exportDate,
                companyName = compName,
                clientsCount = clientsArr?.length() ?: 0,
                productsCount = prodArr?.length() ?: 0,
                quotesCount = quotesArr?.length() ?: 0,
                itemsCount = itemsArr?.length() ?: 0
            )
        } catch (e: Exception) {
            BackupValidationResult(isValid = false, errorMessage = "Formato JSON inválido: ${e.localizedMessage}")
        }
    }

    suspend fun restoreFromUri(
        context: Context,
        uri: Uri,
        repository: QuoteRepository,
        replaceExisting: Boolean
    ): RestoreResult {
        return try {
            // Step 1: Validate file first
            val validation = validateBackup(context, uri)
            if (!validation.isValid) {
                return RestoreResult(success = false, errorMessage = validation.errorMessage)
            }

            // Step 2: Create preventive safety backup if replacing (MANDATÓRIO)
            var preventiveBackupFile: File? = null
            if (replaceExisting) {
                try {
                    preventiveBackupFile = exportBackup(context, repository, isPreventive = true)
                } catch (e: Exception) {
                    return RestoreResult(
                        success = false,
                        errorMessage = "Restauração cancelada por segurança: falha ao gerar o backup preventivo obrigatório (${e.localizedMessage}). Seus dados não foram alterados."
                    )
                }
                val backupFile = preventiveBackupFile
                if (backupFile == null || !backupFile.exists() || backupFile.length() == 0L ||
                    runCatching {
                        val backup = JSONObject(backupFile.readText(Charsets.UTF_8))
                        backup.optString("app") == "Orçamento Express" &&
                            backup.optJSONArray("quotes") != null && backup.optJSONArray("items") != null
                    }.getOrDefault(false).not()
                ) {
                    return RestoreResult(
                        success = false,
                        errorMessage = "Restauração cancelada por segurança: o arquivo de backup preventivo não pôde ser gravado com integridade. Seus dados não foram alterados."
                    )
                }
            }

            val jsonContent = readContentFromUri(context, uri)
            val root = JSONObject(jsonContent)

            // Step 3: Parse Profile and Logo Base64
            var profile: CompanyProfileEntity? = null
            if (root.has("profile")) {
                val p = root.getJSONObject("profile")
                var restoredLogoPath: String? = null

                if (p.has("logoBase64") && !p.isNull("logoBase64")) {
                    val b64 = p.getString("logoBase64")
                    if (b64.isNotBlank()) {
                        try {
                            val decoded = Base64.decode(b64, Base64.NO_WRAP)
                            val logoDir = File(context.filesDir, "branding").apply { mkdirs() }
                            val logoFile = File(logoDir, "restored_logo_${System.currentTimeMillis()}.png")
                            FileOutputStream(logoFile).use { it.write(decoded) }
                            restoredLogoPath = logoFile.absolutePath
                        } catch (_: Exception) {
                            restoredLogoPath = null
                        }
                    }
                }

                profile = CompanyProfileEntity(
                    id = 1,
                    tradeName = p.optString("tradeName", ""),
                    legalName = p.optString("legalName", ""),
                    document = p.optString("document", ""),
                    phone = p.optString("phone", ""),
                    email = p.optString("email", ""),
                    address = p.optString("address", ""),
                    pixKey = p.optString("pixKey", ""),
                    pixKeyType = p.optString("pixKeyType", "Chave"),
                    logoPath = restoredLogoPath ?: repository.getCompanyProfile().logoPath,
                    defaultNotes = p.optString("defaultNotes", ""),
                    defaultPaymentTerms = p.optString("defaultPaymentTerms", ""),
                    defaultValidityDays = p.optInt("defaultValidityDays", 15)
                )
            }

            // Step 4: Parse Clients
            val clients = mutableListOf<ClientEntity>()
            val clientsArr = root.optJSONArray("clients")
            if (clientsArr != null) {
                for (i in 0 until clientsArr.length()) {
                    val c = clientsArr.getJSONObject(i)
                    clients.add(
                        ClientEntity(
                            id = c.optLong("id", 0),
                            name = c.optString("name", "").trim(),
                            document = c.optString("document", "").trim(),
                            phone = c.optString("phone", "").trim(),
                            email = c.optString("email", "").trim(),
                            address = c.optString("address", "").trim(),
                            notes = c.optString("notes", "").trim(),
                            createdAt = c.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Step 5: Parse Products
            val products = mutableListOf<ProductServiceEntity>()
            val prodArr = root.optJSONArray("products")
            if (prodArr != null) {
                for (i in 0 until prodArr.length()) {
                    val p = prodArr.getJSONObject(i)
                    products.add(
                        ProductServiceEntity(
                            id = p.optLong("id", 0),
                            title = p.optString("title", "").trim(),
                            type = p.optString("type", "SERVICE"),
                            unit = p.optString("unit", "un").trim(),
                            unitPrice = p.optDouble("unitPrice", 0.0),
                            category = p.optString("category", "Geral").trim(),
                            description = p.optString("description", "").trim(),
                            createdAt = p.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Step 6: Parse Quotes
            val quotes = mutableListOf<QuoteEntity>()
            val quotesArr = root.optJSONArray("quotes")
            if (quotesArr != null) {
                for (i in 0 until quotesArr.length()) {
                    val q = quotesArr.getJSONObject(i)
                    quotes.add(
                        QuoteEntity(
                            id = q.optLong("id", 0),
                            quoteNumber = q.optString("quoteNumber", "").trim(),
                            clientId = if (q.isNull("clientId")) null else q.optLong("clientId"),
                            clientName = q.optString("clientName", "").trim(),
                            clientDocument = q.optString("clientDocument", "").trim(),
                            clientPhone = q.optString("clientPhone", "").trim(),
                            clientEmail = q.optString("clientEmail", "").trim(),
                            clientAddress = q.optString("clientAddress", "").trim(),
                            issueDate = q.optLong("issueDate", System.currentTimeMillis()),
                            validUntil = q.optLong("validUntil", System.currentTimeMillis()),
                            status = q.optString("status", "PENDING"),
                            subtotal = q.optDouble("subtotal", 0.0),
                            discountType = q.optString("discountType", "FIXED"),
                            discountValue = q.optDouble("discountValue", 0.0),
                            discountCalculated = q.optDouble("discountCalculated", 0.0),
                            shippingFee = q.optDouble("shippingFee", 0.0),
                            total = q.optDouble("total", 0.0),
                            paymentTerms = q.optString("paymentTerms", "").trim(),
                            paymentMethod = q.optString("paymentMethod", "PIX").trim(),
                            notes = q.optString("notes", "").trim(),
                            createdAt = q.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = q.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Step 7: Parse Quote Items
            val items = mutableListOf<QuoteItemEntity>()
            val itemsArr = root.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val it = itemsArr.getJSONObject(i)
                    items.add(
                        QuoteItemEntity(
                            id = it.optLong("id", 0),
                            quoteId = it.optLong("quoteId", 0),
                            productId = if (it.isNull("productId")) null else it.optLong("productId"),
                            description = it.optString("description", "").trim(),
                            type = it.optString("type", "SERVICE"),
                            unit = it.optString("unit", "un").trim(),
                            quantity = it.optDouble("quantity", 1.0),
                            unitPrice = it.optDouble("unitPrice", 0.0),
                            totalPrice = it.optDouble("totalPrice", 0.0)
                        )
                    )
                }
            }

            // Step 8: Execute transactional atomic restore in Room
            repository.restoreDatabaseRawData(
                profile = profile,
                quotes = quotes,
                items = items,
                clients = clients,
                products = products,
                replaceExisting = replaceExisting
            )

            RestoreResult(
                success = true,
                clientsCount = clients.size,
                productsCount = products.size,
                quotesCount = quotes.size,
                preventiveBackupPath = preventiveBackupFile?.absolutePath
            )
        } catch (e: Exception) {
            RestoreResult(success = false, errorMessage = "Falha atômica durante restauração: ${e.localizedMessage}")
        }
    }

    private fun readContentFromUri(context: Context, uri: Uri): String {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line)
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }

    fun getBackupUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
