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

### Theme (colors and type)

The palette is the shared "Raíz" brand palette, mirrored from the web app: warm paper
surfaces, ink text, a dark-earth brand anchor, sage greens, mustard for the primary
action and brick for alerts. Three layers, in order:

- `theme/Palette.kt` — the only literal hex values in the codebase (`AlPalette`).
  Colors marked "derived" are mobile-only tints of the paper/line pair.
- `theme/Color.kt` + `theme/Theme.kt` — `AlColors`, the semantic roles screens consume
  through `AlTheme.colors.*`. Never reach for `AlPalette` outside the theme package.
- `theme/KomoStyles.kt` — the same palette expressed as KomoUI's `KomoStyles` contract
  plus a Material 3 fallback scheme, so the KomoUI primitives under the `Al*` wrappers
  (Button, Input, Card, Checkbox, Dialog…) inherit the brand instead of shadcn neutrals.

`AlTheme` configures KomoTheme itself, so **wrap the app in `AlTheme` only — never in
`KomoTheme` directly**. Dark mode is pinned off: the palette is light-only.

Three type families, all in `theme/Type.kt`:

| Role | Family | `AlTheme.typography.*` |
|---|---|---|
| Display / brand, screen titles | Fraunces | `display`, `title` |
| Body and UI | Work Sans | `subtitle`, `body`, `bodyStrong`, `label`, `caption` |
| Data — indicator numbers, readings, stamped dates | IBM Plex Mono | `dataLarge`, `data`, `dataSmall` |

The same split is mapped onto Material's scale (display/headline → Fraunces, everything
else → Work Sans) so untokenized Material and KomoUI text still lands on the right face.
Use the mono roles for numbers that are read as data, not for numbers inside a sentence.

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

### Blur-aware form validation (pattern)
Form fields validate per-field with a UX rule: **never show an error while the
user is editing the field**; surface it only after they leave it (blur), and
only when the field has content that breaks a rule. Empty-but-touched fields stay
quiet; `Submit` force-shows every error (including "required"). The reusable
pieces live in `core/validation/`:

- `TextFieldState` — `value` + `isFocused` + `hasBlurred` + `forceShow` + `error`.
  `error` is recomputed eagerly (so `isValid` is always current); `displayError`
  decides *when* to render it. Update it from the ViewModel with `onChange`,
  `onFocus`, and `showError` (submit).
- `Rules` — composable `Validator` builders (`required`, `maxLength`, `minLength`,
  `matches`, `digitsInRange`) combined with `Rules.of(...)` (first failure wins).
  Content rules pass on blank, so `required` owns presence.
- `AlInput` exposes `onFocusChange: (Boolean) -> Unit`; feed it into
  `TextFieldState.onFocus`. The initial unfocused state is never reported, so the
  blur callback only fires after a real focus.

Wiring (see `presentation/feature/subject/create_subject/` as the reference):
```kotlin
// State: one TextFieldState per text field
data class FooState(val dni: TextFieldState = TextFieldState(), ...)

// ViewModel: one Validator per field, one-line reducers
private val dniValidator: Validator = Rules.of(
    Rules.required("Ingresá el DNI."),
    Rules.digitsInRange(1..99_999_999, "DNI inválido."),
)
is FooAction.DniChanged -> _state.update { it.copy(dni = it.dni.onChange(action.value, dniValidator)) }
is FooAction.DniFocusChanged -> _state.update { it.copy(dni = it.dni.onFocus(action.focused, dniValidator)) }
// submit(): re-validate + force-show, then build the model only if every field isValid
val validated = _state.value.copy(dni = _state.value.dni.showError(dniValidator), ...)

// Screen:
AlInput(
    value = state.dni.value,
    onValueChange = { onAction(FooAction.DniChanged(it)) },
    onFocusChange = { onAction(FooAction.DniFocusChanged(it)) },
    error = state.dni.displayError,
)
```
Non-text fields (e.g. a `Sex` selector) keep a plain `String?` error set on submit.

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

## Environments (tst / prod)

Two environments, `tst` and `prod`. Per-environment values live in the git-ignored
`local.properties` (`api_url_tst`, `api_url_prod`); copy `local.properties.example` to get
started. `api_url_prod` falls back to the known production host, `api_url_tst` has no default.

