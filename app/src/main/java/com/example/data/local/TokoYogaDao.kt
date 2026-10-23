package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Data class gabungan untuk riwayat detail transaksi
data class TransaksiDetailWithProduct(
    @Embedded val detail: TransaksiDetailEntity,
    @Relation(
        parentColumn = "produk_id",
        entityColumn = "id"
    )
    val produk: ProdukProfilEntity?
)

@Dao
interface TokoYogaDao {

    // ================= CRUD PRODUK PROFIL =================
    @Query("SELECT * FROM produk_profil ORDER BY id DESC")
    fun getAllProduk(): Flow<List<ProdukProfilEntity>>

    @Query("SELECT * FROM produk_profil WHERE id = :id LIMIT 1")
    suspend fun getProdukById(id: Int): ProdukProfilEntity?

    @Query("SELECT * FROM produk_profil WHERE kategori = :kategori ORDER BY section_no ASC")
    fun getProdukByKategori(kategori: String): Flow<List<ProdukProfilEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduk(produk: ProdukProfilEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllProduk(produkList: List<ProdukProfilEntity>)

    @Update
    suspend fun updateProduk(produk: ProdukProfilEntity)

    @Delete
    suspend fun deleteProduk(produk: ProdukProfilEntity)

    @Query("DELETE FROM produk_profil WHERE id = :id")
    suspend fun deleteProdukById(id: Int)

    @Query("SELECT COUNT(*) FROM produk_profil")
    suspend fun getProdukCount(): Int


    // ================= TRANSAKSI & DETAIL =================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaksi(transaksi: TransaksiEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaksiDetails(details: List<TransaksiDetailEntity>)

    @Query("SELECT * FROM transaksi ORDER BY id DESC")
    fun getAllTransaksi(): Flow<List<TransaksiEntity>>

    @Query("SELECT * FROM transaksi WHERE id = :id LIMIT 1")
    suspend fun getTransaksiById(id: Int): TransaksiEntity?

    @androidx.room.Transaction
    @Query("SELECT * FROM transaksi_detail WHERE transaksi_id = :transaksiId")
    suspend fun getDetailsWithProduct(transaksiId: Int): List<TransaksiDetailWithProduct>

    @androidx.room.Transaction
    @Query("SELECT * FROM transaksi_detail")
    fun getAllDetailsWithProductFlow(): Flow<List<TransaksiDetailWithProduct>>

    @Query("DELETE FROM transaksi WHERE id = :id")
    suspend fun deleteTransaksi(id: Int)

    // ================= DRAFT ASSEMBLY 3D =================
    @Query("SELECT * FROM draft_assembly ORDER BY id DESC")
    fun getAllDrafts(): Flow<List<DraftAssemblyEntity>>

    @Query("SELECT * FROM draft_assembly WHERE id = :id LIMIT 1")
    suspend fun getDraftById(id: Int): DraftAssemblyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: DraftAssemblyEntity): Long

    @Update
    suspend fun updateDraft(draft: DraftAssemblyEntity)

    @Query("DELETE FROM draft_assembly WHERE id = :id")
    suspend fun deleteDraftById(id: Int)
}
