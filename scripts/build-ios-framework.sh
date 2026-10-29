#!/bin/sh
set -eu

case "${APP_ENVIRONMENT:-}" in
    tst|prod) ;;
    *) echo "error: APP_ENVIRONMENT must be tst or prod (select an iOS scheme)." >&2; exit 1 ;;
esac

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd "$SRCROOT/.."

# Always pass the environment, including for custom Xcode configurations. Gradle
# tracks it as an input so switching schemes cannot reuse the other backend URL.
exec ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode \
    "-PtargetEnvironment=$APP_ENVIRONMENT"
