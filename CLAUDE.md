# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此代码库中工作提供指导。

## 项目概述

这是一个简化的基于 scrcpy 的相机工具，已精简到只支持拍照功能。它移除了所有 ADB 协议、连接逻辑和视频流功能，专注于使用 Android Camera2 API 进行拍照。

## 构建命令

### 手动构建（推荐）
```bash
# 首先设置 Android 环境
./setup_android_env.sh

# 构建项目
./build_without_gradle.sh

# 输出：build_manual/scrcpy-server（JAR 文件）
```

### Gradle 构建（备选）
```bash
./gradlew build
# 输出：build/app/outputs/flutter-apk/app-release.apk
```

## 架构概览

### 核心组件
- **Server.java** - 主入口点，处理命令行参数并协调拍照
- **CameraCapture.java** - 使用 ImageReader 实现 Camera2 API 拍照功能
- **Options.java** - 解析命令行参数（camera_id、camera_facing 等）
- **ServiceManager.java** - 通过反射提供 Android 系统服务访问
- **FakeContext.java** - 模拟 Android Context 用于系统服务访问

### 关键执行流程
1. 通过 `app_process` 执行：`CLASSPATH=scrcpy-server.jar app_process /data/local/tmp com.genymobile.scrcpy.Server camera_id=0`
2. Server 解析参数以检测相机模式
3. CameraCapture 初始化 Camera2 设备和 ImageReader
4. 创建单张照片的捕获会话
5. 将 JPEG 保存到 `/data/local/tmp/scrcpy_photo.jpg`

### 重要设计决策
- 无 ADB 依赖 - 通过 app_process 直接运行
- 仅支持单次拍照（无连续流式传输）
- 使用反射访问系统服务（无需完整应用上下文）
- 简化参数解析以支持直接执行
- 最小内存占用（无视频编码，无网络栈）

## 相机功能

### 支持的参数
- `camera_id=N` - 通过 ID 选择特定相机
- `camera_facing=back|front|external` - 通过镜头方向选择相机

### 拍照过程
1. 使用 Camera2 API 打开相机设备
2. 创建 JPEG 格式的 ImageReader
3. 配置带单个 Surface 的捕获会话
4. 触发静态捕获请求
5. 图像可用时：保存到 /data/local/tmp/scrcpy_photo.jpg
6. 立即清理资源

## 构建要求

- Android SDK Platform API 35
- Android Build Tools 35.0.0
- Java 8 兼容性
- 相机访问需要 root 权限
- Termux 环境用于编译

## 代码清理状态

✓ 已移除所有 ADB 相关代码
✓ 已移除所有连接逻辑
✓ 已移除所有视频流组件
✓ 项目现在专注于拍照功能

## 与完整 scrcpy 的主要区别

1. 无服务器-客户端通信
2. 无显示/屏幕捕获
3. 无 ADB 协议实现
4. 无视频编码或流式传输
5. 通过 app_process 直接执行
6. 仅用于单一目的的拍照
7. 最少的依赖和系统要求