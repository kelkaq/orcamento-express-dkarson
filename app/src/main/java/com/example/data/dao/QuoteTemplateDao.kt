package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.QuoteTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteTemplateDao {
    @Query("SELECT * FROM quote_templates ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuoteTemplateEntity>>

    @Query("SELECT * FROM quote_templates")
    suspend fun getAllRaw(): List<QuoteTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: QuoteTemplateEntity): Long

    @Query("DELETE FROM quote_templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM quote_templates")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(templates: List<QuoteTemplateEntity>)
}
