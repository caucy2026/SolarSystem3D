# 体积火焰（Volumetric Fire）— 算法原理与可复用提示词

> 基于 yomotsu/VolumetricFire（移植自 Alfred Fuller 的 Real-time Procedural Volumetric Fire）
> 论文参考：Olano 的 Modified Noise、视图对齐切片法

---

## 一、算法核心：视图对齐切片（View-Aligned Slicing）

### 1.1 总体流程

```
每帧执行：
┌──────────────────────────────────────────────────┐
│ 1. 计算相机→火焰包围盒的视线方向 viewVector       │
├──────────────────────────────────────────────────┤
│ 2. 将包围盒 8 个角点投影到 viewVector             │
│    → 得到 minDistance / maxDistance              │
├──────────────────────────────────────────────────┤
│ 3. 从 maxDistance 向 minDistance 步进 sliceSpacing│
│    每个步进 = 一个切片平面（⟂ 视线）              │
├──────────────────────────────────────────────────┤
│ 4. 对每个切片平面，计算与包围盒的交线多边形        │
│    → 三角化为三角形扇（triangle fan）             │
├──────────────────────────────────────────────────┤
│ 5. 更新 BufferGeometry（顶点位置 + 3D 纹理坐标）  │
├──────────────────────────────────────────────────┤
│ 6. 片元着色器采样火焰剖面纹理 + 噪声扰动           │
└──────────────────────────────────────────────────┘
```

### 1.2 包围盒 → 角点投影

```
火焰包围盒：width × height × depth 的立方体
8 个角点：所有 (±w/2, ±h/2, ±d/2) 的组合

相机视线方向 viewVector = normalize( -modelViewMatrix.column2 )

每个角点投影距离 = dot(cornerPosition, viewVector)
→ 得到 minDistance（最近角点）和 maxDistance（最远角点）
→ 第一个切片从 maxDistance 开始，向 minDistance 步进
```

### 1.3 切片交线计算（Marching-Cubes 边表法）

**数据结构**：
```
cornerNeighbors[8][3]  — 每个角点的 3 个相邻角点索引
incomingEdges[8][8]    — 角点之间的边连接关系（-1 = 无连接）
```

**活动边（Active Edge）跟踪**：
```
每条活动边存储：
  - startIndex / endIndex  — 边的两个端点（角点索引）
  - pos / deltaPos         — 当前切片上的位置 + 每步增量
  - tex / deltaTex         — 当前切片上的纹理坐标 + 每步增量
  - prev / next            — 边链表指针（环形双向链表）
```

**算法**（类似 Marching Cubes 的边追踪）：

```
1. 从最远角点 maxCorner 开始，创建 3 条初始活动边
   （maxCorner 到它的 3 个邻居）

2. 优先级队列管理边的过期时间
   （过期时间 = 边的 endIndex 角点投影距离）

3. 每个切片步进时：
   a. 弹出所有到期的边
   b. 对每条到期边，判断是分裂（split）还是合并（merge）：
      - 分裂：边终点不在现有边的端点中 → 创建 2 条新边
      - 合并：边终点与相邻边的终点相同 → 合并为 1 条新边
   c. 遍历边的环形链表，收集当前切片的多边形顶点
   d. 三角化为三角形扇（triangle fan）
   e. sliceDistance -= sliceSpacing
```

**为什么不是简单的平面求交**：
- 通用平面-立方体求交需要处理 6 个面的平面方程
- 边追踪法利用立方体的拓扑结构，更高效
- 关键是追踪哪个边与当前的哪些边相连

### 1.4 三角形扇三角化

```
收集到的顶点序列：v0, v1, v2, ..., v(N-1)
三角化：(v0, v1, v2), (v0, v2, v3), ..., (v0, v(N-2), v(N-1))
→ 总共 N-2 个三角形
```

---

## 二、片元着色器：火焰采样

### 2.1 Modified Noise（mNoise）

**原理**（Olano 2005）：

```
输入：3D 坐标 pos(x, y, z)

步骤：
1. z 的整数/小数部分：intArg = floor(z), fracArg = fract(z)
2. 用 BBS 伪随机数生成器计算 2 个哈希值：
   hash.x = mBBS(intArg * 3 + 0, 61)
   hash.y = mBBS(intArg * 3 + 3, 61)
3. 用哈希值从预生成噪声纹理 nzw.png 中查表：
   g = [
     texture(nzw, (pos.x, pos.y + hash.x) / 61),
     texture(nzw, (pos.x, pos.y + hash.y) / 61)
   ] * 2 - 1  → 映射到 [-1, 1]
4. 沿 z 方向线性插值：
   return mix(g.x + g.y * fracArg, g.z + g.w * (fracArg - 1), smoothstep(fracArg))
```

