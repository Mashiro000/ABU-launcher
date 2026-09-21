# ABU Launcher (阿布桌面)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.10.4-green.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-Android%209%20(API%2028)-blue.svg)](https://developer.android.com/about/versions/pie)
[![TargetSdk](https://img.shields.io/badge/targetSdk-Android%2015%20(API%2035)-orange.svg)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**ABU Launcher（阿布桌面）** 是一款专为 Android 智能电视及机顶盒大屏设备打造的高性能、现代化**原生电视桌面（Android TV Launcher）**。

基于 **100% Kotlin + Jetpack Compose** 纯原生开发，彻底摆脱传统 Android TV 对旧版 Leanback 架构或跨平台方案（如 Flutter）的依赖，深度复刻类似 Apple TV / tvOS 的极致流体拟态视觉与焦点动效交互。

---
<img width="1280" height="696" alt="image" src="https://github.com/user-attachments/assets/30a557a0-7090-4b4c-9971-c299461aac7c" />
<img width="1280" height="696" alt="image" src="https://github.com/user-attachments/assets/ed5a1b34-da17-4678-ae06-bc37250366bd" />
<img width="1280" height="696" alt="image" src="https://github.com/user-attachments/assets/a4d841a7-193f-4730-9308-550560fd899c" />
<img width="1164" height="655" alt="image" src="https://github.com/user-attachments/assets/3e697f4c-92e0-4f73-b962-a8996a5eefb1" />





## ✨ 核心特性

- 🎨 **Apple TV / tvOS 视觉哲学**：
  - G2 连续曲率超椭圆（Squircle）胶囊与卡片。
  - 高动态焦点悬浮光晕（Focus Glow & Sweep）与平滑缩放反馈。
  - 媒体卡片展开至电影/剧集详情页的弹性流体过渡（Spring 物理动画与共享时间轴入场动效）。
- 🌫️ **分代系毛玻璃与渐进模糊**：
  - **Android 13+ (API 33+)**：采用硬件加速的实时渐进式毛玻璃模糊（基于 Haze 1.7.2 渲染真实背景采样与垂直渐变遮罩）。
  - **Android 9 ~ 12 (API 28~32)**：后台线程降采样高斯模糊平滑降级，确保高中低端电视芯片均能稳定满帧运行。
- 📺 **全输入形态交互适配**：
  - 完美适配电视遥控器方向键（D-Pad）、确认（Enter/DPad Center）及返回（Back）。
  - 兼容鼠标悬浮、滚轮操作与触控交互，支持标准 Android 手机、平板或车载屏幕。
- 🚀 **系统桌面与独立 TV 应用入口**：
  - 声明 `CATEGORY_HOME`、`CATEGORY_LAUNCHER` 与 `CATEGORY_LEANBACK_LAUNCHER`，既可直接设为系统默认桌面，也可作为普通应用运行。

---

## 🛠️ 技术栈与架构

- **应用名称**：ABU Launcher（阿布桌面）
- **包名 (Namespace)**：`com.limi.tvdesktop`
- **编程语言**：Kotlin 2.4.10
- **构建系统**：Gradle 9.5.0 / Android Gradle Plugin 9.3.2
- **Java 版本**：Java 17 (JavaVersion.VERSION_17)
- **UI 框架**：
  - Jetpack Compose Foundation & UI 1.10.4
  - Material 3 (1.4.0)
  - Activity Compose 1.12.2
- **毛玻璃效果**：Dev Chrisbanes Haze 1.7.2

---

## 📂 项目结构

```text
├── Android/                    # Android Studio 工程根目录
│   ├── app/                    # 主应用模块 (com.limi.tvdesktop)
│   │   └── src/main/
│   │       ├── java/.../tvdesktop/  # Compose UI、转场、焦点管理、业务逻辑
│   │       └── res/                 # 矢量图标、壁纸与字体资源
│   ├── references/             # 设计规范、参考图与素材来源说明
│   ├── qa/                     # 验证文档与测试用例
│   ├── build.gradle.kts        # 工程级构建脚本
│   └── settings.gradle.kts     # 模块设置
├── LICENSE                     # MIT 开源许可证
└── README.md                   # 项目说明文档
```

---

## 🚀 编译与运行

### 运行环境要求
1. **JDK 17** 及以上
2. **Android SDK 36.1**（编译使用，最低运行版本依旧为 Android 9）
3. 最新版 **Android Studio** (推荐 Ladybug / Meerkat 或更高版本)

### 命令行编译

在 `Android` 目录下执行：

```powershell
# Windows 编译 Debug 版本
cd Android
./gradlew.bat assembleDebug

# Windows 编译 Release 版本
./gradlew.bat assembleRelease
```

编译成功后，APK 产物位于：
- **Debug 版**：`Android/app/build/outputs/apk/debug/app-debug.apk`
- **Release 版**：`Android/app/build/outputs/apk/release/app-release-unsigned.apk`

### 安装到电视或模拟器

```bash
adb connect <电视设备IP>:5555
adb install -r Android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。欢迎 Star、Issue 与 Pull Request！
