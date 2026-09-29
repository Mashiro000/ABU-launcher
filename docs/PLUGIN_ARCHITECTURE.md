# ABU 插件架构（v1）

## 目标

- 主 APK 使用 Media3 与系统 MediaCodec；MPV 以按需插件提供。
- 插件在应用内安装，不要求安装独立 APK。
- 普通插件使用 TypeScript/JavaScript 沙箱，通过宿主能力桥访问系统服务。
- 插件可以提供完整页面宿主；同一页面同时只能有一个宿主，其他插件通过服务、事件和插槽协作。

## 插件类型

| kind | 用途 | 执行方式 |
| --- | --- | --- |
| `player` | MPV 等播放器内核 | 受信任 native/Dex host |
| `ui` | 页面、掌机模式、组件 | JavaScript 沙箱 + 原生声明式 UI |
| `data_source` | 媒体或频道来源 | JavaScript 沙箱 + 网络能力桥 |
| `subtitle` | 字幕搜索、转换 | JavaScript 沙箱 |
| `system` | 按键映射、设备能力组合 | JavaScript 沙箱 + 系统能力桥 |

## 包格式

扩展名使用 `.abu-plugin`，内容是 ZIP：

```text
manifest.json
signature.ed25519
dist/index.js                 # 脚本插件
lib/arm64-v8a/*.so           # native 插件（可选）
lib/armeabi-v7a/*.so
lib/x86_64/*.so
```

`manifest.json` 最小示例：

```json
{
  "schemaVersion": 1,
  "id": "com.example.handheld",
  "name": "掌机模式",
  "version": "1.0.0",
  "author": "Example",
  "kind": "ui",
  "entry": "dist/index.js",
  "hostApi": ">=1.0.0 <2.0.0",
  "permissions": [
    { "id": "input.gamepad", "title": "读取手柄按键" },
    { "id": "bluetooth.scan", "title": "扫描附近蓝牙设备", "sensitive": true }
  ],
  "networkDomains": []
}
```

## 安全规则

1. 官方插件使用 Ed25519 签名；第三方可自签名，首次安装确认发布者指纹。
2. 包下载后先校验 SHA-256，再验证清单签名。
3. 插件只能读写自己的私有目录。跨插件数据通过授权服务传递。
4. Emby Token、密码和认证请求头不暴露给插件；插件调用宿主的 Emby API。
5. 网络访问仅允许清单声明且获用户批准的 HTTPS 域名。
6. 安装时展示全部权限；敏感权限首次调用时再次确认。
7. 权限确认、安全模式、账号认证页面不能被 UI 插件覆盖。
8. 连续三次启动失败时禁用最近启用的插件并恢复默认页面。
9. 每个插件保留当前版本与最近两个历史版本。

## UI 合作模型

- 页面宿主通过 `ui.claimSurface(surfaceId)` 请求接管 `home`、`settings` 或 `player`。
- 同一 `surfaceId` 同时只有一个完整宿主。
- 其他插件通过具名 slot 插入组件，例如 `home.quickActions`。
- 插件通过事件总线和带版本的服务契约联动，不直接修改其他插件的节点树。
- 宿主使用原生组件协议描述 UI，Compose 负责焦点、主题、触摸和遥控器渲染。

## MPV 策略

- 官方插件包内附第三方许可与对应源码说明，并按上游许可要求发布。
- 发布 `arm64-v8a`、`armeabi-v7a`、`x86_64` 三个包，客户端只下载当前 ABI。
- Media3 是默认内核。播放健康评分综合首帧、帧输出、缓存、轨道、时间线、错误类型和历史成功内核。
- 未安装 MPV 时，切换前显示来源、版本、体积和权限，用户确认后下载并从原进度续播。
- 每个 Emby 媒体或 IPTV 频道记录最后成功的内核。

## 官方仓库

默认索引：

```text
https://github.com/Mashiro000/ABU-launcher/releases/latest/download/plugins.json
```

用户可添加第三方 HTTPS 索引。索引提供插件版本、ABI 资产 URL、大小、SHA-256、签名与权限摘要。

## 实施阶段

1. 插件模型、隔离存储、版本保留、设置入口。
2. 仓库同步、本地导入、下载、Ed25519 校验与权限数据库。
3. 将现有 `libmpv` 从主 APK 移至 player 插件，并实现 native/Dex host。
4. JavaScript 沙箱、能力桥和声明式 Compose 渲染器。
5. 页面宿主、插槽、事件总线、崩溃恢复和安全模式。

## JavaScript 运行时契约（已实现）

- 第三方脚本在 `android:isolatedProcess` 服务中由 QuickJS 执行，隔离进程没有宿主 `Context`、账号或文件路径。
- 全局导出 `ABUPlugin`；页面首次加载调用 `render({ surface })`，点击声明式按钮调用 `onAction({ surface, action })`。
- 每次调用限时 2 秒，脚本入口最大 2 MiB，返回 JSON 最大 1 MiB；超时会断开并重建隔离进程。
- UI 仅接受 `column`、`row`、`card`、`text`、`button`、`spacer`，最多 12 层、300 个节点。
- 完整页面用 `surfaces: ["home"]` 声明；同一页面按官方、已验证、未验证的顺序确定唯一宿主。
- 协作扩展用 `slots` 声明，多个插件可同时注册；插件之间不共享对象或执行环境。
- 宿主能力当前包括 `device.info`、插件隔离键值存储及 HTTPS 白名单请求。USB/蓝牙能力已保留协议，但在宿主实现前会明确拒绝。
- 启动后前 15 秒长按返回进入安全模式；连续三次未能稳定启动会停用最近启用的插件。
