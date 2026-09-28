# 阿布桌面播放器完整整改提示词

你正在维护 Android TV 项目“阿布桌面”，工程位于 `Android/`，使用 Kotlin、Jetpack Compose、AndroidX Media3 ExoPlayer 1.4.1。请直接修改代码并在 MuMu 模拟器验证，不要只给建议或伪代码。保留现有媒体库、播放地址、播放进度上报、错误提示、画中画和低性能模式，不得用演示数据代替真实媒体数据。

## 当前已确认的问题

1. `SettingsPage.kt` 的播放器设置只有四行静态文案：“硬件加速解码、字幕默认样式、音轨优先、默认播放倍速”，点击没有实际设置页面，也没有持久化或作用到 ExoPlayer。
2. `VideoPlayerView.kt` 的音轨和字幕抽屉只更新 Compose 列表的 `isSelected`，没有通过 Media3 的 `TrackSelectionParameters` / `TrackSelectionOverride` 切换真实轨道。
3. 没有字幕轨道时，代码伪造中文、英文字幕和多条音轨。必须删除所有假轨道；无轨道时显示明确空状态。字幕列表无论是否存在轨道，都应提供“关闭字幕”。
4. `tv_player_view.xml` 只创建默认 `PlayerView`，没有配置 `SubtitleView`。当前字幕黑框来自 Media3 默认字幕样式，不是设置页中的“白色无阴影”；那条设置目前是假的。
5. 当前没有外置字幕 URL/格式/轨道索引的数据链路。`MediaStreamInfo` 丢失字幕的 index、是否外置、默认/强制标记、DeliveryUrl 等信息；`MediaItem.fromUri()` 也没有添加 `SubtitleConfiguration`。
6. Media3 默认字幕渲染不能完整保证复杂 ASS/SSA 特效。需要明确区分普通字幕和特效字幕，不能宣称支持但实际丢失定位、字体、描边、移动或卡拉 OK 效果。
7. 控制条隐藏时，Activity 级 `PlayerKeyBridge` 能收到 OK；控制条打开后却完全依赖 Compose 自动空间寻焦。控制条包含三个嵌套 Row、会改变高度的 tooltip、时间轴和全屏焦点层，没有显式焦点图，导致键盘/遥控器左右键无法稳定选择按钮。
8. 所有 `TvGlassCard` 固定添加 `20.dp` 阴影，因此底部播放控制条存在我不需要的阴影。
9. “更多设置”按钮的 `onClick = {}`，属于空功能。
10. 播放器每 250ms 更新 `currentPosition`，会让过大的 `VideoPlayerScreen` 组合范围持续重组。低性能模式仍使用 `TextureView`，不利于低配电视的视频合成性能。
11. 真实媒体的弹幕列表始终为空；如果没有真实弹幕数据源，应明确显示“当前媒体无弹幕”，不能保留看似可用但没有数据链路的入口。

## 目标体验

播放器应是完整的 Android TV 遥控器优先播放器。按 OK/Enter 必须可靠打开控制条；控制条出现后焦点默认落到播放/暂停按钮；左右键逐个移动焦点，上键进入时间轴，下键回到主按钮行；焦点不能丢到视频 `AndroidView` 或不可见控件。返回键按层级关闭抽屉、关闭控制条、最后退出播放器。

底部控制条保持现有圆角半透明风格，但彻底去掉外部投影。不要用大面积动态阴影。低性能模式关闭视频背景采样和模糊，并优先使用 `SurfaceView`；标准模式只有确实需要视频采样时才允许 `TextureView`。

默认字幕不要黑色矩形底框。默认采用“白字 + 适度黑色描边 + 透明背景”，适合电视远距离观看。字幕设置必须真正生效并持久化。

## 必须完成的实现

### 1. 字幕渲染和特效字幕

建立 `PlayerPreferences`（SharedPreferences 或 DataStore）并提供可观察状态，至少包含：

- 字幕渲染模式：`原始特效`、`清爽描边`、`系统样式`。
- 字号：80%–160%。
- 垂直位置/底部边距。
- 前景色、描边色、描边粗细。
- 背景：默认透明，可选半透明背景；禁止默认黑框。
- 是否应用内嵌字幕样式和内嵌字号。
- 首选字幕语言、首选音轨语言。
- 默认播放倍速。
- 硬件解码优先/允许软件回退。

