package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.*
import com.example.data.repository.TokoYogaRepository
import com.example.mesh3d.Extrusion3DViewer
import com.example.mesh3d.Point2D
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pilihan urutan tampilan stok inventaris.
 */
enum class InventorySortOption(val label: String) {
    LOWEST_STOCK("Stok Terendah (Prioritas)"),
    HIGHEST_STOCK("Stok Tertinggi"),
    SECTION_AZ("Nomor Seksi (A-Z)"),
    HIGHEST_VALUE("Nilai Aset Terbesar")
}

/**
 * Layar Dashboard Inventaris Toko Yoga:
 * - Menampilkan metrik ringkasan stok & nilai aset
 * - Alert peringatan stok menipis secara interaktif
 * - Filter pencarian cepat (search bar) berdasarkan nomor seksi, kategori, rak gudang, & batch
 * - Pengelolaan penyesuaian stok langsung (restock, tambah/kurang stok)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDashboardScreen(
    repository: TokoYogaRepository,
    onNavigateToCanvas: (() -> Unit)? = null,
    onNavigateTo3DStudio: ((String) -> Unit)? = null,
    onNavigateToKatalog: (() -> Unit)? = null,
    onNavigateToPOS: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Data Inventaris dari Room Database
    val inventoryWithProductList by repository.inventoryWithProductFlow.collectAsState(initial = emptyList())
    val lowStockList by repository.lowStockItemsFlow.collectAsState(initial = emptyList())
    val allProducts by repository.productsFlow.collectAsState(initial = emptyList())

    // 2. State Pencarian & Filter
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var filterLowStockOnly by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(InventorySortOption.LOWEST_STOCK) }

    // 3. State Dialog / Modal
    var itemToRestock by remember { mutableStateOf<InventoryWithProduct?>(null) }
    var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }
    var showAddNewItemDialog by remember { mutableStateOf(false) }
    var preview3DItem by remember { mutableStateOf<InventoryWithProduct?>(null) }

    // Formatters
    val rupiahFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }

    // Metrik Agregat
    val totalUniqueProfiles = inventoryWithProductList.size
    val totalStockBars = remember(inventoryWithProductList) {
        inventoryWithProductList.sumOf { it.inventoryItem.quantityStock }
    }
    val totalInventoryValue = remember(inventoryWithProductList) {
        inventoryWithProductList.sumOf { item ->
            val pricePerMeter = item.product?.unitPrice ?: 0L
            val standardLenMeters = (item.inventoryItem.standardLengthMm / 1000.0)
            (item.inventoryItem.quantityStock * standardLenMeters * pricePerMeter).toLong()
        }
    }
    val criticalAlertCount = remember(inventoryWithProductList) {
        inventoryWithProductList.count { it.inventoryItem.quantityStock <= it.inventoryItem.minStockAlert }
    }

    // Filter & Sort Inventaris
    val filteredItems = remember(inventoryWithProductList, searchQuery, selectedCategoryFilter, filterLowStockOnly, sortOption) {
        var items = inventoryWithProductList.filter { item ->
            val inv = item.inventoryItem
            val prod = item.product

            val matchQuery = searchQuery.isBlank() ||
                    inv.sectionNumber.contains(searchQuery, ignoreCase = true) ||
                    (prod?.name?.contains(searchQuery, ignoreCase = true) ?: false) ||
                    inv.warehouseLocation.contains(searchQuery, ignoreCase = true) ||
                    inv.batchNumber.contains(searchQuery, ignoreCase = true) ||
                    (prod?.color?.contains(searchQuery, ignoreCase = true) ?: false)

            val matchCategory = selectedCategoryFilter == "Semua" ||
                    (prod?.category?.displayName?.equals(selectedCategoryFilter, ignoreCase = true) ?: false)

            val matchLowStock = !filterLowStockOnly || (inv.quantityStock <= inv.minStockAlert)

            matchQuery && matchCategory && matchLowStock
        }

        when (sortOption) {
            InventorySortOption.LOWEST_STOCK -> items.sortedBy { it.inventoryItem.quantityStock }
            InventorySortOption.HIGHEST_STOCK -> items.sortedByDescending { it.inventoryItem.quantityStock }
            InventorySortOption.SECTION_AZ -> items.sortedBy { it.inventoryItem.sectionNumber }
            InventorySortOption.HIGHEST_VALUE -> items.sortedByDescending { item ->
                val price = item.product?.unitPrice ?: 0L
                (item.inventoryItem.quantityStock * 6 * price)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = "Dashboard Icon",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "Dashboard Inventaris Profil Aluminium",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Manajemen Stok, Peringatan Kritis, dan Nilai Aset Gudang",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Tombol Tambah Barang Masuk
                    Button(
                        onClick = { showAddNewItemDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_add_inventory_item"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah Stok", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Shortcut Buka Kasir POS
                    if (onNavigateToPOS != null) {
                        IconButton(
                            onClick = onNavigateToPOS,
                            modifier = Modifier.testTag("btn_nav_to_pos")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = "Kasir POS", tint = Color(0xFF34D399))
                        }
                    }

                    // Shortcut Buka Gambar Profil 2D
                    if (onNavigateToCanvas != null) {
                        IconButton(
                            onClick = onNavigateToCanvas,
                            modifier = Modifier.testTag("btn_nav_to_canvas")
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = "Profil 2D", tint = Color(0xFF38BDF8))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B132B)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // =========================================================================
            // 1. KARTU STATISTIK RINGKASAN METRIK GUDANG
            // =========================================================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Kartu 1: Total Profil
                    StatSummaryCard(
                        title = "Item Profil Unik",
                        value = "$totalUniqueProfiles Jenis",
                        subtitle = "${allProducts.size} Terdaftar di Master",
                        icon = Icons.Default.Category,
                        accentColor = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f),
                        testTag = "stat_total_profiles"
                    )

                    // Kartu 2: Total Batang Stok
                    StatSummaryCard(
                        title = "Total Stok Fisik",
                        value = "$totalStockBars Batang",
                        subtitle = "Standar 6 Meter per Batang",
                        icon = Icons.Default.Inventory,
                        accentColor = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                        testTag = "stat_total_stock"
                    )

                    // Kartu 3: Peringatan Stok Rendah
                    StatSummaryCard(
                        title = "Stok Menipis (Alert)",
                        value = "$criticalAlertCount Profil",
                        subtitle = if (criticalAlertCount > 0) "Perlu Segera Restock!" else "Semua Stok Aman",
                        icon = Icons.Default.Warning,
                        accentColor = if (criticalAlertCount > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        testTag = "stat_low_stock_count"
                    )

                    // Kartu 4: Estimasi Nilai Valuasi Aset
                    StatSummaryCard(
                        title = "Valuasi Aset Inventaris",
                        value = rupiahFormat.format(totalInventoryValue),
                        subtitle = "Berdasarkan Harga / Meter",
                        icon = Icons.Default.MonetizationOn,
                        accentColor = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1.2f),
                        testTag = "stat_inventory_valuation"
                    )
                }
            }

            // =========================================================================
            // 2. BANNER & DAFTAR PERINGATAN STOK RENDAH (LOW-STOCK ALERTS)
            // =========================================================================
            if (criticalAlertCount > 0) {
                item {
                    LowStockAlertBanner(
                        lowStockItems = inventoryWithProductList.filter {
                            it.inventoryItem.quantityStock <= it.inventoryItem.minStockAlert
                        },
                        onQuickRestock = { item -> itemToRestock = item },
                        onFilterLowStockToggled = {
                            filterLowStockOnly = !filterLowStockOnly
                        },
                        isFiltered = filterLowStockOnly
                    )
                }
            }

            // =========================================================================
            // 3. KONTROL PENCARIAN & FILTER CEPAT (SEARCH & FILTER BAR)
            // =========================================================================
            item {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Bar Input
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Cari nomor seksi, nama profil, lokasi rak gudang, atau batch...",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Cari", tint = Color(0xFF38BDF8))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = Color(0xFF94A3B8))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_search_bar"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // Baris Filter Chips (Kategori & Alert Toggle)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Filter Kategori
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val categories = listOf("Semua", "Shop Front", "Sliding Door", "Casement Window", "Curtain Wall", "Commodities")
                                items(categories) { cat ->
                                    val isSelected = selectedCategoryFilter == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCategoryFilter = cat },
                                        label = { Text(cat, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF0284C7),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0xFF0F172A),
                                            labelColor = Color(0xFF94A3B8)
                                        ),
                                        modifier = Modifier.testTag("filter_chip_$cat")
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            // Toggle Khusus Hanya Stok Menipis
                            FilterChip(
                                selected = filterLowStockOnly,
                                onClick = { filterLowStockOnly = !filterLowStockOnly },
                                label = { Text("⚠️ Stok Menipis Saja", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFDC2626),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color(0xFFEF4444)
                                ),
                                modifier = Modifier.testTag("chip_filter_low_stock")
                            )
                        }

                        // Baris Pengurutan (Sorting)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Menampilkan ${filteredItems.size} dari $totalUniqueProfiles item profil",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Urutkan: ", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Spacer(Modifier.width(6.dp))
                                InventorySortOption.values().forEach { opt ->
                                    val isSel = sortOption == opt
                                    Surface(
                                        color = if (isSel) Color(0xFF334155) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .clickable { sortOption = opt }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            opt.label.substringBefore(" ("),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) Color(0xFF38BDF8) else Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 4. DAFTAR ITEM INVENTARIS FISIK (INVENTORY CARDS)
            // =========================================================================
            if (filteredItems.isEmpty()) {
                item {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Tidak ada item profil yang cocok dengan pencarian",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Coba ubah kata kunci atau hapus filter kategori di atas.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedCategoryFilter = "Semua"
                                    filterLowStockOnly = false
                                }
                            ) {
                                Text("Reset Semua Filter")
                            }
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.inventoryItem.id }) { itemWithProduct ->
                    val inv = itemWithProduct.inventoryItem
                    val prod = itemWithProduct.product

                    InventoryItemCard(
                        inventoryItem = inv,
                        product = prod,
                        rupiahFormat = rupiahFormat,
                        dateFormat = dateFormat,
                        onRestockClicked = { itemToRestock = itemWithProduct },
                        onIncrementStock = {
                            coroutineScope.launch {
                                repository.updateStockQuantity(inv.id, inv.quantityStock + 1)
                            }
                        },
                        onDecrementStock = {
                            if (inv.quantityStock > 0) {
                                coroutineScope.launch {
                                    repository.updateStockQuantity(inv.id, inv.quantityStock - 1)
                                }
                            }
                        },
                        onView3DClicked = {
                            preview3DItem = itemWithProduct
                        },
                        onDeleteClicked = {
                            itemToDelete = inv
                        }
                    )
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOGS
    // =========================================================================

    // 1. DIALOG QUICK RESTOCK
    if (itemToRestock != null) {
        val target = itemToRestock!!
        var addStockInput by remember { mutableStateOf("10") }
        var batchInput by remember { mutableStateOf(target.inventoryItem.batchNumber) }
        var locationInput by remember { mutableStateOf(target.inventoryItem.warehouseLocation) }

        AlertDialog(
            onDismissRequest = { itemToRestock = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddBusiness, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(Modifier.width(8.dp))
                    Text("Restock Barang Masuk", color = Color.White)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${target.inventoryItem.sectionNumber} • ${target.product?.name ?: ""}",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Stok saat ini: ${target.inventoryItem.quantityStock} batang (Batas minimum alert: ${target.inventoryItem.minStockAlert})",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = addStockInput,
                        onValueChange = { addStockInput = it },
                        label = { Text("Jumlah Batang Masuk") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tombol Quick Add Chips (+5, +10, +25, +50)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 20, 50).forEach { qty ->
                            FilledTonalButton(
                                onClick = { addStockInput = qty.toString() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("+$qty", fontSize = 11.sp)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = batchInput,
                        onValueChange = { batchInput = it },
                        label = { Text("Nomor Batch Pengiriman") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = locationInput,
                        onValueChange = { locationInput = it },
                        label = { Text("Lokasi Rak Gudang") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val added = addStockInput.toIntOrNull() ?: 0
                        if (added > 0) {
                            coroutineScope.launch {
                                val newQty = target.inventoryItem.quantityStock + added
                                repository.updateStockQuantity(target.inventoryItem.id, newQty)
                                Toast.makeText(context, "Stok ${target.inventoryItem.sectionNumber} bertambah $added batang!", Toast.LENGTH_SHORT).show()
                                itemToRestock = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Konfirmasi Masuk", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRestock = null }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // 2. DIALOG TAMBAH BARANG INVENTARIS BARU
    if (showAddNewItemDialog) {
        var inputSection by remember { mutableStateOf("") }
        var inputBatch by remember { mutableStateOf("BATCH-${System.currentTimeMillis() % 10000}") }
        var inputStock by remember { mutableStateOf("20") }
        var inputMinAlert by remember { mutableStateOf("5") }
        var inputLocation by remember { mutableStateOf("Rak Profil F") }
        var selectedProductId by remember { mutableStateOf(allProducts.firstOrNull()?.id ?: 1) }

        AlertDialog(
            onDismissRequest = { showAddNewItemDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddBox, contentDescription = null, tint = Color(0xFFD97706))
                    Spacer(Modifier.width(8.dp))
                    Text("Daftarkan Stok Profil Baru", color = Color.White)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputSection,
                        onValueChange = { inputSection = it },
                        label = { Text("Nomor Seksi (e.g. SF-002, Hollow 40x40)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputStock,
                        onValueChange = { inputStock = it },
                        label = { Text("Jumlah Stok Awal (Batang)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputMinAlert,
                        onValueChange = { inputMinAlert = it },
                        label = { Text("Batas Minimum Alert (Batang)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputLocation,
                        onValueChange = { inputLocation = it },
                        label = { Text("Lokasi Rak / Gudang") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputBatch,
                        onValueChange = { inputBatch = it },
                        label = { Text("Nomor Batch") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val stockInt = inputStock.toIntOrNull() ?: 10
                        val minInt = inputMinAlert.toIntOrNull() ?: 5
                        val cleanSection = inputSection.ifBlank { "PROFIL-CUSTOM-${System.currentTimeMillis() % 1000}" }

                        coroutineScope.launch {
                            val newItem = InventoryItem(
                                productId = selectedProductId,
                                sectionNumber = cleanSection,
                                batchNumber = inputBatch,
                                quantityStock = stockInt,
                                standardLengthMm = 6000.0,
                                minStockAlert = minInt,
                                warehouseLocation = inputLocation,
                                status = if (stockInt <= minInt) InventoryStatus.LOW_STOCK else InventoryStatus.IN_STOCK,
                                lastRestocked = Date()
                            )
                            repository.insertInventoryItem(newItem)
                            showAddNewItemDialog = false
                            Toast.makeText(context, "Item $cleanSection berhasil ditambahkan ke inventaris!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Simpan ke Gudang", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNewItemDialog = false }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // 3. DIALOG KONFIRMASI HAPUS
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Item Inventaris?", color = Color.White) },
            text = {
                Text(
                    "Apakah Anda yakin ingin menghapus catatan stok untuk ${target.sectionNumber}? Tindakan ini tidak dapat dibatalkan.",
                    color = Color(0xFF94A3B8)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteInventoryItem(target.id)
                            itemToDelete = null
                            Toast.makeText(context, "Item ${target.sectionNumber} telah dihapus", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // 4. DIALOG PREVIEW 3D OPENGL PROFIL DARI DASHBOARD
    if (preview3DItem != null) {
        val item = preview3DItem!!
        val sampleContour = remember(item) {
            // Kontur 2D geometris representatif berdasarkan profil
            listOf(
                Point2D(0f, 0f),
                Point2D(50f, 0f),
                Point2D(50f, 40f),
                Point2D(0f, 40f)
            )
        }

        Dialog(
            onDismissRequest = { preview3DItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "3D Mesh Preview: ${item.inventoryItem.sectionNumber}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        IconButton(onClick = { preview3DItem = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                        }
                    }

                    Extrusion3DViewer(
                        contour = sampleContour,
                        initialThicknessMm = item.product?.thickness?.toFloat() ?: 1.25f,
                        initialLengthMm = 1000f,
                        isClosed = true,
                        initialWarna = item.product?.color ?: "Silver",
                        sectionName = item.inventoryItem.sectionNumber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Kartu Ringkasan Metrik Statistik Gudang
 */
