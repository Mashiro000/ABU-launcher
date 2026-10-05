# ABU Launcher (阿布桌面)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.10.4-green.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-Android%207.1%20(API%2025)-blue.svg)](https://developer.android.com/about/versions/nougat)
[![TargetSdk](https://img.shields.io/badge/targetSdk-Android%2015%20(API%2035)-orange.svg)](https://developer.android.com/about/versions/15)
[![QQ Group](https://img.shields.io/badge/QQ%E4%BA%A4%E6%B5%81%E7%BE%A4-367281933-red.svg)](https://qm.qq.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**ABU Launcher（阿布桌面）** 是一款面向 Android 电视、机顶盒与掌机的原生桌面，围绕 **TV 模式、主机模式、掌机模式** 打造不同设备上的使用体验。

项目基于 **Kotlin + Jetpack Compose** 原生开发，注重遥控器、手柄与触控输入，以及媒体播放、插件扩展和流畅的焦点交互。

> 💬 **用户与开发者官方 QQ 交流群**：**`367281933`**（欢迎加群反馈体验、交流功能建议、获取最新内测包）

---
<img width="1280" height="696" alt="image" src="https://github.com/user-attachments/assets/30a557a0-7090-4b4c-9971-c299461aac7c" />
<img width="1280" height="696" alt="image" src="https://github.com/user-attachments/assets/ed5a1b34-da17-4678-ae06-bc37250366bd" />
<img width="1280" height="696" alt="image" src="https://github.com/user-attachments/assets/a4d841a7-193f-4730-9308-550560fd899c" />
<img width="1164" height="655" alt="image" src="https://github.com/user-attachments/assets/3e697f4c-92e0-4f73-b962-a8996a5eefb1" />

---

## ✨ 核心特性

- 📺 **TV 模式**：当前提供面向大屏的桌面、媒体库、遥控器焦点交互与播放器。
- 🎮 **主机模式**：以减少干扰、突出游戏内容为方向；控制中心已有入口，专属模式功能仍在开发。
- 🕹️ **掌机模式**：面向手持设备、手柄与触控场景；仍在开发，当前版本尚未提供完整模式。
- 🎨 **原生视觉与交互**：
  - G2 连续曲率超椭圆（Squircle）胶囊与卡片。
  - 高动态焦点悬浮光晕（Focus Glow & Sweep）与平滑缩放反馈。
  - 媒体卡片展开至电影/剧集详情页的弹性流体过渡（Spring 物理动画与共享时间轴入场动效）。
- 🌫️ **分代系毛玻璃与渐进模糊**：
  - **Android 13+ (API 33+)**：采用硬件加速的实时渐进式毛玻璃模糊（基于 Haze 1.7.2 渲染真实背景采样与垂直渐变遮罩）。
  - **Android 7.1 ~ 12 (API 25~32)**：后台线程降采样高斯模糊平滑降级，确保高中低端电视芯片均能稳定满帧运行。
- 🎬 **本地与远程媒体库支持**：
  - 支持 WebDAV / Alist / Emby / Plex 多源接入与海报墙展示。
  - 主程序默认使用 Android Media3 与系统硬件解码；MPV 作为可按需安装的官方插件，不再增大主安装包。
- 🧩 **模块化插件平台**：
  - 内置官方插件仓库，可按当前设备 ABI 一键下载、验签、安装、启停和回滚插件。
  - 支持第三方 HTTPS 仓库、本地 `.abu-plugin` 导入、细粒度权限确认和风险提示。
  - 插件可声明设置页、首页、播放器悬浮层及完整页面，并通过受控能力桥访问宿主功能。
- 📺 **全输入形态交互适配**：
  - 完美适配电视遥控器方向键（D-Pad）、确认（Enter/DPad Center）及返回（Back）。
  - 兼容鼠标悬浮、滚轮操作与触控交互，支持标准 Android 手机、平板或车载屏幕。
- 🚀 **系统桌面与独立 TV 应用入口**：
  - 声明 `CATEGORY_HOME`、`CATEGORY_LAUNCHER` 与 `CATEGORY_LEANBACK_LAUNCHER`，既可直接设为系统默认桌面，也可作为普通应用运行。

---

## 💬 社区与交流

如果您在使用过程中遇到任何问题，或者有新功能想法与视觉建议，欢迎通过以下方式交流：

- **官方 QQ 交流群**：**`367281933`**
- **GitHub Issues**：欢迎直接提交 [Issue](https://github.com/Mashiro000/ABU-launcher/issues) 与 [Pull Request](https://github.com/Mashiro000/ABU-launcher/pulls)

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
- **视频引擎**：AndroidX Media3 ExoPlayer 1.4.1（默认）与可选 MPV 插件
- **插件脚本沙箱**：QuickJS；Ed25519 签名校验；独立 Android Service 隔离执行

插件开发者请从 [ABU Plugin SDK 开发指南](plugin-sdk/README.md) 开始；申请收录到应用内官方库请阅读 [官方插件库投稿指南](https://github.com/Mashiro000/ABU-plugins/blob/main/CONTRIBUTING.md)。

---

## 📂 项目结构

```text
├── Android/                    # Android Studio 工程根目录
│   ├── app/                    # 主应用模块 (com.limi.tvdesktop)
│   │   └── src/main/
│   │       ├── java/.../tvdesktop/  # Compose UI、转场、焦点管理、业务逻辑、播放器
│   │       └── res/                 # 矢量图标、壁纸与字体资源
│   ├── references/             # 设计规范、参考图与素材来源说明
│   ├── qa/                     # 验证文档与测试用例
│   ├── build.gradle.kts        # 工程级构建脚本
│   └── settings.gradle.kts     # 模块设置
├── plugin-sdk/                 # 第三方插件 TypeScript SDK、示例与打包工具
├── tools/                      # 官方 MPV 插件构建工具
├── docs/                       # 插件架构与开发文档
├── LICENSE                     # MIT 开源许可证
├── CHANGELOG.md                # 版本更新记录
└── README.md                   # 项目说明文档
```

---

## 🚀 编译与运行

### 运行环境要求
1. **JDK 17** 及以上
2. **Android SDK 36.1**（编译使用，最低运行版本为 Android 7.1 / API 25）
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

编译成功后会同时生成 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 与通用 APK，产物位于：
- **Debug 版**：`Android/app/build/outputs/apk/debug/app-debug.apk`
- **Release 版**：`Android/app/build/outputs/apk/release/`

### 安装到电视或模拟器

```bash
adb connect <电视设备IP>:5555
adb install -r Android/app/build/outputs/apk/release/app-universal-release.apk
```

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。欢迎 Star、Issue 与 Pull Request！
