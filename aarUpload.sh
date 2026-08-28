#!/bin/bash
set -e

cd "$(dirname "$0")"

echo "Upload aar  work!"

if [ -z "$JAVA17_HOME" ]; then
    echo "错误: 未设置 JAVA17_HOME 环境变量" >&2
    exit 1
fi
export JAVA_HOME="$JAVA17_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew "frameanimation:artifactoryPublish"
./gradlew "apng:artifactoryPublish"
./gradlew "awebp:artifactoryPublish"
./gradlew "gif:artifactoryPublish"
./gradlew "awebpencoder:artifactoryPublish"
./gradlew "plugin_glide:artifactoryPublish"

echo "所有模块上传完成"
