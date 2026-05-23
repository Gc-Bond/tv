# 西瓜影院轻量版 Android TV 壳

这是给老款小米盒子准备的极简 WebView 壳应用，启动后直接打开 `https://wxjsw.com/`。

## 设计目标

- 尽量少依赖，降低老盒子运行负担。
- 支持 Android TV/盒子启动器入口。
- 支持网页后退、视频全屏和基础错误提示。

## 重要限制

- 播放能力取决于盒子系统 WebView 和目标网站播放器。
- 如果网站使用新版浏览器特性、DRM、复杂广告脚本或不兼容老 TLS，APK 壳无法彻底解决。
- 本项目只提供网页容器，不内置、不抓取、不分发任何视频内容。

## 手工构建说明

本项目是无 Gradle 的轻量工程，可以用 Android SDK 命令行工具构建。当前机器如需直接打包 APK，需要可用的 `aapt`、`javac`、`d8`、`zipalign` 和 `apksigner`。

调试签名文件会保存在项目根目录的 `wxjsw-debug.keystore`，这样重复安装时签名保持一致；该文件不提交。

```bash
./build_apk.sh
```

生成文件：

```text
build/outputs/wxjsw-tv-shell.apk
```

## 安装到盒子

先在小米盒子里打开开发者选项和 USB 调试，电脑能看到设备后执行：

```bash
adb install -r build/outputs/wxjsw-tv-shell.apk
```

如果通过网络调试安装，先连接盒子的 IP：

```bash
adb connect 盒子IP地址:5555
adb install -r build/outputs/wxjsw-tv-shell.apk
```
