package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE quoteId = :quoteId ORDER BY paidAt DESC, id DESC")
    fun observeByQuote(quoteId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE quoteId = :quoteId ORDER BY paidAt DESC, id DESC")
    suspend fun getByQuote(quoteId: Long): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM payments WHERE quoteId = :quoteId")
    suspend fun receivedCents(quoteId: Long): Long

    @Query("SELECT COUNT(*) FROM payments WHERE quoteId = :quoteId")
    suspend fun countForQuote(quoteId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments ORDER BY id")
    suspend fun getAllRaw(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(payments: List<PaymentEntity>)

    @Query("DELETE FROM payments")
    suspend fun clearAll()
}
