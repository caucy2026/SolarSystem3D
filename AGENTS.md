# SolarSystem3D — AI 编程助手指南

> 双屏 3D 太阳系 Android 应用。Three.js r150 WebView 渲染，Mali-G52 GPU，双屏异显。
> 原生 GL 验证已完成：WebView 比原生 GL 慢 20-35%，详见 `docs/native-gl-architecture.md`

---

## 构建与安装

```powershell
$env:JAVA_HOME='D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7'
D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7\bin\java.exe `
  -classpath 'D:\work\ai_code\SolarSystem3D\gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon `
  -p D:\work\ai_code\SolarSystem3D

adb install -r D:\work\ai_code\SolarSystem3D\app\build\outputs\apk\release\app-release.apk
```

| 环境 | 路径 |
|------|------|
| JDK 17 | `D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7` |
| Android SDK | `D:\work\ai_code\tools\android-sdk` (api 34) |
| Gradle | 8.5 / Kotlin 1.9.20 / compileSdk 34 / minSdk 31 |
| Keystore | `D:\qianming\debug.keystore` (alias=`androiddebugkey`, pwd=`880203`) |
| 包名 | `com.solar.dualscreen` |

---

## 架构

### 双 Activity 方案

| Activity | 物理屏 | 文件 | 视角 | 启动方式 |
|----------|:------:|------|:----:|----------|
| `MainActivity` | Display 0 | `planet.html` | 🔭 单星球 3D + 信息卡片 | 用户点击图标 |
| `SolarPresentation` | Display 2 | `solar.html` | ☀️ 太阳系全景 + 撞击系统 | `startActivity` + 反射 `setLaunchDisplayId` |

### 渲染引擎

- **Three.js r150** 内联打包，零 CDN 依赖
- 两个 HTML 文件各自独立 Three.js 场景，通过 `Android.onPlanetSelected()` JS Bridge 通信
- STL 3D 模型通过 `http://local.stl/*` 虚拟 URL → `shouldInterceptRequest` 从 assets 读取

### 数据流

```
副屏(solar.html) 用户点击行星
  → Android.onPlanetSelected(name)
    → MainActivity.selectPlanet(name)
      → 主屏(planet.html) webView.evaluateJavascript("onSelectPlanet('name')")
        → showPlanet(name) + showSwInfo(name) → 3D星球 + 信息卡
```

---

## 核心文件

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主屏：加载 `planet.html`，WebView + JS Bridge，STL/NAS 纹理拦截 |
| `SolarPresentation.kt` | 副屏：加载 `solar.html`，WebView + JS Bridge，纹理下载缓存 |
| `SolarState.kt` | 单例：MainActivity / SolarPresentation 引用 + 回调 |
| **`planet.html`** | 主屏渲染：单星球 3D（Three.js）+ `#swInfo` 信息卡片 + `showPlanet/showSwInfo` |
| **`solar.html`** | 副屏渲染：太阳系全景 + Transit 小行星 + 木星撞击系统（引力波/坑） |
| `SolarGLRenderer.kt` | 🧪 原生 GLES 2.0 太阳渲染器（验证用，后续可扩展为完整场景） |
| `app/src/main/assets/models/` | STL 3D 小行星模型（bennu/eros/toutatis/geographos/kleopatra/asteroid_rq36/itokawa/vesta_a） |

---

## solar.html 场景结构

### 对象层级
- ☀️ 太阳（IcosahedronGeometry detail=4 + ShaderMaterial + 太阳风暴粒子）
- 🪐 8 大行星（SphereGeometry 24×12 + 纹理 + 标签）
- ☄️ 8 颗 STL 小行星（独立轨道 23-27，`data` 数组中 `model` 字段）
- 🌑 17 颗卫星（SphereGeometry 16×8，确定性哈希初始角度，两边屏幕一致）
- 🪨 20 颗 Transit 小行星（`asteroidGroup`）：
  - B0-B11：小行星带穿梭（轨道 21-31）
  - I0-I7：撞击专用（外太阳系 → 木星捕获 → 撞击）
- 💫 主小行星带（2100 粒子，轨道 21.5-24，延迟 1.2s 加载）
- 💫 柯伊伯带（2100 粒子，轨道 59-88，延迟 2.4s 加载）
- ⭐ 星空背景球（1400 粒子，半径 180-200）
- ☀️ 太阳风暴粒子（蓝色 1680 + 橙红 1400，AdditiveBlending ShaderMaterial）
- 🔵 轨道线（仅 8 大行星，色相均匀分布避免棕色）

