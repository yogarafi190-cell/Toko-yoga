package com.example.ui.canvas

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh3d.Point2D
import kotlin.math.*

/**
 * 2D Canvas UI Component yang memungkinkan pengguna menggambar dan mendefinisikan
 * penampang profil ekstrusi aluminium dengan menempatkan titik-titik pada grid koordinat.
 */
@Composable
fun AluminiumProfileCanvas(
    points: List<Point2D>,
    isClosed: Boolean,
    wallThicknessMm: Float,
    selectedPointIndex: Int?,
    activeTool: CanvasTool,
    gridSnapMm: Float,
    modifier: Modifier = Modifier,
    onPointsChanged: (List<Point2D>) -> Unit,
    onSelectPoint: (Int?) -> Unit,
    onCursorCoordinateChanged: ((Point2D?) -> Unit)? = null,
    onAddPoint: ((Point2D) -> Unit)? = null,
    onInsertPoint: ((Int, Point2D) -> Unit)? = null,
    onDeletePoint: ((Int) -> Unit)? = null
) {
    val density = LocalDensity.current

    // Zoom scale: Pixel per Millimeter (default 3.5 px/mm)
    var zoomScale by remember { mutableFloatStateOf(3.5f) }
    // Pan offset dalam satuan pixel layar
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Titik yang sedang di-drag oleh user
    var draggingPointIndex by remember { mutableStateOf<Int?>(null) }
    // Posisi kursor saat ini (dalam mm)
    var cursorPositionMm by remember { mutableStateOf<Point2D?>(null) }

    // Snap math helper
    fun snapValue(value: Float): Float {
        if (gridSnapMm <= 0f) return value
        return (round(value / gridSnapMm) * gridSnapMm)
    }

    // Konversi koordinat layar (Pixel) -> Dunia 2D CAD (Millimeter, Cartesian +Y atas)
    fun screenToWorld(screenPx: Offset, width: Float, height: Float): Point2D {
        val centerX = width / 2f + panOffset.x
        val centerY = height / 2f + panOffset.y
        val mmX = (screenPx.x - centerX) / zoomScale
        val mmY = (centerY - screenPx.y) / zoomScale
        return Point2D(mmX, mmY)
    }

    fun screenToWorld(screenPx: Offset, intSize: androidx.compose.ui.unit.IntSize): Point2D {
        return screenToWorld(screenPx, intSize.width.toFloat(), intSize.height.toFloat())
    }

    fun screenToWorld(screenPx: Offset, canvasSize: androidx.compose.ui.geometry.Size): Point2D {
        return screenToWorld(screenPx, canvasSize.width, canvasSize.height)
    }

    // Konversi koordinat Dunia 2D CAD (Millimeter) -> Layar (Pixel)
    fun worldToScreen(worldPoint: Point2D, canvasSize: androidx.compose.ui.geometry.Size): Offset {
        val centerX = canvasSize.width / 2f + panOffset.x
        val centerY = canvasSize.height / 2f + panOffset.y
        val px = centerX + worldPoint.x * zoomScale
        val py = centerY - worldPoint.y * zoomScale
        return Offset(px, py)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0F172A))
            .testTag("aluminium_profile_canvas_container")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("aluminium_profile_canvas")
                // Gesture 1: Tap Detection (Add, Select, Insert, Delete)
                .pointerInput(points, activeTool, gridSnapMm, zoomScale, panOffset, selectedPointIndex) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val worldPt = screenToWorld(tapOffset, size)
                            val touchRadiusPx = with(density) { 26.dp.toPx() }
                            val touchRadiusMm = touchRadiusPx / zoomScale

                            val nearestIndex = ProfileCalculations.findNearestPointIndex(
                                points = points,
                                query = worldPt,
                                thresholdMm = touchRadiusMm
                            )

                            when (activeTool) {
                                CanvasTool.DELETE_POINT -> {
                                    if (nearestIndex != null) {
                                        if (onDeletePoint != null) {
                                            onDeletePoint(nearestIndex)
                                        } else {
                                            val updated = points.toMutableList().apply { removeAt(nearestIndex) }
                                            onPointsChanged(updated)
                                        }
                                        onSelectPoint(null)
                                    }
                                }
                                CanvasTool.INSERT_POINT -> {
                                    if (nearestIndex != null) {
                                        onSelectPoint(nearestIndex)
                                    } else {
                                        val segIndex = ProfileCalculations.findNearestSegmentIndex(
                                            points = points,
                                            query = worldPt,
                                            isClosed = isClosed,
                                            thresholdMm = touchRadiusMm * 1.5f
                                        )
                                        if (segIndex != null) {
                                            val snappedX = snapValue(worldPt.x)
                                            val snappedY = snapValue(worldPt.y)
                                            val newPt = Point2D(snappedX, snappedY)
                                            if (onInsertPoint != null) {
                                                onInsertPoint(segIndex + 1, newPt)
                                            } else {
                                                val updated = points.toMutableList().apply { add(segIndex + 1, newPt) }
                                                onPointsChanged(updated)
                                            }
                                            onSelectPoint(segIndex + 1)
                                        }
                                    }
                                }
                                CanvasTool.SELECT_MOVE -> {
                                    onSelectPoint(nearestIndex)
                                }
                                CanvasTool.ADD_POINT -> {
                                    if (nearestIndex != null) {
                                        onSelectPoint(nearestIndex)
                                    } else {
                                        val snappedX = snapValue(worldPt.x)
                                        val snappedY = snapValue(worldPt.y)
                                        val newPt = Point2D(snappedX, snappedY)
                                        if (onAddPoint != null) {
                                            onAddPoint(newPt)
                                        } else {
                                            val updated = points + newPt
                                            onPointsChanged(updated)
                                        }
                                        onSelectPoint(points.size) // Select new added point
                                    }
                                }
                                CanvasTool.PAN_ZOOM -> {
                                    onSelectPoint(nearestIndex)
                                }
                            }
                        }
                    )
                }
                // Gesture 2: Drag Gestures (Move Selected Point or Pan Canvas)
                .pointerInput(points, activeTool, gridSnapMm, zoomScale, panOffset, selectedPointIndex) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val worldPt = screenToWorld(startOffset, size)
                            val touchRadiusPx = with(density) { 26.dp.toPx() }
                            val touchRadiusMm = touchRadiusPx / zoomScale

                            val nearest = ProfileCalculations.findNearestPointIndex(
                                points = points,
                                query = worldPt,
                                thresholdMm = touchRadiusMm
                            )

                            if (nearest != null) {
                                draggingPointIndex = nearest
                                onSelectPoint(nearest)
                            } else {
                                draggingPointIndex = null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val curDrag = draggingPointIndex
                            if (curDrag != null && curDrag in points.indices && activeTool != CanvasTool.PAN_ZOOM) {
                                // Geser titik sudut terpilih
                                val oldPt = points[curDrag]
                                val dxMm = dragAmount.x / zoomScale
                                val dyMm = -dragAmount.y / zoomScale // Cartesian +Y atas
                                val rawNewX = oldPt.x + dxMm
                                val rawNewY = oldPt.y + dyMm
                                val newX = snapValue(rawNewX)
                                val newY = snapValue(rawNewY)

                                val updatedPt = Point2D(newX, newY)
                                cursorPositionMm = updatedPt
                                onCursorCoordinateChanged?.invoke(updatedPt)

                                if (newX != oldPt.x || newY != oldPt.y) {
                                    val updatedList = points.toMutableList()
                                    updatedList[curDrag] = updatedPt
                                    onPointsChanged(updatedList)
                                }
                            } else {
                                // Pan kanvas
                                panOffset += dragAmount
                            }
                        },
                        onDragEnd = {
                            draggingPointIndex = null
                        },
                        onDragCancel = {
                            draggingPointIndex = null
                        }
                    )
                }
                // Gesture 3: Pinch to Zoom and Pan
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        panOffset += pan
                        zoomScale = (zoomScale * zoom).coerceIn(0.5f, 25f)
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f + panOffset.x
            val centerY = canvasHeight / 2f + panOffset.y

            // 1. GAMBAR GRID KOORDINAT
            drawCoordinateGrid(
                canvasSize = size,
                centerX = centerX,
                centerY = centerY,
                zoomScale = zoomScale,
                gridSnapMm = gridSnapMm
            )

            // 2. GAMBAR SUMBU UTAMA (X & Y AXES DENGAN PANAH DAN ANGKA)
            drawAxes(
                canvasSize = size,
                centerX = centerX,
                centerY = centerY,
                zoomScale = zoomScale
            )

            // 3. GAMBAR RONGGA DINDING ALUMINIUM (HOLLOW WALL PREVIEW)
            if (points.size >= 3 && wallThicknessMm > 0f) {
                drawHollowProfilePreview(
                    points = points,
                    isClosed = isClosed,
                    wallThicknessMm = wallThicknessMm,
                    centerX = centerX,
                    centerY = centerY,
                    zoomScale = zoomScale
                )
            }

            // 4. GAMBAR KONTUR LUAR UTAMA (PROFILE CONTOUR STROKE)
            if (points.size >= 2) {
                drawProfileContour(
                    points = points,
                    isClosed = isClosed,
                    centerX = centerX,
                    centerY = centerY,
                    zoomScale = zoomScale
                )

                // 5. GAMBAR CALLOUT DIMENSI SEGMEN (PANJANG SISI DALAM MM)
                drawSegmentDimensions(
                    points = points,
                    isClosed = isClosed,
                    centerX = centerX,
                    centerY = centerY,
                    zoomScale = zoomScale
                )
            }

            // 6. GAMBAR TITIK SUDUT (VERTICES HANDLES & LABELS)
            drawVertices(
                points = points,
                selectedPointIndex = selectedPointIndex,
                centerX = centerX,
                centerY = centerY,
                zoomScale = zoomScale
            )

            // 7. GAMBAR INDIKATOR TITIK ASAL (ORIGIN 0,0)
            drawOriginMarker(centerX, centerY)
        }

        // Floating Control Bar: Zoom In, Zoom Out, Reset Center View
        CanvasViewportControls(
            zoomScale = zoomScale,
            onZoomIn = { zoomScale = (zoomScale * 1.25f).coerceAtMost(25f) },
            onZoomOut = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.5f) },
            onResetView = {
                panOffset = Offset.Zero
                zoomScale = 3.5f
            },
            onFitToBounds = {
                if (points.isNotEmpty()) {
                    val bbox = ProfileCalculations.computeBoundingBox(points)
                    val widthMm = bbox.width.coerceAtLeast(40f)
                    val heightMm = bbox.height.coerceAtLeast(40f)
                    // Scale agar pas di tengah kanvas dengan padding 25%
                    val targetScaleX = 600f / (widthMm * 1.5f)
                    val targetScaleY = 500f / (heightMm * 1.5f)
                    zoomScale = min(targetScaleX, targetScaleY).coerceIn(1f, 15f)
                    val midMmX = (bbox.minX + bbox.maxX) / 2f
                    val midMmY = (bbox.minY + bbox.maxY) / 2f
                    panOffset = Offset(-midMmX * zoomScale, midMmY * zoomScale)
                } else {
                    panOffset = Offset.Zero
                    zoomScale = 3.5f
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // Overlay status kursor koordinat real-time di sudut kiri bawah
        if (cursorPositionMm != null || selectedPointIndex != null) {
            val displayPt = if (selectedPointIndex != null && selectedPointIndex in points.indices) {
                points[selectedPointIndex]
            } else {
                cursorPositionMm
            }
            if (displayPt != null) {
                Surface(
                    color = Color(0xCC0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .testTag("canvas_coordinate_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8))
                        )
                        Text(
                            text = "X: ${String.format("%.1f", displayPt.x)} mm  |  Y: ${String.format("%.1f", displayPt.y)} mm",
                            color = Color(0xFFF8FAFC),
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Menggambar grid garis koordinat (minor & major grid lines).
 */
private fun DrawScope.drawCoordinateGrid(
    canvasSize: androidx.compose.ui.geometry.Size,
    centerX: Float,
    centerY: Float,
    zoomScale: Float,
    gridSnapMm: Float
) {
    // Tentukan interval garis grid sesuai level zoom
    val stepMm = when {
        zoomScale >= 10f -> 5f
        zoomScale >= 4f -> 10f
        zoomScale >= 1.5f -> 20f
        else -> 50f
    }
    val majorStepMm = stepMm * 5f
    val stepPx = stepMm * zoomScale

    val minMmX = (-centerX) / zoomScale
    val maxMmX = (canvasSize.width - centerX) / zoomScale
    val minMmY = (centerY - canvasSize.height) / zoomScale
    val maxMmY = centerY / zoomScale

    val startXIndex = floor(minMmX / stepMm).toInt()
    val endXIndex = ceil(maxMmX / stepMm).toInt()
    val startYIndex = floor(minMmY / stepMm).toInt()
    val endYIndex = ceil(maxMmY / stepMm).toInt()

    val minorColor = Color(0xFF1E293B)
    val majorColor = Color(0xFF334155)

    // Garis Vertikal (Sumbu X)
    for (i in startXIndex..endXIndex) {
        val mm = i * stepMm
        if (abs(mm) < 0.001f) continue // Dilewati karena digambar khusus oleh drawAxes
        val x = centerX + mm * zoomScale
        val isMajor = abs(mm % majorStepMm) < 0.001f
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(x, 0f),
            end = Offset(x, canvasSize.height),
            strokeWidth = if (isMajor) 1.5f else 1f
        )
    }

    // Garis Horizontal (Sumbu Y)
    for (j in startYIndex..endYIndex) {
        val mm = j * stepMm
        if (abs(mm) < 0.001f) continue // Dilewati karena digambar khusus oleh drawAxes
        val y = centerY - mm * zoomScale
        val isMajor = abs(mm % majorStepMm) < 0.001f
        drawLine(
            color = if (isMajor) majorColor else minorColor,
            start = Offset(0f, y),
            end = Offset(canvasSize.width, y),
            strokeWidth = if (isMajor) 1.5f else 1f
        )
    }
}

/**
 * Menggambar sumbu koordinat utama X (Cyan) dan Y (Emerald) dengan angka penunjuk mm.
 */
private fun DrawScope.drawAxes(
    canvasSize: androidx.compose.ui.geometry.Size,
    centerX: Float,
    centerY: Float,
    zoomScale: Float
) {
    val axisColorX = Color(0xFF38BDF8)
    val axisColorY = Color(0xFF34D399)

    // Sumbu X Utama (Y = 0)
    if (centerY in 0f..canvasSize.height) {
        drawLine(
            color = axisColorX,
            start = Offset(0f, centerY),
            end = Offset(canvasSize.width, centerY),
            strokeWidth = 2.5f
        )
    }

    // Sumbu Y Utama (X = 0)
    if (centerX in 0f..canvasSize.width) {
        drawLine(
            color = axisColorY,
            start = Offset(centerX, 0f),
            end = Offset(centerX, canvasSize.height),
            strokeWidth = 2.5f
        )
    }

    // Angka penunjuk skala pada sumbu
    val stepMm = when {
        zoomScale >= 8f -> 20f
        zoomScale >= 3f -> 50f
        else -> 100f
    }

    val paint = Paint().apply {
        color = android.graphics.Color.argb(200, 148, 163, 184)
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }

    val minMmX = (-centerX) / zoomScale
    val maxMmX = (canvasSize.width - centerX) / zoomScale
    val startX = (floor(minMmX / stepMm) * stepMm).toInt()
    val endX = (ceil(maxMmX / stepMm) * stepMm).toInt()

    for (xMm in startX..endX step stepMm.toInt()) {
        if (xMm == 0) continue
        val px = centerX + xMm * zoomScale
        val py = (centerY + 32f).coerceIn(32f, canvasSize.height - 10f)
        drawContext.canvas.nativeCanvas.drawText("$xMm", px - 18f, py, paint)
    }

    val minMmY = (centerY - canvasSize.height) / zoomScale
    val maxMmY = centerY / zoomScale
    val startY = (floor(minMmY / stepMm) * stepMm).toInt()
    val endY = (ceil(maxMmY / stepMm) * stepMm).toInt()

    for (yMm in startY..endY step stepMm.toInt()) {
        if (yMm == 0) continue
        val py = centerY - yMm * zoomScale
        val px = (centerX + 12f).coerceIn(10f, canvasSize.width - 50f)
        drawContext.canvas.nativeCanvas.drawText("$yMm", px, py + 8f, paint)
    }
}

/**
 * Menggambar visualisasi penampang berongga (Hollow Wall Preview) aluminium.
 */
private fun DrawScope.drawHollowProfilePreview(
    points: List<Point2D>,
    isClosed: Boolean,
    wallThicknessMm: Float,
    centerX: Float,
    centerY: Float,
    zoomScale: Float
) {
    if (points.size < 3) return

    val n = points.size
    // Hitung orientasi (Clockwise vs Counter-Clockwise)
    var signedArea = 0.0
    for (i in 0 until n) {
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        signedArea += (p2.x - p1.x) * (p2.y + p1.y)
    }
    val isClockwise = signedArea > 0

    // Hitung titik dinding dalam (inner contour)
    val innerPoints = mutableListOf<Point2D>()
    for (i in 0 until n) {
        val prev = if (i == 0) {
            if (isClosed) points[n - 1] else points[0]
        } else points[i - 1]

        val curr = points[i]
        val next = if (i == n - 1) {
            if (isClosed) points[0] else points[n - 1]
        } else points[i + 1]

        val d1 = (curr - prev).normalized()
        val d2 = (next - curr).normalized()

        val n1 = if (isClockwise) Point2D(-d1.y, d1.x) else Point2D(d1.y, -d1.x)
        val n2 = if (isClockwise) Point2D(-d2.y, d2.x) else Point2D(d2.y, -d2.x)

        var avgNormal = (n1 + n2).normalized()
        if (avgNormal.length() < 0.001f) avgNormal = n1

        val dot = avgNormal.x * n1.x + avgNormal.y * n1.y
        val scale = if (dot > 0.2f) (wallThicknessMm / dot).coerceAtMost(wallThicknessMm * 2.2f) else wallThicknessMm
        innerPoints.add(curr + (avgNormal * scale))
    }

    // Path gabungan EvenOdd: Mengarsir dinding aluminium dan membiarkan rongga tengah kosong
    val combinedPath = Path().apply {
        fillType = PathFillType.EvenOdd

        // Kontur luar
        val p0Screen = Offset(centerX + points[0].x * zoomScale, centerY - points[0].y * zoomScale)
        moveTo(p0Screen.x, p0Screen.y)
        for (i in 1 until points.size) {
            val pScreen = Offset(centerX + points[i].x * zoomScale, centerY - points[i].y * zoomScale)
            lineTo(pScreen.x, pScreen.y)
        }
        if (isClosed) close()

        // Kontur dalam
        if (isClosed && innerPoints.isNotEmpty()) {
            val in0Screen = Offset(centerX + innerPoints[0].x * zoomScale, centerY - innerPoints[0].y * zoomScale)
            moveTo(in0Screen.x, in0Screen.y)
            for (i in 1 until innerPoints.size) {
                val inScreen = Offset(centerX + innerPoints[i].x * zoomScale, centerY - innerPoints[i].y * zoomScale)
                lineTo(inScreen.x, inScreen.y)
            }
            close()
        }
    }

    // Arsir bidang padat aluminium
    drawPath(
        path = combinedPath,
        color = Color(0x3538BDF8)
    )

    // Garis garis tepi dinding dalam
    val innerStrokePath = Path().apply {
        if (innerPoints.isNotEmpty()) {
            val start = Offset(centerX + innerPoints[0].x * zoomScale, centerY - innerPoints[0].y * zoomScale)
            moveTo(start.x, start.y)
            for (i in 1 until innerPoints.size) {
                val p = Offset(centerX + innerPoints[i].x * zoomScale, centerY - innerPoints[i].y * zoomScale)
                lineTo(p.x, p.y)
            }
            if (isClosed) close()
        }
    }

    drawPath(
        path = innerStrokePath,
        color = Color(0x8093C5FD),
        style = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
    )
}

/**
 * Menggambar garis kontur utama profil luar.
 */
private fun DrawScope.drawProfileContour(
    points: List<Point2D>,
    isClosed: Boolean,
    centerX: Float,
    centerY: Float,
    zoomScale: Float
) {
    val path = Path().apply {
        val p0 = Offset(centerX + points[0].x * zoomScale, centerY - points[0].y * zoomScale)
        moveTo(p0.x, p0.y)
        for (i in 1 until points.size) {
            val p = Offset(centerX + points[i].x * zoomScale, centerY - points[i].y * zoomScale)
            lineTo(p.x, p.y)
        }
        if (isClosed) close()
    }

    drawPath(
        path = path,
        color = Color(0xFF38BDF8),
        style = Stroke(
            width = 3.5f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Menggambar dimensi panjang setiap segmen (dalam mm) di atas garis penampang.
 */
private fun DrawScope.drawSegmentDimensions(
    points: List<Point2D>,
    isClosed: Boolean,
    centerX: Float,
    centerY: Float,
    zoomScale: Float
) {
    val count = if (isClosed) points.size else points.size - 1
    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    val bgPaint = Paint().apply {
        color = android.graphics.Color.argb(220, 15, 23, 42)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    for (i in 0 until count) {
        val p1 = points[i]
        val p2 = points[(i + 1) % points.size]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val lengthMm = sqrt(dx * dx + dy * dy)
        if (lengthMm < 1f) continue

        // Titik tengah segmen
        val midX = (p1.x + p2.x) / 2f
        val midY = (p1.y + p2.y) / 2f
        val midPx = centerX + midX * zoomScale
        val midPy = centerY - midY * zoomScale

        val text = "${String.format("%.1f", lengthMm)} mm"
        val textWidth = paint.measureText(text)

        // Gambar background pill kecil untuk keterbacaan teks
        drawContext.canvas.nativeCanvas.drawRoundRect(
            midPx - textWidth / 2f - 8f,
            midPy - 18f,
            midPx + textWidth / 2f + 8f,
            midPy + 10f,
            6f,
            6f,
            bgPaint
        )

        drawContext.canvas.nativeCanvas.drawText(
            text,
            midPx - textWidth / 2f,
            midPy + 4f,
            paint
        )
    }
}

/**
 * Menggambar titik-titik sudut (vertices handles) dengan nomor P1, P2... dan highlight saat dipilih.
 */
private fun DrawScope.drawVertices(
    points: List<Point2D>,
    selectedPointIndex: Int?,
    centerX: Float,
    centerY: Float,
    zoomScale: Float
) {
    val labelPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }

    for (i in points.indices) {
        val p = points[i]
        val px = centerX + p.x * zoomScale
        val py = centerY - p.y * zoomScale
        val isSelected = (i == selectedPointIndex)

        // Efek glowing ring jika dipilih
        if (isSelected) {
            drawCircle(
                color = Color(0x60F59E0B),
                radius = 24f,
                center = Offset(px, py)
            )
            drawCircle(
                color = Color(0xFFF59E0B),
                radius = 16f,
                center = Offset(px, py),
                style = Stroke(width = 3.5f)
            )
        }

        // Handle titik putih dengan border biru
        drawCircle(
            color = if (isSelected) Color(0xFFF59E0B) else Color(0xFF38BDF8),
            radius = 10f,
            center = Offset(px, py)
        )
        drawCircle(
            color = Color.White,
            radius = 6f,
            center = Offset(px, py)
        )

        // Label nomor titik P1, P2...
        val label = "P${i + 1}"
        drawContext.canvas.nativeCanvas.drawText(
            label,
            px + 14f,
            py - 10f,
            labelPaint
        )
    }
}

/**
 * Menggambar tanda titik asal (0,0) Origin.
 */
private fun DrawScope.drawOriginMarker(centerX: Float, centerY: Float) {
    drawCircle(
        color = Color(0xFFF59E0B),
        radius = 5f,
        center = Offset(centerX, centerY)
    )
    drawCircle(
        color = Color(0xFFF59E0B),
        radius = 12f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1.5f)
    )
}

/**
 * Tombol kontrol zoom in/out, fit to screen, dan reset center.
 */
@Composable
private fun CanvasViewportControls(
    zoomScale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    onFitToBounds: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xEE1E293B),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        shadowElevation = 6.dp,
        modifier = modifier.testTag("canvas_viewport_controls")
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_zoom_in")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_zoom_out")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
            Divider(color = Color(0xFF334155), modifier = Modifier.width(28.dp))
            IconButton(
                onClick = onFitToBounds,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_fit_to_bounds")
            ) {
                Icon(Icons.Default.FitScreen, contentDescription = "Fit to Screen", tint = Color(0xFF38BDF8))
            }
            IconButton(
                onClick = onResetView,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_reset_view")
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Center (0,0)", tint = Color(0xFFF59E0B))
            }
        }
    }
}
