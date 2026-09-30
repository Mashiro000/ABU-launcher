# 调试与故障排查

先在项目目录运行 `npm run build`、`npm run validate`。若校验报具体字段，修改 `manifest.json` 后重打包；只改清单不重打包不会改变已生成的 ZIP。安装与运行问题要记录插件 ID、版本、宿主 APK 版本、设备型号、Android 版本和操作步骤，不要贴 Token、密码或完整网络响应。

| 现象 | 检查 |
| --- | --- |
| 导入失败 | 包必须是 `.abu-plugin` ZIP，根目录含 `manifest.json` 和真实的 `entry`；文件名大小写一致，路径不得越界；同版本不能再次安装 |
| “宿主 API 不兼容” | 检查 `hostApi` 与 APK；API 1.1 插件需安装包含 1.1 的新 APK，不能只升级 SDK |
| 插件已启用但页面没出现 | 完整页面同一位置只显示一个所有者；检查清单 `surfaces/slots`，以及官方/已验证插件的优先级 |
| 页面空白或脚本错误 | 确认脚本定义 `globalThis.ABUPlugin`，方法同步返回 JSON 对象，入口 ≤2 MiB；输出最多约 1 MiB，UI 最多 300 节点、12 层 |
| 返回键无效 | `navigation.push` 后宿主才有页面栈；根页返回由主程序处理；`render` 返回的导航被忽略 |
| 输入/开关值丢失 | `onAction.values` 只是当前页面值；永久保存需 `storage.set`；JS 全局变量每次执行重置 |
| 能力返回 `ok:false` | 检查清单权限、用户是否拒绝、请求参数、网络白名单；插件必须在 UI 中处理失败 |
| 网络 3xx 或失败 | 宿主不跟随重定向；URL 必须是 HTTPS 且主机与 `networkDomains` 完全匹配，响应 ≤512 KiB |
| 服务找不到 | 提供方须安装且启用、清单声明服务名/版本；调用方 `owner` 是提供方插件 ID，`minimumVersion` 不能高于提供方版本 |
| 签名失败 | `.abu-plugin` 的任一字节变化都会让签名失效；重新打包后重新签名；更新必须继续使用原发布公钥 |
| 遥控器焦点异常 | 先复现于内置 `button/input/list`；记录机型和复现视频，暂不要靠任意 Compose 焦点 API 绕过宿主 |
| 媒体源点播放无反应 | 检查 `streamUrl` 是否为声明域名的 HTTPS URL，插件是否声明并获得 `network` 权限；`value.error` 会在列表页显示 |
| 字幕没有出现 | 确认插件 `kind=subtitle`、已启用、实现 `onSubtitle`，URL 是白名单域名下的 `.srt`/`.vtt`；HLS/DASH 多周期内容对外挂字幕可能有限制 |
| 蓝牙清单为空或失败 | 此 API 只返回已配对设备；确认系统蓝牙已启用、插件授权通过、Android 12+ 的 `BLUETOOTH_CONNECT` 已允许；不会主动扫描 |

主程序的插件详情和运行失败提示会显示部分错误。推荐 `npm run logs -- --id <插件ID>`：只输出插件 ID、调用阶段和结果类别；不会输出能力参数或网络正文。生成项目中可直接 `npm run logs`。若必须看原始系统日志，先运行 `adb devices` 确认连接，再用 `adb logcat -d -s ABUPlugin AndroidRuntime TvPlayer`；原始日志可能包含其他系统/播放器信息，发布 Issue 前必须手工删除 URL 中的令牌、账号和设备地址。
