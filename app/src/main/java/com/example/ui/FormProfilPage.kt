package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ProdukProfilEntity
import com.example.data.repository.TokoYogaRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormProfilPage(
    repository: TokoYogaRepository,
    produkId: Int? = null, // null untuk Tambah Baru, angka untuk Edit
    onNavigateBack: () -> Unit,
    onSuccessSaved: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isEditMode = produkId != null

    var sectionNo by remember { mutableStateOf("") }
    var kategori by remember { mutableStateOf("Shop Front") }
    var thicknessText by remember { mutableStateOf("") }
    var hargaText by remember { mutableStateOf("") }
    var warna by remember { mutableStateOf("Silver") }

    // Error states
    var sectionNoError by remember { mutableStateOf<String?>(null) }
    var thicknessError by remember { mutableStateOf<String?>(null) }
    var hargaError by remember { mutableStateOf<String?>(null) }

    var isSubmitting by remember { mutableStateOf(false) }
    var isLoadingExisting by remember { mutableStateOf(isEditMode) }
    var existingFile3dPath by remember { mutableStateOf("") }

    val kategoriList = listOf(
        "Shop Front",
        "Sliding Door",
        "Casement Window",
        "Curtain Wall",
        "Commodities"
    )

    val warnaList = listOf(
        "Silver",
        "Black",
        "Brown",
        "Putih"
    )

    // Load data lama jika dalam mode Edit
    LaunchedEffect(produkId) {
        if (produkId != null) {
            val existing = repository.getProdukById(produkId)
            if (existing != null) {
                sectionNo = existing.sectionNo
                kategori = existing.kategori
                thicknessText = existing.thickness.toString()
                hargaText = existing.hargaPerMeter.toString()
                warna = existing.warna
                existingFile3dPath = existing.file3dPath
            }
            isLoadingExisting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEditMode) "Edit Profil Aluminium" else "Tambah Profil Baru",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_form")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        if (isLoadingExisting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Info Card Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isEditMode) Icons.Default.EditNote else Icons.Default.AddBox,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (isEditMode) "Mode Edit Profil #$produkId" else "Spesifikasi Ekstrusi Baru",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Database SQLite lokal Toko Yoga akan diperbarui secara instan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 1. TextField Section No
                OutlinedTextField(
                    value = sectionNo,
                    onValueChange = {
                        sectionNo = it
                        if (it.isNotBlank()) sectionNoError = null
                    },
                    label = { Text("Section No / Kode Profil *") },
                    placeholder = { Text("Contoh: SF-001, SD-9055, Pipa 40x40") },
                    leadingIcon = { Icon(Icons.Default.Tag, contentDescription = null) },
                    isError = sectionNoError != null,
                    supportingText = {
                        if (sectionNoError != null) {
                            Text(sectionNoError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_section_no"),
                    singleLine = true
                )

                // 2. Dropdown Kategori
                var expandedKategori by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedKategori,
                    onExpandedChange = { expandedKategori = !expandedKategori }
                ) {
                    OutlinedTextField(
                        value = kategori,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Kategori Profil *") },
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKategori) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("dropdown_kategori")
                    )
                    ExposedDropdownMenu(
                        expanded = expandedKategori,
                        onDismissRequest = { expandedKategori = false }
                    ) {
                        kategoriList.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    kategori = item
                                    expandedKategori = false
                                }
                            )
                        }
                    }
                }

                // 3. TextField Thickness (mm)
                OutlinedTextField(
                    value = thicknessText,
                    onValueChange = {
                        thicknessText = it
                        if (it.isNotBlank()) thicknessError = null
                    },
                    label = { Text("Thickness / Ketebalan Dinding (mm) *") },
                    placeholder = { Text("1.25") },
                    leadingIcon = { Icon(Icons.Default.LineWeight, contentDescription = null) },
                    suffix = { Text("mm") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = thicknessError != null,
                    supportingText = {
                        if (thicknessError != null) {
                            Text(thicknessError!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Ketebalan aluminium hollow (umumnya 1.0 - 2.5 mm)")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_thickness"),
                    singleLine = true
                )

                // 4. TextField Harga per Meter
                OutlinedTextField(
                    value = hargaText,
                    onValueChange = {
                        hargaText = it
                        if (it.isNotBlank()) hargaError = null
                    },
                    label = { Text("Harga per Meter (Rp) *") },
                    placeholder = { Text("48000") },
                    leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                    prefix = { Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = hargaError != null,
                    supportingText = {
                        if (hargaError != null) {
                            Text(hargaError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_harga"),
                    singleLine = true
                )

                // 5. Pilihan Warna
                Text(
                    "Pilihan Warna Anodized / Powder Coating *",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    warnaList.forEach { colorName ->
                        val isSelected = warna.equals(colorName, ignoreCase = true)
                        val chipColor = when (colorName.lowercase()) {
                            "black" -> Color(0xFF1E1E1E)
                            "brown" -> Color(0xFF5D4037)
                            "putih" -> Color(0xFFF1F5F9)
                            else -> Color(0xFFB0BEC5)
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = { warna = colorName },
                            label = { Text(colorName) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(chipColor)
                                        .border(1.dp, Color.Gray, CircleShape)
                                )
                            },
                            modifier = Modifier.testTag("chip_warna_$colorName")
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tombol Simpan
                Button(
                    onClick = {
                        // Validasi ketat
                        var valid = true

                        if (sectionNo.trim().isEmpty()) {
                            sectionNoError = "Section No wajib diisi!"
                            valid = false
                        } else if (sectionNo.trim().length < 2) {
                            sectionNoError = "Minimal 2 karakter"
                            valid = false
                        }

                        val parsedThickness = thicknessText.replace(',', '.').toDoubleOrNull()
                        if (parsedThickness == null || parsedThickness <= 0) {
                            thicknessError = "Masukkan ketebalan angka valid (> 0 mm)"
                            valid = false
                        } else if (parsedThickness > 20.0) {
                            thicknessError = "Ketebalan maksimal 20 mm"
                            valid = false
                        }

                        val parsedHarga = hargaText.filter { it.isDigit() }.toLongOrNull()
                        if (parsedHarga == null || parsedHarga <= 0) {
                            hargaError = "Harga wajib diisi dan lebih dari Rp 0"
                            valid = false
                        }

                        if (!valid) return@Button

                        isSubmitting = true
                        coroutineScope.launch {
                            val entity = ProdukProfilEntity(
                                id = produkId ?: 0,
                                sectionNo = sectionNo.trim(),
                                kategori = kategori,
                                thickness = parsedThickness!!,
                                hargaPerMeter = parsedHarga!!,
                                warna = warna,
                                file3dPath = existingFile3dPath
                            )

                            if (isEditMode) {
                                repository.updateProduk(entity)
                            } else {
                                repository.insertProduk(entity)
                            }
                            isSubmitting = false
                            onSuccessSaved()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_simpan_profil"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            if (isEditMode) Icons.Default.Save else Icons.Default.AddCircle,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isEditMode) "Simpan Perubahan (Update)" else "Simpan ke Database SQLite",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
