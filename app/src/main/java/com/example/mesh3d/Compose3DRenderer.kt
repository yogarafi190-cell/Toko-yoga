package com.example.mesh3d

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Compose3DAssemblyRenderer(
    scene: AssemblySceneMesh,
    selectedPartId: String? = null,
    modifier: Modifier = Modifier,
    initialYaw: Float = 35f,
    initialPitch: Float = -25f
) {
    var yaw by remember { mutableFloatStateOf(initialYaw) }
    var pitch by remember { mutableFloatStateOf(initialPitch) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var showWireframe by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("canvas_3d_assembly")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw += dragAmount.x * 0.5f
                        pitch = (pitch - dragAmount.y * 0.5f).coerceIn(-89f, 89f)
                    }
                }
        ) {
            if (scene.triangles.isEmpty() || scene.vertices.isEmpty()) {
                // Gambar pesan jika kanvas kosong
                return@Canvas
            }

            renderAssemblyScene(
                scene = scene,
                yawDeg = yaw,
                pitchDeg = pitch,
                zoom = zoom,
                showWireframe = showWireframe
            )
        }

        // Quick View Angle Presets (3D Iso, Front, Top, Side)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0xCC1E293B), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(
                onClick = { yaw = 35f; pitch = -25f },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("3D Iso", color = Color.White, fontSize = 11.sp)
            }
            TextButton(
                onClick = { yaw = 0f; pitch = 0f },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Front", color = Color.White, fontSize = 11.sp)
            }
            TextButton(
                onClick = { yaw = 0f; pitch = -88f },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Top", color = Color.White, fontSize = 11.sp)
            }
            TextButton(
                onClick = { yaw = 90f; pitch = 0f },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Side", color = Color.White, fontSize = 11.sp)
            }
        }

        // Overlay Kontrol Kamera & Wireframe
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color(0xCC1E293B), RoundedCornerShape(10.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { zoom = (zoom + 0.2f).coerceAtMost(4.0f) },
                modifier = Modifier.size(32.dp).testTag("btn_zoom_in_scene")
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.3f) },
                modifier = Modifier.size(32.dp).testTag("btn_zoom_out_scene")
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { yaw = initialYaw; pitch = initialPitch; zoom = 1.0f },
                modifier = Modifier.size(32.dp).testTag("btn_reset_cam_scene")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Sudut", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { showWireframe = !showWireframe },
                modifier = Modifier.size(32.dp).testTag("btn_wireframe_scene")
            ) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = "Wireframe",
                    tint = if (showWireframe) Color(0xFFF59E0B) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Indikator Info Rangka
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            color = Color(0x99000000),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                "${scene.totalPieces} Batang • ${scene.totalMaterialLengthMm.toInt()} mm Total • ${scene.totalVerticesCount} Verts",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

