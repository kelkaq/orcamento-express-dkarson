package com.example.data.repository

import androidx.room.withTransaction
import com.example.billing.FreePlanLimitException
import com.example.billing.PlanPolicy
import com.example.billing.ProRequiredException
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.ServiceOrderEntity
import com.example.data.entity.QuoteTemplateEntity
import com.example.data.entity.DraftQuoteEntity
import com.example.data.entity.QuoteCostEntity
import com.example.data.entity.AgendaEventEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteSequenceEntity
import com.example.data.entity.QuoteWithItems
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.combine

data class DashboardMetrics(
    val totalQuotes: Int = 0,
    val pendingCount: Int = 0,
    val approvedCount: Int = 0,
    val totalApprovedRevenue: Double = 0.0,
    val totalReceived: Double = 0.0,
    val totalReceivable: Double = 0.0,
    val pendingValue: Double = 0.0,
    val completedCount: Int = 0,
    val overdueCount: Int = 0,
    val estimatedCosts: Double = 0.0,
    val estimatedProfit: Double = 0.0,
    val pricedQuotesCount: Int = 0
)

class QuoteRepository(
    private val db: AppDatabase,
    private val hasVerifiedPro: () -> Boolean = { false }
) {
    private fun requirePro(feature: String) {
        if (!hasVerifiedPro()) throw ProRequiredException(feature)
    }

    suspend fun monthlyFreeQuotesUsed(now: Long = System.currentTimeMillis()): Int {
        val (start, end) = PlanPolicy.currentMonthBounds(now)
        return quoteDao.countCreatedBetween(start, end)
    }

    val database: AppDatabase get() = db

    private val clientDao = db.clientDao()
    private val productServiceDao = db.productServiceDao()
    private val quoteDao = db.quoteDao()
    private val paymentDao = db.paymentDao()
    private val orderDao = db.serviceOrderDao()
    private val templateDao = db.quoteTemplateDao()
    private val draftDao = db.draftQuoteDao()
    private val costDao = db.quoteCostDao()
    private val agendaDao = db.agendaEventDao()
    private val companyProfileDao = db.companyProfileDao()
    private val sequenceDao = db.quoteSequenceDao()

    val dashboardMetrics: Flow<DashboardMetrics> = dashboardMetricsByPeriod("ALL")

    /**
     * Data de emissão filtra propostas e datas reais de pagamento filtram recebimentos.
     * Aprovação histórica sem timestamp não é atribuída artificialmente ao período.
     */
    fun dashboardMetricsByPeriod(period: String): Flow<DashboardMetrics> =
        combine(
            quoteDao.getAllQuotesWithItems(), paymentDao.observeAll(), costDao.observeAll()
        ) { quotesWithItems, payments, costs ->
            val calendar = Calendar.getInstance()
            val now = System.currentTimeMillis()
            val start: Long
            val end: Long
            when (period) {
                "MONTH" -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    start = calendar.timeInMillis
                    calendar.add(Calendar.MONTH, 1)
                    end = calendar.timeInMillis
                }
                "LAST_MONTH" -> {
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    end = calendar.timeInMillis
                    calendar.add(Calendar.MONTH, -1)
                    start = calendar.timeInMillis
                }
                "YEAR" -> {
                    calendar.set(Calendar.MONTH, Calendar.JANUARY)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    start = calendar.timeInMillis
                    calendar.add(Calendar.YEAR, 1)
                    end = calendar.timeInMillis
                }
                else -> {
                    start = 0L
                    end = Long.MAX_VALUE
                }
            }
            val issued = quotesWithItems.map { it.quote }.filter { it.issueDate >= start && it.issueDate < end }
            val approved = issued.filter { it.status == "APPROVED" || it.status == "COMPLETED" }
            val pending = issued.filter { it.status == "PENDING" }
            // Todas as parcelas de um orçamento são consideradas no seu saldo, não apenas as do período.
            val paidByQuote = payments.groupBy { it.quoteId }
                .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
            fun cents(total: Double): Long = BigDecimal.valueOf(total)
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            val approvedCents = approved.sumOf { cents(it.total) }
            val remainingCents = approved.sumOf { q ->
                (cents(q.total) - (paidByQuote[q.id] ?: 0L)).coerceAtLeast(0L)
            }
            val receivedCents = payments.filter { it.paidAt >= start && it.paidAt < end }
                .sumOf { it.amountCents }
            val costsById = costs.associateBy { it.quoteId }
            val pricedQuotes = approved.filter { costsById[it.id] != null }
            val costCents = pricedQuotes.sumOf { costsById[it.id]!!.totalCents }
            val profitCents = pricedQuotes.sumOf { cents(it.total) -
                costsById[it.id]!!.totalCents }
            DashboardMetrics(
                totalQuotes = issued.size,
                pendingCount = pending.size,
                approvedCount = approved.size,
                totalApprovedRevenue = approvedCents / 100.0,
                totalReceived = receivedCents / 100.0,
                totalReceivable = remainingCents / 100.0,
                pendingValue = pending.sumOf { cents(it.total) } / 100.0,
                completedCount = issued.count { it.status == "COMPLETED" },
                overdueCount = pending.count { it.validUntil < now },
                estimatedCosts = costCents / 100.0,
                estimatedProfit = profitCents / 100.0,
                pricedQuotesCount = pricedQuotes.size
            )
        }

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

    suspend fun saveQuoteWithItems(
        quote: QuoteEntity,
        items: List<QuoteItemEntity>,
        costEstimate: QuoteCostEntity? = null
    ): Long = db.withTransaction {
        // Limite somente para NOVOS orçamentos. Visualizar/editar documentos existentes é sempre permitido.
        if (quote.id <= 0L && !hasVerifiedPro()) {
            val (start, end) = PlanPolicy.currentMonthBounds()
            if (quoteDao.countCreatedBetween(start, end) >= PlanPolicy.FREE_QUOTES_PER_MONTH) {
                throw FreePlanLimitException()
            }
        }
        require(quote.id <= 0 || paymentDao.countForQuote(quote.id) == 0) {
            "Este orçamento possui recebimentos. Não é permitido alterar os valores após o pagamento."
        }
        // Preserva a criação original ao editar: não altera histórico nem reinicia
        // indevidamente a cota mensal de quem está no plano Gratuito.
        val savedQuote = if (quote.id > 0L) {
            val original = quoteDao.getQuoteWithItems(quote.id)?.quote
                ?: error("Orçamento não encontrado.")
            quote.copy(createdAt = original.createdAt)
        } else quote.copy(createdAt = System.currentTimeMillis())
        val savedId = quoteDao.saveQuoteWithItems(savedQuote, items)
        if (costEstimate != null) {
            require(costEstimate.totalCents >= 0L) { "O custo não pode ser negativo." }
            costDao.upsert(costEstimate.copy(quoteId = savedId, updatedAt = System.currentTimeMillis()))
        }
        savedId
    }

    suspend fun updateQuoteStatus(quoteId: Long, newStatus: String) = db.withTransaction {
        require(paymentDao.countForQuote(quoteId) == 0 || newStatus in setOf("APPROVED", "COMPLETED")) {
            "Há recebimentos registrados. Não é possível voltar este orçamento a pendente ou cancelado."
        }
        quoteDao.updateStatus(quoteId, newStatus)
    }

    suspend fun deleteQuote(quote: QuoteEntity) = db.withTransaction {
        require(orderDao.getByQuote(quote.id) == null) { "Exclua a ordem de serviço antes de excluir o orçamento." }
        require(paymentDao.countForQuote(quote.id) == 0) {
            "Este orçamento possui recibos e recebimentos. Não pode ser excluído."
        }
        quoteDao.deleteQuote(quote)
    }

    fun observePayments(quoteId: Long): Flow<List<PaymentEntity>> = paymentDao.observeByQuote(quoteId)

    val allPayments: Flow<List<PaymentEntity>> = paymentDao.observeAll()

    val agendaEvents: Flow<List<AgendaEventEntity>> = agendaDao.observeAll()
    fun observeQuoteCosts(quoteId: Long): Flow<QuoteCostEntity?> = costDao.observeByQuote(quoteId)
    suspend fun getQuoteCosts(quoteId: Long): QuoteCostEntity? = costDao.getByQuote(quoteId)

    suspend fun saveQuoteCosts(
        quoteId: Long,
        materialsCents: Long,
        laborCents: Long,
        travelCents: Long,
        otherCents: Long
    ) = db.withTransaction {
        require(listOf(materialsCents, laborCents, travelCents, otherCents).all { it >= 0 }) {
            "Os custos não podem ser negativos."
        }
        require(quoteDao.getQuoteWithItems(quoteId) != null) { "Orçamento não encontrado." }
        costDao.upsert(QuoteCostEntity(quoteId, materialsCents, laborCents, travelCents, otherCents))
    }

    suspend fun saveAgendaEvent(event: AgendaEventEntity): Long = db.withTransaction {
        require(event.title.isNotBlank()) { "Informe o título do compromisso." }
        require(event.scheduledAt > 0) { "Informe uma data válida." }
        require(event.kind in setOf("FOLLOW_UP", "SERVICE")) { "Tipo de evento inválido." }
        require(event.status in setOf("PENDING", "DONE", "CANCELED")) { "Situação inválida." }
        if (event.quoteId != null) {
            require(quoteDao.getQuoteWithItems(event.quoteId) != null) { "O orçamento foi excluído." }
        }
        val old = if (event.id > 0) agendaDao.getById(event.id) else null
        agendaDao.upsert(event.copy(
            title = event.title.trim().take(160),
            notes = event.notes.trim().take(1200),
            createdAt = old?.createdAt ?: System.currentTimeMillis(),
            remindedAt = if (old?.remindAt == event.remindAt &&
                old?.status == event.status) old.remindedAt else null,
            updatedAt = System.currentTimeMillis()
        ))
    }
    suspend fun deleteAgendaEvent(id: Long) = agendaDao.delete(id)
    suspend fun getAgendaEvent(id: Long) = agendaDao.getById(id)
    suspend fun getDueAgendaReminders(now: Long) = agendaDao.getDueReminders(now)
    suspend fun markAgendaReminded(id: Long, now: Long) = agendaDao.markReminded(id, now)

    /** Eventos da aprovação online só podem alterar proposta pendente, jamais uma paga. */
    suspend fun applyOnlineDecision(quoteId: Long, decision: String) = db.withTransaction {
        require(decision in setOf("APPROVED", "REJECTED"))
        val current = quoteDao.getQuoteWithItems(quoteId)?.quote ?: error("Orçamento inexistente.")
        require(current.status == "PENDING" && paymentDao.countForQuote(quoteId) == 0) {
            "A situação já mudou; confira antes de atualizar."
        }
        quoteDao.updateStatus(quoteId, decision)
    }
    val templates: Flow<List<QuoteTemplateEntity>> = templateDao.observeAll()
    fun observeServiceOrder(quoteId: Long): Flow<ServiceOrderEntity?> = orderDao.observeByQuote(quoteId)
    suspend fun getServiceOrder(quoteId: Long): ServiceOrderEntity? = orderDao.getByQuote(quoteId)

    /** A OS sempre reutiliza o orçamento e não cria um segundo faturamento. */
    suspend fun saveServiceOrder(
        quoteId: Long,
        responsible: String,
        description: String,
        materials: String,
        notes: String,
        scheduledAt: Long?,
        status: String
    ): Long = db.withTransaction {
        require(status in listOf("SCHEDULED", "IN_PROGRESS", "COMPLETED")) { "Situação da OS inválida." }
        requirePro("Criar ou editar ordem de serviço")
        val q = quoteDao.getQuoteWithItems(quoteId)?.quote ?: error("Orçamento não encontrado.")
        require(q.status in listOf("APPROVED", "COMPLETED")) { "Aprove o orçamento para criar a OS." }
        val old = orderDao.getByQuote(quoteId)
        val now = System.currentTimeMillis()
        val order = ServiceOrderEntity(
            id = old?.id ?: 0L,
            quoteId = quoteId,
            status = status,
            scheduledAt = scheduledAt,
            completedAt = if (status == "COMPLETED") old?.completedAt ?: now else null,
            responsible = responsible.trim(),
            description = description.trim(),
            materials = materials.trim(),
            notes = notes.trim(),
            createdAt = old?.createdAt ?: now,
            updatedAt = now
        )
        val id = if (old == null) orderDao.insert(order) else {
            orderDao.update(order)
            old.id
        }
        if (status == "COMPLETED" && q.status != "COMPLETED") {
            quoteDao.updateStatus(quoteId, "COMPLETED")
        }
        id
    }

    suspend fun saveTemplateFromQuote(quoteId: Long, title: String): Long = db.withTransaction {
        requirePro("Criar modelos de orçamento")
        require(title.trim().isNotBlank()) { "Informe o nome do modelo." }
        val quote = quoteDao.getQuoteWithItems(quoteId) ?: error("Orçamento não encontrado.")
        val json = JSONArray().apply {
            quote.items.forEach { item ->
                put(JSONObject().apply {
                    put("description", item.description)
                    put("type", item.type)
                    put("unit", item.unit)
                    put("quantity", item.quantity)
                    put("unitPrice", item.unitPrice)
                })
            }
        }
        templateDao.insert(QuoteTemplateEntity(
            title = title.trim().take(80),
            itemsJson = json.toString(),
            paymentTerms = quote.quote.paymentTerms,
            paymentMethod = quote.quote.paymentMethod,
            notes = quote.quote.notes
        ))
    }

    suspend fun deleteTemplate(id: Long) = templateDao.delete(id)
    suspend fun loadDraft(key: String): String? = draftDao.get(key)?.payloadJson
    suspend fun saveDraft(key: String, payload: String) =
        draftDao.upsert(DraftQuoteEntity(draftKey = key, payloadJson = payload))
    suspend fun clearDraft(key: String) = draftDao.delete(key)

    suspend fun getPayments(quoteId: Long): List<PaymentEntity> = paymentDao.getByQuote(quoteId)

    /**
     * Valor em centavos, conferido na mesma transação do saldo.
     * O aplicativo registra declaração manual do usuário, não confirmação bancária.
     */
    suspend fun registerPayment(
        quoteId: Long,
        amountCents: Long,
        paidAt: Long,
        method: String,
        notes: String
    ): Long = db.withTransaction {
        requirePro("Registrar recebimentos e emitir novos recibos")
        require(amountCents > 0) { "Informe um valor maior que zero." }
        require(method.isNotBlank()) { "Informe a forma de pagamento." }
        require(paidAt > 0 && paidAt <= System.currentTimeMillis() + 86_400_000L) {
            "Informe uma data válida para o recebimento."
        }
        val quote = quoteDao.getQuoteWithItems(quoteId)?.quote
            ?: error("O orçamento não foi encontrado.")
        require(quote.status == "APPROVED" || quote.status == "COMPLETED") {
            "Aprove o orçamento antes de registrar o recebimento."
        }
        val totalCents = BigDecimal.valueOf(quote.total)
            .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        val received = paymentDao.receivedCents(quoteId)
        require(amountCents <= totalCents - received) {
            "O pagamento é maior que o valor ainda disponível para receber."
        }
        paymentDao.insert(PaymentEntity(
            quoteId = quoteId,
            amountCents = amountCents,
            paidAt = paidAt,
            method = method.trim(),
            notes = notes.trim()
        ))
    }

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
        return saveQuoteWithItems(duplicatedQuote, duplicatedItems)
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
            products = productServiceDao.getAllRaw(),
            payments = paymentDao.getAllRaw(),
            serviceOrders = orderDao.getAllRaw(),
            templates = templateDao.getAllRaw(),
            drafts = draftDao.getAllRaw(),
            quoteCosts = costDao.getAllRaw(),
            agendaEvents = agendaDao.getAllRaw()
        )
    }

    suspend fun restoreDatabaseRawData(
        profile: CompanyProfileEntity?,
        quotes: List<QuoteEntity>,
        items: List<QuoteItemEntity>,
        clients: List<ClientEntity>,
        products: List<ProductServiceEntity>,
        replaceExisting: Boolean,
        payments: List<PaymentEntity> = emptyList(),
        serviceOrders: List<ServiceOrderEntity> = emptyList(),
        templates: List<QuoteTemplateEntity> = emptyList(),
        drafts: List<DraftQuoteEntity> = emptyList(),
        quoteCosts: List<QuoteCostEntity> = emptyList(),
        agendaEvents: List<AgendaEventEntity> = emptyList()
    ) {
        db.withTransaction {
            if (replaceExisting) {
                // Modo SUBSTITUIR: limpa tabelas e restaura os IDs exatamente como vieram
                orderDao.clearAll()
                paymentDao.clearAll()
                costDao.clearAll()
                agendaDao.clearAll()
                templateDao.clearAll()
                draftDao.clearAll()
                quoteDao.clearAllQuoteItems()
                quoteDao.clearAllQuotes()
                clientDao.clearAllClients()
                productServiceDao.clearAll()

                profile?.let { companyProfileDao.upsertProfile(it.copy(id = 1)) }
                if (clients.isNotEmpty()) clientDao.insertAll(clients)
                if (products.isNotEmpty()) productServiceDao.insertAll(products)
                if (quotes.isNotEmpty()) quoteDao.insertQuotesRaw(quotes)
                if (items.isNotEmpty()) quoteDao.insertQuoteItemsRaw(items)
                if (payments.isNotEmpty()) paymentDao.insertRaw(payments)
                if (serviceOrders.isNotEmpty()) orderDao.insertRaw(serviceOrders)
                if (templates.isNotEmpty()) templateDao.insertRaw(templates)
                if (drafts.isNotEmpty()) draftDao.insertRaw(drafts)
                if (quoteCosts.isNotEmpty()) costDao.insertRaw(quoteCosts)
                if (agendaEvents.isNotEmpty()) agendaDao.insertRaw(agendaEvents)
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

                // Preserve recibos históricos com referências aos orçamentos importados.
                val remappedPayments = payments.map { payment ->
                    val newQuoteId = quoteIdMap[payment.quoteId]
                        ?: error("Backup contém recebimento sem orçamento correspondente.")
                    payment.copy(id = 0, quoteId = newQuoteId)
                }
                if (remappedPayments.isNotEmpty()) paymentDao.insertRaw(remappedPayments)

                val remappedOrders = serviceOrders.map { order ->
                    val newQuoteId = quoteIdMap[order.quoteId]
                        ?: error("Backup possui OS sem orçamento correspondente.")
                    order.copy(id = 0, quoteId = newQuoteId)
                }
                if (remappedOrders.isNotEmpty()) orderDao.insertRaw(remappedOrders)
                for (template in templates) {
                    templateDao.insert(template.copy(id = 0))
                }
                // Custos pertencem ao orçamento importado; nunca sobrescrevem custos locais.
                for (cost in quoteCosts) {
                    val newQuoteId = quoteIdMap[cost.quoteId]
                        ?: error("Custo de orçamento não localizado na importação.")
                    costDao.upsert(cost.copy(quoteId = newQuoteId))
                }
                for (event in agendaEvents) {
                    val newQuoteId = event.quoteId?.let { oldId -> quoteIdMap[oldId] }
                    agendaDao.upsert(event.copy(id = 0L, quoteId = newQuoteId))
                }
                // Rascunhos locais prevalecem na mesclagem; não substituir silenciosamente.
                for (draft in drafts) {
                    if (draftDao.get(draft.draftKey) == null) draftDao.upsert(draft)
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
    val products: List<ProductServiceEntity>,
    val payments: List<PaymentEntity> = emptyList(),
    val serviceOrders: List<ServiceOrderEntity> = emptyList(),
    val templates: List<QuoteTemplateEntity> = emptyList(),
    val drafts: List<DraftQuoteEntity> = emptyList(),
    val quoteCosts: List<QuoteCostEntity> = emptyList(),
    val agendaEvents: List<AgendaEventEntity> = emptyList()
)
