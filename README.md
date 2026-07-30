# Ingeniería en Alimentación — Mobile App

Compose Multiplatform app (Android + iOS).

## Requirements

- JDK 21 (Gradle uses it; `JAVA_HOME` is set automatically by `./scripts/compile.sh`)
- Android Studio with Kotlin Multiplatform plugin
- Xcode 15+ for iOS

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

iOS has no environment split yet, and no Xcode project is checked in.

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
