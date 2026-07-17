# 原生 OpenGL ES 渲染架构 — 验证与对比

> 日期：2026-07-16 ~ 2026-07-17
> 目标：验证 WebView + Three.js WebGL 是否引入额外 GPU 开销，评估原生 GL 迁移价值
> 结论：WebView WebGL 比原生 GL 慢约 20-35%，太阳复杂 fragment shader 是双重瓶颈

---

## 1. 架构对比

### 当前架构：WebView + Three.js (WebGL)

```
┌──────────────┐    ┌──────────────┐
│ MainActivity │    │SolarPresent. │
│  (Display 0) │    │  (Display 2) │
│              │    │              │
│  ┌────────┐  │    │  ┌────────┐  │
│  │WebView │  │    │  │WebView │  │
│  │        │  │    │  │        │  │
│  │Three.js│  │    │  │Three.js│  │
│  │ WebGL  │  │    │  │ WebGL  │  │
│  └───┬────┘  │    │  └───┬────┘  │
│      │       │    │      │       │
│  ┌───▼────┐  │    │  ┌───▼────┐  │
│  │Chromium│  │    │  │Chromium│  │
│  │  GPU   │  │    │  │  GPU   │  │
│  │Command │  │    │  │Command │  │
│  │Buffer  │  │    │  │Buffer  │  │
│  └───┬────┘  │    │  └───┬────┘  │
└──────┼───────┘    └──────┼───────┘
       │                   │
   ┌───▼───────────────────▼───┐
   │     Mali-G52 (6核 GPU)    │
   │  EGL / OpenGL ES 2.0/3.0  │
   └───────────────────────────┘
```

**开销来源：**
- Chromium 的 WebGL 实现需要在 JS ↔ C++ ↔ GPU 之间做命令缓冲和序列化
- `renderer.render()` 内部遍历场景树、排序、提交 draw calls，每帧重复
- 两个独立 WebView 各自维护 WebGL 上下文，GPU 状态切换开销
- Shader 编译走 ANGLE/Chromium 的 shader translator（可能有额外指令）

### 实验架构：原生 GLSurfaceView (OpenGL ES 2.0)

```
┌──────────────┐    ┌──────────────┐
│ MainActivity │    │SolarPresent. │
│  (Display 0) │    │  (Display 2) │
│              │    │              │
│  ┌────────┐  │    │  ┌────────┐  │
│  │GLSurf. │  │    │  │GLSurf. │  │
│  │ View   │  │    │  │ View   │  │
│  │        │  │    │  │        │  │
│  │SolarGL │  │    │  │SolarGL │  │
│  │Renderer│  │    │  │Renderer│  │
│  └───┬────┘  │    │  └───┬────┘  │
│      │       │    │      │       │
│  ┌───▼────┐  │    │  ┌───▼────┐  │
│  │GLES 2.0│  │    │  │GLES 2.0│  │
│  │Direct  │  │    │  │Direct  │  │
│  └───┬────┘  │    │  └───┬────┘  │
└──────┼───────┘    └──────┼───────┘
       │                   │
   ┌───▼───────────────────▼───┐
   │     Mali-G52 (6核 GPU)    │
   └───────────────────────────┘
```

**优势：**
- Kotlin → JNI → EGL → GPU，路径短，无 Chromium 中间层
- 手动控制 shader 编译、纹理绑定、draw call 顺序
- 单 EGL 上下文，无切换开销
- Shader 直接编译为 Mali 原生指令

---

## 2. 性能调试过程（逐层排除法）

### 阶段一：怀疑粒子过多
```
操作：HIDE_PARTICLES = true，隐藏所有动态粒子（太阳风暴、小行星带、星空）
结果：FPS 不变（35-50 → 35-50）
结论：粒子不是瓶颈
```

### 阶段二：怀疑三角形过多
```
操作：太阳 Icosahedron detail 8→4（131万→5K），行星 32×16→24×12，卫星 24×12→16×8
结果：三角形从 ~140万 → ~1.5万，但 FPS 仍然是 35-50
结论：三角形数不是瓶颈，Mali-G52 处理 80万三角形只需 0.6ms
```

