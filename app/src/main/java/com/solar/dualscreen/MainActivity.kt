package com.solar.dualscreen

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
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

class MainActivity : AppCompatActivity() {
    companion object { private const val TAG = "MainActivity" }

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        @Suppress("DEPRECATION")
        if (windowManager.defaultDisplay.displayId != Display.DEFAULT_DISPLAY) {
            val opts = ActivityOptions.makeBasic()
            opts.launchDisplayId = Display.DEFAULT_DISPLAY
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK), opts.toBundle())
            finish(); return
        }

val texDir = File(filesDir, "planet_textures")
        webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                    Log.i(TAG, "JS|${msg.messageLevel()}| ${msg.message()}")
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    // SSS textures: 优先从 assets 读取
                    if (url.contains("solarsystemscope.com/textures/download/")) {
                        val filename = url.substringAfterLast('/')
                        // NASA 纹理走 assets/textures/nasa/
                        if (url.contains("/nasa/")) {
                            try {
                                val decodedName = java.net.URLDecoder.decode(filename, "UTF-8")
                                val bytes = assets.open("textures/nasa/$decodedName").readBytes()
                                Log.i(TAG, "Serving NASA: $decodedName (${bytes.size/1024}KB)")
                                return WebResourceResponse("image/jpeg", "binary", 200, "OK",
                                    mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))
                            } catch (_: Exception) {}
                        }
                        // 优先 assets (预编译离线可用)
                        try {
                            val assetBytes = assets.open("textures/$filename").readBytes()
                            val mime = if (filename.endsWith(".png")) "image/png" else "image/jpeg"
                            return WebResourceResponse(mime, "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(assetBytes))
                        } catch (_: Exception) {}
                        // 回退: 运行时下载缓存
                        val file = File(texDir, filename)
                        if (file.exists() && file.length() > 5000) {
                            val bytes = file.readBytes()
                            val mime = if (filename.endsWith(".png")) "image/png" else "image/jpeg"
                            return WebResourceResponse(mime, "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))
                        }
                    }
                    // Steve Albers 纹理 (卫星真实照片): 从 assets 或缓存读取
                    if (url.contains("stevealbers.net/albers/sos/")) {
                        val filename = url.substringAfterLast('/')
                        // 优先 assets
                        try {
                            val assetBytes = assets.open("textures/$filename").readBytes()
                            val mime = if (filename.endsWith(".png")) "image/png" else "image/jpeg"
                            return WebResourceResponse(mime, "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(assetBytes))
                        } catch (_: Exception) {}
                        // 回退缓存
                        val file = File(texDir, filename)
                        if (file.exists() && file.length() > 5000) {
                            val bytes = file.readBytes()
                            val mime = if (filename.endsWith(".png")) "image/png" else "image/jpeg"
                            return WebResourceResponse(mime, "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))
                        }
                    }
                    // NASA moon textures: http://local.nasa/filename.jpg → assets/textures/nasa/
                    if (url.startsWith("http://local.nasa/")) {
                        val filename = url.substringAfterLast('/')
                        try {
                            val bytes = assets.open("textures/nasa/$filename").readBytes()
                            Log.i(TAG, "Serving NASA: $filename (${bytes.size/1024}KB)")
                            return WebResourceResponse("image/jpeg", "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))
                        } catch (_: Exception) {}
                    }
                    // NASA moon textures: serve from local assets for offline use
                    if (url.contains("raw.githubusercontent.com/nasa/NASA-3D-Resources")) {
                        val filename = url.substringAfterLast('/')
                        try {
                            val bytes = assets.open("textures/nasa/$filename").readBytes()
                            Log.i(TAG, "Serving NASA texture: $filename (${bytes.size/1024}KB)")
                            return WebResourceResponse("image/jpeg", "binary", 200, "OK",
                                mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(bytes))
                        } catch (_: Exception) {}
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
            isFocusable = true
            isFocusableInTouchMode = true
            addJavascriptInterface(AndroidBridge(), "Android")
        }
        val wv = webView!!
        wv.loadUrl("file:///android_asset/solar.html?focus=Jupiter")
        wv.requestFocus()
        setContentView(wv)

        SolarState.mainActivity = this
        handler.postDelayed({ launchSolarScreen() }, 500)
    }

    private fun launchSolarScreen() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val myId = display?.displayId ?: 0
        for (d in dm.displays) {
            if (d.displayId != myId && d.isValid) {
                val intent = Intent(this, SolarPresentation::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    try {
                        val opts = ActivityOptions.makeBasic()
                        opts.javaClass.getMethod("setLaunchDisplayId", Int::class.javaPrimitiveType)
                            .invoke(opts, d.displayId)
                        startActivity(intent, opts.toBundle())
                    } catch (e: Exception) { startActivity(intent) }
                } else startActivity(intent)
                handler.postDelayed({
                    SolarState.solarPresentation?.let { pres ->
                        pres.onPlanetSelected = { name -> runOnUiThread { selectPlanet(name) } }
                    }
                }, 800)
                return
            }
        }
    }

    fun selectPlanet(name: String) {
        Log.i(TAG, "selectPlanet: $name")
        webView?.evaluateJavascript("onSelectPlanet('$name')", null)
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun onPlanetSelected(name: String) { runOnUiThread { selectPlanet(name) } }

        @JavascriptInterface
        fun getMemoryInfo(): String {
            val rt = Runtime.getRuntime()
            val usedMB = (rt.totalMemory() - rt.freeMemory()) / 1048576
            val maxMB = rt.maxMemory() / 1048576
            val totalMB = rt.totalMemory() / 1048576
            val nativeMB = android.os.Debug.getNativeHeapAllocatedSize() / 1048576
            return "$usedMB/$totalMB/$maxMB/$nativeMB"
        }
    }

    override fun onBackPressed() { exitApp() }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); finishAffinity() }
    override fun onDestroy() {
        try { SolarState.solarPresentation?.finish() } catch (_: Exception) {}
        SolarState.solarPresentation = null; SolarState.mainActivity = null
        webView?.destroy(); super.onDestroy()
    }
    private fun exitApp() {
        try { SolarState.solarPresentation?.finish() } catch (_: Exception) {}
        handler.postDelayed({ finishAffinity() }, 100)
    }
}
