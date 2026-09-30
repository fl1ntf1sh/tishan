# 题山（Tishan）

题山是一款本地优先的 Android 刷题应用。当前仓库已完成题库资源管理、普通刷题、复习/错题、模拟测试、可选类别备份、设置与统计等 V1 功能，并完成指定品牌图标、非灰调莫兰迪浅/深色主题、自定义图标导航和本地 release 签名。当前没有进行中的工作项；应用商店发布仍未执行。

## 技术栈

- Kotlin + Jetpack Compose + Material 3
- 单 Activity、Navigation Compose、按 feature 分包
- Room / SQLite（已实现 v5 实体、DAO、schema 和 1→2/2→3/3→4/4→5 显式迁移）
- `.ts` 备份（类别清单 + SQLite 快照；题库/题目必选，学习/模考/设置可选）
- SharedPreferences + StateFlow（主题、字体和模考默认时长）
- 固定莫兰迪品牌主题（雾霾蓝、豆沙粉、鼠尾草绿、暖米白）与 Compose Canvas 自定义导航图标
- Gradle Kotlin DSL + Version Catalog

## 目录

```text
app/src/main/java/com/tishan/app/
├── core/       # 导航、设计系统与共享 UI
├── data/       # Room、导入、备份和仓储实现
├── domain/     # 领域模型与可测试业务规则
└── feature/    # practice、review、manage 三个功能域
docs/           # 原始需求与题库样例，不打包进 APK
.memoflow/      # 需求、方案、计划与进度证据
```

## 环境准备

需要 JDK 17+、Android SDK Platform/Build Tools 36。Windows 下优先使用根目录的 `gradlew-local.bat`；它为 Java NIO 使用短临时目录，再调用未修改的官方 Gradle Wrapper，可避开部分桌面宿主中的回环连接失败。

## 构建 APK

```powershell
# Debug APK
.\gradlew-local.bat :app:assembleDebug

# 已签名 Release APK（需要本机 keystore.properties 与 keystore/）
.\gradlew-local.bat :app:assembleRelease
```

产物位置：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

## 测试与质量检查

```powershell
# JVM 单元测试
.\gradlew-local.bat :app:testDebugUnitTest

# 设备/模拟器测试（需先启动 AVD 或连接设备）
.\gradlew-local.bat :app:connectedDebugAndroidTest

# Android Lint
.\gradlew-local.bat :app:lintDebug

# 一次执行完整回归和 release 构建
.\gradlew-local.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleRelease
```

报告位置：

- JVM 测试：`app/build/reports/tests/testDebugUnitTest/index.html`
- 设备测试：`app/build/reports/androidTests/connected/debug/index.html`
- Lint：`app/build/reports/lint-results-debug.html`

## 安装、启动与签名校验

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apksigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat"

# 查看设备
& $adb devices

# 安装 Debug 或 Release
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb install -r app\build\outputs\apk\release\app-release.apk

# 启动应用
& $adb shell am start -n com.tishan.app/.MainActivity

# 验证 release 签名和证书
& $apksigner verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

若设备中已有不同签名的同包名应用，覆盖安装会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；确认无需保留该设备上的本地数据后，可先执行 `& $adb uninstall com.tishan.app` 再安装。

## 其他常用命令

```powershell
# 清理构建产物
.\gradlew-local.bat :app:clean

# 查看依赖树
.\gradlew-local.bat :app:dependencies
```
