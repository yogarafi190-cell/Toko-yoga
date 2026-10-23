package com.example.mesh3d.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.mesh3d.Mesh3DData
import com.example.mesh3d.Point2D
import com.example.mesh3d.Vertex3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * Renderer OpenGL ES 2.0 untuk merender penampang ekstrusi aluminium 3D
 * dengan shading logam Blinn-Phong, depth buffer, dan kontrol rotasi kamera.
 */
class ExtrusionGLRenderer : GLSurfaceView.Renderer {

    // Buffers
    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var wireframeBuffer: FloatBuffer? = null
    private var vertexCount = 0
    private var wireframeCount = 0

    // Program OpenGL
    private var programId = 0
    private var uMVPMatrixHandle = 0
    private var uMVMatrixHandle = 0
    private var uLightPosHandle = 0
    private var uColorHandle = 0
    private var uIsWireframeHandle = 0
    private var aPositionHandle = 0
    private var aNormalHandle = 0

    // Matriks Transformasi
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Bounding Box & Centering
    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f
    private var maxExtent = 100f

    // Parameter Kamera
    @Volatile var yawDeg = 35f
    @Volatile var pitchDeg = -25f
    @Volatile var zoom = 1.0f
    @Volatile var panX = 0f
    @Volatile var panY = 0f
    @Volatile var showWireframe = false
    @Volatile var autoRotate = false

    // Warna Material Aluminium (R, G, B, A)
    @Volatile var materialColor = floatArrayOf(0.75f, 0.80f, 0.85f, 1.0f) // Default: Anodized Silver

    // Posisi Sumber Cahaya (Eye space)
    private val lightPosInEyeSpace = floatArrayOf(500f, 800f, 1000f)

    // Data Mesh terbaru yang akan di-update di thread GL
    private var pendingMesh: Mesh3DData? = null
    private var needsMeshUpload = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Background gelap profesional CAD (Slate Navy 0xFF0B132B)
        GLES20.glClearColor(0.043f, 0.075f, 0.169f, 1.0f)

        // Aktifkan Depth Testing (Z-buffering)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        // Matikan face culling agar rongga hollow dalam dan luar terlihat sempurna
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // Inisialisasi Shaders
        initShaders()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(1f, height.toFloat())

        // Proyeksi Perspektif
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 1f, 10000f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Upload mesh jika ada perubahan dari thread UI
        synchronized(this) {
            if (needsMeshUpload) {
                pendingMesh?.let { uploadMeshToBuffers(it) }
                needsMeshUpload = false
            }
        }

        if (vertexCount == 0 || programId == 0) return

        if (autoRotate) {
            yawDeg = (yawDeg + 0.4f) % 360f
        }

        // Setup Matriks Kamera (View Matrix)
        val distance = (maxExtent * 2.2f) / zoom.coerceAtLeast(0.1f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, distance, 0f, 0f, 0f, 0f, 1f, 0f)

        // Setup Matriks Model (Rotasi, Translasi, Centering)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, panX, panY, 0f)
        Matrix.rotateM(modelMatrix, 0, pitchDeg, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, -centerX, -centerY, -centerZ)

        // Hitung MV dan MVP Matrix
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUseProgram(programId)

