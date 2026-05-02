#!/bin/bash
# Verification script for plan implementation phases
# Run after completing a phase to check compilation and proto generation

export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# --- Kotlin Compilation ---
COMPILE_OUTPUT=$(./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon 2>&1)
COMPILE_EXIT=$?
if [ $COMPILE_EXIT -ne 0 ]; then
    echo "=== Compilation FAILED ==="
    echo "$COMPILE_OUTPUT" | grep -A 2 -E "^e:|error:|FAILURE|Exception|Unresolved|cannot find" | head -60
    exit 1
fi

# --- Proto Generation (only if .proto files changed) ---
PROTO_CHANGED=$(git diff --name-only HEAD~1 -- '*.proto' 2>/dev/null || true)
if [ -n "$PROTO_CHANGED" ]; then
    PROTO_OUTPUT=$(./gradlew generateProtos --no-daemon 2>&1)
    PROTO_EXIT=$?
    if [ $PROTO_EXIT -ne 0 ]; then
        echo "=== Proto Generation FAILED ==="
        echo "$PROTO_OUTPUT" | grep -A 2 -E "^e:|error:|FAILURE|Exception" | head -60
        exit 1
    fi
fi

echo "=== Verification passed ==="
