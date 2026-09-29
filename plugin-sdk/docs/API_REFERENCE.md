# 宿主 API 1.1 参考

本页描述当前源码中的脚本插件协议；需要包含 API 1.1 的新 APK。已发布 0.03 的边界见 [兼容表](COMPATIBILITY.md)。TypeScript 类型在 [`../types/abu-plugin.d.ts`](../types/abu-plugin.d.ts)。

## 包与清单

`.abu-plugin` 是 ZIP，根目录至少有 `manifest.json` 和清单指定的 `.js` 入口；可选 `assets/`。脚本包不得含 `.so`、`.dex` 或 `.apk`。入口最大 2 MiB、解压总量最大 200 MiB、文件最多 2000 个。宿主拒绝目录穿越；不要用符号链接。SDK 校验器还限制 ZIP 最多 250 MiB。`player` 原生插件不是第三方脚本插件类型。

```json
{
  "schemaVersion": 1,
  "id": "com.example.demo",
  "name": "Demo",
  "version": "1.0.0",
  "author": "Your Name",
  "description": "A sample plugin",
  "kind": "ui",
  "entry": "dist/index.js",
  "hostApi": ">=1.1.0 <2.0.0",
  "surfaces": ["home"],
  "slots": ["home.quickActions"],
  "permissions": [{ "id": "storage", "title": "保存设置", "sensitive": false }],
  "networkDomains": [],
  "services": [{ "name": "library", "version": 1 }]
}
```

`id` 为 3–100 位英文字母、数字、点、连字符或下划线；发布后保持不变。`version` 是 1–64 位版本字符串；目前宿主的版本保留与排序不是严格 SemVer 解析，建议使用 `1.2.3` 格式。`hostApi` 只接受 `>=x.y.z <x.y.z`；新宿主在安装、启用和切换版本时检查，不兼容则拒绝。缺省 `hostApi` 为旧包兼容而允许，但新插件必须显式声明。`kind` 可写 `ui`、`system`、`data_source`、`subtitle`。后两者只在 API 1.1 新 APK 中有宿主入口，0.03 不能运行其媒体功能。

`surfaces` 目前实际挂载 `home`、`settings`，同一完整页面只选一个已启用插件，优先级为官方、已验证、未验证，同级按 ID 排序。`player` 字段虽然能声明，完整播放器页面目前没有挂载点，不要使用。`slots` 可写 `home.quickActions`、`player.overlay`；多个已启用插件按 ID 排列，插件收到的 `surface` 为 `slot:<名称>`，例如 `slot:home.quickActions`。插件不能覆盖原生安全、授权和账号页面。`services` 的名称为 1–100 位字母、数字、点、连字符或下划线，版本为正整数；启用时自动注册，停用或卸载后自动撤销。

包的 Ed25519 签名**不放在 ZIP 里**：官方库版本 JSON 的每个资产条目存公钥对应的签名、SHA-256、大小和固定下载 URL。签名覆盖完整 ZIP 字节；本地导入无仓库验证，显示“未验证”。

## 入口、方法与输出

入口脚本必须同步定义 `globalThis.ABUPlugin`。每次调用都会重新执行整个入口；方法返回可 JSON 序列化对象，不可返回 Promise。基本调用：

| 方法 | 输入 | 用途 |
| --- | --- | --- |
| `render` | `{surface, route}` | 首次显示、页面入栈/返回后重绘；`route` 初始为 `root` |
| `onAction` | `{surface, route, action, values}` | 按钮动作；`values` 是当前输入/开关值的字符串映射 |
| `onCapabilities` | `{results:[{id,ok,value?,error?}]}` | 宿主能力结果；按 `id` 区分请求 |
| `onEvent` | `{sourcePluginId,topic,payload}` | 其他插件发布的事件；可省略，省略时无动作 |
| `onService` | `{callerPluginId,service,method,arguments}` | 提供服务时处理调用；通过输出的 `value` 返回 JSON 对象 |
| `onDataSource` | `{operation:"list",query,cursor}` | `data_source` 插件返回一页媒体项目，见下文 |
| `onSubtitle` | `{title,durationMs,seasonNumber,episodeNumber}` | `subtitle` 插件返回字幕 HTTPS URL，见下文 |

输出可同时包含 `ui`、`capabilities`、`navigation`、`value`：

```json
{
  "ui": {"type":"button","text":"设置","action":"open"},
  "navigation": {"push":"settings"},
  "capabilities": [{"id":"read","capability":"storage.get","arguments":{"key":"name"}}]
}
```

能力调用后，`onCapabilities` 收到 `{results:[{"id":"read","ok":true,"value":{"value":"Alice"}}]}`。失败时 `ok:false` 且有 `error`；不要直接假定 `value` 存在。导航 `push` 只接受相对路由名（最长 100 位，不含 `..`），`params` 可携带 JSON 对象，随后在 `render`/`onAction` 输入中收到；`pop:true` 返回上一页。页面栈最多 16 层，系统返回键也会弹出上一页。`render` 输出的导航会被忽略，以免重绘造成循环。`onAction` 的 `values` 只保存在当前页面宿主状态，不是持久化数据。

## UI 节点