### 撞击系统
- 每 ~30s 一颗 I 系列小行星从外太阳系穿越，被木星捕获
- 撞击效果：3 层蓝色引力波扩散环 + 木星表面暗色撞击坑
- 主屏聚焦木星时同步可见（`asteroidGroup.visible` + `impactEffects` 可见）

### Mali-G52 兼容注意事项
- STL 材质用 `MeshLambertMaterial`（`MeshPhongMaterial` + `flatShading` 渲染异常）
- `MeshBasicMaterial` 无光照 → 扁平无立体感 → 不用于 STL
- 岩石色板全部灰调，避免棕色渲染
- 标签用 Canvas Sprite（512×128，纯文字无背景），`renderOrder=999`

### 主屏性能优化（`updateMainScreenVisibility`）
- 主屏只渲染：焦点星球 + 卫星 + 光环 + 撞击特效（若聚焦木星）
- 隐藏：其他行星、轨道线、小行星带、柯伊伯带、Transit 小行星（非捕获态）、星空背景、太阳风暴粒子
- 左上角 FPS 显示：帧率 + 三角形总面数（`countSceneTriangles()` 遍历统计）

### 几何体三角形预算（Mali-G52 优化后）

| 对象 | 几何参数 | 三角形数 |
|------|----------|----------|
| ☀️ 太阳 Icosahedron | detail=4 | ~5,120 |
| ☀️ 太阳光晕 | SphereGeometry 24×12 | ~576 |
| 🪐 8 大行星 | SphereGeometry 24×12 | ~576 × 8 |
| 🌑 17 颗卫星 | SphereGeometry 16×8 | ~256 × 17 |
| ☄️ STL 小行星 | 可变（STL 原始面数） | ~5K-50K × 8 |

> 太阳 detail 从 8 降到 4，三角形 131 万 → 5120（-99.6%），帧率显著提升。

---

## planet.html 场景结构

### 对象
- 单星球 3D 视图（`showPlanet` 函数）
- STL 小行星支持（异步加载 + `_reqId` 防抖）
- 卫星系统（程序化纹理 fallback）
- 土星光环（多层 RingGeometry）
- 相机轨道（单指旋转 + 双指缩放 + 惯性）

### 信息卡片（`#swInfo`）
- 位置：底部居中，半透明毛玻璃效果
- 内容：中英文名、距太阳距离（km + 光行时间）、半径/重力/公转/自转、描述、冷知识（可翻页）
- 数据源：`planetInfo` 对象（含 Sun + 8 大行星 + Pluto + 5 STL 小行星）

---

## 太阳系缩放方案（solar.html）

| 参数 | 显示值 |
|------|--------|
| 太阳半径 | 3.5 |
| 行星半径 | 0.5~2.0 |
| 小行星半径 | 0.5~1.1 |
| 轨道半径 | 6~55 |
| 卫星轨道 | 1.3~15.0 |

**原则**：所有星球统一缩放处理，不做特殊区分。

---

## 卫星系统

| 行星 | 卫星 |
|------|------|
| 🌍 地球 | 月球 |
| ♂ 火星 | 火卫一、火卫二 |
| ♃ 木星 | 木卫一 ~ 四（伽利略卫星） |
| ♄ 土星 | 土卫六、土卫二 |
| ⛢ 天王星 | 天卫一 ~ 五 |
| ♆ 海王星 | 海卫一 |

---

## ⚠️ 编辑铁律

1. 不用 PowerShell 管道编辑 `.kt` 或 `.html` 文件（UTF-8 损坏）
2. 所有星球统一缩放方案，不做地球特殊处理
3. 纹理是单张等距矩形投影，非瓦片金字塔
4. 主屏 = `planet.html`，副屏 = `solar.html`，**不要混用**
5. 信息卡片在 `planet.html` 的 `#swInfo`，不要用 HTML 覆盖层替代
6. 双 Activity 生命周期：副屏返回键 → 主屏 `finishAffinity()`
7. Mali-G52 不用 `MeshPhongMaterial` + `flatShading`（渲染为棕色），不用 `MeshBasicMaterial`（无立体感）

## ⚠️ 编辑铁律

1. 不用 PowerShell 管道编辑 `.kt` 文件
2. 所有星球统一缩放方案，不做地球特殊处理
3. 纹理是单张等距矩形投影，非瓦片金字塔
4. 版本号同步修改 MainActivity 和 SolarPresentation
5. 双 Activity 生命周期：副屏返回键 → 主屏 `finishAffinity()`
