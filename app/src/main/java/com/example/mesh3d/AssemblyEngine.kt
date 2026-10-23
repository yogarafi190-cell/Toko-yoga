package com.example.mesh3d

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

data class AssemblyPart(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val profileType: String = "Hollow Box",
    val lengthMm: Float = 1000f,
    val widthMm: Float = 50f,
    val heightMm: Float = 50f,
    val thicknessMm: Float = 1.5f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotPitch: Float = 0f, // Derajat putar sumbu X
    val rotYaw: Float = 0f,   // Derajat putar sumbu Y
    val rotRoll: Float = 0f,  // Derajat putar sumbu Z
    val color: String = "Silver",
    val isVisible: Boolean = true
)

data class AssemblySceneMesh(
    val totalPieces: Int,
    val totalMaterialLengthMm: Double,
    val totalVerticesCount: Int,
    val vertices: List<Vertex3D>,
    val triangles: List<Triangle3D>,
    val indices: List<Int>,
    val parts: List<AssemblyPart>
)

object AssemblyEngine {

    /**
     * Membangun mesh gabungan multi-objek untuk seluruh rangkaian rangka aluminium.
     * Menerapkan transformasi rotasi 3D (Euler angles) dan translasi posisi independen tiap part.
     */
    fun buildAssemblyMesh(parts: List<AssemblyPart>): AssemblySceneMesh {
        val visibleParts = parts.filter { it.isVisible }
        if (visibleParts.isEmpty()) {
            return AssemblySceneMesh(
                totalPieces = 0,
                totalMaterialLengthMm = 0.0,
                totalVerticesCount = 0,
                vertices = emptyList(),
                triangles = emptyList(),
                indices = emptyList(),
                parts = parts
            )
        }

        val allVertices = mutableListOf<Vertex3D>()
        val allTriangles = mutableListOf<Triangle3D>()
        val allIndices = mutableListOf<Int>()
        var totalMaterialLength = 0.0

        for (part in visibleParts) {
            totalMaterialLength += part.lengthMm.toDouble()

            // 1. Dapatkan kontur 2D dasar penampang profil
            val contour2D = HollowExtruder.getPresetProfile(
                type = part.profileType,
                width = part.widthMm,
                height = part.heightMm
            )

            // 2. Buat hollow extrusion lokal sepanjang panjang batang
            val localMesh = HollowExtruder.generateHollowMesh(
                contour = contour2D,
                thickness = part.thicknessMm.toDouble(),
                panjangBatang = part.lengthMm.toDouble(),
                isClosed = true
            )

            if (localMesh.vertices.isEmpty()) continue

            // 3. Matriks Transformasi (Rotasi Pitch/Yaw/Roll lalu Translasi Posisi)
            val radPitch = Math.toRadians(part.rotPitch.toDouble()).toFloat()
            val radYaw = Math.toRadians(part.rotYaw.toDouble()).toFloat()
            val radRoll = Math.toRadians(part.rotRoll.toDouble()).toFloat()

            val cosP = cos(radPitch); val sinP = sin(radPitch)
            val cosY = cos(radYaw);   val sinY = sin(radYaw)
            val cosR = cos(radRoll);  val sinR = sin(radRoll)

            fun transformPoint(v: Vertex3D): Vertex3D {
                // Rotasi Roll (Z)
                var x1 = v.x * cosR - v.y * sinR
                var y1 = v.x * sinR + v.y * cosR
                var z1 = v.z

                // Rotasi Pitch (X)
                var y2 = y1 * cosP - z1 * sinP
                var z2 = y1 * sinP + z1 * cosP
                var x2 = x1

                // Rotasi Yaw (Y)
                var x3 = x2 * cosY + z2 * sinY
                var z3 = -x2 * sinY + z2 * cosY
                var y3 = y2

                // Translasi Posisi
                return Vertex3D(
                    x = x3 + part.posX,
                    y = y3 + part.posY,
                    z = z3 + part.posZ
                )
            }

            fun transformNormal(n: Vertex3D): Vertex3D {
                // Normal hanya mengalami rotasi (tanpa translasi)
                val x1 = n.x * cosR - n.y * sinR
                val y1 = n.x * sinR + n.y * cosR
                val z1 = n.z

                val y2 = y1 * cosP - z1 * sinP
                val z2 = y1 * sinP + z1 * cosP
                val x2 = x1

                val x3 = x2 * cosY + z2 * sinY
                val z3 = -x2 * sinY + z2 * cosY
                val y3 = y2

                return Vertex3D(x3, y3, z3).normalized()
            }

            val startIndex = allVertices.size
            val transformedVertices = localMesh.vertices.map { transformPoint(it) }
            allVertices.addAll(transformedVertices)

            for (tri in localMesh.triangles) {
                allTriangles.add(
                    Triangle3D(
                        v1 = transformPoint(tri.v1),
                        v2 = transformPoint(tri.v2),
                        v3 = transformPoint(tri.v3),
                        normal = transformNormal(tri.normal),
                        isInner = tri.isInner
                    )
                )
            }

            for (idx in localMesh.indices) {
                allIndices.add(startIndex + idx)
            }
        }

        return AssemblySceneMesh(
            totalPieces = visibleParts.size,
            totalMaterialLengthMm = totalMaterialLength,
            totalVerticesCount = allVertices.size,
            vertices = allVertices,
            triangles = allTriangles,
            indices = allIndices,
            parts = parts
        )
    }

