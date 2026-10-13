# MuMu 实时性能采样（2026-10-12）

设备：当前运行的 MuMu 实例 2，Android 12 / API 32，ADB 127.0.0.1:16448。显示横屏 2560×1440。安装本次生成的 release APK，配置为完整动画、实时模糊。之前的 Android 15 实例 3 未运行。本实例此前没有安装此应用，因此使用的是首次安装后的默认内容（演示媒体库、约 11 个桌面应用）。

采集方式：ADB 实际输入，配合 dumpsys gfxinfo reset / framestats 分段采样。首页焦点操作间隔 180ms；应用列表展开与收起间隔 650ms，重复 8 轮；设置及媒体库使用连续上下滑动输入。每段均单独重置渲染统计。结果是系统帧耗时分位数，不是 FPS，也不是优化前后对照测试。

| 操作 | 采样帧数 | P50 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| 首页焦点左右切换 | 74 | 5ms | 9ms | 14ms |
| 应用列表展开、收起、焦点切换 | 228 | 5ms | 10ms | 15ms |
| 设置连续上下滚动 | 63 | 6ms | 10ms | 16ms |
| 媒体库第一轮滚动和焦点输入 | 75 | 18ms | 85ms | 150ms |
| 媒体库后续滚动 | 61 | 13ms | 34ms | 48ms |

第一次启动 am start -W 返回 COLD，TotalTime 2052ms。操作后的 TOTAL PSS 为 153218KB（约 149.6MiB），RSS 230992KB（约 225.6MiB）。采样日志未发现 FATAL EXCEPTION、ANR in、OutOfMemoryError。

首页、应用列表和设置这次采样较快；媒体库仍存在较长帧耗时，后续滚动较第一轮更快，但还不能宣称稳定 60fps。两轮媒体输入并非完全一致，不能将差异全部归因于图片缓存。MuMu 的新旧卡顿指标不一致：媒体库第一轮新指标为 0%，legacy 为 25.33%；GPU 时间统计也异常，因此不依据这些值宣称零卡顿。真实低性能电视、用户的大型媒体库及大量应用仍需另测，本次没有旧版本基线，不能给出性能提升百分比。

原始数据：test-shots/perf-home.txt、perf-apps.txt、perf-settings.txt、perf-media.txt、perf-media-warm.txt、perf-memory.txt、perf-logcat.txt。
