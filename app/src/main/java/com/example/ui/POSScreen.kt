package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cuttingstock.CutRequest
import com.example.cuttingstock.CuttingStockUiState
import com.example.cuttingstock.CuttingStockViewModel
import com.example.cuttingstock.ReceiptCalculation
import com.example.data.local.PaymentMethod
import com.example.data.local.ProdukProfilEntity
import com.example.data.local.TransaksiEntity
import com.example.data.repository.TokoYogaRepository
import com.example.export.ReportExporter
import com.example.mesh3d.AssemblyPart
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Layar Kasir POS (Point of Sale) Toko Yoga Aluminium:
 * - Grid Layout interaktif untuk memilih profil aluminium dengan cepat
 * - Algoritma Cutting Stock Optimizer (Greedy Bin-Packing 1D) via ViewModel
 * - Kalkulasi struk belanja / receipt calculation real-time
 * - Dialog cetak struk kasir termal & integrasi ekspor nota
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSScreen(
    repository: TokoYogaRepository,
    incomingAssemblyParts: List<AssemblyPart>? = null,
    onNavigateToStudio: () -> Unit,
    onNavigateToKatalog: () -> Unit,
    onNavigateToRiwayat: () -> Unit,
    onNavigateToDashboard: (() -> Unit)? = null,
    onNavigateToCanvas: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. ViewModel Cutting Stock Optimizer & POS
    val viewModel = remember { CuttingStockViewModel(repository) }
    val uiState by viewModel.uiState.collectAsState()

    // 2. Data Master Profil dari Repository
    val produkList by repository.allProduk.collectAsState(initial = emptyList())

    // 3. Filter Grid Profil
    var profileSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }

    // 4. Input Potongan Kustom
    var inputLengthText by remember { mutableStateOf("") }
    var inputQtyText by remember { mutableStateOf("1") }
    var inputLabelText by remember { mutableStateOf("") }

    // 5. State Dialog Struk Kasir
    var showPrintedReceiptDialog by remember { mutableStateOf(false) }
    var printedReceiptData by remember { mutableStateOf<ReceiptCalculation?>(null) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }

    // Formatters
    val rupiahFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    // Otomatis pilih profil pertama jika belum ada yang terpilih
    LaunchedEffect(produkList) {
        if (uiState.selectedProfile == null && produkList.isNotEmpty()) {
            viewModel.selectProfile(produkList.first())
        }
    }

    // Jika ada kiriman dari 3D Studio, masukkan ke antrean potong
    LaunchedEffect(incomingAssemblyParts) {
        if (!incomingAssemblyParts.isNullOrEmpty()) {
            viewModel.loadFromAssemblyParts(incomingAssemblyParts)
        }
    }

    // Filter list profil untuk Grid Selector
    val filteredProfiles = remember(produkList, profileSearchQuery, selectedCategoryFilter) {
        produkList.filter { p ->
            val matchQuery = profileSearchQuery.isBlank() ||
                    p.sectionNo.contains(profileSearchQuery, ignoreCase = true) ||
                    p.kategori.contains(profileSearchQuery, ignoreCase = true) ||
                    p.warna.contains(profileSearchQuery, ignoreCase = true)

            val matchCategory = selectedCategoryFilter == "Semua" ||
                    p.kategori.equals(selectedCategoryFilter, ignoreCase = true)

            matchQuery && matchCategory
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PointOfSale,
                            contentDescription = "Kasir POS",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "Kasir POS & Optimasi Pemotongan Aluminium",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "Pilih Profil dari Grid, Hitung Efisiensi Potong, & Cetak Nota Transaksi",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Tombol Shortcut Dashboard
                    if (onNavigateToDashboard != null) {
                        IconButton(
                            onClick = onNavigateToDashboard,
                            modifier = Modifier.testTag("btn_pos_nav_dashboard")
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = "Dashboard", tint = Color(0xFFF59E0B))
                        }
                    }

                    // Tombol Shortcut 3D Studio
                    IconButton(
                        onClick = onNavigateToStudio,
                        modifier = Modifier.testTag("btn_pos_nav_studio")
                    ) {
                        Icon(Icons.Default.ViewInAr, contentDescription = "3D Studio", tint = Color(0xFF38BDF8))
                    }

                    // Tombol Shortcut Riwayat Transaksi
                    IconButton(
                        onClick = onNavigateToRiwayat,
                        modifier = Modifier.testTag("btn_pos_nav_riwayat")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = "Riwayat Nota", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        // LAYOUT DUA KOLOM RESPONSIVE LANDSCAPE:
        // KOLOM KIRI (1.3f): GRID SELECTOR PROFIL & ANTREAN POTONGAN
        // KOLOM KANAN (1.1f): STRUK KALKULASI NOTA KASIR & CHECKOUT
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B132B))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // =========================================================================
            // KOLOM 1: GRID PEMILIHAN PROFIL & BUILDER POTONGAN
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // PANEL 1: GRID SELECTOR PROFIL ALUMINIUM
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pilih Profil Aluminium", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            }

                            // Info profil aktif
                            uiState.selectedProfile?.let { sel ->
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text(
                                        "Aktif: ${sel.sectionNo} (${rupiahFormat.format(sel.hargaPerMeter)}/m)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Search Bar Cepat Profil
                        OutlinedTextField(
                            value = profileSearchQuery,
                            onValueChange = { profileSearchQuery = it },
                            placeholder = { Text("Cari kode seksi (misal: SF-001, SD, Pipa)...", fontSize = 11.sp, color = Color(0xFF64748B)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (profileSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { profileSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("pos_search_profile_input"),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(Modifier.height(6.dp))

                        // Category Chips Filter
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val categories = listOf("Semua", "Shop Front", "Sliding Door", "Casement Window", "Curtain Wall", "Commodities")
                            items(categories) { cat ->
                                val isSel = selectedCategoryFilter == cat
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedCategoryFilter = cat },
                                    label = { Text(cat, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0284C7),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.testTag("pos_cat_chip_$cat")
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // GRID LAYOUT PROFIL ALUMINIUM
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 135.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("pos_profile_grid"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredProfiles, key = { it.id }) { profil ->
                                val isSelected = uiState.selectedProfile?.id == profil.id
                                ProfileGridCard(
                                    profil = profil,
                                    isSelected = isSelected,
                                    rupiahFormat = rupiahFormat,
                                    onSelect = { viewModel.selectProfile(profil) },
                                    onQuickAddWholeBar = {
                                        viewModel.selectProfile(profil)
                                        viewModel.addCutRequest(
                                            lengthMm = 6000.0,
                                            quantity = 1,
                                            label = "1 Batang Utuh (${profil.sectionNo})"
                                        )
                                        Toast.makeText(context, "+1 Batang 6m ${profil.sectionNo}", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                // PANEL 2: BUILDER POTONGAN & PRESETS CEPAT
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header & Presets Cepat Ukuran
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Tambah Ukuran Potongan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Standar: ${uiState.standardBarLengthMm.toInt()} mm (Saw Kerf: ${uiState.sawKerfMm}mm)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }

                                // Tombol Preset Cepat (Shortcut Potongan Standar)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val presets = listOf(
                                        Triple("1 Batang (6m)", 6000.0, "Batang Utuh"),
                                        Triple("Tiang (2.1m)", 2100.0, "Tiang Kusen"),
                                        Triple("Ambang (0.9m)", 900.0, "Ambang"),
                                        Triple("Jendela (1.2m)", 1200.0, "Kusen Jendela"),
                                        Triple("Partisi (0.6m)", 600.0, "Partisi Pendek")
                                    )
                                    items(presets) { (title, len, lbl) ->
                                        FilledTonalButton(
                                            onClick = {
                                                viewModel.addCutRequest(lengthMm = len, quantity = 1, label = lbl)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = Color(0xFF0F172A),
                                                contentColor = Color(0xFF38BDF8)
                                            ),
                                            modifier = Modifier.testTag("btn_quick_preset_${len.toInt()}")
                                        ) {
                                            Text(title, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Form Input Kustom Potongan
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inputLengthText,
                                    onValueChange = { inputLengthText = it },
                                    label = { Text("Panjang (mm)", fontSize = 11.sp) },
                                    placeholder = { Text("2100") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("pos_input_cut_length")
                                )

                                OutlinedTextField(
                                    value = inputQtyText,
                                    onValueChange = { inputQtyText = it },
                                    label = { Text("Qty", fontSize = 11.sp) },
                                    placeholder = { Text("2") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(0.7f)
                                        .testTag("pos_input_cut_qty")
                                )

                                OutlinedTextField(
                                    value = inputLabelText,
                                    onValueChange = { inputLabelText = it },
                                    label = { Text("Bagian (Opsional)", fontSize = 11.sp) },
                                    placeholder = { Text("Kusen Kiri") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("pos_input_cut_label")
                                )

                                Button(
                                    onClick = {
                                        val len = inputLengthText.toDoubleOrNull()
                                        val qty = inputQtyText.toIntOrNull() ?: 1
                                        if (len == null || len <= 0.0) {
                                            Toast.makeText(context, "Masukkan panjang potong yang valid!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (len > uiState.standardBarLengthMm) {
                                            Toast.makeText(context, "Panjang melebihi 1 batang utuh (${uiState.standardBarLengthMm.toInt()} mm)!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }

                                        viewModel.addCutRequest(
                                            lengthMm = len,
                                            quantity = qty,
                                            label = inputLabelText.ifBlank { "Potongan #${uiState.requestedPieces.size + 1}" }
                                        )

                                        inputLengthText = ""
                                        inputQtyText = "1"
                                        inputLabelText = ""
                                    },
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("pos_btn_add_cut"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Tambah", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Daftar Antrean Potongan
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Antrean Potongan (${uiState.requestedPieces.sumOf { it.quantity }} potong total):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                                if (uiState.requestedPieces.isNotEmpty()) {
                                    TextButton(
                                        onClick = { viewModel.clearAllCutRequests() },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Kosongkan", fontSize = 11.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }

                        items(uiState.requestedPieces, key = { it.id }) { cut ->
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "${cut.lengthMm.toInt()} mm (${cut.label})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            "${(cut.lengthMm / 1000.0) * cut.quantity} meter total",
                                            fontSize = 10.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.updateCutQuantity(cut.id, cut.quantity - 1) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Kurang", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }

                                        Text(
                                            "${cut.quantity}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )

                                        IconButton(
                                            onClick = { viewModel.updateCutQuantity(cut.id, cut.quantity + 1) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Tambah", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                        }

                                        Spacer(Modifier.width(6.dp))

                                        IconButton(
                                            onClick = { viewModel.removeCutRequest(cut.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // VISUALISASI OPTIMASI PEMOTONGAN (GREEDY BIN-PACKING)
                        uiState.optimizationResult?.let { opt ->
                            if (opt.totalBarsNeeded > 0) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.PrecisionManufacturing, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(6.dp))
                                                    Text("Pola Pemotongan Greedy Bin-Packing", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                                }

                                                Surface(
                                                    color = if (opt.overallEfficiencyPct >= 85.0) Color(0x3334D399) else Color(0x33F59E0B),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        "Efisiensi ${String.format(Locale.US, "%.1f", opt.overallEfficiencyPct)}%",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (opt.overallEfficiencyPct >= 85.0) Color(0xFF34D399) else Color(0xFFF59E0B),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // Visual Bar Allocations
                                            opt.barAllocations.forEach { bar ->
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Batang #${bar.barNumber} (${bar.cuts.size} potong)", color = Color.White, fontSize = 10.sp)
                                                        Text("Sisa: ${bar.remainingWasteMm.toInt()} mm", color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(18.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0xFF1E293B))
                                                    ) {
                                                        val palette = listOf(Color(0xFF38BDF8), Color(0xFF34D399), Color(0xFF818CF8), Color(0xFFF472B6))
                                                        bar.cuts.forEachIndexed { i, cut ->
                                                            val w = (cut / opt.standardBarLengthMm).toFloat().coerceIn(0.01f, 1f)
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxHeight()
                                                                    .weight(w)
                                                                    .background(palette[i % palette.size])
                                                                    .border(0.5.dp, Color(0x40000000)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text("${cut.toInt()}", color = Color.Black, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }

                                                        if (bar.remainingWasteMm > 0) {
                                                            val wasteW = (bar.remainingWasteMm / opt.standardBarLengthMm).toFloat().coerceIn(0.01f, 1f)
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxHeight()
                                                                    .weight(wasteW)
                                                                    .background(Color(0xFFEF4444).copy(alpha = 0.45f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text("Sisa", color = Color.White, fontSize = 8.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // KOLOM 2: STRUK KALKULASI NOTA KASIR & CHECKOUT REGISTER
            // =========================================================================
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
            ) {
                val receipt = uiState.receiptCalculation

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Struk Kasir Toko
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "TOKO YOGA ALUMINIUM",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "Spesialis Kusen, Jendela, & Pintu Aluminium",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                "No. Nota: ${receipt?.invoiceNumber ?: "-"}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Divider(color = Color(0xFF334155), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    // Input Nama Pelanggan
                    item {
                        OutlinedTextField(
                            value = uiState.customerName,
                            onValueChange = { viewModel.setCustomerName(it) },
                            label = { Text("Nama Pelanggan / Proyek", fontSize = 11.sp) },
                            placeholder = { Text("Bpk. Hendra / Proyek Ruko") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pos_customer_name_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Tabel Rincian Item Potongan Struk
                    item {
                        Text("RINCIAN BARANG & PEMOTONGAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    }

                    if (receipt == null || receipt.items.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Belum ada potongan profil yang ditambahkan", color = Color(0xFF64748B), fontSize = 12.sp)
                            }
                        }
                    } else {
                        items(receipt.items) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${item.sectionNumber} - ${item.name}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "${item.quantity} pcs @ ${item.cutLengthMm.toInt()} mm (${String.format(Locale.US, "%.2f", item.totalMeters)} m) x ${rupiahFormat.format(item.unitPricePerMeter)}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Text(
                                    rupiahFormat.format(item.subtotal),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }

                    item {
                        Divider(color = Color(0xFF334155), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    // Ringkasan Bahan Batang Utuh & Metrik
                    item {
                        receipt?.let { rec ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Bahan Batang Utuh (6m):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Text("${rec.totalBarsNeeded} Batang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Efisiensi Potong:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    Text("${String.format(Locale.US, "%.1f", rec.cuttingEfficiencyPct)}% (Sisa: ${rec.cuttingWasteMm.toInt()} mm)", fontSize = 11.sp, color = Color(0xFF34D399))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Subtotal Bahan:", fontSize = 12.sp, color = Color.White)
                                    Text(rupiahFormat.format(rec.materialSubtotal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    // Pajak PPN & Grand Total
                    item {
                        receipt?.let { rec ->
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("TOTAL TAGIHAN", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFFF59E0B))
                                        Text(
                                            rupiahFormat.format(rec.grandTotal),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF34D399)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Metode Pembayaran Chips
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Metode Pembayaran:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PaymentMethod.values().forEach { method ->
                                    val isSel = uiState.paymentMethod == method
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { viewModel.setPaymentMethod(method) },
                                        label = { Text(method.displayName.substringBefore(" /"), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF34D399),
                                            selectedLabelColor = Color.Black
                                        ),
                                        modifier = Modifier.testTag("pos_payment_chip_${method.name}")
                                    )
                                }
                            }
                        }
                    }

                    // Kalkulator Uang Diterima & Kembalian (Jika Tunai / Cash)
                    if (uiState.paymentMethod == PaymentMethod.CASH && receipt != null) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Uang Tunai Diterima (Rp):", fontSize = 11.sp, color = Color(0xFF94A3B8))

                                OutlinedTextField(
                                    value = if (uiState.cashReceived > 0L) uiState.cashReceived.toString() else "",
                                    onValueChange = { str ->
                                        viewModel.setCashReceived(str.filter { it.isDigit() }.toLongOrNull() ?: 0L)
                                    },
                                    placeholder = { Text(rupiahFormat.format(receipt.grandTotal)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pos_cash_received_input")
                                )

                                // Shortcut Uang Cepat (Pas, 100rb, 200rb, 500rb, 1jt)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val shortcuts = listOf(
                                        "Uang Pas" to receipt.grandTotal,
                                        "50.000" to 50000L,
                                        "100.000" to 100000L,
                                        "200.000" to 200000L,
                                        "500.000" to 500000L,
                                        "1.000.000" to 1000000L
                                    )
                                    items(shortcuts) { (label, amount) ->
                                        FilledTonalButton(
                                            onClick = { viewModel.setCashReceived(amount) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                containerColor = Color(0xFF0F172A),
                                                contentColor = Color(0xFFF59E0B)
                                            )
                                        ) {
                                            Text(label, fontSize = 9.sp)
                                        }
                                    }
                                }

                                // Baris Kembalian
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Kembalian:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            rupiahFormat.format(receipt.changeAmount),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (receipt.changeAmount >= 0) Color(0xFF34D399) else Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TOMBOL SIMPAN TRANSAKSI & CETAK STRUK KASIR
                    item {
                        Button(
                            onClick = {
                                viewModel.saveTransaction(
                                    onSuccess = { trxId ->
                                        printedReceiptData = receipt
                                        showPrintedReceiptDialog = true
                                        showSuccessSnackbar = true
                                        Toast.makeText(context, "Transaksi #TRX-$trxId berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !uiState.isSavingTransaction && uiState.requestedPieces.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_simpan_transaksi"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (uiState.isSavingTransaction) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Simpan & Cetak Struk Kasir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOG: STRUK KASIR TERMAL RESMI (PRINTED THERMAL RECEIPT)
    // =========================================================================
    if (showPrintedReceiptDialog && printedReceiptData != null) {
        val receipt = printedReceiptData!!

        Dialog(
            onDismissRequest = { showPrintedReceiptDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .width(420.dp)
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White, // Tampilan Kertas Struk Kasir
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Struk Kasir
                    Text("TOKO YOGA ALUMINIUM", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                    Text("Jl. Industri Raya No. 88, Denpasar - Bali", fontSize = 11.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                    Text("Telp / WA: 0812-3456-7890", fontSize = 11.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                    Text("========================================", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    // Meta Data Struk
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("No. Nota   : ${receipt.invoiceNumber}", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                        Text("Tanggal    : ${dateFormat.format(receipt.timestamp)}", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                        Text("Pelanggan  : ${receipt.customerName}", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                        Text("Kasir      : Offline POS", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                    }

                    Text("----------------------------------------", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    // Daftar Item
                    receipt.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.sectionNumber} ${item.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                                Text("${item.quantity} x ${item.cutLengthMm.toInt()}mm @ ${rupiahFormat.format(item.unitPricePerMeter)}/m", fontSize = 10.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                            }
                            Text(rupiahFormat.format(item.subtotal), fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Text("----------------------------------------", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    // Ringkasan Optimasi
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bahan Batang Utuh (6m):", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                            Text("${receipt.totalBarsNeeded} Batang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efisiensi Pemotongan  :", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                            Text("${String.format(Locale.US, "%.1f", receipt.cuttingEfficiencyPct)}%", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sisa Limbah (Scrap)   :", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                            Text("${receipt.cuttingWasteMm.toInt()} mm", fontSize = 11.sp, color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Text("========================================", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    // Total & Bayar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TOTAL BELANJA :", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                            Text(rupiahFormat.format(receipt.grandTotal), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Bayar (${receipt.paymentMethod.displayName.substringBefore(" /")}):", fontSize = 11.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                            Text(rupiahFormat.format(receipt.cashReceived), fontSize = 11.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("KEMBALIAN     :", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                            Text(rupiahFormat.format(receipt.changeAmount), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black, fontFamily = FontFamily.Monospace)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("** TERIMA KASIH ATAS KUNJUNGAN ANDA **", fontSize = 10.sp, color = Color.DarkGray, fontFamily = FontFamily.Monospace)
                    Text("Barang yang sudah dipotong tidak dapat ditukar", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)

                    Spacer(Modifier.height(14.dp))

                    // Tombol Aksi Struk
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                    val trxEntity = TransaksiEntity(id = 0, tanggal = nowStr, totalHarga = receipt.grandTotal)
                                    val details = repository.getDetailsWithProduct(0)
                                    val file = ReportExporter.exportTransaksiToSpreadsheet(context, trxEntity, details)
                                    if (file != null) ReportExporter.shareReportFile(context, file)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bagikan Excel", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { showPrintedReceiptDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Text("Selesai", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Kartu item profil pada Grid Selector POS
 */
@Composable
private fun ProfileGridCard(
    profil: ProdukProfilEntity,
    isSelected: Boolean,
    rupiahFormat: NumberFormat,
    onSelect: () -> Unit,
    onQuickAddWholeBar: () -> Unit
) {
    Surface(
        color = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF0F172A),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("profile_grid_item_${profil.sectionNo}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        profil.sectionNo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Terpilih",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            profil.kategori,
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            profil.warna,
                            fontSize = 9.sp,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    "${rupiahFormat.format(profil.hargaPerMeter)} / m",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )

                Text(
                    "6m: ${rupiahFormat.format(profil.hargaPerMeter * 6)}",
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Tombol Cepat Tambah 1 Batang 6m
            Button(
                onClick = onQuickAddWholeBar,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .testTag("btn_grid_add_6m_${profil.id}"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFF0284C7) else Color(0xFF334155),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("+1 Batang (6m)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
