#!/usr/bin/env bash

# 设置Android SDK环境变量
export ANDROID_HOME="$HOME/android-sdk"

# 设置Java环境变量（如果需要）
export JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-17"

# 确保build-tools 36.0.0存在
if [ ! -d "$ANDROID_HOME/build-tools/36.0.0" ]; then
    echo "警告: build-tools 36.0.0 不存在，请检查Android SDK安装"
    exit 1
fi

# 确保android-35平台存在
if [ ! -d "$ANDROID_HOME/platforms/android-35" ]; then
    echo "警告: android-35 平台不存在，请检查Android SDK安装"
    exit 1
fi

echo "Android SDK环境已设置:"
echo "  ANDROID_HOME: $ANDROID_HOME"
echo "  Java版本: $(java -version 2>&1 | head -n 1)"
echo ""
echo "现在可以运行: BUILD_DIR=my_build_dir ./build_without_gradle.sh"