        // Uniforms
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVMatrixHandle, 1, false, mvMatrix, 0)
        GLES20.glUniform3fv(uLightPosHandle, 1, lightPosInEyeSpace, 0)

        // 1. Render Solid Shaded Surface jika bukan wireframe murni
        vertexBuffer?.let { vBuf ->
            vBuf.position(0)
            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vBuf)

            normalBuffer?.let { nBuf ->
                nBuf.position(0)
                GLES20.glEnableVertexAttribArray(aNormalHandle)
                GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 0, nBuf)
            }

            if (!showWireframe) {
                // Gambar Poligon Solid Shaded
                GLES20.glUniform1i(uIsWireframeHandle, 0)
                GLES20.glUniform4fv(uColorHandle, 1, materialColor, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
            }
        }

        // 2. Render Wireframe CAD Lines jika diaktifkan atau sebagai kontur
        wireframeBuffer?.let { wBuf ->
            wBuf.position(0)
            GLES20.glEnableVertexAttribArray(aPositionHandle)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, wBuf)

            // Wireframe warna oranye CAD yang kontras atau putih halus
            val wireColor = if (showWireframe) floatArrayOf(0.96f, 0.62f, 0.04f, 1.0f) else floatArrayOf(0.35f, 0.45f, 0.55f, 0.6f)
            GLES20.glUniform1i(uIsWireframeHandle, 1)
            GLES20.glUniform4fv(uColorHandle, 1, wireColor, 0)

            GLES20.glLineWidth(if (showWireframe) 2.5f else 1.0f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, wireframeCount)
        }

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
    }

    /**
     * Set data mesh baru untuk dirender.
     */
    fun updateMesh(mesh: Mesh3DData) {
        synchronized(this) {
            pendingMesh = mesh
            needsMeshUpload = true
        }
    }

    private fun uploadMeshToBuffers(mesh: Mesh3DData) {
        if (mesh.triangles.isEmpty()) {
            vertexCount = 0
            wireframeCount = 0
            return
        }

        // 1. Hitung Bounding Box
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

        centerX = (minX + maxX) / 2f
        centerY = (minY + maxY) / 2f
        centerZ = (minZ + maxZ) / 2f
        maxExtent = max(max(maxX - minX, maxY - minY), (maxZ - minZ) * 0.4f).coerceAtLeast(20f)

        // 2. Siapkan Vertices & Normals untuk GL_TRIANGLES
        val triCount = mesh.triangles.size
        val verticesArray = FloatArray(triCount * 9)
        val normalsArray = FloatArray(triCount * 9)
        val wireframeArray = FloatArray(triCount * 18) // 3 segmen garis x 2 titik x 3 koordinat

        var vIdx = 0
        var wIdx = 0

        for (t in mesh.triangles) {
            // Vertex 1
            verticesArray[vIdx] = t.v1.x; verticesArray[vIdx + 1] = t.v1.y; verticesArray[vIdx + 2] = t.v1.z
            normalsArray[vIdx] = t.normal.x; normalsArray[vIdx + 1] = t.normal.y; normalsArray[vIdx + 2] = t.normal.z
            // Vertex 2
            verticesArray[vIdx + 3] = t.v2.x; verticesArray[vIdx + 4] = t.v2.y; verticesArray[vIdx + 5] = t.v2.z
            normalsArray[vIdx + 3] = t.normal.x; normalsArray[vIdx + 4] = t.normal.y; normalsArray[vIdx + 5] = t.normal.z
            // Vertex 3
            verticesArray[vIdx + 6] = t.v3.x; verticesArray[vIdx + 7] = t.v3.y; verticesArray[vIdx + 8] = t.v3.z
            normalsArray[vIdx + 6] = t.normal.x; normalsArray[vIdx + 7] = t.normal.y; normalsArray[vIdx + 8] = t.normal.z

            // Wireframe Edge 1: v1 -> v2
            wireframeArray[wIdx] = t.v1.x; wireframeArray[wIdx + 1] = t.v1.y; wireframeArray[wIdx + 2] = t.v1.z
            wireframeArray[wIdx + 3] = t.v2.x; wireframeArray[wIdx + 4] = t.v2.y; wireframeArray[wIdx + 5] = t.v2.z
            // Wireframe Edge 2: v2 -> v3
            wireframeArray[wIdx + 6] = t.v2.x; wireframeArray[wIdx + 7] = t.v2.y; wireframeArray[wIdx + 8] = t.v2.z
            wireframeArray[wIdx + 9] = t.v3.x; wireframeArray[wIdx + 10] = t.v3.y; wireframeArray[wIdx + 11] = t.v3.z
            // Wireframe Edge 3: v3 -> v1
            wireframeArray[wIdx + 12] = t.v3.x; wireframeArray[wIdx + 13] = t.v3.y; wireframeArray[wIdx + 14] = t.v3.z
            wireframeArray[wIdx + 15] = t.v1.x; wireframeArray[wIdx + 16] = t.v1.y; wireframeArray[wIdx + 17] = t.v1.z

            vIdx += 9
            wIdx += 18
        }

        vertexBuffer = ByteBuffer.allocateDirect(verticesArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(verticesArray); position(0) }

        normalBuffer = ByteBuffer.allocateDirect(normalsArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(normalsArray); position(0) }

        wireframeBuffer = ByteBuffer.allocateDirect(wireframeArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(wireframeArray); position(0) }

        vertexCount = triCount * 3
        wireframeCount = triCount * 6
    }

    fun setMaterial(finishName: String) {
        materialColor = when (finishName.lowercase()) {
            "black", "hitam", "anodized black" -> floatArrayOf(0.18f, 0.18f, 0.20f, 1.0f)
            "brown", "coklat", "dark bronze" -> floatArrayOf(0.36f, 0.25f, 0.20f, 1.0f)
            "white", "putih", "powder white" -> floatArrayOf(0.92f, 0.94f, 0.96f, 1.0f)
            "gold", "emas", "champagne gold" -> floatArrayOf(0.85f, 0.68f, 0.42f, 1.0f)
            else -> floatArrayOf(0.72f, 0.78f, 0.84f, 1.0f) // Silver anodized
        }
    }

    private fun initShaders() {
        val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uMVMatrix;
            uniform vec3 uLightPos;
            attribute vec4 aPosition;
            attribute vec3 aNormal;
            varying vec3 vPosition;
            varying vec3 vNormal;
            varying vec3 vLightDir;

            void main() {
                vPosition = vec3(uMVMatrix * aPosition);
                vNormal = normalize(vec3(uMVMatrix * vec4(aNormal, 0.0)));
                vLightDir = normalize(uLightPos - vPosition);
                gl_Position = uMVPMatrix * aPosition;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 uColor;
            uniform int uIsWireframe;
            varying vec3 vPosition;
            varying vec3 vNormal;
            varying vec3 vLightDir;

            void main() {
                if (uIsWireframe == 1) {
                    gl_FragColor = uColor;
                    return;
                }
                vec3 norm = normalize(vNormal);
                // Normal dua sisi untuk rongga berongga hollow
                if (!gl_FrontFacing) {
                    norm = -norm;
                }
                vec3 lightDir = normalize(vLightDir);
                vec3 viewDir = normalize(-vPosition);

                // Komponen Ambient
                float ambient = 0.32;

                // Komponen Diffuse (Lambert)
                float diff = max(dot(norm, lightDir), 0.0) * 0.58;

                // Komponen Specular (Blinn-Phong) untuk efek kilau logam aluminium
                vec3 halfwayDir = normalize(lightDir + viewDir);
                float spec = pow(max(dot(norm, halfwayDir), 0.0), 32.0) * 0.45;

                // Lampu sekunder (Fill Light) dari bawah/belakang
                vec3 fillDir = normalize(vec3(-0.4, -0.6, -0.5));
                float fill = max(dot(norm, -fillDir), 0.0) * 0.18;

                vec3 finalRgb = (uColor.rgb * (ambient + diff + fill)) + vec3(spec);
                gl_FragColor = vec4(clamp(finalRgb, 0.0, 1.0), uColor.a);
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)

            uMVPMatrixHandle = GLES20.glGetUniformLocation(prog, "uMVPMatrix")
            uMVMatrixHandle = GLES20.glGetUniformLocation(prog, "uMVMatrix")
            uLightPosHandle = GLES20.glGetUniformLocation(prog, "uLightPos")
            uColorHandle = GLES20.glGetUniformLocation(prog, "uColor")
            uIsWireframeHandle = GLES20.glGetUniformLocation(prog, "uIsWireframe")
            aPositionHandle = GLES20.glGetAttribLocation(prog, "aPosition")
            aNormalHandle = GLES20.glGetAttribLocation(prog, "aNormal")
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}
