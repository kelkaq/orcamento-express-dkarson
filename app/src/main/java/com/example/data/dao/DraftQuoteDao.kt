package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.DraftQuoteEntity

@Dao
interface DraftQuoteDao {
    @Query("SELECT * FROM quote_drafts WHERE draftKey = :key")
    suspend fun get(key: String): DraftQuoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftQuoteEntity)

    @Query("DELETE FROM quote_drafts WHERE draftKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM quote_drafts")
    suspend fun getAllRaw(): List<DraftQuoteEntity>

    @Query("DELETE FROM quote_drafts")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(items: List<DraftQuoteEntity>)
}
