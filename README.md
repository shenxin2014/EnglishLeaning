# EnglishLeaning · 英语 MP3 精听

离线优先的 Android 精听应用。导入本地英语 MP3，生成带时间戳的英文字幕，播放时逐词高亮。支持点词跳播、SRT 导入、离线查词及收藏回听。

[产品需求文档](docs/requirements.md)

## 使用

1. 从「课程库」导入 MP3，等待离线转写。
2. 在课程详情点单词跳播，点时间从句首播放，长按字幕进入收藏菜单。
3. 在「收藏复习」查词或点击带来源的收藏回听。课程详情可打开离线中文辅助显示。

新转写可使用词级时间戳。缺少词级时间的字幕显示「词级估算」。当前中文辅助内容来自预置句译和内置词汇释义。

## 构建

需要 JDK 17、Android SDK 35、NDK `28.2.13676358` 和 CMake `3.22.1`。使用 Android Studio 打开项目，或设置 Android SDK 路径后运行 Gradle Wrapper。

```sh
git clone --recurse-submodules https://github.com/shenxin2014/EnglishLeaning.git
cd EnglishLeaning
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

在 Windows 上使用 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug`。已经普通克隆的工作区可运行 `git submodule update --init --recursive`。

调试 APK 生成在 `app/build/outputs/apk/debug/app-debug.apk`。应用最低支持 Android 8.0，默认构建面向支持 ARMv8.2 FP16 和 Dot Product 指令的 ARM64 设备。

首次构建会联网准备约 77.7 MB 的英文模型并校验 SHA-256。后续构建会复用本地模型。生成的 APK 自带模型，手机转写可离线运行。

## 项目结构

- `app/src/main/java/com/example/listenenglish/ui` 包含 Activity、Fragment、ViewModel 和列表适配器。
- `data` 包含 Room 表、DAO、迁移和学习数据仓库。
- `transcription`、`subtitle` 与 `translation` 分别处理音频转写、字幕时间轴和离线释义。
- `app/src/main/cpp` 包含 JNI 桥接和固定版本的 whisper.cpp 子模块。

## 第三方资源

- whisper.cpp 子模块固定到 v1.7.5，许可随子模块提供。[whisper.cpp 许可](https://github.com/ggml-org/whisper.cpp/blob/v1.7.5/LICENSE)
- 随 APK 打包的 `ggml-tiny.en.bin` 为 OpenAI Whisper 英文 tiny 模型的 GGML 转换版，约 77.7 MB。[模型文件](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-tiny.en.bin) · [模型许可](app/src/main/assets/models/WHISPER-LICENSE.txt)
