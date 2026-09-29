# 插槽扩展示例

最低版本：包含宿主 API 1.1 的 ABU APK（0.03 不支持此示例）。无权限。源码在 `src/index.ts`；在 `plugin-sdk` 根目录运行 `npm install && npm run build:examples`，生成 `dist/com.example.slots-1.0.0.abu-plugin`。通过「设置 → 插件 → 从本地导入插件」安装并启用。

预期：首页 `home.quickActions` 显示可遥控器确认/触摸的按钮；点击后显示成功文字。打开任意可播放视频，`player.overlay` 显示叠层文字。两处都是受限声明式 UI，不可修改宿主其他界面。停用插件后两处同时消失。