### 阶段三：加 JS/R 计时器
```
操作：在 animate() 中测量 JS 计算时间 vs renderer.render() 时间
结果：
  主屏：JS=0.8ms, R=2.7ms  (40-50 FPS)
  副屏：JS=0.7ms, R=7.5ms  (35-50 FPS)
结论：JS 完全不是瓶颈（<1ms），问题在 GPU 渲染（R 值）
```

### 阶段四：逐类隐藏（DEBUG_MODE）
```
操作：通过 scene.traverse 系统性地隐藏不同物体类别，记录 R 值变化

┌──────────┬────────────────────┬───────┬──────┐
│ 模式     │ 可见内容            │ 三角形 │ R    │
├──────────┼────────────────────┼───────┼──────┤
│ MODE 3   │ 全空               │ 0     │0.6ms │
│ MODE 2   │ 行星+卫星+STL+光环  │ 815K  │0.6ms │
│ MODE 1   │ MODE 2 + ☀️太阳    │ 820K  │6.4ms │  ← 瓶颈定位！
│ MODE 0   │ 完整场景            │ 820K  │7.5ms │
└──────────┴────────────────────┴───────┴──────┘

关键发现：MODE 2（815K三角形）R=0.6ms，加上太阳（+5K三角形）R飙升到6.4ms。
太阳只有5120个三角形，但 R 涨了10倍 → 太阳的 fragment shader 是瓶颈。
```

### 阶段五：原生 GL 验证
```
操作：创建 SolarGLRenderer，用原生 GLES 2.0 渲染同样的太阳 shader
结果：FPS 57-60（稳定期），偶尔掉到 44
对比：WebView 同 shader 跑 35-50 FPS
结论：原生 GL 比 WebView 快 20-35%，但复杂 shader 本身在 Mali-G52 上仍重
```

---

## 3. 核心源码：SolarGLRenderer.kt

```kotlin
// 文件：app/src/main/java/com/solar/dualscreen/SolarGLRenderer.kt
// 职责：原生 OpenGL ES 2.0 太阳渲染器
// 面向后续扩展为完整太阳系渲染器

class SolarGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    // ============ 完整移植的太阳 Fragment Shader ============
    // 与 solar.html 中的 sunMat 完全一致：
    //   - FBM 4层噪声（hash + noise + fbm）
    //   - 3纹理混合（uEmission + uPlasma + uHelio）
    //   - 火焰效果（flameNoise + flameStreak + flameShape + flicker）
    //   - 动态 UV 偏移（sin/cos 时间驱动）

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 vUv;
        uniform sampler2D uEmission, uPlasma, uHelio;
        uniform float uTime;

        float hash(vec2 p) { /* ... */ }
        float noise(vec2 p) { /* ... */ }
        float fbm(vec2 p) { /* 4 octaves */ }

        void main() {
            vec3 base = texture2D(uEmission, vUv).rgb;
            vec3 plasma = texture2D(uPlasma, pUV).rgb;  // UV动画
            vec3 helio = texture2D(uHelio, hUV).rgb;     // UV动画
            vec3 col = base * 1.1 + plasma * 0.35 + helio * 0.25;

            float flameNoise = fbm(fUV * 3.0);
            // ... 火焰计算 ...
            col = mix(col, col + flameCol * 2.5, flameMask * 0.55);
            gl_FragColor = vec4(col * 1.5, 1.0);
        }
    """

    // ============ Icosahedron 生成 (detail=4, 5120面) ============
    // 从正二十面体基础顶点出发，递归细分4次
    // 每细分一次：每个三角形分裂为4个，新顶点归一化到单位球面
    // 最终生成：2562顶点, 5120三角形
    // UV：球面映射 (atan2 + asin)

    private fun buildIcosahedron(detail: Int) { /* ... */ }

    // ============ 程序化纹理 ============
    // 替代 Three.js 的 Canvas 2D API 纹理生成
    // createProceduralPlasmaTex(): 活跃区斑点 + 射线
    // createProceduralHelioTex(): 日冕弧线
    // 使用 Bresenham 画线 + 二次贝塞尔曲线

    // ============ 相机控制 ============
    var camTheta = 0f      // 水平旋转
    var camPhi = 0.5f       // 垂直角
    var camDistance = 8f    // 距离

    fun rotate(dTheta: Float, dPhi: Float) {
        camTheta += dTheta
        camPhi = (camPhi + dPhi).coerceIn(0.05f, PI - 0.05f)
    }

    fun zoom(factor: Float) {
        camDistance = (camDistance * factor).coerceIn(2f, 50f)
    }

    // ============ 渲染循环 ============
    override fun onDrawFrame(gl: GL10?) {
        // 1. 计算相机矩阵（球坐标 → LookAt）
        // 2. 自转矩阵（绕Y轴旋转）
        // 3. MVP = Projection × View × Model
        // 4. 绑定3个纹理单元
        // 5. 提交顶点 + 索引缓冲区
        // 6. glDrawElements
        // 7. FPS 计数
    }
}
```

