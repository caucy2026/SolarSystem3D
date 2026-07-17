package com.solar.dualscreen

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SolarGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // ============ Shaders — 从 assets/shaders/ 加载纯GLSL，不经过Java字符串 ============
    private val vertexShaderCode: String
        get() = context.assets.open("shaders/sun.vert").bufferedReader().readText()
    private val fragmentShaderCode: String
        get() = context.assets.open("shaders/sun.frag").bufferedReader().readText()

    private var program = 0
    private var mvpHandle = 0
    private var posHandle = 0
    private var texCoordHandle = 0
    private var timeHandle = 0
    private var emissionTexId = 0
    private var plasmaTexId = 0
    private var helioTexId = 0

    // ============ Icosahedron 几何体 (detail=4) ============
    private var vertexCount = 0
    private var indexCount = 0
    private var vertexBuffer: FloatBuffer? = null
    private var indexBuffer: ShortBuffer? = null

    // ============ 矩阵 ============
    private val mvpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private var startTime = System.currentTimeMillis()
    private var fpsFrames = 0
    private var fpsLastLog = 0L

    // ============ 相机控制 ============
    var camTheta = 0f        // 水平旋转角
    var camPhi = 0.5f         // 垂直角 (避免极点)
    var camDistance = 8f      // 距离
    var camTargetX = 0f; var camTargetY = 0f; var camTargetZ = 0f

    fun rotate(dTheta: Float, dPhi: Float) {
        camTheta += dTheta
        camPhi = (camPhi + dPhi).coerceIn(0.05f, Math.PI.toFloat() - 0.05f)
    }

    fun zoom(factor: Float) {
        camDistance = (camDistance * factor).coerceIn(2f, 50f)
    }

    // ============ Shader 编译 ============
    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            android.util.Log.e("SolarGL", "Shader compile error: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    // ============ 程序链接 ============
    private fun createProgram(): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        if (vs == 0 || fs == 0) return 0
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    // ============ 纹理加载 ============
    private fun loadTexture(resName: String): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = context.assets.open("textures/$resName").use { BitmapFactory.decodeStream(it) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return texId
    }

    // ============ Icosahedron 生成 (detail=4, 20*4^4=5120面) ============
    private fun buildIcosahedron(detail: Int) {
        val X = 0.5257311121191336f
        val Z = 0.85065080835204f
        val baseVerts = floatArrayOf(
            -X, 0f, Z,  X, 0f, Z,  -X, 0f, -Z,  X, 0f, -Z,
            0f, Z, X,  0f, Z, -X,  0f, -Z, X,  0f, -Z, -X,
            Z, X, 0f, -Z, X, 0f,  Z, -X, 0f, -Z, -X, 0f
        )
        val baseIndices = shortArrayOf(
            0,4,1, 0,9,4, 9,5,4, 4,5,8, 4,8,1,
            8,10,1, 8,3,10, 5,3,8, 5,2,3, 2,7,3,
            7,10,3, 7,6,10, 7,11,6, 11,0,6, 0,1,6,
            6,1,10, 9,0,11, 9,11,2, 9,2,5, 7,2,11
        )

        var verts = baseVerts.toMutableList()
        var indices = baseIndices.map { it.toInt() }.toMutableList()

        for (d in 0 until detail) {
            val newVerts = verts.toMutableList()
            val newIndices = mutableListOf<Int>()
            val edgeMap = mutableMapOf<Long, Int>()

            fun midPoint(i1: Int, i2: Int): Int {
                val key = if (i1 < i2) (i1.toLong() shl 32) or i2.toLong()
                          else (i2.toLong() shl 32) or i1.toLong()
                edgeMap[key]?.let { return it }
                val p = newVerts.size / 3
                val x = (verts[i1*3] + verts[i2*3]) / 2f
                val y = (verts[i1*3+1] + verts[i2*3+1]) / 2f
                val z = (verts[i1*3+2] + verts[i2*3+2]) / 2f
                val len = Math.sqrt((x*x + y*y + z*z).toDouble()).toFloat()
                newVerts.addAll(listOf(x/len, y/len, z/len))
                edgeMap[key] = p
                return p
            }

            for (i in indices.indices step 3) {
                val a = indices[i]; val b = indices[i+1]; val c = indices[i+2]
                val ab = midPoint(a, b); val bc = midPoint(b, c); val ca = midPoint(c, a)
                newIndices.addAll(listOf(a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca))
            }
            verts = newVerts; indices = newIndices
        }

        // 生成 UV（球面映射）
        val finalVerts = FloatArray(verts.size / 3 * 5) // pos3 + uv2
        for (i in 0 until verts.size / 3) {
            val x = verts[i*3]; val y = verts[i*3+1]; val z = verts[i*3+2]
            finalVerts[i*5] = x * 3.5f
            finalVerts[i*5+1] = y * 3.5f
            finalVerts[i*5+2] = z * 3.5f
            finalVerts[i*5+3] = (Math.atan2(z.toDouble(), x.toDouble()) / (2*Math.PI) + 0.5).toFloat()
            finalVerts[i*5+4] = (Math.asin(y.toDouble()) / Math.PI + 0.5).toFloat()
        }

        vertexCount = finalVerts.size / 5
        indexCount = indices.size

        vertexBuffer = ByteBuffer.allocateDirect(finalVerts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(finalVerts); position(0)
            }
        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer().apply {
                indices.forEach { put(it.toShort()) }; position(0)
            }

        android.util.Log.i("SolarGL", "Icosahedron detail=$detail: ${vertexCount} verts, ${indexCount/3} tris")
    }

    // ============ Renderer 生命周期 ============
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0.04f, 1f)  // 深蓝空间背景
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        program = createProgram()
        if (program == 0) {
            android.util.Log.e("SolarGL", "Failed to create shader program!")
            return
        }
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        timeHandle = GLES20.glGetUniformLocation(program, "uTime")

        // 加载纹理
        emissionTexId = loadTexture("2k_sun.jpg")
        // 程序化生成 plasma 和 helio 纹理（512x256 RGBA）
        plasmaTexId = createProceduralPlasmaTex(512, 256)
        helioTexId = createProceduralHelioTex(512, 256)

        buildIcosahedron(4)
        startTime = System.currentTimeMillis()
        android.util.Log.i("SolarGL", "GL Surface created OK")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.perspectiveM(projMatrix, 0, 45f, ratio, 0.3f, 600f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        if (program == 0) return

        val elapsed = (System.currentTimeMillis() - startTime) * 0.001f

        // 相机：球坐标 → 笛卡尔坐标
        val eyeX = camTargetX + camDistance * Math.cos(camTheta.toDouble()).toFloat() * Math.sin(camPhi.toDouble()).toFloat()
        val eyeY = camTargetY + camDistance * Math.cos(camPhi.toDouble()).toFloat()
        val eyeZ = camTargetZ + camDistance * Math.sin(camTheta.toDouble()).toFloat() * Math.sin(camPhi.toDouble()).toFloat()
        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, camTargetX, camTargetY, camTargetZ, 0f, 1f, 0f)

        // 旋转 + MVP
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, elapsed * 30f, 0f, 1f, 0f) // 自转
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // 时间
        GLES20.glUniform1f(timeHandle, elapsed)

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, emissionTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uEmission"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, plasmaTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uPlasma"), 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, helioTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uHelio"), 2)

        // 顶点数据
        val vb = vertexBuffer ?: return
        val ib = indexBuffer ?: return
        vb.position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 20, vb)
        GLES20.glEnableVertexAttribArray(posHandle)
        vb.position(3)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 20, vb)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // 绘制
        ib.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, ib)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)

        // FPS logging
        fpsFrames++
        val now = System.currentTimeMillis()
        if (now - fpsLastLog >= 2000) {
            val fps = fpsFrames * 1000f / (now - fpsLastLog)
            android.util.Log.i("SolarGL", "FPS: " + String.format("%.1f", fps))
            fpsFrames = 0; fpsLastLog = now
        }
    }

    // ============ 程序化纹理（替代 Three.js Canvas 纹理） ============
    private fun createProceduralPlasmaTex(w: Int, h: Int): Int {
        val pixels = IntArray(w * h)
        val rng = java.util.Random(42)
        // 活跃区斑点
        for (ri in 0..5) {
            val rx = 50 + rng.nextInt(w - 100)
            val ry = 20 + rng.nextInt(h - 40)
            val rr = 15 + rng.nextInt(30)
            for (y in maxOf(0, ry-rr) until minOf(h, ry+rr)) {
                for (x in maxOf(0, rx-rr) until minOf(w, rx+rr)) {
                    val dx = (x - rx).toFloat() / rr
                    val dy = (y - ry).toFloat() / rr
                    val d = Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                    if (d < 1f) {
                        val a = ((1f - d) * (1f - d) * 0.5f * 255).toInt()
                        val idx = y * w + x
                        val existing = pixels[idx]
                        val r = minOf(255, (existing shr 16 and 0xFF) + a)
                        val g = minOf(255, (existing shr 8 and 0xFF) + (a * 0.8f).toInt())
                        val b = minOf(255, (existing and 0xFF) + (a * 0.5f).toInt())
                        pixels[idx] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        // 射线
        for (i in 0..200) {
            val x = rng.nextInt(w); val y = rng.nextInt(h)
            val a = rng.nextFloat() * Math.PI.toFloat() * 2
            val l = 5 + rng.nextInt(20)
            val cx = x + (Math.cos(a.toDouble()) * l * 0.5).toInt()
            val cy = y + (Math.sin(a.toDouble()) * l * 0.5).toInt()
            val ex = x + (Math.cos(a.toDouble()) * l).toInt()
            val ey = y + (Math.sin(a.toDouble()) * l).toInt()
            drawLine(pixels, w, h, x, y, cx, cy, ex, ey, 255, 180, 80, 30 + rng.nextInt(80))
        }
        return uploadPixels(pixels, w, h)
    }

    private fun createProceduralHelioTex(w: Int, h: Int): Int {
        val pixels = IntArray(w * h)
        val rng = java.util.Random(99)
        for (i in 0..400) {
            val x = rng.nextInt(w)
            val y = rng.nextInt(h)
            val ed = minOf(y, h - y).toFloat() / (h / 2).toFloat()
            if (rng.nextFloat() > ed * 0.5f + 0.05f) continue
            val dy = if (y < h/2) -1f else 1f
            val a = dy * (-Math.PI.toFloat()/2) + (rng.nextFloat() - 0.5f) * 0.6f
            val l = 8 + rng.nextInt(40)
            val cx = x + (Math.cos(a.toDouble()) * l * 0.4).toInt()
            val cy = y + (Math.sin(a.toDouble()) * l * 0.4).toInt()
            val ex = x + (Math.cos(a.toDouble()) * l).toInt()
            val ey = y + (Math.sin(a.toDouble()) * l).toInt()
            drawLine(pixels, w, h, x, y, cx, cy, ex, ey, 255, 140, 40, 10 + rng.nextInt(45))
        }
        return uploadPixels(pixels, w, h)
    }

    private fun drawLine(pix: IntArray, w: Int, h: Int,
                         x0: Int, y0: Int, cx: Int, cy: Int, x1: Int, y1: Int,
                         r: Int, g: Int, b: Int, a: Int) {
        // 简化二次贝塞尔线条绘制（Bresenham 线段近似）
        val steps = 20
        var lx = x0.toFloat(); var ly = y0.toFloat()
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val inv = 1f - t
            val px = (inv*inv*x0 + 2*inv*t*cx + t*t*x1).toInt()
            val py = (inv*inv*y0 + 2*inv*t*cy + t*t*y1).toInt()
            drawBresenham(pix, w, h, lx.toInt(), ly.toInt(), px, py, r, g, b, a)
            lx = px.toFloat(); ly = py.toFloat()
        }
    }

    private fun drawBresenham(pix: IntArray, w: Int, h: Int,
                              x0: Int, y0: Int, x1: Int, y1: Int,
                              r: Int, g: Int, b: Int, a: Int) {
        var x = x0; var y = y0
        val dx = kotlin.math.abs(x1 - x0); val dy = -kotlin.math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            if (x in 0 until w && y in 0 until h) {
                val idx = y * w + x
                val existing = pix[idx]
                val nr = minOf(255, (existing shr 16 and 0xFF) + a * r / 255)
                val ng = minOf(255, (existing shr 8 and 0xFF) + a * g / 255)
                val nb = minOf(255, (existing and 0xFF) + a * b / 255)
                pix[idx] = (255 shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    private fun uploadPixels(pixels: IntArray, w: Int, h: Int): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        val buf = ByteBuffer.allocateDirect(pixels.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(pixels); position(0) }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        return texId
    }
}
