# 双屏太阳系

> 双屏异显 3D 太阳系 Android 应用 | Android 12+ | Mali-G52 GPU

## 功能

- ☀️ **太阳**：Icosahedron + FBM 火焰 ShaderMaterial + 太阳风暴粒子（蓝色/橙红）
- 🪐 **8 大行星**：高精度纹理 + 标签 + 真实轨道周期/自转速度
- 🌑 **17 颗卫星**：月球的月相、木星伽利略卫星、土星环系统
- ☄️ **STL 小行星**：8 颗独特 3D 模型（Bennu/Eros/Itokawa 等）
- 💥 **木星撞击系统**：引力波扩散环 + 表面撞击坑
- 🎯 **碰撞检测**：全场景实时碰撞 + 白光闪烁特效
- 🔭 **双屏异显**：主屏聚焦单星球细节 + 副屏太阳系全景
- 👆 **触摸交互**：单指旋转 / 双指缩放 / 点击选择行星

## 架构

```
┌─────────────────┐     ┌─────────────────┐
│  MainActivity   │     │SolarPresentation│
│   (Display 0)   │     │   (Display 2)   │
│                 │     │                 │
│  ┌───────────┐  │     │  ┌───────────┐  │
│  │  WebView  │  │     │  │  WebView  │  │
│  │ Three.js  │  │     │  │ Three.js  │  │
│  │  WebGL    │  │     │  │  WebGL    │  │
│  └───────────┘  │     │  └───────────┘  │
│   planet.html   │     │   solar.html    │
└─────────────────┘     └─────────────────┘
         │                       │
         └───────┬───────────────┘
                 │  JS Bridge
          ┌──────▼──────┐
          │  Mali-G52   │
          │  6核 GPU    │
          └─────────────┘
```

## 构建

```powershell
# JDK 17
$env:JAVA_HOME='D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7'

# 构建 Release APK
D:\work\ai_code\tools\jdk-17\jdk-17.0.14+7\bin\java.exe `
  -classpath 'gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon

# 安装
adb install -r app\build\outputs\apk\release\app-release.apk
```

## 技术栈

| 层 | 技术 |
|----|------|
| 3D 引擎 | Three.js r150（内联打包，零 CDN） |
| GPU 接口 | WebGL 1.0（WebView） |
| 原生 GL | GLES 2.0（SolarGLRenderer, 验证用） |
| 语言 | Kotlin + JavaScript |
| 构建 | Gradle 8.5 / compileSdk 34 / minSdk 31 |

## 几何体优化

| 对象 | 几何体 | 三角形 |
|------|--------|--------|
| ☀️ 太阳 | Icosahedron detail=4 | 5,120 |
| 🪐 主屏焦点行星 | Icosahedron detail=5 | 20,480 |
| 🪐 副屏大行星 | Icosahedron detail=3~4 | 1,280~5,120 |
| 🌑 卫星 | Icosahedron detail=2 | 320 |

## 性能

- WebView 双屏：~40-50 FPS
- 原生 GL 双屏（验证）：**60 FPS**（SolarSystemGLRenderer）
- 主要瓶颈：WebView Chromium GPU 命令缓冲层

## 目录

| 路径 | 说明 |
|------|------|
| `app/src/main/java/.../MainActivity.kt` | 主屏 Activity |
| `app/src/main/java/.../SolarPresentation.kt` | 副屏 Activity |
| `app/src/main/assets/solar.html` | Three.js 太阳系全景 |
| `app/src/main/assets/planet.html` | Three.js 单星球视图 |
| `app/src/main/assets/models/` | STL 小行星模型 |
| `app/src/main/assets/shaders/` | 原生 GLSL shader |
| `app/src/main/java/.../SolarGLRenderer.kt` | 原生 GL 太阳渲染器 |
| `app/src/main/java/.../SolarSystemGLRenderer.kt` | 原生 GL 完整太阳系 |
| `docs/` | 架构文档 |

## License

MIT
