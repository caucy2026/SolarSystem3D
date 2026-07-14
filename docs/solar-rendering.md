# SolarSystem3D — 太阳渲染与粒子效果详细实现

> 文件：`app/src/main/assets/solar.html`（副屏 Display 2，太阳系全景）
> 引擎：Three.js r150（内联打包，无模块依赖）
> 渲染：WebGL 1.0 via Android WebView
> 芯片：V900 (ARM Cortex-A73 + Mali-G52 6核 GPU)，Android 12

---

## 引用的技术与工程

| 技术/工程 | 用途 | 说明 |
|-----------|------|------|
| **Three.js r150** | 3D 渲染引擎 | 内联打包在 `solar.html` 中（`build/three.min.js`），CDN-free |
| **SolarSystem3D** | 本工程 | 双屏太阳系 Android 应用，包名 `com.solar.dualscreen` |
| **SolarPresentation.kt** | 副屏 Activity | 加载 `solar.html` 到副屏 WebView，转发触摸事件 |
| **NASA SDO** | 太阳纹理参考 | AIA 171Å / HMI 数据，`2k_sun.jpg` 光球层纹理 |
| **Solar System Scope** | 纹理下载 | `solarsystemscope.com/textures/` CC-BY-4.0 纹理集 |
| **Go3DGlobe** | 架构参考 | 同平台双屏方案（WorldWind 瓦片地图） |
| **partical.md 规范** | 粒子规范 | GPU 生命周期 `mod(uTime+seed*life, life)` |

---

## 1. 整体场景结构

```
scene
├── AmbientLight(0x334455, 0.35)          // 环境光
├── PointLight(0xffffff, 5, decay=0)      // 太阳点光源（原点，无衰减）
├── MilkyWay 背景纹理 (2k_stars_milky_way.jpg)  // 或星空粒子兜底
├── sun (IcosahedronGeometry 3.5, 8级细分)
│   ├── fp (THREE.Points, 2400 蓝色喷泉粒子)
│   └── ffp (THREE.Points, 2000 黄色火焰粒子)
├── sunLabel (THREE.Sprite, "Sun" 标签)
├── 行星网格 (planetMeshes[], 8 大行星 + 月球/卫星)
│   ├── orbitLine (环形轨道线)
│   └── ring (土星/天王星光环, ringMeshes[])
├── 星带 (AsteroidBelt, KuiperBelt)
└── 星空背景 (2000 粒子, 兜底方案)
```

**关键设计**：粒子作为 `sun` 的子节点，自动跟随 `sun.rotation.y += dt * 0.3` 旋转。

---

## 2. 太阳着色器 — 详细实现

### 2.1 纹理管线

太阳使用 `ShaderMaterial`（非 MeshStandardMaterial），在 fragment shader 中手动叠加 3 层纹理：

```
fragmentShader 流程:
1. base = texture2D(uEmission, vUv)         → 光球层底图
2. plasma = texture2D(uPlasma, 变形 UV)      → 程序化等离子射线
3. helio = texture2D(uHelio, 变形 UV)        → 程序化日冕射线
4. col = base*1.1 + plasma*0.35 + helio*0.25 → 三层叠加
5. flame = 篝火火焰噪声计算                   → 火焰斑块
6. col = mix(col, col+flameCol*2.5, flameMask*0.55)  → 混合火焰
7. gl_FragColor = vec4(col*1.5, 1.0)         → 亮度提升
```

**UV 变形动画**：每层纹理的 UV 坐标随时间漂移，模拟等离子体流动：

```glsl
// 等离子层：UV 沿正弦波漂移
vec2 pUV = vUv + vec2(
  sin(vUv.y*12.0 + uTime*0.06)*0.003 + uTime*0.002,
  cos(vUv.x*15.0 + uTime*0.08)*0.003
);

// 日冕层：UV 沿余弦波漂移（不同频率/相位）
vec2 hUV = vUv + vec2(
  cos(vUv.y*8.0 + uTime*0.04)*0.002,
  sin(vUv.x*10.0 + uTime*0.05)*0.002
);
```

### 2.2 篝火火焰算法

火焰基于 2D FBM (Fractional Brownian Motion) 噪声，在片段着色器中实时计算：

