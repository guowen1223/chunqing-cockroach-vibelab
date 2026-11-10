# 纯情蟑螂火辣辣 — Vibe Lab 正常金币提交版 0.55

本目录是独立 Android Gradle 导出工程，不是 Unity 源工程。
包名：com.damao.dazhanglang；版本0.55；versionCode38。

## 本版变化

- 关闭无限金币：解锁外观正常扣款，金币不足不能购买。
- 普通波次的普通小强有15%概率转为金币小强；每只仍奖励100金币。
- 原有特殊怪轮换和缺披萨时的回血小强保留；Boss战小弟、首页和结算角色不参与额外转换。
- 保留已保存的完整原生XR MR游戏、场景布局及新图标。

## 本机验证与提交

使用Unity6000.6.0f1、IL2CPP、仅ARM64；compile/target36，min29。工具链是Unity自带的JDK17、Gradle9.1.0、NDK27.2.12479018。
IL2CPP已在本机强制预编译，清理后的 :launcher:assembleRelease 成功并显示 buildIl2Cpp SKIPPED；两次APK内全部15个原生库哈希一致。规范清单见.vibelab-prebuilt.json（il2cpp-prebuild-v6），包括全部7个提交的jniLibs原生库和metadata哈希。

将本目录作为单个GitHub仓库，仓库根直接含settings.gradle、launcher、unityLibrary和.vibelab-prebuilt.json。在Vibe Lab填写仓库URL和完整40位commit。不要提交APK、Unity源工程、local.properties、构建缓存、keystore或密码。

Unity标准导出没有gradlew/gradlew.bat，未擅自补充。Unity安装路径按平台说明保留，只有unityProjectPath与unity.projectPath已清空。平台统一签名，本地release保留Unity默认debug签名配置。本地重建需设ANDROID_HOME/ANDROID_SDK_ROOT并使用对应版本Gradle与JDK，执行 :launcher:assembleRelease。

9个PICO预编译插件库LOAD对齐为4KB，按官方默认warn策略记录；未宣称16KB运行兼容。APK压缩存放so，zipalign通过不等于16KB ELF兼容。未进行本轮头显或Vibe Lab云端验证，最终以平台端到端结果为准。

当前公网排行榜尚未部署；核心玩法可离线运行。不要再使用旧0.54无限金币提交包。已有玩家存档和已解锁内容不被清空。
