package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteSequenceEntity
import com.example.data.entity.QuoteWithItems
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

data class DashboardMetrics(
    val totalQuotes: Int = 0,
    val pendingCount: Int = 0,
    val approvedCount: Int = 0,
    val totalApprovedRevenue: Double = 0.0
)

class QuoteRepository(private val db: AppDatabase) {
    val database: AppDatabase get() = db

    private val clientDao = db.clientDao()
    private val productServiceDao = db.productServiceDao()
    private val quoteDao = db.quoteDao()
    private val companyProfileDao = db.companyProfileDao()
    private val sequenceDao = db.quoteSequenceDao()

    val dashboardMetrics: Flow<DashboardMetrics> = quoteDao.observeDashboardMetrics()

    // Quotes
    val allQuotes: Flow<List<QuoteWithItems>> = quoteDao.getAllQuotesWithItems()

    fun getFilteredQuotes(status: String, query: String): Flow<List<QuoteWithItems>> =
        quoteDao.getFilteredQuotesWithItems(status, query.trim())

    fun getQuotesByStatus(status: String): Flow<List<QuoteWithItems>> =
        if (status.isEmpty() || status == "ALL") quoteDao.getAllQuotesWithItems()
        else quoteDao.getQuotesByStatus(status)

    fun searchQuotes(query: String): Flow<List<QuoteWithItems>> = quoteDao.searchQuotes(query)

    fun getQuoteWithItemsFlow(id: Long): Flow<QuoteWithItems?> = quoteDao.getQuoteWithItemsFlow(id)

    suspend fun getQuoteWithItems(id: Long): QuoteWithItems? = quoteDao.getQuoteWithItems(id)

    suspend fun saveQuoteWithItems(quote: QuoteEntity, items: List<QuoteItemEntity>): Long =
        quoteDao.saveQuoteWithItems(quote, items)

    suspend fun updateQuoteStatus(quoteId: Long, newStatus: String) =
        quoteDao.updateStatus(quoteId, newStatus)

    suspend fun deleteQuote(quote: QuoteEntity) = quoteDao.deleteQuote(quote)

