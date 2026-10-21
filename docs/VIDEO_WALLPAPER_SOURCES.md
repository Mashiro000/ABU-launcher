# 视频壁纸来源与素材说明

本地审核版目录包含 114 条 Apple Aerial 公共 CDN 视频链接：城市 30、自然 41、海底 21、太空 22。目录参考 [Aerial TV 公共目录](https://github.com/pengchujin/aerial-tv/blob/main/app/src/main/assets/aerials.json)，按本项目界面独立整理名称与分类，不复制其界面或实现代码。它不是对 Apple 当前所有版本片库的完整性承诺。

完整视频从 `https://sylvan.apple.com/Videos/` 按需下载，不打包或上传至本项目 Release。缩略图为各视频第一秒的真实画面，480×270 JPEG，位于 `docs/video-previews`，通过网络按页加载并缓存，**不包含在 APK 中**。每页仅组合 8 个视频卡片。首次审核使用本机联网小图源；用户确认后再上传网络资源并发布，正式地址指向项目 GitHub 原图目录。

视频与预览画面版权归 Apple 及相关权利人，不适用本项目代码 MIT 许可证。本项目与 Apple、Aerial、Aerial TV 不存在隶属或背书关系。

## Apple CDN 证书兼容

Apple 视频 CDN 的证书链由 Apple Root CA 签发，该根不在 Android 默认系统信任库中，会导致 `Trust anchor for certification path not found`。应用仅为精确域名 `sylvan.apple.com` 增加 Apple 官方公开根证书，继续校验证书链、有效期和 HTTPS 主机名，不对其它域名扩大信任，不安装系统证书，不使用“信任所有证书”的实现。

- 官方来源：[Apple PKI](https://www.apple.com/certificateauthority/)、[Apple Inc. Root](https://www.apple.com/appleca/AppleIncRootCertificate.cer)
- 公开根证书 SHA-256：`b0b1730ecbc7ff4505142c49f1295e6eda6bcaed7e2c68c5be91b5a11001f024`
- 有效期至 2035-02-09；若源站换用其它证书链需正常校验或更新信任配置，不能关闭验证兜底。

渐变壁纸为本项目原创配色与代码绘制，不来源于 Apple 图片素材。