```glsl
// 步骤 1: 平滑噪声函数（用于 FBM）
float noise(vec2 p) {
  vec2 i = floor(p), f = fract(p);
  f = f*f*(3.0 - 2.0*f);  // Hermite 平滑
  return mix(mix(hash(i), hash(i+vec2(1,0)), f.x),
             mix(hash(i+vec2(0,1)), hash(i+vec2(1,1)), f.x), f.y);
}

// 步骤 2: 4 层 FBM
float fbm(vec2 p) {
  float v = 0.0, a = 0.5;
  vec2 s = vec2(1.0);
  for (int i=0; i<4; i++) {
    v += a * noise(p*s);
    a *= 0.5;   // 振幅减半
    s *= 2.1;   // 频率加倍
  }
  return v;  // 输出 [0, 1]
}

// 步骤 3: 构建火焰形状
float wind = sin(uTime*1.3)*0.04 + sin(uTime*2.1+vUv.y*5.0)*0.03;  // 风
vec2 fUV = vUv*vec2(1.0, 0.7) + vec2(wind, uTime*0.015);           // 火UV
float flameNoise = fbm(fUV*3.0);                                    // FBM 噪声
float flameStreak = abs(fract(vUv.y*8.0 + flameNoise*0.6) - 0.5)*2.0; // 条纹

// 步骤 4: 火焰遮罩
float flameShape = 1.0 - smoothstep(0.0, 0.5, flameStreak)
                        * (1.0 - smoothstep(0.3, 0.7, flameNoise));
float flicker = 0.7 + 0.3*sin(uTime*7.3+vUv.x*13.0)*cos(uTime*5.1+vUv.y*17.0);
float flameMask = smoothstep(0.3, 0.65, flameNoise)  // 噪声阈值
                * smoothstep(0.0, 0.25, flameShape)   // 形状
                * flicker;                             // 闪烁

// 步骤 5: 火焰颜色（暗红→橙→亮黄）
float flameH = flameNoise * flameShape;
vec3 flameCol = mix(
  vec3(0.8, 0.15, 0.01),                                    // 暗红（底部）
  mix(vec3(1.0, 0.55, 0.05), vec3(1.0, 0.9, 0.3), flameH),  // 橙→亮黄（上部）
  flameH
);
```

### 2.3 程序化纹理生成（Canvas 2D）

`uPlasma`（等离子射线）和 `uHelio`（日冕）在 JS 中用 Canvas 2D API 绘制，1024×512 分辨率：

```javascript
// uPlasma: 6 个活跃区 + 150 条散射线
for (var ri=0; ri<6; ri++) {
  // 每个活跃区: 径向渐变光晕 + 150 条贝塞尔曲线射线
  var cg = pctx.createRadialGradient(rx, ry, 0, rx, ry, rr*0.5);
  cg.addColorStop(0, 'rgba(255,255,240,0.9)');
  cg.addColorStop(0.3, 'rgba(255,200,80,0.4)');
  cg.addColorStop(1, 'rgba(0,0,0,0)');
  // ... quadraticCurveTo 绘制射线
}

// uHelio: 极区射线（仅 y 接近 0 或 512 的区域）
// 500 条射线，密度与纬度相关（极区密集）
if (Math.random() > ed*0.5+0.05) continue;  // 低纬度跳过
```

---

## 3. 太阳风暴粒子系统 — 详细实现

### 3.1 粒子生命周期（GPU 驱动）

每个粒子的生命周期完全在 GPU 顶点着色器中管理，无需 CPU 更新 buffer：

```glsl
// 顶点着色器中的生命周期计算
float t = mod(uTime + aSeed*aLife, aLife);  // 每个粒子独立循环时间
float l = t / aLife;                         // 归一化进度 [0, 1]
vec3 p = aVel * t;                           // 匀速直线运动
gl_PointSize = aSize * POINT_MULT / -mv.z;   // 透视缩放的粒子大小
vA = 1.0 - l;                                // alpha 衰减传递给 frag shader
```

**关键设计**：
- `aSeed*aLife` 给每个粒子不同的相位偏移，防止所有粒子同步
- `mod()` 自动循环，粒子"死亡"后立即重生
- 不需要 JS 端更新 position buffer，完全 GPU 驱动

### 3.2 蓝色喷泉 (Fountain) — 完整着色器

