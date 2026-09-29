# ABU Plugin SDK

1. 安装 Node.js 20+，在本目录运行 `npm install`。
2. 复制 `examples/hello`，修改 `manifest.json` 和 `src/index.ts`。
3. 运行 `npm run build:example`；插件包输出到 `plugin-sdk/dist`。
4. 在 ABU 的“设置 → 插件 → 从本地导入插件”安装，确认权限后启用。

脚本运行在无 Android 权限的隔离进程中，只能返回 SDK 定义的声明式 UI 和能力请求。网络请求必须在清单的 `networkDomains` 中声明域名，并声明 `network` 权限。
