# Ingeniería en Alimentación — Mobile App

Compose Multiplatform app (Android + iOS).

## Requirements

- JDK 21 (Gradle uses it; `JAVA_HOME` is set automatically by `./scripts/compile.sh`)
- Android Studio with Kotlin Multiplatform plugin
- Xcode 15+ for iOS

## Build

```bash
./scripts/compile.sh                                      # quick common-main type check
./gradlew :composeApp:assembleDebug --no-daemon           # Android APK
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator.

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
