package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.entity.QuoteSequenceEntity

@Dao
interface QuoteSequenceDao {
    @Query("SELECT lastSequence FROM quote_sequences WHERE year = :year LIMIT 1")
    suspend fun getLastSequence(year: Int): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSequence(entity: QuoteSequenceEntity)

    @Transaction
    suspend fun getNextSequenceNumber(year: Int, minStartSequence: Int): Int {
        val current = getLastSequence(year) ?: 0
        val base = maxOf(current, minStartSequence)
        val next = base + 1
        setSequence(QuoteSequenceEntity(year = year, lastSequence = next))
        return next
    }
}
