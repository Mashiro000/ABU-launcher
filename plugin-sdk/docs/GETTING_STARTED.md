# 从空项目到第一次运行

1. 安装 Node.js 20+ 和 Android 上支持宿主 API 1.1 的 ABU Launcher。Windows 可在 PowerShell、macOS/Linux 可在终端执行 README 中的创建命令。所有命令使用 `npm`；用 `pnpm` 时把 `npm` 替换即可。
2. 在 SDK 目录运行 `npm run create:plugin -- --id com.example.demo --name Demo --out projects/demo`。ID 建议采用反向域名，首次发布后不要修改。
3. 进入 `projects/demo`，运行 `npm install`、`npm run build`、`npm run validate`。生成的 ZIP 在当前项目 `dist/`。打包器只收录 `manifest.json`、`dist/` 和可选的 `assets/`，不会包含源码、私钥或 `node_modules`。
4. 把包传到设备，在「设置 → 插件 → 从本地导入插件」安装，核对显示的作者、权限、未验证风险提示，然后启用。示例声明 `home` 页面；若另一个插件也接管同一完整页面，宿主按信任级别选择一个所有者，可在插件设置里调整启停。
5. 修改 `src/index.ts` 的按钮文本或 `onAction`，提高 `manifest.json` 的版本，重新运行构建、校验、导入。相同版本不能重复安装；需要恢复旧版本时在插件详情中切换保留的版本。

## 文件结构

```text
manifest.json          插件身份、兼容范围、权限和扩展点
src/index.ts           TypeScript 源码
types/abu-plugin.d.ts  与创建时 SDK 一起复制的公开类型
tools/                 打包、校验、签名命令
dist/index.js          TypeScript 编译结果
dist/*.abu-plugin      安装包
assets/                可选图片等静态资源
```

`.gitignore` 默认忽略 `dist/`、`node_modules/` 和 `.keys/`。发布时提交源码与清单；上传构建产物到固定标签的 Release。不要提交私钥。

## 运行模型

每次 `render`、`onAction`、`onCapabilities`、`onEvent` 或 `onService` 调用，隔离进程重新执行入口脚本。**不要用 JS 全局变量保存重要状态**；用 `storage.get/set` 存储插件数据。能力调用是两阶段同步协议：方法返回 `capabilities`，宿主执行后再调用 `onCapabilities({results})`。插件方法不能返回 Promise。单次脚本调用限时 2 秒，最大输出约 1 MiB，能力往返最多 4 轮。

首次体验建议按 [多页面示例](EXAMPLES.md)做一次：输入名称 → 保存 → 返回 → 重新进入，确认值仍在。遇到错误先看 [故障排查](TROUBLESHOOTING.md)。
