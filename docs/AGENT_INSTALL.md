# 给 AI Agent 的阿布桌面安装说明

本页供能够在用户电脑上执行终端命令的 Agent 阅读。用户无需理解下面的技术步骤；先读完，再实际执行。用户常用提示词：

```text
请按 https://github.com/Mashiro000/ABU-launcher/blob/main/docs/AGENT_INSTALL.md 帮我给电视安装阿布桌面，电视 IP 是 192.168.1.20，按 Home 返回阿布；需要我操作电视时提醒我，完成后检查并告诉我怎么恢复。
```

## 1. 准备与连接

- 使用用户提供的电视 IP，确认电脑和电视在同一网络。缺少型号、授权或配对信息时，只问当下必要的信息。
- 检查 `adb version`；没有时安装 Google 官方 [Platform-Tools](https://developer.android.com/tools/releases/platform-tools)。
- 使用 `adb connect <IP:端口>` 和 `adb devices -l` 检查连接，所有后续命令均带 `-s <设备>`。传统网络 ADB 通常使用 5555；无线调试先按电视提示配对，再连接显示的调试端口。
- 出现 unauthorized 时提醒用户在电视上允许调试。不得操作用户未指定的设备。
- 读取 `ro.build.version.sdk` 和 `ro.product.cpu.abilist`，要求 API ≥ 25。

## 2. 下载、校验和安装

- 从 [官方 Releases](https://github.com/Mashiro000/ABU-launcher/releases) 读取最新已发布版本及实际资源名，不猜测 URL。按电视系统 ABI 选择 APK；无法确定时使用 universal。
- 下载发布的 SHA256SUMS.txt，并计算、核对 APK SHA-256。签名冲突、版本降级或安装失败时停止并解释，不自动卸载、清数据或强制降级。
- 执行 `adb -s <设备> install -r <APK>`，确认 Success。
- release 包名为 `com.limi.tvdesktop`，Activity 完整名称为 `com.limi.tvdesktop.MainActivity`。用 `am start -W -n com.limi.tvdesktop/com.limi.tvdesktop.MainActivity` 启动，确认 Status: ok，检查屏幕与普通应用入口。
- macOS / Linux / Git Bash 可使用本仓库 `tools/install.sh`；Windows 也可以逐条执行等价 ADB 命令。读取脚本支持的参数后再用，不把其他项目的参数、服务或包名用于阿布。
- 用户明确提供本地开发 APK 时可以使用它，说明来源。debug 包名是 `com.limi.tvdesktop.debug`，Activity 类名仍为 `com.limi.tvdesktop.MainActivity`。

## 3. 按 Home 返回阿布

用户说“按 Home 返回阿布”即授权设置默认桌面。用户说“只安装”时跳过本节。

1. 查询并记录原 HOME：`cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`。确保保存可用的原桌面组件，后续用于恢复。
2. 先确认阿布可启动，再执行 `cmd package set-home-activity com.limi.tvdesktop/com.limi.tvdesktop.MainActivity`。脚本方式使用 `SET_HOME=1`，可记录原桌面。
3. 按 Home 并检查 dumpsys activity/window 中实际前台，同时请用户用实体遥控器确认；命令成功或 HOME 解析结果不能单独证明接管成功。
4. 系统拒绝时，先检查实际安装版本与服务。**当前发布的 0.07 不包含新增 HomeGuardService / RevivalListener，不能给旧 APK 盲目授权，也不能宣称已启用守护。** 新守护仅在本地开发源码中实现，尚未发布。不要擅自给普通用户编译、安装开发版来替代官方发布包。
5. 后续官方发布包明确包含守护时，再读取该版本的安装脚本和说明。如脚本支持 `HOME_GUARD=1`，按需启用；说明涉及无障碍与可选通知监听权限，保留其他无障碍服务。当前远端脚本不一定具有该参数，不能直接假定支持。
6. 不卸载或停用原厂桌面，不使用 Aerial / CleanYourTV 包名或服务，不改无关系统属性，不保持 ADB 常开。厂商拒绝安装或接管时如实报告，保留普通应用启动方式。

## 4. 检查与交付

- 检查 APK 版本、校验结果、安装结果、启动入口与实际前台。
- 请用户按实体遥控器 Home，并确认待机唤醒；重启前征得用户同意。未经实测不声称开机自启或某品牌兼容。
- 最后简短说明：装了哪个版本、Home 是否通过、哪些未验证、如何恢复；失败时附关键错误，不将已安装等同于已接管。
- 脚本安装有原 HOME 记录时，恢复命令为 `TV=<设备> bash tools/restore.sh`；如果是手动安装，使用已记录的原组件执行 set-home-activity，再按 Home 检查。保留阿布和数据。
- 后续启用了守护的版本，恢复时先关闭守护及相关服务，再恢复原 HOME；以对应版本实现为准。

普通用户教程见 [安装与恢复说明](ADB_INSTALL.md)。安装思路参考 [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 和 [Aerial](https://github.com/pengchujin/aerial-tv)。
