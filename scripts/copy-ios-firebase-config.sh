#!/bin/sh
set -eu

case "${APP_ENVIRONMENT:-}" in
    tst|prod) ;;
    *) echo "error: APP_ENVIRONMENT must be tst or prod." >&2; exit 1 ;;
esac

config="$SRCROOT/Configuration/$APP_ENVIRONMENT/GoogleService-Info.plist"
destination="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/GoogleService-Info.plist"

if [ -f "$config" ]; then
    bundle_id=$(/usr/libexec/PlistBuddy -c 'Print :BUNDLE_ID' "$config")
    if [ "$bundle_id" != "$PRODUCT_BUNDLE_IDENTIFIER" ]; then
        echo "error: Firebase BUNDLE_ID does not match $PRODUCT_BUNDLE_IDENTIFIER." >&2
        exit 1
    fi
    mkdir -p "$(dirname "$destination")"
    cp "$config" "$destination"
elif [ "$KOTLIN_FRAMEWORK_BUILD_TYPE" = "Release" ]; then
    echo "error: Missing $config. Register this bundle ID in Firebase before distributing." >&2
    exit 1
else
    # Remove a previously bundled config when returning to an unconfigured Debug build.
    rm -f "$destination"
    echo "Firebase configuration absent; push notifications are disabled in this Debug build."
fi
