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

### Navigate-back-with-result to refresh (CMP pattern)
When a child editor mutates server data (e.g. `EditProfile` PATCHes `/me`) and the
parent that opened it must reflect the change after `popBackStack()`, push a
**one-shot result** through the back stack entry's `SavedStateHandle` instead of
blanket-refreshing on every `ON_RESUME`. The parent then refreshes only on a real
success — never when the user just opened the editor and backed out. This is how the
`../extra` CMP project (`profile_home` ← `enter_bio`, `listing_detail` ← `edit_listing`)
does it.

**Result keys.** Keep the keys as shared `const val`s in one file so producer and
consumer agree on the string (extra: `presentation/common/Results.kt`):
```kotlin
const val PROFILE_UPDATED_RESULT = "PROFILE_UPDATED_RESULT"
```

**1. Producer — the child editor.** Distinguish a plain back from a successful save in
the `*Destination` (`EditProfile` already has `data object Back` and `data object Saved`),
and emit `Saved` *only* in the PATCH success branch of the ViewModel:
```kotlin
updateMyProfile(update).fold(
    onSuccess = { _state.update { it.copy(isSaving = false, destination = EditProfileDestination.Saved) } },
    onFailure = { error -> _state.update { it.copy(isSaving = false, saveError = error) } },
)
```
Write the result into the **previous** back stack entry's `savedStateHandle` right
before popping, in the `*Navigation` handler:
```kotlin
when (destination) {
    EditProfileDestination.Back -> navController.popBackStack()
    EditProfileDestination.Saved -> {
        navController.previousBackStackEntry?.savedStateHandle?.set(PROFILE_UPDATED_RESULT, true)
        navController.popBackStack()
    }
}
```

**2. Consumer — the parent screen root.** Collect the key as a `StateFlow`, refresh,
then `remove` it so it fires once (consume-once):
```kotlin
LaunchedEffect(entry) {
    entry.savedStateHandle
        .getStateFlow<Boolean?>(PROFILE_UPDATED_RESULT, null)
        .collect { result ->
            if (result != null) {
                viewModel.refresh()
                entry.savedStateHandle.remove<Boolean?>(PROFILE_UPDATED_RESULT)
            }
        }
}
```

**Which back stack entry? (important for the Profile case).** The producer's write
target and the consumer's read target must be the **same** entry. In `../extra` the
parent and child share one NavController, so it is simply
`navController.currentBackStackEntry`. In *this* app the Profile tab lives inside
`AuthedShell`'s **inner** `NavHost`, while `EditProfile` is pushed on the **outer** nav
(`LocalOuterNavController`). So `EditProfile`'s `previousBackStackEntry` is the
**AuthedShell** outer-nav entry — not the Profile inner-nav entry. Both sides must use
the outer nav's shared entry: the producer writes to `outerNav.previousBackStackEntry`
(already the case, since `editProfileScreen` receives the outer `navController`), and
`ProfileScreenRoot` must read from `LocalOuterNavController.current.currentBackStackEntry`,
not its inner entry.

Adopting this lets `ProfileScreenRoot` drop the `LifecycleResumeEffect` +
`skipFirstResume` workaround (which re-fetches `/me` on every tab re-entry); the result
key refreshes only after a confirmed save.

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
