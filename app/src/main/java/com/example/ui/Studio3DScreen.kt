package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.core.content.FileProvider
import com.example.mesh3d.AssemblyEngine
import com.example.mesh3d.AssemblyPart
import com.example.mesh3d.Compose3DAssemblyRenderer
import java.io.File
import java.util.UUID

import com.example.data.local.DraftAssemblyEntity
import com.example.data.repository.TokoYogaRepository
import com.example.mesh3d.AssemblyHistoryManager
import com.example.mesh3d.AssemblySnapshot
import com.example.mesh3d.AssemblySerializer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Studio3DScreen(
    repository: TokoYogaRepository? = null,
    onSendToPOS: ((List<AssemblyPart>) -> Unit)? = null,
    incomingCustomPart: AssemblyPart? = null,
    onCustomPartHandled: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Daftar komponen perakitan (Multi-Object Assembly)
    val assemblyParts = remember {
        mutableStateListOf<AssemblyPart>().apply {
            // Default preset: Kusen Jendela (4 batang)
            addAll(AssemblyEngine.createKusenJendelaPreset(lebarMm = 800f, tinggiMm = 1200f))
        }
    }

    LaunchedEffect(incomingCustomPart) {
        if (incomingCustomPart != null) {
            assemblyParts.add(incomingCustomPart)
            Toast.makeText(context, "Profil kustom ${incomingCustomPart.name} ditambahkan ke Studio 3D!", Toast.LENGTH_SHORT).show()
            onCustomPartHandled?.invoke()
        }
    }

    // State Draft Proyek
    val drafts by repository?.allDrafts?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    var currentDraftId by remember { mutableStateOf<Int?>(null) }
    var currentDraftTitle by remember { mutableStateOf<String?>(null) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var showLoadDraftDialog by remember { mutableStateOf(false) }

    var selectedPartId by remember {
        mutableStateOf(assemblyParts.firstOrNull()?.id)
    }

    // =========================================================================
    // SISTEM HISTORY STACK (UNDO / REDO)
    // =========================================================================
    val historyManager = remember { AssemblyHistoryManager(maxHistorySize = 50) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var lastUndoLabel by remember { mutableStateOf<String?>(null) }
    var lastRedoLabel by remember { mutableStateOf<String?>(null) }
    var undoCount by remember { mutableIntStateOf(0) }
    var redoCount by remember { mutableIntStateOf(0) }

    fun updateHistoryState() {
        canUndo = historyManager.canUndo
        canRedo = historyManager.canRedo
        lastUndoLabel = historyManager.peekUndoLabel()
        lastRedoLabel = historyManager.peekRedoLabel()
        undoCount = historyManager.undoCount
        redoCount = historyManager.redoCount
    }

    fun recordAction(label: String) {
        historyManager.push(assemblyParts.toList(), selectedPartId, label)
        updateHistoryState()
    }

    fun performUndo() {
        val previous = historyManager.undo(assemblyParts.toList(), selectedPartId)
        if (previous != null) {
            assemblyParts.clear()
            assemblyParts.addAll(previous.parts)
            selectedPartId = previous.selectedPartId
            updateHistoryState()
            Toast.makeText(context, "Undo: ${previous.actionLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    fun performRedo() {
        val next = historyManager.redo(assemblyParts.toList(), selectedPartId)
        if (next != null) {
            assemblyParts.clear()
            assemblyParts.addAll(next.parts)
            selectedPartId = next.selectedPartId
            updateHistoryState()
            Toast.makeText(context, "Redo: ${next.actionLabel}", Toast.LENGTH_SHORT).show()
        }
    }

    // Penanganan perubahan slider continuous (snapshot awal sebelum drag, commit setelah lepas)
    var pendingSliderSnapshot by remember { mutableStateOf<List<AssemblyPart>?>(null) }
    fun onSliderDragStart() {
        if (pendingSliderSnapshot == null) {
            pendingSliderSnapshot = assemblyParts.map { it.copy() }
        }
    }
    fun onSliderDragFinish(actionLabel: String) {
        pendingSliderSnapshot?.let { snapshot ->
            historyManager.push(snapshot, selectedPartId, actionLabel)
            updateHistoryState()
        }
        pendingSliderSnapshot = null
    }

    // Bangun scene 3D secara reaktif
    val sceneMesh = remember(assemblyParts.toList()) {
        AssemblyEngine.buildAssemblyMesh(assemblyParts.toList())
    }

    val selectedPart = assemblyParts.find { it.id == selectedPartId }

    var inspectorTab by remember { mutableIntStateOf(0) } // 0: Dimensi, 1: Posisi, 2: Rotasi
    var exportedObjPath by remember { mutableStateOf<String?>(null) }
    var showAddPartDialog by remember { mutableStateOf(false) }

    // Rombak total struktur UI menjadi Horizontal Landscape (2-Column Moblo 3D Studio)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // =====================================================================
        // KOLOM KIRI (Panel Komponen & Inventaris Rangka - Lebar 310.dp)
        // =====================================================================
        Surface(
            modifier = Modifier
                .width(310.dp)
                .fillMaxHeight(),
            color = Color(0xFF131B2A),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Komponen Rangka",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            "${assemblyParts.size} Batang (${sceneMesh.totalMaterialLengthMm.toInt()} mm)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tombol Undo Cepat
                        IconButton(
                            onClick = { performUndo() },
                            enabled = canUndo,
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (canUndo) Color(0xFF1E293B) else Color(0xFF131B2A), CircleShape)
                                .testTag("btn_undo_panel_kiri")
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo Perubahan",
                                tint = if (canUndo) Color(0xFFF59E0B) else Color(0xFF475569),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Tombol Redo Cepat
                        IconButton(
                            onClick = { performRedo() },
                            enabled = canRedo,
                            modifier = Modifier
                                .size(32.dp)
                                .background(if (canRedo) Color(0xFF1E293B) else Color(0xFF131B2A), CircleShape)
                                .testTag("btn_redo_panel_kiri")
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                contentDescription = "Redo Perubahan",
                                tint = if (canRedo) Color(0xFF38BDF8) else Color(0xFF475569),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Tombol Buka Draft
                        IconButton(
                            onClick = { showLoadDraftDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                                .testTag("btn_buka_draft_studio")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (drafts.isNotEmpty()) {
                                        Badge(containerColor = Color(0xFF059669)) {
                                            Text("${drafts.size}", fontSize = 9.sp)
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Buka Draft", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Tombol Simpan Draft
                        IconButton(
                            onClick = { showSaveDraftDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                                .testTag("btn_simpan_draft_studio")
                        ) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "Simpan Draft", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        }

                        // Tombol Tambah Batang (+)
                        IconButton(
                            onClick = { showAddPartDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFD97706), CircleShape)
                                .testTag("btn_tambah_batang_studio")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah Batang", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Banner Draft Aktif
                if (currentDraftTitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    currentDraftTitle!!,
                                    fontSize = 11.sp,
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = {
                                    currentDraftId = null
                                    currentDraftTitle = null
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup Draft", tint = Color.Gray, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Presets Cepat Rangka (Kusen Jendela, Kusen Pintu, Meja/Rak)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistChip(
                        onClick = {
                            recordAction("Preset Jendela")
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createKusenJendelaPreset(800f, 1200f))
                            selectedPartId = assemblyParts.firstOrNull()?.id
                        },
                        label = { Text("Jendela", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                    AssistChip(
                        onClick = {
                            recordAction("Preset Pintu")
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createKusenPintuPreset(900f, 2100f))
                            selectedPartId = assemblyParts.firstOrNull()?.id
                        },
                        label = { Text("Pintu", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                    AssistChip(
                        onClick = {
                            recordAction("Preset Meja/Rak")
                            assemblyParts.clear()
                            assemblyParts.addAll(AssemblyEngine.createRangkaMejaPreset(1000f, 600f, 750f))
                            selectedPartId = assemblyParts.firstOrNull()?.id
                        },
                        label = { Text("Meja/Rak", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color.White)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Daftar Hirarki Objek Batang
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(assemblyParts, key = { it.id }) { part ->
                        val isSelected = part.id == selectedPartId
                        val colorDot = when (part.color.lowercase()) {
                            "black" -> Color(0xFF262626)
                            "brown" -> Color(0xFF5D4037)
                            "putih" -> Color(0xFFF5F5F5)
                            else -> Color(0xFFB0BEC5)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF243046),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedPartId = part.id }
                                .testTag("part_item_${part.id}"),
                            color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(colorDot)
                                            .border(0.5.dp, Color.Gray, CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            part.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.5.sp,
                                            color = if (isSelected) Color(0xFFF59E0B) else Color.White,
                                            maxLines = 1
                                        )
                                        Text(
                                            "${part.lengthMm.toInt()} mm • ${part.profileType}",
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Toggle Visibility
                                    IconButton(
                                        onClick = {
                                            recordAction("Toggle ${part.name}")
                                            val idx = assemblyParts.indexOfFirst { it.id == part.id }
                                            if (idx != -1) {
                                                assemblyParts[idx] = part.copy(isVisible = !part.isVisible)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            if (part.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Tampil",
                                            tint = if (part.isVisible) Color(0xFF94A3B8) else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Hapus Part
                                    IconButton(
                                        onClick = {
                                            recordAction("Hapus ${part.name}")
                                            assemblyParts.remove(part)
                                            if (selectedPartId == part.id) {
                                                selectedPartId = assemblyParts.firstOrNull()?.id
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Hapus Batang",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Bottom Action: Kirim ke Kasir POS / Potong
                Button(
                    onClick = {
                        if (onSendToPOS != null) {
                            onSendToPOS(assemblyParts.toList())
                        } else {
                            Toast.makeText(context, "${assemblyParts.size} batang siap dipotong di kasir!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("btn_kirim_pos_dari_studio"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Kirim ke Kasir & Potong", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // =====================================================================
        // AREA KANAN (3D Viewport Utama + Panel Bawah Kontrol Parameter)
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
                    selectedPartId = selectedPartId,
                    modifier = Modifier.fillMaxSize()
                )

                // Floating Undo / Redo HUD Bar di pojok kiri atas viewport 3D
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    color = Color(0xDD0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Tombol Undo
                        IconButton(
                            onClick = { performUndo() },
                            enabled = canUndo,
                            modifier = Modifier.size(30.dp).testTag("btn_undo_studio")
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Undo Perubahan",
                                tint = if (canUndo) Color(0xFFF59E0B) else Color(0xFF475569),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = if (undoCount > 0) "Undo ($undoCount)" else "Undo",
                            fontSize = 11.sp,
                            fontWeight = if (canUndo) FontWeight.Bold else FontWeight.Normal,
                            color = if (canUndo) Color(0xFFF59E0B) else Color(0xFF475569),
                            modifier = Modifier.clickable(enabled = canUndo) { performUndo() }
                        )

                        Divider(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(16.dp)
                                .width(1.dp),
                            color = Color(0xFF334155)
                        )

                        // Tombol Redo
                        IconButton(
                            onClick = { performRedo() },
                            enabled = canRedo,
                            modifier = Modifier.size(30.dp).testTag("btn_redo_studio")
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                contentDescription = "Redo Perubahan",
                                tint = if (canRedo) Color(0xFF38BDF8) else Color(0xFF475569),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = if (redoCount > 0) "Redo ($redoCount)" else "Redo",
                            fontSize = 11.sp,
                            fontWeight = if (canRedo) FontWeight.Bold else FontWeight.Normal,
                            color = if (canRedo) Color(0xFF38BDF8) else Color(0xFF475569),
                            modifier = Modifier.clickable(enabled = canRedo) { performRedo() }
                        )

                        if (canUndo && lastUndoLabel != null) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "↶ $lastUndoLabel",
                                    fontSize = 10.sp,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Tombol Ekspor OBJ di pojok atas kanvas
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
                                assemblyName = "Rangka_Aluminium"
                            )
                            if (path.isNotBlank()) {
                                exportedObjPath = path
                                Toast.makeText(context, "Berkas .obj perakitan tersimpan!", Toast.LENGTH_LONG).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_export_assembly_obj")
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

            // 2. PANEL BAWAH (Kontrol Parameter Objek Aktif - Height ~170.dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF131B2A),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                if (selectedPart == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Pilih objek batang di panel kiri untuk mengatur posisi & dimensi", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Header Baris Inspector + Tab Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    selectedPart.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }

                            // Tab Switcher
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .padding(2.dp)
                            ) {
                                listOf("Dimensi", "Posisi (XYZ)", "Rotasi (3D)").forEachIndexed { idx, label ->
                                    val isTabActive = inspectorTab == idx
                                    Surface(
                                        color = if (isTabActive) Color(0xFFD97706) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .clickable { inspectorTab = idx }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isTabActive) Color.White else Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Divider(color = Color(0xFF1E293B))
                        Spacer(Modifier.height(6.dp))

                        // Isi Tab Parameter
                        when (inspectorTab) {
                            // TAB 0: DIMENSI & PROFIL
                            0 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Slider Panjang Batang
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Panjang Batang:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                            Text("${selectedPart.lengthMm.toInt()} mm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                        }
                                        Slider(
                                            value = selectedPart.lengthMm,
                                            onValueChange = { newVal ->
                                                onSliderDragStart()
                                                val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                                if (idx != -1) assemblyParts[idx] = selectedPart.copy(lengthMm = newVal)
                                            },
                                            onValueChangeFinished = {
                                                onSliderDragFinish("Panjang ${selectedPart.name} (${selectedPart.lengthMm.toInt()} mm)")
                                            },
                                            valueRange = 100f..3000f,
                                            steps = 29
                                        )
                                    }

                                    // Slider Ketebalan Dinding
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Tebal Hollow:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                            Text("${String.format(Locale.US, "%.2f", selectedPart.thicknessMm)} mm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                        }
                                        Slider(
                                            value = selectedPart.thicknessMm,
                                            onValueChange = { newVal ->
                                                onSliderDragStart()
                                                val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                                if (idx != -1) assemblyParts[idx] = selectedPart.copy(thicknessMm = newVal)
                                            },
                                            onValueChangeFinished = {
                                                onSliderDragFinish("Tebal ${selectedPart.name} (${String.format(Locale.US, "%.2f", selectedPart.thicknessMm)} mm)")
                                            },
                                            valueRange = 0.8f..4.0f,
                                            steps = 16
                                        )
                                    }

                                    // Pilihan Warna Anodized
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Warna Profil:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("Silver", "Black", "Brown", "Putih").forEach { colorName ->
                                                val isColorActive = selectedPart.color.equals(colorName, ignoreCase = true)
                                                Surface(
                                                    color = if (isColorActive) Color(0xFFD97706) else Color(0xFF1E293B),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier
                                                        .clickable {
                                                            recordAction("Warna ${selectedPart.name} ($colorName)")
                                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(color = colorName)
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                                ) {
                                                    Text(colorName, fontSize = 10.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // TAB 1: POSISI X / Y / Z
                            1 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Sumbu X
                                    PositionAxisControl(
                                        axisName = "Pos X",
                                        value = selectedPart.posX,
                                        onValueChange = { newVal ->
                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(posX = newVal)
                                        },
                                        onBeforeChange = {
                                            recordAction("Geser Pos X ${selectedPart.name}")
                                        },
                                        onSliderDragStart = { onSliderDragStart() },
                                        onSliderDragFinish = {
                                            onSliderDragFinish("Geser Pos X ${selectedPart.name} (${selectedPart.posX.toInt()} mm)")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Sumbu Y
                                    PositionAxisControl(
                                        axisName = "Pos Y (Tinggi)",
                                        value = selectedPart.posY,
                                        onValueChange = { newVal ->
                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(posY = newVal)
                                        },
                                        onBeforeChange = {
                                            recordAction("Geser Pos Y ${selectedPart.name}")
                                        },
                                        onSliderDragStart = { onSliderDragStart() },
                                        onSliderDragFinish = {
                                            onSliderDragFinish("Geser Pos Y ${selectedPart.name} (${selectedPart.posY.toInt()} mm)")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Sumbu Z
                                    PositionAxisControl(
                                        axisName = "Pos Z (Kedalaman)",
                                        value = selectedPart.posZ,
                                        onValueChange = { newVal ->
                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(posZ = newVal)
                                        },
                                        onBeforeChange = {
                                            recordAction("Geser Pos Z ${selectedPart.name}")
                                        },
                                        onSliderDragStart = { onSliderDragStart() },
                                        onSliderDragFinish = {
                                            onSliderDragFinish("Geser Pos Z ${selectedPart.name} (${selectedPart.posZ.toInt()} mm)")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // TAB 2: ROTASI (3D Euler)
                            2 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    RotationAxisControl(
                                        axisName = "Pitch (Sumbu X)",
                                        degrees = selectedPart.rotPitch,
                                        onDegreeChange = { newVal ->
                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(rotPitch = newVal)
                                        },
                                        onBeforeChange = {
                                            recordAction("Rotasi Pitch ${selectedPart.name}")
                                        },
                                        onSliderDragStart = { onSliderDragStart() },
                                        onSliderDragFinish = {
                                            onSliderDragFinish("Rotasi Pitch ${selectedPart.name} (${selectedPart.rotPitch.toInt()}°)")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RotationAxisControl(
                                        axisName = "Yaw (Sumbu Y)",
                                        degrees = selectedPart.rotYaw,
                                        onDegreeChange = { newVal ->
                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(rotYaw = newVal)
                                        },
                                        onBeforeChange = {
                                            recordAction("Rotasi Yaw ${selectedPart.name}")
                                        },
                                        onSliderDragStart = { onSliderDragStart() },
                                        onSliderDragFinish = {
                                            onSliderDragFinish("Rotasi Yaw ${selectedPart.name} (${selectedPart.rotYaw.toInt()}°)")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    RotationAxisControl(
                                        axisName = "Roll (Sumbu Z)",
                                        degrees = selectedPart.rotRoll,
                                        onDegreeChange = { newVal ->
                                            val idx = assemblyParts.indexOfFirst { it.id == selectedPart.id }
                                            if (idx != -1) assemblyParts[idx] = selectedPart.copy(rotRoll = newVal)
                                        },
                                        onBeforeChange = {
                                            recordAction("Rotasi Roll ${selectedPart.name}")
                                        },
                                        onSliderDragStart = { onSliderDragStart() },
                                        onSliderDragFinish = {
                                            onSliderDragFinish("Rotasi Roll ${selectedPart.name} (${selectedPart.rotRoll.toInt()}°)")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Tambah Batang Baru ke Kanvas
    if (showAddPartDialog) {
        var newPartName by remember { mutableStateOf("Batang #${assemblyParts.size + 1}") }
        var newPartLength by remember { mutableStateOf("1000") }
        var newPartProfile by remember { mutableStateOf("Hollow Box") }

        AlertDialog(
            onDismissRequest = { showAddPartDialog = false },
            title = { Text("Tambah Batang Aluminium Baru") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPartName,
                        onValueChange = { newPartName = it },
                        label = { Text("Nama Komponen") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPartLength,
                        onValueChange = { newPartLength = it },
                        label = { Text("Panjang Batang (mm)") },
                        suffix = { Text("mm") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val len = newPartLength.toFloatOrNull() ?: 1000f
                        val newPart = AssemblyPart(
                            id = UUID.randomUUID().toString(),
                            name = newPartName.ifBlank { "Batang Baru" },
                            profileType = newPartProfile,
                            lengthMm = len,
                            widthMm = 40f,
                            heightMm = 40f,
                            thicknessMm = 1.35f,
                            posX = 0f,
                            posY = 0f,
                            posZ = 0f,
                            rotPitch = 0f,
                            color = "Silver"
                        )
                        recordAction("Tambah Batang (${newPart.name})")
                        assemblyParts.add(newPart)
                        selectedPartId = newPart.id
                        showAddPartDialog = false
                    }
                ) {
                    Text("Tambahkan ke Kanvas")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPartDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Simpan Draft Proyek
    if (showSaveDraftDialog) {
        var draftTitleInput by remember {
            mutableStateOf(currentDraftTitle ?: "Rangka Aluminium ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())}")
        }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSaveDraftDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan Draft Proyek 3D")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Simpan seluruh posisi, rotasi, dan spesifikasi ${assemblyParts.size} batang ke database SQLite offline agar dapat dilanjutkan nanti.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = draftTitleInput,
                        onValueChange = { draftTitleInput = it },
                        label = { Text("Nama / Judul Draft Proyek") },
                        placeholder = { Text("Contoh: Kusen Jendela Living Room") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_draft_title")
                    )
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Total: ${assemblyParts.size} Batang • ${sceneMesh.totalMaterialLengthMm.toInt()} mm Material",
                            fontSize = 11.sp,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = draftTitleInput.ifBlank { "Draft Proyek" }
                        isSaving = true
                        coroutineScope.launch {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            val json = AssemblySerializer.partsToJson(assemblyParts.toList())
                            val draftEntity = DraftAssemblyEntity(
                                id = currentDraftId ?: 0,
                                title = title,
                                updatedAt = now,
                                totalParts = assemblyParts.size,
                                totalLengthMm = sceneMesh.totalMaterialLengthMm,
                                partsJson = json
                            )
                            val savedId = repository?.saveDraft(draftEntity)
                            if (savedId != null) {
                                currentDraftId = savedId.toInt()
                                currentDraftTitle = title
                            }
                            isSaving = false
                            showSaveDraftDialog = false
                            Toast.makeText(context, "Draft '$title' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_save_draft"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text(if (currentDraftId != null) "Perbarui Draft" else "Simpan Draft Baru")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDraftDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Buka / Kelola Draft Proyek Tersimpan
    if (showLoadDraftDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDraftDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF059669))
                    Spacer(Modifier.width(8.dp))
                    Text("Daftar Draft Proyek Tersimpan")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (drafts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Drafts, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Belum ada draft tersimpan di SQLite.", color = Color.Gray, fontSize = 12.sp)
                                Text("Gunakan tombol Simpan Draft untuk menyimpan rangka saat ini.", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(drafts, key = { it.id }) { item ->
                                val isCurrent = item.id == currentDraftId
                                Surface(
                                    color = if (isCurrent) Color(0xFF1E293B) else Color(0xFF0F172A),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isCurrent) 1.5.dp else 0.5.dp,
                                        color = if (isCurrent) Color(0xFFF59E0B) else Color(0xFF243046)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                item.title,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = if (isCurrent) Color(0xFFF59E0B) else Color.White
                                            )
                                            Text(
                                                "${item.totalParts} Batang • ${item.totalLengthMm.toInt()} mm • ${item.updatedAt}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Button(
                                                onClick = {
                                                    val loadedParts = AssemblySerializer.jsonToParts(item.partsJson)
                                                    if (loadedParts.isNotEmpty()) {
                                                        recordAction("Buka Draft (${item.title})")
                                                        assemblyParts.clear()
                                                        assemblyParts.addAll(loadedParts)
                                                        selectedPartId = loadedParts.firstOrNull()?.id
                                                        currentDraftId = item.id
                                                        currentDraftTitle = item.title
                                                        Toast.makeText(context, "Draft '${item.title}' dimuat!", Toast.LENGTH_SHORT).show()
                                                        showLoadDraftDialog = false
                                                    } else {
                                                        Toast.makeText(context, "Data part draft kosong", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp).testTag("btn_load_draft_${item.id}"),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                                            ) {
                                                Text(if (isCurrent) "Aktif" else "Buka", fontSize = 11.sp)
                                            }

                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository?.deleteDraft(item.id)
                                                        if (currentDraftId == item.id) {
                                                            currentDraftId = null
                                                            currentDraftTitle = null
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Draft", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLoadDraftDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}

@Composable
private fun PositionAxisControl(
    axisName: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onBeforeChange: (() -> Unit)? = null,
    onSliderDragStart: (() -> Unit)? = null,
    onSliderDragFinish: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(axisName, fontSize = 11.sp, color = Color(0xFF94A3B8))
            Text("${value.toInt()} mm", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    onBeforeChange?.invoke()
                    onValueChange(value - 50f)
                },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(26.dp)
            ) {
                Text("-50", fontSize = 9.5.sp)
            }
            FilledTonalButton(
                onClick = {
                    onBeforeChange?.invoke()
                    onValueChange(value - 10f)
                },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(26.dp)
            ) {
                Text("-10", fontSize = 9.5.sp)
            }
            FilledTonalButton(
                onClick = {
                    onBeforeChange?.invoke()
                    onValueChange(value + 10f)
                },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(26.dp)
            ) {
                Text("+10", fontSize = 9.5.sp)
            }
            FilledTonalButton(
                onClick = {
                    onBeforeChange?.invoke()
                    onValueChange(value + 50f)
                },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.weight(1f).height(26.dp)
            ) {
                Text("+50", fontSize = 9.5.sp)
            }
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value.coerceIn(-1500f, 1500f),
            onValueChange = {
                onSliderDragStart?.invoke()
                onValueChange(it)
            },
            onValueChangeFinished = {
                onSliderDragFinish?.invoke()
            },
            valueRange = -1500f..1500f,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )
    }
}

@Composable
private fun RotationAxisControl(
    axisName: String,
    degrees: Float,
    onDegreeChange: (Float) -> Unit,
    onBeforeChange: (() -> Unit)? = null,
    onSliderDragStart: (() -> Unit)? = null,
    onSliderDragFinish: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(axisName, fontSize = 11.sp, color = Color(0xFF94A3B8))
            Text("${degrees.toInt()}°", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(0f, 45f, 90f, 180f).forEach { deg ->
                val isActive = degrees == deg
                Surface(
                    color = if (isActive) Color(0xFFD97706) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(26.dp)
                        .clickable {
                            onBeforeChange?.invoke()
                            onDegreeChange(deg)
                        },
                    contentColor = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${deg.toInt()}°", fontSize = 9.5.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = degrees.coerceIn(0f, 360f),
            onValueChange = {
                onSliderDragStart?.invoke()
                onDegreeChange(it)
            },
            onValueChangeFinished = {
                onSliderDragFinish?.invoke()
            },
            valueRange = 0f..360f,
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
        )
    }
}
