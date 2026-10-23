package com.example.ui.canvas

import com.example.mesh3d.Point2D
import kotlin.math.*

/**
 * Pilihan tool interaksi pada 2D Canvas.
 */
enum class CanvasTool(val title: String, val description: String) {
    ADD_POINT("Tambah Titik", "Ketuk grid untuk menambah titik sudut profil"),
    SELECT_MOVE("Pilih & Geser", "Pilih dan geser titik sudut secara presisi"),
    INSERT_POINT("Sisipkan Titik", "Ketuk pada segmen garis untuk menyisipkan titik baru"),
    DELETE_POINT("Hapus Titik", "Ketuk titik sudut untuk menghapusnya"),
    PAN_ZOOM("Navigasi", "Geser kanvas atau cubit untuk zoom")
}

/**
 * Pilihan resolusi snapping grid koordinat (dalam millimeter).
 */
enum class GridSnapOption(val mm: Float, val label: String) {
    SNAP_OFF(0f, "Bebas (0 mm)"),
    SNAP_1MM(1f, "1 mm"),
    SNAP_5MM(5f, "5 mm"),
    SNAP_10MM(10f, "10 mm"),
    SNAP_20MM(20f, "20 mm")
}

/**
 * Bounding Box 2D untuk penampang ekstrusi aluminium.
 */
data class ProfileBoundingBox(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
) {
    val width: Float get() = (maxX - minX).coerceAtLeast(0f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0f)
}

/**
 * Hasil kalkulasi teknis penampang aluminium.
 */
data class ProfileMetrics(
    val pointCount: Int,
    val isClosed: Boolean,
    val boundingBox: ProfileBoundingBox,
    val perimeterMm: Float,
    val enclosedAreaMm2: Float,
    val aluminiumAreaMm2: Float,
    val estimatedWeightKgPerMeter: Float
)

/**
 * Preset profil standar industri ekstrusi aluminium.
 */
data class ProfilePreset(
    val name: String,
    val category: String,
    val description: String,
    val thicknessMm: Float,
    val isClosed: Boolean,
    val points: List<Point2D>
)

object ProfilePresetRegistry {
    val presets = listOf(
        ProfilePreset(
            name = "Hollow Box 40x40",
            category = "Commodities",
            description = "Pipa kotak aluminium 40 mm x 40 mm tebal 1.2 mm",
            thicknessMm = 1.2f,
            isClosed = true,
            points = listOf(
                Point2D(0f, 0f),
                Point2D(40f, 0f),
                Point2D(40f, 40f),
                Point2D(0f, 40f)
            )
        ),
        ProfilePreset(
            name = "Hollow Rect 40x80",
            category = "Commodities",
            description = "Pipa persegi panjang 40 mm x 80 mm tebal 1.5 mm",
            thicknessMm = 1.5f,
            isClosed = true,
            points = listOf(
                Point2D(0f, 0f),
                Point2D(80f, 0f),
                Point2D(80f, 40f),
                Point2D(0f, 40f)
            )
        ),
        ProfilePreset(
            name = "Siku L-Angle 25x25",
            category = "Commodities",
            description = "Profil siku L equal angle 25 mm x 25 mm tebal 2.0 mm",
            thicknessMm = 2.0f,
            isClosed = false,
            points = listOf(
                Point2D(0f, 25f),
                Point2D(0f, 0f),
                Point2D(25f, 0f)
            )
        ),
        ProfilePreset(
            name = "Kanal U / C-Channel 38x19",
            category = "Commodities",
            description = "Kanal U standar 38 mm x 19 mm tebal 1.5 mm",
            thicknessMm = 1.5f,
            isClosed = false,
            points = listOf(
                Point2D(0f, 19f),
                Point2D(0f, 0f),
                Point2D(38f, 0f),
                Point2D(38f, 19f)
            )
        ),
        ProfilePreset(
            name = "Kusen 3\" Open Back",
            category = "Shop Front",
            description = "Profil kusen aluminium 3 inci (76x38 mm) model open back",
            thicknessMm = 1.3f,
            isClosed = true,
            points = listOf(
                Point2D(0f, 0f),
                Point2D(76f, 0f),
                Point2D(76f, 38f),
                Point2D(56f, 38f),
                Point2D(56f, 15f),
                Point2D(0f, 15f)
            )
        ),
        ProfilePreset(
            name = "T-Slot Extrusion 30x30",
            category = "Shop Front",
            description = "Profil modular T-Slot 30 mm x 30 mm",
            thicknessMm = 1.8f,
            isClosed = true,
            points = listOf(
                Point2D(0f, 0f),
                Point2D(10f, 0f),
                Point2D(10f, 6f),
                Point2D(8f, 6f),
                Point2D(8f, 10f),
                Point2D(10f, 10f),
                Point2D(10f, 20f),
                Point2D(8f, 20f),
                Point2D(8f, 24f),
                Point2D(10f, 24f),
                Point2D(10f, 30f),
                Point2D(0f, 30f)
            )
        )
    )
}

/**
 * Utilitas kalkulasi geometri dan mekanika aluminium.
 */
object ProfileCalculations {

    /**
     * Menghitung Bounding Box dari daftar titik koordinat.
     */
    fun computeBoundingBox(points: List<Point2D>): ProfileBoundingBox {
        if (points.isEmpty()) return ProfileBoundingBox(0f, 0f, 0f, 0f)
        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y

        for (p in points) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        return ProfileBoundingBox(minX, maxX, minY, maxY)
    }