@Composable
private fun StatSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(2.dp))

            Text(
                subtitle,
                fontSize = 10.sp,
                color = accentColor
            )
        }
    }
}

/**
 * Banner Peringatan Stok Menipis (Low-Stock Alerts)
 */
@Composable
private fun LowStockAlertBanner(
    lowStockItems: List<InventoryWithProduct>,
    onQuickRestock: (InventoryWithProduct) -> Unit,
    onFilterLowStockToggled: () -> Unit,
    isFiltered: Boolean
) {
    Surface(
        color = Color(0xFF451A03), // Deep Amber Dark
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alert_banner_low_stock")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "PERINGATAN: ${lowStockItems.size} Profil Mencapai Batas Minimum Stok!",
                            color = Color(0xFFFDE68A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Segera lakukan pemesanan restock agar tidak mengganggu pesanan perakitan.",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp
                        )
                    }
                }

                Button(
                    onClick = onFilterLowStockToggled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFiltered) Color(0xFFF59E0B) else Color(0x33F59E0B),
                        contentColor = if (isFiltered) Color.Black else Color(0xFFFDE68A)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(if (isFiltered) "Tampilkan Semua" else "Filter List", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Carousel Card Low Stock Items
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lowStockItems) { item ->
                    val inv = item.inventoryItem
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(240.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    inv.sectionNumber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    "Stok: ${inv.quantityStock} Batang (Min: ${inv.minStockAlert})",
                                    fontSize = 11.sp,
                                    color = if (inv.quantityStock <= 2) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    inv.warehouseLocation,
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Button(
                                onClick = { onQuickRestock(item) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                modifier = Modifier.testTag("btn_quick_restock_${inv.id}")
                            ) {
                                Text("Restock", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartu Satuan Item Inventaris
 */
@Composable
private fun InventoryItemCard(
    inventoryItem: InventoryItem,
    product: Product?,
    rupiahFormat: NumberFormat,
    dateFormat: SimpleDateFormat,
    onRestockClicked: () -> Unit,
    onIncrementStock: () -> Unit,
    onDecrementStock: () -> Unit,
    onView3DClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    val isLowStock = inventoryItem.quantityStock <= inventoryItem.minStockAlert
    val pricePerMeter = product?.unitPrice ?: 0L
    val itemAssetValue = (inventoryItem.quantityStock * 6 * pricePerMeter)

    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(12.dp),
        border = if (isLowStock) androidx.compose.foundation.BorderStroke(1.dp, Color(0x80EF4444)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_inventory_item_${inventoryItem.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Baris 1: Nomor Seksi, Kategori Chip, & Action Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        inventoryItem.sectionNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))

                    // Badge Kategori
                    val catName = product?.category?.displayName ?: "Standar"
                    Surface(
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            catName,
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Badge Finishing Warna
                    if (product?.color != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                product.color,
                                fontSize = 10.sp,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Status Badge Stok
                Surface(
                    color = when {
                        inventoryItem.quantityStock <= 0 -> Color(0x33EF4444)
                        isLowStock -> Color(0x33F59E0B)
                        else -> Color(0x3310B981)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        inventoryItem.quantityStock <= 0 -> Color(0xFFEF4444)
                                        isLowStock -> Color(0xFFF59E0B)
                                        else -> Color(0xFF10B981)
                                    }
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                inventoryItem.quantityStock <= 0 -> "HABIS"
                                isLowStock -> "STOK MENIPIS"
                                else -> "TERSEDIA"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                inventoryItem.quantityStock <= 0 -> Color(0xFFEF4444)
                                isLowStock -> Color(0xFFF59E0B)
                                else -> Color(0xFF10B981)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Nama Deskripsi Produk
            if (product != null) {
                Text(
                    product.name,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
                Spacer(Modifier.height(8.dp))
            }

            Divider(color = Color(0xFF334155), thickness = 0.5.dp)

            Spacer(Modifier.height(10.dp))

            // Baris 2: Info Lokasi, Batch, & Kontrol Jumlah Stok Fisik
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Teknis
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lokasi: ${inventoryItem.warehouseLocation}", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Batch: ${inventoryItem.batchNumber} • Restock: ${dateFormat.format(inventoryItem.lastRestocked)}", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Nilai Aset: ${rupiahFormat.format(itemAssetValue)} (${rupiahFormat.format(pricePerMeter)}/m)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF59E0B)
                    )
                }

                // Kontrol Counter Stok (+ / -) & Tombol Restock
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tombol Kurang Stok (-)
                    IconButton(
                        onClick = onDecrementStock,
                        enabled = inventoryItem.quantityStock > 0,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_stock_decrement_${inventoryItem.id}")
                    ) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Kurang 1", tint = if (inventoryItem.quantityStock > 0) Color(0xFFEF4444) else Color(0xFF475569))
                    }

                    // Angka Stok
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "${inventoryItem.quantityStock}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLowStock) Color(0xFFF59E0B) else Color.White
                        )
                        Text("Batang", fontSize = 9.sp, color = Color(0xFF94A3B8))
                    }

                    // Tombol Tambah Stok (+)
                    IconButton(
                        onClick = onIncrementStock,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_stock_increment_${inventoryItem.id}")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Tambah 1", tint = Color(0xFF34D399))
                    }

                    Spacer(Modifier.width(8.dp))

                    // Tombol Restock Modal
                    FilledTonalButton(
                        onClick = onRestockClicked,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        modifier = Modifier.testTag("btn_restock_modal_${inventoryItem.id}")
                    ) {
                        Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restock", fontSize = 11.sp)
                    }

                    Spacer(Modifier.width(4.dp))

                    // Tombol Preview 3D
                    IconButton(
                        onClick = onView3DClicked,
                        modifier = Modifier.size(32.dp).testTag("btn_view_3d_${inventoryItem.id}")
                    ) {
                        Icon(Icons.Default.ViewInAr, contentDescription = "Preview 3D", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }

                    // Tombol Hapus
                    IconButton(
                        onClick = onDeleteClicked,
                        modifier = Modifier.size(32.dp).testTag("btn_delete_inv_${inventoryItem.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