普通 SRT/WebVTT/TTML 字幕继续使用 Media3 `SubtitleView`。在 `PlayerView` 创建后取得 `subtitleView`，按设置调用 `setStyle`、`setFractionalTextSize`、`setBottomPaddingFraction`、`setApplyEmbeddedStyles` 和 `setApplyEmbeddedFontSizes`。清爽描边模式使用透明 background/window、黑色 outline edge，不能出现逐行黑色矩形。

ASS/SSA 的“原始特效”模式必须提供真实能力：优先集成 libass 渲染层，保留字体、字号、颜色、描边、阴影、绝对定位、对齐、旋转/移动、卡拉 OK 与多行排版；若某项在当前实现中无法支持，UI 必须明确显示“基础兼容”而不是伪装为完整特效。libass 渲染应在独立 overlay 中完成，只在字幕画面变化时更新，不能每帧创建 Bitmap 或对象。支持媒体内嵌字体附件以及常用系统字体回退，处理 UTF-8/UTF-16 编码。

“原始特效”尊重 ASS 样式；“清爽描边”覆盖样式用于普通阅读；两者要能即时切换。普通字幕和 ASS 字幕都应支持开关、切轨、延迟调整（至少 ±10 秒，100ms 步进）与恢复为 0。

### 2. 真实字幕数据链路

扩展 `MediaStreamInfo`，保存服务端轨道 index/id、codec、language、displayTitle、isDefault、isForced、isExternal、isTextSubtitleStream、deliveryUrl。Emby 优先使用服务端返回的 `DeliveryUrl`；没有时按 Emby API 规则构造字幕流地址。Plex/WebDAV 能提供外置字幕时也映射到统一模型。

构建 `TvPlaybackInfo` 时把真实音轨/字幕元数据和外置字幕配置传入播放器。创建 `MediaItem` 时，把外置字幕通过 `MediaItem.SubtitleConfiguration` 添加，正确填写 URI、MIME、语言、label、selectionFlags 和 roleFlags。不要伪造轨道。

在 `onTracksChanged` 中保存真实 `Tracks.Group`/`MediaTrackGroup` 与轨道 index 的可选择引用。音轨选择使用 `TrackSelectionOverride`；关闭字幕使用 `setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)`；开启具体字幕时重新启用文本轨并设置 override。切换后以新的 `onTracksChanged` 状态刷新 UI，不能先假设成功。

### 3. 播放控制条与遥控器焦点

不要依赖 Compose 的自动空间寻焦。为以下节点创建稳定的 `FocusRequester` 并显式配置 `focusProperties`：

`更多 → 媒体信息 → 播放列表 → 快退10秒 → 上一集 → 播放/暂停 → 下一集 → 快进10秒 → 弹幕 → 音轨字幕 → 画中画（存在时）→ 画面比例`

左右键严格按上述顺序移动，边界不循环。所有按钮的视觉尺寸固定；tooltip 使用 overlay/zIndex 显示，不得改变焦点节点的测量尺寸。时间轴是独立焦点节点：按钮行按上进入时间轴，时间轴按下回到播放/暂停，时间轴左右每次 seek 10 秒。打开抽屉后焦点移动到当前选中项或第一项；左右键按产品逻辑关闭/返回控制条，上下键浏览列表；关闭抽屉后焦点回到触发按钮。

Activity 级按键处理必须覆盖 `KEYCODE_DPAD_CENTER`、`ENTER`、`NUMPAD_ENTER` 和键盘 Space：

- 控制条隐藏：按下 OK/Enter/Space 只打开控制条并消费 down/up，不同时触发暂停。
- 控制条显示：OK 执行当前焦点按钮。
- 控制条隐藏：左右短按 seek 5 秒，长按进入连续快进/快退；必须正确处理 ACTION_UP、按键重复和页面退出，避免倍速卡在 3x。
- 控制条显示：左右键只移动焦点，不能同时 seek；只有时间轴聚焦时左右 seek。
- 任意一次有效操作都重置自动隐藏计时。抽屉打开、暂停、焦点正在移动时不要自动隐藏。
- Back：先关抽屉，再隐藏控制条，再退出播放器。

修复全屏输入层与 `AndroidView` 抢焦点问题。视频 `PlayerView` 不可获得遥控器焦点；Compose 根焦点只在控制条隐藏时接管。用日志记录每次按键、当前控制层状态和焦点目标，便于 MuMu 验证；release 构建关闭高频日志。

