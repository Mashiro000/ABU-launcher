# 复杂插件的实现模式

## 页面与状态

把 `route` 当成页面名，不要把 JavaScript 全局变量当导航状态。`render({route,params})` 根据 `root/settings/detail` 返回对应 UI；`onAction` 可返回 `{navigation:{push:"detail",params:{id:"42"}}}`，宿主重新调用 `render`。返回按钮可返回 `{navigation:{pop:true}}`，Android 返回键也由宿主处理，回退时恢复焦点。页面栈最多 16 层；参数只传短 ID，不要放凭据。

输入框与开关的当前值在 `onAction.values`；对长期设置先调用 `storage.set`，并在下次 `render` 通过 `storage.get` 读取。每次入口脚本重建，因此模块级 `let settings = ...` 不可靠。参考 [`multipage`](../examples/multipage/src/index.ts)。

## 能力调用与降级

一轮方法返回 `capabilities`，宿主再以请求 ID 原样回传 `onCapabilities.results`。按 ID 分支，并先检查 `ok`。授权拒绝、离线、HTTP 错误、超时和服务停用都应在 UI 中显示简短原因与重试按钮。`network.fetch` 返回 HTTP 状态不代表业务成功，先检查 `status`，再解析 `body`。宿主目前没有跨调用取消令牌；请求超时 10 秒，界面关闭后也无法由插件主动取消。大列表应在自己的 API 上分页，每次只请求、绘制一页；宿主未提供自动分页控件。

## 服务依赖

提供方在清单的 `services` 中声明名称与正整数版本；宿主在启用时自动注册，停用时撤销。调用方在请求里写明确的 `owner` 和 `minimumVersion`，结果失败时显示“需启用或更新提供方”。提供方收到由宿主给出的 `callerPluginId`，可自行做调用方白名单；宿主目前没有独立的服务调用授权对话框。不要把服务设计成递归 RPC；嵌套调用会失败。参考 [`service-provider`](../examples/service-provider/src/index.ts) 与 [`service-consumer`](../examples/service-consumer/src/index.ts)。

## 更新与迁移

保持 `id` 与签名公钥不变，提升版本号。安装器保留当前版和最多两个旧版；切换版本不会自动迁移 `storage`。若要更改存储结构，在新版本首次读取时判断旧键并写入新键，同时让旧版仍可读取必要数据。签名私钥丢失或更换须在官方库申请人工审核，不能悄悄用新公钥覆盖旧身份。

## 安全边界

插件不能直接改宿主任意页面、调用任意 Android API 或取得媒体账号凭据。`home`/`settings` 的完整页面接管与两个插槽是目前明确的 UI 扩展点。API 1.1 的媒体数据源/字幕入口和只读 USB/蓝牙枚举有受限宿主实现，但完整播放器页面、设备通信、主动蓝牙扫描与后台常驻任务仍未开放；需要这些功能时先在宿主仓库提出接口需求，不要在脚本里依赖私有 Kotlin 类。
