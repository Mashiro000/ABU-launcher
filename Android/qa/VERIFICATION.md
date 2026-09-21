# 验证记录

退出亮度修复：详情背景的水平与垂直暗色遮罩使用同一展开进度 p 控制 alpha，退出时连续淡出，避免到页面销毁时才突然移除。背景封面交叉淡入和滚动暗化也沿用 p。修复构建及 Lint 通过，并安装至 MuMu。

本次进入与退出统一使用 620ms、cubic-bezier(0.16, 1, 0.3, 1) 的先快后慢转场。详情 PageEntranceScope 设置 replayOnOpen：每次重新打开播放入场，页面内刷新与重组不重播。播放、收藏、更多关闭 zoomOnFocus，播放保持浅色样式。构建与 Lint 通过，APK 已安装 MuMu，并执行进入—退出—重新进入核对，截图 `detail-buttons-v4.png`。海岸背景从封面交叉淡入参考图的侧脸背景。

本次动画修改：assembleDebug 与 lintDebug 成功，APK 已安装 MuMu。详情展开为 820ms 慢—快—慢，圆角连续收敛；共享入场时间轴按 20dp、380ms、35ms、cubic-bezier(0.16, 1, 0.3, 1) 实现。已进入的页面记录在 AppEntranceHost 中并可保存，组件延迟组成、返回或重组时不创建新时间轴。新版首屏 `detail-entrance-top.png` 已核对详情标签没有底框；悬浮标签代码已删除。布局采用参考图详情参数而不再强行套用媒体库的大标题间距。

最终详情剧集截图 `detail-episodes.png` 已核对首卡 20% 放大后边框、名称与说明完整可见。两列信息面板使用相同最小高度；详情模块的页面边距、模块留白、字号与卡片间距沿用媒体库设计参数。

电影 / 剧集详情页：assembleDebug 与 lintDebug 成功，已安装 MuMu。实际核对海岸线详情首屏只显示信息、按钮与标签；剧集左右键可选到第 6 集，名称和说明完整可见；返回后恢复媒体库。电影详情隐藏剧集，演员标签定位到演员模块并交接焦点。收藏 XML 确认“海岸线之外”为 true，更新 APK 后仍保留。`detail-top.png` 为首屏，`detail-horizontal.png` 为剧集横向滚动（早期边距截图），`detail-return.png` 为返回状态。下方模块随下滑渐暗，避免亮色背景影响资料可读性。

焦点滚动修复：PosterCard 使用完整条目的 BringIntoViewRequester，请求区域包括封面、两行说明、20% 缩放外扩、顶部导航安全区与底部留白。MuMu 连续下键进入最近添加后，选中“沙丘 第二部”的名称与“2024 · 电影”均完整可见，截图 `focus-complete-item.png`。本次 assembleDebug 与 lintDebug 成功，修复 APK 已安装。

本次设计一致性调整：统一媒体模块页面边距、货架间距、标题间距与文字层级；海报整个条目缩放并在 Row 兄弟层置顶，避免封面盖住自己的说明。`design-spacing.png`、`design-focus.png` 分别记录首屏和中间卡片放大，已实际核对卡片边框覆盖相邻卡片且没有被遮挡。继续观看末端卡片增加完整可见性检查。

2026-09-18，MuMu 实例 0，ADB `127.0.0.1:16384`，Android 15，实际横屏画面 2560×1440。

- Gradle `:app:assembleDebug :app:lintDebug --offline` 成功。Lint 无错误，存在版本更新建议、演示素材资源等警告。
- APK 安装并启动成功；本次应用 AndroidRuntime 日志没有崩溃记录。
- 首屏实际截图：继续观看为 16:9 卡片的单行横向货架，位置已下移；我的媒体等后续货架在首屏外。
- 初始首卡焦点边框；下键滚动到我的媒体并选中电影分类；返回键回到首屏。
- Animeko 开关开启后显示导航入口；安装更新后开关保留；点击进入独立占位页，顶部与媒体库共用。
- 设置弹窗全屏拦截背景触摸，焦点限制在弹窗内。
- 本次修改后再次构建与 Lint 成功，并安装到 MuMu。导航上/下键切换内容，左右键选中第五张卡片并横向滚动已核对。
- 鼠标滚轮在继续观看区域横向滚动，页面仍停留在首屏；在区域外滚动时，壁纸逐渐变暗与模糊，货架没有黑色底板接缝。
- `first-screen-v2.png` 为新版首屏；`navigation-down-v2.png` 为键盘进入首卡；`last-card-v2.png` 为键盘选择末卡；`mouse-horizontal-v2.png` 为鼠标横向滚动；`mid-blur-v2.png` 为中间模糊状态。
- 下键进入我的媒体并达到最大背景模糊，分类焦点放大 20%，顶部导航保持原位；实际截图为 `full-blur-v2.png`。此次测试 AndroidRuntime 日志无崩溃记录。