private fun DrawScope.renderAssemblyScene(
    scene: AssemblySceneMesh,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    showWireframe: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
    val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()

    val cosY = cos(yawRad); val sinY = sin(yawRad)
    val cosP = cos(pitchRad); val sinP = sin(pitchRad)

    // Bounding box scene
    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

    for (v in scene.vertices) {
        if (v.x < minX) minX = v.x
        if (v.x > maxX) maxX = v.x
        if (v.y < minY) minY = v.y
        if (v.y > maxY) maxY = v.y
        if (v.z < minZ) minZ = v.z
        if (v.z > maxZ) maxZ = v.z
    }

    val modelCenterX = (minX + maxX) / 2f
    val modelCenterY = (minY + maxY) / 2f
    val modelCenterZ = (minZ + maxZ) / 2f

    val maxExtent = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(100f)
    val scale = (minOf(size.width, size.height) * 0.42f / maxExtent) * zoom

    fun project(v: Vertex3D): Triple<Float, Float, Float> {
        val dx = v.x - modelCenterX
        val dy = v.y - modelCenterY
        val dz = v.z - modelCenterZ

        val x1 = dx * cosY - dz * sinY
        val z1 = dx * sinY + dz * cosY

        val y2 = dy * cosP - z1 * sinP
        val z2 = dy * sinP + z1 * cosP

        val screenX = cx + x1 * scale
        val screenY = cy - y2 * scale

        return Triple(screenX, screenY, z2)
    }

    // Gambar Workshop Floor Grid
    val floorY = minY
    val gridStep = (maxExtent / 6f).coerceAtLeast(100f)
    for (i in -4..4) {
        val pStart = project(Vertex3D(modelCenterX + i * gridStep, floorY, modelCenterZ - 4 * gridStep))
        val pEnd = project(Vertex3D(modelCenterX + i * gridStep, floorY, modelCenterZ + 4 * gridStep))
        drawLine(Color(0x1A94A3B8), Offset(pStart.first, pStart.second), Offset(pEnd.first, pEnd.second), 1f)

        val pStart2 = project(Vertex3D(modelCenterX - 4 * gridStep, floorY, modelCenterZ + i * gridStep))
        val pEnd2 = project(Vertex3D(modelCenterX + 4 * gridStep, floorY, modelCenterZ + i * gridStep))
        drawLine(Color(0x1A94A3B8), Offset(pStart2.first, pStart2.second), Offset(pEnd2.first, pEnd2.second), 1f)
    }

    val lightDir = Vertex3D(0.4f, 0.8f, 0.6f).normalized()
    val baseAluColor = Color(0xFFB0BEC5)

    data class ProjTri(
        val p1: Offset, val p2: Offset, val p3: Offset,
        val depth: Float, val color: Color
    )

    val list = mutableListOf<ProjTri>()

    for (t in scene.triangles) {
        val pr1 = project(t.v1)
        val pr2 = project(t.v2)
        val pr3 = project(t.v3)

        val cross2D = (pr2.first - pr1.first) * (pr3.second - pr1.second) -
                (pr2.second - pr1.second) * (pr3.first - pr1.first)

        val depth = (pr1.third + pr2.third + pr3.third) / 3f

        val dot = (t.normal.x * lightDir.x + t.normal.y * lightDir.y + t.normal.z * lightDir.z).coerceIn(-1f, 1f)
        val intensity = if (cross2D > 0) {
            (0.35f + 0.65f * kotlin.math.max(0.1f, dot)).coerceIn(0.2f, 1.0f)
        } else {
            (0.25f + 0.45f * kotlin.math.max(0.05f, -dot)).coerceIn(0.15f, 0.75f)
        }

        val shaded = Color(
            red = (baseAluColor.red * intensity).coerceIn(0f, 1f),
            green = (baseAluColor.green * intensity).coerceIn(0f, 1f),
            blue = (baseAluColor.blue * intensity).coerceIn(0f, 1f),
            alpha = 1.0f
        )

        list.add(
            ProjTri(
                p1 = Offset(pr1.first, pr1.second),
                p2 = Offset(pr2.first, pr2.second),
                p3 = Offset(pr3.first, pr3.second),
                depth = depth,
                color = shaded
            )
        )
    }

    // Painter's algorithm
    list.sortBy { it.depth }

    val path = Path()
    val edgeColor = Color(0x33FFFFFF)

    for (item in list) {
        path.reset()
        path.moveTo(item.p1.x, item.p1.y)
        path.lineTo(item.p2.x, item.p2.y)
        path.lineTo(item.p3.x, item.p3.y)
        path.close()

        drawPath(path = path, color = item.color)

        if (showWireframe) {
            drawPath(path = path, color = Color(0xFFF59E0B), style = Stroke(width = 1.2f))
        } else {
            drawPath(path = path, color = edgeColor, style = Stroke(width = 0.5f))
        }
    }
}

@Composable
fun Compose3DRenderer(
    mesh: Mesh3DData,
    warnaAluminium: String = "Silver",
    modifier: Modifier = Modifier,
    initialYaw: Float = 35f,
    initialPitch: Float = -25f
) {
    var yaw by remember { mutableFloatStateOf(initialYaw) }
    var pitch by remember { mutableFloatStateOf(initialPitch) }
    var zoom by remember { mutableFloatStateOf(1.0f) }
    var showWireframe by remember { mutableStateOf(false) }

    val baseColor = when (warnaAluminium.lowercase()) {
        "black", "hitam" -> Color(0xFF262626)
        "brown", "coklat" -> Color(0xFF5D4037)
        "putih", "white" -> Color(0xFFF5F5F5)
        else -> Color(0xFFB0BEC5)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF0F172A))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("canvas_3d_renderer")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        yaw += dragAmount.x * 0.6f
                        pitch = (pitch - dragAmount.y * 0.6f).coerceIn(-89f, 89f)
                    }
                }
        ) {
            if (mesh.triangles.isEmpty() || mesh.vertices.isEmpty()) return@Canvas

            renderSingleMesh(
                mesh = mesh,
                yawDeg = yaw,
                pitchDeg = pitch,
                zoom = zoom,
                baseColor = baseColor,
                showWireframe = showWireframe
            )
        }

        // Overlay kontrol kamera
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color(0xCC1E293B), RoundedCornerShape(12.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { zoom = (zoom + 0.2f).coerceAtMost(3.0f) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", tint = Color.White)
            }
            IconButton(onClick = { zoom = (zoom - 0.2f).coerceAtLeast(0.4f) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", tint = Color.White)
            }
            IconButton(onClick = { yaw = initialYaw; pitch = initialPitch; zoom = 1.0f }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.White)
            }
            IconButton(onClick = { showWireframe = !showWireframe }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.GridOn, contentDescription = "Wireframe", tint = if (showWireframe) Color(0xFFF59E0B) else Color.White)
            }
        }
    }
}

