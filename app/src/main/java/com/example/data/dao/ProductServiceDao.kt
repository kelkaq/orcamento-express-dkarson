package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.ProductServiceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductServiceDao {
    @Query("SELECT * FROM products_services ORDER BY title ASC")
    fun getAll(): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE type = :type ORDER BY title ASC")
    fun getByType(type: String): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE title LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY title ASC")
    fun search(query: String): Flow<List<ProductServiceEntity>>

    @Query("SELECT * FROM products_services WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProductServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ProductServiceEntity): Long

    @Update
    suspend fun update(item: ProductServiceEntity)

    @Delete
    suspend fun delete(item: ProductServiceEntity)

    @Query("SELECT COUNT(*) FROM products_services")
    suspend fun count(): Int

    @Query("SELECT * FROM products_services")
    suspend fun getAllRaw(): List<ProductServiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProductServiceEntity>)

    @Query("DELETE FROM products_services")
    suspend fun clearAll()
}
