package com.solar.dualscreen

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SolarPresentation : AppCompatActivity() {
    companion object { private const val TAG = "SolarPresentation" }

    private lateinit var webView: WebView
    var onPlanetSelected: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 同步下载所有纹理
        ensureTextures()

        val texDir = File(filesDir, "planet_textures")
        webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    // solarsystemscope textures
                    if (url.contains("solarsystemscope.com/textures/download/")) {
                        val filename = url.substringAfterLast('/')
                        val file = File(texDir, filename)
                        if (file.exists() && file.length() > 5000) {
                            val bytes = file.readBytes()
                            val mime = if (filename.endsWith(".png")) "image/png" else "image/jpeg"
                            val headers = mapOf("Access-Control-Allow-Origin" to "*")
                            Log.i(TAG, "Serving: $filename (${bytes.size/1024}KB)")
                            return WebResourceResponse(mime, "binary", 200, "OK", headers, ByteArrayInputStream(bytes))
                        } else {
                            Log.w(TAG, "Not found locally: $filename")
                        }
                    }
                    // STL 3D models: http://local.stl/filename.stl
                    if (url.startsWith("http://local.stl/")) {
                        val filename = url.removePrefix("http://local.stl/")
                        try {
                            val stlBytes = assets.open("models/$filename").readBytes()
                            Log.i(TAG, "Serving STL: $filename (${stlBytes.size} bytes)")
                            return WebResourceResponse("application/octet-stream", "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(stlBytes))
                        } catch (e: Exception) {
                            Log.e(TAG, "STL not found: $filename", e)
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            addJavascriptInterface(AndroidBridge(), "Android")
        }
        webView.loadUrl("file:///android_asset/solar.html")
        setContentView(webView)
        SolarState.solarPresentation = this
    }

    private fun ensureTextures() {
        val texDir = File(filesDir, "planet_textures").apply { mkdirs() }
        val needed = listOf(
            "2k_sun.jpg", "2k_mercury.jpg", "2k_venus_atmosphere.jpg",
            "2k_earth_daymap.jpg", "2k_mars.jpg", "2k_jupiter.jpg",
            "2k_saturn.jpg", "2k_saturn_ring_alpha.png", "2k_uranus.jpg", "2k_neptune.jpg",
            "2k_moon.jpg",
            "2k_stars_milky_way.jpg"
        )
        val baseUrl = "https://www.solarsystemscope.com/textures/download/"
        for (f in needed) {
            val file = File(texDir, f)
            if (file.exists() && file.length() > 5000) continue  // 5KB 足够（光环PNG小）
            Log.i(TAG, "Downloading: $f ...")
            try {
                val conn = URL(baseUrl + f).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 30000
                if (conn.responseCode == 200) {
                    file.outputStream().use { out -> conn.inputStream.copyTo(out) }
                    Log.i(TAG, "Downloaded: $f (${file.length()/1024}KB)")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Download error: $f ${e.message}")
            }
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onPlanetSelected(name: String) {
            onPlanetSelected?.invoke(name)
        }
    }

    override fun onBackPressed() { SolarState.mainActivity?.finishAffinity(); super.onBackPressed() }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); SolarState.mainActivity?.finishAffinity() }
    override fun onDestroy() { SolarState.solarPresentation = null; webView.destroy(); super.onDestroy() }
}