```glsl
// --- 顶点着色器 ---
attribute vec3 aVel;      // 速度向量（方向+速率）
attribute float aLife;    // 生命周期 (1.5~3.5s)
attribute float aSize;    // 粒子基础大小 (0.072~0.27)
attribute float aSeed;    // 随机种子（相位偏移）
uniform float uTime;      // 全局时间 (performance.now()*0.001)
uniform float uAlpha;     // 风暴阶段 alpha（全亮1.0→衰减0.0）
varying float vA, vS;

void main() {
  float t = mod(uTime + aSeed*aLife, aLife);  // 粒子时间
  float l = t / aLife;                         // 进度
  vec3 p = aVel * t;                           // 位置 = 速度 × 时间
  vec4 mv = modelViewMatrix * vec4(p, 1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = aSize * 2400.0 / -mv.z;       // 像素大小
  vA = 1.0 - l;                                 // 剩余生命比例
  vS = aSeed;
}

// --- 片段着色器 ---
varying float vA, vS;
uniform float uAlpha;

void main() {
  float d = length(gl_PointCoord - 0.5) * 2.0;  // 距中心距离 [0,1]
  float alpha = exp(-d*d*8.0) * vA * 0.9 * uAlpha;  // 锐利高斯衰减
  vec3 col = mix(vec3(0.4,0.6,1.0), vec3(0.2,0.3,0.8), vS);  // 蓝色变体
  gl_FragColor = vec4(col, alpha);  // AdditiveBlending 叠加
}
```

**发射逻辑**（`setFountain()`）：

```javascript
function setFountain(sx, sy, sz) {
  // 1. 计算球面点法线和切线坐标基
  var cl = Math.sqrt(sx*sx + sy*sy + sz*sz);
  var nx = sx/cl, ny = sy/cl, nz = sz/cl;        // 法线 = 归一化位置
  var tx = -ny, tz = nx;                          // 切线 (垂直法线的水平分量)
  var bx = ny*tz - nz*0, by = nz*tx - nx*tz, bz = nx*0 - ny*tx; // 副法线

  // 2. 为每个粒子生成位置和速度
  for (var i=0; i<2400; i++) {
    // 位置：表面点 + 微小抖动
    fPos[i*3]   = sx + (Math.random()-0.5)*0.05;
    fPos[i*3+1] = sy + (Math.random()-0.5)*0.05;
    fPos[i*3+2] = sz + (Math.random()-0.5)*0.05;

    // 速度：法线方向(主) + 切线/副法线方向(扩散)
    var angle = Math.random()*PI*2;
    var speed = 0.75 + Math.random()*1.5;         // 0.75~2.25
    var spread = 0.06 + Math.random()*0.16;       // 扩散半径 6%~22%

    fVel[i*3]   = nx*speed + (tx*cos(angle)+bx*sin(angle))*spread;
    fVel[i*3+1] = ny*speed + (0*cos(angle)+by*sin(angle))*spread;
    fVel[i*3+2] = nz*speed + (tz*cos(angle)+bz*sin(angle))*spread;
  }
  fGeo.attributes.position.needsUpdate = true;
  fGeo.attributes.aVel.needsUpdate = true;
}
```

### 3.3 黄色火焰 (Fire) — 与喷泉的差异

| 特性 | 蓝喷泉 | 黄火焰 |
|------|--------|--------|
| 顶点着色器 | 相同结构 | `aDir` 替代 `aVel`（语义区别） |
| pointSize | `2400 / -mv.z` | `3600 / -mv.z * (1-l)` 渐小 |
| 片段着色器 | 正圆高斯 | 正圆高斯(衰减小) + 颜色渐变 |
| 颜色 | 蓝渐变 | 前30%白黄→橙，后70%橙→暗红 |
| 粒子数 | 2400 | 2000 |
| 生命周期 | 1.5~3.5s | 0.8~2.3s (更短更快) |
| 速度 | 0.75~2.25 | 0.8~3.0 (更快) |

**火焰颜色渐变逻辑**：

```glsl
float vH; // 归一化生命周期进度 (0=新生, 1=消亡)
if (vH < 0.3) {
  col = mix(vec3(1.0,0.95,0.6), vec3(1.0,0.5,0.1), vH/0.3);  // 白黄→橙
} else {
  col = mix(vec3(1.0,0.5,0.1), vec3(0.7,0.05,0.01), (vH-0.3)/0.7); // 橙→暗红
}
```

### 3.4 风暴周期管理（JS 端）

