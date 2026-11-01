# Development guide for agents

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ingeniería en Alimentación — Compose Multiplatform mobile app targeting Android and iOS, a
client of the project's REST backend. Field workers and establishment managers use it to
register subjects, measurements (height/weight), surveys and food stock, and to get recipe
proposals. Subjects, measurements and survey answers are stored locally (SQLDelight) and
synced with the backend, so features must keep working offline.

UI strings and validation messages are in Spanish; code (identifiers, comments) and technical
documentation are in English.

### Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (Multiplatform) |
| UI | Compose Multiplatform, KomoUI wrapped by the `Al*` components |
| Navigation | Navigation Compose (typed routes) |
| DI | Koin |
| Networking | Ktor client |
| Local storage | SQLDelight |
| Push | Firebase Cloud Messaging |
| Tests | `kotlin.test` + Turbine, run on the Android JVM target |

Versions live in `gradle/libs.versions.toml`.

### Features

One folder per feature under `presentation/feature/`: `auth`, `get_started`, `register`,
`home`, `history`, `profile`, `subject`, `measurement`, `survey`, `establishment`, `food`.

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
Feature screens generally use the following files. See `presentation/feature/subject/create_subject/` as a reference:
- `*Action` — sealed user actions for that screen, with payload properties when needed; called as `{ onAction(...) }` in the Screen file
- `*Destination` — sealed list of all destinations reachable from this screen, with navigation params when needed
- `*Navigation` — navigation logic
- `*Screen` — the UI
- `*State` — the screen's state model
- `*ViewModel` — holds state, reduces actions to methods, calls use cases, and emits destinations

To add a feature, follow a similar screen, register the route in `presentation/navigation/Route.kt`, wire the screen into `app/AppNavHost.kt`, and register its dependencies in `di/`. The Home, History, and Profile tabs are wired in `app/AuthedShell.kt`; `HomeNavigation.kt` is not the active Home dispatcher.

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

Use the shared input components rather than implementing scroll offsets per screen:

1. Store a named `rememberScrollState()` and use `Modifier.alInputScroll(scrollState)`
   on the form's scrollable container instead of `verticalScroll(scrollState)`.
2. Pass that same `scrollState` and the current `isImeVisible` to `AlInput`,
   `AlPasswordInput`, or `AlUnitInput`.
3. `alInputScroll` records the viewport coordinates. `AlInput` uses the field's
   unclipped position relative to that viewport plus the current scroll value
   when focus and keyboard visibility trigger scrolling. Never use
   `boundsInRoot().top + scrollState.value`: it includes toolbar/safe-area offsets
   and can scroll the focused field above the visible form.

### Navigate-back-with-result to refresh (CMP pattern)

When a child editor changes data, return a one-shot result through the navigation
entry's `SavedStateHandle`. Keep shared keys in `presentation/navigation/NavResults.kt`.
The child emits a success destination only after the mutation succeeds. Its navigation
handler sets the result on `previousBackStackEntry` before calling `popBackStack()`.

The parent observes the result with the existing `RefreshOnNavResult` helper and
refreshes once. The helper clears the result after consumption. See
`presentation/feature/profile/edit/EditProfileNavigation.kt` and
`presentation/feature/profile/view/ProfileScreen.kt`.

Both sides must use the same back stack entry. Profile lives in `AuthedShell`'s inner
NavHost, but EditProfile is pushed on the outer NavController. The editor writes to the
outer controller's previous entry, and Profile reads
`LocalOuterNavController.current.currentBackStackEntry?.savedStateHandle`.
Do not add a refresh on every lifecycle resume for this flow.

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
the web-host keys are optional overrides. A `tst` artifact still requires `api_url_tst`. The
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

A blank API URL hard-fails any `assemble`/`bundle`/`install`/`embedAndSign` task and only warns for bare compile
tasks, so `scripts/compile.sh` and CI type checks stay green without a `local.properties`.

Both flavors currently claim the same `ingenieriaalimentacion://` deep-link scheme, so with both
installed Android shows a chooser. Suffixing the tst scheme requires the tst backend to emit the
suffixed reset-password link first — otherwise password reset breaks silently.

iOS has a checked-in Xcode project at `iosApp/iosApp.xcodeproj`. Shared schemes
`iosApp-tst` and `iosApp-prod` select DebugTst/ReleaseTst and DebugProd/ReleaseProd.
`iosApp/Configuration/{Tst,Prod}.xcconfig` sets `APP_ENVIRONMENT` and distinct bundle IDs;
`scripts/build-ios-framework.sh` passes that environment to Gradle explicitly. Custom
configurations set `KOTLIN_FRAMEWORK_BUILD_TYPE` to Debug or Release. Build environments
sequentially. Simulator builds do not need an Apple account; see `docs/ios-local-development.md`.

## Documentation

Project documentation for the team lives in `docs/`; the entry points are `README.md`
(Spanish: `README.es.md`) and `CONTRIBUTING.md`. Keep temporary plans, research notes, and
PR drafts in the git-ignored `.context/` directory.

Technical docs are written in English. Docs aimed at the client or non-technical users are in
Spanish. Do not keep the same document in both languages (the READMEs are the only exception).

| Document | Language | Read it when |
|---|---|---|
| `CONTRIBUTING.md` | English | Branch names, commit format, PR rules, CI |
| `docs/ios-local-development.md` | English | Building and running iOS, environments, validation scope |
| `docs/ios-push-notifications.md` | English | Firebase, APNs, and signing for iOS push |
| `docs/recipe-recommendations.md` | English | Recipe flow and its backend API dependency |
| `docs/traspaso-app-mobile.md` | Spanish | Handover, distribution channels, release procedure, store publishing |
| `docs/guia-instalacion-testers.md` | Spanish | Install/update guide for testers (non-technical) |
| `docs/manual-usuario/` | Spanish | End-user manual; see its `mantenimiento.md` to update it |

When you add, rename, or remove a document under `docs/`, update this table and the
documentation list in both READMEs.

## Git workflow

Gitflow-lite with two long-lived branches:

- `develop` — integration branch. All feature/fix branches start from `develop` and merge back
  into `develop` via PR. Base new branches on `develop`, never on `main`.
- `main` — release branch. It only receives **release PRs from `develop`** (`develop` → `main`);
  never merge feature branches or commit directly to `main`.

When creating a PR, target `develop` unless the user explicitly asks for a release, in which case
open a `develop` → `main` PR.

Branch prefixes (`feat/*`, `feature/*`, `fix/*`, `refactor/*`, `docs/*`, `chore/*`) are enforced by
CI, and commits follow Conventional Commits. See `CONTRIBUTING.md`.

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
Tests live in `commonTest` and `androidUnitTest`, mirroring the source packages
(`app`, `core`, `data`, `domain`, `presentation`). Shared fakes are in the `fake` package: reuse
them instead of mocking. View model flows are tested with Turbine. There are no UI tests and no
iOS simulator tests; CI runs this same command.

### iOS
Open `iosApp/iosApp.xcodeproj` and select `iosApp-tst` or `iosApp-prod` with an
Apple Silicon iPhone Simulator. See [local iOS development](docs/ios-local-development.md)
for unsigned CLI builds and [push notification setup](docs/ios-push-notifications.md)
for Firebase and APNs provisioning.
