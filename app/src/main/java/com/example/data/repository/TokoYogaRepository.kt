package com.example.data.repository

import com.example.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import java.util.Date

class TokoYogaRepository(
    private val dao: TokoYogaDao,
    private val productDao: ProductDao? = null,
    private val inventoryDao: InventoryItemDao? = null,
    private val transactionDao: TransactionDao? = null
) {

    constructor(database: TokoYogaDatabase) : this(
        dao = database.tokoYogaDao(),
        productDao = database.productDao(),
        inventoryDao = database.inventoryItemDao(),
        transactionDao = database.transactionDao()
    )

    // ================= PRODUK PROFIL LAMA (COMPATIBILITY) =================
    val allProduk: Flow<List<ProdukProfilEntity>> = dao.getAllProduk()
    val allTransaksi: Flow<List<TransaksiEntity>> = dao.getAllTransaksi()
    val allDetailsWithProduct: Flow<List<TransaksiDetailWithProduct>> = dao.getAllDetailsWithProductFlow()

    suspend fun checkAndSeedInitialData() = withContext(Dispatchers.IO) {
        val count = dao.getProdukCount()
        if (count == 0) {
            dao.insertAllProduk(TokoYogaDatabase.initialProfiles)
        }
        val pCount = productDao?.getProductCount() ?: 0
        if (pCount == 0 && productDao != null && inventoryDao != null) {
            val productIds = productDao.insertAllProducts(TokoYogaDatabase.initialProductEntities)
            val initialInventory = productIds.mapIndexed { index, id ->
                val prod = TokoYogaDatabase.initialProductEntities[index]
                val stock = when (index) {
                    0 -> 3 // Alert Stok Menipis
                    1 -> 4 // Alert Stok Menipis
                    2 -> 2 // Alert Stok Kritis
                    3 -> 15
                    4 -> 40
                    else -> 25 + index * 5
                }
                InventoryItem(
                    productId = id.toInt(),
                    sectionNumber = prod.sectionNumber,
                    batchNumber = "BATCH-2026-${100 + index}",
                    quantityStock = stock,
                    standardLengthMm = 6000.0,
                    minStockAlert = 5,
                    warehouseLocation = "Rak Profil ${('A' + index)}",
                    status = if (stock <= 5) InventoryStatus.LOW_STOCK else InventoryStatus.IN_STOCK,
                    lastRestocked = Date()
                )
            }
            inventoryDao.insertAllInventoryItems(initialInventory)
        }
    }

    suspend fun getProdukById(id: Int): ProdukProfilEntity? = withContext(Dispatchers.IO) {
        dao.getProdukById(id)
    }

    suspend fun insertProduk(produk: ProdukProfilEntity): Long = withContext(Dispatchers.IO) {
        dao.insertProduk(produk)
    }

    suspend fun updateProduk(produk: ProdukProfilEntity) = withContext(Dispatchers.IO) {
        dao.updateProduk(produk)
    }

    suspend fun deleteProduk(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteProdukById(id)
    }

    suspend fun simpanTransaksi(
        transaksi: TransaksiEntity,
        details: List<TransaksiDetailEntity>
    ): Long = withContext(Dispatchers.IO) {
        val trxId = dao.insertTransaksi(transaksi)
        val linkedDetails = details.map { it.copy(transaksiId = trxId.toInt()) }
        dao.insertTransaksiDetails(linkedDetails)
        trxId
    }

    suspend fun getDetailsWithProduct(transaksiId: Int): List<TransaksiDetailWithProduct> =
        withContext(Dispatchers.IO) {
            dao.getDetailsWithProduct(transaksiId)
        }

    suspend fun deleteTransaksi(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteTransaksi(id)
    }

    // ================= DRAFT ASSEMBLY =================
    val allDrafts: Flow<List<DraftAssemblyEntity>> = dao.getAllDrafts()

    suspend fun getDraftById(id: Int): DraftAssemblyEntity? = withContext(Dispatchers.IO) {
        dao.getDraftById(id)
    }

    suspend fun saveDraft(draft: DraftAssemblyEntity): Long = withContext(Dispatchers.IO) {
        dao.insertDraft(draft)
    }

    suspend fun updateDraft(draft: DraftAssemblyEntity) = withContext(Dispatchers.IO) {
        dao.updateDraft(draft)
    }

    suspend fun deleteDraft(id: Int) = withContext(Dispatchers.IO) {
        dao.deleteDraftById(id)
    }

    // ================= NEW SCHEMA: PRODUCT =================
    val productsFlow: Flow<List<Product>> = productDao?.getAllProducts() ?: emptyFlow()

    suspend fun getProductById(id: Int): Product? = withContext(Dispatchers.IO) {
        productDao?.getProductById(id)
    }

    suspend fun getProductBySectionNumber(sectionNo: String): Product? = withContext(Dispatchers.IO) {
        productDao?.getProductBySectionNumber(sectionNo)
    }

    fun getProductsByCategory(category: ProductCategory): Flow<List<Product>> {
        return productDao?.getProductsByCategory(category) ?: emptyFlow()
    }

    fun searchProducts(query: String): Flow<List<Product>> {
        return productDao?.searchProducts(query) ?: emptyFlow()
    }

    suspend fun insertProduct(product: Product): Long = withContext(Dispatchers.IO) {
        productDao?.insertProduct(product) ?: -1L
    }

    suspend fun insertAllProducts(products: List<Product>): List<Long> = withContext(Dispatchers.IO) {
        productDao?.insertAllProducts(products) ?: emptyList()
    }

    suspend fun updateProduct(product: Product) = withContext(Dispatchers.IO) {
        productDao?.updateProduct(product)
    }

    suspend fun deleteProduct(id: Int) = withContext(Dispatchers.IO) {
        productDao?.deleteProductById(id)
    }

    // ================= NEW SCHEMA: INVENTORY ITEM =================
    val inventoryItemsFlow: Flow<List<InventoryItem>> = inventoryDao?.getAllInventoryItems() ?: emptyFlow()
    val inventoryWithProductFlow: Flow<List<InventoryWithProduct>> = inventoryDao?.getInventoryWithProductFlow() ?: emptyFlow()
    val lowStockItemsFlow: Flow<List<InventoryItem>> = inventoryDao?.getLowStockInventoryItems() ?: emptyFlow()

    fun getInventoryBySectionNumber(sectionNo: String): Flow<List<InventoryItem>> {
        return inventoryDao?.getInventoryBySectionNumber(sectionNo) ?: emptyFlow()
    }

    fun searchInventoryItems(query: String): Flow<List<InventoryItem>> {
        return inventoryDao?.searchInventoryItems(query) ?: emptyFlow()
    }

    suspend fun insertInventoryItem(item: InventoryItem): Long = withContext(Dispatchers.IO) {
        inventoryDao?.insertInventoryItem(item) ?: -1L
    }

    suspend fun updateStockQuantity(id: Int, newQuantity: Int) = withContext(Dispatchers.IO) {
        inventoryDao?.updateStockQuantity(id, newQuantity)
    }

    suspend fun deleteInventoryItem(id: Int) = withContext(Dispatchers.IO) {
        inventoryDao?.deleteInventoryItemById(id)
    }

    // ================= NEW SCHEMA: TRANSACTION =================
    val transactionsFlow: Flow<List<Transaction>> = transactionDao?.getAllTransactions() ?: emptyFlow()

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> {
        return transactionDao?.getTransactionsByType(type) ?: emptyFlow()
    }

    fun getTransactionsBetweenDates(start: Date, end: Date): Flow<List<Transaction>> {
        return transactionDao?.getTransactionsBetweenDates(start, end) ?: emptyFlow()
    }

    fun searchTransactions(query: String): Flow<List<Transaction>> {
        return transactionDao?.searchTransactions(query) ?: emptyFlow()
    }

    suspend fun insertTransaction(transaction: Transaction): Long = withContext(Dispatchers.IO) {
        transactionDao?.insertTransaction(transaction) ?: -1L
    }

    suspend fun getTotalCompletedRevenue(): Long = withContext(Dispatchers.IO) {
        transactionDao?.getTotalCompletedRevenue() ?: 0L
    }
}
