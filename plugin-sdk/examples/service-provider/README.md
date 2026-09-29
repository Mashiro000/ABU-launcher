# 服务提供方

最低版本：宿主 API 1.1。无权限。在 `plugin-sdk` 根目录执行 `npm install && npm run build:examples`，安装并启用 `dist/com.example.service-provider-1.0.0.abu-plugin`。它不接管页面，声明 `library` v1 服务，对 `onService` 请求返回静态示例结果；不是实际影视库。需与 `service-consumer` 一同测试。停用它后调用方应显示服务不可用。源码在 `src/index.ts`。
