#!/bin/bash

clear
echo "Upload aar  work!"
export JAVA_HOME=$JAVA17_HOME
export PATH=$JAVA_HOME/bin:$PATH
echo
echo

./gradlew  "frameanimation:artifactoryPublish"
./gradlew  "apng:artifactoryPublish"
./gradlew  "awebp:artifactoryPublish"
./gradlew  "gif:artifactoryPublish"
./gradlew  "awebpencoder:artifactoryPublish"
./gradlew  "plugin_glide:artifactoryPublish"
