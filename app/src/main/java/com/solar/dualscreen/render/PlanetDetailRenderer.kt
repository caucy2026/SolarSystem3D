package com.solar.dualscreen.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.solar.dualscreen.util.Mat4
import com.solar.dualscreen.util.PlanetData.CelestialBody
import com.solar.dualscreen.util.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PlanetDetailRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object { private const val TAG = "PlanetDetail" }

    private var solidProgram = -1
    private var planetProgram = -1
    private var ringProgram = -1
    private val sphereHigh = SphereMesh(64, 32)
    private val ringMesh = RingMesh(1.2f, 2.25f, 128)
    var currentBody: CelestialBody? = null
    var rotationAngle = 0f

    val camera = Camera3D().apply {
        target = Vec3(0f, 0f, 0f); distance = 8f; phi = 15f
        minDist = 2f; maxDist = 30f
    }

    private var lastFrameTime = System.nanoTime()
    private var viewportW = 1; private var viewportH = 1

    private fun displayRadius(body: CelestialBody): Float = when (body.nameEn) {
        "Sun" -> 3.5f; "Jupiter" -> 2.0f; "Saturn" -> 1.7f; "Uranus" -> 1.3f; "Neptune" -> 1.2f
        "Earth" -> 1.0f; "Venus" -> 0.95f; "Mars" -> 0.7f; "Mercury" -> 0.5f
        else -> 1.0f
    }

    private fun bodyColor(body: CelestialBody): FloatArray = when (body.nameEn) {
        "Sun" -> floatArrayOf(1f, 0.85f, 0.2f); "Mercury" -> floatArrayOf(0.7f, 0.7f, 0.7f)
        "Venus" -> floatArrayOf(0.9f, 0.8f, 0.5f); "Earth" -> floatArrayOf(0.3f, 0.5f, 0.9f)
        "Mars" -> floatArrayOf(0.8f, 0.3f, 0.1f); "Jupiter" -> floatArrayOf(0.85f, 0.7f, 0.5f)
        "Saturn" -> floatArrayOf(0.9f, 0.8f, 0.55f); "Uranus" -> floatArrayOf(0.5f, 0.8f, 0.85f)
        "Neptune" -> floatArrayOf(0.2f, 0.4f, 0.9f)
        else -> floatArrayOf(0.6f, 0.6f, 0.6f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0.05f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        solidProgram = ShaderProgram.compile(ShaderProgram.SOLID_VERTEX, ShaderProgram.SOLID_FRAGMENT)
        planetProgram = ShaderProgram.compile(ShaderProgram.PLANET_VERTEX, ShaderProgram.PLANET_FRAGMENT)
        ringProgram = ShaderProgram.compile(ShaderProgram.RING_VERTEX, ShaderProgram.RING_FRAGMENT)
        Log.i(TAG, "solid=$solidProgram planet=$planetProgram ring=$ringProgram")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width; viewportH = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameTime) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameTime = now
        rotationAngle += dt * 8f; if (rotationAngle > 360f) rotationAngle -= 360f

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val body = currentBody ?: return
        val aspect = viewportW.toFloat() / viewportH
        val proj = camera.getProjectionMatrix(aspect, fov = 40f, far = 500f)
        val view = camera.getViewMatrix()
        val vp = proj * view
        val r = displayRadius(body)
        val c = bodyColor(body)

        // 纯色球体渲染 (100% 可靠)
        GLES30.glUseProgram(solidProgram)
        // 矩阵: S * R_y * R_axial (Scale * Rotate, 不 Translate)
        val model = Mat4.scale(r, r, r) *
                    Mat4.rotate(rotationAngle, 0f, 1f, 0f) *
                    Mat4.rotate(body.axialTilt, 0f, 0f, 1f)
        val mvp = vp * model
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(solidProgram, "uMVP"), 1, false, mvp.toFloatArray(), 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(solidProgram, "uColor"), c[0], c[1], c[2])
        sphereHigh.bind(solidProgram, 0, 0, 0)
        sphereHigh.draw()

        // 光环
        if (body.hasRing && ringProgram > 0) {
            GLES30.glUseProgram(ringProgram)
            val ringModel = Mat4.scale(r * 0.9f, r * 0.9f, r * 0.9f) *
                            Mat4.rotate(26.7f, 1f, 0f, 0f)
            val ringMvp = vp * ringModel
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(ringProgram, "uMVP"), 1, false, ringMvp.toFloatArray(), 0)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(ringProgram, "uLightColor"), 0.7f, 0.6f, 0.4f)
            val rt = TextureManager.getTextureId("texture_saturn_ring")
            if (rt != 0) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, rt) }
            ringMesh.bind(ringProgram, 0, 1)
            ringMesh.draw()
        }
    }
}
