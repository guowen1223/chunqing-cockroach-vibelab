# 纯情蟑螂火辣辣 · VibeLab 0.59

Android Gradle 工程，包名为 com.damao.dazhanglang，versionCode 为 42。此版包含第二关武士 Boss、武士和电动车小强的动画修复，以及桌面脚底穿模修复。

使用 Unity 6000.6.0f1 导出，支持 ARM64，最低 API 29，目标 API 36。IL2CPP 已预编译，二进制和元数据的 SHA-256 见 .vibelab-prebuilt.json。

本机设置 ANDROID_HOME 和 ANDROID_SDK_ROOT 后，用 Unity 自带的 Gradle 9.1.0 和 JDK 17 执行 :launcher:assembleRelease。终验构建成功，日志显示 buildIl2Cpp SKIPPED，两次构建的 15 个原生库哈希全部一致。平台签名由 VibeLab 处理。

提交时将仓库根目录指向此 Gradle 工程，并在 VibeLab 填写仓库 URL 和完整的 40 位 commit ID。云端构建与 PICO 真机运行尚待验证。
