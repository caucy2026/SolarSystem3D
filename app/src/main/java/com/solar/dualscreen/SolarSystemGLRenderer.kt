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
import kotlin.math.*

class SolarSystemGLRenderer(private val ctx: Context) : GLSurfaceView.Renderer {

    // ============ 行星数据 ============
    data class Planet(val name: String, val size: Float, val orbit: Float, val period: Float,
                      val color: FloatArray, val texName: String?, val ringInner: Float = 0f, val ringOuter: Float = 0f)

    private val planets = listOf(
        Planet("Mercury", 0.5f, 6f, 0.24f, floatArrayOf(0.7f,0.7f,0.7f), "2k_mercury.jpg"),
        Planet("Venus", 0.9f, 10f, 0.62f, floatArrayOf(0.9f,0.8f,0.6f), "2k_venus_atmosphere.jpg"),
        Planet("Earth", 1.0f, 14f, 1.0f, floatArrayOf(0.2f,0.4f,0.8f), "2k_earth_daymap.jpg"),
        Planet("Mars", 0.7f, 18f, 1.88f, floatArrayOf(0.8f,0.3f,0.1f), "2k_mars.jpg"),
        Planet("Jupiter", 2.0f, 26f, 11.9f, floatArrayOf(0.85f,0.7f,0.5f), "2k_jupiter.jpg"),
        Planet("Saturn", 1.7f, 34f, 29.5f, floatArrayOf(0.9f,0.85f,0.6f), null, 2.2f, 3.0f),
        Planet("Uranus", 1.3f, 44f, 84f, floatArrayOf(0.5f,0.7f,0.8f), null),
        Planet("Neptune", 1.2f, 52f, 165f, floatArrayOf(0.2f,0.3f,0.9f), null)
    )

    // ============ Shaders ============
    // 通用行星 shader（纹理或纯色）
    private val planetVert = """
        uniform mat4 uMVP; attribute vec4 aPos; attribute vec2 aUV; varying vec2 vUV;
        void main(){ vUV=aUV; gl_Position=uMVP*aPos; }
    """.trimIndent()
    private val planetFrag = """
        precision mediump float; varying vec2 vUV;
        uniform sampler2D uTex; uniform vec3 uColor; uniform float uHasTex;
        void main(){
            vec3 c = uHasTex>0.5 ? texture2D(uTex,vUV).rgb : uColor;
            gl_FragColor=vec4(c*1.2,1.0);
        }
    """.trimIndent()

    // 太阳 FBM shader（复用已验证的）
    private val sunFrag = """
        precision mediump float; varying vec2 vUV;
        uniform sampler2D uEmission,uPlasma,uHelio; uniform float uTime;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
        float fbm(vec2 p){float v=0.0,a=0.5;vec2 s=vec2(1.0);for(int i=0;i<4;i++){v+=a*noise(p*s);a*=0.5;s*=2.1;}return v;}
        void main(){
            vec3 base=texture2D(uEmission,vUV).rgb;
            vec2 pUV=vUV+vec2(sin(vUV.y*12.0+uTime*0.06)*0.003+uTime*0.002,cos(vUV.x*15.0+uTime*0.08)*0.003);
            vec3 plasma=texture2D(uPlasma,pUV).rgb;
            vec2 hUV=vUV+vec2(cos(vUV.y*8.0+uTime*0.04)*0.002,sin(vUV.x*10.0+uTime*0.05)*0.002);
            vec3 helio=texture2D(uHelio,hUV).rgb;
            vec3 col=base*1.1+plasma*0.35+helio*0.25;
            float wind=sin(uTime*1.3)*0.04+sin(uTime*2.1+vUV.y*5.0)*0.03;
            vec2 fUV=vUV*vec2(1.0,0.7)+vec2(wind,uTime*0.015);
            float flameNoise=fbm(fUV*3.0);
            float flameStreak=abs(fract(vUV.y*8.0+flameNoise*0.6)-0.5)*2.0;
            float flameShape=1.0-smoothstep(0.0,0.5,flameStreak)*(1.0-smoothstep(0.3,0.7,flameNoise));
            float flicker=0.7+0.3*sin(uTime*7.3+vUV.x*13.0)*cos(uTime*5.1+vUV.y*17.0);
            float flameMask=smoothstep(0.3,0.65,flameNoise)*smoothstep(0.0,0.25,flameShape)*flicker;
            float flameH=flameNoise*flameShape;
            vec3 flameCol=mix(vec3(0.8,0.15,0.01),mix(vec3(1.0,0.55,0.05),vec3(1.0,0.9,0.3),flameH),flameH);
            col=mix(col,col+flameCol*2.5,flameMask*0.55);
            gl_FragColor=vec4(col*1.5,1.0);
        }
    """.trimIndent()

