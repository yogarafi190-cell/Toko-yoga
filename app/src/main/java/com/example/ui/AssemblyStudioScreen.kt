package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.core.content.FileProvider
import com.example.data.local.DraftAssemblyEntity
import com.example.data.repository.TokoYogaRepository
import com.example.mesh3d.AssemblyBounds
import com.example.mesh3d.AssemblyEngine
import com.example.mesh3d.AssemblyPart
import com.example.mesh3d.AssemblySerializer
import com.example.mesh3d.Compose3DAssemblyRenderer
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Menu Assembling Parts into One (Perakitan Komponen Menjadi Satu Kesatuan):
 * - Memilih dan merakit banyak batang ekstrusi profil aluminium menjadi satu kesatuan unit konstruksi.
 * - Mengatur sambungan sudut (90° Butt Joint atau 45° Miter Cut Adu Manis).
 * - Visualisasi 3D Interaktif dengan Exploded View Slider (0% Rapat Assembled <-> 100% Terurai).
 * - Menghitung dimensi total (Bounding Box: W x H x D), berat estimasi, dan daftar potong (BOM).
 * - Transfer unit rakitan ke 3D Studio, Kasir POS (Cutting Stock Optimizer), atau Ekspor 3D .OBJ.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssemblyStudioScreen(
    repository: TokoYogaRepository? = null,
    onNavigateToStudio: ((List<AssemblyPart>) -> Unit)? = null,
    onNavigateToPOS: ((List<AssemblyPart>) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Daftar komponen dalam workbench perakitan
    val assemblyParts = remember {
        mutableStateListOf<AssemblyPart>().apply {
            addAll(AssemblyEngine.createKusenJendelaPreset(800f, 1200f))
        }
    }

    // Set ID part yang dipilih untuk digabungkan (default: semua part terpilih)
    val selectedPartIds = remember {
        mutableStateListOf<String>().apply {
            addAll(assemblyParts.map { it.id })
        }
    }

    // Nama Unit Rakitan
    var unitNameInput by remember { mutableStateOf("Unit Kusen Jendela 80x120") }
    var selectedJointType by remember { mutableStateOf("45_miter") } // "45_miter" atau "90_butt"

    // Status Assembled
    val isAssembled = remember(assemblyParts.toList()) {
        val groups = assemblyParts.mapNotNull { it.groupId }.distinct()
        groups.isNotEmpty() && assemblyParts.all { it.groupId == groups.first() }
    }
    val currentGroupName = remember(assemblyParts.toList()) {
        assemblyParts.firstOrNull { it.groupName != null }?.groupName
    }

    // Exploded View Slider (0.0 = Rapat Sempurna / Assembled, 1.0 = Ledakan Terurai Maksimal)
    var explodedFactor by remember { mutableFloatStateOf(0.0f) }

    // Hitung Mesh 3D dengan exploded factor
    val sceneMesh = remember(assemblyParts.toList(), explodedFactor) {
        AssemblyEngine.buildAssemblyMesh(assemblyParts.toList(), explodedFactor = explodedFactor)
    }

    // Hitung Batas Dimensi & Spesifikasi Rakitan
    val bounds: AssemblyBounds = remember(assemblyParts.toList()) {
        AssemblyEngine.calculateAssemblyBounds(assemblyParts.toList())
    }

    // Dialog state
    var showAddPartDialog by remember { mutableStateOf(false) }
    var showBOMDialog by remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var exportedObjPath by remember { mutableStateOf<String?>(null) }

    // Seluruh layout landscape responsif: Kolom Kontrol Kiri + Area 3D Viewport Kanan
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // =====================================================================
        // PANEL KONTROL KIRI (340.dp) - Presets, Seleksi Batang, & Aksi Assembling
        // =====================================================================
        Surface(
            modifier = Modifier
                .width(340.dp)
                .fillMaxHeight(),
            color = Color(0xFF131B2A),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Top Header Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onNavigateBack != null) {
                            IconButton(
                                onClick = { onNavigateBack() },
                                modifier = Modifier.size(32.dp).testTag("btn_back_assembly")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        Column {
                            Text(
                                "Menu Assembling Parts",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            Text(
                                "Perakitan Komponen Menjadi 1 Unit",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Status Chip Assembled vs Loose
                    Surface(
                        color = if (isAssembled) Color(0xFF059669) else Color(0xFFD97706),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isAssembled) "TERGABUNG" else "TERURAI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Presets Cepat Unit Rangka Konstruksi
                Text(
                    "PILIH PRESET UNIT RANGKA:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = {
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createKusenJendelaPreset(800f, 1200f))
                            selectedPartIds.clear()
                            selectedPartIds.addAll(assemblyParts.map { it.id })
                            unitNameInput = "Kusen Jendela 80x120"
                            explodedFactor = 0f
                        },
                        label = { Text("Jendela", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_jendela"),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                    AssistChip(
                        onClick = {
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createKusenPintuPreset(900f, 2100f))
                            selectedPartIds.clear()
                            selectedPartIds.addAll(assemblyParts.map { it.id })
                            unitNameInput = "Kusen Pintu 90x210"
                            explodedFactor = 0f
                        },
                        label = { Text("Pintu", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_pintu"),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                    AssistChip(
                        onClick = {
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createPartisiKacaPreset(1200f, 2400f))
                            selectedPartIds.clear()
                            selectedPartIds.addAll(assemblyParts.map { it.id })
                            unitNameInput = "Partisi Kaca 120x240"
                            explodedFactor = 0f
                        },
                        label = { Text("Partisi", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_partisi"),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = {
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createEtalaseAluminiumPreset(1200f, 500f, 900f))
                            selectedPartIds.clear()
                            selectedPartIds.addAll(assemblyParts.map { it.id })
                            unitNameInput = "Etalase Aluminium 120x90"
                            explodedFactor = 0f
                        },
                        label = { Text("Etalase", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_etalase"),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                    AssistChip(
                        onClick = {
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createRangkaMejaPreset(1000f, 600f, 750f))
                            selectedPartIds.clear()
                            selectedPartIds.addAll(assemblyParts.map { it.id })
                            unitNameInput = "Rangka Meja 100x60"
                            explodedFactor = 0f
                        },
                        label = { Text("Meja/Rak", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f).testTag("preset_meja"),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                    AssistChip(
                        onClick = { showAddPartDialog = true },
                        label = { Text("+ Custom", fontSize = 10.5.sp) },
                        modifier = Modifier.weight(1f).testTag("btn_tambah_custom_part"),
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = Color(0xFFFBBF24),
                            containerColor = Color(0xFF1E293B)
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                // KARTU AKSI UTAMA: GABUNGKAN MENJADI SATU KESATUAN (ASSEMBLE INTO ONE)
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isAssembled) Color(0xFF059669) else Color(0xFFD97706)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ASSEMBLY CONFIGURATION",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAssembled) Color(0xFF34D399) else Color(0xFFFBBF24)
                            )
                            if (isAssembled) {
                                TextButton(
                                    onClick = {
                                        val firstGroup = assemblyParts.firstOrNull { it.groupId != null }?.groupId
                                        if (firstGroup != null) {
                                            val disassembled = AssemblyEngine.disassembleGroup(assemblyParts.toList(), firstGroup)
                                            assemblyParts.clear()
                                            assemblyParts.addAll(disassembled)
                                            Toast.makeText(context, "Unit berhasil dipisahkan kembali!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.height(24.dp).testTag("btn_ungroup_assembly")
                                ) {
                                    Icon(Icons.Default.CallSplit, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Pisahkan", fontSize = 10.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Nama Unit Rakitan
                        OutlinedTextField(
                            value = unitNameInput,
                            onValueChange = { unitNameInput = it },
                            label = { Text("Nama Unit Jadi", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_unit_assembly_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF475569),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )

                        Spacer(Modifier.height(6.dp))

                        // Sambungan Sudut (Joint Type)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedJointType == "45_miter",
                                onClick = { selectedJointType = "45_miter" },
                                label = { Text("45° Adu Manis", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedJointType == "90_butt",
                                onClick = { selectedJointType = "90_butt" },
                                label = { Text("90° Butt Joint", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Tombol Utama: Gabungkan Menjadi Satu Kesatuan
                        Button(
                            onClick = {
                                if (selectedPartIds.isEmpty()) {
                                    Toast.makeText(context, "Pilih minimal 1 bagian untuk digabung", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val updated = AssemblyEngine.assemblePartsIntoOne(
                                    parts = assemblyParts.toList(),
                                    unitName = unitNameInput,
                                    targetIds = selectedPartIds.toSet(),
                                    jointType = selectedJointType
                                )
                                assemblyParts.clear()
                                assemblyParts.addAll(updated)
                                explodedFactor = 0f // Langsung rapatkan unit yang baru dirakit
                                Toast.makeText(context, "✅ Berhasil merakit ${selectedPartIds.size} bagian menjadi 1 Unit!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_assemble_into_one"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAssembled) Color(0xFF047857) else Color(0xFFD97706)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isAssembled) "Perbarui Unit Rakitan" else "Gabung Menjadi 1 Unit",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Header Daftar Bagian yang Dirakit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "BAGIAN RANGKA (${selectedPartIds.size}/${assemblyParts.size})",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Row {
                        TextButton(
                            onClick = {
                                selectedPartIds.clear()
                                selectedPartIds.addAll(assemblyParts.map { it.id })
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text("Pilih Semua", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Daftar Komponen Rangka
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(assemblyParts, key = { it.id }) { part ->
                        val isChecked = selectedPartIds.contains(part.id)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isChecked) 1.dp else 0.5.dp,
                                    color = if (isChecked) Color(0xFF38BDF8) else Color(0xFF1E293B),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    if (isChecked) selectedPartIds.remove(part.id) else selectedPartIds.add(part.id)
                                },
                            color = if (isChecked) Color(0xFF1A263D) else Color(0xFF0F172A)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedPartIds.add(part.id) else selectedPartIds.remove(part.id)
                                        },
                                        modifier = Modifier.size(20.dp),
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color(0xFF38BDF8),
                                            uncheckedColor = Color.Gray
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            part.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.5.sp,
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            "${part.lengthMm.toInt()} mm • ${part.profileType} • ${part.color}",
                                            fontSize = 9.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                // Hapus Part dari Workbench
                                IconButton(
                                    onClick = {
                                        assemblyParts.remove(part)
                                        selectedPartIds.remove(part.id)
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bottom Buttons: Simpan Draft & Lembar Kerja BOM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { showSaveDraftDialog = true },
                        modifier = Modifier.weight(1f).height(34.dp).testTag("btn_simpan_draft_assembly"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Simpan Draft", fontSize = 10.5.sp)
                    }

                    OutlinedButton(
                        onClick = { showBOMDialog = true },
                        modifier = Modifier.weight(1f).height(34.dp).testTag("btn_lihat_bom_assembly"),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Daftar Potong (BOM)", fontSize = 10.5.sp)
                    }
                }
            }
        }

        // =====================================================================
        // AREA KANAN: 3D VIEWPORT LUAS + EXPLODED VIEW HUD + TRANSFER BAR
        // =====================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. KANVAS 3D VIEWPORT LUAS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Compose3DAssemblyRenderer(
                    scene = sceneMesh,
                    selectedPartId = null,
                    modifier = Modifier.fillMaxSize()
                )

                // FLOATING EXPLODED VIEW CONTROLLER (Pojok Kiri Atas Viewport)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .widthIn(max = 280.dp),
                    color = Color(0xDD0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "EXPLODED VIEW (URAIAN)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                "${(explodedFactor * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (explodedFactor == 0f) Color(0xFF34D399) else Color(0xFFF59E0B)
                            )
                        }

                        Slider(
                            value = explodedFactor,
                            onValueChange = { explodedFactor = it },
                            valueRange = 0f..1.5f,
                            modifier = Modifier.height(28.dp).testTag("slider_exploded_view"),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF38BDF8),
                                activeTrackColor = Color(0xFF0284C7)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { explodedFactor = 0f },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(20.dp).testTag("btn_rapatkan_unit")
                            ) {
                                Text("Rapatkan (0%)", fontSize = 10.sp, color = Color(0xFF34D399))
                            }
                            TextButton(
                                onClick = { explodedFactor = 1.0f },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(20.dp).testTag("btn_ledakkan_unit")
                            ) {
                                Text("Uraikan (100%)", fontSize = 10.sp, color = Color(0xFFF59E0B))
                            }
                        }
                    }
                }

                // Tombol Ekspor OBJ di pojok kanan atas
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val path = AssemblyEngine.exportAssemblyToObj(
                                context = context,
                                scene = sceneMesh,
                                assemblyName = unitNameInput
                            )
                            if (path.isNotBlank()) {
                                exportedObjPath = path
                                Toast.makeText(context, "Berkas .obj unit '$unitNameInput' tersimpan!", Toast.LENGTH_LONG).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_export_unit_obj")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ekspor .OBJ", fontSize = 11.sp)
                    }

                    if (exportedObjPath != null) {
                        FilledTonalButton(
                            onClick = {
                                try {
                                    val file = File(exportedObjPath!!)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/octet-stream"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Bagikan Model 3D .OBJ"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bagikan", fontSize = 11.sp)
                        }
                    }
                }
            }

            // 2. KARTU SPESIFIKASI & TRANSFER OUTPUT UNIT RAKITAN
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF131B2A),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ringkasan Dimensi & Material
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SquareFoot, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Dimensi Unit: ${bounds.widthMm.toInt()} x ${bounds.heightMm.toInt()} x ${bounds.depthMm.toInt()} mm",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Total Bahan: ${(bounds.totalMaterialLengthMm / 1000.0).format(2)} meter • ${bounds.totalPieces} batang • Berat: ~${bounds.estimatedWeightKg.format(1)} kg",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Tombol Transfer Cepat
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Transfer ke Kasir POS & Cutting Stock Optimizer
                        Button(
                            onClick = {
                                if (onNavigateToPOS != null) {
                                    onNavigateToPOS(assemblyParts.toList())
                                } else {
                                    Toast.makeText(context, "Membuka kasir untuk unit $unitNameInput...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_transfer_assembly_to_pos")
                        ) {
                            Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Kirim ke Kasir & Potong", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        // Transfer ke 3D Studio
                        Button(
                            onClick = {
                                if (onNavigateToStudio != null) {
                                    onNavigateToStudio(assemblyParts.toList())
                                } else {
                                    Toast.makeText(context, "Membuka unit di 3D Studio...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_transfer_assembly_to_studio")
                        ) {
                            Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buka di 3D Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOG: TAMBAH BATANG KUSTOM KE WORKBENCH
    // =========================================================================
    if (showAddPartDialog) {
        var customName by remember { mutableStateOf("Pengaku Tengah") }
        var customLength by remember { mutableStateOf("800") }
        var customProfileType by remember { mutableStateOf("Hollow Box") }
        var customColor by remember { mutableStateOf("Silver") }

        Dialog(onDismissRequest = { showAddPartDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tambah Batang Profil ke Rakitan", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Nama Komponen") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customLength,
                        onValueChange = { customLength = it },
                        label = { Text("Panjang Batang (mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddPartDialog = false }) {
                            Text("Batal", color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val len = customLength.toFloatOrNull() ?: 800f
                                val newPart = AssemblyPart(
                                    name = customName,
                                    profileType = customProfileType,
                                    lengthMm = len,
                                    widthMm = 40f,
                                    heightMm = 40f,
                                    thicknessMm = 1.35f,
                                    posX = 0f,
                                    posY = len / 2f,
                                    posZ = 0f,
                                    color = customColor
                                )
                                assemblyParts.add(newPart)
                                selectedPartIds.add(newPart.id)
                                showAddPartDialog = false
                                Toast.makeText(context, "$customName ditambahkan ke workbench!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("Tambahkan")
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOG: DAFTAR POTONG & LEMBAR KERJA (BOM)
    // =========================================================================
    if (showBOMDialog) {
        Dialog(onDismissRequest = { showBOMDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Lembar Kerja Perakitan (BOM)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            Text("Unit: $unitNameInput • ${bounds.totalPieces} Potongan", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                        IconButton(onClick = { showBOMDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.Gray)
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(assemblyParts) { part ->
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(part.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                                        Text("${part.profileType} • Warna: ${part.color}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${part.lengthMm.toInt()} mm", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFF59E0B))
                                        Text("Potong ${if (part.jointType == "45_miter") "45° Adu Manis" else "90° Rata"}", fontSize = 9.sp, color = Color(0xFF38BDF8))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { showBOMDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                    ) {
                        Text("Tutup Lembar Kerja")
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODAL DIALOG: SIMPAN KE DRAFT PROYEK
    // =========================================================================
    if (showSaveDraftDialog) {
        Dialog(onDismissRequest = { showSaveDraftDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Simpan Unit Perakitan ke Database", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = unitNameInput,
                        onValueChange = { unitNameInput = it },
                        label = { Text("Nama Unit / Judul Proyek") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSaveDraftDialog = false }) {
                            Text("Batal", color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val json = AssemblySerializer.partsToJson(assemblyParts.toList())
                                    val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                                    val draft = DraftAssemblyEntity(
                                        title = unitNameInput,
                                        updatedAt = now,
                                        totalParts = assemblyParts.size,
                                        totalLengthMm = bounds.totalMaterialLengthMm,
                                        partsJson = json
                                    )
                                    repository?.saveDraft(draft)
                                    showSaveDraftDialog = false
                                    Toast.makeText(context, "Unit '$unitNameInput' tersimpan di Draft!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Simpan Sekarang")
                        }
                    }
                }
            }
        }
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)
