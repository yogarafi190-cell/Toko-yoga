package com.example.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.data.local.ProdukProfilEntity
import com.example.data.repository.TokoYogaRepository
import com.example.mesh3d.*
import com.example.ui.canvas.*
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Pilihan Mode Tampilan Workspace Profil
 */
enum class CanvasViewMode(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CANVAS_2D("2D CAD", Icons.Default.Draw),
    VIEWER_3D("3D Extrusi", Icons.Default.ViewInAr),
    SPLIT_VIEW("Split 2D/3D", Icons.Default.VerticalSplit)
}

/**
 * Layar Workspace Editor Penampang Profil Aluminium 2D.
 * Dilengkapi dengan Canvas koordinat interaktif, snapping grid,
 * kalkulator metrik penampang real-time, presets standar industri,
 * dan integrasi ekstrusi 3D langsung ke inventaris dan studio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCanvasScreen(
    repository: TokoYogaRepository? = null,
    onNavigateToStudioWithPart: ((AssemblyPart) -> Unit)? = null,
    onNavigateBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. STATE TITIK-TITIK KOORDINAT (Default: Hollow Tube 40x40)
    var points by remember {
        mutableStateOf(
            listOf(
                Point2D(0f, 0f),
                Point2D(40f, 0f),
                Point2D(40f, 40f),
                Point2D(0f, 40f)
            )
        )
    }

    // History stack untuk Undo & Redo
    var undoStack by remember { mutableStateOf(listOf<List<Point2D>>()) }
    var redoStack by remember { mutableStateOf(listOf<List<Point2D>>()) }

    fun pushHistory(newPoints: List<Point2D>) {
        undoStack = undoStack + listOf(points)
        redoStack = emptyList()
        points = newPoints
    }

    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + listOf(points)
            points = prev
        }
    }

    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + listOf(points)
            points = next
        }
    }

    // 2. PARAMETER TEKNIS PROFIL
    var sectionName by remember { mutableStateOf("CUST-4040") }
    var category by remember { mutableStateOf("Commodities") }
    var isClosed by remember { mutableStateOf(true) }
    var wallThicknessMm by remember { mutableFloatStateOf(1.2f) }
    var gridSnapOption by remember { mutableStateOf(GridSnapOption.SNAP_5MM) }
    var activeTool by remember { mutableStateOf(CanvasTool.ADD_POINT) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    var pricePerMeter by remember { mutableLongStateOf(85000L) }
    var colorName by remember { mutableStateOf("Silver") }
    var viewMode by remember { mutableStateOf(CanvasViewMode.CANVAS_2D) }

    // Dialog state
    var showPresetDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var show3DPreviewDialog by remember { mutableStateOf(false) }
    var showPointsTableSheet by remember { mutableStateOf(false) }

    // Hitung metrik profil secara reaktif
    val metrics = remember(points, wallThicknessMm, isClosed) {
        ProfileCalculations.computeMetrics(points, wallThicknessMm, isClosed)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Draw,
                            contentDescription = "2D Canvas Icon",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                "2D Extrusion Profile Canvas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                "$sectionName • ${points.size} Titik Sudut • ${metrics.boundingBox.width.toInt()}x${metrics.boundingBox.height.toInt()} mm",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    // Switcher Mode Tampilan: 2D CAD | 3D Extrusion | Split 2D/3D
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        CanvasViewMode.values().forEach { mode ->
                            val isSel = viewMode == mode
                            Surface(
                                color = if (isSel) Color(0xFF38BDF8) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable { viewMode = mode }
                                    .testTag("tab_mode_${mode.name}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        mode.icon,
                                        contentDescription = null,
                                        tint = if (isSel) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        mode.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Tombol Undo
                    IconButton(
                        onClick = { handleUndo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.testTag("btn_canvas_undo")
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF475569)
                        )
                    }

                    // Tombol Redo
                    IconButton(
                        onClick = { handleRedo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.testTag("btn_canvas_redo")
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF475569)
                        )
                    }

                    // Tombol Buka Preset Standar
                    IconButton(
                        onClick = { showPresetDialog = true },
                        modifier = Modifier.testTag("btn_canvas_presets")
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Pilih Preset Profil", tint = Color(0xFFF59E0B))
                    }

                    // Tombol Tabel Koordinat
                    IconButton(
                        onClick = { showPointsTableSheet = true },
                        modifier = Modifier.testTag("btn_canvas_points_table")
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = "Tabel Koordinat", tint = Color(0xFF38BDF8))
                    }

                    // Tombol Preview 3D Extrusion
                    FilledTonalButton(
                        onClick = { show3DPreviewDialog = true },
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("btn_canvas_preview_3d"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF38BDF8)
                        )
                    ) {
                        Icon(Icons.Default.ViewInAr, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("3D Preview", fontSize = 12.sp)
                    }

                    // Tombol Simpan ke Database
                    Button(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("btn_canvas_save_profile"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Simpan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF0B132B))
        ) {
            // TOOLBAR KONTROL ATAS: Pilihan Tool & Snapping Grid
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. Tool Selector Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(CanvasTool.values()) { tool ->
                                FilterChip(
                                    selected = activeTool == tool,
                                    onClick = { activeTool = tool },
                                    label = { Text(tool.title, fontSize = 12.sp) },
                                    leadingIcon = {
                                        val icon = when (tool) {
                                            CanvasTool.ADD_POINT -> Icons.Default.AddLocationAlt
                                            CanvasTool.SELECT_MOVE -> Icons.Default.NearMe
                                            CanvasTool.INSERT_POINT -> Icons.Default.LinearScale
                                            CanvasTool.DELETE_POINT -> Icons.Default.Delete
                                            CanvasTool.PAN_ZOOM -> Icons.Default.PanTool
                                        }
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFD97706),
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF94A3B8),
                                        iconColor = Color(0xFF94A3B8)
                                    ),
                                    modifier = Modifier.testTag("tool_chip_${tool.name}")
                                )
                            }
                        }

                        // Tombol Reset / Clear Canvas
                        OutlinedButton(
                            onClick = {
                                pushHistory(emptyList())
                                selectedPointIndex = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x60EF4444)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_canvas_clear")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bersihkan", fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // 2. Baris Setting Parameter Grid Snapping, Loop Tertutup & Tebal Dinding
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Snapping selector
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Snap:", fontSize = 12.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                            Spacer(Modifier.width(6.dp))
                            GridSnapOption.values().forEach { snap ->
                                val isSelected = gridSnapOption == snap
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF38BDF8) else Color(0xFF0F172A))
                                        .clickable { gridSnapOption = snap }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                        .testTag("snap_option_${snap.name}")
                                ) {
                                    Text(
                                        snap.label,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Toggle Loop Tertutup (Hollow Tube) vs Terbuka (Siku / Plat)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bentuk:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = isClosed,
                                onClick = { isClosed = !isClosed },
                                label = { Text(if (isClosed) "Loop Tertutup (Hollow)" else "Profil Terbuka", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        if (isClosed) Icons.Default.AllInclusive else Icons.Default.ShowChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2563EB),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.testTag("toggle_closed_loop")
                            )
                        }

                        // Tebal Dinding Aluminium Slider / Chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "Tebal: ${String.format("%.1f", wallThicknessMm)} mm",
                                fontSize = 12.sp,
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            listOf(1.0f, 1.2f, 1.5f, 2.0f, 3.0f).forEach { t ->
                                val isSelected = abs(wallThicknessMm - t) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Color(0xFFF59E0B) else Color(0xFF0F172A))
                                        .clickable { wallThicknessMm = t }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "${t}mm",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AREA UTAMA: TAMPILAN SESUAI VIEW MODE (2D CAD / 3D EXTRUSION / SPLIT)
            when (viewMode) {
                CanvasViewMode.VIEWER_3D -> {
                    Extrusion3DViewer(
                        contour = points,
                        initialThicknessMm = wallThicknessMm,
                        initialLengthMm = 1000f,
                        isClosed = isClosed,
                        initialWarna = colorName,
                        sectionName = sectionName,
                        onSendToStudio = { part ->
                            onNavigateToStudioWithPart?.invoke(part)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                CanvasViewMode.SPLIT_VIEW -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // Kiri: 2D Compose Canvas
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                        ) {
                            AluminiumProfileCanvas(
                                points = points,
                                isClosed = isClosed,
                                wallThicknessMm = wallThicknessMm,
                                selectedPointIndex = selectedPointIndex,
                                activeTool = activeTool,
                                gridSnapMm = gridSnapOption.mm,
                                modifier = Modifier.fillMaxSize(),
                                onPointsChanged = { newPts -> pushHistory(newPts) },
                                onSelectPoint = { idx -> selectedPointIndex = idx },
                                onAddPoint = { pt -> pushHistory(points + pt) },
                                onInsertPoint = { idx, pt ->
                                    val updated = points.toMutableList().apply { add(idx, pt) }
                                    pushHistory(updated)
                                },
                                onDeletePoint = { idx ->
                                    if (idx in points.indices) {
                                        val updated = points.toMutableList().apply { removeAt(idx) }
                                        pushHistory(updated)
                                    }
                                }
                            )
                        }

                        // Kanan: Real-Time 3D Extrusion Mesh Viewer (OpenGL ES 2.0)
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxHeight()
                        ) {
                            Extrusion3DViewer(
                                contour = points,
                                initialThicknessMm = wallThicknessMm,
                                initialLengthMm = 1000f,
                                isClosed = isClosed,
                                initialWarna = colorName,
                                sectionName = sectionName,
                                onSendToStudio = { part ->
                                    onNavigateToStudioWithPart?.invoke(part)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                CanvasViewMode.CANVAS_2D -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        // 1. Komponen 2D Compose Canvas
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            AluminiumProfileCanvas(
                                points = points,
                                isClosed = isClosed,
                                wallThicknessMm = wallThicknessMm,
                                selectedPointIndex = selectedPointIndex,
                                activeTool = activeTool,
                                gridSnapMm = gridSnapOption.mm,
                                modifier = Modifier.fillMaxSize(),
                                onPointsChanged = { newPts ->
                                    pushHistory(newPts)
                                },
                                onSelectPoint = { idx ->
                                    selectedPointIndex = idx
                                },
                                onAddPoint = { pt ->
                                    pushHistory(points + pt)
                                },
                                onInsertPoint = { idx, pt ->
                                    val updated = points.toMutableList().apply { add(idx, pt) }
                                    pushHistory(updated)
                                },
                                onDeletePoint = { idx ->
                                    if (idx in points.indices) {
                                        val updated = points.toMutableList().apply { removeAt(idx) }
                                        pushHistory(updated)
                                    }
                                }
                            )
                        }

                        // 2. Panel Metrik Rekayasa Profil (Sidebar Kanan)
                        EngineeringMetricsSidebar(
                            metrics = metrics,
                            wallThicknessMm = wallThicknessMm,
                            selectedPoint = if (selectedPointIndex != null && selectedPointIndex in points.indices) points[selectedPointIndex!!] else null,
                            selectedPointIndex = selectedPointIndex,
                            onDeleteSelectedPoint = {
                                val idx = selectedPointIndex
                                if (idx != null && idx in points.indices) {
                                    val updated = points.toMutableList().apply { removeAt(idx) }
                                    pushHistory(updated)
                                    selectedPointIndex = null
                                }
                            },
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .background(Color(0xFF0F172A))
                        )
                    }
                }
            }
        }
    }

    // MODAL DIALOG: PILIH PRESET STANDAR INDUSTRI
    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFFF59E0B))
                    Spacer(Modifier.width(8.dp))
                    Text("Pilih Preset Profil Standar", color = Color.White)
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ProfilePresetRegistry.presets) { preset ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pushHistory(preset.points)
                                    sectionName = preset.name.replace(" ", "-")
                                    category = preset.category
                                    wallThicknessMm = preset.thicknessMm
                                    isClosed = preset.isClosed
                                    showPresetDialog = false
                                    Toast.makeText(context, "Preset ${preset.name} dimuat", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text(preset.description, color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Tebal: ${preset.thicknessMm} mm • ${preset.points.size} Titik • ${if (preset.isClosed) "Loop Tertutup" else "Terbuka"}",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetDialog = false }) {
                    Text("Tutup", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // MODAL DIALOG: PREVIEW EKSTRUSI 3D LENGKAP
    if (show3DPreviewDialog) {
        val mesh3D = remember(points, wallThicknessMm, isClosed) {
            HollowExtruder.generateHollowMesh(
                contour = points,
                thickness = wallThicknessMm.toDouble(),
                panjangBatang = 600.0,
                isClosed = isClosed
            )
        }

        val sceneMesh = remember(mesh3D) {
            AssemblySceneMesh(
                totalPieces = 1,
                totalMaterialLengthMm = 600.0,
                totalVerticesCount = mesh3D.vertices.size,
                vertices = mesh3D.vertices,
                triangles = mesh3D.triangles,
                indices = mesh3D.indices,
                parts = listOf(
                    AssemblyPart(
                        name = sectionName,
                        profileType = sectionName,
                        lengthMm = 600f,
                        widthMm = metrics.boundingBox.width,
                        heightMm = metrics.boundingBox.height,
                        thicknessMm = wallThicknessMm
                    )
                )
            )
        }

        AlertDialog(
            onDismissRequest = { show3DPreviewDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ViewInAr, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(Modifier.width(8.dp))
                    Text("Preview Ekstrusi 3D: $sectionName", color = Color.White)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    Text(
                        "Penampang 2D diekstrusi sepanjang 600 mm sumbu Z dengan ketebalan ${wallThicknessMm} mm (${mesh3D.triangles.size} poligon). Putar dengan sentuhan.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Compose3DAssemblyRenderer(
                            scene = sceneMesh,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        show3DPreviewDialog = false
                        val newPart = AssemblyPart(
                            name = sectionName,
                            profileType = sectionName,
                            lengthMm = 1000f,
                            widthMm = metrics.boundingBox.width,
                            heightMm = metrics.boundingBox.height,
                            thicknessMm = wallThicknessMm,
                            color = colorName
                        )
                        onNavigateToStudioWithPart?.invoke(newPart)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Ekstrusi ke 3D Studio", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { show3DPreviewDialog = false }) {
                    Text("Tutup", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // MODAL DIALOG: SIMPAN PROFIL KE DATABASE INVENTARIS
    if (showSaveDialog) {
        var inputSectionNo by remember { mutableStateOf(sectionName) }
        var inputCategory by remember { mutableStateOf(category) }
        var inputPriceText by remember { mutableStateOf(pricePerMeter.toString()) }
        var inputColor by remember { mutableStateOf(colorName) }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFFD97706))
                    Spacer(Modifier.width(8.dp))
                    Text("Simpan Profil ke Inventaris", color = Color.White)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputSectionNo,
                        onValueChange = { inputSectionNo = it },
                        label = { Text("Nomor Seksi (Section No)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputPriceText,
                        onValueChange = { inputPriceText = it },
                        label = { Text("Harga per Meter (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Kategori Chip Selector
                    Text("Kategori:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val categories = listOf("Shop Front", "Sliding Door", "Casement Window", "Commodities", "Custom")
                        items(categories) { cat ->
                            FilterChip(
                                selected = inputCategory == cat,
                                onClick = { inputCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Warna Chip Selector
                    Text("Warna:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Silver", "Black", "Brown", "Putih").forEach { clr ->
                            FilterChip(
                                selected = inputColor == clr,
                                onClick = { inputColor = clr },
                                label = { Text(clr, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val price = inputPriceText.toLongOrNull() ?: 85000L
                        coroutineScope.launch {
                            // Ekspor file OBJ ke memori lokal HP
                            val mesh3D = HollowExtruder.generateHollowMesh(
                                contour = points,
                                thickness = wallThicknessMm.toDouble(),
                                panjangBatang = 6000.0,
                                isClosed = isClosed
                            )
                            val cleanSection = inputSectionNo.trim().replace(" ", "_")
                            val fileName = "profil_${cleanSection}_${System.currentTimeMillis()}.obj"
                            val objPath = HollowExtruder.exportToObjFile(context, mesh3D, fileName)

                            // Simpan entitas ke Room Database
                            val newEntity = ProdukProfilEntity(
                                sectionNo = inputSectionNo.trim(),
                                kategori = inputCategory,
                                thickness = wallThicknessMm.toDouble(),
                                hargaPerMeter = price,
                                warna = inputColor,
                                file3dPath = objPath ?: ""
                            )
                            repository?.insertProduk(newEntity)

                            sectionName = inputSectionNo
                            category = inputCategory
                            pricePerMeter = price
                            colorName = inputColor
                            showSaveDialog = false
                            Toast.makeText(context, "Profil $inputSectionNo berhasil disimpan ke inventaris!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Simpan ke Database", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Batal", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // MODAL DIALOG: TABEL KOORDINAT TITIK
    if (showPointsTableSheet) {
        AlertDialog(
            onDismissRequest = { showPointsTableSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(Modifier.width(8.dp))
                    Text("Daftar Titik Koordinat (${points.size} Titik)", color = Color.White)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(points) { idx, pt ->
                        Surface(
                            color = if (idx == selectedPointIndex) Color(0xFF334155) else Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPointIndex = idx }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "P${idx + 1}",
                                        color = if (idx == selectedPointIndex) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "X: ${String.format("%.1f", pt.x)} mm , Y: ${String.format("%.1f", pt.y)} mm",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = points.toMutableList().apply { removeAt(idx) }
                                        pushHistory(updated)
                                        if (selectedPointIndex == idx) selectedPointIndex = null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPointsTableSheet = false }) {
                    Text("Selesai", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // MODAL DIALOG: 3D EXTRUSION MESH VIEWER (OPENGL ES 2.0)
    if (show3DPreviewDialog) {
        Dialog(
            onDismissRequest = { show3DPreviewDialog = false },
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
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "3D Extrusion Mesh Viewer (OpenGL ES 2.0)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        IconButton(
                            onClick = { show3DPreviewDialog = false },
                            modifier = Modifier.testTag("btn_close_3d_preview")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                        }
                    }

                    Extrusion3DViewer(
                        contour = points,
                        initialThicknessMm = wallThicknessMm,
                        initialLengthMm = 1000f,
                        isClosed = isClosed,
                        initialWarna = colorName,
                        sectionName = sectionName,
                        onSendToStudio = { part ->
                            show3DPreviewDialog = false
                            onNavigateToStudioWithPart?.invoke(part)
                        },
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
 * Sidebar kanan untuk metrik teknis rekayasa profil aluminium.
 */
