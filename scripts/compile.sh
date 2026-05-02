#!/bin/bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
exec ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon 2>&1 | tail -5
