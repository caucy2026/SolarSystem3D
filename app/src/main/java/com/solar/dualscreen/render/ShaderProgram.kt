package com.solar.dualscreen.render

import android.opengl.GLES30
import android.util.Log

/**
 * OpenGL ES 3.0 Shader 编译/链接工具
 */
object ShaderProgram {

    fun compile(vertexSrc: String, fragmentSrc: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            Log.e("Shader", "Link failed: $log")
            GLES30.glDeleteProgram(program)
            return 0
        }
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e("Shader", "Compile failed ($type): $log")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    // ============ 标准行星 Shader (纹理 + Blinn-Phong 光照) ============
    val PLANET_VERTEX = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec3 aNormal;
        layout(location=2) in vec2 aTexCoord;

        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform mat3 uNormalMatrix;
        uniform vec3 uLightPos;   // 世界空间光源位置 (太阳)
        uniform vec3 uViewPos;    // 相机位置

        out vec3 vFragPos;
        out vec3 vNormal;
        out vec2 vTexCoord;
        out vec3 vLightDir;
        out vec3 vViewDir;

        void main() {
            vec4 worldPos = uModel * vec4(aPosition, 1.0);
            vFragPos = worldPos.xyz;
            vNormal = normalize(uNormalMatrix * aNormal);
            vTexCoord = aTexCoord;
            vLightDir = normalize(uLightPos - worldPos.xyz);
            vViewDir = normalize(uViewPos - worldPos.xyz);
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val PLANET_FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform sampler2D uTexture;
        uniform vec3 uLightColor;
        uniform vec3 uAmbientColor;
        uniform float uShininess;
        uniform float uSpecularStrength;

        in vec3 vFragPos;
        in vec3 vNormal;
        in vec2 vTexCoord;
        in vec3 vLightDir;
        in vec3 vViewDir;

        out vec4 fragColor;

        void main() {
            vec3 texColor = texture(uTexture, vTexCoord).rgb;
            vec3 N = normalize(vNormal);
            vec3 L = normalize(vLightDir);
            float NdotL = max(dot(N, L), 0.0);
            vec3 diffuse = uLightColor * NdotL * texColor;
            vec3 ambient = uAmbientColor * texColor * 0.3;
            vec3 H = normalize(L + normalize(vViewDir));
            float spec = pow(max(dot(N, H), 0.0), uShininess) * uSpecularStrength;
            vec3 specular = uLightColor * spec;
            vec3 result = ambient + diffuse + specular;
            // 背面用环境光避免全黑
            float backFade = smoothstep(0.0, 0.3, NdotL);
            result = mix(ambient * 0.5, result, backFade);
            fragColor = vec4(result, 1.0);
        }
    """.trimIndent()

    // ============ 太阳 Shader (自发光, 无光照) ============
    val SUN_VERTEX = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=2) in vec2 aTexCoord;
        uniform mat4 uMVP;
        out vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val SUN_FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform sampler2D uTexture;
        in vec2 vTexCoord;
        out vec4 fragColor;
        void main() {
            vec3 tex = texture(uTexture, vTexCoord).rgb;
            fragColor = vec4(tex * 1.5, 1.0);
        }
    """.trimIndent()

    // ============ 光环 Shader (半透明纹理) ============
    val RING_VERTEX = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec2 aTexCoord;
        uniform mat4 uMVP;
        out vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val RING_FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform sampler2D uTexture;
        uniform vec3 uLightColor;
        in vec2 vTexCoord;
        out vec4 fragColor;
        void main() {
            vec4 tex = texture(uTexture, vTexCoord);
            fragColor = vec4(tex.rgb * uLightColor, tex.a);
        }
    """.trimIndent()

    // ============ 轨道线 Shader (纯色) ============
    val ORBIT_VERTEX = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        uniform mat4 uMVP;
        void main() {
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val ORBIT_FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform vec3 uColor;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(uColor, 1.0);
        }
    """.trimIndent()

    // ============ 星空天球 Shader ============
    val SKYBOX_VERTEX = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        layout(location=1) in vec2 aTexCoord;
        uniform mat4 uMVP;
        out vec2 vTexCoord;
        void main() {
            vTexCoord = aTexCoord;
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val SKYBOX_FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform sampler2D uTexture;
        in vec2 vTexCoord;
        out vec4 fragColor;
        void main() {
            fragColor = texture(uTexture, vTexCoord);
        }
    """.trimIndent()

    // ============ 兜底纯色 Shader (零纹理、零光照, 绝对可靠) ============
    val SOLID_VERTEX = """
        #version 300 es
        precision highp float;
        layout(location=0) in vec3 aPosition;
        uniform mat4 uMVP;
        void main() {
            gl_Position = uMVP * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    val SOLID_FRAGMENT = """
        #version 300 es
        precision highp float;
        uniform vec3 uColor;
        out vec4 fragColor;
        void main() {
            fragColor = vec4(uColor, 1.0);
        }
    """.trimIndent()
}
