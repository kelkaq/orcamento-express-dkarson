package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import com.example.ui.screens.quotes.QuotesScreen
import com.example.ui.viewmodel.CompanyViewModel
import com.example.ui.viewmodel.QuotesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regressões de usabilidade: histórico visível na Home e calculadora alcançável
 * sem abrir o formulário ou procurar depois de vários campos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeHistoryCalculatorUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: AppDatabase
    private lateinit var repository: QuoteRepository

    @Before
    fun setupDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = QuoteRepository(db)
    }

    @After
    fun closeDb() { db.close() }

    private fun showHome(): QuotesViewModel {
        val quotes = QuotesViewModel(repository)
        val company = CompanyViewModel(repository)
        compose.setContent {
            MaterialTheme {
                QuotesScreen(
                    viewModel = quotes,
                    companyViewModel = company,
                    onNewQuote = {},
                    onEditQuote = {},
                    onViewDetail = {},
                    onRestoreData = {}
                )
            }
        }
        return quotes
    }

    @Test
    fun `history title and calculator are visible immediately on an empty install`() {
        showHome()
        compose.onNodeWithTag("home_history_title").assertIsDisplayed()
        compose.onNodeWithTag("home_calculator_top_button").assertIsDisplayed()
        compose.onNodeWithTag("home_calculator_top_button").performClick()
        compose.onNodeWithText("Calculadora rápida").assertIsDisplayed()
    }

    @Test
    fun `a saved quote appears in accessible home history`() {
        val id = runBlocking {
            repository.saveQuoteWithItems(
                QuoteEntity(quoteNumber = "#ORC-2026-1000", clientName = "João",
                    total = 120.0, status = "APPROVED"),
                listOf(QuoteItemEntity(quoteId = 0, description = "Serviço",
                    quantity = 1.0, unitPrice = 120.0, totalPrice = 120.0))
            )
        }
        val quotesViewModel = showHome()
        compose.onNodeWithTag("home_history_title").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 8000) {
            quotesViewModel.quotesList.value.isNotEmpty()
        }
        compose.onNodeWithTag("home_main_scroll_history")
            .performScrollToNode(hasTestTag("quote_card_$id"))
        compose.onNodeWithTag("quote_card_$id").assertIsDisplayed()
    }
}