| 类型 | 字段 | 行为 |
| --- | --- | --- |
| `column`、`row`、`card` | `children` | 宿主排列子节点 |
| `list` | `children` | 可滚动列表；子节点按索引识别 |
| `text` | `text`, `tone?` | 文本，`tone` 为 `default/muted/accent/danger` |
| `button` | `text`, `action` | 遥控确认键或触控调用 `onAction` |
| `input` | `id`, `text?`, `hint?`, `value?` | 单行输入，变化在下次 `onAction.values[id]` 中 |
| `toggle` | `id`, `text?`, `value?` | 开关，`values[id]` 为字符串 `"true"/"false"` |
| `image` | `asset`, `text?` | 只读取本插件 `assets/` 内图片；文件 ≤4 MiB，尺寸 ≤2048×2048 |
| `progress` | `progress` | 0–1 进度值，超界被截断 |
| `spacer` | — | 12 dp 空白 |

树深度 ≤12，节点数 ≤300。宿主负责绘制与触控/焦点行为；返回上一页时会恢复上次焦点路径。插件不能注入任意 Compose 代码或修改宿主安全页面。列表分页需由插件用按钮和游标实现，不存在自动分页控件。

## 能力请求

每项请求写成 `{id, capability, arguments}`。宿主对需要权限的能力先检查清单声明，再显示用户授权；拒绝会作为失败结果返回。授权状态按插件保存。`usb`/`bluetooth` 还会受 Android 系统权限和硬件支持限制。

| 能力 | 参数 | 成功值 / 失败要点 |
| --- | --- | --- |
| `device.info` | `{}` | `{sdk,model}`；无权限 |
| `storage.get` | `{key}` | `{value:string\|null}`；需 `storage` |
| `storage.set` | `{key,value}` | `{ok:true}`；需 `storage`，以字符串保存 |
| `network.fetch` | `{url}` | `{status,body}`；需 `network`，只允许 HTTPS 且主机在 `networkDomains`；10 秒超时、响应 ≤512 KiB，不自动跟随重定向 |
| `events.publish` | `{topic,payload}` | `{published:boolean}`；事件是即时广播，不保证离线投递 |
| `services.resolve` | `{owner,name,minimumVersion}` | `{found,owner?,name?,version?}`；提供方须启用且版本满足要求 |
| `services.register` | `{name,version}` | `{ok:true}`；仅能注册清单声明的服务；API 1.1 启用时已自动注册，通常无需调用 |
| `services.call` | `{owner,name,minimumVersion,method,arguments}` | 提供方 `onService` 的 `value`；提供方未启用、版本不符、超时或异常均以失败结果返回；不允许自调用和嵌套服务调用 |
| `usb.list` | `{}` | `{supported,devices:[{id,vendorId,productId,...}]}`；需 `usb`，只读枚举，不打开设备 |
| `bluetooth.list` | `{}` | `{supported,enabled,devices:[{name,address,type,...}]}`；需 `bluetooth`，Android 12+ 还需系统 `BLUETOOTH_CONNECT`；仅已配对设备，不主动扫描 |

服务名以提供方插件 ID 命名空间隔离。调用方 ID 由宿主填入 `onService`，提供方应按需检查并拒绝不信任的调用方。`arguments`、结果均应只含 JSON；不要在服务参数里传账号凭据。`onService` 可以发起普通宿主能力请求，但不得再次调用服务，以阻止循环依赖。服务不是后台常驻进程，每次调用仍会重新运行脚本。

## 媒体扩展与设备事件

`data_source` 插件在媒体库的“插件媒体源”中由用户选择。宿主调用 `onDataSource({operation:"list",query:"",cursor:null})`；插件返回 `value:{items:[{id,title,streamUrl}],nextCursor}`。单页最多 100 项，ID/标题最多 200 字，游标最多 200 字。`streamUrl` 必须是清单中 `networkDomains` 精确声明主机的 HTTPS URL，且插件声明 `network` 权限；否则播放被拒绝。网络抓取仍通过 `network.fetch` 两阶段回调。数据源不会收到 Emby Token。当前不支持把数据源接入 Emby 账号或后台同步，播放进度也不回传插件。

`subtitle` 插件在打开视频时收到标题、时长、季/集编号；不收到账号 ID、Token、原始播放地址或请求头。返回 `value:{subtitles:[{url:"https://sub.example.com/a.vtt"}]}`，字符串 URL 也可。宿主最多查询 3 个已启用字幕插件、每个最多接收 10 条，只接受声明域名的 HTTPS `.srt`/`.vtt`，随后交给 Media3 或 mpv 播放。网络字幕端点可能获取观看者 IP；请在权限说明中告知用户。宿主目前不提供媒体指纹或文件名匹配 API。

USB 插拔广播与蓝牙 ACL 连接/断开广播会以 `sourcePluginId:"host"`、`topic:"device.usb.attached"` 等送到**正在显示的插件界面**的 `onEvent`；不是后台任务或可靠队列。设备清单需主动调用 `usb.list`/`bluetooth.list` 刷新。目前没有 USB 打开/传输、蓝牙扫描/配对/连接 API。用户拒绝插件授权或 Android 权限时会收到失败结果，插件应显示降级界面。

## 发布元数据

`node tools/publish.mjs --file dist/com.example.demo-1.0.0.abu-plugin --key .keys/publisher.pem --url https://github.com/YOU/REPO/releases/download/v1.0.0/com.example.demo-1.0.0.abu-plugin` 输出 `publicKey` 和 `asset`（`abi`、`url`、`size`、`sha256`、`signature`）。若私钥文件不存在会创建；妥善备份，不要提交。把 `asset` 放入官方库 `versions/<版本>.json` 的 `assets` 数组，`publicKey` 放入插件目录的 `plugin.json`。目前此命令只生成元数据，不会自动发布 GitHub Release 或开 PR。
