# ABU Launcher (阿布桌面)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.10.4-green.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/minSdk-Android%207.1%20(API%2025)-blue.svg)](https://developer.android.com/about/versions/nougat)
[![TargetSdk](https://img.shields.io/badge/targetSdk-Android%2015%20(API%2035)-orange.svg)](https://developer.android.com/about/versions/15)
[![QQ Group](https://img.shields.io/badge/QQ%E4%BA%A4%E6%B5%81%E7%BE%A4-367281933-red.svg)](https://qm.qq.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**ABU Launcher（阿布桌面）** 是一款面向 Android 电视、机顶盒与掌机的原生桌面，围绕 **TV 模式、主机模式、掌机模式** 打造不同设备上的使用体验。

项目基于 **Kotlin + Jetpack Compose** 原生开发，注重遥控器、手柄与触控输入，以及媒体播放、插件扩展和流畅的焦点交互。

最新版本 **0.07**：[下载安装包](https://github.com/Mashiro000/ABU-launcher/releases/tag/v0.07) · [更新日志](CHANGELOG.md) · [版本说明](docs/RELEASE_NOTES_0_07.md)。

> **安装声明**：支持直接点击 APK 安装，安装后可从应用列表点击“阿布桌面”启动；ADB 与默认桌面设置均为可选。本版签名与旧 0.06 不同，覆盖安装可能失败，卸载会清除配置，请先记录或备份。详见[安装与恢复指南](docs/ADB_INSTALL.md)。

> 💬 **用户与开发者官方 QQ 交流群**：**`367281933`**（欢迎加群反馈体验、交流功能建议、获取最新内测包）

---

## 📥 安装

支持 Android 7.1 及以上的电视和盒子，选择下面任意一种方式即可。

### 方式一：直接安装（不需要电脑）

1. 打开 **[下载安装包](https://github.com/Mashiro000/ABU-launcher/releases/latest)**，在发布页的 **Assets（资源）** 中下载 APK；不知道选哪个，就下载文件名带 **`universal`** 的通用包。
2. 将 APK 复制到 U 盘插入电视，或直接下载到电视。
3. 用电视文件管理器打开 APK，按提示允许安装；安装完成后，选择“打开”，或从“全部应用”打开“阿布桌面”。
4. 想按 Home 返回阿布：进入阿布 **设置 → 系统与关于 → 设置默认桌面**，按系统提示选择阿布。

### 方式二：让 AI Agent 帮你安装（使用电脑）

适合不想自己输入命令的用户。使用 **Codex、Claude Code、Trae 等能在电脑上执行终端命令的 Agent**；普通网页聊天无法直接给电视安装软件。

1. 电脑和电视连接同一个网络。
2. 在电视设置里打开 **ADB 调试 / 网络调试**，并在网络设置里找到电视 IP。不知道怎么打开，可以把电视品牌和型号告诉 Agent。
3. 复制下面整句话发给 Agent，将 **`192.168.1.20`** 改为你的电视 IP。代码块右上角可点击复制：

```text
请按 https://github.com/Mashiro000/ABU-launcher/blob/main/docs/AGENT_INSTALL.md 帮我给电视安装阿布桌面，电视 IP 是 192.168.1.20，按 Home 返回阿布；需要我操作电视时提醒我，完成后检查并告诉我怎么恢复。
```

4. 首次连接时，在电视上用遥控器确认“允许调试”；安装后按一次实体遥控器的 **Home 键**，确认是否进入阿布。

**只是想安装、不想更换默认桌面？** 将提示词中的“按 Home 返回阿布”改为“只安装，不更换默认桌面”即可。

**版本说明：** 当前已发布的 0.07 支持直接安装、ADB 安装和系统允许的默认桌面设置；新增“桌面守护”尚未随新版 APK 发布。部分电视会限制第三方桌面，当前版本不能保证接管 Home。后续支持守护的安装包发布后，Agent 应先确认实际版本和服务，再按需启用。

安装遇到问题或想恢复原桌面，查看 **[安装与恢复说明](docs/ADB_INSTALL.md)**。旧版升级出现签名冲突时先记录配置；卸载会清除数据。

---
本次 0.07 实际界面（应用宝与 Android 模拟器截图）：

![渐变桌面](docs/images/0.07/gradient-home.png)
![应用移动](docs/images/0.07/move-app.png)
![壁纸设置与渐变色卡](docs/images/0.07/gradient-picker.png)
![在线视频壁纸与真实预览](docs/images/0.07/video-wallpaper.png)
![空媒体库与添加入口](docs/images/0.07/media-library.png)
![添加媒体库弹窗](docs/images/0.07/add-library.png)
![视频壁纸与实时控制中心](docs/images/0.07/video-qa/realtime-control-center.png)
![文件夹待选、完成与取消](docs/images/0.07/review/folder-staging.png)

视频和预览画面来源见 [素材说明](docs/VIDEO_WALLPAPER_SOURCES.md)。

---

## ✨ 核心特性

- 📺 **TV 模式**：当前提供面向大屏的桌面、媒体库、遥控器焦点交互与播放器。
- 🎮 **主机模式**：以减少干扰、突出游戏内容为方向；控制中心已有入口，专属模式功能仍在开发。
- 🕹️ **掌机模式**：面向手持设备、手柄与触控场景；仍在开发，当前版本尚未提供完整模式。
- 🎨 **原生视觉与交互**：
  - G2 连续曲率超椭圆（Squircle）胶囊与卡片。
  - 高动态焦点悬浮光晕（Focus Glow & Sweep）与平滑缩放反馈。
  - 媒体卡片展开至电影/剧集详情页的弹性流体过渡（Spring 物理动画与共享时间轴入场动效）。
- 🎨 **原创渐变壁纸**：六组内置配色，圆形渐变色块选择，代码生成，基本不增加素材体积。
- 🎞️ **视频与每日壁纸**：内置在线航拍目录、真实画面预览、按需下载与本地视频；Bing 每日检查，支持锁定和历史选择。
- 📺 **Dock 内容预览（可关闭）**：读取支持应用的 Android TV 推荐频道与 Watch Next，展示内容与跳转按钮；没有公开数据时保留壁纸，默认关闭。
- 🌫️ **分代系毛玻璃与渐进模糊**：
  - **Android 12+ (API 31+)**：支持硬件加速实时背景模糊；Android 13+ 的设置背景支持渐进式模糊，导航条使用均匀模糊。
  - **Android 7.1 起**：提供后台降采样静态模糊，效果可按设备性能调整或关闭。
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
1. **JDK 21**（Gradle daemon 配置要求）
2. **Android SDK 36.1**（编译使用，最低运行版本为 Android 7.1 / API 25）
3. 最新版 **Android Studio** (推荐 Ladybug / Meerkat 或更高版本)

### 命令行编译

macOS / Linux：在 Android 目录运行 `./gradlew assembleRelease`。可通过 `ABU_SIGNING_STORE` 引用既有签名文件；密码与别名使用环境变量 `ABU_SIGNING_STORE_PASSWORD`、`ABU_SIGNING_ALIAS`、`ABU_SIGNING_KEY_PASSWORD`，不要把密钥或密码提交到仓库。未指定时沿用 Android debug 签名流程。


在 `Android` 目录下执行：

```powershell
# Windows 编译 Debug 版本
cd Android
./gradlew.bat assembleDebug

# Windows 编译 Release 版本
./gradlew.bat assembleRelease
```

编译成功后会同时生成 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 与通用 APK，产物位于：
- **Debug 版**：`Android/app/build/outputs/apk/debug/app-<ABI>-debug.apk`
- **Release 版**：`Android/app/build/outputs/apk/release/`

### 参考与致谢

阿布桌面在开发过程中学习和参考了 [CleanYourTV](https://cleanyour.tv)、[Aerial](https://github.com/pengchujin/aerial-tv) 与 [Tigris](https://github.com/lesnolie/Tigris) 的安装引导、电视交互及性能优化思路。感谢作者与贡献者，他们的开源工作为本项目提供了灵感和参考。

阿布按自身设计风格和功能需求独立实现；如使用第三方代码、字体或素材，保留对应来源、版权与许可证声明。参考项目与本项目不存在背书关系。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。欢迎 Star、Issue 与 Pull Request！