    /**
     * Preset Rangka Kusen Jendela Aluminium Standar (4 Batang)
     * Total panjang material = 2x lebarMm + 2x tinggiMm
     */
    fun createKusenJendelaPreset(lebarMm: Float = 800f, tinggiMm: Float = 1200f): List<AssemblyPart> {
        val profile = "Hollow Box"
        val profileSize = 40f
        val thickness = 1.35f

        return listOf(
            AssemblyPart(
                id = "tiang_kiri",
                name = "Tiang Vertikal Kiri",
                profileType = profile,
                lengthMm = tinggiMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = 0f,
                posY = 0f,
                posZ = 0f,
                rotPitch = 90f, // Tegak berdiri sumbu Y
                rotYaw = 0f,
                rotRoll = 0f,
                color = "Silver"
            ),
            AssemblyPart(
                id = "tiang_kanan",
                name = "Tiang Vertikal Kanan",
                profileType = profile,
                lengthMm = tinggiMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = lebarMm,
                posY = 0f,
                posZ = 0f,
                rotPitch = 90f, // Tegak berdiri sumbu Y
                rotYaw = 0f,
                rotRoll = 0f,
                color = "Silver"
            ),
            AssemblyPart(
                id = "ambang_atas",
                name = "Kusen Ambang Atas",
                profileType = profile,
                lengthMm = lebarMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = 0f,
                posY = tinggiMm,
                posZ = 0f,
                rotPitch = 0f,
                rotYaw = 90f, // Melintang horizontal sumbu X
                rotRoll = 0f,
                color = "Silver"
            ),
            AssemblyPart(
                id = "ambang_bawah",
                name = "Kusen Ambang Bawah",
                profileType = profile,
                lengthMm = lebarMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = 0f,
                posY = 0f,
                posZ = 0f,
                rotPitch = 0f,
                rotYaw = 90f, // Melintang horizontal sumbu X
                rotRoll = 0f,
                color = "Silver"
            )
        )
    }

    /**
     * Preset Rangka Kusen Pintu Aluminium Standar (3 Batang - Tiang Kiri, Tiang Kanan, Ambang Atas)
     */
    fun createKusenPintuPreset(lebarMm: Float = 900f, tinggiMm: Float = 2100f): List<AssemblyPart> {
        val profile = "Shop Front Open Back"
        val profileSize = 50f
        val thickness = 1.40f

        return listOf(
            AssemblyPart(
                id = "pintu_tiang_kiri",
                name = "Tiang Pintu Kiri",
                profileType = profile,
                lengthMm = tinggiMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = 0f,
                posY = 0f,
                posZ = 0f,
                rotPitch = 90f,
                color = "Black"
            ),
            AssemblyPart(
                id = "pintu_tiang_kanan",
                name = "Tiang Pintu Kanan",
                profileType = profile,
                lengthMm = tinggiMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = lebarMm,
                posY = 0f,
                posZ = 0f,
                rotPitch = 90f,
                color = "Black"
            ),
            AssemblyPart(
                id = "pintu_ambang_atas",
                name = "Ambang Atas Pintu",
                profileType = profile,
                lengthMm = lebarMm,
                widthMm = profileSize,
                heightMm = profileSize,
                thicknessMm = thickness,
                posX = 0f,
                posY = tinggiMm,
                posZ = 0f,
                rotPitch = 0f,
                rotYaw = 90f,
                color = "Black"
            )
        )
    }