```javascript
// 常量
var stormFull = 3000;   // 全亮 3 秒
var stormFade = 6000;   // 衰减 6 秒

// 每帧 animate() 中
var now = performance.now();

// 蓝喷泉
var be = now - blueStart;
var ba = be < stormFull ? 1.0 : Math.max(0, 1.0-(be-stormFull)/stormFade);
if (be > stormFull+stormFade) {
  randomBlueSpot();  // 换位置重生
  ba = 1.0;          // alpha 重置
}
fMat.uniforms.uAlpha.value = ba;
fMat.uniforms.uTime.value = now * 0.001;

// 黄火焰（完全独立）
var ye = now - yellowStart;
var ya = ye < stormFull ? 1.0 : Math.max(0, 1.0-(ye-stormFull)/stormFade);
if (ye > stormFull+stormFade) {
  randomYellowSpot();
  ya = 1.0;
}
ffMat.uniforms.uAlpha.value = ya;
ffMat.uniforms.uTime.value = now * 0.001;
```

### 3.5 为何粒子必须是 sun 子节点

```
scene (world space)
  └── sun (rotates at 0.3 rad/s on Y axis)
       ├── fp (Fountain Points)
       └── ffp (Fire Points)
```

- 粒子位置和速度存储为 **sun 局部空间坐标**（`aVel * t` 也在局部空间）
- `modelViewMatrix` 自动包含 sun 的旋转矩阵
- 粒子从诞生到消失，与太阳表面特征点保持**相对位置不变**
- 发射时计算的法线/切线方向也自动随 sun 旋转

---

## 4. 参数速查表

### 4.1 太阳着色器火焰

| 参数 | 值 | 作用 | 增大效果 |
|------|:--:|------|----------|
| `fbm(fUV*N)` | 3.0 | 噪声缩放 | 火苗变小 |
| `vUv.y*N` | 8.0 | 条纹密度 | 条纹变密 |
| `stripe*N` | 0.6 | 噪声对条纹的影响 | 条纹更扭曲 |
| `flameCol*N` | 2.5 | 火焰亮度 | 更亮 |
| `flameMask*N` | 0.55 | 火焰覆盖 | 更多火苗 |
| `smoothstep(lo, hi)` | 0.3, 0.65 | 噪声阈值 | lo↓hi↓=更多火苗 |

### 4.2 粒子系统

| 参数 | 蓝喷泉 | 黄火焰 | 说明 |
|------|:-----:|:-----:|------|
| `FC` / `FFC` | 2400 | 2000 | 粒子数 |
| pointSize 倍率 | 2400 | 3600 | ⚠️ 低于1200消失 |
| aSize 范围 | 0.072~0.27 | 0.09~0.36 | 个体大小 |
| 衰减指数 | exp(-d²×8) | exp(-d²×6) | 边缘锐利度 |
| 生命周期 | 1.5~3.5s | 0.8~2.3s | — |
| `stormFull` | 3000ms | 3000ms | 全亮时长 |
| `stormFade` | 6000ms | 6000ms | 衰减时长 |
| 速度 | 0.75~2.25 | 0.8~3.0 | 单位/秒 |
| 扩散角 | 6%~22% | 6%~22% | 弧度 |

### 4.3 pointSize 与可见性

相机距离 85，1080p 屏幕：

| 倍率 | min px | max px | 可见 |
|:---:|:---:|:---:|:---:|
| 2400/3600 | 2.0 / 3.8 | 7.6 / 15.2 | ✅ |
| 1200/1800 | 1.0 / 1.9 | 3.8 / 7.6 | ⚠️ |
| 360/540 | 0.3 / 0.6 | 1.1 / 2.3 | ❌ |

---

## 5. 相关文件索引

| 文件 | 位置 | 说明 |
|------|------|------|
| `solar.html` | `app/src/main/assets/` | 副屏太阳系全景（含所有着色器和粒子） |
| `planet.html` | `app/src/main/assets/` | 主屏单星球细节视图 |
| `SolarPresentation.kt` | `app/src/main/java/.../` | 副屏 WebView Activity |
| `MainActivity.kt` | `app/src/main/java/.../` | 主屏 Activity |
| `solar-rendering.md` | 项目根目录 | 本文档 |
| `AGENTS.md` | 项目根目录 | AI 编程助手指南 |
| `build.bat` | 项目根目录 | 一键构建 |
| `particles-only.html` | `GoDualScreen/` | 粒子效果独立调试页面 |
| `sun-particles.html` | `GoDualScreen/` | 太阳粒子完整调试页面 |
