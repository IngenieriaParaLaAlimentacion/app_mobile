# Build and run iOS without an Apple account

The Xcode project is at `iosApp/iosApp.xcodeproj`. The simulator lets you build, install and run the app **without signing in to Apple and without a paid membership**. This local validation does not enable distribution to users.

## Setup

- A Mac with Xcode and an iOS Simulator runtime installed from Xcode's settings.
- Xcode selected for the command-line tools: check `xcode-select -p` and `xcodebuild -version`.
- JDK 21. The Kotlin build phase selects it through `/usr/libexec/java_home -v 21`.
- Android SDK, required to configure the shared Gradle module, which also contains Android. Set its location with `sdk.dir` in `local.properties` or `ANDROID_HOME` in the environment.
- Android `tst` and iOS `iosApp-tst` share **the same `api_url_tst` property in `local.properties`**. If you already have an Android configuration in another checkout, reuse that value in the new workspace. If none exists, copy `local.properties.example` and fill it in. Do not overwrite a local file that is already configured.

CocoaPods, XcodeGen and other generation tools are not needed to open or build the project.

The project resolves FirebaseCore and FirebaseMessaging through Swift Package Manager to integrate the notifications already in `develop`. The first time you open the project it also downloads those dependencies.

## Firebase configuration for notifications

Each environment uses its own file, downloaded when registering the matching Bundle ID in Firebase:

- `iosApp/Configuration/tst/GoogleService-Info.plist`
- `iosApp/Configuration/prod/GoogleService-Info.plist`

The build phase copies the environment's file into the bundle and checks that its `BUNDLE_ID` matches the app's. These files are ignored by Git.

A Debug build can run without that file, with push notifications disabled. Token registration operations return a configuration error, and sign-out can complete without Firebase. A Release build requires the environment's file: if it is missing or belongs to another Bundle ID, the build stops. Receiving notifications also requires completing the APNs, signing and capabilities setup of the Apple team; that validation is still pending. [Official Firebase setup for Apple](https://firebase.google.com/docs/ios/setup).

## Choose the environment

| Xcode scheme | Local run | Distribution archive | Bundle ID |
| --- | --- | --- | --- |
| `iosApp-tst` | `DebugTst` | `ReleaseTst` | `ar.com.ingenieriaalimentacion.tst` |
| `iosApp-prod` | `DebugProd` | `ReleaseProd` | `ar.com.ingenieriaalimentacion` |

The configurations live in `iosApp/Configuration/`. The build phase runs `scripts/build-ios-framework.sh` and passes the matching `-PtargetEnvironment` to Gradle. `KOTLIN_FRAMEWORK_BUILD_TYPE` maps the configurations to Debug/Release. Build the environments one after another, because both generate the same shared Kotlin configuration.

`tst` requires an explicit URL in `local.properties`. `prod` uses `api_url_prod` if it is set and, otherwise, the production default defined in Gradle. Choosing `prod` means using that backend for every action in the app.

## Run from Xcode

1. Open `iosApp/iosApp.xcodeproj`.
2. Select `iosApp-tst` or `iosApp-prod` and an **iPhone Simulator** as the destination.
3. Run Product → Run. A simulator needs no signing team and no Apple account.

The first build downloads dependencies and generates the Kotlin framework, so it may take longer than later ones. The configuration disables User Script Sandboxing to allow the Gradle integration documented by Kotlin. [Direct integration with Xcode](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html).

## Build from the terminal without signing

From the repository root:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp-tst \
  -configuration DebugTst \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .context/ios/DerivedData \
  CODE_SIGNING_ALLOWED=NO build
```

For production, change the scheme to `iosApp-prod` and the configuration to `DebugProd`. The resulting `.app` is inside `Build/Products/<configuration>-iphonesimulator/` under the chosen Derived Data folder. It is a simulator app, not an IPA that can be installed on a physical iPhone.

To install and open it on a running simulator, after building production:

```bash
xcrun simctl list devices available
xcrun simctl install booted \
  .context/ios/DerivedData/Build/Products/DebugProd-iphonesimulator/IngenieriaAlimentacion.app
xcrun simctl launch booted ar.com.ingenieriaalimentacion
```

If more than one simulator is running, replace `booted` with the identifier of the chosen device. For `tst`, use `DebugTst-iphonesimulator` and the Bundle ID with the `.tst` suffix.

## What needs an account later

To test on a physical iPhone you must configure signing and an Apple team. A free Personal Team has restrictions and seven-day profiles. TestFlight, App Store and Ad Hoc require the matching membership or an approved fee waiver. [Apple account and Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account).

Distribution preparation and functional acceptance testing are detailed in the [handover document](traspaso-app-mobile.md) (in Spanish). The first simulator run does not replace testing of login, storage, offline mode, sync, external links and a physical device.

## Validation performed

Validation date: October 28, 2026.

- Xcode 26.3, iPhoneSimulator SDK 26.2, iPhone 17 Pro simulator with iOS 26.3.1, arm64 architecture.
- `DebugProd` and `DebugTst` builds with `CODE_SIGNING_ALLOWED=NO`: `BUILD SUCCEEDED` in both cases.
- Installation with `simctl install` and launch with `simctl launch`: the app stayed open and showed the welcome screen with the "Crear cuenta" and "Iniciar sesión" options.
- Validated: Kotlin/Swift compilation, framework linking, Compose resources and UI startup in both environments. No one signed in and no data was loaded.
- The `tst` URL was taken from the `local.properties` of the main Android checkout and set in this workspace's local file, which is ignored by Git. Verified: `APP_ENVIRONMENT=tst`, the Bundle ID ending in `.tst` and the test URL inside the built framework.
- Release schemes and signed distribution have not been validated yet; authenticated and sync tests are also still pending.

Both builds were also compiled on `develop`, with the Firebase dependencies and the notification sources included in the project, without `GoogleService-Info.plist`. The logs of that check are in `.context/ios/build-tst-develop.log` and `.context/ios/build-prod-develop.log`. We also verified that a missing Firebase configuration is rejected in Release or with a wrong Bundle ID, that a valid configuration is copied, and that a previous configuration is removed in Debug. These checks do not prove that push notifications are received.

The screenshots are in `.context/ios/primera-ejecucion.png` (production) and `.context/ios/ejecucion-tst.png` (test); the logs are in `.context/ios/build-prod.log` and `.context/ios/build-tst.log`. `.context/` is not part of the delivered repository.

The project declares iOS 15.0 as its deployment target, but the linker reported that an ICU object from a dependency was built for iOS Simulator 17.2. Before fixing the minimum supported version for distribution, check that dependency and test the older versions you choose. This run only proves the 26.3.1 runtime listed above.
