package com.example.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProdukProfilEntity
import com.example.data.repository.TokoYogaRepository
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KatalogScreen(
    repository: TokoYogaRepository,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Int) -> Unit,
    onNavigateTo3D: (String, Double, String) -> Unit,
    onNavigateToCanvas: (() -> Unit)? = null,
    onNavigateToDashboard: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val allProduk by repository.allProduk.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedKategori by remember { mutableStateOf("Semua") }
    var itemToDelete by remember { mutableStateOf<ProdukProfilEntity?>(null) }

    val kategoriFilters = listOf(
        "Semua",
        "Shop Front",
        "Sliding Door",
        "Casement Window",
        "Curtain Wall",
        "Commodities"
    )

    val rupiahFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    val filteredList = remember(allProduk, searchQuery, selectedKategori) {
        allProduk.filter { p ->
            val matchQuery = searchQuery.isBlank() ||
                    p.sectionNo.contains(searchQuery, ignoreCase = true) ||
                    p.kategori.contains(searchQuery, ignoreCase = true) ||
                    p.warna.contains(searchQuery, ignoreCase = true)

            val matchCategory = selectedKategori == "Semua" || p.kategori.equals(selectedKategori, ignoreCase = true)

            matchQuery && matchCategory
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_tambah_profil")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Profil Baru")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Inventaris Profil Aluminium",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Total ${allProduk.size} spesifikasi ekstrusi terdaftar",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onNavigateToDashboard != null) {
                        FilledTonalButton(
                            onClick = onNavigateToDashboard,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFFF59E0B)
                            ),
                            modifier = Modifier.testTag("btn_katalog_open_dashboard")
                        ) {
                            Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Dashboard Stok", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (onNavigateToCanvas != null) {
                        FilledTonalButton(
                            onClick = onNavigateToCanvas,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1E293B),
                                contentColor = Color(0xFF38BDF8)
                            ),
                            modifier = Modifier.testTag("btn_katalog_open_canvas")
                        ) {
                            Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gambar Profil 2D", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Search Bar untuk filter berdasarkan Section Number atau Kategori Produk
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari Section No atau Kategori (misal: SF-301, Sliding)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus pencarian")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_search_katalog"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

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
                            modifier = Modifier.testTag("chip_filter_$cat")
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

            // Status hasil pencarian
            Text(
                text = "Ditemukan ${filteredList.size} dari ${allProduk.size} profil aluminium",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            // List Profil
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Architecture,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isNotEmpty() || selectedKategori != "Semua")
                                "Tidak ada profil yang cocok dengan kriteria pencarian"
                            else
                                "Tidak ada profil aluminium yang cocok",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        if (searchQuery.isNotEmpty() || selectedKategori != "Semua") {
                            Text(
                                "Coba ubah kata kunci Section No atau pilih kategori lain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedKategori = "Semua"
                                }
                            ) {
                                Text("Hapus Filter & Tampilkan Semua")
                            }
                        } else {
                            Text(
                                "Ketuk tombol '+' untuk menambahkan profil baru.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        val chipColor = when (item.warna.lowercase()) {
                            "black" -> Color(0xFF1E1E1E)
                            "brown" -> Color(0xFF5D4037)
                            "putih" -> Color(0xFFF1F5F9)
                            else -> Color(0xFFB0BEC5)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("card_profil_${item.id}"),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.sectionNo,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            item.kategori,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }

                                    // Harga per Meter Badge
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "${rupiahFormat.format(item.hargaPerMeter)}/m",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.height(10.dp))

                                // Badges Row: Thickness & Warna
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "Tebal: ${item.thickness} mm",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFFE2E8F0),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(chipColor)
                                                    .border(0.5.dp, Color.Gray, CircleShape)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(item.warna, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(Modifier.height(8.dp))

                                // Aksi: 3D View, Edit, Hapus
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            onNavigateTo3D(item.kategori, item.thickness, item.warna)
                                        },
                                        modifier = Modifier.testTag("btn_view_3d_${item.id}")
                                    ) {
                                        Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Lihat 3D")
                                    }

                                    TextButton(
                                        onClick = { onNavigateToEdit(item.id) },
                                        modifier = Modifier.testTag("btn_edit_${item.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Edit")
                                    }

                                    IconButton(
                                        onClick = { itemToDelete = item },
                                        modifier = Modifier.testTag("btn_delete_${item.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Konfirmasi Hapus
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus Profil Aluminium?") },
            text = { Text("Apakah Anda yakin ingin menghapus '${target.sectionNo}' dari database SQLite lokal?") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteProduk(target.id)
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}
