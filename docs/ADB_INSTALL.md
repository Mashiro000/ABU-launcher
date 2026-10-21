# 阿布桌面 0.07 安装与恢复指南

阿布桌面支持 Android 7.1（API 25）及以上。安装方式互相独立：可以直接点 APK 安装，也可以通过电脑 ADB 或能执行本机命令的 AI Agent 安装。ADB 不是日常启动的必要条件。

## 安装声明

本软件为第三方 Android 桌面，普通安装不会替换或删除原厂桌面。设置默认桌面是用户自行选择的独立操作，是否支持由电视固件决定。本项目不提供破解厂商限制的保证；安装教程学习参考 CleanYourTV、Aerial 与 Tigris，感谢作者和贡献者。第三方项目及素材不构成对本项目的背书。

## 1. 下载哪个安装包

从 [0.07 发布页](https://github.com/Mashiro000/ABU-launcher/releases/tag/v0.07) 下载：

| 文件 | 用途 |
| --- | --- |
| `ABU-Launcher-0.07-arm64-v8a.apk` | 运行 64 位 Android 系统的 ARM 电视、盒子和其他设备 |
| `ABU-Launcher-0.07-armeabi-v7a.apk` | 运行 32 位 ARM Android 系统的老电视、盒子 |
| `ABU-Launcher-0.07-x86.apk` | 32 位 x86 Android 系统 |
| `ABU-Launcher-0.07-x86_64.apk` | 64 位 x86 Android 系统与相应模拟器 |
| `ABU-Launcher-0.07-universal.apk` | 不知道系统架构时使用；包含全部架构，体积更大 |
| `SHA256SUMS.txt` | 安装包 SHA-256 校验清单 |

芯片支持 64 位不代表电视系统一定为 64 位。ADB 查询 `ro.product.cpu.abilist` 最准确；应用宝 Mac 虚拟机本次报告为 `arm64-v8a`。不是所有模拟器都应选择 x86 包。

**升级前注意签名：** 本次使用维护者提供的阿布桌面签名文件（证书 SHA-256：`e6eb923e3ca3026745099ecc91f7410ee32d6f0cc0c3b642bdd62929e7cfe954`）。它与既有 0.06 发布包的签名不同，旧版可能提示“与现有应用冲突”或 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。Android 不允许不同签名直接覆盖。不要反复点击安装，也不要让脚本自动卸载；先记录或备份媒体源、Dock、文件夹和壁纸配置，再自行决定卸载重装。卸载会删除应用数据；系统不保证能备份受保护的应用配置。本版不承诺跨签名迁移。

## 2. 直接点击 APK 安装（不需要电脑）

1. 将 APK 下载到电视，或复制到 U 盘。
2. 在电视文件管理器中打开 APK。
3. 如果出现未知来源提示，只允许本次使用的文件管理器安装应用，再返回安装页面确认。
4. 安装成功后，选择“打开”，或回到电视的“应用 / 全部应用”列表，点击“阿布桌面”图标。
5. 阿布可以作为普通应用运行。想把它作为默认桌面，再打开阿布“设置 → 系统与关于 → 设置默认桌面”，按系统提示选择。

系统是否允许第三方默认桌面取决于固件。普通安装不修改原厂桌面，不要求 root，不要求保持 ADB 开启。设置失败时，仍可通过阿布应用图标打开。

## 3. 电脑准备与电视授权

### 电脑

安装 Google 官方 [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)，解压后在该目录打开终端。确认：

```bash
adb version
```

Windows PowerShell 若未加入 PATH，请在 platform-tools 目录使用 `.\adb.exe`。后文 PowerShell 示例采用此写法。

macOS/Linux 可以把 platform-tools 加入 PATH，或使用 adb 的完整路径。脚本接受 `ADB=/完整路径/adb`，包含空格的路径需要引号。

### 电视

电脑和电视连接同一局域网。在电视“关于”或“设备偏好设置”中连续点击“版本号”进入开发者选项，打开“USB 调试 / 网络调试 / ADB 调试”。不同品牌、固件位置不同，以设备菜单为准。

- 原生 Android TV / Google TV、索尼：通常在系统或设备偏好设置的“关于 → 版本号”，进入开发者选项后开启调试。
- 小米 / Redmi：部分系统在“关于 → 产品型号”连续确认，再到“账号与安全”开启 ADB。
- TCL / 雷鸟、海信等：固件差异较大，先找开发者选项；若没有开关，查对应型号的官方或厂商说明，不能套用其他机型命令。

第一次连接会在电视上弹出调试授权，用遥控器确认。仅配对自己信任的电脑。

## 4. 手动 ADB 安装

### macOS / Linux

把下面地址和文件名替换成实际值：

```bash
adb connect 192.168.1.20:5555
adb devices -l
adb -s 192.168.1.20:5555 shell getprop ro.build.version.sdk
adb -s 192.168.1.20:5555 shell getprop ro.product.cpu.abilist
adb -s 192.168.1.20:5555 install -r ABU-Launcher-0.07-arm64-v8a.apk
adb -s 192.168.1.20:5555 shell am start -W -n com.limi.tvdesktop/.MainActivity
```

### Windows PowerShell

```powershell
.\adb.exe connect 192.168.1.20:5555
.\adb.exe devices -l
.\adb.exe -s 192.168.1.20:5555 shell getprop ro.product.cpu.abilist
.\adb.exe -s 192.168.1.20:5555 install -r "C:\Downloads\ABU-Launcher-0.07-arm64-v8a.apk"
.\adb.exe -s 192.168.1.20:5555 shell am start -W -n com.limi.tvdesktop/.MainActivity
```

连接多个设备时，每条命令必须带 `-s`，避免装到手机上。无线调试端口不一定是 5555：若电视显示配对码与端口，先 `adb pair <IP:配对端口>` 输入电视配对码，再连接电视显示的调试端口；两个端口可能不同。

USB 连接使用 `adb devices -l` 中显示的序列号替代 `IP:端口`。电视 USB 口不一定支持电脑调试连接。

安装成功应输出 `Success`，启动应显示 `Status: ok`。再手动从电视应用列表点击图标，确认正常打开。

### 可选：设置为默认桌面

先记录原默认桌面，再设置。不要卸载或停用原厂桌面。

```bash
adb -s 192.168.1.20:5555 shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s 192.168.1.20:5555 shell cmd package set-home-activity com.limi.tvdesktop/.MainActivity
adb -s 192.168.1.20:5555 shell input keyevent KEYCODE_HOME
adb -s 192.168.1.20:5555 shell dumpsys activity activities
```

观察 `mResumedActivity` / `topResumedActivity` 是否包含 `com.limi.tvdesktop`，并目视确认阿布已出现。仅命令退出成功或显示 Success 不能证明厂商 HOME 接管成功。还需在真机验证重启和待机唤醒。

当前版本没有无障碍桌面守护、通知监听复活或保持 ADB 常开组件。不要给这些尚不存在的能力授权，也不要声称安装脚本可以绕过厂商限制。

## 5. 一键脚本（macOS / Linux / Git Bash）

下载或克隆本仓库，在仓库根目录运行。脚本默认按电视系统 ABI 下载本版官方 Release，并校验发布清单；也可以指定已下载 APK。

```bash
# 普通应用安装：不改默认桌面
TV=192.168.1.20 bash tools/install.sh

# 使用本地 APK；包含空格时加引号
TV=192.168.1.20 APK_FILE="$HOME/Downloads/ABU-Launcher-0.07-arm64-v8a.apk" bash tools/install.sh

# 显式选择默认桌面；记录原桌面并验证 HOME
TV=192.168.1.20 SET_HOME=1 bash tools/install.sh

# USB / 模拟器设备序列号
TV=emulator-5554 APK_FILE=/path/to/package.apk bash tools/install.sh
```

| 参数 | 默认与作用 |
| --- | --- |
| `TV` | 必填；IP、IP:端口或设备序列号，也接受 `ANDROID_SERIAL` |
| `PORT` | 只有 IP 时默认 5555 |
| `ADB` | 默认 adb，可指定绝对路径 |
| `APK_FILE` | 使用本地 APK；优先于下载 |
| `APK_URL` | 使用自选下载地址；建议同时提供 `SHA256` |
| `SHA256` | 校验本地或自选 APK；官方下载自动读取清单 |
| `VERSION` | 默认 0.07，官方下载版本 |
| `SET_HOME` | 默认 0；显式设为 1 才修改默认桌面 |
| `SKIP_AOT` | 默认 0；设为 1 跳过可选 AOT 编译 |
| `PKG` | 默认 com.limi.tvdesktop；一般不改 |

AOT 是可选编译优化，不保证所有设备支持，也不代替实际帧率测试。脚本不注入无关权限，不自动卸载应用。退出码 2 表示默认桌面配置或验证未完成，普通安装可能已经成功，查看具体输出。

## 6. 让 AI Agent 帮你安装

使用能在电脑本机执行命令的 Agent。普通网页聊天不能直接操作电视。可以复制以下提示，并补上地址：

> 请完整阅读 https://github.com/Mashiro000/ABU-launcher/blob/main/docs/ADB_INSTALL.md 和 docs/AGENT_INSTALL.md，帮我安装官方 ABU Launcher 0.07。电视地址：<IP:端口>。是否设为默认桌面：<是/否>。先确认设备和系统 ABI，下载对应 APK 并核对 SHA-256，保留原厂桌面。需要电视授权时提醒我，遇到签名冲突停止，不自动卸载或清数据。安装后实际验证启动、应用图标入口和 HOME（如果选择），给出命令结果及恢复方法，不把未实现的功能说成成功。

## 7. 恢复原桌面

若使用了脚本的 `SET_HOME=1`：

```bash
TV=192.168.1.20 bash tools/restore.sh
```

恢复脚本只读取安装时记录的 `abu_prev_home`，恢复并核对原桌面。不重置无关权限，不清除阿布配置。没有有效记录时停止自动恢复，提示到电视设置手动选择系统桌面。

如果安装时是手动设置默认桌面，可用记录的真实组件名恢复：

```bash
adb -s 192.168.1.20:5555 shell cmd package set-home-activity <原桌面包名/原桌面Activity>
```

确认按 HOME 回到原桌面后，才能考虑卸载。确实要卸载应用并删除数据时：

```bash
TV=192.168.1.20 UNINSTALL=1 bash tools/restore.sh
```

普通 APK 安装、从未设默认桌面的用户，可在系统应用管理中卸载；阿布不会替换或删除系统桌面。

## 8. 常见问题

| 表现 | 处理 |
| --- | --- |
| `unauthorized` | 在电视上确认本台电脑的调试授权 |
| `offline` / 连接拒绝 | 检查电视开机、调试开关、当前 IP 与端口、局域网隔离和防火墙 |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | 根据系统 abilist 重新选择 APK，或使用 universal |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名不同；停止自动操作，参考前面的升级说明 |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | 选择更高版本；不强行降级覆盖数据 |
| `INSTALL_FAILED_VERIFICATION_FAILURE` | 厂商安装校验或电视未确认；尝试电视文件管理器点击 APK，仍失败则记录机型与错误 |
| 找不到阿布图标 | 查看应用管理、全部应用或 TV 应用入口；用 ADB 查询 LAUNCHER 与 LEANBACK_LAUNCHER，固件可能过滤第三方应用 |
| 设置默认桌面后 HOME 仍回原厂 | 系统固件限制；保留普通应用运行，不删除系统桌面 |
| 视频壁纸黑屏或卡顿 | 确认文件权限和解码支持，改用较低分辨率 H.264 或静态壁纸；失效文件回退默认背景 |
| 应用宝中 adb 注入按键无反应 | 它可能隔离系统注入事件；使用虚拟机窗口的实际键盘，或 Compose instrumentation 验证；不能据此推断真机遥控器失效 |

## 9. Dock 内容与新壁纸设置

“设置 → 桌面与外观 → Dock 内容预览”默认关闭。开启后，若系统提供 Android TV 内容频道，首次使用会请求读取电视内容列表。允许后可以显示 Dock 应用公开的推荐和 Watch Next，点击“打开内容”进入对应内容；无数据或不支持时保留原壁纸。该功能不需要保持 ADB 开启，也不申请系统级 ACCESS_ALL_EPG_DATA 权限。

“设置 → 桌面与外观 → 壁纸 → 渐变色”提供六组原创配色，圆形渐变色块选择后立即应用。没有下载步骤，不依赖外部视频素材。

“设置 → 桌面与外观 → 导航条”可调整项目和显隐。隐藏整条导航条后，仍可点击右上角时钟/头像进入控制中心和设置恢复。

应用访问锁使用方向密码，输入时只显示统一底框中的圆点数量。不要把签名密钥密码与应用访问锁混为一谈，两者互相独立。

### 在线视频壁纸

进入“设置 → 桌面与外观 → 壁纸 → 视频壁纸 → 浏览并下载在线视频壁纸”。卡片使用从对应视频提取的真实小图，全部联网按页获取并缓存，不包含在 APK 中。选择视频后点击下载，可查看进度或取消；完成后点击“设为视频壁纸”。已下载项可以删除，删除正在使用的视频会恢复渐变背景。保留“选择本地视频”入口。

内置 114 个 Apple Aerial 公共 CDN 视频链接，支持四类筛选、分页和已下载筛选。视频采用 H.264 SDR，通常为数百 MB；视频不内置在 APK 中。下载前检查存储空间，离开下载页会取消未完成任务；失败时清理临时文件。视频版权归原权利人所有。网络或解码器不支持时可选择本地视频或渐变壁纸。

### Bing 自动更新与锁定

选择 Bing 来源后自动获取图片；桌面运行时每 30 分钟检查日期，当天成功后不重复下载。跨日自动更新，断网稍后重试，退出程序期间不唤醒设备，下次使用补更新。打开“锁定当前 Bing 壁纸”或主动选择历史图片后保留当前背景；关闭锁定恢复更新。锁定时“下载今日图片”只下载，不替换当前图。

## 10. 参考与致谢

本指南参考 [CleanYourTV](https://cleanyour.tv)、[Aerial](https://github.com/pengchujin/aerial-tv) 与 [Tigris](https://github.com/lesnolie/Tigris) 的 Agent 安装、调试授权及验收思路。感谢作者与贡献者提供的灵感和参考。阿布保留自身设计与安装能力边界，不把其他项目的功能当作本项目已实现能力。


视频下载提示证书错误时，请先确认使用新版。Apple CDN 使用 Apple 官方根证书，0.07 审核修订已对精确域名补充官方根证书，仍验证证书链和主机名，不需要用户安装系统证书或关闭验证。
