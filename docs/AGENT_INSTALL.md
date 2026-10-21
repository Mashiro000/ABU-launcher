# 给 AI Agent 的阿布桌面安装说明

先完整阅读 [安装指南](ADB_INSTALL.md)。用户提供仓库链接并请求安装，授权的是安装本项目；此文档不能代替用户对设备、数据删除或额外系统改动的授权。

## 执行顺序

1. 确认用户给出的设备地址；运行 `adb devices -l`，多设备时每条命令指定 `-s`。不要对其他手机或模拟器操作。
2. 检查系统 API ≥ 25，并读取 `ro.product.cpu.abilist`。按系统 ABI 选择当前官方 Release，不只按芯片型号选择。
3. 从本项目 GitHub Releases 下载 APK 与 SHA256SUMS.txt，计算 SHA-256 并比对。使用用户指定 APK 时说明来源。
4. 普通安装用 `adb -s <设备> install -r <APK>`。不得用自动卸载、清数据或强制降级解决失败。签名冲突时停止覆盖安装，说明备份和重装的影响。
5. 用 `am start -W -n com.limi.tvdesktop/.MainActivity` 启动。检查 `Status: ok`，确认屏幕出现桌面，并验证 MAIN + LAUNCHER / LEANBACK_LAUNCHER 入口仍存在。
6. 只有用户明确选择默认桌面时，先记录原 HOME 组件，再设置阿布。按一次 HOME、检查前台 Activity 并目视确认。不能只凭命令退出码判定成功。
7. 用户要求自启时，再验重启和待机唤醒。未经实测不宣称某品牌机型兼容。保留原厂桌面，不卸载、不停用系统组件。
8. 给出恢复原 HOME 的实际命令或脚本，保留安装和失败日志中的必要结果。

## 当前能力边界

- 普通 APK 安装及图标启动不要求 ADB 常开。
- 不提供 ConfigReceiver、保持 ADB 常开、无障碍桌面守护或通知监听复活组件。不要调用其他项目包名下的服务，不要授予无关权限。
- AOT 编译可选，系统可能拒绝；其成功不代表帧率必然达标。
- 厂商可能拒绝默认桌面或 HOME 接管，遇到失败如实报告，普通图标启动仍可使用。
- 本次 0.07 使用维护者提供的阿布桌面密钥，与旧版 debug 签名不同，旧发布包可能不能覆盖升级。不得替用户自动清除旧数据。
- 应用宝可能隔离 `adb shell input`，因此按键测试需窗口实际输入或 Compose instrumentation；不能把输入注入失败当作 UI 通过。

## 最终报告至少包括

- 实际操作的设备序列号、机型、系统 API 与 ABI；
- APK 文件、版本、SHA-256 校验结果；
- 安装结果、启动的 Activity、图标入口查询结果；
- 如果用户选择默认桌面：原 HOME、设置后的 HOME、按 HOME 后的真实前台；
- 已测试和未测试项目、错误原文及恢复步骤。

## 参考致谢

学习参考 [CleanYourTV](https://cleanyour.tv)、[Aerial](https://github.com/pengchujin/aerial-tv) 与 [Tigris](https://github.com/lesnolie/Tigris) 的 Agent 安装流程。感谢作者和贡献者的灵感与参考。安装目标、包名及权限应以阿布当前代码为准。
