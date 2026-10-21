# 视频壁纸来源与素材说明

内置目录是四个 Apple Aerial 公共 CDN 视频链接。视频不包含在安装包内，不上传至本项目 Release，用户按需从原地址下载。视频与对应预览画面版权归 Apple 及相关权利人所有，不适用本项目代码的 MIT 许可证。本项目与 Apple、Aerial、Aerial TV 不存在隶属或背书关系。

目录核对参考：[Aerial TV 目录](https://github.com/pengchujin/aerial-tv/blob/main/app/src/main/assets/aerials.json)、[Aerial](https://aerialscreensaver.github.io/)。目录、名称与链接为本项目独立整理，没有复制参考项目实现代码。

| 画面 | 原始视频文件 | 内置预览图 |
| --- | --- | --- |
| 开普半岛海狮 | `SE_A016_C009_SDR_20190717_SDR_2K_AVC.mov` | `aerial_seals.jpg` |
| 格陵兰冰峡湾 | `comp_GL_G002_C002_PSNK_v03_SDR_PS_20180925_SDR_2K_AVC.mov` | `aerial_greenland.jpg` |
| 南加州至下加利福尼亚 | `comp_A114_C001_0305OT_v10_SDR_FINAL_22062018_SDR_2K_AVC.mov` | `aerial_california.jpg` |
| 韩国与日本夜航 | `comp_GMT026_363A_103NC_E1027_KOREA_JAPAN_NIGHT_v18_SDR_PS_20180907_SDR_2K_AVC.mov` | `aerial_asia_night.jpg` |

视频地址前缀为 `https://sylvan.apple.com/Videos/`。预览图为相应视频约第一秒的真实画面，缩为 480×270 JPEG，四张共 98,020 字节，保障离线浏览目录和低性能电视的显示速度，不把无关图片用作预览。原 CDN 可能变更，网络/解码异常时提供失败提示或回退背景。

渐变壁纸为本项目原创配色与代码绘制，不来源于 Apple 的图片素材。
