package com.solar.dualscreen.render

import android.opengl.GLES30
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * 光环网格 — 内径到外径的环形 strip。
 * segments 控制圆周分段数。
 */
class RingMesh(innerRadius: Float = 1.2f, outerRadius: Float = 2.25f, segments: Int = 128) {

    private val vertexCount: Int
    private val indexCount: Int
    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer

    val stridePosTex = 5 * 4  // pos(3) + tex(2)

    init {
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        for (i in 0..segments) {
            val angle = i * 2.0 * PI / segments
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            // 光环在 XZ 平面
            // 外圈顶点
            vertices.addAll(listOf(cosA * outerRadius, 0f, sinA * outerRadius, 1f, 0f))
            // 内圈顶点
            vertices.addAll(listOf(cosA * innerRadius, 0f, sinA * innerRadius, 0f, 0f))
        }

        for (i in 0 until segments) {
            val a = (i * 2).toShort()
            val b = (a + 1).toShort()
            val c = (a + 2).toShort()
            val d = (a + 3).toShort()
            indices.addAll(listOf(a, b, c, c, b, d))
        }

        vertexCount = vertices.size / 5
        indexCount = indices.size

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices.toFloatArray()).flip()

        indexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        indexBuffer.put(indices.toShortArray()).flip()
    }

    fun bind(program: Int, posLoc: Int = 0, texLoc: Int = 1) {
        GLES30.glVertexAttribPointer(posLoc, 3, GLES30.GL_FLOAT, false, stridePosTex, vertexBuffer)
        GLES30.glEnableVertexAttribArray(posLoc)

        vertexBuffer.position(3)
        GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, stridePosTex, vertexBuffer)
        GLES30.glEnableVertexAttribArray(texLoc)

        vertexBuffer.position(0)
    }

    fun draw() {
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, indexBuffer)
    }
}
