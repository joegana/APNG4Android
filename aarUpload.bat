rem 运行此脚本之前,请把sdk/linker/build.gradle 中的curFlavor 定义注释掉
cls
echo "Upload aar  work!"
echo
echo
REM from command: --static-backup
set JAVA_HOME=%JAVA17_HOME%
set PATH=%JAVA_HOME%/bin;%PATH%
cmd /c gradlew  "frameanimation:artifactoryPublish"
cmd /c gradlew  "apng:artifactoryPublish"
cmd /c gradlew  "awebp:artifactoryPublish"
cmd /c gradlew  "gif:artifactoryPublish"
cmd /c gradlew  "awebpencoder:artifactoryPublish"
cmd /c gradlew  "plugin_glide:artifactoryPublish"