**BBS 伪随机数生成器**（Blum Blum Shub）：
```glsl
vec2 mBBS(vec2 val, float modulus) {
    val = mod(val, modulus);
    return mod(val * val, modulus);  // x² mod 61
}
```

### 2.2 湍流（Turbulence）= 分形噪声

```glsl
float turbulence(vec3 pos) {
    float sum = 0.0;
    float freq = 1.0;   // 频率从 1 开始
    float amp = 1.0;    // 振幅从 1 开始
    for (int i = 0; i < 4; i++) {
        sum += abs(mnoise(pos * freq)) * amp;  // 取绝对值！
        freq *= 2.0;    // lacunarity = 2.0
        amp *= 0.5;     // gain = 0.5
    }
    return sum;
}
```

> **关键**：取 `abs()` 绝对值，使噪声低谷也变正值 → 火焰的"褶皱"效果

### 2.3 火焰剖面采样（sampleFire）

```glsl
vec4 sampleFire(vec3 loc, vec4 scale) {
    // loc: 3D 纹理坐标 [0,1]
    // scale: (x,y,z)=空间缩放, w=时间速度

    // Step 1: 转为火焰剖面坐标 (radius, height)
    loc.xz = loc.xz * 2.0 - 1.0;           // → [-1, 1]
    vec2 st = vec2(
        sqrt(dot(loc.xz, loc.xz)),         // radius: 距中心轴距离
        loc.y                                // height: 垂直高度
    );

    // Step 2: 噪声空间变换
    loc.y -= time * scale.w;               // 噪声向上滚动
    loc *= scale.xyz;                       // 缩放噪声空间

    // Step 3: 湍流偏移
    // sqrt(st.y) → 越往上越不稳定（火焰顶端更湍急）
    float offset = sqrt(st.y) * 1.3 * turbulence(loc);
    st.y += offset;                         // 垂直偏移

    // Step 4: 越界处理
    if (st.y > 1.0) return vec4(0,0,0,1);  // 超出范围 → 透明
    vec4 result = texture2D(fireProfile, st);

    // Step 5: 底部渐隐
    if (st.y < 0.1) result *= st.y / 0.1;  // 避免切片裁剪线

    return result;
}
```

### 2.4 火焰剖面纹理（fireProfile）

`firetex.png` 是一个 2D 纹理：
- **x 轴** = 距火焰中心轴的距离（radius：0=中心，1=边缘）
- **y 轴** = 火焰高度（0=底部，1=顶部）

典型火焰剖面：中心亮白 → 边缘橙红 → 外部透明

---

## 三、完整数据流图

```
┌──────────────────────┐
│  包围盒 8 角点       │
│  (±w/2,±h/2,±d/2)  │
└────────┬─────────────┘
         │
         ▼
┌──────────────────────┐
│  dot(corner, view)   │  ← 投影到视线方向
│  → minDist, maxDist  │
└────────┬─────────────┘
         │
         ▼  sliceDistance = maxDist .. minDist (step sliceSpacing)
┌──────────────────────┐
│  活动边追踪           │  ← Marching-Cubes 边表
│  → 切片多边形顶点     │
│  → 3D 纹理坐标插值   │
└────────┬─────────────┘
         │
         ▼  triangle fan 三角化
┌──────────────────────┐
│  BufferGeometry       │
│  position + texCoord  │
│  (每帧动态更新)        │
└────────┬─────────────┘
         │
         ▼  GPU 渲染
┌──────────────────────────────────────────┐
│  顶点着色器：                             │
│    pass-through (position, texCoord)     │
├──────────────────────────────────────────┤
│  片元着色器：                             │
│    texCoord → (radius, height)           │
│    turbulence(loc*scale + time) → offset │
│    sample fireProfile(radius, height')   │
│    AdditiveBlending, DoubleSide          │
└──────────────────────────────────────────┘
```

---

## 四、关键参数

| 参数 | 含义 | 建议值 |
|------|------|--------|
| `width` | 火焰宽（X） | 1.5-2.0 |
| `height` | 火焰高（Y） | 2.0-4.0 |
| `depth` | 火焰深（Z） | 1.5-2.0 |
| `sliceSpacing` | 切片间距 | 0.3-0.5 |
| `scale.xyz` | 噪声空间缩放 | (1.0, 2.0, 1.0) |
| `scale.w` | 噪声时间速度 | 0.3-0.7 |
| `magnatude` | 湍流强度 | 1.0-1.5 |
| `lacunarity` | 分形频率倍增 | 2.0 |
| `gain` | 分形振幅衰减 | 0.5 |
| `octives` | 噪声层数 | 4 |

**sliceSpacing 越小 → 越多切片 → 越精细 → 越慢**
**sliceSpacing 越大 → 越少切片 → 越快 → 可见切片层**

