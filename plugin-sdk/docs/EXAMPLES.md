# 参考插件与演练

在 `plugin-sdk` 目录运行 `npm install && npm run build:examples`，所有可运行示例包输出到 `dist/`。每个示例有完整清单、TypeScript 源码和 `tsconfig.json`；可用 `node tools/validate.mjs dist/<文件名>.abu-plugin` 单独校验。导入后手动启用，并确认没有其他插件抢占同一完整页面。

| 示例 | 最低宿主 | 权限 | 预期行为 |
| --- | --- | --- | --- |
| [`hello`](../examples/hello/) | 0.03 | 无 | 首页显示按钮；点击后读取设备信息 |
| [`multipage`](../examples/multipage/) | API 1.1 新 APK | `storage` | 首页 → 设置 → 输入名称 → 保存返回；再次打开仍显示名称 |
| [`service-provider`](../examples/service-provider/) | API 1.1 新 APK | 无 | 启用后声明 `library` v1 服务，返回示例搜索结果；不接管页面 |
| [`service-consumer`](../examples/service-consumer/) | API 1.1 新 APK | 无 | 输入关键字，调用提供方服务并显示列表；停用提供方后显示失败提示 |

服务联动测试顺序：先安装并启用 provider、consumer；进入 consumer 所有的首页，搜索 `ABU`；应显示两项。停用 provider 再搜索，应看到“服务不可用”。重新启用后恢复。示例结果是本地静态数据，不是真实影视库。

多页面测试顺序：启用 `multipage`，允许 `storage` 权限，进入首页，打开设置，输入名称，保存；再次进入首页应看到名称。按系统返回键应回上一级。拒绝权限时应显示明确失败信息。用遥控器方向键/确认键与触控各走一遍；若某设备焦点丢失，请记录机型和 Android 版本提 Issue。

以下三类是计划中的参考场景，**目前不能标为可运行插件**：

- 插槽扩展：宿主已挂载 `home.quickActions` 与 `player.overlay`，但还未提供独立、完整的插槽示例与焦点验收。
- 媒体/直播来源：`data_source` 和 `subtitle` 仅是清单类型，宿主尚无注册、列表查询、凭据代理和播放交接 API。
- 设备模式：`usb.list`、`bluetooth.list` 当前被宿主拒绝，缺少权限代理、发现和断连处理；不能用来制作可靠掌机插件。

这些缺口按 [`../../docs/PLUGIN_DEVELOPER_COMPLETION_PLAN.md`](../../docs/PLUGIN_DEVELOPER_COMPLETION_PLAN.md) 实施。不要把类型名称或界面示意误认为已具备功能。