尚未进行 Android 9 或 Android 13 实机验证，也未进行 720p / 1080p / 4K 分辨率分别运行验证。等比缩放与首屏边界按统一设计视口实现。

MuMu 将应用放在虚拟任务显示屏，同时向另一个 HWC 屏输出镜像。APK 更新后显示编号会变化；不能使用固定 `screencap` 屏编号。应重新读取 `dumpsys SurfaceFlinger --display-id` 与 `dumpsys activity activities`。测试时向当前任务显示屏注入输入，在非黑屏镜像屏截图。

`mumu-animeko-page.png` 为独立页面截图；`mumu-library.png` 为下滑后的货架；`mumu-final.png` 为焦点修正版首屏（Animeko 开启状态）。`first-screen.png` 将记录最终交付默认状态。

2026-09-19: Native home page added with shared persistent header, centered bold 24-hour clock, weekday/date and Android ICU ChineseCalendar lunar date. Bottom dock is one row of six icon-only launch targets, backed by installed launcher activities (package visibility scoped with intent queries). Haze 28dp blur on Android 12+, cached cropped wallpaper blur on older versions. FocusCard selection/sweep and global entrance timeline reused; directional focus routes from Home tab to dock, across six targets and back to Home tab. AssembleDebug/lintDebug passed, installed on connected device; screenshots home-page.png and home-navigation.png verify full-screen layout and no lower icon-row peeking. AndroidRuntime log showed no crash during key traversal. Current coastal wallpaper retained; reference Chief of War artwork is not included.

2026-09-18：媒体选中放大 10%，使用轻微弹簧；继续观看默认卡片 352×198。
界面文字、按钮、玻璃表面与进度条改为中性白灰，选中加入轻微白色外发光；导航选中不缩放。
详情页方向键增加无候选控件时的上下滚动，信息面板和媒体信息可聚焦；操作按钮向上滚回顶部并选择返回按钮。
MuMu 方向键测试截图：detail-back-focus.png、detail-final-bottom.png（媒体信息完整显示且焦点可见）。
构建与 lintDebug 通过。
继续观看焦点同步 Banner 背景、标题、介绍、进度及播放入口；其他模块不会更新 bannerIndex。Android 9-12 兼容模糊也按 Banner 图重新渲染。MuMu 实测选择《暗涌》更新背景与文案，截图 banner-follow-selection.png；导航下键先到继续播放，再到第一张继续观看卡片。

2026-09-18: Remote scrolling optimization: removed 180ms delayed caption relocation; cancel superseded detail navigation jobs; fixed Haze input scale at 0.5 while preserving 0–50dp blur; background artwork warmup with concurrent caches; cast avatars decoded off UI thread. MuMu Android 15 2560x1440, same 8 Down + 8 Up sequence at 500ms intervals before explicit shelf routing: high-input-latency count 144 -> 36, legacy janky frames 25.41% -> 11.11%, modern janky frames 1.64% -> 1.75%; this single sample does not establish overall frame-jank improvement. Final explicit shelf Down routing verified with 10 Down presses at 700ms: media information fully visible and focused (scroll-bottom-optimized.png). Assemble and lint passed.

2026-09-19: Fullscreen variable-height canvas. Removed forced landscape, enabled resizeableActivity and immersive window insets. Scale follows width / 1672; viewport uses actual available height; hero retains 941-unit upper bound instead of stretching to portrait bottom. Detail cover expands to actual viewport height; cached glass wallpaper uses ContentScale.Crop-equivalent drawing. Assemble/lint passed. Installed SM-F936N; verified unfolded landscape 2176x1812 and portrait 1812x2176: full-width/full-height rendering, library categories/favorites visible below continued watching in portrait. Detail shelves and information visible in tall viewport. Phone rotation setting restored to free. Screenshots phone-fullscreen.png, phone-portrait.png, phone-detail-portrait.png.

