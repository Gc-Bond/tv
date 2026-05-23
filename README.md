# 西瓜影院轻量版 Android TV

一个面向 Android TV / 电视盒子的原生壳应用。应用使用隐藏 WebView 读取 `wxjsw.com` 的页面数据，前台界面由原生 Android 控件渲染，遥控器焦点、分类筛选、影片详情和播放体验都按电视场景设计。

## 功能

- 原生首页和分类列表，支持遥控器方向键操作。
- 分类筛选、分页和加载中遮罩，加载超过 10 秒后允许继续操作。
- 影片详情在右侧区域内展示，包含封面、主演、导演、简介和剧集列表。
- 剧集支持详情页局部预览播放，选中预览区后可切到全屏。
- 播放器基于 `TextureView + MediaPlayer`，局部预览和全屏之间复用同一个播放视图。
- 切换页面时自动停止详情页预览，避免后台继续播放。

## 项目结构

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/gc/wxjswtv/
app/src/main/res/
build_apk.sh
```

主要模块：

- `MainActivity`：主界面、分类、详情和焦点调度。
- `WxjswDataParser`：解析首页、分类、详情和播放页 HTML。
- `NativePlayerController`：原生播放器控制。
- `TextureMediaPlayerView`：可在局部和全屏之间平滑切换的播放视图。
- `FilterPanelController`：分类筛选面板控制。
- `CategoryLoadingController`：分类加载遮罩和超时解锁。

## 构建

本项目没有使用 Gradle，直接通过 Android SDK 命令行工具构建。当前脚本默认使用：

- Android SDK Platform：`android-36.1`
- Build Tools：`36.1.0`
- Java：`/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home`
- 最低系统版本：Android 4.1，`minSdkVersion=16`

执行：

```bash
./build_apk.sh
```

生成 APK：

```text
build/outputs/wxjsw-tv-shell.apk
```

调试签名文件会生成在项目根目录：

```text
wxjsw-debug.keystore
```

该文件不会提交到仓库。

## 安装

USB 调试连接电视盒子后执行：

```bash
adb install -r build/outputs/wxjsw-tv-shell.apk
```

网络调试：

```bash
adb connect 盒子IP地址:5555
adb install -r build/outputs/wxjsw-tv-shell.apk
```

启动应用：

```bash
adb shell am start -n com.gc.wxjswtv/.MainActivity
```

## 说明

- 本项目不内置、不分发任何视频内容，只把目标网站页面作为数据源。
- 播放能力取决于设备系统的 `MediaPlayer` 对视频格式和 HLS 的支持。
- 当前主要适配电视横屏和遥控器操作，手机触摸布局还没有单独分支。
