#!/bin/zsh
cd "$(dirname "$0")" || exit 1
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
exec ./gradlew --offline -Poculus runClient