### 4. 控制条视觉调整

给 `TvGlassCard` 增加独立参数，例如 `shadowElevation` 或 `showShadow`。底部播放控制条传 `0.dp`/`false`，不得产生 ambient/spot shadow。不要移除其他页面的阴影，除非它们明确传参关闭。

控制条的圆角、半透明底色和必要的细边界可以保留。低性能模式使用稳定纯色半透明背景；标准模式可用低成本模糊。控制条显示/隐藏只做轻量 alpha/translation 动画，不做大面积缩放或动态阴影。

### 5. 完成播放器设置页

把 `SettingsCategory.PLAYER` 四个静态占位项改成真正可进入、可遥控操作的设置页面。至少实现：

- 解码策略。
- 字幕样式预览和上述全部字幕设置。
- 首选音轨/字幕语言。
- 默认倍速（0.5x–2.0x 常用档位）。
- 快进快退步长。
- 控制条自动隐藏时间。
- 是否自动播放下一集。

设置修改后新播放器实例必须生效；适合即时生效的字幕样式在当前播放中立即更新。提供“恢复默认”。不要展示尚未实现的选项。

### 6. 清理其余未完成功能

- 实现“更多设置”，至少包含播放速度、字幕延迟、画面比例和解码/播放信息；若已有独立入口则合理整合，不能保留空点击。
- 音轨、字幕、倍速、比例切换后显示短暂、无黑框的状态提示。
- 切换剧集时更新标题、集数、时长、续播位置、音轨和字幕列表；清理旧轨道 override，并在失败时保留可理解的错误状态。
- 真实弹幕无数据源时隐藏入口或显示明确空状态；不要注入 demo 弹幕。
- 完善 PiP 生命周期：进入 PiP 隐藏控制 UI，退出后恢复合理焦点；PiP 中不要显示抽屉和弹幕设置。
- 250ms 播放位置更新只影响时间轴、弹幕和必要文本，拆小组合范围，避免整个播放器 UI 持续重组。低性能模式优先 `SurfaceView`、关闭 Haze source/effect 和非必要动画。

## 验收标准

在 MuMu 模拟器至少执行以下测试，并保存截图、关键日志和结果到 `Android/qa/VERIFICATION.md`：

1. 键盘 Enter、Numpad Enter、Space 和模拟遥控器 OK 在控制条隐藏时均能打开控制条；第一次按键不误触暂停。
2. 控制条显示后，从播放/暂停开始连续按左、右，焦点按固定顺序遍历全部可见按钮；跨三个按钮组不丢焦点。上键进入时间轴，时间轴左右 seek，下键回主按钮。
3. 控制条隐藏时左右短按各 seek 5 秒；长按进入连续快进/快退，松手恢复 1x；退出播放器后不会残留 3x。
4. 打开播放列表、弹幕、媒体信息、音轨字幕抽屉时，焦点进入抽屉；Back 逐层退出且焦点正确恢复。
5. 底部控制条截图中无外部阴影；低性能模式下无模糊并使用低成本视频表面。
6. SRT/WebVTT 默认显示白字黑描边、透明背景，无黑色矩形；字号、位置、颜色、描边和延迟调整即时生效且重启保留。
7. 使用一份包含定位、字体、颜色、描边、移动和卡拉 OK 的 ASS 测试文件逐项验证特效；记录未支持项，不能只验证普通文本出现。
8. 至少选择两条真实音轨和两条真实字幕，确认听到/看到的内容变化；关闭字幕后画面无字幕。无轨道媒体不能出现伪造选项。
9. 外置字幕、内嵌字幕、强制字幕、默认字幕分别验证。服务端字幕加载失败时显示具体原因，不影响视频继续播放。
10. 播放器设置中的每一个可见选项都能点击、持久化并实际影响播放器。
11. 标准模式与低性能模式各连续播放并操作 10 分钟，无崩溃、无 ANR、无持续内存增长；记录平均帧耗时、P95 和冻结帧。

完成后运行：

```powershell
$env:JAVA_HOME='G:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline
```

最终回复必须列出：修改文件、真实完成的功能、仍受格式/设备限制的能力、测试结果、MuMu 截图/日志位置。不得把 UI 占位、假轨道或静态文案描述为已实现功能。