    /**
     * Preset Rangka Meja / Rak Aluminium (8 Batang - 4 Kaki & 4 Frame Atas)
     */
    fun createRangkaMejaPreset(panjangMm: Float = 1000f, lebarMm: Float = 600f, tinggiMm: Float = 750f): List<AssemblyPart> {
        val profile = "Hollow Box"
        val profileSize = 40f
        val thickness = 1.20f

        return listOf(
            // 4 Kaki
            AssemblyPart(name = "Kaki Depan Kiri", lengthMm = tinggiMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = 0f, posY = 0f, posZ = 0f, rotPitch = 90f),
            AssemblyPart(name = "Kaki Depan Kanan", lengthMm = tinggiMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = panjangMm, posY = 0f, posZ = 0f, rotPitch = 90f),
            AssemblyPart(name = "Kaki Belakang Kiri", lengthMm = tinggiMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = 0f, posY = 0f, posZ = lebarMm, rotPitch = 90f),
            AssemblyPart(name = "Kaki Belakang Kanan", lengthMm = tinggiMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = panjangMm, posY = 0f, posZ = lebarMm, rotPitch = 90f),
            // 4 Frame Atas (Tabletop Rim)
            AssemblyPart(name = "Frame Atas Depan", lengthMm = panjangMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = 0f, posY = tinggiMm, posZ = 0f, rotYaw = 90f),
            AssemblyPart(name = "Frame Atas Belakang", lengthMm = panjangMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = 0f, posY = tinggiMm, posZ = lebarMm, rotYaw = 90f),
            AssemblyPart(name = "Frame Atas Kiri", lengthMm = lebarMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = 0f, posY = tinggiMm, posZ = 0f, rotPitch = 0f, rotYaw = 0f, rotRoll = 0f),
            AssemblyPart(name = "Frame Atas Kanan", lengthMm = lebarMm, widthMm = profileSize, heightMm = profileSize, thicknessMm = thickness, posX = panjangMm, posY = tinggiMm, posZ = 0f, rotPitch = 0f, rotYaw = 0f, rotRoll = 0f)
        )
    }

    /**
     * Mengekspor seluruh rakitan rangka multi-objek ke file Wavefront .obj lokal
     */
    fun exportAssemblyToObj(context: Context, scene: AssemblySceneMesh, assemblyName: String): String {
        return try {
            val dir = File(context.filesDir, "models_3d")
            if (!dir.exists()) dir.mkdirs()

            val cleanName = assemblyName.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val file = File(dir, "assembly_${cleanName}_${System.currentTimeMillis()}.obj")

            FileWriter(file).use { writer ->
                writer.write("# TOKO YOGA MULTI-OBJECT ALUMINIUM ASSEMBLY\n")
                writer.write("# Total Batang: ${scene.totalPieces}\n")
                writer.write("# Total Panjang Bahan: ${scene.totalMaterialLengthMm} mm\n")
                writer.write("o $cleanName\n\n")

                for (v in scene.vertices) {
                    writer.write("v ${v.x} ${v.y} ${v.z}\n")
                }
                writer.write("\n")

                for (t in scene.triangles) {
                    writer.write("vn ${t.normal.x} ${t.normal.y} ${t.normal.z}\n")
                }
                writer.write("\n")

                for (i in scene.triangles.indices) {
                    val idx1 = scene.indices[i * 3] + 1
                    val idx2 = scene.indices[i * 3 + 1] + 1
                    val idx3 = scene.indices[i * 3 + 2] + 1
                    val nIdx = i + 1
                    writer.write("f $idx1//$nIdx $idx2//$nIdx $idx3//$nIdx\n")
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
