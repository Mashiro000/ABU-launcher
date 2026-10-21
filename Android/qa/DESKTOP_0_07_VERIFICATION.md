# 0.07 桌面验证记录

日期：2026-10-22。构建：versionName 0.07 / versionCode 9 / minSdk 25。

## 构建与自动化

- `:app:testDebugUnitTest`：43 项 JVM 测试通过，覆盖移动边界、文件夹动画时间轴、静态模糊策略、更新包架构选择等。
- `python3 tools/test-install.py`：4 项离线安装脚本契约测试通过。
- 官方 Android 模拟器：Android 37 ARM64，横屏 1920×1080 / density 160；24 项设备测试通过（`OK (24 tests)`）。
- 设备用例包含：Dock 与应用区分别移动及保存、被移动项语义标识与网格无重叠、空媒体库直达添加页、隐藏导航条仍可进入控制中心、导航条移动、设置逐项上下移动和分类/返回焦点、静态模糊策略、六种渐变和持久化、二级设置标题单行、Dock 内容过滤与 Intent 安全、方向密码数量圆点、在线视频目录、Bing 锁定/解锁状态，以及视频下载后视频轨道检查和解码。
- 最后调整色卡点击焦点与旧滚动请求后，对桌面、设置导航、渐变再次复测；结果见下方最终补记。
- AndroidX Test 升级为 runner 1.7.0、ext.junit 1.3.0、Espresso 3.7.0，以适配新模拟器输入接口；只影响测试依赖。

## 视频源与网络边界

四条 Apple 公共 CDN 地址均通过主机 HTTPS HEAD 检查，返回 200，视频约 205–439 MiB。真实预览图从各自视频第一秒提取，四张 JPEG 共 98,020 字节，已人工核对海狮、冰峡湾和太空画面。

官方模拟器直连公网下载遇到 `SSLHandshakeException: Trust anchor for certification path not found`，属于该隔离环境的证书信任限制。没有关闭 TLS 校验或安装新的信任证书。下载集成测试使用主机正常 HTTPS 下载的同一 214,607,394 字节官方视频，通过只绑定本机的测试 HTTP 服务和 adb reverse 转发到模拟器；应用执行真实流式下载、长度/轨道校验、原子落盘与首帧解码，测试后清理测试视频。这验证下载和解码代码路径，不等同于所有电视网络的 CDN 连通性测试。

## 应用宝

应用宝 Android 12L / API 32 虚拟机安装同代码独立 debug 包成功；实际 `am start -W` 返回 `Status: ok`、`LaunchState: COLD`，可通过 MainActivity 启动。没有卸载原有正式版或清理其配置。早期设置导航用例及静态模糊用例在此环境有通过记录。

本轮完整 instrumentation 受到应用宝宿主窗口生命周期影响，出现 `Input dispatching timed out (Application does not have a focused window)`，因此完整回归采用官方模拟器交叉执行，不把应用宝超时记录当成通过。

## 截图

新版图片位于 [`docs/images/0.07`](../../docs/images/0.07)，由实际 Compose 界面捕获。README 采用桌面、应用移动、媒体库、壁纸设置等本次截图，不再使用第一版远程截图。孤立组件测试截图仅作 QA 辅助，不代表整个桌面的最终版式。

## 适用范围与限制

- 模拟器不能替代低性能电视的真实 GPU 帧率、解码器、厂商 HOME 限制、遥控器连发和待机测试；本次没有虚构真机 FPS 数据。
- 应用宝没有可用的 Android TV 内容频道提供者，真实第三方 Dock 内容效果仍取决于支持的电视固件和应用；无数据时保留壁纸。
- Bing 锁定/恢复逻辑有设备测试；跨真实自然日的持续联网更新未进行 24 小时运行测试。实现为使用桌面期间按日期检查，非强制定时后台唤醒。
- 新版官方模拟器显示现有原生库的 16 KB 页兼容提示，需要系统兼容模式；尚未完成 QuickJS 等依赖的 16 KB 原生库专项迁移。常见旧电视的 4 KB 页场景不受这条提示约束。
- 0.07 使用维护者提供的正式密钥，和旧 0.06 debug 证书不同，不能保证直接覆盖；安装指南明确提示备份/记录配置后自行决定卸载重装，脚本不会自动清数据。

## 最终补记

- 额外色卡完整可见性用例通过（`OK (1 test)`），明确使用遥控器/键盘输入模式，断言聚焦正确且底边保留至少 12px，已重新截图。

- 最后焦点滚动修复后的 14 项针对性回归通过（`OK (14 tests)`）。
- 最终 `assembleRelease` 成功，R8 与 Release Lint Vital 通过。
- 五种 ABI 安装包均通过 apksigner 签名校验，证书一致；package `com.limi.tvdesktop`、versionCode 9、versionName 0.07、普通 LAUNCHER 启动入口均已检查。
- ARM64 正式签名包在官方模拟器实际安装成功，MainActivity 冷启动返回 `Status: ok`，约 894ms；保留普通 APK 安装与应用图标启动方式。
- 包体：ARM64 约 5.33 MB（旧版 11.49 MB），ARM32 4.61 MB，x86 5.24 MB，x86_64 5.37 MB，universal 11.32 MB；大小与校验值以 Release 附件和 SHA256SUMS.txt 为准。

