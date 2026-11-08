package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.billing.FreePlanLimitException
import com.example.billing.PlanPolicy
import com.example.billing.ProRequiredException
import com.example.billing.EntitlementStore
import com.example.data.database.AppDatabase
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FreemiumPolicyTest {
    private lateinit var db: AppDatabase
    private lateinit var free: QuoteRepository
    private lateinit var paid: QuoteRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        free = QuoteRepository(db) { false }
        paid = QuoteRepository(db) { true }
    }
    @After fun tearDown() = db.close()

    private suspend fun create(repo: QuoteRepository, n: Int): Long =
        repo.saveQuoteWithItems(
            QuoteEntity(quoteNumber = "#TEST-$n", clientName = "Cliente $n",
                status = "APPROVED", total = 100.0),
            listOf(QuoteItemEntity(quoteId = 0, description = "Serviço",
                unitPrice = 100.0, quantity = 1.0, totalPrice = 100.0))
        )

    @Test fun `free creates exactly five quotes, pro can create more, old can be edited`() = runBlocking {
        val ids = (1..5).map { create(free, it) }
        assertEquals(5, free.monthlyFreeQuotesUsed())
        assertTrue(runCatching { create(free, 6) }.exceptionOrNull() is FreePlanLimitException)
        assertTrue(runCatching { free.duplicateQuote(ids.first()) }
            .exceptionOrNull() is FreePlanLimitException)
        // Editing is not a new quote and must NOT consume quota.
        val old = free.getQuoteWithItems(ids.first())!!
        free.saveQuoteWithItems(old.quote.copy(notes = "Atualizado"), old.items)
        assertEquals("Atualizado", free.getQuoteWithItems(ids.first())!!.quote.notes)
        assertEquals(5, free.monthlyFreeQuotesUsed())
        assertTrue(create(paid, 6) > 0)
    }

    @Test fun `paid features reject free users but never delete old documents`() = runBlocking {
        val quoteId = create(free, 1)
        assertTrue(runCatching {
            free.saveServiceOrder(quoteId, "Equipe", "Troca", "", "", null, "SCHEDULED")
        }.exceptionOrNull() is ProRequiredException)
        assertTrue(runCatching {
            free.registerPayment(quoteId, 5000, System.currentTimeMillis(), "PIX", "")
        }.exceptionOrNull() is ProRequiredException)
        assertTrue(runCatching {
            free.saveTemplateFromQuote(quoteId, "Modelo")
        }.exceptionOrNull() is ProRequiredException)
        paid.saveServiceOrder(quoteId, "Equipe", "Troca", "", "", null, "SCHEDULED")
        paid.registerPayment(quoteId, 5000, System.currentTimeMillis(), "PIX", "")
        assertEquals(1, free.getPayments(quoteId).size)
        assertEquals("SCHEDULED", free.getServiceOrder(quoteId)?.status)
    }

    @Test fun `five quota resets at local month boundary`() {
        val c = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = c.timeInMillis
        val old = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 12, 0, 0)
        }.timeInMillis
        val dates = listOf(old, now, now + 1200)
        assertEquals(3, PlanPolicy.remainingNewQuotes(dates, now))
        assertEquals(0, PlanPolicy.remainingNewQuotes(List(6) { now }, now))
    }

    @Test fun `unverified subscription never activates Pro`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = EntitlementStore(context)
        store.revoke()
        assertFalse(store.isProNow())
        // Only backend-verified path may set a valid cached entitlement.
        store.grantVerified(System.currentTimeMillis() - 1L)
        assertFalse(store.isProNow())
        store.revoke()
    }
}
