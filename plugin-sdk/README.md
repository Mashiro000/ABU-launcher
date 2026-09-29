# ABU Plugin SDK 开发指南

ABU 插件不是独立 APK。第三方插件以 `.abu-plugin` ZIP 包安装，JavaScript 在隔离进程的 QuickJS 沙箱中运行，通过声明式 UI 和受控能力与主程序交互。

> 当前 SDK API：v1；最低宿主版本：ABU Launcher 0.03。

## 五分钟创建第一个插件

需要 Node.js 20+。

```bash
git clone https://github.com/Mashiro000/ABU-launcher.git
cd ABU-launcher/plugin-sdk
npm install
npm run build:example
```

生成文件：`plugin-sdk/dist/com.example.hello-1.0.0.abu-plugin`。

将它复制到 Android 设备，然后在 ABU Launcher 中打开“设置 → 插件 → 从本地导入插件”。本地导入插件会显示为“未验证”，确认权限并手动启用后即可运行。

开发自己的插件时，复制 `examples/hello`，然后修改 `manifest.json`、`src/index.ts` 和 `tsconfig.json`。

## 插件包结构

`.abu-plugin` 实际上是 ZIP 文件：

```text
manifest.json
dist/index.js
assets/                 # 可选，静态资源
```

第三方脚本插件不能携带 `.dex`、APK 或原生动态库。`player`/native 插件具有主程序级执行风险，目前只接受 ABU 维护者发布。

## manifest.json

```json
{
  "schemaVersion": 1,
  "id": "com.example.hello",
  "name": "Hello ABU",
  "version": "1.0.0",
  "author": "你的名字",
  "description": "一个插件示例",
  "kind": "ui",
  "entry": "dist/index.js",
  "hostApi": ">=1.0.0 <2.0.0",
  "surfaces": ["home"],
  "slots": ["home.quickActions"],
  "permissions": [],
  "networkDomains": []
}
```

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `schemaVersion` | 是 | 当前固定为 `1` |
| `id` | 是 | 全局唯一 ID，建议使用反向域名；发布后不可改 |
| `name` / `version` / `author` | 是 | 展示名称、版本和作者 |
| `kind` | 是 | `ui`、`data_source`、`subtitle`、`system`；`player` 仅限官方 native 插件 |
| `entry` | 是 | JavaScript 入口，必须位于包内且最大 2 MiB |
| `surfaces` | 否 | 可申请完整页面：`home`、`settings`、`player` |
| `slots` | 否 | 可插入区域，如 `home.quickActions`、`player.overlay` |
| `permissions` | 否 | 插件需要的宿主权限 |
| `networkDomains` | 否 | `network.fetch` 可以访问的 HTTPS 域名白名单，不带协议或路径 |

## JavaScript 生命周期

入口必须设置全局 `ABUPlugin`：

```ts
globalThis.ABUPlugin = {
  render({ surface }) {
    return {
      ui: {
        type: "column",
        children: [
          { type: "text", text: `当前页面：${surface}`, tone: "accent" },
          { type: "button", text: "读取设备信息", action: "read-device" }
        ]
      }
    };
  },
  onAction({ surface, action }) {
    if (action === "read-device") {
      return { capabilities: [{ id: "device", capability: "device.info", arguments: {} }] };
    }
    return this.render({ surface });
  },
  onCapabilities({ results }) {
    const result = results[0];
    return {
      ui: {
        type: "text",
        text: result?.ok ? JSON.stringify(result.value) : `失败：${result?.error}`
      }
    };
  }
};
```

- `render({ surface })`：进入插件页面或插槽时调用。
- `onAction({ surface, action })`：用户点击插件按钮时调用。
- `onCapabilities({ results })`：宿主能力执行完毕后回传结果。
- `onEvent({ sourcePluginId, topic, payload })`：接收插件事件。

单次调用限时 2 秒；输出 JSON 最大 1 MiB；声明式 UI 最多 12 层、300 个节点。

## UI 组件

支持 `column`、`row`、`card`、`text`、`button`、`spacer`。组件由宿主 Compose 渲染，因此自动获得电视遥控焦点、触控、主题和安全限制。

```ts
const page = {
  type: "card",
  children: [
    { type: "text", text: "标题", tone: "accent" },
    { type: "text", text: "说明", tone: "muted" },
    { type: "button", text: "执行", action: "run", tone: "default" }
  ]
};
```

`tone` 可用值：`default`、`muted`、`accent`、`danger`。

## 宿主能力与权限

| capability | 清单权限 | 状态 |
| --- | --- | --- |
| `device.info` | 无 | 可用 |
| `storage.get` / `storage.set` | `storage` | 可用，数据按插件隔离 |
| `network.fetch` | `network` | 可用，仅 HTTPS、仅白名单域名、响应最大 512 KiB |
| `events.publish` | 无 | 可用 |
| `services.register` / `services.resolve` | 无 | 可用，用于插件联动 |
| `usb.list` | `usb` | 协议已保留，0.03 暂未开放 |
| `bluetooth.list` | `bluetooth` | 协议已保留，0.03 暂未开放 |

权限声明示例：

```json
{
  "permissions": [
    { "id": "storage", "title": "保存插件设置", "sensitive": false },
    { "id": "network", "title": "访问示例服务", "sensitive": true }
  ],
  "networkDomains": ["api.example.com"]
}
```

不要请求与功能无关的权限。插件不能读取 Emby Token、密码、Android `Context` 或其他插件的私有数据。

## 调试与打包

```bash
npm run build:example
node tools/pack.mjs path/to/your-plugin
```

发布前至少检查：

1. 本地导入、启用、禁用和卸载均正常。
2. 遥控器方向键、确认键、返回键和触控均可操作。
3. 拒绝每项权限后插件仍能明确提示，不崩溃。
4. 网络域名全部列入 `networkDomains`，没有收集无关数据。
5. `id` 不再变化，版本号高于已发布版本。

## 发布到官方插件库

完整流程见 [ABU 官方插件库投稿指南](https://github.com/Mashiro000/ABU-plugins/blob/main/CONTRIBUTING.md)：

1. 用自己的 Ed25519 私钥签名 `.abu-plugin`，私钥永久保留在自己手中，绝不能提交。
2. 在自己的公开 GitHub 仓库创建 Release，并上传插件包与对应源码。
3. Fork `Mashiro000/ABU-plugins`，为插件建立 `plugins/<插件ID>/` 文件夹，并把每个版本分别放进 `versions/`。
4. 运行 `npm run check:index`，然后提交 Pull Request。
5. 维护者审核源码、权限、签名、公钥连续性和安装测试；通过后合并，应用会自动读取新索引。

官方库收录不是代码所有权转让。作者继续维护源码、发布包和签名密钥；官方库负责审核与分发索引。

## 兼容性原则

- 不依赖未写入本指南的内部 Kotlin 类。
- 对未知 `surface`、事件或能力失败提供降级逻辑。
- 新功能先通过能力检测，不假定所有用户都已升级宿主。
- 插件超时后宿主可能重建运行时，因此不要只把重要状态存在 JS 内存中。

类型定义见 [`types/abu-plugin.d.ts`](types/abu-plugin.d.ts)，完整架构和安全模型见 [`../docs/PLUGIN_ARCHITECTURE.md`](../docs/PLUGIN_ARCHITECTURE.md)。
