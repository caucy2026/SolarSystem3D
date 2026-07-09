package com.solar.dualscreen.render

import android.opengl.GLES30
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class SphereMesh(latBands: Int = 32, lonBands: Int = 16) {

    private val indexCount: Int
    private var vboVertex = 0
    private var vboIndex = 0
    val stride = 8 * 4

    init {
        val vertices = mutableListOf<Float>()
        val indices = mutableListOf<Short>()
        for (lat in 0..latBands) {
            val theta = lat * PI / latBands
            val st = sin(theta).toFloat(); val ct = cos(theta).toFloat()
            for (lon in 0..lonBands) {
                val phi = lon * 2.0 * PI / lonBands
                val sp = sin(phi).toFloat(); val cp = cos(phi).toFloat()
                val x = cp * st; val y = ct; val z = sp * st
                val u = 1f - lon.toFloat() / lonBands; val v = 1f - lat.toFloat() / latBands
                vertices.addAll(listOf(x, y, z, x, y, z, u, v))
            }
        }
        for (lat in 0 until latBands) {
            for (lon in 0 until lonBands) {
                val f = (lat * (lonBands + 1) + lon).toShort()
                val s = (f + lonBands + 1).toShort()
                indices.addAll(listOf(f, s, (f + 1).toShort(), s, (s + 1).toShort(), (f + 1).toShort()))
            }
        }
        indexCount = indices.size

        // Upload vertices to GPU VBO
        val vb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices.toFloatArray()).flip()
        val bufs = IntArray(2); GLES30.glGenBuffers(2, bufs, 0)
        vboVertex = bufs[0]; vboIndex = bufs[1]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboVertex)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)

        // Upload indices to GPU VBO
        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices.toShortArray()).flip()
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vboIndex)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun bind(program: Int, posLoc: Int = 0, normalLoc: Int = 1, texLoc: Int = 2) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboVertex)
        GLES30.glVertexAttribPointer(posLoc, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(posLoc)
        if (normalLoc != posLoc) {
            GLES30.glVertexAttribPointer(normalLoc, 3, GLES30.GL_FLOAT, false, stride, 12)
            GLES30.glEnableVertexAttribArray(normalLoc)
        }
        if (texLoc != posLoc && texLoc != normalLoc) {
            GLES30.glVertexAttribPointer(texLoc, 2, GLES30.GL_FLOAT, false, stride, 24)
            GLES30.glEnableVertexAttribArray(texLoc)
        }
    }

    fun draw() {
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, vboIndex)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
    }
}
