# ABU 插件开发

ABU 的第三方脚本插件是 `.abu-plugin` ZIP 包，不是独立 APK。JavaScript 在隔离的 QuickJS 进程中执行；页面、存储、网络和插件协作都由宿主代理。插件不能直接取得 Android `Context`、Emby 凭据或其他插件的数据。

**先核对版本：** 已发布的 ABU Launcher 0.03 只支持旧协议的少量 UI 和能力。此仓库正在开发的宿主 API 1.1 才提供多页面、表单、图片、服务调用和清单兼容检查；在包含这些改动的新 APK 发布并安装前，不能把下面的新示例当成 0.03 可运行插件。[兼容表](docs/COMPATIBILITY.md)列出了差别。

## 从零创建

需要 Node.js 20+、npm（或 pnpm）和安装了新宿主的 Android 设备。

```bash
git clone https://github.com/Mashiro000/ABU-launcher.git
cd ABU-launcher/plugin-sdk
npm install
npm run create:plugin -- --id com.example.demo --name Demo --out projects/demo
cd projects/demo
npm install
npm run build
npm run validate
```

脚手架生成独立源码、清单、类型、构建和校验命令，不覆盖已有目录。`dist/com.example.demo-1.0.0.abu-plugin` 可在 ABU「设置 → 插件 → 从本地导入插件」安装；本地导入显示为「未验证」，安装后还须手动启用。修改源码后提高 `manifest.json` 与 `package.json` 的版本，再构建、重新导入。

现有示例可用 `npm run build:examples` 一次构建。`examples/hello` 兼容旧 UI；`multipage`、`service-provider` 和 `service-consumer` 要求宿主 API 1.1。[示例说明](docs/EXAMPLES.md)包含预期行为、权限和测试步骤。

## 文档导航

- [入门、生命周期和工作流程](docs/GETTING_STARTED.md)
- [清单、包格式、UI、能力与服务 API](docs/API_REFERENCE.md)
- [多页面、持久化、网络错误和依赖降级模式](docs/PATTERNS.md)
- [示例与验收步骤](docs/EXAMPLES.md)
- [兼容性和已知缺口](docs/COMPATIBILITY.md)
- [调试与故障排查](docs/TROUBLESHOOTING.md)
- [官方插件库投稿指南](https://github.com/Mashiro000/ABU-plugins/blob/main/CONTRIBUTING.md)

完整 TypeScript 契约在 [`types/abu-plugin.d.ts`](types/abu-plugin.d.ts)。如果文档与实际 API 有出入，请以宿主实现为准并提交 Issue；不要假设文档中标记“未接入”的能力可以调用。
