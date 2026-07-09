package com.solar.dualscreen.touch

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.solar.dualscreen.render.Camera3D

/**
 * Arcball 旋转 + Pinch 缩放控制器。
 * 用于主屏单星球细节视图。
 */
class ArcballController(private val camera: Camera3D) {

    private var lastX = 0f
    private var lastY = 0f
    private var isRotating = false

    private var scaleDetector: ScaleGestureDetector? = null
    private var scaleFactor = 1f

    fun attach(scaleDetector: ScaleGestureDetector) {
        this.scaleDetector = scaleDetector
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector?.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    lastX = event.x
                    lastY = event.y
                    isRotating = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isRotating && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    camera.rotate(dx, dy)
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isRotating = false
            }
        }
        return true
    }
}
