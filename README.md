# 纯情蟑螂火辣辣 — Vibe Lab 提交工程 0.54

本目录是独立的 Android Gradle 导出工程，不是 Unity 源工程。
包名：com.damao.dazhanglang；版本：0.54；versionCode：37。
内容：完整原生 XR MR 游戏、当前保存的场景、新应用图标、无限金币测试模式。

## 本地已验证

- Unity 6000.6.0f1 / IL2CPP / ARM64；compileSdk 36、targetSdk 36、minSdk 29。
- 使用 Unity 自带 JDK17、Gradle9.1.0、NDK27.2.12479018（r27c）；未替换工具链。
- IL2CPP 在本机真实强制预编译，libil2cpp.so 约46.76MiB。
- 清理后重新执行 :launcher:assembleRelease 成功，buildIl2Cpp SKIPPED。
- 前后两个 APK 内全部15个原生库名称、ABI和SHA256一致。
- .vibelab-prebuilt.json 为 il2cpp-prebuild-v6 契约清单；含7个实际随工程提交的jniLibs库及metadata哈希。

## 重要事项

- 不要删除或改写 .vibelab-prebuilt.json、预编译.so、metadata、模块结构和gradle/wrapper/gradle-wrapper.properties。
- Unity导出模板没有gradlew/gradlew.bat，这是正常行为。使用对应Unity自带的Gradle运行器或由平台构建。
- gradle.properties及模块中的Unity安装路径按平台说明保留，只有unityProjectPath和unity.projectPath已清空。
- 在本地构建时，显式设置ANDROID_HOME与ANDROID_SDK_ROOT为有效Android SDK目录；使用JDK17和同版本Gradle，执行 :launcher:assembleRelease。
- release保留Unity模板的debug签名配置；仓库不含自定义keystore或签名密码。平台会统一签名。
- 9个PICO厂商预编译库的ELF LOAD对齐为4KB，按官方默认warn策略保留。so在APK内压缩存放，ZIP检查不能证明16KB运行兼容，未宣称16KB兼容。
- 本地验证不等于平台构建结果；未做本轮真机和平台端到端验证。
- 这是无限金币测试版本。当前没有公网排行榜服务器；核心玩法可离线运行。

## 提交

将本目录内容作为一个独立GitHub仓库，仓库根目录应直接包含settings.gradle、launcher、unityLibrary和.vibelab-prebuilt.json。
在Vibe Lab填写仓库地址和完整40位commit id。每次更新需要新commit并重新审核。
不要将测试APK、local.properties、缓存、密钥、Unity源工程或本机构建工具加入该仓库。
