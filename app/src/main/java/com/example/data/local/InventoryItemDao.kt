package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {

    @Query("SELECT * FROM inventory_items ORDER BY id DESC")
    fun getAllInventoryItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryItemById(id: Int): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE product_id = :productId")
    fun getInventoryByProductId(productId: Int): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE section_number = :sectionNumber")
    fun getInventoryBySectionNumber(sectionNumber: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE quantity_stock <= min_stock_alert ORDER BY quantity_stock ASC")
    fun getLowStockInventoryItems(): Flow<List<InventoryItem>>

    @Query("""
        SELECT * FROM inventory_items 
        WHERE section_number LIKE '%' || :query || '%' 
           OR batch_number LIKE '%' || :query || '%' 
           OR warehouse_location LIKE '%' || :query || '%'
        ORDER BY section_number ASC
    """)
    fun searchInventoryItems(query: String): Flow<List<InventoryItem>>

    @androidx.room.Transaction
    @Query("SELECT * FROM inventory_items ORDER BY id DESC")
    fun getInventoryWithProductFlow(): Flow<List<InventoryWithProduct>>

    @androidx.room.Transaction
    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getInventoryWithProductById(id: Int): InventoryWithProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInventoryItems(items: List<InventoryItem>): List<Long>

    @Update
    suspend fun updateInventoryItem(item: InventoryItem)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteInventoryItemById(id: Int)

    @Query("UPDATE inventory_items SET quantity_stock = :newQuantity WHERE id = :id")
    suspend fun updateStockQuantity(id: Int, newQuantity: Int)

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun getInventoryCount(): Int
}
