package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.QuoteCostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteCostDao {
    @Query("SELECT * FROM quote_costs WHERE quoteId = :quoteId LIMIT 1")
    fun observeByQuote(quoteId: Long): Flow<QuoteCostEntity?>

    @Query("SELECT * FROM quote_costs WHERE quoteId = :quoteId LIMIT 1")
    suspend fun getByQuote(quoteId: Long): QuoteCostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: QuoteCostEntity)

    @Query("SELECT * FROM quote_costs ORDER BY quoteId")
    fun observeAll(): Flow<List<QuoteCostEntity>>

    @Query("SELECT * FROM quote_costs")
    suspend fun getAllRaw(): List<QuoteCostEntity>

    @Query("DELETE FROM quote_costs")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(values: List<QuoteCostEntity>)
}
