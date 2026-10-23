package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "section_number")
    val sectionNumber: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category")
    val category: ProductCategory = ProductCategory.SHOP_FRONT,

    @ColumnInfo(name = "thickness")
    val thickness: Double = 1.25, // mm

    @ColumnInfo(name = "unit_price")
    val unitPrice: Long = 0L, // Rp / meter

    @ColumnInfo(name = "color")
    val color: String = "Silver",

    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "file_3d_path")
    val file3dPath: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date()
)

typealias ProductEntity = Product
