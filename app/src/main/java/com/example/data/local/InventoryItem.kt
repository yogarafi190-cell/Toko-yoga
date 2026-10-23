package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.Date

@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("product_id"),
        Index("section_number")
    ]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "product_id")
    val productId: Int,

    @ColumnInfo(name = "section_number")
    val sectionNumber: String,

    @ColumnInfo(name = "batch_number")
    val batchNumber: String = "",

    @ColumnInfo(name = "quantity_stock")
    val quantityStock: Int = 0, // Batang profil aluminium

    @ColumnInfo(name = "standard_length_mm")
    val standardLengthMm: Double = 6000.0, // Panjang standar pabrik 6000 mm (6 m)

    @ColumnInfo(name = "min_stock_alert")
    val minStockAlert: Int = 5,

    @ColumnInfo(name = "warehouse_location")
    val warehouseLocation: String = "Gudang Utama",

    @ColumnInfo(name = "status")
    val status: InventoryStatus = InventoryStatus.IN_STOCK,

    @ColumnInfo(name = "last_restocked")
    val lastRestocked: Date = Date()
)

typealias InventoryItemEntity = InventoryItem

data class InventoryWithProduct(
    @Embedded val inventoryItem: InventoryItem,
    @Relation(
        parentColumn = "product_id",
        entityColumn = "id"
    )
    val product: Product?
)
