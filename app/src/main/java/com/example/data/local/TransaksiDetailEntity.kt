package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaksi_detail",
    foreignKeys = [
        ForeignKey(
            entity = TransaksiEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaksi_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProdukProfilEntity::class,
            parentColumns = ["id"],
            childColumns = ["produk_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("transaksi_id"),
        Index("produk_id")
    ]
)
data class TransaksiDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "transaksi_id")
    val transaksiId: Int,
    
    @ColumnInfo(name = "produk_id")
    val produkId: Int,
    
    @ColumnInfo(name = "panjang_diminta")
    val panjangDiminta: Double, // dalam mm atau meter
    
    @ColumnInfo(name = "jumlah_batang")
    val jumlahBatang: Int,
    
    @ColumnInfo(name = "subtotal")
    val subtotal: Long
)