    suspend fun duplicateQuote(sourceQuoteId: Long): Long {
        val original = quoteDao.getQuoteWithItems(sourceQuoteId) ?: return -1L
        val nextNumber = generateNextQuoteNumber()
        val duplicatedQuote = original.quote.copy(
            id = 0,
            quoteNumber = nextNumber,
            status = "PENDING",
            issueDate = System.currentTimeMillis(),
            validUntil = System.currentTimeMillis() + (15L * 24 * 60 * 60 * 1000),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val duplicatedItems = original.items.map { it.copy(id = 0, quoteId = 0) }
        return quoteDao.saveQuoteWithItems(duplicatedQuote, duplicatedItems)
    }

    suspend fun generateNextQuoteNumber(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val yearStr = year.toString()

        // Inspect existing quote numbers in the database to prevent collision after deletions or imports
        val existingNumbers = quoteDao.getAllQuoteNumbersForYear(yearStr)
        var maxExistingSeq = 0
        for (num in existingNumbers) {
            // Pattern: #ORC-YYYY-NNNN
            val parts = num.split("-")
            if (parts.size >= 3) {
                val seqPart = parts.last().takeWhile { it.isDigit() }
                val seqNum = seqPart.toIntOrNull() ?: 0
                if (seqNum > maxExistingSeq) {
                    maxExistingSeq = seqNum
                }
            }
        }

        val nextSeq = sequenceDao.getNextSequenceNumber(year, maxExistingSeq)
        return "#ORC-$year-${String.format("%04d", nextSeq)}"
    }

    suspend fun getDashboardMetrics(): DashboardMetrics {
        val total = quoteDao.countQuotes()
        val pending = quoteDao.countByStatus("PENDING")
        val approved = quoteDao.countByStatus("APPROVED") + quoteDao.countByStatus("COMPLETED")
        val rev = quoteDao.getTotalApprovedRevenue() ?: 0.0
        return DashboardMetrics(
            totalQuotes = total,
            pendingCount = pending,
            approvedCount = approved,
            totalApprovedRevenue = rev
        )
    }

    // Clients
    val allClients: Flow<List<ClientEntity>> = clientDao.getAllClients()

    fun searchClients(query: String): Flow<List<ClientEntity>> = clientDao.searchClients(query)

    suspend fun getClientById(id: Long): ClientEntity? = clientDao.getClientById(id)

    suspend fun saveClient(client: ClientEntity): Long =
        if (client.id > 0) {
            clientDao.updateClient(client)
            client.id
        } else {
            clientDao.insertClient(client)
        }

    suspend fun deleteClient(client: ClientEntity) = clientDao.deleteClient(client)

    // Catalog (Products & Services)
    val allProductsServices: Flow<List<ProductServiceEntity>> = productServiceDao.getAll()

    fun getProductsServicesByType(type: String): Flow<List<ProductServiceEntity>> =
        if (type == "ALL") productServiceDao.getAll() else productServiceDao.getByType(type)

    fun searchProductsServices(query: String): Flow<List<ProductServiceEntity>> =
        productServiceDao.search(query)

    suspend fun saveProductService(item: ProductServiceEntity): Long =
        if (item.id > 0) {
            productServiceDao.update(item)
            item.id
        } else {
            productServiceDao.insert(item)
        }

    suspend fun deleteProductService(item: ProductServiceEntity) = productServiceDao.delete(item)

    // Company Profile
    val companyProfileFlow: Flow<CompanyProfileEntity?> = companyProfileDao.getProfileFlow()

    suspend fun getCompanyProfile(): CompanyProfileEntity =
        companyProfileDao.getProfile() ?: CompanyProfileEntity(id = 1)

    suspend fun updateCompanyProfile(profile: CompanyProfileEntity) =
        companyProfileDao.upsertProfile(profile.copy(id = 1, updatedAt = System.currentTimeMillis()))

    suspend fun getAllClientsRaw(): List<ClientEntity> = clientDao.getAllClientsRaw()
    suspend fun getAllProductsRaw(): List<ProductServiceEntity> = productServiceDao.getAllRaw()

    /** Captura todos os dados em um mesmo estado consistente do banco. */
    suspend fun getDatabaseRawData(): DatabaseBackupSnapshot = db.withTransaction {
        DatabaseBackupSnapshot(
            profile = companyProfileDao.getProfile() ?: CompanyProfileEntity(),
            quotes = quoteDao.getAllQuotesRaw(),
            items = quoteDao.getAllQuoteItemsRaw(),
            clients = clientDao.getAllClientsRaw(),
            products = productServiceDao.getAllRaw()
        )
    }

    suspend fun restoreDatabaseRawData(
        profile: CompanyProfileEntity?,
        quotes: List<QuoteEntity>,
        items: List<QuoteItemEntity>,
        clients: List<ClientEntity>,
        products: List<ProductServiceEntity>,
        replaceExisting: Boolean
    ) {
        db.withTransaction {
            if (replaceExisting) {
                // Modo SUBSTITUIR: limpa tabelas e restaura os IDs exatamente como vieram
                quoteDao.clearAllQuoteItems()
                quoteDao.clearAllQuotes()
                clientDao.clearAllClients()
                productServiceDao.clearAll()

                profile?.let { companyProfileDao.upsertProfile(it.copy(id = 1)) }
                if (clients.isNotEmpty()) clientDao.insertAll(clients)
                if (products.isNotEmpty()) productServiceDao.insertAll(products)
                if (quotes.isNotEmpty()) quoteDao.insertQuotesRaw(quotes)
                if (items.isNotEmpty()) quoteDao.insertQuoteItemsRaw(items)
            } else {
                // Modo MESCLAR:
                // 1. Perfil da Empresa: preserva dados locais se já existirem; apenas preenche se vazio
                if (profile != null) {
                    val currentProfile = companyProfileDao.getProfile()
                    if (currentProfile == null || currentProfile.tradeName.isBlank()) {
                        companyProfileDao.upsertProfile(profile.copy(id = 1))
                    }
                }

                // 2. Clientes: remapear IDs importados.
                // Se já existir cliente com mesmo documento (ou mesmo nome caso documento esteja em branco), vincula ao cliente existente.
                // Caso contrário, insere como novo registro (id = 0) e registra no mapa de IDs.
                val clientIdMap = mutableMapOf<Long, Long>()
                val existingClients = clientDao.getAllClientsRaw()

                for (importedClient in clients) {
                    val oldId = importedClient.id
                    val existingMatch = existingClients.find { existing ->
                        (importedClient.document.isNotBlank() && existing.document.isNotBlank() && existing.document == importedClient.document) ||
                        (importedClient.name.isNotBlank() && existing.name.equals(importedClient.name, ignoreCase = true))
                    }

                    val targetClientId = if (existingMatch != null) {
                        existingMatch.id
                    } else {
                        clientDao.insertClient(importedClient.copy(id = 0))
                    }

                    if (oldId > 0) {
                        clientIdMap[oldId] = targetClientId
                    }
                }

                // 3. Produtos e Serviços: remapear IDs importados.
                // Se já existir produto/serviço com mesmo título e tipo, vincula ao item existente.
                // Caso contrário, insere como novo registro (id = 0).
                val productIdMap = mutableMapOf<Long, Long>()
                val existingProducts = productServiceDao.getAllRaw()

                for (importedProduct in products) {
                    val oldId = importedProduct.id
                    val existingMatch = existingProducts.find { existing ->
                        existing.title.equals(importedProduct.title, ignoreCase = true) &&
                        existing.type.equals(importedProduct.type, ignoreCase = true)
                    }

                    val targetProductId = if (existingMatch != null) {
                        existingMatch.id
                    } else {
                        productServiceDao.insert(importedProduct.copy(id = 0))
                    }

                    if (oldId > 0) {
                        productIdMap[oldId] = targetProductId
                    }
                }

                // 4. Orçamentos: remapear IDs e evitar colisão de número de orçamento
                // Mantém mapa: oldQuoteId -> newQuoteId
                val quoteIdMap = mutableMapOf<Long, Long>()
                val existingQuoteNumbers = quoteDao.getAllQuotesRaw().map { it.quoteNumber }.toMutableSet()

                for (importedQuote in quotes) {
                    val oldQuoteId = importedQuote.id

                    // Resolver novo clientId remapeado se aplicável
                    val remappedClientId = importedQuote.clientId?.let { oldCId ->
                        clientIdMap[oldCId]
                    }

                    // Verificar se o número do orçamento já existe na base local; se existir, gera um novo sequencial
                    var assignedNumber = importedQuote.quoteNumber
                    if (existingQuoteNumbers.contains(assignedNumber) || assignedNumber.isBlank()) {
                        assignedNumber = generateNextQuoteNumber()
                    }
                    existingQuoteNumbers.add(assignedNumber)

                    val quoteToInsert = importedQuote.copy(
                        id = 0,
                        quoteNumber = assignedNumber,
                        clientId = remappedClientId,
                        updatedAt = System.currentTimeMillis()
                    )
                    val newQuoteId = quoteDao.insertQuote(quoteToInsert)
                    if (oldQuoteId > 0) {
                        quoteIdMap[oldQuoteId] = newQuoteId
                    }
                }

                // 5. Itens do Orçamento: remapear quoteId e productId
                val remappedItems = mutableListOf<QuoteItemEntity>()
                for (importedItem in items) {
                    val newQuoteId = quoteIdMap[importedItem.quoteId]
                    if (newQuoteId != null) {
                        val remappedProductId = importedItem.productId?.let { oldPId ->
                            productIdMap[oldPId]
                        }
                        remappedItems.add(
                            importedItem.copy(
                                id = 0,
                                quoteId = newQuoteId,
                                productId = remappedProductId
                            )
                        )
                    }
                }
                if (remappedItems.isNotEmpty()) {
                    quoteDao.insertQuoteItems(remappedItems)
                }
            }

            // Sincronizar o contador de sequência com o maior número existente no ano
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val yearQuotes = quoteDao.getAllQuoteNumbersForYear(year.toString())
            var maxSeq = 0
            for (qNum in yearQuotes) {
                val parts = qNum.split("-")
                if (parts.size >= 3) {
                    val sNum = parts.last().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                    if (sNum > maxSeq) maxSeq = sNum
                }
            }
            if (maxSeq > 0) {
                sequenceDao.setSequence(QuoteSequenceEntity(year = year, lastSequence = maxSeq))
            }
        }
    }
}

data class DatabaseBackupSnapshot(
    val profile: CompanyProfileEntity,
    val quotes: List<QuoteEntity>,
    val items: List<QuoteItemEntity>,
    val clients: List<ClientEntity>,
    val products: List<ProductServiceEntity>
)
