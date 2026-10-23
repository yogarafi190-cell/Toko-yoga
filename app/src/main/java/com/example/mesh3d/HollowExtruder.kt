package com.example.mesh3d

import android.content.Context
import java.io.File
import java.io.FileWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object HollowExtruder {

    /**
     * Menghasilkan mesh 3D Hollow (berongga di tengah) dari kumpulan koordinat 2D (Point2D).
     *
     * @param contour Kumpulan titik koordinat X,Y penampang luar profil (mm).
     * @param thickness Ketebalan dinding aluminium ekstrusi (mm).
     * @param panjangBatang Panjang ekstrusi sumbu Z (mm).
     * @param isClosed Apakah poligon tertutup (true untuk box/tube berongga).
     */
    fun generateHollowMesh(
        contour: List<Point2D>,
        thickness: Double,
        panjangBatang: Double,
        isClosed: Boolean = true
    ): Mesh3DData {
        if (contour.size < 3) {
            return Mesh3DData(emptyList(), emptyList(), emptyList(), emptyList())
        }

        val wallThickness = thickness.toFloat().coerceAtLeast(0.5f)
        val lengthZ = panjangBatang.toFloat().coerceAtLeast(10f)

        // 1. Hitung titik dinding dalam (inner loop) dengan inward normal offset
        val (outerPoints, innerPoints) = computeHollowContours(contour, wallThickness, isClosed)

        val vertices = mutableListOf<Vertex3D>()
        val normals = mutableListOf<Vertex3D>()
        val triangles = mutableListOf<Triangle3D>()
        val indices = mutableListOf<Int>()

        val n = outerPoints.size
        if (n < 3 || innerPoints.size != n) {
            return Mesh3DData(emptyList(), emptyList(), emptyList(), emptyList())
        }

        // Susunan Vertex:
        // 0 .. n-1         : Outer Front (Z = 0)
        // n .. 2n-1        : Inner Front (Z = 0)
        // 2n .. 3n-1       : Outer Back  (Z = lengthZ)
        // 3n .. 4n-1       : Inner Back  (Z = lengthZ)

        // Titik Outer Front (Z = 0)
        for (p in outerPoints) vertices.add(Vertex3D(p.x, p.y, 0f))
        // Titik Inner Front (Z = 0)
        for (p in innerPoints) vertices.add(Vertex3D(p.x, p.y, 0f))
        // Titik Outer Back (Z = lengthZ)
        for (p in outerPoints) vertices.add(Vertex3D(p.x, p.y, lengthZ))
        // Titik Inner Back (Z = lengthZ)
        for (p in innerPoints) vertices.add(Vertex3D(p.x, p.y, lengthZ))

        fun addQuad(i1: Int, i2: Int, i3: Int, i4: Int, isInner: Boolean = false) {
            val v1 = vertices[i1]
            val v2 = vertices[i2]
            val v3 = vertices[i3]
            val v4 = vertices[i4]

            val edge1 = v2 - v1
            val edge2 = v3 - v1
            var norm = edge1.cross(edge2).normalized()
            if (isInner) {
                // Balikkan normal agar mengarah ke rongga dalam
                norm = norm * -1f
            }

            // Segitiga 1: v1, v2, v3
            triangles.add(Triangle3D(v1, v2, v3, norm, isInner))
            indices.add(i1)
            indices.add(i2)
            indices.add(i3)
            normals.add(norm)

            // Segitiga 2: v1, v3, v4
            triangles.add(Triangle3D(v1, v3, v4, norm, isInner))
            indices.add(i1)
            indices.add(i3)
            indices.add(i4)
            normals.add(norm)
        }

        val count = if (isClosed) n else n - 1

        for (i in 0 until count) {
            val next = (i + 1) % n

            val outF1 = i
            val outF2 = next
            val inF1 = i + n
            val inF2 = next + n

            val outB1 = i + 2 * n
            val outB2 = next + 2 * n
            val inB1 = i + 3 * n
            val inB2 = next + 3 * n

            // 1. Dinding Luar (Outer Side Walls) sepanjang sumbu Z
            addQuad(outF1, outF2, outB2, outB1, isInner = false)

            // 2. Dinding Dalam Berongga (Inner Hollow Cavity Walls) sepanjang sumbu Z
            addQuad(inF1, inB1, inB2, inF2, isInner = true)

            // 3. Ring Tutup Depan (Front Ring Cap di Z = 0)
            addQuad(outF1, inF1, inF2, outF2, isInner = false)

            // 4. Ring Tutup Belakang (Back Ring Cap di Z = lengthZ)
            addQuad(outB1, outB2, inB2, inB1, isInner = false)
        }

        return Mesh3DData(vertices, normals, triangles, indices)
    }

    /**
     * Menghitung poligon dalam (inner loop) berjarak `thickness` dari poligon luar.
     */
    private fun computeHollowContours(
        points: List<Point2D>,
        thickness: Float,
        isClosed: Boolean
    ): Pair<List<Point2D>, List<Point2D>> {
        val n = points.size
        val inner = ArrayList<Point2D>(n)

        // Tentukan arah orientasi (Clockwise / Counter-Clockwise)
        var signedArea = 0.0
        for (i in 0 until n) {
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            signedArea += (p2.x - p1.x) * (p2.y + p1.y)
        }
        val isClockwise = signedArea > 0

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

            // Normal garis
            val n1 = if (isClockwise) Point2D(-d1.y, d1.x) else Point2D(d1.y, -d1.x)
            val n2 = if (isClockwise) Point2D(-d2.y, d2.x) else Point2D(d2.y, -d2.x)

            var avgNormal = (n1 + n2).normalized()
            if (avgNormal.length() < 0.001f) {
                avgNormal = n1
            }

            // Miter limit untuk mencegah runcing berlebihan di sudut tajam
            val dot = avgNormal.x * n1.x + avgNormal.y * n1.y
            val scale = if (dot > 0.2f) (thickness / dot).coerceAtMost(thickness * 2.5f) else thickness

            val inPoint = curr + (avgNormal * scale)
            inner.add(inPoint)
        }

        return Pair(points, inner)
    }

    /**
     * Mengekspor Mesh 3D ke file lokal berformat Wavefront .obj di penyimpanan HP
     * dan mengembalikan absolute path file tersebut.
     */
    fun exportToObjFile(
        context: Context,
        mesh: Mesh3DData,
        sectionName: String
    ): String {
        try {
            val dir = File(context.filesDir, "models_3d")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val sanitizedName = sectionName.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val fileName = "profile_${sanitizedName}_${System.currentTimeMillis()}.obj"
            val file = File(dir, fileName)

            FileWriter(file).use { writer ->
                writer.write("# Toko Yoga 3D Hollow Extruder\n")
                writer.write("# Profil Aluminium: $sectionName\n")
                writer.write("o $sanitizedName\n\n")

                // Tulis Vertices (v x y z)
                for (v in mesh.vertices) {
                    writer.write("v ${v.x} ${v.y} ${v.z}\n")
                }
                writer.write("\n")

                // Tulis Normals (vn x y z)
                for (t in mesh.triangles) {
                    val n = t.normal
                    writer.write("vn ${n.x} ${n.y} ${n.z}\n")
                }
                writer.write("\n")

                // Tulis Faces (f v1//vn v2//vn v3//vn) - 1-based index
                for (i in mesh.triangles.indices) {
                    val idx1 = mesh.indices[i * 3] + 1
                    val idx2 = mesh.indices[i * 3 + 1] + 1
                    val idx3 = mesh.indices[i * 3 + 2] + 1
                    val nIdx = i + 1
                    writer.write("f $idx1//$nIdx $idx2//$nIdx $idx3//$nIdx\n")
                }
            }

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    // Preset profil standar aluminium untuk kemudahan user
    fun getPresetProfile(type: String, width: Float = 50f, height: Float = 50f): List<Point2D> {
        return when (type) {
            "Hollow Box" -> listOf(
                Point2D(0f, 0f),
                Point2D(width, 0f),
                Point2D(width, height),
                Point2D(0f, height)
            )
            "Shop Front Open Back" -> listOf(
                Point2D(0f, 0f),
                Point2D(width, 0f),
                Point2D(width, height),
                Point2D(width * 0.7f, height),
                Point2D(width * 0.7f, height * 0.25f),
                Point2D(0f, height * 0.25f)
            )
            "Sliding Door Kusen" -> listOf(
                Point2D(0f, 0f),
                Point2D(width, 0f),
                Point2D(width, height * 0.6f),
                Point2D(width * 0.8f, height * 0.6f),
                Point2D(width * 0.8f, height),
                Point2D(width * 0.2f, height),
                Point2D(width * 0.2f, height * 0.6f),
                Point2D(0f, height * 0.6f)
            )
            "Curtain Wall Mullion" -> listOf(
                Point2D(width * 0.35f, 0f),
                Point2D(width * 0.65f, 0f),
                Point2D(width * 0.65f, height * 0.3f),
                Point2D(width, height * 0.3f),
                Point2D(width, height),
                Point2D(0f, height),
                Point2D(0f, height * 0.3f),
                Point2D(width * 0.35f, height * 0.3f)
            )
            else -> listOf(
                Point2D(0f, 0f),
                Point2D(width, 0f),
                Point2D(width, height),
                Point2D(0f, height)
            )
        }
    }
}
