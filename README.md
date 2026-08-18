<p align="center">
  <img src="docs/assets/smart-hole-avatar-badge.png" width="144" alt="Smart Hole 头像徽章">
</p>

# Smart Hole

简体中文 | [English](README.en.md)

**运行在智能手机上的原生智能体。**

*Powered by [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness).*

Smart Hole 基于 DeepSeek Harness 构建，并将持续支持 DSH 的全部功能。当前支持
Root Android。

## 特点

- 原生 Jetpack Compose 界面，不是 WebView 套壳；
- 官方 DSH Agent、Session、模型、工具与插件运行时；
- Android/Bionic arm64 Node.js，无需 Linux 虚拟机、rootfs、chroot 或 proot；
- uid 0 本地 Host，以及 Android 文件、应用、设置和系统服务能力；
- 官方 JSON RPC、WebSocket、提问、审批、Goal、Todo、Plan Mode 与子 Agent；
- 原生 Queue/Steer、历史分页、斜杠命令发现、Reasoning 折叠展示与后台 Job 状态；
- 原生 Markdown、按 Turn 聚合的工具轨迹、Workspace 分组、会话重命名/分叉/归档与模型管理；
- 分层的原生 Feature Module；子 Agent 树展示运行状态、耗时、Token，并可进入其会话；
- 输入区原生显示 DSH 上下文占用与 DeepSeek API 余额，余额查询不会向 UI 回传 API Key；
- 兼容全局及逐目录 `AGENTS.md`/`CLAUDE.md` 工作区指令；
- 直接消费 DSH 插件提供的工具 Presentation 与 Session Projection，未知卡片安全降级；
- 不依赖 GApps、Google Play 服务或特定厂商 ROM。

## 架构

```text
原生 Compose App（普通 Android UID）
        |
        | 受保护的回环 JSON RPC + WebSocket
        v
官方 DSH Host（uid 0，Android/Bionic Node.js）
        |
        +-- DSH 标准工具与插件
        +-- dsh-plugin-android
        v
Android 文件、应用、设置、命令与系统服务
```

## 支持范围

| 层级 | Android / 设备 | Root | 状态 |
| --- | --- | --- | --- |
| 已验证 | Android 16 / API 36、Redmi K90、LineageOS 23.2 | Magisk 30.7 | 通过参考设备验收 |
| 兼容目标 | Android 15–16、arm64-v8a、AOSP 衍生及 OEM ROM | Magisk、KernelSU、APatch | 尚待逐项验证 |
| 编译目标 | Android 17 / API 37 | — | 已编译，尚未完成运行验证 |
| 技术下限 | Android 9 / API 28 | — | 仅代表 APK 和 Node 构建下限 |

当前不支持 32 位、x86 或无 Root 设备。K90 是参考设备，不是运行时条件。

## 当前缺口

- 签名运行时安装器与升级通道；
- 更多手机、ROM、KernelSU 与 APatch 验证；
- PTY 终端和文件/图片附件；
- 完整的 Provider、模型目录和插件管理界面；
- 会话 ZIP 导出、消息反馈与 Trajectory 检查界面；
- Goal、Todo、子 Agent 的完整管理交互，以及 Workflow、Ralph 等核心语义的原生呈现；
- 持久审计历史与更多 Android Framework 工具。

精确验证结果见 [参考设备验证记录](docs/reference-validation.md)，实现设计见
[架构说明](docs/architecture.md)。

## 安全

DSH Host 以 uid 0 和 `danger-full-access` 运行。它只监听 `127.0.0.1`，并通过
`iptables owner` 规则限制为仅当前 APK UID 可访问。SELinux、锁屏、硬件
KeyStore 和内核能力仍然有效。

当前 Profile 不逐工具审批。请只在你接受模型可控制 Root Agent 的设备上使用。

## 构建 App

需要 JDK 17、Android SDK Platform 37 和 Build Tools 36.0.0：

```bash
export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"
./gradlew lint test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 构建运行时

```bash
./runtime/dsh-android/prepare-runtime.sh

NODE_LICENSE=/path/to/node-v24.18.1/LICENSE \
NODE_LIBCXX=/path/to/android-ndk-r29/.../aarch64-linux-android/libc++_shared.so \
  ./runtime/package-runtime.sh /path/to/android-arm64/node \
  runtime/dist/dsh-android-arm64.tar.gz

ANDROID_SERIAL=<serial> \
  ./runtime/deploy-runtime.sh runtime/dist/dsh-android-arm64.tar.gz
```

运行时安装到 `/data/adb/dsh-android/`，其中保存 Runtime、Workspace、Session、
凭据和日志。

## 上游声明

Smart Hole 是独立社区项目，不是 DeepSeek 官方产品，也未获得 DeepSeek AI 背书。
DeepSeek Harness 使用上游许可证；分发时必须保留其许可证与第三方声明。
