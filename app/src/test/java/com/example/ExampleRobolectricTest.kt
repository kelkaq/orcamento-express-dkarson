package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.ProductServiceEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import com.example.util.BackupManager
import com.example.util.PdfGenerator
import kotlinx.coroutines.runBlocking
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
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: QuoteRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = QuoteRepository(db)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        // testDebugUnitTest usa os recursos de src/debug: o nome distingue a instalação de teste.
        assertEquals("Orçamento Express (Teste)", appName)
    }

    @Test
    fun `test client and product persistence`() = runBlocking {
        val clientId = repository.saveClient(
            ClientEntity(
                name = "Carlos Silva",
                phone = "11999998888",
                document = "12345678901",
                email = "carlos@example.com",
                address = "Rua das Flores, 123"
            )
        )
        assertTrue(clientId > 0)

        val retrievedClient = repository.getClientById(clientId)
        assertNotNull(retrievedClient)
        assertEquals("Carlos Silva", retrievedClient?.name)

        val prodId = repository.saveProductService(
            ProductServiceEntity(
                title = "Pintura Residencial",
                type = "SERVICE",
                unit = "m²",
                unitPrice = 45.0,
                category = "Reforma"
            )
        )
        assertTrue(prodId > 0)
    }

    @Test
    fun `test quote creation, calculation, and duplication`() = runBlocking {
        val quote = QuoteEntity(
            quoteNumber = "#ORC-2026-0001",
            clientName = "Maria Souza",
            clientDocument = "98765432100",
            clientPhone = "11988887777",
            status = "PENDING",
            subtotal = 300.0,
            discountType = "FIXED",
            discountValue = 50.0,
            discountCalculated = 50.0,
            shippingFee = 20.0,
            total = 270.0
        )

        val items = listOf(
            QuoteItemEntity(
                quoteId = 0,
                description = "Troca de fiação",
                type = "SERVICE",
                unit = "hr",
                quantity = 2.0,
                unitPrice = 100.0,
                totalPrice = 200.0
            ),
            QuoteItemEntity(
                quoteId = 0,
                description = "Disjuntor bipolar",
                type = "PRODUCT",
                unit = "un",
                quantity = 1.0,
                unitPrice = 100.0,
                totalPrice = 100.0
            )
        )

        val savedId = repository.saveQuoteWithItems(quote, items)
        assertTrue(savedId > 0)

        val loaded = repository.getQuoteWithItems(savedId)
        assertNotNull(loaded)
        assertEquals(2, loaded?.items?.size)
        assertEquals(270.0, loaded?.quote?.total ?: 0.0, 0.001)

        // Status update
        repository.updateQuoteStatus(savedId, "APPROVED")
        val updated = repository.getQuoteWithItems(savedId)
        assertEquals("APPROVED", updated?.quote?.status)

        // Duplication
        val duplicatedId = repository.duplicateQuote(savedId)
        assertTrue(duplicatedId > 0)
        val duplicated = repository.getQuoteWithItems(duplicatedId)
        assertNotNull(duplicated)
        assertEquals(2, duplicated?.items?.size)
        assertEquals("PENDING", duplicated?.quote?.status)
    }

    @Test
    fun `test quote sequence uniqueness even after deletion`() = runBlocking {
        // Generate first quote number
        val num1 = repository.generateNextQuoteNumber()
        val quote1 = QuoteEntity(quoteNumber = num1, clientName = "Cliente 1", total = 100.0)
        val id1 = repository.saveQuoteWithItems(quote1, listOf(QuoteItemEntity(quoteId = 0, description = "Item", totalPrice = 100.0)))

        // Generate second quote number
        val num2 = repository.generateNextQuoteNumber()
        val quote2 = QuoteEntity(quoteNumber = num2, clientName = "Cliente 2", total = 200.0)
        val id2 = repository.saveQuoteWithItems(quote2, listOf(QuoteItemEntity(quoteId = 0, description = "Item 2", totalPrice = 200.0)))

        assertTrue(num1 != num2)

        // Delete quote 1 and quote 2
        val loaded1 = repository.getQuoteWithItems(id1)
        val loaded2 = repository.getQuoteWithItems(id2)
        if (loaded1 != null) repository.deleteQuote(loaded1.quote)
        if (loaded2 != null) repository.deleteQuote(loaded2.quote)

        // Generate third quote number - MUST be sequential and strictly NOT reuse num1 or num2!
        val num3 = repository.generateNextQuoteNumber()
        assertTrue(num3 != num1)
        assertTrue(num3 != num2)
    }

    @Test
    fun `test backup validation and corrupt file handling`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Corrupt / invalid file
        val corruptFile = File(context.cacheDir, "corrupt_test.json").apply {
            writeText("{ invalid json: 123 }")
        }
        val corruptUri = android.net.Uri.fromFile(corruptFile)
        val corruptResult = BackupManager.validateBackup(context, corruptUri)
        assertFalse(corruptResult.isValid)
        assertNotNull(corruptResult.errorMessage)

        // 2. Incompatible app signature
        val wrongAppFile = File(context.cacheDir, "wrong_app_test.json").apply {
            writeText("""{"app": "Another App", "version": 1}""")
        }
        val wrongAppUri = android.net.Uri.fromFile(wrongAppFile)
        val wrongAppResult = BackupManager.validateBackup(context, wrongAppUri)
        assertFalse(wrongAppResult.isValid)
    }

    @Test
    fun `test backup export and restore with replace vs merge`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Populate initial data
        repository.saveClient(ClientEntity(name = "Cliente Original", phone = "111111111"))
        repository.saveProductService(ProductServiceEntity(title = "Serviço Original", unitPrice = 100.0))
        val quoteOriginal = QuoteEntity(quoteNumber = "#ORC-2026-0001", clientName = "Cliente Original", total = 100.0)
        repository.saveQuoteWithItems(quoteOriginal, listOf(QuoteItemEntity(quoteId = 0, description = "Item Original", totalPrice = 100.0)))

        // Export backup
        val backupFile = BackupManager.exportBackup(context, repository)
        assertTrue(backupFile.exists())
        val backupUri = android.net.Uri.fromFile(backupFile)

        // Validate exported file
        val validation = BackupManager.validateBackup(context, backupUri)
        assertTrue(validation.isValid)
        assertEquals(1, validation.quotesCount)
        assertEquals(1, validation.clientsCount)
        assertEquals(1, validation.productsCount)

        // Add a new local record
        repository.saveClient(ClientEntity(name = "Cliente Local Novo", phone = "222222222"))

        // Test MERGE restore
        val mergeResult = BackupManager.restoreFromUri(context, backupUri, repository, replaceExisting = false)
        assertTrue(mergeResult.success)
        val clientsAfterMerge = repository.getAllClientsRaw()
        assertEquals(2, clientsAfterMerge.size) // Both Cliente Original and Cliente Local Novo exist

        // Test REPLACE restore (with preventive backup)
        val replaceResult = BackupManager.restoreFromUri(context, backupUri, repository, replaceExisting = true)
        assertTrue(replaceResult.success)
        assertNotNull(replaceResult.preventiveBackupPath) // Preventive safety backup was generated
        val clientsAfterReplace = repository.getAllClientsRaw()
        assertEquals(1, clientsAfterReplace.size) // Only the backup's single client remains
        assertEquals("Cliente Original", clientsAfterReplace[0].name)
    }

    @Test
    fun `test multi-page PDF pagination calculation for 1, 10, 30 and 100 items`() {
        // 1 item: fits comfortably on 1 page
        val items1 = listOf(QuoteItemEntity(quoteId = 1, description = "Item 1", totalPrice = 10.0))
        val pages1 = PdfGenerator.calculatePageCount(items1)
        assertEquals(1, pages1)

        // 10 items: fits comfortably on 1 page with summary
        val items10 = (1..10).map { QuoteItemEntity(quoteId = 1, description = "Item $it", totalPrice = 10.0) }
        val pages10 = PdfGenerator.calculatePageCount(items10)
        assertEquals(1, pages10)

        // 30 items: exceeds single page capacity, wraps to 2 pages
        val items30 = (1..30).map { QuoteItemEntity(quoteId = 1, description = "Item $it", totalPrice = 10.0) }
        val pages30 = PdfGenerator.calculatePageCount(items30)
        assertTrue(pages30 >= 2)

        // 100 items: multi-page document
        val items100 = (1..100).map { QuoteItemEntity(quoteId = 1, description = "Item de serviço número $it com descrição detalhada", totalPrice = 10.0) }
        val pages100 = PdfGenerator.calculatePageCount(items100)
        assertTrue(pages100 >= 3)
    }

    @Test
    fun `test merge mode strictly remaps IDs without overwriting existing records`() = runBlocking {
        // 1. Inserir registro local com ID 1
        val localClientId = repository.saveClient(ClientEntity(id = 0, name = "Cliente Local Existente", phone = "1111"))
        assertEquals(1L, localClientId)

        val localProdId = repository.saveProductService(ProductServiceEntity(id = 0, title = "Produto Local Existente", unitPrice = 50.0))
        assertEquals(1L, localProdId)

        val localQuoteNumber = "#ORC-2026-0001"
        val localQuoteId = repository.saveQuoteWithItems(
            QuoteEntity(id = 0, quoteNumber = localQuoteNumber, clientName = "Cliente Local Existente", total = 50.0),
            listOf(QuoteItemEntity(id = 0, quoteId = 0, description = "Item Local", totalPrice = 50.0))
        )
        assertEquals(1L, localQuoteId)

        // 2. Simular dados de backup com IDs COINCIDENTES (id = 1) e nomes diferentes
        val importedClients = listOf(ClientEntity(id = 1L, name = "Cliente Importado Diferente", phone = "9999"))
        val importedProducts = listOf(ProductServiceEntity(id = 1L, title = "Produto Importado Diferente", unitPrice = 90.0))
        val importedQuotes = listOf(QuoteEntity(id = 1L, clientId = 1L, quoteNumber = "#ORC-2026-0001", clientName = "Cliente Importado Diferente", total = 90.0))
        val importedItems = listOf(QuoteItemEntity(id = 1L, quoteId = 1L, productId = 1L, description = "Item Importado", totalPrice = 90.0))

        // 3. Executar restauração em modo MESCLAR (replaceExisting = false)
        repository.restoreDatabaseRawData(
            profile = null,
            quotes = importedQuotes,
            items = importedItems,
            clients = importedClients,
            products = importedProducts,
            replaceExisting = false
        )

        // 4. Verificar que o registro local ID 1 foi TOTALMENTE PRESERVADO e não sobrescrito
        val client1 = repository.getClientById(1L)
        assertNotNull(client1)
        assertEquals("Cliente Local Existente", client1?.name)

        val allClients = repository.getAllClientsRaw()
        assertEquals(2, allClients.size) // Ambos existem com IDs distintos!
        val client2 = allClients.find { it.name == "Cliente Importado Diferente" }
        assertNotNull(client2)
        assertTrue(client2!!.id != 1L)

        // 5. Verificar que o produto local ID 1 também foi preservado
        val allProducts = repository.getAllProductsRaw()
        assertEquals(2, allProducts.size)
        val prod1 = allProducts.find { it.id == 1L }
        assertEquals("Produto Local Existente", prod1?.title)

        // 6. Verificar que os orçamentos e itens foram remapeados sem colisão
        val allQuotes = repository.database.quoteDao().getAllQuotesRaw()
        assertEquals(2, allQuotes.size)
        val originalQuote = allQuotes.find { it.id == 1L }
        assertEquals("Cliente Local Existente", originalQuote?.clientName)
        assertEquals("#ORC-2026-0001", originalQuote?.quoteNumber)

        val importedQuote = allQuotes.find { it.id != 1L }
        assertNotNull(importedQuote)
        assertEquals("Cliente Importado Diferente", importedQuote?.clientName)
        // O número de orçamento foi reatribuído automaticamente para evitar colisão
        assertTrue(importedQuote?.quoteNumber != "#ORC-2026-0001")

        // 7. Verificar que os itens do orçamento importado apontam para a nova cotação e novo produto
        val allItems = repository.database.quoteDao().getAllQuoteItemsRaw()
        assertEquals(2, allItems.size)
        val importedItemInDb = allItems.find { it.quoteId == importedQuote?.id }
        assertNotNull(importedItemInDb)
        assertEquals(client2.id, importedQuote?.clientId)
        assertEquals(client2.name, importedQuote?.clientName)
    }
}