---

## 五、为什么不能用粒子系统替代

| 粒子系统 | 体积切片 |
|----------|----------|
| 点精灵最大尺寸受限（~64px） | 无尺寸限制 |
| 需要海量粒子覆盖体积 | 切片数（~10-30 片） |
| 粒子间有空隙 | 切片连续覆盖 |
| 视角旋转时粒子"旋转" | 切片始终 ⟂ 视线 |
| 深度排序复杂 | 自动正确（切片已按深度排列）|

---

## 六、纹理资源说明

| 纹理 | 用途 | 生成方式 |
|------|------|----------|
| `nzw.png` | 噪声查找表 | 预生成（61×61），可用 Canvas2D 生成 |
| `firetex.png` | 火焰剖面 | 1D 渐变图：中心白→边缘橙→外透明 |

**nzw.png 的 Canvas 生成思路**：
```js
const size = 61;
const canvas = document.createElement('canvas');
canvas.width = canvas.height = size;
const ctx = canvas.getContext('2d');
const imgData = ctx.createImageData(size, size);
for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
        // 填充满足 mBBS 算法的伪随机值
        const val = (x * x + y * y) % 61 / 61 * 255;
        const i = (y * size + x) * 4;
        imgData.data[i] = imgData.data[i+1] = val;
        imgData.data[i+2] = val;  // XY 通道
        imgData.data[i+3] = 255;
    }
}
ctx.putImageData(imgData, 0, 0);
```

**firetex.png 的 Canvas 生成思路**：
```js
// 256×256，x=半径(0→1)，y=高度(0→1)
for (let y = 0; y < 256; y++) {
    for (let x = 0; x < 256; x++) {
        const r = x / 256;           // 距中心轴距离
        const h = y / 256;           // 高度
        // 火焰剖面：高处暗、低处亮；中心亮、边缘暗
        const brightness = (1 - h * 0.7) * exp(-r * 3.0);
        // 颜色映射：白→黄→橙→红→透明
        // ...
    }
}
```

---

## 七、可复用提示词（复制到另一个项目使用）

---

【角色设定】
你是一位精通 Three.js、WebGL 着色器编程和实时体积渲染（Volumetric Rendering）的资深图形学专家。

【核心任务】
使用 Three.js r160+ 实现实时 3D 体积火焰效果。**不要用粒子系统（Points/ParticleSystem）！** 必须使用视图对齐切片法（View-Aligned Slicing）。

【技术方案：视图对齐切片法】

### 第 1 步：定义火焰包围盒

- 火焰是一个 `width × height × depth` 的 3D 立方体区域
- 8 个角点坐标：所有 `(±w/2, ±h/2, ±d/2)` 的组合
- 8 个纹理坐标角点：`(0/1, 0/1, 0/1)` 的三元组合

### 第 2 步：计算视线方向并投影角点

```js
// 从 modelViewMatrix 提取视线方向（第 3 列取反）
viewVector.set(
    -modelViewMatrix.elements[2],
    -modelViewMatrix.elements[6],
    -modelViewMatrix.elements[10]
).normalize();

// 8 个角点投影到视线方向
for (i = 0; i < 8; i++) {
    cornerDistance[i] = dot(posCorners[i], viewVector);
}
// 得到最近距离 minDistance 和最远距离 maxDistance
```

### 第 3 步：沿视线方向等距切片

```js
let sliceDist = floor(maxDistance / sliceSpacing) * sliceSpacing;
while (sliceDist > minDistance) {
    // 计算当前切片平面与包围盒的交线多边形
    // → 收集多边形顶点
    // → 三角化为三角形扇
    sliceDist -= sliceSpacing;
}
```

**交线计算**：使用"活动边追踪"算法（类似 Marching Cubes 的边表法）
- 维护一个按深度排序的边过期优先队列
- 每条边存储：当前切片上的位置、纹理坐标、位置增量、纹理增量
- 边过期时判断是分裂（split）还是合并（merge）
- 遍历边环形链表收集当前切片多边形顶点

### 第 4 步：片元着色器采样火焰

```glsl
// 核心采样函数
vec4 sampleFire(vec3 texCoord, vec4 scale, float time) {
    // 1. 3D坐标 → 火焰剖面坐标 (radius, height)
    vec2 st = vec2(length(texCoord.xz * 2.0 - 1.0), texCoord.y);

    // 2. 噪声空间变换 + 时间滚动
    vec3 noiseCoord = texCoord * scale.xyz;
    noiseCoord.y -= time * scale.w;

    // 3. 湍流偏移（越靠上越湍急）
    float offset = sqrt(st.y) * 1.3 * turbulence(noiseCoord);
    st.y += offset;

    // 4. 采样火焰剖面纹理
    vec4 color = texture(fireProfile, st);

    // 5. 底部渐隐（避免切片裁剪线）
    if (st.y < 0.1) color *= st.y / 0.1;

    return color;
}
```

