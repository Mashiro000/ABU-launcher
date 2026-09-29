# 公共媒体数据源示例

最低版本：包含宿主 API 1.1 的 ABU APK（0.03 不支持）。源码在 `src/index.ts`；在 `plugin-sdk` 根目录运行 `npm install && npm run build:examples`，生成 `dist/com.example.media-source-1.0.0.abu-plugin`。在「设置 → 插件」本地导入并启用，允许 `network` 权限。

进入媒体库 → 插件媒体源 → Google sample media。插件从 Google 公共测试目录取 JSON，按标题搜索并以 8 项一页分页；点击条目把白名单 HTTPS 视频地址交给宿主播放器。断网或拒绝授权应显示错误，不能无限等待。示例数据不是影视库，也不包含 Emby 凭据。改造为自己的服务时，只改 `networkDomains` 和获取/解析逻辑，保持 `onDataSource` 返回协议。公开来源参见 [Google Cast 官方示例](https://developers.google.com/cast/codelabs/cast-receiver)。