2026-09-19: Shared explicit-row remote navigation captures Up/Down before geometric focus search. Adjacent rows include controls, filters and shelves; ordinary targets use nearest horizontal center. View-all actions moving Down select the first target; detail horizontal shelves first scroll back to index 0. AssembleDebug and lintDebug passed, installed on SM-F936N. ADB key-event logs verified library header -> play -> watching -> categories -> favorites action -> first filter -> favorites, recent additions action -> first card (x 2004 -> 237.5), recent music action -> first card (x 2004 -> 237.5). Detail verified actions -> tabs -> season controls -> episodes -> cast action -> first cast (x 1993 -> 224) -> similar action -> first similar (x 1993 -> 248.5). No AndroidRuntime crash appeared in this sequence.

2026-09-19: Up/Down now centers content focus between the fixed top-bar safe area and bottom inset, clamped to real list boundaries without added blank space. Poster focus registration covers the entire cover/caption item. Automatic BringIntoView scrolling is disabled inside the shared host to avoid competing animations; horizontal shelves retain their explicit horizontal navigation. Phone logs verified library categories, action rows, filters and complete posters settle within 1px of target y=973.67px; a 120ms alternating-key sequence ends centered on the correct row. Detail cast/similar shelves and action rows also centered in the preceding test. At the library end, the final action remains below center with canScrollForward=false. Shared FocusSweep covers cards, buttons, navigation capsules, text actions, detail tabs, reading panels and search focus: 500ms, BlendMode.Overlay, travel from top-left to bottom-right with both gradient edges outside the shape at completion. Assemble/lint passed and final APK installed on SM-F936N. Sweep direction/endpoint verified by implementation geometry; no frame-by-frame visual measurement was made.

2026-09-19: 快捷控制中心：面板 Haze 模糊半径 52 -> 102dp（全屏背景仍为 28dp）；选中样式改为内描边，聚焦/悬停时 100%、仅选中未聚焦时 50% 透明度，所有磁贴不再因焦点放大（移除 1.035 缩放的 graphicsLayer）。第二排紧凑磁贴由固定 height(160dp) 改为 IntrinsicSize.Min + fillMaxHeight，消除行高 160dp 与内容约 136dp 之间多出的 24dp 空白，四排间距统一为 8dp，末排“当前桌面”恢复 124dp 设计高度。assembleDebug 成功并安装 emulator-5554；应用位于显示 9，通过 adb 输入打开快捷控制中心，uiautomator 边界确认各排间距 7.8/8.0/7.9dp、末排 124dp、聚焦磁贴边界与未聚焦一致（无放大），截图 control-center-tiles-v2.png、control-center-focus-v2.png。

2026-09-19: 快捷控制中心微调：磁贴填充未聚焦改为 0x30（约19%不透明度，原 0x76）、聚焦/选中改为 0x60（约38%，原 0xEA 近白），边框聚焦/悬停不透明度由 100% 降为 90%（仅选中未聚焦仍保持 50%）。assembleDebug 成功并安装 emulator-5554；应用位于显示 9，adb 输入打开快捷控制中心，像素采样确认活动磁贴呈浅灰（R≈110，原近白）、非活动磁贴贴近面板背景（明显更透），截图 control-center-tiles-v3.png。

2026-09-19（20:16~21:00，SM-F936N，Android 16，adb 无线 1920×1080 覆盖分辨率）：adb kill-server/start-server 后重连真机；assembleDebug 重建并安装成功（app-debug.apk 51MB），冷启动无 AndroidRuntime 崩溃、无 FATAL。以 uiautomator dump 替代截图核对尺寸（当前会话模型无法查看图像）：dock 磁贴 250×152px（132dp）、图标 93×93px（80.96dp）、继续观看卡 404×227px（352dp 16:9）、卡间距 28px（24dp）、页面左书脊 73px（64dp），均与 LibraryDesign / HomePage 设计值一致，scale≈1.148。

