# Ingeniería en Alimentación — Mobile App

Compose Multiplatform app (Android + iOS).

## Requirements

- JDK 21 (Gradle uses it; `JAVA_HOME` is set automatically by `./scripts/compile.sh`)
- Android Studio with Kotlin Multiplatform plugin
- Xcode with an iOS Simulator runtime (local iOS build verified with Xcode 26.3)

## Setup

Copy `local.properties.example` to `local.properties` and fill in `api_url_tst`. It is
git-ignored and holds the per-environment backend URLs.

## Build

The app ships in two environments, `tst` and `prod`, as Android product flavors. The `tst`
build installs alongside `prod` under its own application id (`…ingenieriaalimentacion.tst`),
so the two never share the offline queue or the stored session.

```bash
./scripts/compile.sh                                       # quick common-main type check
./gradlew :composeApp:assembleTstDebug --no-daemon         # test-environment APK
./gradlew :composeApp:assembleProdDebug --no-daemon        # production APK
```

Build one environment per Gradle invocation — requesting both at once fails fast, because
only one environment can be baked into the generated config. See CLAUDE.md for the details.

iOS: open `iosApp/iosApp.xcodeproj`, select `iosApp-tst` or `iosApp-prod`, and run on an
iPhone Simulator. No Apple account is needed for the simulator. The schemes select the
backend environment explicitly; `tst` requires `api_url_tst` in `local.properties`.
See [local iOS development](docs/ios-desarrollo-local.md) for setup and unsigned CLI builds.

## Handoff and distribution

- [Traspaso de la app mobile y alternativas de distribución](docs/traspaso-app-mobile.md) — current delivery, account handoff, Android/iOS options, costs, and optional store publication.
- [Cómo instalar la app de prueba](docs/guia-instalacion-testers.md) — Android installation and updates through Firebase App Distribution.
- [Compilar y ejecutar iOS sin una cuenta Apple](docs/ios-desarrollo-local.md) — Xcode schemes, simulator builds, and validation scope.

## Tests

GitHub Actions runs `commonTest` and `androidUnitTest` on the Android JVM target
for pull requests and pushes to `develop` and `main`. The **CI** workflow
also supports manual runs and uploads HTML and JUnit XML reports, including on test
failure. It does not require backend credentials, release signing, or an emulator.

To run the same suite locally (JDK 21 and Android SDK required):

```bash
./gradlew :composeApp:testProdDebugUnitTest -PtargetEnvironment=prod --no-daemon
```

This suite does not run iOS simulator tests or UI tests.

## Architecture

Clean Architecture + MVVM with Koin for DI and jetbrains-navigation-compose for navigation.

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/`
  - `app/` — root `App()` composable, `AppNavHost`, `AppViewModel`
  - `core/design_system/` — `AlTheme`, color/spacing/typography tokens, components
  - `data/`, `domain/` — placeholders for layered code
  - `di/` — Koin modules (`platformModule` is `expect`/`actual`)
  - `presentation/feature/` — one folder per feature, six files: `*Action`, `*Destination`, `*Navigation`, `*Screen`, `*State`, `*ViewModel`
  - `presentation/navigation/Route.kt` — sealed `Route` registry

To add a feature, copy `presentation/feature/home/` and rename, then register the route in `Route.kt` and the screen in `app/AppNavHost.kt`.
