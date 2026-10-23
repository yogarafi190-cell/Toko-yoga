package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaksi")
data class TransaksiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "tanggal")
    val tanggal: String, // format: YYYY-MM-DD HH:mm
    
    @ColumnInfo(name = "total_harga")
    val totalHarga: Long
)
