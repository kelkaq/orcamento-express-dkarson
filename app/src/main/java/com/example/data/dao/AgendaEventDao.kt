package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.AgendaEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AgendaEventDao {
    @Query("SELECT * FROM agenda_events ORDER BY scheduledAt ASC, id ASC")
    fun observeAll(): Flow<List<AgendaEventEntity>>

    @Query("SELECT * FROM agenda_events ORDER BY scheduledAt ASC")
    suspend fun getAllRaw(): List<AgendaEventEntity>

    @Query("SELECT * FROM agenda_events WHERE id = :id")
    suspend fun getById(id: Long): AgendaEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: AgendaEventEntity): Long

    @Query("DELETE FROM agenda_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM agenda_events")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRaw(items: List<AgendaEventEntity>)

    @Query("SELECT * FROM agenda_events WHERE status = 'PENDING' AND remindAt IS NOT NULL AND remindAt <= :now AND remindedAt IS NULL ORDER BY remindAt ASC")
    suspend fun getDueReminders(now: Long): List<AgendaEventEntity>

    @Query("UPDATE agenda_events SET remindedAt = :now WHERE id = :id AND remindedAt IS NULL AND status = 'PENDING'")
    suspend fun markReminded(id: Long, now: Long): Int
}
