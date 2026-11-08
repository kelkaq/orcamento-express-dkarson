package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.entity.QuoteEntity
import com.example.data.entity.QuoteItemEntity
import com.example.data.entity.QuoteWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteDao {
    @Transaction
    @Query("SELECT * FROM quotes ORDER BY issueDate DESC, id DESC")
    fun getAllQuotesWithItems(): Flow<List<QuoteWithItems>>

    @Transaction
    @Query("""
        SELECT * FROM quotes 
        WHERE (:status = 'ALL' OR status = :status) 
          AND (:query = '' OR (clientName LIKE '%' || :query || '%' OR quoteNumber LIKE '%' || :query || '%')) 
        ORDER BY issueDate DESC, id DESC
    """)
    fun getFilteredQuotesWithItems(status: String, query: String): Flow<List<QuoteWithItems>>

    @Transaction
    @Query("SELECT * FROM quotes WHERE status = :status ORDER BY issueDate DESC, id DESC")
    fun getQuotesByStatus(status: String): Flow<List<QuoteWithItems>>

    @Transaction
    @Query("SELECT * FROM quotes WHERE clientName LIKE '%' || :query || '%' OR quoteNumber LIKE '%' || :query || '%' ORDER BY issueDate DESC, id DESC")
    fun searchQuotes(query: String): Flow<List<QuoteWithItems>>

    @Transaction
    @Query("SELECT * FROM quotes WHERE id = :id LIMIT 1")
    fun getQuoteWithItemsFlow(id: Long): Flow<QuoteWithItems?>

    @Transaction
    @Query("SELECT * FROM quotes WHERE id = :id LIMIT 1")
    suspend fun getQuoteWithItems(id: Long): QuoteWithItems?

    @Query("SELECT quoteNumber FROM quotes WHERE quoteNumber LIKE '#ORC-' || :year || '-%'")
    suspend fun getAllQuoteNumbersForYear(year: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: QuoteEntity): Long

    @Update
    suspend fun updateQuote(quote: QuoteEntity)

    @Delete
    suspend fun deleteQuote(quote: QuoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuoteItems(items: List<QuoteItemEntity>)

    @Query("DELETE FROM quote_items WHERE quoteId = :quoteId")
    suspend fun deleteItemsByQuoteId(quoteId: Long)

    @Query("UPDATE quotes SET status = :newStatus, updatedAt = :updatedAt WHERE id = :quoteId")
    suspend fun updateStatus(quoteId: Long, newStatus: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM quotes WHERE createdAt >= :start AND createdAt < :end")
    suspend fun countCreatedBetween(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM quotes")
    suspend fun countQuotes(): Int

    @Query("SELECT COUNT(*) FROM quotes WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("SELECT SUM(total) FROM quotes WHERE status IN ('APPROVED', 'COMPLETED')")
    suspend fun getTotalApprovedRevenue(): Double?

    @Transaction
    suspend fun saveQuoteWithItems(quote: QuoteEntity, items: List<QuoteItemEntity>): Long {
        val quoteId = if (quote.id > 0) {
            updateQuote(quote.copy(updatedAt = System.currentTimeMillis()))
            deleteItemsByQuoteId(quote.id)
            quote.id
        } else {
            insertQuote(quote.copy(createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
        }
        val itemsWithQuoteId = items.map { it.copy(id = 0, quoteId = quoteId) }
        insertQuoteItems(itemsWithQuoteId)
        return quoteId
    }

    @Query("SELECT * FROM quotes")
    suspend fun getAllQuotesRaw(): List<QuoteEntity>

    @Query("SELECT * FROM quote_items")
    suspend fun getAllQuoteItemsRaw(): List<QuoteItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotesRaw(quotes: List<QuoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuoteItemsRaw(items: List<QuoteItemEntity>)

    @Query("DELETE FROM quotes")
    suspend fun clearAllQuotes()

    @Query("DELETE FROM quote_items")
    suspend fun clearAllQuoteItems()
}
