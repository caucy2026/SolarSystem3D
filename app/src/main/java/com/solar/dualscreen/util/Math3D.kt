package com.solar.dualscreen.util

import kotlin.math.*

// ========== Vec3 ==========
data class Vec3(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f) {
    operator fun plus(v: Vec3) = Vec3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vec3) = Vec3(x - v.x, y - v.y, z - v.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)

    fun dot(v: Vec3): Float = x * v.x + y * v.y + z * v.z
    fun cross(v: Vec3): Vec3 = Vec3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun normalize(): Vec3 {
        val len = length()
        return if (len > 1e-6f) this / len else Vec3()
    }

    fun distanceTo(v: Vec3): Float = (this - v).length()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}

// ========== Mat4 (column-major, OpenGL compatible) ==========
class Mat4 {
    // Column-major: m[col * 4 + row]
    val m = FloatArray(16)

    companion object {
        fun identity(): Mat4 {
            val r = Mat4()
            r.m[0] = 1f; r.m[5] = 1f; r.m[10] = 1f; r.m[15] = 1f
            return r
        }

        fun perspective(fovY: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val r = Mat4()
            val f = 1f / tan(fovY * 0.5f * PI.toFloat() / 180f)
            r.m[0] = f / aspect
            r.m[5] = f
            r.m[10] = (far + near) / (near - far)
            r.m[11] = -1f
            r.m[14] = (2f * far * near) / (near - far)
            return r
        }

        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalize()
            val s = f.cross(up.normalize()).normalize()
            val u = s.cross(f)
            val r = Mat4()
            r.m[0] = s.x;  r.m[4] = s.y;  r.m[8] = s.z
            r.m[1] = u.x;  r.m[5] = u.y;  r.m[9] = u.z
            r.m[2] = -f.x; r.m[6] = -f.y; r.m[10] = -f.z
            r.m[12] = -s.dot(eye)
            r.m[13] = -u.dot(eye)
            r.m[14] = f.dot(eye)
            r.m[15] = 1f
            return r
        }

        fun translate(tx: Float, ty: Float, tz: Float): Mat4 {
            val r = identity()
            r.m[12] = tx; r.m[13] = ty; r.m[14] = tz
            return r
        }

        fun rotate(angleDeg: Float, ax: Float, ay: Float, az: Float): Mat4 {
            val a = angleDeg * PI.toFloat() / 180f
            val c = cos(a); val s = sin(a)
            val axis = Vec3(ax, ay, az).normalize()
            val t = 1f - c
            val r = Mat4()
            r.m[0] = t * axis.x * axis.x + c
            r.m[1] = t * axis.x * axis.y + s * axis.z
            r.m[2] = t * axis.x * axis.z - s * axis.y
            r.m[4] = t * axis.x * axis.y - s * axis.z
            r.m[5] = t * axis.y * axis.y + c
            r.m[6] = t * axis.y * axis.z + s * axis.x
            r.m[8] = t * axis.x * axis.z + s * axis.y
            r.m[9] = t * axis.y * axis.z - s * axis.x
            r.m[10] = t * axis.z * axis.z + c
            r.m[15] = 1f
            return r
        }

