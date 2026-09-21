# NativeTvDesktop (Android 原生电视桌面) 工程架构与源码分析报告

> **分析模型**：Gemini 3.8 Flash  
> **工程目录**：`e:\limi\launcher 3`  
> **报告生成时间**：2026 年 9 月  

---

## 1. 工程概况与技术定位

本项目名为 **NativeTvDesktop**（命名空间 `com.limi.tvdesktop`），是一个面向 Android 智能电视及机顶盒大屏设备的高性能、现代化**原生电视桌面（Android TV Launcher）**。

### 核心设计哲学
1. **纯粹原生 Jetpack Compose**：彻底摆脱传统 Android TV 对旧版 Leanback 架构或跨平台方案（如 Flutter）的依赖，基于 100% Kotlin + Jetpack Compose 打造，具有极高的渲染性能和现代化声明式 UI 表达力。
2. **Apple TV / tvOS 极致拟物与流体交互**：深度复刻 tvOS 标志性的视觉语言，包括 G2 连续曲率超椭圆（Squircle）、高动态焦点悬浮光晕（Focus Glow & Sweep）、实时多阶毛玻璃拟态（Progressive Frosted Glass）以及卡片向详情页展开的流体转场。
3. **软硬件全代系兼容**：最低运行版本定为 **Android 9 (API 28)**，并针对 Android 13+ (API 33) 采用硬件加速的实时渐进式模糊（Haze），在低版本设备上采用后台线程降采样高斯盒式模糊平滑降级，确保在高中低端电视芯片上均能流畅稳定运行。

---

## 2. 技术栈与工程配置

### 核心构建依赖
* **编程语言**：Kotlin 2.4.10 (`org.jetbrains.kotlin.plugin.compose`)
* **构建系统**：Gradle 9.5.0 + Android Gradle Plugin 9.3.2
* **Java 运行级别**：Java 17 (JavaVersion.VERSION_17)
* **SDK 兼容目标**：
  * `minSdk` = 28 (Android 9 Pie)
  * `targetSdk` = 35 (Android 15)
  * `compileSdk` = 36, `compileSdkMinor` = 1
* **核心三方库**：
  * `androidx.activity:activity-compose:1.12.2`：系统窗口与返回键拦截管理
  * `androidx.compose.ui:ui:1.10.4`
  * `androidx.compose.foundation:foundation:1.10.4`
  * `androidx.compose.material3:material3:1.4.0`
  * `dev.chrisbanes.haze:haze:1.7.2`：跨平台实时毛玻璃与渐进模糊渲染

### Android 系统特性集成
在 `AndroidManifest.xml` 中声明了：
* `<uses-feature android:name="android.software.leanback" android:required="false" />` 与 `touchscreen: false`，保证兼容标准手机平板与 TV 终端。
* 同时声明了 `LAUNCHER`、`LEANBACK_LAUNCHER` 与系统的 `HOME` Category，可直接替代系统桌面运行。
* 沉浸式窗口配置：全面隐藏系统状态栏与导航栏，启用 `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` 与 `IMMERSIVE_STICKY`。

---

## 3. 目录与文件架构

整个工程的主体位于 `Android/` 目录下，整体文件层次如下：

```
e:\limi\launcher 3\
├── .gitignore
├── home-seconds.png          # 桌面设计参考图
├── reasonix.toml
├── Gemini-3.8-Flash.md       # 本分析报告
└── Android\
    ├── build.gradle.kts      # 顶层构建配置
    ├── settings.gradle.kts   # 模块与仓储配置
    ├── README.md             # 设计规范与构建说明文档
    ├── references/           # 设计参考图与网络素材来源声明
    ├── qa/                   # 验证基线、视觉对比与测试截图 (113 项资产)
    └── app/
        ├── build.gradle.kts
        └── src/main/
            ├── AndroidManifest.xml
            ├── res/          # 字体 (MaShanZheng 书法)、矢量图标与多套 4K/1080P 演示海报
            └── java/com/limi/tvdesktop/
                ├── MainActivity.kt           # 核心容器、全局顶栏、媒体库主屏与状态中枢
                ├── HomePage.kt               # "首页"：时钟农历、毛玻璃 Dock 栏与本地 App 启动
                ├── MediaDetailPage.kt        # "媒体详情页"：无缝卡片展开转场、选集与演职员
                ├── ControlCenter.kt          # "快捷控制中心"：tvOS 风格抽屉、设备模式与开关
                ├── AppIconProcessor.kt       # 智能 App 图标解析提取器、自适应取色与色彩微调
                ├── ContinuousCornerShape.kt  # 核心数学造型：G2 连续曲率圆角 (Squircle)
                ├── FocusSweep.kt             # 焦点光泽扫光动效 (Linear Gradient Sweep)
                ├── RowFocusNavigation.kt     # 电视遥控器 D-Pad 2D 行对齐与焦点跳转引擎
                ├── HorizontalShelfScroll.kt  # 横向单行货架丝滑阻尼滚动与长按加速调度器
                ├── PageEntrance.kt           # 全局交错入场动画时钟控制器 (PageEntranceScope)
                ├── TopBarBackdrop.kt         # 顶部固定导航背景实时垂直渐变羽化
                ├── AdaptiveCanvas.kt         # 1672×941 设计视口等比自适应投影画板
                ├── CapsuleTextAction.kt      # 胶囊按钮交互与焦点指示器
                └── DemoLibrary.kt            # 本地离线媒体数据定义、低版本模糊预处理与缓存
```

