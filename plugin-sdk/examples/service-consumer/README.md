# 服务调用方

最低版本：宿主 API 1.1。无权限。在 `plugin-sdk` 根目录执行 `npm install && npm run build:examples`，先启用 `service-provider`，再安装启用 `dist/com.example.service-consumer-1.0.0.abu-plugin`。在首页输入关键字并查询，预期显示提供方的静态结果；停用提供方或提高最低服务版本时应显示降级提示。两个插件的 ID、服务名和版本须一致。源码在 `src/index.ts`。
