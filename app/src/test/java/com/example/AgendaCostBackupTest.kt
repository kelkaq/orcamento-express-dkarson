package com.example

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.AgendaEventEntity
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.repository.QuoteRepository
import com.example.util.BackupManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class AgendaCostBackupTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: QuoteRepository
    @Before fun init() {
        db=Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo=QuoteRepository(db){true}
    }
    @After fun close()=db.close()

    private suspend fun createQuote(): Long = repo.saveQuoteWithItems(
        QuoteEntity(quoteNumber="ORC-TEST",clientName="Cliente",total=1500.0),
        listOf(QuoteItemEntity(quoteId=0,description="Pintura",quantity=1.0,
            unitPrice=1500.0,totalPrice=1500.0))
    )

    @Test fun `agenda and costs roundtrip in backup version four`()= runBlocking {
        val quoteId=createQuote()
        repo.saveQuoteCosts(quoteId,18000,42000,5000,1000)
        repo.saveAgendaEvent(AgendaEventEntity(
            quoteId=quoteId,title="Retornar orçamento",clientName="Cliente",
            scheduledAt=System.currentTimeMillis()+3_600_000L,
            remindAt=System.currentTimeMillis()+1_800_000L
        ))
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val file=BackupManager.exportBackup(ctx,repo)
        val root=JSONObject(file.readText())
        assertEquals(4,root.getInt("version"))
        assertEquals(1,root.getJSONArray("quoteCosts").length())
        assertEquals(1,root.getJSONArray("agendaEvents").length())
        assertTrue(BackupManager.validateBackup(ctx,Uri.fromFile(file)).isValid)
        val restored=BackupManager.restoreFromUri(ctx,Uri.fromFile(file),repo,replaceExisting=true)
        assertTrue(restored.errorMessage.orEmpty(),restored.success)
        assertEquals(66000L,repo.getQuoteCosts(quoteId)?.totalCents)
        assertEquals(1,repo.agendaEvents.first().size)
    }

    @Test fun `editing an old quote preserves its original creation timestamp`()=runBlocking {
        val quoteId=createQuote()
        val original=repo.getQuoteWithItems(quoteId)!!.quote
        repo.saveQuoteWithItems(
            original.copy(notes="Alterado",createdAt=System.currentTimeMillis()+999000L),
            repo.getQuoteWithItems(quoteId)!!.items
        )
        assertEquals(original.createdAt,repo.getQuoteWithItems(quoteId)!!.quote.createdAt)
    }

    @Test fun `a due reminder can be marked only once`()=runBlocking {
        val id=repo.saveAgendaEvent(AgendaEventEntity(
            title="Retornar",scheduledAt=System.currentTimeMillis()-10000,
            remindAt=System.currentTimeMillis()-12000
        ))
        assertEquals(1,repo.getDueAgendaReminders(System.currentTimeMillis()).size)
        assertEquals(1,repo.markAgendaReminded(id,System.currentTimeMillis()))
        assertEquals(0,repo.markAgendaReminded(id,System.currentTimeMillis()))
        assertTrue(repo.getDueAgendaReminders(System.currentTimeMillis()).isEmpty())
    }
}
