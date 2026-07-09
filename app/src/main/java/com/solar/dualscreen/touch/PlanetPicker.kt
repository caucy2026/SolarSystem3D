package com.solar.dualscreen.touch

import android.view.MotionEvent
import com.solar.dualscreen.render.Camera3D
import com.solar.dualscreen.render.SolarRenderer
import com.solar.dualscreen.util.Mat4
import com.solar.dualscreen.util.PlanetData
import com.solar.dualscreen.util.PlanetData.CelestialBody
import com.solar.dualscreen.util.Vec3
import kotlin.math.*

class PlanetPicker(private val renderer: SolarRenderer) {
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val dragThreshold = 10f

    fun onTouchEvent(event: MotionEvent, viewportW: Int, viewportH: Int): CelestialBody? {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                dragStartX = event.x; dragStartY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    if (abs(dx) + abs(dy) > 2f) {
                        renderer.camera.rotate(dx, dy)
                        lastX = event.x; lastY = event.y
                    }
                    if (abs(event.x - dragStartX) + abs(event.y - dragStartY) > dragThreshold) isDragging = true
                } else if (event.pointerCount == 2) isDragging = true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) return pickPlanet(event.x, event.y, viewportW, viewportH)
            }
        }
        return null
    }

    private fun pickPlanet(screenX: Float, screenY: Float, vpW: Int, vpH: Int): CelestialBody? {
        val camera = renderer.camera
        val proj = camera.getProjectionMatrix(vpW.toFloat() / vpH, far = 600f)
        val view = camera.getViewMatrix()
        val invVP = (proj * view).inverse()
        val ndcX = (2f * screenX) / vpW - 1f
        val ndcY = 1f - (2f * screenY) / vpH
        val nearPoint = invVP.transform(Vec3(ndcX, ndcY, -1f))
        val farPoint = invVP.transform(Vec3(ndcX, ndcY, 1f))
        val rayDir = (farPoint - nearPoint).normalize()
        val rayOrigin = nearPoint

        var closestDist = Float.MAX_VALUE
        var closestBody: CelestialBody? = null

        // 太阳
        testBody(PlanetData.SUN, rayOrigin, rayDir, 0f, 0f)?.let {
            if (it < closestDist) { closestDist = it; closestBody = PlanetData.SUN }
        }
        // 行星
        val simTime = renderer.simTime
        for (planet in PlanetData.ALL_PLANETS) {
            val orbitR = orbitOf(planet)
            val orbitAngle = ((simTime / planet.orbitPeriod) * 360f) % 360f
            val px = orbitR * cos(orbitAngle * PI.toFloat() / 180f)
            val pz = orbitR * sin(orbitAngle * PI.toFloat() / 180f)
            testBody(planet, rayOrigin, rayDir, px, pz)?.let {
                if (it < closestDist) { closestDist = it; closestBody = planet }
            }
        }
        return closestBody
    }

    private fun orbitOf(body: CelestialBody): Float = when (body.nameEn) {
        "Mercury" -> 6f; "Venus" -> 10f; "Earth" -> 15f; "Mars" -> 20f
        "Jupiter" -> 28f; "Saturn" -> 37f; "Uranus" -> 47f; "Neptune" -> 55f
        else -> 0f
    }

    private fun radiusOf(body: CelestialBody): Float = when (body.nameEn) {
        "Sun" -> 3.5f; "Jupiter" -> 2.0f; "Saturn" -> 1.7f; "Uranus" -> 1.3f
        "Neptune" -> 1.2f; "Earth" -> 1.0f; "Venus" -> 0.95f; "Mars" -> 0.7f; "Mercury" -> 0.5f
        else -> 0.3f
    }

    private fun testBody(body: CelestialBody, rayOrigin: Vec3, rayDir: Vec3, px: Float, pz: Float): Float? {
        val center = Vec3(px, 0f, pz)
        val hitR = radiusOf(body) * 1.5f  // 扩大 50% 便于触摸
        val oc = rayOrigin - center
        val b = oc.dot(rayDir)
        val c = oc.dot(oc) - hitR * hitR
        val disc = b * b - c
        if (disc < 0f) return null
        val t = -b - sqrt(disc)
        return if (t > 0f) t else null
    }
}
