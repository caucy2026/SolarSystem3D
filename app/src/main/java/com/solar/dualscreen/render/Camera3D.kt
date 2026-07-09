package com.solar.dualscreen.render

import com.solar.dualscreen.util.Mat4
import com.solar.dualscreen.util.Vec3
import kotlin.math.*

/**
 * 3D 相机 — Arcball 旋转 + 缩放控制。
 * 使用球坐标系围绕目标点旋转。
 */
class Camera3D {
    /** 目标点 */
    var target = Vec3(0f, 0f, 0f)

    /** 相机到目标的距离 */
    var distance = 15f
        set(v) { field = v.coerceIn(minDist, maxDist) }

    /** 旋转角 (度) — 水平 */
    var theta = 0f   // 绕 Y 轴

    /** 旋转角 (度) — 垂直 */
    var phi = 30f    // 偏离水平面的仰角

    var minDist = 1f
    var maxDist = 100f

    private val sensitivityRotate = 0.3f
    private val sensitivityZoom = 0.01f

    /** 获取相机世界位置 */
    fun getPosition(): Vec3 {
        val tRad = theta * PI.toFloat() / 180f
        val pRad = phi * PI.toFloat() / 180f
        val x = target.x + distance * cos(pRad) * sin(tRad)
        val y = target.y + distance * sin(pRad)
        val z = target.z + distance * cos(pRad) * cos(tRad)
        return Vec3(x, y, z)
    }

    /** 获取 lookAt 矩阵 */
    fun getViewMatrix(): Mat4 {
        return Mat4.lookAt(getPosition(), target, Vec3.UP)
    }

    /** 拖拽旋转 */
    fun rotate(dx: Float, dy: Float) {
        theta -= dx * sensitivityRotate
        phi += dy * sensitivityRotate
        phi = phi.coerceIn(-89f, 89f)
    }

    /** 缩放 */
    fun zoom(delta: Float) {
        distance -= delta * sensitivityZoom * distance
    }

    /** 聚焦到某一点 */
    fun focusOn(newTarget: Vec3, newDistance: Float? = null) {
        target = newTarget
        if (newDistance != null) distance = newDistance
    }

    /** 获取投影矩阵 (perspective) */
    fun getProjectionMatrix(aspect: Float, fov: Float = 45f, near: Float = 0.1f, far: Float = 1000f): Mat4 {
        return Mat4.perspective(fov, aspect, near, far)
    }
}
