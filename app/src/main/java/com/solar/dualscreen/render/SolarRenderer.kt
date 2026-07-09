package com.solar.dualscreen.render

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.solar.dualscreen.util.Mat4
import com.solar.dualscreen.util.PlanetData
import com.solar.dualscreen.util.PlanetData.CelestialBody
import com.solar.dualscreen.util.Vec3
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class SolarRenderer(private val context: Context) : GLSurfaceView.Renderer {
    companion object { private const val TAG = "SolarRenderer" }

    // 着色器 - 初始化为 -1 表示未编译
    private var solidProgram = -1
    private var planetProgram = -1
    private var sunProgram = -1
    private var ringProgram = -1
    private var orbitProgram = -1
    private var skyboxProgram = -1

    // 标记: 复杂 shader 是否可用
    private var useComplexShaders = true

    // 网格
    private val sphereLow = SphereMesh(24, 12)
    private val ringMesh = RingMesh(1.2f, 2.25f, 96)
    private val skyboxSphere = SphereMesh(16, 8)

    private var orbitVbo = 0
    private var orbitVboSize = 0

    // ============ 诊断模式 — 超大尺寸确保可见 ============
    private fun displayRadius(body: CelestialBody): Float = when (body.nameEn) {
        "Sun" -> 8f; "Jupiter" -> 4.5f; "Saturn" -> 3.8f; "Uranus" -> 3f
        "Neptune" -> 2.8f; "Earth" -> 2.5f; "Venus" -> 2.3f; "Mars" -> 1.8f
        "Mercury" -> 1.2f; "Moon" -> 0.8f
        "Titan", "Ganymede", "Callisto" -> 0.6f; "Io", "Europa", "Triton" -> 0.5f
        else -> 0.3f
    }

    private fun displayOrbit(body: CelestialBody): Float = when (body.nameEn) {
        "Mercury" -> 10f; "Venus" -> 16f; "Earth" -> 24f; "Mars" -> 32f
        "Jupiter" -> 45f; "Saturn" -> 58f; "Uranus" -> 72f; "Neptune" -> 84f
        else -> 0f
    }

    private fun moonOrbitOffset(moon: CelestialBody): Float = when (moon.nameEn) {
        "Moon" -> 1.5f; "Phobos" -> 0.6f; "Deimos" -> 1.0f
        "Io" -> 1.2f; "Europa" -> 1.6f; "Ganymede" -> 2.0f; "Callisto" -> 2.5f
        "Mimas" -> 1.0f; "Enceladus" -> 1.4f; "Titan" -> 2.2f
        "Miranda" -> 1.0f; "Ariel" -> 1.3f; "Umbriel" -> 1.6f; "Titania" -> 2.0f; "Oberon" -> 2.4f
        "Triton" -> 2.0f
        else -> 1.0f
    }

    // 星球纯色 (用于 solid shader fallback)
    private fun bodyColor(body: CelestialBody): FloatArray = when (body.nameEn) {
        "Sun" -> floatArrayOf(1f, 0.85f, 0.2f)
        "Mercury" -> floatArrayOf(0.7f, 0.7f, 0.7f)
        "Venus" -> floatArrayOf(0.9f, 0.8f, 0.5f)
        "Earth" -> floatArrayOf(0.3f, 0.5f, 0.9f)
        "Mars" -> floatArrayOf(0.8f, 0.3f, 0.1f)
        "Jupiter" -> floatArrayOf(0.85f, 0.7f, 0.5f)
        "Saturn" -> floatArrayOf(0.9f, 0.8f, 0.55f)
        "Uranus" -> floatArrayOf(0.5f, 0.8f, 0.85f)
        "Neptune" -> floatArrayOf(0.2f, 0.4f, 0.9f)
        else -> floatArrayOf(0.6f, 0.6f, 0.6f)
    }

    var simTime = 0f
    private var lastFrameTime = System.nanoTime()

    val camera = Camera3D().apply {
        target = Vec3(0f, 0f, 0f)
        distance = 100f; phi = 45f; theta = 25f
        minDist = 10f; maxDist = 200f
    }

    var onPlanetSelected: ((CelestialBody) -> Unit)? = null
    private var viewportW = 1
    private var viewportH = 1

    // ==================== GL 生命周期 ====================
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 红色诊断背景
        GLES30.glClearColor(0.3f, 0.0f, 0.0f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        // 1. 先编译绝对可靠的 solid shader
        solidProgram = ShaderProgram.compile(ShaderProgram.SOLID_VERTEX, ShaderProgram.SOLID_FRAGMENT)
        Log.i(TAG, "solidProgram=$solidProgram (should be >0)")

        // 2. 编译复杂 shader
        planetProgram = ShaderProgram.compile(ShaderProgram.PLANET_VERTEX, ShaderProgram.PLANET_FRAGMENT)
        sunProgram = ShaderProgram.compile(ShaderProgram.SUN_VERTEX, ShaderProgram.SUN_FRAGMENT)
        ringProgram = ShaderProgram.compile(ShaderProgram.RING_VERTEX, ShaderProgram.RING_FRAGMENT)
        orbitProgram = ShaderProgram.compile(ShaderProgram.ORBIT_VERTEX, ShaderProgram.ORBIT_FRAGMENT)
        skyboxProgram = ShaderProgram.compile(ShaderProgram.SKYBOX_VERTEX, ShaderProgram.SKYBOX_FRAGMENT)

        useComplexShaders = planetProgram > 0 && sunProgram > 0
        Log.i(TAG, "planet=$planetProgram sun=$sunProgram ring=$ringProgram orbit=$orbitProgram sky=$skyboxProgram useComplex=$useComplexShaders")

        buildOrbitLines()
        loadTextures()
        checkGLError("onSurfaceCreated")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width; viewportH = height
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        simTime += ((now - lastFrameTime) / 1e9f).coerceIn(0f, 0.1f)
        lastFrameTime = now

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val aspect = viewportW.toFloat() / viewportH
        val proj = camera.getProjectionMatrix(aspect, fov = 50f, far = 600f)
        val view = camera.getViewMatrix()
        val vp = proj * view

        // 暂时禁用天空球 — 先确认星球能渲染
        // drawSkybox(proj, view)

        // 轨道线
        if (orbitProgram > 0) drawOrbits(vp)

        // 太阳
        drawSunSolid(vp)

        // 行星 + 卫星
        for (planet in PlanetData.ALL_PLANETS) {
            drawBodySolid(planet, vp)
            for (moon in planet.moons) drawMoonSolid(moon, planet, vp)
        }

        checkGLError("onDrawFrame")
    }

    // ==================== 通用纯色球体渲染 (100% 可靠) ====================
    private fun drawSolidSphere(vp: Mat4, px: Float, py: Float, pz: Float,
                                radius: Float, rotation: Float,
                                r: Float, g: Float, b: Float) {
        GLES30.glUseProgram(solidProgram)
        // 矩阵乘法: S * R * T = T @ R @ S (我们的 times 是反向的)
        // 效果: 先缩放→再旋转→再平移 (正确顺序)
        val model = Mat4.scale(radius, radius, radius) *
                    Mat4.rotate(rotation, 0f, 1f, 0f) *
                    Mat4.translate(px, py, pz)
        val mvp = vp * model
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(solidProgram, "uMVP"), 1, false, mvp.toFloatArray(), 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(solidProgram, "uColor"), r, g, b)
        sphereLow.bind(solidProgram, 0, 0, 0)
        sphereLow.draw()
    }

    private fun drawSunSolid(vp: Mat4) {
        val sun = PlanetData.SUN
        val c = bodyColor(sun)
        val rot = simTime * 15f  // 太阳缓慢自转
        drawSolidSphere(vp, 0f, 0f, 0f, displayRadius(sun), rot, c[0], c[1], c[2])
    }

    private fun drawBodySolid(body: CelestialBody, vp: Mat4) {
        val orbitR = displayOrbit(body)
        val angle = ((simTime / body.orbitPeriod) * 360f) % 360f
        val px = orbitR * cos(angle * PI.toFloat() / 180f)
        val pz = orbitR * sin(angle * PI.toFloat() / 180f)
        val rot = (simTime / body.rotationPeriod) * 360f
        val c = bodyColor(body)
        drawSolidSphere(vp, px, 0f, pz, displayRadius(body), rot, c[0], c[1], c[2])

        // 光环
        if (body.hasRing && ringProgram > 0) drawRing(vp, px, pz, displayRadius(body))
    }

    private fun drawMoonSolid(moon: CelestialBody, parent: CelestialBody, vp: Mat4) {
        val parentOrbitR = displayOrbit(parent)
        val parentAngle = ((simTime / parent.orbitPeriod) * 360f) % 360f
        val ppx = parentOrbitR * cos(parentAngle * PI.toFloat() / 180f)
        val ppz = parentOrbitR * sin(parentAngle * PI.toFloat() / 180f)

        val moonAngle = ((simTime / moon.orbitPeriod) * 360f) % 360f
        val moonR = moonOrbitOffset(moon)
        val mx = ppx + moonR * cos(moonAngle * PI.toFloat() / 180f)
        val mz = ppz + moonR * sin(moonAngle * PI.toFloat() / 180f)
        val mRot = (simTime / moon.rotationPeriod) * 360f
        val c = bodyColor(moon)
        drawSolidSphere(vp, mx, 0f, mz, displayRadius(moon), mRot, c[0], c[1], c[2])
    }

    // ==================== 光环（纹理版） ====================
    private fun drawRing(vp: Mat4, px: Float, pz: Float, planetR: Float) {
        GLES30.glUseProgram(ringProgram)
        val ringScale = planetR * 0.9f
        val model = Mat4.scale(ringScale, ringScale, ringScale) *
                    Mat4.rotate(26.7f, 1f, 0f, 0f) *
                    Mat4.translate(px, 0f, pz)
        val mvp = vp * model
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(ringProgram, "uMVP"), 1, false, mvp.toFloatArray(), 0)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(ringProgram, "uLightColor"), 0.7f, 0.6f, 0.4f)
        val ringTexId = TextureManager.getTextureId("texture_saturn_ring")
        if (ringTexId != 0) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ringTexId) }
        ringMesh.bind(ringProgram, 0, 1)
        ringMesh.draw()
    }

    // ==================== 星空天球 ====================
    private fun drawSkybox(proj: Mat4, view: Mat4) {
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(skyboxProgram)
        val viewNoTrans = Mat4()
        for (i in 0..2) for (j in 0..2) viewNoTrans.m[i * 4 + j] = view.m[i * 4 + j]
        viewNoTrans.m[15] = 1f
        val mvp = proj * viewNoTrans * Mat4.scale(250f, 250f, 250f)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(skyboxProgram, "uMVP"), 1, false, mvp.toFloatArray(), 0)
        var texId = TextureManager.getTextureId("texture_stars")
        if (texId == 0) texId = TextureManager.getTextureId("color_skybox") ?: 0
        if (texId != 0) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId) }
        skyboxSphere.bind(skyboxProgram, 0, 0, 1)
        skyboxSphere.draw()
        GLES30.glDepthMask(true)
    }

    // ==================== 轨道线 ====================
    private fun buildOrbitLines() {
        val segments = 256
        val verts = mutableListOf<Float>()
        for (planet in PlanetData.ALL_PLANETS) {
            val r = displayOrbit(planet)
            for (i in 0..segments) {
                val a = i * 2.0 * PI / segments
                verts.add(r * cos(a).toFloat()); verts.add(0f); verts.add(r * sin(a).toFloat())
            }
        }
        orbitVboSize = verts.size / 3
        val buf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(verts.toFloatArray()).flip()
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0); orbitVbo = vbos[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, orbitVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES30.GL_STATIC_DRAW)
    }

    private fun drawOrbits(vp: Mat4) {
        if (orbitVbo == 0) return
        GLES30.glUseProgram(orbitProgram)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(orbitProgram, "uMVP"), 1, false, vp.toFloatArray(), 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, orbitVbo)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glLineWidth(1.5f)
        val segs = 257
        for ((i, _) in PlanetData.ALL_PLANETS.withIndex()) {
            val t = i.toFloat() / (PlanetData.ALL_PLANETS.size - 1)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(orbitProgram, "uColor"),
                0.2f + 0.3f * (1f - t), 0.2f + 0.4f * t, 0.4f + 0.3f * t)
            GLES30.glDrawArrays(GLES30.GL_LINE_STRIP, i * segs, segs)
        }
    }

    // ==================== 纹理 ====================
    private fun loadTextures() {
        TextureManager.createColorTexture(0.02f, 0.02f, 0.08f, "color_skybox")
        TextureManager.createColorTexture(0.7f, 0.6f, 0.4f, "texture_saturn_ring")
        tryLoadRealTextures()
    }

    private fun tryLoadRealTextures() {
        TextureManager.loadFromFile(context, "2k_sun.jpg", "texture_sun")
        TextureManager.loadFromFile(context, "2k_stars_milky_way.jpg", "texture_stars")
        TextureManager.loadFromFile(context, "2k_stars.jpg", "texture_stars")
        for (body in PlanetData.ALL_BODIES) {
            body.texture2k?.let { TextureManager.loadFromFile(context, it, "texture_${body.nameEn.lowercase()}") }
        }
        TextureManager.loadFromFile(context, "2k_saturn_ring_alpha.png", "texture_saturn_ring")
    }

    // ==================== 调试 ====================
    private fun checkGLError(tag: String) {
        var err = GLES30.glGetError()
        while (err != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "GL error at $tag: 0x${err.toString(16)}")
            err = GLES30.glGetError()
        }
    }
}
