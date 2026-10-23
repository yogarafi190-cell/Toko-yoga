package com.example.mesh3d

data class Point2D(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: Point2D) = Point2D(x + other.x, y + other.y)
    operator fun minus(other: Point2D) = Point2D(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Point2D(x * scalar, y * scalar)
    operator fun div(scalar: Float) = Point2D(x / scalar, y / scalar)

    fun length(): Float = kotlin.math.sqrt(x * x + y * y)

    fun normalized(): Point2D {
        val len = length()
        return if (len > 0.0001f) Point2D(x / len, y / len) else Point2D(0f, 0f)
    }

    // Normal tegak lurus 90 derajat terhadap vektor
    fun perpendicular(): Point2D = Point2D(-y, x)
}

data class Vertex3D(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(o: Vertex3D) = Vertex3D(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vertex3D) = Vertex3D(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vertex3D(x * s, y * s, z * s)

    fun length(): Float = kotlin.math.sqrt(x * x + y * y + z * z)

    fun normalized(): Vertex3D {
        val len = length()
        return if (len > 0.00001f) Vertex3D(x / len, y / len, z / len) else Vertex3D(0f, 0f, 1f)
    }

    fun cross(o: Vertex3D): Vertex3D = Vertex3D(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x
    )

    fun dot(o: Vertex3D): Float = x * o.x + y * o.y + z * o.z
}

data class Triangle3D(
    val v1: Vertex3D,
    val v2: Vertex3D,
    val v3: Vertex3D,
    val normal: Vertex3D,
    val isInner: Boolean = false
)

data class Mesh3DData(
    val vertices: List<Vertex3D>,
    val normals: List<Vertex3D>,
    val triangles: List<Triangle3D>,
    val indices: List<Int> // Triplet of vertex indices
)
