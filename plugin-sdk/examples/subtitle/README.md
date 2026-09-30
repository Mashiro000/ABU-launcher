# 字幕插件示例

最低版本：包含宿主 API 1.1 的新 APK，0.03 不支持。权限：`network`，只用于播放来自 `dash.akamaized.net` 的公开 WebVTT 字幕。源码在 `src/index.ts`；在 `plugin-sdk` 根目录运行 `npm install && npm run build:examples`，导入并启用生成的 `dist/com.example.subtitle-1.0.0.abu-plugin`。

插件只在视频标题包含 `Elephants Dream` 时返回字幕，其他标题返回空列表，避免给不相关媒体误配字幕。可用该片的公开测试视频验证；宿主打开视频时会请求插件授权，并把返回的 `.vtt` 字幕交给播放器。字幕服务器会看到设备 IP，因此需要用户许可。此示例不进行通用字幕搜索，也不会取得账号 Token、媒体流地址或本地文件名。公开测试字幕 URL 已在 2026-09-30 返回 HTTP 200 / `text/vtt`；第三方站点将来可能下线。
