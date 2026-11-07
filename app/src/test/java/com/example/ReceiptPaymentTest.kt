package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import com.example.util.BackupManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReceiptPaymentTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: QuoteRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = QuoteRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `only approved quotes can have receipts and partial amounts are accurate`() = runBlocking {
        val quoteId = repo.saveQuoteWithItems(
            QuoteEntity(quoteNumber = "#ORC-TEST-0001", clientName = "Cliente de Teste",
                status = "PENDING", total = 275.50),
            listOf(QuoteItemEntity(quoteId = 0, description = "Serviço", totalPrice = 275.50))
        )

        assertTrue(runCatching {
            repo.registerPayment(quoteId, 10000L, System.currentTimeMillis(), "PIX", "")
        }.isFailure)

        repo.updateQuoteStatus(quoteId, "APPROVED")
        val p1 = repo.registerPayment(quoteId, 10000L, System.currentTimeMillis(), "PIX", "Entrada")
        val p2 = repo.registerPayment(quoteId, 17550L, System.currentTimeMillis(), "Dinheiro", "Saldo")

        assertTrue(p1 > 0 && p2 > p1)
        val payments = repo.getPayments(quoteId)
        assertEquals(2, payments.size)
        assertEquals(27550L, payments.sumOf { it.amountCents })

        assertTrue(runCatching {
            repo.registerPayment(quoteId, 1L, System.currentTimeMillis(), "PIX", "")
        }.isFailure)
        assertTrue(runCatching {
            repo.updateQuoteStatus(quoteId, "PENDING")
        }.isFailure)
        assertTrue(runCatching {
            repo.saveQuoteWithItems(
                QuoteEntity(id = quoteId, quoteNumber = "#ORC-TEST-0001",
                    status = "APPROVED", clientName = "Cliente de Teste", total = 100.0),
                emptyList()
            )
        }.isFailure)
        assertTrue(runCatching {
            repo.deleteQuote(repo.getQuoteWithItems(quoteId)!!.quote)
        }.isFailure)
    }

    @Test
    fun `backup preserves payment receipts after replace restore`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quoteId = repo.saveQuoteWithItems(
            QuoteEntity(quoteNumber = "#ORC-TEST-0002", clientName = "Cliente",
                status = "APPROVED", total = 50.00),
            listOf(QuoteItemEntity(quoteId = 0, description = "Serviço", totalPrice = 50.00))
        )
        repo.registerPayment(quoteId, 5000L, System.currentTimeMillis(), "PIX", "Pago")
        val backup = BackupManager.exportBackup(context, repo)
        val root = JSONObject(backup.readText(Charsets.UTF_8))
        assertEquals(3, root.getInt("version"))
        assertEquals(1, root.getJSONArray("payments").length())
        assertTrue(BackupManager.validateBackup(context, Uri.fromFile(backup)).isValid)

        val restored = BackupManager.restoreFromUri(
            context, Uri.fromFile(backup), repo, replaceExisting = true
        )
        assertTrue(restored.errorMessage ?: "", restored.success)
        val restoredPayments = repo.getPayments(quoteId)
        assertEquals(1, restoredPayments.size)
        assertEquals(5000L, restoredPayments.first().amountCents)
    }

    @Test
    fun `receipt backup rejects malformed payment relations`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val quoteId = repo.saveQuoteWithItems(
            QuoteEntity(quoteNumber = "#ORC-TEST-0003", clientName = "Cliente",
                status = "APPROVED", total = 100.00),
            listOf(QuoteItemEntity(quoteId = 0, description = "Serviço", totalPrice = 100.00))
        )
        repo.registerPayment(quoteId, 10000L, System.currentTimeMillis(), "PIX", "")
        val backup = BackupManager.exportBackup(context, repo)
        val root = JSONObject(backup.readText(Charsets.UTF_8))
        root.getJSONArray("payments").getJSONObject(0).put("quoteId", 987654321L)
        val invalidFile = java.io.File(context.cacheDir, "payment-bad-reference.json")
        invalidFile.writeText(root.toString())
        val validation = BackupManager.validateBackup(context, Uri.fromFile(invalidFile))
        assertFalse(validation.isValid)
    }
}
