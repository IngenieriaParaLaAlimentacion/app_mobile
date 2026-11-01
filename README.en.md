# Ingeniería en Alimentación · Mobile App

[![es](https://img.shields.io/badge/lang-es-red.svg)](README.md)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20-7F52FF.svg)](gradle/libs.versions.toml)
[![Compose Multiplatform](https://img.shields.io/badge/compose%20multiplatform-1.10.3-4285F4.svg)](gradle/libs.versions.toml)

Android and iOS app for the **Ingeniería para la Alimentación** project, built with Kotlin and Compose Multiplatform. It lets users register subjects, measurements and surveys, manage foods and browse recipes. Subjects, measurements and survey answers are stored locally and synced with the backend.

---

## Documentation

> The linked guides and the user manual are written in Spanish.

- [Online user manual](https://ingenieriaparalaalimentacion.github.io/app_mobile/), with search, chapter navigation and PDF download. Also available in the repository as [HTML](docs/manual-usuario/index.html) and [PDF](docs/manual-usuario/manual.pdf). Steps and screenshots are for Android.
- [Install and update the test app](docs/guia-instalacion-testers.md).
- [Build and run iOS on a simulator](docs/ios-desarrollo-local.md).
- [Set up iOS notifications](docs/push-notifications-ios.md).
- [Handover and distribution](docs/traspaso-app-mobile.md).
- [Recipe recommendations: technical overview](docs/recipe-recommendations.md).
- [Development conventions](claude.md).

---

## Prerequisites

| Tool | Minimum version | Notes |
|------|-----------------|-------|
| JDK | 21 | Used to run Gradle |
| Android SDK | API 36 | The app requires Android 8.0 (API 26) or later |
| Android Studio | With Kotlin Multiplatform support | To develop Android from the IDE |
| Xcode | With an iOS Simulator runtime | iOS only; requires macOS |

The project includes targets for Apple Silicon devices and simulators (`iosArm64` and `iosSimulatorArm64`). See the [scope of the iOS validation](docs/ios-desarrollo-local.md#validación-realizada-el-28-de-octubre-de-2026).

---

## Installation

```bash
# 1. Clone the repository
git clone https://github.com/IngenieriaParaLaAlimentacion/app_mobile.git
cd app_mobile

# 2. Create the local configuration
cp local.properties.example local.properties

# 3. Select JDK 21 (macOS)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## Configuration

Fill in `local.properties`, which is ignored by Git:

| Key | Required | Description |
|-----|----------|-------------|
| `sdk.dir` | Yes* | Path to the Android SDK. *Alternative: set `ANDROID_HOME` |
| `api_url_tst` | Yes, for `tst` | Test backend URL, including the trailing slash |
| `api_url_prod` | No | Production backend URL. When empty, it uses the one defined in `composeApp/build.gradle.kts` |
| `web_host_tst`, `web_host_prod` | No | Hosts for the email verification links |

For Android notifications, add the environment's `google-services.json` to `composeApp/src/tst/` or `composeApp/src/prod/`. Android builds that process Google Services need a valid configuration for the chosen package. The [iOS guide](docs/ios-desarrollo-local.md) explains the Firebase setup for that platform. Android signing and distribution use the local files described in the [handover document](docs/traspaso-app-mobile.md).

---

## Running

### Android

The `tst` and `prod` variants use different packages and can coexist without sharing session or local data.

Run each command separately from the repository root:

```bash
# Quick type check (commonMain)
./scripts/compile.sh

# Debug APKs
./gradlew :composeApp:assembleTstDebug --no-daemon
./gradlew :composeApp:assembleProdDebug --no-daemon
```

`scripts/compile.sh` selects JDK 21 on macOS and compiles the `commonMain` metadata. That check does not replace an Android or iOS build. APKs are written to `composeApp/build/outputs/apk/<environment>/debug/`.

> Build one environment per Gradle invocation. BuildKonfig generates a single shared configuration and rejects implicitly selecting both environments. Selection follows this order: `-PtargetEnvironment=tst|prod`, the environment inferred from the task name, and finally `tst`.

### iOS

Open `iosApp/iosApp.xcodeproj`, select `iosApp-tst` or `iosApp-prod` and run on an iPhone Simulator. The schemes explicitly select the backend; `tst` requires `api_url_tst` in `local.properties`.

The simulator does not need an Apple account. The [local iOS development guide](docs/ios-desarrollo-local.md) includes the unsigned build commands and the limits of the testing done.

---

## Tests

With JDK 21 and the Android SDK configured:

```bash
./gradlew :composeApp:testProdDebugUnitTest -PtargetEnvironment=prod --no-daemon
```

The [CI](.github/workflows/ci.yml) workflow runs `commonTest` and `androidUnitTest` on the Android JVM target for pull requests and pushes to `develop` and `main`. It can also be run manually and keeps the HTML and JUnit XML reports even when tests fail. It needs no backend credentials, distribution signing or emulator.

This suite does not run UI tests or tests on the iOS simulator.

---

## Code structure

The app uses Clean Architecture, MVVM, Koin for dependency injection and Navigation Compose for navigation.

Shared code lives in `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/`:

| Folder | Contents |
|--------|----------|
| `app/` | `App`, `AppNavHost`, `AppViewModel` and `AuthedShell`, which handles navigation for the authenticated tabs |
| `core/design_system/` | Theme, typography and reusable components |
| `core/validation/` | Form validation rules |
| `data/` | HTTP clients, DTOs, mappers, repositories, local storage and sync. SQLDelight schemas live in `composeApp/src/commonMain/sqldelight/` |
| `domain/` | Models, repository interfaces, use cases and sync contracts |
| `di/` | Koin modules, with platform implementations through `expect` and `actual` |
| `presentation/feature/` | Screens grouped by feature, with actions, destinations, navigation, state and view models |
| `presentation/navigation/` | Typed routes, external links and navigation results |

`composeApp/src/androidMain/` and `iosMain/` contain the platform-specific implementations. `iosApp/` contains the Swift app and its Xcode project.

To add a screen, follow a similar feature, declare it in `presentation/navigation/Route.kt` and register it in `app/AppNavHost.kt`. The Home, History and Profile tabs are registered in `app/AuthedShell.kt`. Register its dependencies in `di/`.

---

## License

Academic project — **grupo-193**.
