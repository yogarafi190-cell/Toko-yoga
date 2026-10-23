package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "draft_assembly")
data class DraftAssemblyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String,

    @ColumnInfo(name = "total_parts")
    val totalParts: Int,

    @ColumnInfo(name = "total_length_mm")
    val totalLengthMm: Double,

    @ColumnInfo(name = "parts_json")
    val partsJson: String
)