private fun DrawScope.renderSingleMesh(
    mesh: Mesh3DData,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    baseColor: Color,
    showWireframe: Boolean
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
    val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()

    val cosY = cos(yawRad); val sinY = sin(yawRad)
    val cosP = cos(pitchRad); val sinP = sin(pitchRad)

    var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

    for (v in mesh.vertices) {
        if (v.x < minX) minX = v.x
        if (v.x > maxX) maxX = v.x
        if (v.y < minY) minY = v.y
        if (v.y > maxY) maxY = v.y
        if (v.z < minZ) minZ = v.z
        if (v.z > maxZ) maxZ = v.z
    }

    val modelCenterX = (minX + maxX) / 2f
    val modelCenterY = (minY + maxY) / 2f
    val modelCenterZ = (minZ + maxZ) / 2f

    val maxExtent = maxOf(maxX - minX, maxY - minY, (maxZ - minZ) * 0.4f).coerceAtLeast(10f)
    val scale = (minOf(size.width, size.height) * 0.45f / maxExtent) * zoom

    fun project(v: Vertex3D): Triple<Float, Float, Float> {
        val dx = v.x - modelCenterX
        val dy = v.y - modelCenterY
        val dz = (v.z - modelCenterZ) * 0.35f

        val x1 = dx * cosY - dz * sinY
        val z1 = dx * sinY + dz * cosY

        val y2 = dy * cosP - z1 * sinP
        val z2 = dy * sinP + z1 * cosP

        return Triple(cx + x1 * scale, cy - y2 * scale, z2)
    }

    val lightDir = Vertex3D(0.5f, 0.8f, 0.7f).normalized()

    data class Tri(val p1: Offset, val p2: Offset, val p3: Offset, val depth: Float, val color: Color)
    val projectedList = mutableListOf<Tri>()

    for (t in mesh.triangles) {
        val pr1 = project(t.v1); val pr2 = project(t.v2); val pr3 = project(t.v3)
        val cross2D = (pr2.first - pr1.first) * (pr3.second - pr1.second) -
                (pr2.second - pr1.second) * (pr3.first - pr1.first)

        val depth = (pr1.third + pr2.third + pr3.third) / 3f
        val dot = (t.normal.x * lightDir.x + t.normal.y * lightDir.y + t.normal.z * lightDir.z).coerceIn(-1f, 1f)
        val intensity = if (cross2D > 0) {
            (0.35f + 0.65f * kotlin.math.max(0.1f, dot)).coerceIn(0.2f, 1.0f)
        } else {
            (0.25f + 0.45f * kotlin.math.max(0.05f, -dot)).coerceIn(0.15f, 0.75f)
        }

        val shaded = Color(
            (baseColor.red * intensity).coerceIn(0f, 1f),
            (baseColor.green * intensity).coerceIn(0f, 1f),
            (baseColor.blue * intensity).coerceIn(0f, 1f),
            1.0f
        )
        projectedList.add(Tri(Offset(pr1.first, pr1.second), Offset(pr2.first, pr2.second), Offset(pr3.first, pr3.second), depth, shaded))
    }

    projectedList.sortBy { it.depth }
    val path = Path()
    for (item in projectedList) {
        path.reset()
        path.moveTo(item.p1.x, item.p1.y)
        path.lineTo(item.p2.x, item.p2.y)
        path.lineTo(item.p3.x, item.p3.y)
        path.close()
        drawPath(path = path, color = item.color)
        if (showWireframe) {
            drawPath(path = path, color = Color(0xFFF59E0B), style = Stroke(width = 1.2f))
        } else {
            drawPath(path = path, color = Color(0x33FFFFFF), style = Stroke(width = 0.5f))
        }
    }
}