    /**
     * Menghitung total keliling / panjang kontur (mm).
     */
    fun computePerimeter(points: List<Point2D>, isClosed: Boolean): Float {
        if (points.size < 2) return 0f
        var total = 0f
        val count = if (isClosed) points.size else points.size - 1
        for (i in 0 until count) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            total += sqrt(dx * dx + dy * dy)
        }
        return total
    }

    /**
     * Menghitung luas poligon tertutup menggunakan rumus Shoelace (mm²).
     */
    fun computeShoelaceArea(points: List<Point2D>): Float {
        if (points.size < 3) return 0f
        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            area += (p1.x * p2.y - p2.x * p1.y)
        }
        return (abs(area) * 0.5).toFloat()
    }

    /**
     * Menghitung estimasi luas penampang bahan aluminium padat (mm²)
     * dengan mempertimbangkan ketebalan dinding profil (wall thickness).
     */
    fun computeAluminiumArea(points: List<Point2D>, thicknessMm: Float, isClosed: Boolean): Float {
        val t = thicknessMm.coerceAtLeast(0.5f)
        val perimeter = computePerimeter(points, isClosed)
        if (perimeter <= 0.01f) return 0f

        return if (isClosed) {
            val enclosed = computeShoelaceArea(points)
            if (enclosed > 0f) {
                // Untuk penampang berongga (hollow tube): Area = Luas luar - Luas rongga dalam
                // Pendekatan analitis presisi: Luas dinding ~ Keliling * Tebal - Sudut overlap
                val approxArea = perimeter * t - (4f * t * t)
                approxArea.coerceIn(t * 10f, enclosed * 0.95f)
            } else {
                perimeter * t
            }
        } else {
            // Profil terbuka (L-Angle, C-Channel, Plat): Area = Panjang garis * Tebal
            perimeter * t
        }
    }

    /**
     * Menghitung estimasi berat aluminium per meter (kg/m).
     * Massa jenis aluminium padat standar: 2.70 g/cm³ = 0.00270 kg/m per mm² luas.
     */
    fun computeWeightPerMeter(aluminiumAreaMm2: Float, densityGperCm3: Float = 2.70f): Float {
        // Area (mm²) * 1000 mm (1m) = Volume (mm³)
        // Volume (cm³) = Volume (mm³) / 1000 = Area (mm²) cm³
        // Berat (gram) = Area (mm²) * density (g/cm³)
        // Berat (kg) = Berat (gram) / 1000 = Area (mm²) * density / 1000
        return (aluminiumAreaMm2 * densityGperCm3 / 1000f).coerceAtLeast(0f)
    }

    /**
     * Menghitung ringkasan metrik lengkap penampang profil.
     */
    fun computeMetrics(points: List<Point2D>, thicknessMm: Float, isClosed: Boolean): ProfileMetrics {
        val bbox = computeBoundingBox(points)
        val perimeter = computePerimeter(points, isClosed)
        val enclosedArea = if (isClosed && points.size >= 3) computeShoelaceArea(points) else 0f
        val alumArea = computeAluminiumArea(points, thicknessMm, isClosed)
        val weight = computeWeightPerMeter(alumArea)

        return ProfileMetrics(
            pointCount = points.size,
            isClosed = isClosed,
            boundingBox = bbox,
            perimeterMm = perimeter,
            enclosedAreaMm2 = enclosedArea,
            aluminiumAreaMm2 = alumArea,
            estimatedWeightKgPerMeter = weight
        )
    }

    /**
     * Mencari indeks titik terdekat dari posisi pointer (dalam mm koordinat).
     */
    fun findNearestPointIndex(points: List<Point2D>, query: Point2D, thresholdMm: Float): Int? {
        if (points.isEmpty()) return null
        var bestIndex: Int? = null
        var bestDistSq = thresholdMm * thresholdMm

        for (i in points.indices) {
            val p = points[i]
            val dx = p.x - query.x
            val dy = p.y - query.y
            val dSq = dx * dx + dy * dy
            if (dSq <= bestDistSq) {
                bestDistSq = dSq
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Mencari segmen garis terdekat untuk penyisipan titik baru.
     * Mengembalikan indeks i dari segmen (i -> i+1), atau null jika di luar threshold.
     */
    fun findNearestSegmentIndex(
        points: List<Point2D>,
        query: Point2D,
        isClosed: Boolean,
        thresholdMm: Float
    ): Int? {
        if (points.size < 2) return null
        val count = if (isClosed) points.size else points.size - 1
        var bestIndex: Int? = null
        var minDistance = thresholdMm

        for (i in 0 until count) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            val dist = pointToSegmentDistance(query, p1, p2)
            if (dist < minDistance) {
                minDistance = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    /**
     * Jarak tegak lurus titik ke segmen garis (p1, p2).
     */
    private fun pointToSegmentDistance(p: Point2D, p1: Point2D, p2: Point2D): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 0.0001f) {
            val d1x = p.x - p1.x
            val d1y = p.y - p1.y
            return sqrt(d1x * d1x + d1y * d1y)
        }
        val t = (((p.x - p1.x) * dx + (p.y - p1.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = p1.x + t * dx
        val projY = p1.y + t * dy
        val px = p.x - projX
        val py = p.y - projY
        return sqrt(px * px + py * py)
    }
}