---

## 4. 核心子系统与关键技术实现剖析

### 4.1 屏幕适配与设计坐标系统 (`AdaptiveCanvas.kt`)
* **设计分辨率基准**：采用 `1672 × 941` 物理设计视口。
* **等比自适应缩放机制**：
  * 通过 `BoxWithConstraints` 动态获取真实屏幕可用宽度，计算缩放因子 `scale = maxWidth / 1672f`。
  * 利用 `CompositionLocalProvider(LocalDensity provides Density(scale, 1f))` 重新定义 Compose 的 Density，使所有子布局中的 dp 值自动按比例放大/缩小。
  * **超宽与高屏适配**：不进行黑边拉伸，高度依据真实比例自适应向下延展（`canvasHeight`），使 720P、1080P、4K 电视均呈现严格一致的比例与布局结构。

### 4.2 数学造型与视觉质感 (`ContinuousCornerShape.kt` & `FocusSweep.kt`)
* **G2 Continuous Curvature (超椭圆)**：
  * 传统 Android `RoundedCornerShape` 在直线段与圆弧切点处存在明显的曲率突变（C1 连续），视觉偏硬。
  * 本项目在 `ContinuousCornerShape` 中通过三次贝塞尔曲线（Cubic Bézier）逼近 G2 连续曲线，使切线与曲率平滑过渡到直边，完美呈现苹果设计的平滑感。对胶囊形状自动平滑退化保留圆润端部。
* **高动态白光光晕 (White Focus Glow)**：
  * 在卡片聚焦时，利用 12 层降幂衰减的细微半透明轮廓在 `drawBehind` 中叠加，营造出电视屏幕特有的呼吸泛光层。
* **焦点掠光特效 (`focusSweep`)**：
  * 选中卡片瞬间，触发一条对角线倾斜的线性渐变半透明光带（`BlendMode.Overlay`），以 500ms 匀速划过卡片表面，赋予界面生动的拟物光影反馈。

### 4.3 电视遥控器 2D 焦点导航系统 (`RowFocusNavigation.kt`)
电视界面最大的挑战在于遥控器（D-Pad）的上下左右按键导航逻辑。原生 Compose 的自动焦点搜索容易因组件坐标偏差导致焦点乱跳：
* **结构化行规范 (`FocusRowSpec`)**：将界面垂直划分为多个逻辑行（如 `header`、`play`、`watching`、`categories`、`favorites` 等）。
* **X 轴锚点保持 (`anchorX`)**：当用户在多行之间连续按上下方向键时，引擎锁定当前的 X 坐标，在下一行寻找在 X 轴欧氏距离最近的目标卡片，杜绝了焦点在换行时跳回第一项的问题。
* **视口自动居中滚动**：配合 `animateScrollBy` 和 `RowScrollMotion`（`CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`），在焦点发生垂直转移时将目标货架平滑居中展示，并屏蔽系统默认的急促滚动冲突。

### 4.4 货架滚动与长按加速调度 (`HorizontalShelfScroll.kt`)
* **单行横向货架**：针对“继续观看”、“剧集列表”、“演职员”等单行 LazyRow，禁用可能导致跳动的系统 BringIntoView 抢占。
* **焦点与滚动同帧触发**：通过 `snapshotFlow` 监听元素可见性，在滚动开始的瞬间交接焦点，使得焦点的放大动画与货架的滑移无缝重叠。
* **长按连发优化 (`ShelfRepeatMotion`)**：检测按键间隔，若进入重复按键窗口（<220ms），立即将长达 2000ms 的长滑过渡切换为 110ms 的快速响应插值，避免按键堆积延迟。

