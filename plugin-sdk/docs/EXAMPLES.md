# 参考插件与演练

在 `plugin-sdk` 目录运行 `npm install && npm run build:examples`，所有可运行示例包输出到 `dist/`。每个示例有完整清单、TypeScript 源码和 `tsconfig.json`；可用 `node tools/validate.mjs dist/<文件名>.abu-plugin` 单独校验。导入后手动启用，并确认没有其他插件抢占同一完整页面。

| 示例 | 最低宿主 | 权限 | 预期行为 |
| --- | --- | --- | --- |
| [`hello`](../examples/hello/) | 0.03 | 无 | 首页显示按钮；点击后读取设备信息 |
| [`multipage`](../examples/multipage/) | API 1.1 新 APK | `storage` | 首页 → 设置 → 输入名称 → 保存返回；再次打开仍显示名称 |
| [`service-provider`](../examples/service-provider/) | API 1.1 新 APK | 无 | 启用后声明 `library` v1 服务，返回示例搜索结果；不接管页面 |
| [`service-consumer`](../examples/service-consumer/) | API 1.1 新 APK | 无 | 输入关键字，调用提供方服务并显示列表；停用提供方后显示失败提示 |
| [`slots`](../examples/slots/) | API 1.1 新 APK | 无 | 首页快捷操作与播放器叠层，不接管完整页面 |
| [`media-source`](../examples/media-source/) | API 1.1 新 APK | `network` | 读取 Google 公共示例目录，搜索/分页，交给宿主播放；不是影视库 |
| [`subtitle`](../examples/subtitle/) | API 1.1 新 APK | `network` | 标题匹配 Elephants Dream 时返回公开 WebVTT 字幕；其他视频不配错字幕 |
| [`devices`](../examples/devices/) | API 1.1 新 APK | `usb`、`bluetooth` | 设置页只读列出 USB 与已配对蓝牙设备；无硬件时显示空列表 |

服务联动测试顺序：先安装并启用 provider、consumer；进入 consumer 所有的首页，搜索 `ABU`；应显示两项。停用 provider 再搜索，应看到“服务不可用”。重新启用后恢复。示例结果是本地静态数据，不是真实影视库。

多页面测试顺序：启用 `multipage`，允许 `storage` 权限，进入首页，打开设置，输入名称，保存；再次进入首页应看到名称。按系统返回键应回上一级。拒绝权限时应显示明确失败信息。用遥控器方向键/确认键与触控各走一遍；若某设备焦点丢失，请记录机型和 Android 版本提 Issue。

插槽示例测试：启用后首页可见“插槽示例”按钮；进入播放器可见叠层文字。播放器叠层不等于接管播放器完整页面。

媒体源示例测试：启用并允许网络权限，在媒体库打开“插件媒体源”，选择 Google sample media；检查搜索、加载更多和播放。示例使用 Google 公共测试目录，若网络不可达会显示错误；不提供 Emby 登录或凭据代理。第三方开发者可把同一结构改成自己的授权服务，但目前宿主不会代管第三方账号凭据。

设备示例测试：启用后进入设置页，按“刷新”，分别允许或拒绝插件授权、Android 蓝牙权限；检查结果或错误。插拔 USB/连接蓝牙时界面收到 `device.*` 事件，但设备无对应硬件时无法完成这部分验收。此示例不能操控掌机 USB 外设，也不能主动扫描蓝牙。