        fun scale(sx: Float, sy: Float, sz: Float): Mat4 {
            val r = Mat4()
            r.m[0] = sx; r.m[5] = sy; r.m[10] = sz; r.m[15] = 1f
            return r
        }
    }

    operator fun times(other: Mat4): Mat4 {
        val r = Mat4()
        for (col in 0..3) {
            for (row in 0..3) {
                var sum = 0f
                for (k in 0..3) sum += other.m[col * 4 + k] * m[k * 4 + row]
                r.m[col * 4 + row] = sum
            }
        }
        return r
    }

    fun transform(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12]
        val y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13]
        val z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        val w = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15]
        return if (abs(w) > 1e-6f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    fun transpose(): Mat4 {
        val r = Mat4()
        for (i in 0..3) for (j in 0..3) r.m[j * 4 + i] = m[i * 4 + j]
        return r
    }

    fun inverse(): Mat4 {
        val inv = FloatArray(16)
        val m0 = m
        inv[0] = m0[5] * m0[10] * m0[15] - m0[5] * m0[11] * m0[14] - m0[9] * m0[6] * m0[15] +
                 m0[9] * m0[7] * m0[14] + m0[13] * m0[6] * m0[11] - m0[13] * m0[7] * m0[10]
        inv[4] = -m0[4] * m0[10] * m0[15] + m0[4] * m0[11] * m0[14] + m0[8] * m0[6] * m0[15] -
                  m0[8] * m0[7] * m0[14] - m0[12] * m0[6] * m0[11] + m0[12] * m0[7] * m0[10]
        inv[8] = m0[4] * m0[9] * m0[15] - m0[4] * m0[11] * m0[13] - m0[8] * m0[5] * m0[15] +
                 m0[8] * m0[7] * m0[13] + m0[12] * m0[5] * m0[11] - m0[12] * m0[7] * m0[9]
        inv[12] = -m0[4] * m0[9] * m0[14] + m0[4] * m0[10] * m0[13] + m0[8] * m0[5] * m0[14] -
                   m0[8] * m0[6] * m0[13] - m0[12] * m0[5] * m0[10] + m0[12] * m0[6] * m0[9]
        inv[1] = -m0[1] * m0[10] * m0[15] + m0[1] * m0[11] * m0[14] + m0[9] * m0[2] * m0[15] -
                  m0[9] * m0[3] * m0[14] - m0[13] * m0[2] * m0[11] + m0[13] * m0[3] * m0[10]
        inv[5] = m0[0] * m0[10] * m0[15] - m0[0] * m0[11] * m0[14] - m0[8] * m0[2] * m0[15] +
                 m0[8] * m0[3] * m0[14] + m0[12] * m0[2] * m0[11] - m0[12] * m0[3] * m0[10]
        inv[9] = -m0[0] * m0[9] * m0[15] + m0[0] * m0[11] * m0[13] + m0[8] * m0[1] * m0[15] -
                  m0[8] * m0[3] * m0[13] - m0[12] * m0[1] * m0[11] + m0[12] * m0[3] * m0[9]
        inv[13] = m0[0] * m0[9] * m0[14] - m0[0] * m0[10] * m0[13] - m0[8] * m0[1] * m0[14] +
                  m0[8] * m0[2] * m0[13] + m0[12] * m0[1] * m0[10] - m0[12] * m0[2] * m0[9]
        inv[2] = m0[1] * m0[6] * m0[15] - m0[1] * m0[7] * m0[14] - m0[5] * m0[2] * m0[15] +
                 m0[5] * m0[3] * m0[14] + m0[13] * m0[2] * m0[7] - m0[13] * m0[3] * m0[6]
        inv[6] = -m0[0] * m0[6] * m0[15] + m0[0] * m0[7] * m0[14] + m0[4] * m0[2] * m0[15] -
                  m0[4] * m0[3] * m0[14] - m0[12] * m0[2] * m0[7] + m0[12] * m0[3] * m0[6]
        inv[10] = m0[0] * m0[5] * m0[15] - m0[0] * m0[7] * m0[13] - m0[4] * m0[1] * m0[15] +
                  m0[4] * m0[3] * m0[13] + m0[12] * m0[1] * m0[7] - m0[12] * m0[3] * m0[5]
        inv[14] = -m0[0] * m0[5] * m0[14] + m0[0] * m0[6] * m0[13] + m0[4] * m0[1] * m0[14] -
                   m0[4] * m0[2] * m0[13] - m0[12] * m0[1] * m0[6] + m0[12] * m0[2] * m0[5]
        inv[3] = -m0[1] * m0[6] * m0[11] + m0[1] * m0[7] * m0[10] + m0[5] * m0[2] * m0[11] -
                  m0[5] * m0[3] * m0[10] - m0[9] * m0[2] * m0[7] + m0[9] * m0[3] * m0[6]
        inv[7] = m0[0] * m0[6] * m0[11] - m0[0] * m0[7] * m0[10] - m0[4] * m0[2] * m0[11] +
                 m0[4] * m0[3] * m0[10] + m0[8] * m0[2] * m0[7] - m0[8] * m0[3] * m0[6]
        inv[11] = -m0[0] * m0[5] * m0[11] + m0[0] * m0[7] * m0[9] + m0[4] * m0[1] * m0[11] -
                   m0[4] * m0[3] * m0[9] - m0[8] * m0[1] * m0[7] + m0[8] * m0[3] * m0[5]
        inv[15] = m0[0] * m0[5] * m0[10] - m0[0] * m0[6] * m0[9] - m0[4] * m0[1] * m0[10] +
                  m0[4] * m0[2] * m0[9] + m0[8] * m0[1] * m0[6] - m0[8] * m0[2] * m0[5]

        var det = m0[0] * inv[0] + m0[1] * inv[4] + m0[2] * inv[8] + m0[3] * inv[12]
        if (abs(det) < 1e-10f) return identity()
        det = 1f / det
        val r = Mat4()
        for (i in 0..15) r.m[i] = inv[i] * det
        return r
    }

    fun toFloatArray(): FloatArray = m.copyOf()
}
