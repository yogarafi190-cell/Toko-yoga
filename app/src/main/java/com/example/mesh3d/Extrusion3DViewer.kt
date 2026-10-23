package com.example.mesh3d

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.mesh3d.opengl.OpenGLExtrusionViewer

/**
 * Pilihan Engine Renderer 3D
 */
enum class RendererEngine(val label: String, val badge: String) {
    OPENGL_ES("OpenGL ES 2.0", "GPU Hardware"),
    COMPOSE_CANVAS("Compose Canvas", "Software Engine")
}

/**
 * Komponen 3D Viewer lengkap untuk memvisualisasikan hasil ekstrusi penampang profil 2D
 * dengan kontrol panjang batang dinamis, pilihan finishing warna,
 * serta dukungan dual engine (OpenGL ES 2.0 & Compose Canvas).
 */
@Composable
fun Extrusion3DViewer(
    contour: List<Point2D>,
    initialThicknessMm: Float = 1.2f,
    initialLengthMm: Float = 1000f,
    isClosed: Boolean = true,
    initialWarna: String = "Silver",
    sectionName: String = "Profil Kustom",
    onExportObj: ((String) -> Unit)? = null,
    onSendToStudio: ((AssemblyPart) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var lengthMm by remember { mutableFloatStateOf(initialLengthMm) }
    var thicknessMm by remember { mutableFloatStateOf(initialThicknessMm) }
    var warnaAluminium by remember { mutableStateOf(initialWarna) }
    var selectedEngine by remember { mutableStateOf(RendererEngine.OPENGL_ES) }

    // Hitung Mesh 3D secara reaktif terhadap contour, thickness, dan panjang ekstrusi
    val mesh = remember(contour, thicknessMm, lengthMm, isClosed) {
        if (contour.size >= 3) {
            HollowExtruder.generateHollowMesh(
                contour = contour,
                thickness = thicknessMm.toDouble(),
                panjangBatang = lengthMm.toDouble(),
                isClosed = isClosed
            )
        } else {
            Mesh3DData(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // 1. TOOLBAR KONTROL ATAS
        Surface(
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "3D Extrusion Mesh Viewer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            "$sectionName • Panjang: ${lengthMm.toInt()} mm • Tebal: ${String.format("%.1f", thicknessMm)} mm",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // ENGINE TOGGLE (OpenGL ES 2.0 vs Compose Canvas)
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF0B132B), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        RendererEngine.values().forEach { engine ->
                            val isSelected = selectedEngine == engine
                            Surface(
                                color = if (isSelected) Color(0xFF38BDF8) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { selectedEngine = engine }
                            ) {
                                Text(
                                    engine.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // PILIHAN FINISHING WARNA ALUMINIUM & SLIDER PANJANG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pilihan Warna Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Finishing:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        val colors = listOf("Silver", "Hitam", "Coklat", "Putih", "Gold")
                        colors.forEach { clr ->
                            val isSel = warnaAluminium.equals(clr, ignoreCase = true)
                            val chipDotColor = when (clr) {
                                "Hitam" -> Color(0xFF262626)
                                "Coklat" -> Color(0xFF5D4037)
                                "Putih" -> Color(0xFFF5F5F5)
                                "Gold" -> Color(0xFFE0A96D)
                                else -> Color(0xFFB0BEC5)
                            }
                            FilterChip(
                                selected = isSel,
                                onClick = { warnaAluminium = clr },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(chipDotColor)
                                                .border(0.5.dp, Color.White, CircleShape)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(clr, fontSize = 11.sp)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF334155),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Ekspor & Aksi Studio
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Tombol Simpan/Ekspor OBJ
                        OutlinedButton(
                            onClick = {
                                if (mesh.vertices.isNotEmpty()) {
                                    val fileName = "profil_${sectionName.replace(" ", "_")}_${System.currentTimeMillis()}.obj"
                                    val path = HollowExtruder.exportToObjFile(context, mesh, fileName)
                                    onExportObj?.invoke(path)
                                    Toast.makeText(context, "Mesh diekspor ke: $fileName", Toast.LENGTH_LONG).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(".OBJ", fontSize = 11.sp)
                        }

                        // Tombol Kirim ke 3D Studio
                        if (onSendToStudio != null) {
                            Button(
                                onClick = {
                                    val bbox = com.example.ui.canvas.ProfileCalculations.computeBoundingBox(contour)
                                    val part = AssemblyPart(
                                        id = "custom_${System.currentTimeMillis()}",
                                        name = sectionName,
                                        profileType = "Profil Kustom",
                                        lengthMm = lengthMm,
                                        widthMm = bbox.width.coerceAtLeast(10f),
                                        heightMm = bbox.height.coerceAtLeast(10f),
                                        thicknessMm = thicknessMm,
                                        color = warnaAluminium
                                    )
                                    onSendToStudio(part)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Buka di Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 2. KONTROL PARAMETER EKSTRUSI (Panjang Batang Slider)
        Surface(
            color = Color(0xFF131D31),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Slider Panjang Ekstrusi (100 mm - 6000 mm)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Panjang Ekstrusi Z:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = lengthMm,
                        onValueChange = { lengthMm = it },
                        valueRange = 100f..6000f,
                        modifier = Modifier.weight(1f).testTag("slider_extrusion_length")
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${lengthMm.toInt()} mm (${String.format("%.2f", lengthMm / 1000f)} m)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                }

                // Slider Tebal Dinding
                Row(
                    modifier = Modifier.width(220.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tebal:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = thicknessMm,
                        onValueChange = { thicknessMm = it },
                        valueRange = 0.5f..5.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${String.format("%.1f", thicknessMm)} mm",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF34D399)
                    )
                }
            }
        }

        // 3. AREA 3D VIEWER (OPENGL ES ATAU COMPOSE CANVAS)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            if (contour.size < 3) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B132B)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Draw,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tambahkan minimal 3 titik pada Canvas 2D untuk membentuk penampang 3D",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                when (selectedEngine) {
                    RendererEngine.OPENGL_ES -> {
                        OpenGLExtrusionViewer(
                            mesh = mesh,
                            warnaAluminium = warnaAluminium,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    RendererEngine.COMPOSE_CANVAS -> {
                        Compose3DRenderer(
                            mesh = mesh,
                            warnaAluminium = warnaAluminium,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
