package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransaksiDetailWithProduct
import com.example.data.local.TransaksiEntity
import com.example.data.repository.TokoYogaRepository
import com.example.export.ReportExporter
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RiwayatScreen(
    repository: TokoYogaRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allTransaksi by repository.allTransaksi.collectAsState(initial = emptyList())
    val allDetailsWithProduct by repository.allDetailsWithProduct.collectAsState(initial = emptyList())

    val rupiahFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    // State pencarian & filter kategori
    var searchQuery by remember { mutableStateOf("") }
    var selectedKategori by remember { mutableStateOf("Semua") }

    val kategoriFilters = listOf(
        "Semua",
        "Shop Front",
        "Sliding Door",
        "Casement Window",
        "Curtain Wall",
        "Commodities"
    )

    // Pemetaan detail transaksi berdasarkan ID transaksi
    val detailsGroupedByTrx = remember(allDetailsWithProduct) {
        allDetailsWithProduct.groupBy { it.detail.transaksiId }
    }

    var selectedTrxForDetails by remember { mutableStateOf<Int?>(null) }
    var loadedDetails by remember { mutableStateOf<List<TransaksiDetailWithProduct>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(false) }

    val totalPendapatan = remember(allTransaksi) {
        allTransaksi.sumOf { it.totalHarga }
    }

    // Filter transaksi berdasarkan Section Number atau Kategori Produk
    val filteredTransaksi = remember(allTransaksi, detailsGroupedByTrx, searchQuery, selectedKategori) {
        allTransaksi.filter { trx ->
            val details = detailsGroupedByTrx[trx.id] ?: emptyList()
            val sectionNumbers = details.mapNotNull { it.produk?.sectionNo }
            val categories = details.mapNotNull { it.produk?.kategori }
            val trxIdStr = trx.id.toString()
            val trxCode = "TRX-${trx.id}"

            val matchQuery = searchQuery.isBlank() ||
                    sectionNumbers.any { it.contains(searchQuery, ignoreCase = true) } ||
                    categories.any { it.contains(searchQuery, ignoreCase = true) } ||
                    trxIdStr.contains(searchQuery, ignoreCase = true) ||
                    trxCode.contains(searchQuery, ignoreCase = true) ||
                    trx.tanggal.contains(searchQuery, ignoreCase = true)

            val matchKategori = selectedKategori == "Semua" ||
                    categories.any { it.equals(selectedKategori, ignoreCase = true) }

            matchQuery && matchKategori
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header & Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Penjualan Kasir POS", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Text(
                        rupiahFormat.format(totalPendapatan),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "${allTransaksi.size} Nota",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Search Bar untuk filter berdasarkan Section Number atau Kategori Produk
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari Section No atau Kategori pada transaksi (misal: SF-301, Sliding)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Transaksi") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_search_transaksi"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        // Filter Chips Bar (Kategori Produk)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(kategoriFilters) { cat ->
                    val isSelected = selectedKategori == cat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedKategori = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        modifier = Modifier.testTag("chip_filter_trx_$cat")
                    )
                }
            }

            if (searchQuery.isNotEmpty() || selectedKategori != "Semua") {
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        searchQuery = ""
                        selectedKategori = "Semua"
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp)
                }
            }
        }

        // Info Jumlah Transaksi Terfilter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Riwayat Nota Transaksi (SQLite Offline):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Ditemukan ${filteredTransaksi.size} dari ${allTransaksi.size} nota",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(6.dp))

        if (allTransaksi.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Belum ada riwayat transaksi tersimpan", color = Color.Gray)
                    Text(
                        "Lakukan pesanan di Kasir POS untuk mencatat nota.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else if (filteredTransaksi.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tidak ada nota transaksi yang cocok",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tidak ada transaksi yang memuat Section No / Kategori \"${if (searchQuery.isNotEmpty()) searchQuery else selectedKategori}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = {
                            searchQuery = ""
                            selectedKategori = "Semua"
                        }
                    ) {
                        Text("Reset Filter & Tampilkan Semua Nota")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredTransaksi, key = { it.id }) { trx ->
                    val isExpanded = selectedTrxForDetails == trx.id
                    val trxDetails = detailsGroupedByTrx[trx.id] ?: emptyList()
                    val distinctProducts = remember(trxDetails) {
                        trxDetails.mapNotNull { it.produk }.distinctBy { it.sectionNo }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_transaksi_${trx.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            "Nota #TRX-${trx.id}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            trx.tanggal,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Text(
                                    rupiahFormat.format(trx.totalHarga),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontSize = 15.sp
                                )
                            }

                            // Ringkasan Section No & Kategori dalam Nota ini
                            if (distinctProducts.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(distinctProducts) { prod ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Sell,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "${prod.sectionNo} (${prod.kategori})",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(Modifier.height(8.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (isExpanded) {
                                            selectedTrxForDetails = null
                                        } else {
                                            selectedTrxForDetails = trx.id
                                            if (trxDetails.isNotEmpty()) {
                                                loadedDetails = trxDetails
                                            } else {
                                                isLoadingDetails = true
                                                coroutineScope.launch {
                                                    loadedDetails = repository.getDetailsWithProduct(trx.id)
                                                    isLoadingDetails = false
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isExpanded) "Tutup Rincian" else "Lihat Rincian")
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                val details = repository.getDetailsWithProduct(trx.id)
                                                val file = ReportExporter.exportTransaksiToSpreadsheet(context, trx, details)
                                                if (file != null) {
                                                    ReportExporter.shareReportFile(context, file)
                                                } else {
                                                    Toast.makeText(context, "Gagal membuat berkas Excel", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.testTag("btn_export_excel_${trx.id}"),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Excel / WA", fontSize = 12.sp)
                                    }

                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.deleteTransaksi(trx.id)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Hapus Nota",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Rincian Accordion
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isLoadingDetails) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally))
                                    } else {
                                        loadedDetails.forEachIndexed { i, d ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            "${i + 1}. ${d.produk?.sectionNo ?: "Profil"} • ${d.produk?.kategori ?: "-"} (${d.produk?.warna ?: "-"})",
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            "Potongan: ${d.detail.panjangDiminta.toInt()} mm × ${d.detail.jumlahBatang} batang",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    Text(
                                                        rupiahFormat.format(d.detail.subtotal),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
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
