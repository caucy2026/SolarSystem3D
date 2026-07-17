package com.solar.dualscreen

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class JupiterGLRenderer(private val ctx: Context) : GLSurfaceView.Renderer {

    // 木星轨道参数（与Three.js一致）
    var orbitRadius = 28f; var period = 712f  // 轨道周期（地球天）
    var jupiterX = 0f; var jupiterZ = 0f       // 当前位置（由外部更新）

    private val vertShader = """
        uniform mat4 uMVP; attribute vec4 aPos; attribute vec2 aUV; varying vec2 vUV;
        void main(){ vUV=aUV; gl_Position=uMVP*aPos; }
    """.trimIndent()
    private val fragShader = """
        precision mediump float; varying vec2 vUV; uniform sampler2D uTex;
        void main(){ gl_FragColor=vec4(texture2D(uTex,vUV).rgb*1.2,1.0); }
    """.trimIndent()

    private var prog = 0; private var texId = 0
    private var vertexBuf: java.nio.FloatBuffer? = null
    private var indexBuf: java.nio.ShortBuffer? = null
    private var triCount = 0
    private var started = false
    private var startTime = 0L

    // 相机
    var camDist = 85f; var camTheta = 0f; var camPhi = 0.5f
    private val projM = FloatArray(16); private val viewM = FloatArray(16)
    private val modelM = FloatArray(16); private val mvpM = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)  // 透明
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val vs = compile(GLES20.GL_VERTEX_SHADER, vertShader)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragShader)
        prog = GLES20.glCreateProgram(); GLES20.glAttachShader(prog, vs); GLES20.glAttachShader(prog, fs); GLES20.glLinkProgram(prog)

        // Icosahedron detail=3 (1280面)
        buildIcosahedron(2.0f, 3)

        // 木星纹理
        texId = loadTex("2k_jupiter.jpg")
        startTime = System.currentTimeMillis()
        started = true
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(projM, 0, 45f, w.toFloat()/h, 0.3f, 600f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!started) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val t = (System.currentTimeMillis() - startTime) * 0.001f

        // 相机
        val ex = camDist * cos(camTheta)*sin(camPhi); val ey = camDist * cos(camPhi)
        val ez = camDist * sin(camTheta)*sin(camPhi)
        Matrix.setLookAtM(viewM, 0, ex.toFloat(), ey.toFloat(), ez.toFloat(), 0f, 0f, 0f, 0f, 1f, 0f)

        // 木星位置 + 自转（与Three.js同步的轨道计算）
        val angle = (t / period * 2.0 * PI).toFloat()
        jupiterX = cos(angle) * orbitRadius
        jupiterZ = sin(angle) * orbitRadius

        Matrix.setIdentityM(modelM, 0)
        Matrix.translateM(modelM, 0, jupiterX, 0f, jupiterZ)
        Matrix.rotateM(modelM, 0, t * 50f, 0f, 1f, 0f)  // 自转
        Matrix.multiplyMM(mvpM, 0, viewM, 0, modelM, 0)
        Matrix.multiplyMM(mvpM, 0, projM, 0, mvpM, 0)

        GLES20.glUseProgram(prog)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(prog, "uMVP"), 1, false, mvpM, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, "uTex"), 0)

        val vb = vertexBuf ?: return; val ib = indexBuf ?: return
        vb.position(0)
        val ap = GLES20.glGetAttribLocation(prog, "aPos")
        GLES20.glVertexAttribPointer(ap, 3, GLES20.GL_FLOAT, false, 20, vb); GLES20.glEnableVertexAttribArray(ap)
        vb.position(3)
        val au = GLES20.glGetAttribLocation(prog, "aUV")
        GLES20.glVertexAttribPointer(au, 2, GLES20.GL_FLOAT, false, 20, vb); GLES20.glEnableVertexAttribArray(au)
        ib.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, triCount*3, GLES20.GL_UNSIGNED_SHORT, ib)
        GLES20.glDisableVertexAttribArray(ap); GLES20.glDisableVertexAttribArray(au)
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        return s
    }

    private fun loadTex(name: String): Int {
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); val id = ids[0]
        try {
            val bmp = ctx.assets.open("textures/$name").use { BitmapFactory.decodeStream(it) }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
        } catch (e: Exception) { return 0 }
        return id
    }

    private fun buildIcosahedron(radius: Float, detail: Int) {
        val X=0.5257311121191336f;val Z=0.85065080835204f
        val bv=floatArrayOf(-X,0f,Z,X,0f,Z,-X,0f,-Z,X,0f,-Z,0f,Z,X,0f,Z,-X,0f,-Z,X,0f,-Z,-Z,Z,X,0f,-Z,X,0f,Z,-X,0f,-Z,-X,0f)
        val bi=shortArrayOf(0,4,1,0,9,4,9,5,4,4,5,8,4,8,1,8,10,1,8,3,10,5,3,8,5,2,3,2,7,3,7,10,3,7,6,10,7,11,6,11,0,6,0,1,6,6,1,10,9,0,11,9,11,2,9,2,5,7,2,11)
        var verts=bv.toMutableList();var indices=bi.map{it.toInt()}.toMutableList()
        for(d in 0 until detail){
            val nv=verts.toMutableList();val ni=mutableListOf<Int>();val em=mutableMapOf<Long,Int>()
            fun mid(a:Int,b:Int):Int{
                val k=if(a<b)(a.toLong()shl 32)or b.toLong() else (b.toLong()shl 32)or a.toLong()
                em[k]?.let{return it}
                val p=nv.size/3
                val mx=(verts[a*3]+verts[b*3])/2f;val my=(verts[a*3+1]+verts[b*3+1])/2f;val mz=(verts[a*3+2]+verts[b*3+2])/2f
                val len=sqrt((mx*mx+my*my+mz*mz).toDouble()).toFloat()
                nv.addAll(listOf(mx/len,my/len,mz/len));em[k]=p;return p
            }
            for(i in indices.indices step 3){val a=indices[i];val b=indices[i+1];val c=indices[i+2];val ab=mid(a,b);val bc=mid(b,c);val ca=mid(c,a);ni.addAll(listOf(a,ab,ca,b,bc,ab,c,ca,bc,ab,bc,ca))}
            verts=nv;indices=ni
        }
        val fv=FloatArray(verts.size/3*5)
        for(i in 0 until verts.size/3){
            fv[i*5]=verts[i*3]*radius;fv[i*5+1]=verts[i*3+1]*radius;fv[i*5+2]=verts[i*3+2]*radius
            fv[i*5+3]=((atan2(verts[i*3+2].toDouble(),verts[i*3].toDouble())/(2*PI)+0.5)).toFloat()
            fv[i*5+4]=((asin(verts[i*3+1].toDouble())/PI+0.5)).toFloat()
        }
        triCount=indices.size/3
        vertexBuf=ByteBuffer.allocateDirect(fv.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(fv);position(0)}
        indexBuf=ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply{indices.forEach{put(it.toShort())};position(0)}
    }
}