2026-09-19（21:00）：导航条「当前页胶囊」尺寸调整 95.04×46.08dp → 115.04×51.08dp（宽 +20dp、高 +5dp）。Header 中原本写死的 `.72f` 系数改为按标签槽位比例计算 `((width - 10.dp) / tabs.size + 10.dp) * .8715f`，使默认的 5 标签下为 115dp、开启 Animeko 的 6 标签下随槽位同为 115.4dp。悬停/聚焦胶囊（134×74dp，浮出玻璃条）未改动。assembleDebug 与 lintDebug 通过。

2026-09-19（21:08~21:15）：安装到 MuMu 模拟器（127.0.0.1:16384，Android 15，设备内画面 2560×1440 横屏，scale = 2560/1672 ≈ 1.5311），冷启动无崩溃。此实例 Animeko 为开启状态，导航为 6 个标签（首页/媒体库/直播/应用/游戏/Animeko）。像素级核对：`screencap -a` 取到的 `all_2.png`（对应 SurfaceFlinger 的任务显示屏）中检测到的白色胶囊为 **[724,50] 177×78px**，换算 115.6×50.9dp，与 6 标签下的设计值 115.4×51.08dp 一致（改动前应为 95.3×46.1dp ≈ 146×71px）；胶囊水平中心 812.5px 与第一个标签槽位中心（732..892）完全重合，垂直位于导航条中线上。uiautomator 同时确认此时遥控器焦点在底部 dock 的「图库」上，没有任何导航标签处于聚焦态，即纯「当前页」状态。截图 `nav-capsule-mumu.png`。

注意 MuMu 的 `screencap -d` 需要 SurfaceFlinger 的长整型 display id（`dumpsys SurfaceFlinger --display-id`），不是 0/2/3/4 这类逻辑 id；直接用 `screencap -ap` 一次抓全部活动屏更省事，应用通常在 `all_2.png`。

2026-09-19: 删除快捷控制中心标题下的副标题“一切，尽在掌控”。assembleDebug 成功并安装 emulator-5554，uiautomator 确认标题仍在、副标题已从可访问性树中消失。

2026-09-19: 快捷控制中心磁贴改为按“选中”区分填充不透明度：选中（桌面模式）白色 0xE6（90%），启用但未选中深灰 0x4D（30%）；边框改为白色、不透明度 100%。assembleDebug 成功并安装 emulator-5554，像素采样确认选中磁贴近白（R≈223）、未选中磁贴贴近面板背景（更透），截图 control-center-tiles-v4.png。

2026-09-19: 快捷控制中心首排磁贴重命名：左侧“桌面模式”->“电视模式”、右侧“专注模式”->“主机模式”（副标题未改）。assembleDebug 成功并安装 emulator-5554，uiautomator 确认新名称已生效、旧名称消失。

2026-09-19: 快捷控制中心面板高度由固定 height(700dp) 改为 wrapContentHeight()，消除删除副标题后底部多出的约 26dp 空白。assembleDebug 成功并安装 emulator-5554；uiautomator 实测末排“当前桌面”底部距面板底边 31.6dp（原 58.3dp，顶部内边距为 32dp），屏幕像素扫描确认面板底边位于设计 y≈807（原 820），各磁贴尺寸与 8dp 行距不变。截图 control-center-tiles-v6.png。

2026-09-19: 快捷控制中心修复焦点逃逸与点击穿透：根 Box 增加 focusProperties{onExit={cancelFocusChange()}} + focusGroup() 形成焦点陷阱；全屏遮罩 Box 增加 pointerInput 在 Initial 阶段吞掉指针事件，避免点击面板外区域穿透到底层页面（实测此前会误开媒体详情页）。assembleDebug 成功并安装 emulator-5554；adb 注入方向键验证 UP/LEFT/RIGHT/DOWN 各方向均停在控制中心内（末排停在“当前桌面”），点击遮罩不再打开底层详情页，面板内“关闭”磁贴点击仍生效。