### 4.5 苹果风格流体转场：卡片到详情页 (`MediaDetailPage.kt`)
* **共享元素展开动画**：
  * 点击媒体卡片时，记录卡片在根视口的绝对边界 `DetailOrigin.bounds`。
  * 详情页启动时，背景不是突兀弹出，而是由原始卡片矩形通过 `Animatable` 在 800ms 内平滑插值插值放大至全屏。
  * 圆角通过 `12dp * (1 - p^3)` 渐变过渡为直角，背景图片与渐变层同步淡入。
* **轻微弹簧收回 (`returnMotion`)**：
  * 返回时采用软弹簧插值（`dampingRatio = 0.75f, stiffness = 130f`），内容渐渐收拢回卡片位置，无缝衔接原卡片已高亮的状态，杜绝二次弹跳。

### 4.6 智能 App 提取与色彩提炼引擎 (`AppIconProcessor.kt` & `HomePage.kt`)
* **首页 Dock 栏**：
  * 查询系统所有具备 `CATEGORY_LAUNCHER` / `CATEGORY_LEANBACK_LAUNCHER` 的已安装应用，展示在类似 tvOS 的底部 Dock 栏。
* **多模式图标智能归一化**：
  * 针对不同类型应用图标（`AdaptiveIconDrawable`、单色图标、透明 Logo、Legacy 图标）进行通道分离与边缘探测。
  * 自动提取前景图标、居中并缩放至标准面积，剔除杂乱背景。
* **品牌色提取与微调 (`createBrandGradient`)**：
  * 自动分析应用图标的主色相与饱和度（排除黑白灰等中性色），在 HSL 色彩空间将亮度微调 ±5%，生成专属的双色微渐变品牌底板，使得整个 Dock 栏风格高度和谐统一。
  * 具备磁盘持久化与内存 `LruCache` 双重缓存加速。

### 4.7 全局交错入场与性能考量 (`PageEntranceScope.kt`)
* **共享时间轴**：一级页面与详情页每次呈现时，所有卡片及文字按索引顺序延迟 35ms、380ms 匀速滑入（`20dp → 0dp`, `alpha 0 → 1`）。
* **全局单次播放控制**：通过 `EntranceRegistry` 记录已访问页面 Key，防止 LazyColumn 复用时或滚动刷新时出现多余的二次闪烁。

---

## 5. 项目亮点与优势总结

1. **工业级 TV UI 交互质感**：无论是毛玻璃的纵深层次感、超椭圆圆角、焦点动态放大，还是丝滑的滚动阻尼，在当前开源 Android TV 项目中均处于领先水平，完全媲美甚至超越了 Apple TV 的原生体验。
2. **极佳的代码架构与模块解耦**：虽然目前是单模块项目，但各个逻辑切片（数学形状、动效、焦点调度、图标处理、视口自适应）职责清晰明确，扩展性极强。
3. **健全的验证基线**：`qa/` 目录下保留了 110 余张在不同设备（模拟器、MuMu 模拟器、真机）上的关键帧与边界测试截图，并配有详尽的 `VERIFICATION.md` 验证文档，质量要求极高。

---

## 6. 后续演进与建议扩展方向

当前工程主要完成了**界面框架、交互体系、视觉设计与演示数据**的构建。若要进一步升级为成熟的生产级系统桌面或媒体中心，建议在后续阶段推进以下方向：

1. **媒体服务器协议接入**：
   * 将 `DemoLibrary` 中的模拟数据解耦，对接 Emby / Jellyfin / Plex 或本地 SMB / WebDAV 网络存储协议。
   * 支持通过 ExoPlayer / Media3 进行 4K HDR、杜比视界及 Atmos 音频的原生硬解播放。
2. **状态中心与系统能力补全**：
   * 补全 `ControlCenter.kt` 中的 Wi-Fi 扫描与连接、蓝牙配对、遥控器电量监测等实际系统 API 联动。
3. **性能监控与低内存电视优化**：
   * 针对 1GB/2GB 内存的老旧电视盒子，进一步优化海报 Bitmap 的下采样率与纹理释放策略，避免极端情况下的 OOM。

---
*本报告由 Gemini 3.8 Flash 对当前工作区源码进行全量遍历与深度分析后自动整理输出。*