    // 轨道线 shader
    private val lineVert = """
        uniform mat4 uMVP; attribute vec3 aPos;
        void main(){ gl_Position=uMVP*vec4(aPos,1.0); }
    """.trimIndent()
    private val lineFrag = """
        precision mediump float; uniform vec4 uColor;
        void main(){ gl_FragColor=uColor; }
    """.trimIndent()

    // ============ GL 资源 ============
    private var planetProg = 0; private var sunProg = 0; private var lineProg = 0
    private var started = false
    private var startTime = 0L

    // 相机
    var camTheta = 0f; var camPhi = 0.5f; var camDist = 55f
    private val vpMatrix = FloatArray(16); private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16); private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // 轨道线
    private var orbitVerts = FloatArray(0); private var orbitBuf: FloatBuffer? = null
    private var orbitCount = 0

    // 纹理缓存
    private val texCache = mutableMapOf<String, Int>()

    // FPS
    private var fpsN = 0; private var fpsT = 0L

    fun rotate(dt: Float, dp: Float) { camTheta += dt; camPhi = (camPhi + dp).coerceIn(0.05f, PI.toFloat() - 0.05f) }
    fun zoom(f: Float) { camDist = (camDist * f).coerceIn(8f, 200f) }

    // ============ Shader 工具 ============
    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type); GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { android.util.Log.e("SolarSys", "shader err: ${GLES20.glGetShaderInfoLog(s)}"); return 0 }
        return s
    }
    private fun linkProgram(vs: Int, fs: Int): Int {
        val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        return p
    }

    // ============ 几何体 ============
    private data class Mesh(val vb: FloatBuffer, val ib: ShortBuffer, val triCount: Int)

    private fun buildIcosahedron(radius: Float, detail: Int): Mesh {
        val X = 0.5257311121191336f; val Z = 0.85065080835204f
        val bv = floatArrayOf(-X,0f,Z, X,0f,Z, -X,0f,-Z, X,0f,-Z, 0f,Z,X, 0f,Z,-X, 0f,-Z,X, 0f,-Z,-Z, Z,X,0f, -Z,X,0f, Z,-X,0f, -Z,-X,0f)
        val bi = shortArrayOf(0,4,1,0,9,4,9,5,4,4,5,8,4,8,1,8,10,1,8,3,10,5,3,8,5,2,3,2,7,3,7,10,3,7,6,10,7,11,6,11,0,6,0,1,6,6,1,10,9,0,11,9,11,2,9,2,5,7,2,11)
        var verts = bv.toMutableList(); var indices = bi.map { it.toInt() }.toMutableList()
        for (d in 0 until detail) {
            val nv = verts.toMutableList(); val ni = mutableListOf<Int>()
            val em = mutableMapOf<Long, Int>()
            fun mid(a: Int, b: Int): Int {
                val k = if (a < b) (a.toLong() shl 32) or b.toLong() else (b.toLong() shl 32) or a.toLong()
                em[k]?.let { return it }
                val p = nv.size / 3
                val mx = (verts[a*3]+verts[b*3])/2f; val my = (verts[a*3+1]+verts[b*3+1])/2f; val mz = (verts[a*3+2]+verts[b*3+2])/2f
                val len = sqrt((mx*mx+my*my+mz*mz).toDouble()).toFloat()
                nv.addAll(listOf(mx/len, my/len, mz/len)); em[k] = p; return p
            }
            for (i in indices.indices step 3) {
                val a=indices[i];val b=indices[i+1];val c=indices[i+2]
                val ab=mid(a,b);val bc=mid(b,c);val ca=mid(c,a)
                ni.addAll(listOf(a,ab,ca,b,bc,ab,c,ca,bc,ab,bc,ca))
            }
            verts=nv;indices=ni
        }
        val fv = FloatArray(verts.size/3*5)
        for (i in 0 until verts.size/3) {
            fv[i*5]=verts[i*3]*radius;fv[i*5+1]=verts[i*3+1]*radius;fv[i*5+2]=verts[i*3+2]*radius
            fv[i*5+3]=((atan2(verts[i*3+2].toDouble(), verts[i*3].toDouble())/(2*PI)+0.5)).toFloat()
            fv[i*5+4]=((asin(verts[i*3+1].toDouble())/PI+0.5)).toFloat()
        }
        val vb = ByteBuffer.allocateDirect(fv.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(fv);position(0) }
        val ib = ByteBuffer.allocateDirect(indices.size*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { indices.forEach{put(it.toShort())};position(0) }
        return Mesh(vb, ib, indices.size/3)
    }

    private fun buildOrbitLine(radius: Float, segments: Int = 180): FloatArray {
        val v = FloatArray((segments+1)*3)
        for (i in 0..segments) { val a = (2*PI*i/segments).toFloat(); v[i*3]=cos(a)*radius; v[i*3+1]=0f; v[i*3+2]=sin(a)*radius }
        return v
    }

    // ============ 纹理 ============
    private fun loadTex(name: String): Int {
        texCache[name]?.let { return it }
        val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); val id = ids[0]
        try {
            val bmp = ctx.assets.open("textures/$name").use { BitmapFactory.decodeStream(it) }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0); bmp.recycle()
            texCache[name] = id
        } catch (e: Exception) {
            android.util.Log.w("SolarSys", "Texture $name not found, using color fallback")
            return 0
        }
        return id
    }

    // ============ 初始化 ============
    private lateinit var sunMesh: Mesh
    private val planetMeshes = mutableListOf<Mesh>()
    private var sunEmissionTex = 0; private var sunPlasmaTex = 0; private var sunHelioTex = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0.02f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // 编译 shader
        val pvs = compileShader(GLES20.GL_VERTEX_SHADER, planetVert)
        val pfs = compileShader(GLES20.GL_FRAGMENT_SHADER, planetFrag)
        planetProg = linkProgram(pvs, pfs)
        val svs = compileShader(GLES20.GL_VERTEX_SHADER, planetVert) // 复用顶点shader
        val sfs = compileShader(GLES20.GL_FRAGMENT_SHADER, sunFrag)
        sunProg = linkProgram(svs, sfs)
        val lvs = compileShader(GLES20.GL_VERTEX_SHADER, lineVert)
        val lfs = compileShader(GLES20.GL_FRAGMENT_SHADER, lineFrag)
        lineProg = linkProgram(lvs, lfs)

        // 太阳
        sunMesh = buildIcosahedron(3.5f, 4)
        sunEmissionTex = loadTex("2k_sun.jpg")
        sunPlasmaTex = createPlasmaTex(); sunHelioTex = createHelioTex()

        // 行星
        for (p in planets) {
            val detail = if (p.size >= 1.5f) 3 else 2
            planetMeshes.add(buildIcosahedron(p.size, detail))
            p.texName?.let { loadTex(it) }
        }

        // 轨道线
        val allOrbits = mutableListOf<Float>()
        for (p in planets) { allOrbits.addAll(buildOrbitLine(p.orbit).toList()) }
        orbitVerts = allOrbits.toFloatArray(); orbitCount = orbitVerts.size / 3
        orbitBuf = ByteBuffer.allocateDirect(orbitVerts.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(orbitVerts);position(0) }

        startTime = System.currentTimeMillis()
        started = true
        android.util.Log.i("SolarSys", "Init done: ${planets.size} planets, ${orbitCount/180} orbits")
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        Matrix.perspectiveM(projMatrix, 0, 45f, w.toFloat()/h, 0.3f, 600f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!started) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val t = (System.currentTimeMillis() - startTime) * 0.001f

        // 相机
        val ex = camDist * cos(camTheta.toDouble()).toFloat() * sin(camPhi.toDouble()).toFloat()
        val ey = camDist * cos(camPhi.toDouble()).toFloat()
        val ez = camDist * sin(camTheta.toDouble()).toFloat() * sin(camPhi.toDouble()).toFloat()
        Matrix.setLookAtM(viewMatrix, 0, ex, ey, ez, 0f, 0f, 0f, 0f, 1f, 0f)

        // --- 画轨道线 ---
        GLES20.glUseProgram(lineProg)
        orbitBuf?.let { ob ->
            ob.position(0)
            val ap = GLES20.glGetAttribLocation(lineProg, "aPos")
            GLES20.glVertexAttribPointer(ap, 3, GLES20.GL_FLOAT, false, 12, ob)
            GLES20.glEnableVertexAttribArray(ap)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(lineProg, "uMVP"), 1, false, mvpMatrix, 0)
            GLES20.glUniform4f(GLES20.glGetUniformLocation(lineProg, "uColor"), 0.25f, 0.3f, 0.4f, 0.28f)
            var offset = 0
            for (p in planets) {
                GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, offset, 181); offset += 181
            }
            GLES20.glDisableVertexAttribArray(ap)
        }

        // --- 画太阳 ---
        GLES20.glUseProgram(sunProg)
        drawSun(t)

        // --- 画行星 ---
        GLES20.glUseProgram(planetProg)
        for (i in planets.indices) {
            val p = planets[i]
            val angle = (t / p.period * 2f * PI).toFloat()
            val px = cos(angle) * p.orbit; val pz = sin(angle) * p.orbit
            val mesh = planetMeshes[i]
            val texId = p.texName?.let { texCache[it] } ?: 0
            drawPlanet(mesh, px, 0f, pz, p.size, t, p.color, texId)
        }

        // FPS
        fpsN++; val now = System.currentTimeMillis()
        if (now - fpsT >= 2000) {
            android.util.Log.i("SolarSys", "FPS: ${String.format("%.1f", fpsN*1000f/(now-fpsT))}")
            fpsN = 0; fpsT = now
        }
    }

    private fun drawSun(t: Float) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, t * 30f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)
        val mh = GLES20.glGetUniformLocation(sunProg, "uMVP")
        val th = GLES20.glGetUniformLocation(sunProg, "uTime")
        GLES20.glUniformMatrix4fv(mh, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(th, t)
        bindTex(GLES20.GL_TEXTURE0, sunEmissionTex, sunProg, "uEmission")
        bindTex(GLES20.GL_TEXTURE1, sunPlasmaTex, sunProg, "uPlasma")
        bindTex(GLES20.GL_TEXTURE2, sunHelioTex, sunProg, "uHelio")
        drawMesh(sunMesh, sunProg)
    }

    private fun drawPlanet(mesh: Mesh, x: Float, y: Float, z: Float, size: Float, t: Float, color: FloatArray, texId: Int) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, t * 50f / size, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(planetProg, "uMVP"), 1, false, mvpMatrix, 0)
        GLES20.glUniform3fv(GLES20.glGetUniformLocation(planetProg, "uColor"), 1, color, 0)
        val hasTex = if (texId != 0) { bindTex(GLES20.GL_TEXTURE0, texId, planetProg, "uTex"); 1.0f } else 0f
        GLES20.glUniform1f(GLES20.glGetUniformLocation(planetProg, "uHasTex"), hasTex)
        drawMesh(mesh, planetProg)
    }

    private fun bindTex(unit: Int, texId: Int, prog: Int, name: String) {
        GLES20.glActiveTexture(unit); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(prog, name), unit - GLES20.GL_TEXTURE0)
    }

    private fun drawMesh(m: Mesh, prog: Int) {
        val ap = GLES20.glGetAttribLocation(prog, "aPos")
        val au = GLES20.glGetAttribLocation(prog, "aUV")
        m.vb.position(0)
        GLES20.glVertexAttribPointer(ap, 3, GLES20.GL_FLOAT, false, 20, m.vb); GLES20.glEnableVertexAttribArray(ap)
        m.vb.position(3)
        GLES20.glVertexAttribPointer(au, 2, GLES20.GL_FLOAT, false, 20, m.vb); GLES20.glEnableVertexAttribArray(au)
        m.ib.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.triCount * 3, GLES20.GL_UNSIGNED_SHORT, m.ib)
        GLES20.glDisableVertexAttribArray(ap); GLES20.glDisableVertexAttribArray(au)
    }

    // ============ 太阳程序化纹理 ============
    private fun createPlasmaTex(): Int {
        val w=256;val h=128;val pix=IntArray(w*h);val r=java.util.Random(42)
        for(ri in 0..4){val rx=30+r.nextInt(w-60);val ry=15+r.nextInt(h-30);val rr=12+r.nextInt(20)
            for(y in maxOf(0,ry-rr)until minOf(h,ry+rr))for(x in maxOf(0,rx-rr)until minOf(w,rx+rr)){
                val dx=(x-rx).toFloat()/rr;val dy=(y-ry).toFloat()/rr;val d=sqrt((dx*dx+dy*dy).toDouble()).toFloat()
                if(d<1f){val a=((1f-d)*(1f-d)*0.4f*255).toInt();val idx=y*w+x;val ex=pix[idx]
                    pix[idx]=(255 shl 24)or(minOf(255,(ex shr 16 and 0xFF)+a)shl 16)or(minOf(255,(ex shr 8 and 0xFF)+(a*0.7f).toInt())shl 8)or(minOf(255,(ex and 0xFF)+(a*0.4f).toInt()))}}}
        return uploadTex(pix,w,h)
    }
    private fun createHelioTex(): Int {
        val w=256;val h=128;val pix=IntArray(w*h);val r=java.util.Random(99)
        for(i in 0..200){val x=r.nextInt(w);val y=r.nextInt(h);val ed=minOf(y,h-y).toFloat()/(h/2f);if(r.nextFloat()>ed*0.5f+0.05f)continue
            val dy=if(y<h/2)-1f else 1f;val a=dy*(-PI.toFloat()/2)+(r.nextFloat()-0.5f)*0.6f;val l=6+r.nextInt(25)
            val ex=x+(cos(a.toDouble())*l).toInt();val ey=y+(sin(a.toDouble())*l).toInt()
            bresenham(pix,w,h,x,y,ex,ey,255,140,40,8+r.nextInt(35))}
        return uploadTex(pix,w,h)
    }
    private fun bresenham(pix:IntArray,w:Int,h:Int,x0:Int,y0:Int,x1:Int,y1:Int,R:Int,G:Int,B:Int,A:Int){
        var x=x0;var y=y0;val dx=abs(x1-x0);val dy=-abs(y1-y0);val sx=if(x0<x1)1 else -1;val sy=if(y0<y1)1 else -1;var err=dx+dy
        while(true){
            if(x in 0 until w&&y in 0 until h){val idx=y*w+x;val ex=pix[idx];pix[idx]=(255 shl 24)or(minOf(255,(ex shr 16 and 0xFF)+A*R/255)shl 16)or(minOf(255,(ex shr 8 and 0xFF)+A*G/255)shl 8)or(minOf(255,(ex and 0xFF)+A*B/255))}
            if(x==x1&&y==y1)break;val e2=2*err
            if(e2>=dy){err+=dy;x+=sx}
            if(e2<=dx){err+=dx;y+=sy}
        }
    }
    private fun uploadTex(pix:IntArray,w:Int,h:Int):Int{
        val ids=IntArray(1);GLES20.glGenTextures(1,ids,0);val id=ids[0]
        val buf=ByteBuffer.allocateDirect(pix.size*4).order(ByteOrder.nativeOrder()).asIntBuffer().apply{put(pix);position(0)}
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,w,h,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,buf)
        return id
    }
}
