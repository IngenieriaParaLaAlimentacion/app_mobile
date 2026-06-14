# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ingeniería en Alimentación — Compose Multiplatform mobile app targeting Android and iOS.

### Components
- `/composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/` — shared Kotlin source
  - `app/` — root `App()` composable, `AppNavHost`, `AppViewModel`
  - `core/design_system/` — `AlTheme`, color/spacing/typography tokens, reusable components. Every color and composable should come from here if it already exists.
  - `data/` — Data layer (DTOs, mappers, clients, repository implementations)
  - `domain/` — Domain layer (`model/`, `usecase/`, `repository/` interfaces)
  - `presentation/` — Screens, view models, UI; one folder per feature under `presentation/feature/`
  - `presentation/navigation/Route.kt` — sealed `Route` registry
  - `di/` — Koin modules (`platformModule` is `expect`/`actual`)
- `/composeApp/src/androidMain/` — Android-specific source
- `/composeApp/src/iosMain/` — iOS-specific source
- `/iosApp/` — Xcode project for the iOS app shell

## Technical guidelines

CLEAN architecture with separation between data, domain, and presentation layers.
MVVM with use cases; the use case can be skipped for direct repository usage in the view model when there is no business logic to add.
Use cases orchestrate repository calls and add business logic, keeping it out of view models.
Dependency injection is handled with Koin.

### On presentation
Each feature screen has six files (see `presentation/feature/home/` as the reference):
- `*Action` — sealed user actions for that screen, with payload properties when needed; called as `{ onAction(...) }` in the Screen file
- `*Destination` — sealed list of all destinations reachable from this screen, with navigation params when needed
- `*Navigation` — navigation logic
- `*Screen` — the UI
- `*State` — the screen's state model
- `*ViewModel` — holds state, reduces actions to methods, calls use cases, and emits destinations

To add a new feature, copy `presentation/feature/home/`, rename, then register the route in `presentation/navigation/Route.kt` and wire the screen into `app/AppNavHost.kt`.

### On domain
Models, use cases, repository interfaces, utils, and constants. No dependencies on `data` or `presentation`.

### On data
DTOs for HTTP request/response bodies, mappers, repository implementations, and clients.

### Icons
This project uses **MingCute** icons, NOT Material Design icons. There is no
`compose.materialIconsExtended` dependency and `androidx.compose.material.icons.*`
(`Icons.Default.*`, `Icons.AutoMirrored.*`, etc.) must not be used.

Icons are Android Vector Drawables under
`composeApp/src/commonMain/composeResources/drawable/`, named `ic_mingcute_<name>.xml`,
and rendered from common code via `vectorResource(Res.drawable.ic_mingcute_<name>)`.
Their fill is `#FF000000`, so always pass a `tint` at the call site (see
`AlPasswordInput`, `AlBackTopAppBar`, `AlListRow`).

```kotlin
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_home_line
import org.jetbrains.compose.resources.vectorResource

Icon(
    imageVector = vectorResource(Res.drawable.ic_mingcute_home_line),
    contentDescription = "...",
    tint = AlTheme.colors.textMuted,
)
```

To add a new icon, use the `add-mingcute-icon` skill (runs the deterministic
driver) and rebuild once so the `Res.drawable.*` accessor is generated.

### Scroll-to-field on keyboard open (CMP pattern)
Do NOT use `BringIntoViewRequester` — it is experimental and unreliable on iOS in Compose Multiplatform. Use `ScrollState` directly:

1. Store a named `rememberScrollState()` and pass it to `verticalScroll(scrollState)`.
2. Track the field's position with `onGloballyPositioned`:
   ```kotlin
   .onGloballyPositioned { coordinates ->
       fieldScrollOffset = coordinates.boundsInRoot().top.toInt() + scrollState.value
   }
   ```
3. Combine IME visibility with focus state to trigger the scroll:
   ```kotlin
   LaunchedEffect(isFocused, isImeVisible) {
       if (isFocused && isImeVisible) {
           scrollState.animateScrollTo(fieldScrollOffset)
       }
   }
   ```

## Commands

The project requires Java 21. `scripts/compile.sh` sets `JAVA_HOME` automatically.

### Quick type check (commonMain)
```bash
./scripts/compile.sh
```

### Android build
```bash
./gradlew :composeApp:assembleDebug --no-daemon
```

### iOS
Open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator.