2026-09-19: 快捷控制中心改弹出动画并去掉整屏模糊。点击头像时面板以头像为缩放原点弹出（MainActivity 用 onGloballyPositioned 记录头像中心，ControlCenter 换算成 overlay 比例作为 TransformOrigin），同时透明度 0->1；关闭时反向缩回头像，动画结束才真正移除。整屏遮罩的 28dp Haze 模糊与压暗全部移除，只保留面板自身 102dp 模糊；透明遮罩仍吞掉指针事件。关键点校验：把动画临时改成 12s tween 后逐帧截图（screencap 每帧约 1.5s），面板变化区域 bbox 依次为 (1918,210)-(2446,594) -> (1342,210)-(2464,1146) -> (1258,210)-(2506,1230)，与以头像 (2438,87) 为原点的缩放吻合；遮罩区域边缘能量 20.31（关闭时同为 20.31，旧版 28dp 模糊时为 1.46），确认背景不再模糊。首版弹簧约 200ms 偏快偏硬，按反馈改为 tween 500ms + CubicBezierEasing(.34,1.12,.5,1) 轻微过冲。assembleDebug 成功并安装 emulator-5554，强制重启后验证：未开(无面板) -> 打开(有面板) -> 关闭(无面板且回到媒体库)。截图 control-center-pop.png。注意本机 animator_duration_scale=0.5，实际播放为标称时长的一半。

2026-09-19: 弹出动画时长由 500ms 调至 1000ms，曲线改为项目标准 cubic-bezier(.16,1,.3,1)（先快后慢、无过冲、更舒缓），打开与关闭共用同一套 spec 保证放大缩小节奏一致。assembleDebug 成功并安装 emulator-5554，强制重启后 open/close 流程复核通过。

2026-09-19: 弹出动画重做为「原地均匀缩放」：去掉以头像为原点的 overlay 缩放（会让面板中心大幅位移、观感比例不一致），改为围绕面板中心统一 scaleX=scaleY 缩放、不透明度保持 1（始终同一块面板），打开与关闭共用同一软弹簧 spring(dampingRatio=.78, stiffness=170)，回弹柔和、节奏对称；移除 MainActivity 的头像定位与 ControlCenter 的 avatarCenter/overlayBounds 相关接线。assembleDebug 成功并安装 emulator-5554，force-stop 后 open->close->reopen 三次 uiautomator 状态复核通过。

2026-09-19: 弹性缩放动画恢复。之前为解决“背景从左到右加载”把缩放改成了平移（弹性消失），现改回围绕面板自身中心的统一 scaleX=scaleY 缩放（transformOrigin 默认 Center，对称、无单侧扫动），弹簧沿用 spring(dampingRatio=.7, stiffness=90)。assembleDebug 成功并安装 emulator-5554，force-stop 后 open->close->reopen 复核通过。

## 2026-09-20 ����ҳ���ض����������޸�
- ���⣺����ҳ�����տ� spring ����ƫ�ͣ�dampingRatio=.75������һ�λص�������һ�οɼ��Ķ����񵴡�
- �޸ģ�MediaDetailPage.kt returnMotion() dampingRatio .75f -> .9f��stiffness 100 ���䣩������һ�λص������ֱ��ͣס��
- ��֤��assembleDebug �����ɹ����Ѱ�װ�� MuMu��127.0.0.1:16384��lastUpdateTime 2026-09-20 01:38:31���������������ָд��˹�Ŀ��ȷ�ϡ�

## 2026-09-20 �����տ����ߴ�ƫ�󣨶������ţ��޸�
- ���⣺����ҳ���غ������ͣ��ƫ��ĳߴ磬������ɽ��㿨��С��
- ����FocusCard onClick �� zoomOnFocus=false ��֧ targetScale=1.1f���� PosterCard/DItem �� 1.1x ��������� Column �ϣ�boundsInRoot �Ѻ������ţ����� DetailOrigin.bounds �����ηŴ�Ϊ base*1.21���տ�ͣ�� 1.21x ����ʵ��Ƭ�� 1.1x �ӹܣ��������䣨���붯�����Ҳͬ��ƫ�󣩡�
- �޸ģ�MainActivity.kt FocusCard onClick targetScale else ��֧ 1.1f -> 1f��zoomOnFocus=false ʱ bounds ���ǽ���̬�����Ӿ��ߴ磩��
- ��֤��assembleDebug �����ɹ����豸δ���ߣ�MuMu δ������SM-F936N ���ߵ��ߣ�����װ��Ŀ�⡣
  2026-09-20 02:00 ��װ�� SM-F936N������ adb��lastUpdateTime 02:00:47����������������Ŀ�⡣
