# SolarSystem3D — AI 编程助手指南

> 双屏 3D 太阳系 Android 应用。基于 V900 平台（Mali-G52 + OpenGL ES 3.2 + 双屏异显）。

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

| Activity | 物理屏 | 视角 | 启动方式 |
|----------|:------:|:----:|----------|
| `MainActivity` | Display 0 | 🔭 单星球细节 3D | 用户点击图标 |
| `SolarPresentation` | Display 2 | ☀️ 太阳系全景 | `startActivity` + 反射 `setLaunchDisplayId` |

### 3D 引擎

- **Raw OpenGL ES 3.0**，零外部依赖
- 自建数学库 (`Vec3`/`Mat4`)，自建球体和光环网格
- 所有星球使用**统一等距矩形投影纹理**（不分瓦片）

### 纹理源

- `https://www.solarsystemscope.com/textures/download/`
- 免费 CC Attribution 4.0，2K/8K 单张 JPG
- 全部 13 个文件 ≈ 10MB（远小于 Go3DGlobe 的瓦片方案）

---

## 核心文件

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 主屏：单星球 3D 细节 + Arcball 旋转/缩放 |
| `SolarPresentation.kt` | 副屏：太阳系全景 + 触摸拾取 |
| `SolarState.kt` | 单例：共享渲染器引用 + 回调 |
| `PlanetData.kt` | 9 行星 + 太阳 + 卫星轨道/物理数据 + 纹理 URL |
| `render/SolarRenderer.kt` | 副屏渲染：公转/自转动画，9 行星+卫星+轨道+光环 |
| `render/PlanetDetailRenderer.kt` | 主屏渲染：高模单星球 + Blinn-Phong 光照 |
| `render/SphereMesh.kt` | UV 球体网格（低模 24×12 / 高模 64×32） |
| `render/RingMesh.kt` | 土星环 CD 状环形 strip |
| `render/ShaderProgram.kt` | GLSL 着色器：行星 Blinn-Phong / 太阳自发光 / 光环透明 |
| `render/TextureManager.kt` | 纹理加载/缓存/创建 fallback 纯色 |
| `render/Camera3D.kt` | Arcball 旋转 + 缩放相机 |
| `touch/ArcballController.kt` | 主屏拖拽旋转 + Pinch 缩放 |
| `touch/PlanetPicker.kt` | 副屏射线-球体相交拾取 |
| `util/Math3D.kt` | 自建 Vec3 / Mat4（无外部依赖） |
| `util/PlanetData.kt` | 天体数据定义 |

---

## 太阳系缩放方案

| 参数 | 实际值 | 显示缩放 |
|------|--------|---------|
| 太阳半径 | 696,340 km | 0.3（显示单位） |
| 行星半径 | 2,440~69,911 km | 压缩到太阳的 0.01~0.1 |
| 小卫星半径 | <1,000 km | 放大 30× 确保可见 |
| 轨道半径 | 0.387~30.07 AU | ×2.5 显示单位 |
| 卫星轨道 | 0.00006~0.0126 AU | ×8 额外放大 |

**原则**：所有星球统一缩放处理，不做特殊区分。

---

## 卫星系统

| 行星 | 卫星 |
|------|------|
| 🌍 地球 | 月球 |
| ♂ 火星 | 火卫一、火卫二 |
| ♃ 木星 | 木卫一 ~ 四（伽利略卫星） |
| ♄ 土星 | 土卫六、土卫二、土卫一 |
| ⛢ 天王星 | 天卫一 ~ 五 |
| ♆ 海王星 | 海卫一 |

---

## ⚠️ 编辑铁律

1. 不用 PowerShell 管道编辑 `.kt` 文件
2. 所有星球统一缩放方案，不做地球特殊处理
3. 纹理是单张等距矩形投影，非瓦片金字塔
4. 版本号同步修改 MainActivity 和 SolarPresentation
5. 双 Activity 生命周期：副屏返回键 → 主屏 `finishAffinity()`