### 第 5 步：实现 Modified Noise（Olano 2005）

**关键要求**：
- 使用预生成的 61×61 噪声纹理 `nzw.png`（而不是 GLSL 中的 `sin/hash` 函数）
- 使用 BBS（Blum Blum Shub）伪随机数生成器计算纹理查表偏移
- `mBBS(val, modulus) = (val²) mod 61`

```glsl
vec2 mBBS(vec2 val, float modulus) {
    return mod(val * val, modulus);
}

float mnoise(vec3 pos) {
    float intZ = floor(pos.z);
    float fracZ = fract(pos.z);
    vec2 hash = mBBS(intZ * 3.0 + vec2(0, 3), 61.0);
    vec4 g = vec4(
        texture(nzw, vec2(pos.x, pos.y + hash.x) / 61.0).xy,
        texture(nzw, vec2(pos.x, pos.y + hash.y) / 61.0).xy
    ) * 2.0 - 1.0;
    return mix(g.x + g.y * fracZ, g.z + g.w * (fracZ - 1.0), smoothstep(0, 1, fracZ));
}
```

### 第 6 步：湍流 = 多层噪声取绝对值叠加

```glsl
float turbulence(vec3 pos) {
    float sum = 0.0, freq = 1.0, amp = 1.0;
    for (int i = 0; i < 4; i++) {
        sum += abs(mnoise(pos * freq)) * amp;  // ← 取绝对值是关键！
        freq *= 2.0;   // lacunarity
        amp *= 0.5;    // gain
    }
    return sum;
}
```

### 第 7 步：生成纹理资源

**nzw.png（噪声查找表）**：
```js
const size = 61;
// 用 Canvas2D 填充满足 mBBS 算法的伪随机值
// 每个像素存储 (x² + y²) mod 61 的值
```

**firetex.png（火焰剖面）**：
```js
// 256×256 渐变图
// X轴 = 距中心轴距离（0=中心白色 → 1=边缘透明）
// Y轴 = 火焰高度（0=底部亮 → 1=顶部暗）
// 颜色带：白 → 黄 → 橙 → 红 → 透明
```

### 第 8 步：每帧更新流程

```js
function update(elapsedTime) {
    // 1. 重算视线方向
    updateViewVector();
    // 2. 重新切片（因为视线可能变了）
    slice();
    // 3. 更新 BufferGeometry（顶点位置+纹理坐标+索引）
    updateGeometry();
    // 4. 更新时间 uniform
    mesh.material.uniforms.time.value = elapsedTime;
}
```

### 第 9 步：材质设置

```js
new THREE.RawShaderMaterial({
    vertexShader: vs,    // pass-through: position + texCoord
    fragmentShader: fs,  // sampleFire()
    uniforms: {
        nzw:          { value: noiseTexture },
        fireProfile:  { value: fireProfileTexture },
        time:         { value: 0 }
    },
    side: THREE.DoubleSide,           // 双面渲染
    blending: THREE.AdditiveBlending, // 叠加混合
    transparent: true
})
```

【与粒子系统的区别 — 必须用切片法的原因】

| 粒子系统 | 体积切片 |
|----------|----------|
| 点精灵尺寸有上限 | 无尺寸限制 |
| 粒子间有空隙 | 切片连续无缝隙 |
| 视角依赖旋转 | 切片始终垂直视线 |
| 需海量粒子 | 10-30 片足够 |

【避坑清单】
1. ❌ 不要用 Points/ParticleSystem → ✅ 用动态 BufferGeometry + 切片
2. ❌ 不要用 GLSL `sin/hash` 做噪声 → ✅ 用预生成噪声纹理 + BBS PRNG
3. ❌ 不要用 `ShaderMaterial` → ✅ 用 `RawShaderMaterial`（完全自定义，不混入 Three.js 内置 chunk）
4. ❌ 不要忘记 `side: THREE.DoubleSide` — 单面会丢失背面切片
5. ❌ 不要每帧重建 Geometry → ✅ 预分配足够大的 Buffer，每帧 `set()` 更新
6. ❌ sliceSpacing 不要太大（>1.0）→ ✅ 用 0.3-0.5（切片层可见）
7. ❌ 不要在 turbulence 中漏掉 `abs()` — 不取绝对值火焰会"消失"

【参考项目】
- 源码：https://github.com/yomotsu/VolumetricFire
- 原论文：http://webgl-fire.appspot.com/html/fire.html
- 噪声论文：Olano, "Modified Noise for Evaluation on Graphics Hardware" (2005)
