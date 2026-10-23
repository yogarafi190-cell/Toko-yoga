package com.example.mesh3d.opengl

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mesh3d.Mesh3DData

/**
 * Komponen Compose 3D Viewer berbasis OpenGL ES 2.0 untuk merender
 * hasil ekstrusi profil aluminium 2D secara real-time dengan akselerasi GPU hardware.
 */
@Composable
fun OpenGLExtrusionViewer(
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
    var autoRotate by remember { mutableStateOf(false) }

    val renderer = remember { ExtrusionGLRenderer() }
    var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Sinkronisasi lifecycle Android untuk menghemat GPU baterai saat background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glViewRef?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update data mesh & material saat parameter berubah
    LaunchedEffect(mesh) {
        renderer.updateMesh(mesh)
        glViewRef?.requestRender()
    }

    LaunchedEffect(warnaAluminium) {
        renderer.setMaterial(warnaAluminium)
        glViewRef?.requestRender()
    }

    LaunchedEffect(yaw, pitch, zoom, showWireframe, autoRotate) {
        renderer.yawDeg = yaw
        renderer.pitchDeg = pitch
        renderer.zoom = zoom
        renderer.showWireframe = showWireframe
        renderer.autoRotate = autoRotate
        glViewRef?.requestRender()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0B132B))
    ) {
        // 1. HARDWARE OPENGL ES 2.0 SURFACE VIEW
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glViewRef = this
                    renderer.updateMesh(mesh)
                    renderer.setMaterial(warnaAluminium)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("opengl_surface_view")
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.2f, 4.0f)
                        yaw += pan.x * 0.45f
                        pitch = (pitch - pan.y * 0.45f).coerceIn(-89f, 89f)
                    }
                }
        )

        // 2. SUDUT PANDANG PRESET CAD (3D Iso, Front Penampang, Top, Side)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0xDD0F172A), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "View:",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
            TextButton(
                onClick = { yaw = 35f; pitch = -25f; autoRotate = false },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("3D Iso", color = Color.White, fontSize = 11.sp)
            }
            TextButton(
                onClick = { yaw = 0f; pitch = 0f; autoRotate = false },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Penampang Depan", color = Color(0xFF38BDF8), fontSize = 11.sp)
            }
            TextButton(
                onClick = { yaw = 0f; pitch = -88f; autoRotate = false },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Atas", color = Color.White, fontSize = 11.sp)
            }
            TextButton(
                onClick = { yaw = 90f; pitch = 0f; autoRotate = false },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("Samping", color = Color.White, fontSize = 11.sp)
            }
        }

        // 3. BADGE STATUS RENDERER GPU OPENGL
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            color = Color(0xDD0F172A),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF34D399))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "OpenGL ES 2.0 GPU",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }
        }

        // 4. OVERLAY KONTROL ZOOM, WIREFRAME, AUTO-ROTATE
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color(0xDD0F172A), RoundedCornerShape(10.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Auto Rotate Turntable
            IconButton(
                onClick = { autoRotate = !autoRotate },
                modifier = Modifier.size(34.dp).testTag("btn_gl_autorotate")
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Putar Otomatis",
                    tint = if (autoRotate) Color(0xFF34D399) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Zoom In
            IconButton(
                onClick = { zoom = (zoom + 0.25f).coerceAtMost(4.0f) },
                modifier = Modifier.size(34.dp).testTag("btn_gl_zoom_in")
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Perbesar", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Zoom Out
            IconButton(
                onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.2f) },
                modifier = Modifier.size(34.dp).testTag("btn_gl_zoom_out")
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Perkecil", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Reset Sudut Kamera
            IconButton(
                onClick = { yaw = initialYaw; pitch = initialPitch; zoom = 1.0f; autoRotate = false },
                modifier = Modifier.size(34.dp).testTag("btn_gl_reset_cam")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Sudut", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Wireframe Toggle
            IconButton(
                onClick = { showWireframe = !showWireframe },
                modifier = Modifier.size(34.dp).testTag("btn_gl_wireframe")
            ) {
                Icon(
                    Icons.Default.GridOn,
                    contentDescription = "Wireframe CAD",
                    tint = if (showWireframe) Color(0xFFF59E0B) else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 5. STATISTIK MESH 3D (JUMLAH POLIGON & VERTICES)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            color = Color(0x99000000),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                "${mesh.triangles.size} Poligon • ${mesh.vertices.size} Vertices • Finishing: $warnaAluminium",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
