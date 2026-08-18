#!/bin/sh

set -eu

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
elif [ -x /opt/homebrew/opt/openjdk@17/bin/java ]; then
    JAVA_CMD=/opt/homebrew/opt/openjdk@17/bin/java
elif command -v java >/dev/null 2>&1; then
    JAVA_CMD=java
else
    echo "JDK 17 is required. Install it with: brew install openjdk@17" >&2
    exit 1
fi

exec "$JAVA_CMD" \
    -Dorg.gradle.appname=gradlew \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
