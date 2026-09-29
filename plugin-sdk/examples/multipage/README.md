# 多页面与持久化示例

最低版本：包含宿主 API 1.1 的新 APK，0.03 不支持。权限：`storage`，用于保存显示名称。在 `plugin-sdk` 根目录执行 `npm install && npm run build:examples`，安装生成的 `dist/com.example.multipage-1.0.0.abu-plugin` 并启用。首页打开设置页，输入名称、保存、返回；再次进入仍能读取名称。遥控器和触控都应可操作；拒绝授权时应显示错误。源码在 `src/index.ts`，它演示每次脚本重新运行后用宿主存储恢复状态。