---

## 4. 触摸交互实现

原生 GLSurfaceView 上实现与 Three.js 对等的触摸交互：

```kotlin
// 单指拖拽 → 旋转相机
override fun onTouchEvent(e: MotionEvent): Boolean {
    when (e.actionMasked) {
        ACTION_DOWN -> { lastX = e.x; lastY = e.y }
        ACTION_MOVE -> {
            if (e.pointerCount >= 2) {
                // 双指 → 缩放
                val dist = hypot(e.getX(1)-e.getX(0), e.getY(1)-e.getY(0))
                renderer.zoom(lastDist / dist)
                lastDist = dist
            } else {
                // 单指 → 旋转
                renderer.rotate(-dx * 0.005f, dy * 0.005f)
            }
        }
    }
    return true
}
```

**已实现：** 拖拽旋转 + 双指缩放
**待实现（后续）：** 点击拾取行星（raycasting）、惯性旋转、信息卡片 Overlay

---

## 5. 性能对比总结

| 指标 | WebView Three.js | 原生 GLSurfaceView | 差异 |
|------|-----------------|-------------------|------|
| 空场景 R | 0.6ms | <0.1ms | 原生快 6x |
| 815K 三角形 R | 0.6ms | — | 基本相同 |
| 太阳 shader FPS | 35-50 | 44-60 | 原生快 ~20-35% |
| 完整场景 FPS | 35-50 | — | 待实现 |
| 触摸延迟 | WebView 内处理 | Android MotionEvent | 原生更低 |
| Shader 编译 | Chromium ANGLE | GLES 直编 | 原生更可控 |
| 开发效率 | Three.js 声明式 | 手写 GL 管线 | WebView 快 10x+ |

---

## 6. 后续迁移路线

```
Phase 1 ✅ 原生 GL 验证 — 太阳单球体 + FBM shader + 触摸
Phase 2 ⬜ 完整场景迁移 — 8行星 + 纹理 + 轨道计算
Phase 3 ⬜ 高级特性 — STL模型加载 + 碰撞检测 + 小行星带
Phase 4 ⬜ 双屏联动 — GLSurfaceView 间通信替代 JS Bridge
Phase 5 ⬜ 性能调优 — VBO/VAO优化 + instancing + frustum culling
```

### 关键风险

1. **纹理加载**：需从 assets 读取 Bitmap → GL Utils.texImage2D，替代 Three.js TextureLoader
2. **STL 解析**：需在 Kotlin 端实现二进制 STL parser，替代 JS parseSTL()
3. **行星标签**：需用 Canvas → Bitmap → GL Texture 或 Android TextView Overlay
4. **轨道线**：需生成 GL_LINE_STRIP 几何体
5. **信息卡片**：需 Android 原生 UI（CardView/TextView）浮在 GLSurfaceView 上方

> 预估完整迁移工作量：2000-3000 行 Kotlin + GLSL，约 2-3 个完整工作日。
