package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ServiceOrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceOrderDao {
    @Query("SELECT * FROM service_orders WHERE quoteId = :quoteId LIMIT 1")
    fun observeByQuote(quoteId: Long): Flow<ServiceOrderEntity?>

    @Query("SELECT * FROM service_orders WHERE quoteId = :quoteId LIMIT 1")
    suspend fun getByQuote(quoteId: Long): ServiceOrderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(order: ServiceOrderEntity): Long

    @Update
    suspend fun update(order: ServiceOrderEntity)

    @Query("SELECT * FROM service_orders ORDER BY scheduledAt DESC")
    fun observeAll(): Flow<List<ServiceOrderEntity>>

    @Query("SELECT * FROM service_orders")
    suspend fun getAllRaw(): List<ServiceOrderEntity>

    @Query("DELETE FROM service_orders")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(items: List<ServiceOrderEntity>)
}
