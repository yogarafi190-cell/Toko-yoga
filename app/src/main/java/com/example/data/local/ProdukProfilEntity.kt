package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "produk_profil")
data class ProdukProfilEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    @ColumnInfo(name = "section_no")
    val sectionNo: String,
    
    @ColumnInfo(name = "kategori")
    val kategori: String, // Shop Front, Sliding Door, Casement Window, Curtain Wall, Commodities
    
    @ColumnInfo(name = "thickness")
    val thickness: Double, // Ketebalan dinding profil (mm)
    
    @ColumnInfo(name = "harga_per_meter")
    val hargaPerMeter: Long, // Harga rupiah per meter
    
    @ColumnInfo(name = "warna")
    val warna: String, // Silver, Black, Brown, Putih
    
    @ColumnInfo(name = "file_3d_path")
    val file3dPath: String = "" // Path lokasi file .obj lokal di HP
)
