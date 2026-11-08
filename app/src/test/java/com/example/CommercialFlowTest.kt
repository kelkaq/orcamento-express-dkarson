package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import com.example.ui.viewmodel.QuoteEditorUiState
import com.example.util.BackupManager
import com.example.util.QuoteDraftSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CommercialFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: QuoteRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        // Testes de funções Pro partem de assinatura previamente verificada.
        repo = QuoteRepository(db) { true }
    }

    @After
    fun tearDown() { db.close() }

    private suspend fun approvedQuote(): Long =
        repo.saveQuoteWithItems(
            QuoteEntity(quoteNumber = "#ORC-COM-0001", status = "APPROVED",
                clientName = "Maria", total = 250.0),
            listOf(QuoteItemEntity(quoteId = 0, description = "Instalação", unit = "un",
                quantity = 1.0, unitPrice = 250.0, totalPrice = 250.0))
        )

    @Test
    fun `service order is unique linked and must not duplicate earnings`() = runBlocking {
        val id = approvedQuote()
        val before = repo.dashboardMetricsByPeriod("ALL").first()
        val orderId = repo.saveServiceOrder(id, "Técnico", "Montagem", "Cabos",
            "Sem pendências", null, "SCHEDULED")
        assertTrue(orderId > 0)
        val updated = repo.saveServiceOrder(id, "Técnico", "Montagem finalizada",
            "Cabos", "", null, "COMPLETED")
        assertEquals(orderId, updated)
        assertEquals("COMPLETED", repo.getQuoteWithItems(id)?.quote?.status)
        assertEquals("COMPLETED", repo.getServiceOrder(id)?.status)
        val after = repo.dashboardMetricsByPeriod("ALL").first()
        assertEquals(before.totalApprovedRevenue, after.totalApprovedRevenue, 0.01)
        assertEquals(1, after.completedCount)
    }

    @Test
    fun `templates keep items and reusable terms without clients`() = runBlocking {
        val id = approvedQuote()
        val templateId = repo.saveTemplateFromQuote(id, "Instalação residencial")
        assertTrue(templateId > 0)
        val model = repo.templates.first().first()
        assertEquals("Instalação residencial", model.title)
        assertTrue(model.itemsJson.contains("Instalação"))
        assertFalse(model.itemsJson.contains("Maria"))
    }

    @Test
    fun `draft persistence is recoverable and removable`() = runBlocking {
        val json = QuoteDraftSerializer.encode(
            QuoteEditorUiState(quoteId = 0, quoteNumber = "#ORC-TEST",
                clientName = "Cliente retomado", notes = "Observação ditada")
        )
        repo.saveDraft("new", json)
        val restored = QuoteDraftSerializer.decode(
            repo.loadDraft("new")!!, QuoteEditorUiState(quoteId = 0)
        )
        assertTrue(restored.draftRecovered)
        assertEquals("Cliente retomado", restored.clientName)
        repo.clearDraft("new")
        assertEquals(null, repo.loadDraft("new"))
    }

    @Test
    fun `complete backup preserves payments orders templates and drafts`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = approvedQuote()
        repo.registerPayment(id, 15000L, System.currentTimeMillis(), "PIX", "Sinal")
        repo.saveServiceOrder(id, "Técnico", "Execução", "", "", null, "SCHEDULED")
        repo.saveTemplateFromQuote(id, "Modelo completo")
        repo.saveDraft("new", QuoteDraftSerializer.encode(QuoteEditorUiState()))

        val exported = BackupManager.exportBackup(context, repo)
        val root = JSONObject(exported.readText())
        assertEquals(4, root.getInt("version"))
        assertEquals(1, root.getJSONArray("payments").length())
        assertEquals(1, root.getJSONArray("serviceOrders").length())
        assertEquals(1, root.getJSONArray("templates").length())
        assertEquals(1, root.getJSONArray("drafts").length())
        assertTrue(BackupManager.validateBackup(context, Uri.fromFile(exported)).isValid)

        val result = BackupManager.restoreFromUri(context, Uri.fromFile(exported),
            repo, replaceExisting = true)
        assertTrue(result.errorMessage ?: "", result.success)
        assertEquals(1, repo.getPayments(id).size)
        assertNotNull(repo.getServiceOrder(id))
        assertEquals(1, repo.templates.first().size)
        assertNotNull(repo.loadDraft("new"))
    }
}
