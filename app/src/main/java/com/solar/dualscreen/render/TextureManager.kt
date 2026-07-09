package com.solar.dualscreen.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.solar.dualscreen.util.PlanetData
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * 纹理管理器 — 加载/缓存/下载星球纹理。
 * 所有星球统一使用单张等距矩形投影纹理。
 */
object TextureManager {
    private val textureCache = ConcurrentHashMap<String, Int>()
    private const val TEXTURE_DIR = "planet_textures"

    /** 获取纹理 ID，若未加载则返回 0 */
    fun getTextureId(key: String): Int = textureCache[key] ?: 0

    /** 从 assets 加载纹理 */
    fun loadFromAssets(context: Context, assetPath: String, key: String): Int {
        textureCache[key]?.let { return it }
        try {
            val input = context.assets.open(assetPath)
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            if (bitmap == null) return 0
            val id = createGLTexture(bitmap)
            bitmap.recycle()
            textureCache[key] = id
            Log.i("TextureManager", "Loaded from assets: $key")
            return id
        } catch (e: Exception) {
            Log.w("TextureManager", "Failed to load $assetPath: ${e.message}")
            return 0
        }
    }

    /** 从本地文件加载纹理 */
    fun loadFromFile(context: Context, filename: String, key: String): Int {
        textureCache[key]?.let { return it }
        val dir = File(context.filesDir, TEXTURE_DIR)
        val file = File(dir, filename)
        if (!file.exists()) return 0
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) return 0
            val id = createGLTexture(bitmap)
            bitmap.recycle()
            textureCache[key] = id
            Log.i("TextureManager", "Loaded from file: $filename")
            id
        } catch (e: Exception) {
            Log.w("TextureManager", "Failed to load $filename: ${e.message}")
            0
        }
    }

    /** 从程序化颜色创建纯色纹理 (用于 fallback) */
    fun createColorTexture(r: Float, g: Float, b: Float, key: String): Int {
        textureCache[key]?.let { return it }
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        val color = (255 shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        bitmap.eraseColor(color)
        val id = createGLTexture(bitmap)
        bitmap.recycle()
        textureCache[key] = id
        return id
    }

    /** 异步下载纹理 */
    fun downloadTexture(context: Context, body: PlanetData.CelestialBody, highRes: Boolean = false,
                        onComplete: ((Boolean) -> Unit)? = null) {
        val url = PlanetData.getTextureUrl(body, highRes) ?: return
        val filename = url.substringAfterLast('/')

        Thread {
            try {
                val dir = File(context.filesDir, TEXTURE_DIR)
                dir.mkdirs()
                val file = File(dir, filename)
                if (file.exists()) {
                    onComplete?.invoke(true)
                    return@Thread
                }

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                if (conn.responseCode == 200) {
                    val bitmap = BitmapFactory.decodeStream(conn.inputStream)
                    bitmap?.let {
                        FileOutputStream(file).use { out ->
                            it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        }
                        it.recycle()
                    }
                    conn.disconnect()
                    onComplete?.invoke(true)
                    Log.i("TextureManager", "Downloaded: $filename")
                } else {
                    Log.w("TextureManager", "Download failed: $url code=${conn.responseCode}")
                    onComplete?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e("TextureManager", "Download error: $url ${e.message}")
                onComplete?.invoke(false)
            }
        }.start()
    }

    /** 判断本地纹理文件是否存在 */
    fun hasLocalTexture(context: Context, body: PlanetData.CelestialBody): Boolean {
        val filename = body.texture2k ?: return false
        val file = File(context.filesDir, "$TEXTURE_DIR/$filename")
        return file.exists()
    }

    private fun createGLTexture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        return ids[0]
    }

    fun deleteTexture(key: String) {
        textureCache.remove(key)?.let {
            GLES30.glDeleteTextures(1, intArrayOf(it), 0)
        }
    }

    fun clearAll() {
        textureCache.values.forEach {
            GLES30.glDeleteTextures(1, intArrayOf(it), 0)
        }
        textureCache.clear()
    }
}