@Composable
private fun EngineeringMetricsSidebar(
    metrics: ProfileMetrics,
    wallThicknessMm: Float,
    selectedPoint: Point2D?,
    selectedPointIndex: Int?,
    onDeleteSelectedPoint: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "METRIK PENAMPANG",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            letterSpacing = 1.sp
        )

        // 1. Kartu Dimensi Luar
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Dimensi Luar (W x H)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(
                    "${String.format("%.1f", metrics.boundingBox.width)} x ${String.format("%.1f", metrics.boundingBox.height)} mm",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // 2. Kartu Keliling Luar
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Keliling Profil", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(
                    "${String.format("%.1f", metrics.perimeterMm)} mm",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8)
                )
            }
        }

        // 3. Kartu Luas Penampang Bahan Aluminium
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Luas Bahan Solid Aluminium", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(
                    "${String.format("%.1f", metrics.aluminiumAreaMm2)} mm²",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399)
                )
                if (metrics.enclosedAreaMm2 > 0f) {
                    Text(
                        "Rongga dalam: ${String.format("%.0f", metrics.enclosedAreaMm2 - metrics.aluminiumAreaMm2)} mm²",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // 4. Kartu Estimasi Berat per Meter
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("Estimasi Berat Batang", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(
                    "${String.format("%.3f", metrics.estimatedWeightKgPerMeter)} kg/m",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B)
                )
                Text(
                    "Massa 6 meter: ${String.format("%.2f", metrics.estimatedWeightKgPerMeter * 6f)} kg",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        // 5. Titik Sudut Terpilih
        if (selectedPoint != null && selectedPointIndex != null) {
            Surface(
                color = Color(0x30F59E0B),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Titik P${selectedPointIndex + 1} Terpilih",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                        IconButton(
                            onClick = onDeleteSelectedPoint,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus Titik", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        "Koordinat: (${String.format("%.1f", selectedPoint.x)}, ${String.format("%.1f", selectedPoint.y)}) mm",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Petunjuk Cepat
        Surface(
            color = Color(0xFF0B132B),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Petunjuk:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Text("• Sentuh grid untuk tambah titik.", fontSize = 10.sp, color = Color(0xFF64748B))
                Text("• Geser titik untuk ubah posisi.", fontSize = 10.sp, color = Color(0xFF64748B))
                Text("• Cubit layar untuk zoom in/out.", fontSize = 10.sp, color = Color(0xFF64748B))
            }
        }
    }
}
