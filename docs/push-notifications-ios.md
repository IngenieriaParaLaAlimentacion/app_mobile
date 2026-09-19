# iOS push-notification setup

The repository currently versions Swift sources but not `iosApp.xcodeproj`; therefore these
steps cannot be applied or verified in this PR.

1. Create/version the Xcode project and add the existing `iosApp/iosApp` source tree.
2. Add Firebase iOS SDK through Swift Package Manager and link `FirebaseCore` and
   `FirebaseMessaging` to the app target.
3. Create separate Firebase iOS apps for tst/prod bundle IDs. Put each
   `GoogleService-Info.plist` outside git and select/copy the correct file through an
   xcconfig-backed build phase. Never let tst and prod share one plist.
4. Enable Push Notifications and Background Modes > Remote notifications. Commit the
   entitlements file once the project exists.
5. Upload the APNs authentication key to both Firebase projects.
6. Add `IOSFirebaseMessagingBridge.swift` and `PushNotificationDelegate.swift` to the app target.
7. Apply the `AppDelegate.swift` changes: configure Firebase first, install the bridge,
   initialize Koin, then configure the notification delegate.
8. Forward `didRegisterForRemoteNotificationsWithDeviceToken` to
   `Messaging.messaging().apnsToken`.
9. Verify on a physical device: initial permission, authenticated registration, token rotation,
   logout deletion, foreground banner, background tap, killed-app tap, and selection/opening of
   FoodStock for the payload establishment.