**BuildKonfig is the single carrier.** The `buildkonfig` block in `composeApp/build.gradle.kts`
reads `local.properties` and generates one `internal object AlEnv` into **commonMain**.
`data/network/ApiConfig` is the only reader of `AlEnv`, so adding a new environment value means
editing Gradle plus `ApiConfig` and nothing else. Do not read `AlEnv` directly elsewhere.

`web_host_tst` / `web_host_prod` carry the backoffice **web** host of each environment (bare
host). Unlike the API URL, both have a default committed in `composeApp/build.gradle.kts`, so
the `local.properties` keys are optional overrides and a fresh clone builds either flavor. The
host feeds two places that must agree: the `webHost` manifest placeholder of each flavor (the
`autoVerify` App Link filter for `https://<host>/verify-email`) and `ApiConfig.WEB_HOST` (the
`navDeepLink` pattern in `presentation/navigation/DeepLinks.kt`). It must match the host of the
backend's `BACKOFFICE_DEEPLINK_BASE`, and that origin must serve `/.well-known/assetlinks.json`.

**Android flavors** (`flavorDimensions "environment"`) give `tst` an `applicationIdSuffix = ".tst"`
and a `-tst` version suffix, so both builds coexist on one device. That suffix is what keeps the
SQLDelight offline queue and the auth prefs sandboxed per environment — without it, queued writes
could flush to the wrong backend. `app_name` and `deep_link_scheme` are per-flavor `resValue`s;
they are deliberately **not** in `res/values/strings.xml`, which would shadow them.

Environment resolution, in order: `-PtargetEnvironment=tst|prod` → inferred from the requested
Android variant task (`assembleTstDebug` → `tst`) → `tst` fallback (never silently prod).

Two guardrails worth knowing before you touch that block:
- `generateBuildKonfig` pins `targetEnvironment` as a task input. Without it, flipping
  environments can leave the task `UP-TO-DATE` and bake a **stale** env into the build.
- Only one environment can be baked per Gradle invocation, so requesting both flavors at once
  (`./gradlew assembleTstDebug assembleProdDebug`) fails fast rather than shipping the wrong URL.

A blank API URL hard-fails any `assemble`/`bundle`/`install` task and only warns for bare compile
tasks, so `scripts/compile.sh` and CI type checks stay green without a `local.properties`.

Both flavors currently claim the same `ingenieriaalimentacion://` deep-link scheme, so with both
installed Android shows a chooser. Suffixing the tst scheme requires the tst backend to emit the
suffixed reset-password link first — otherwise password reset breaks silently.

iOS has a checked-in Xcode project at `iosApp/iosApp.xcodeproj`. Shared schemes
`iosApp-tst` and `iosApp-prod` select DebugTst/ReleaseTst and DebugProd/ReleaseProd.
`iosApp/Configuration/{Tst,Prod}.xcconfig` sets `APP_ENVIRONMENT` and distinct bundle IDs;
`scripts/build-ios-framework.sh` passes that environment to Gradle explicitly. Custom
configurations set `KOTLIN_FRAMEWORK_BUILD_TYPE` to Debug or Release. Build environments
sequentially. Simulator builds do not need an Apple account; see `docs/ios-desarrollo-local.md`.

## Git workflow

Gitflow-lite with two long-lived branches:

- `develop` — integration branch. All feature/fix branches start from `develop` and merge back
  into `develop` via PR. Base new branches on `develop`, never on `main`.
- `main` — release branch. It only receives **release PRs from `develop`** (`develop` → `main`);
  never merge feature branches or commit directly to `main`.

When creating a PR, target `develop` unless the user explicitly asks for a release, in which case
open a `develop` → `main` PR.

## Commands

The project requires Java 21. `scripts/compile.sh` sets `JAVA_HOME` automatically.

### Quick type check (commonMain)
```bash
./scripts/compile.sh
```

### Android build
Every variant is flavored — there is no plain `assembleDebug`.
```bash
./gradlew :composeApp:assembleTstDebug --no-daemon
./gradlew :composeApp:assembleProdDebug --no-daemon
```

### Tests
```bash
./gradlew :composeApp:testProdDebugUnitTest --no-daemon
```

### iOS
No Xcode project is checked in yet, so the app cannot be run on a simulator from this repo.
