# Set up push notifications on iOS

The `iosApp/iosApp.xcodeproj` project already includes FirebaseCore and FirebaseMessaging through Swift Package Manager. The Swift integration is wired to the shared Kotlin code. To receive notifications you still need to set up Firebase and APNs for each environment, enable the signing capabilities and validate on a physical iPhone.

## Included integration

- `iosApp/iosApp/AppDelegate.swift` configures Firebase when it finds the configuration file, installs `IOSFirebaseMessagingBridge`, initializes Koin and sets up `PushNotificationDelegate`. It also forwards the APNs token to Firebase Messaging.
- `iosApp/iosApp/Notifications/IOSFirebaseMessagingBridge.swift` connects token retrieval and deletion with Kotlin.
- `iosApp/iosApp/Notifications/PushNotificationDelegate.swift` requests permission, registers the device, forwards FCM tokens to Kotlin and shows foreground banners. When a `stale_establishment` notification is tapped, it passes the establishment and the days without updates to the shared code.
- `scripts/copy-ios-firebase-config.sh` copies the selected environment's Firebase file into the bundle and checks its Bundle ID.

## Set up Firebase per environment

1. Register each iOS app in the matching Firebase project:

	| Scheme | Bundle ID | Local file |
	| --- | --- | --- |
	| `iosApp-tst` | `ar.com.ingenieriaalimentacion.tst` | `iosApp/Configuration/tst/GoogleService-Info.plist` |
	| `iosApp-prod` | `ar.com.ingenieriaalimentacion` | `iosApp/Configuration/prod/GoogleService-Info.plist` |

2. Download each `GoogleService-Info.plist` and save it at the path shown. The files are ignored by Git.
3. Select the environment's scheme when building. The `Tst.xcconfig` and `Prod.xcconfig` configurations define `APP_ENVIRONMENT` and the Bundle ID.

A Debug build can run without the file, with push disabled. A Release build fails if it is missing. A file with the wrong Bundle ID stops the build in both cases. The [iOS local development guide](ios-local-development.md) details the local build and the checks performed.

## Set up APNs and signing

1. Select the Apple team and configure signing for the iOS target.
2. Enable **Push Notifications** and **Background Modes → Remote notifications** in **Signing & Capabilities**. The repository does not yet include an entitlements file for push. Add the one Xcode generates when you complete this setup.
3. Upload the team's APNs authentication key in the Cloud Messaging settings of each Firebase project.
4. Check that the provisioning profile matches the Bundle ID and has the push capability enabled.

References: [set up Firebase on Apple](https://firebase.google.com/docs/ios/setup) and [set up Firebase Cloud Messaging on iOS](https://firebase.google.com/docs/cloud-messaging/ios/client).

## Validate on a physical iPhone

With Firebase, APNs and the backend configured, check:

- Initial permission request, acceptance and rejection.
- Token registration with an authenticated session, token rotation and deletion on sign-out.
- Foreground banners are received.
- Opening from the background and with the app closed.
- Selecting the payload's establishment and opening its stock when the notification is tapped.

The builds and simulator launches recorded in the development guide do not prove push delivery. These tests with the services configured are still pending.
