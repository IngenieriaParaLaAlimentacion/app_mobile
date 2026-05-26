# Authed Shell + Bottom Navigation (Home / Settings) Implementation Plan

> **Implementation note (Phase 3):** the plan assumed `Icons.Default.Home`/`Icons.Default.Settings` ship transitively with `compose.material`. They don't — `material-icons-core` is a separate artifact in CMP. Resolved by adding `implementation(compose.materialIconsExtended)` to `composeApp/build.gradle.kts`.

## Overview

Introduce a persistent bottom navigation bar that is visible only inside the authenticated portion of the app. Initially two tabs: **Home** and **Settings**. The Settings screen contains a single **Logout** button that calls the existing `SignOutUseCase`, which posts to `POST /api/v1/auth/logout`. As part of this work, the logout request body is fixed to conform to the server contract (`{device_id, refresh_token}`), which is currently missing and silently 400-ing server-side.

The bottom bar is modeled as part of the authed area via a nested `NavHost`. A new outer route `Route.AuthedShell` owns a shell composable that renders the `AlBottomNavBar` plus an inner `NavHost` containing `Home` and `Settings`. Login redirects to `AuthedShell`, logout pops back to `GetStarted`.

## Current State Analysis

- Navigation is a single flat `NavHost` in `app/AppNavHost.kt:17-34`. All screens (`GetStarted`, `Home`, `Register`, `Login`, etc.) are registered at the root. No nested graphs exist yet.
- The decision of where the user starts on app launch is made in `app/App.kt:43-47` via `startDestinationFor(sessionStatus)` — `Authenticated → Route.Home`, everything else `→ Route.GetStarted`. **`startDestination` on `NavHost` is only honored on first composition**, so flipping `sessionStatus` does not re-mount the graph; sign-out navigation must be triggered explicitly.
- `SessionStatus` (`domain/model/auth/SessionStatus.kt:3-11`) already encodes `NotAuthenticated(isSignOut = true)` distinct from refresh failure — useful but not load-bearing here.
- `AuthRepositoryImpl.signOut()` (`data/auth/AuthRepositoryImpl.kt:52-58`) already:
  - Best-effort POSTs `api/v1/auth/logout` (currently **without a body** — server returns 400, swallowed by `runCatching`).
  - Deletes session via `SessionManager`.
  - Clears Ktor `BearerAuthProvider` cache.
  - Sets `statusSink.value = SessionStatus.NotAuthenticated(isSignOut = true)`.
- `SignOutUseCase` already exists (`domain/usecase/auth/SignOutUseCase.kt:5-7`) and is registered (`di/UseCasesModule.kt:17`).
- `DeviceIdProvider` is already in DI (`di/RepositoriesModule.kt`) and `UserSession.refreshToken` is available on the stored session — both required for the corrected logout body.
- OpenAPI (`/openapi.json`) confirms `POST /api/v1/auth/logout` requires `{"device_id": string, "refresh_token": string}` → `204 No Content` on success.
- Per-feature MVVM convention (6 files) is documented in `CLAUDE.md` and consistently followed in every existing feature (`presentation/feature/home/` is the canonical reference).
- Design system (`core/design_system/`) currently exposes only `AlButton`, `AlScaffold`, `AlSuccessTick`, plus tokens (`AlColors`, `AlSpacing`, `AlTypography`, `AlCorners`, `AlBorders`). No bottom-bar primitive.
- Dependencies (`gradle/libs.versions.toml` + `composeApp/build.gradle.kts`): `compose.material3`, `compose.material`, `compose.foundation`, `navigation-compose`, `koin-compose-viewmodel`, `jetbrains-lifecycle-runtime-compose` are all available. Basic Material icons (`Icons.Default.Home`, `Icons.Default.Settings`) ship with `compose.material`. **No `material-icons-extended` dependency** — only the core icon set is available.

### Key Discoveries
- `signOut()` body is missing (`data/auth/AuthRepositoryImpl.kt:54`) — server-side invalidation never actually succeeds today.
- Login's "clear back stack" idiom (`presentation/feature/auth/login/LoginNavigation.kt:25-30`) — `popUpTo(0) { inclusive = true }` is the project's chosen pattern for switching auth state; we reuse it for both login→shell and shell→get-started.
- No `currentBackStackEntryAsState` usage anywhere — nested `NavHost` is also unprecedented. Both are introduced cleanly inside the shell.
- Two theming layers wrap content (`app/App.kt:29-30`): `KomoTheme` + `AlTheme`. Settings screen and bottom bar will use `AlTheme` tokens consistently (matching `Home`, not the Komo-styled auth screens).

## Desired End State

- After a successful login, the user lands inside `AuthedShell` showing the Home tab; switching tabs swaps the inner `NavHost` content without leaving the shell.
- The bottom bar is **not** visible during `GetStarted`, `Login`, `Register*`, `ForgotPassword`, or `ResetPassword*` flows.
- The Settings tab renders a button labeled **Logout** that, on tap:
  1. Disables itself while in flight.
  2. Calls `SignOutUseCase()`.
  3. On completion, navigates the outer `NavController` to `Route.GetStarted` clearing the back stack.
- The HTTP call to `/api/v1/auth/logout` carries `{device_id, refresh_token}` and returns 204 in the happy path.
- Compilation passes via `./scripts/compile.sh`.
- Android debug build assembles via `./gradlew :composeApp:assembleDebug --no-daemon`.

## What We're NOT Doing

- No real Settings content beyond the Logout button (no profile editing, notifications, theme toggle, etc.).
- No animations / transitions between tabs beyond Compose defaults.
- No badge counts, deep-linking, or back-press handling beyond Compose Navigation defaults.
- No iOS-specific styling beyond what `AlTheme` already provides.
- No tests authored in this plan (per `CLAUDE.md` this codebase has no test scaffolding yet for features and adding it is out of scope).
- No refactor of the two-theme stack (`KomoTheme` + `AlTheme`).
- No saved-state restoration across tab swaps (`saveState = true`) — tab switches reset child state for now; can be added later if needed.

## Implementation Approach

Four phases, ordered so each is independently compilable:

1. **Fix the logout request body** in the data layer. Independent of any UI work and makes the server actually invalidate the session.
2. **Settings feature scaffold** following the 6-file MVVM convention. Adds `Route.Settings` to the registry but does not wire it into the nav host yet — registration happens in Phase 4.
3. **`AlBottomNavBar` design-system component**. Pure UI; can be previewed and dropped anywhere.
4. **`AuthedShell` + nested NavHost**. Wire `Route.AuthedShell` into the outer host, move `Home` and `Settings` registration into the inner host, redirect login success and `startDestinationFor` to `AuthedShell`, and wire Settings logout to pop back to `GetStarted`.

---

## Phase 1: Fix Logout Request Body

### Overview
The server requires `{device_id, refresh_token}` in the body of `POST /api/v1/auth/logout`. Today the call sends no body and silently 400s. Move the HTTP call into `AuthApi` (consistent with every other auth endpoint), build a `LogoutRequest` DTO, and pass the required values.

### Changes Required

#### 1. New DTO
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/dto/LogoutRequest.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.auth.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogoutRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("refresh_token") val refreshToken: String,
)
```

#### 2. AuthApi gains a `logout` method
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthApi.kt`

Note: `AuthApi` is wired to the no-auth `refresh` client today. The logout endpoint per OpenAPI does not require `Authorization`, so it correctly belongs on the same client. Add:

```kotlin
suspend fun logout(refreshToken: String, deviceId: String) {
    refreshClient.post("api/v1/auth/logout") {
        contentType(ContentType.Application.Json)
        setBody(LogoutRequest(deviceId = deviceId, refreshToken = refreshToken))
    }
}
```

#### 3. `AuthRepositoryImpl.signOut()` uses the new API method
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt`

Replace lines 52-58 with a version that reads the current `UserSession` (so we have `refreshToken`) and the device id from `DeviceIdProvider`, then calls `api.logout(...)`. Always proceed to local cleanup even if the network call fails (keeps existing best-effort behavior). The authed `httpClient` field is no longer needed for this call but remains needed for `clearBearerCache()`.

```kotlin
override suspend fun signOut() {
    val session = (statusSink.value as? SessionStatus.Authenticated)?.session
    val refreshToken = session?.refreshToken
    if (refreshToken != null) {
        val deviceId = deviceIdProvider.getOrCreate()
        runCatching { api.logout(refreshToken, deviceId) }
    }
    sessionManager.deleteSession()
    clearBearerCache()
    statusSink.value = SessionStatus.NotAuthenticated(isSignOut = true)
}
```

### Success Criteria

#### Automated Verification
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Android debug builds: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification
- [ ] Trigger sign-out (after Phase 4 lands) and inspect device logs / proxy: the `POST /api/v1/auth/logout` request body contains both `device_id` and `refresh_token` and the response is `204 No Content`.
- [ ] Killing the network mid-logout still results in a fully logged-out local state.

---

## Phase 2: Settings Feature Scaffold

### Overview
Add the Settings feature by copying the `home/` 6-file pattern. The screen has one action — `SignOutClick` — and one destination — `LoggedOut` (consumed by the outer NavController in Phase 4 to navigate to `GetStarted`). The VM owns a single boolean `isSigningOut` to disable the button while the call is in flight.

### Changes Required

#### 1. Route entry
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`

Add alongside the existing entries:

```kotlin
@Serializable
@SerialName("settings")
data object Settings : Route()
```

#### 2. Six feature files
**Directory**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/settings/` (new)

`SettingsAction.kt`:
```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.settings

sealed interface SettingsAction {
    data object SignOutClick : SettingsAction
}
```

`SettingsDestination.kt`:
```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.settings

sealed class SettingsDestination {
    data object LoggedOut : SettingsDestination()
}
```

`SettingsState.kt`:
```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.settings

data class SettingsState(
    val isSigningOut: Boolean = false,
    val destination: SettingsDestination? = null,
)
```

`SettingsViewModel.kt`:
```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.domain.usecase.auth.SignOutUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), _state.value)

    fun onAction(action: SettingsAction) {
        when (action) {
            SettingsAction.SignOutClick -> performSignOut()
        }
    }

    fun clearDestination() {
        _state.value = _state.value.copy(destination = null)
    }

    private fun performSignOut() {
        if (_state.value.isSigningOut) return
        _state.value = _state.value.copy(isSigningOut = true)
        viewModelScope.launch {
            signOut()
            _state.value = _state.value.copy(
                isSigningOut = false,
                destination = SettingsDestination.LoggedOut,
            )
        }
    }
}
```

`SettingsScreen.kt`: follow the `HomeScreen.kt` two-composable split (`SettingsScreenRoot` collects state + runs `LaunchedEffect(state.destination)` calling `onLoggedOut`, then `clearDestination()`; `SettingsScreen` is stateless and shows a centered `AlButton` labeled "Logout"). The `SettingsScreenRoot` accepts an `onLoggedOut: () -> Unit` callback so the outer NavController can pop. Use `AlScaffold` + `AlTheme.spacing` for layout.

`SettingsNavigation.kt`: per project convention, exposes a `navigateToSettings()` extension and a `settingsScreen(navController)` graph builder — though in Phase 4 the graph registration lives in the **inner** `NavHost` inside `AuthedShell`, not the outer one. Provide both anyway for consistency with `HomeNavigation.kt` etc. The `navigate(SettingsDestination, ...)` reducer in this file is a no-op because `LoggedOut` is consumed by the outer host via the `onLoggedOut` callback passed into `SettingsScreenRoot`.

#### 3. Koin registration
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`

Add (using `viewModelOf` pattern from other VMs):

```kotlin
viewModelOf(::SettingsViewModel)
```

### Success Criteria

#### Automated Verification
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Android debug builds: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification
- [ ] (Deferred to Phase 4 — Settings is not yet routable.)

---

## Phase 3: `AlBottomNavBar` Design-System Component

### Overview
Add a project-themed bottom navigation bar to the design system. Pure UI, no state — caller passes `selectedTab` and `onTabSelected`. Two tabs hard-coded for now via a sealed `AlBottomNavTab` enum/sealed list local to the component; structure leaves room for adding more later. Visuals use `AlTheme` tokens (surface, accent, textBase/textMuted, spacing). Icons use `androidx.compose.material.icons.Icons.Default.Home` and `Icons.Default.Settings` from `compose.material`. Selected tab uses `AlTheme.colors.accent`; unselected uses `AlTheme.colors.textMuted`.

### Changes Required

#### 1. The component
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/navbar/AlBottomNavBar.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.components.navbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import androidx.compose.foundation.clickable

enum class AlBottomNavTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Settings("Settings", Icons.Default.Settings),
}

@Composable
fun AlBottomNavBar(
    selected: AlBottomNavTab,
    onSelected: (AlBottomNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AlTheme.colors.surfaceBase)
            .height(64.dp)
            .padding(horizontal = AlTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlBottomNavTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint = if (isSelected) AlTheme.colors.accent else AlTheme.colors.textMuted
            Column(
                modifier = Modifier
                    .clickable { onSelected(tab) }
                    .padding(AlTheme.spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = tab.label,
                    style = AlTheme.typography.caption,
                    color = tint,
                )
            }
        }
    }
}
```

Notes:
- `compose.material` exposes `androidx.compose.material.icons.Icons.Default.*` (Home, Settings) via `material-icons-core` shipped transitively — no extra dependency needed.
- A subtle top border (`AlTheme.borders.thin` + `AlTheme.colors.border`) can be added if desired; omitted here for simplicity.

### Success Criteria

#### Automated Verification
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Android debug builds: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification
- [ ] (Deferred to Phase 4 — component is unused until then.)

---

## Phase 4: `AuthedShell` + Outer/Inner NavHost Wiring

### Overview
Introduce `Route.AuthedShell` as the single outer entry point into the authenticated area. The shell composable owns its own `NavHostController` and renders a `Scaffold(bottomBar = AlBottomNavBar(...))` with an inner `NavHost(startDestination = Route.Home)` containing `Home` and `Settings`. Tab presses navigate inside the inner host with `popUpTo(graph.startDestinationId) { saveState = false } launchSingleTop = true`. Settings logout flows out of the shell via an `onSignOut` callback the outer host passes in, which then runs `outerNavController.navigate(Route.GetStarted) { popUpTo(0) { inclusive = true } }`.

### Changes Required

#### 1. Add `Route.AuthedShell`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`

```kotlin
@Serializable
@SerialName("authed_shell")
data object AuthedShell : Route()
```

#### 2. New shell composable
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AuthedShell.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.components.navbar.AlBottomNavBar
import ar.com.ingenieriaalimentacion.core.design_system.components.navbar.AlBottomNavTab
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeScreenRoot
import ar.com.ingenieriaalimentacion.presentation.feature.settings.SettingsScreenRoot
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun AuthedShell(onSignOut: () -> Unit) {
    val innerNav = rememberNavController()
    val entry by innerNav.currentBackStackEntryAsState()
    val selectedTab = when {
        entry?.destination?.hasRoute(Route.Settings::class) == true -> AlBottomNavTab.Settings
        else -> AlBottomNavTab.Home
    }

    Scaffold(
        bottomBar = {
            AlBottomNavBar(
                selected = selectedTab,
                onSelected = { tab ->
                    val target: Route = when (tab) {
                        AlBottomNavTab.Home -> Route.Home
                        AlBottomNavTab.Settings -> Route.Settings
                    }
                    innerNav.navigate(target) {
                        popUpTo(innerNav.graph.findStartDestination().id) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        NavHost(
            navController = innerNav,
            startDestination = Route.Home,
            modifier = Modifier.padding(padding),
        ) {
            composable<Route.Home> {
                // Home does not need a NavController for outbound navigation in this scope;
                // pass the inner nav for consistency. CreateSubject etc. still live outside the
                // shell, so for now Home's outbound destinations are no-ops or open via outer host
                // — see notes below.
                HomeScreenRoot(navController = innerNav)
            }
            composable<Route.Settings> {
                SettingsScreenRoot(onLoggedOut = onSignOut)
            }
        }
    }
}
```

Notes on `HomeScreenRoot` and the inner NavController:
- `HomeScreen` currently navigates to `Route.CreateSubject` via `HomeDestination.NavigateToCreateSubject`. `CreateSubject` is registered in the **outer** `NavHost`. Calling `innerNav.navigate(Route.CreateSubject)` will fail because the inner graph does not contain that route.
- For this plan we keep CreateSubject outside the shell. Update `HomeScreenRoot` to accept an additional callback `onNavigateOutside: (Route) -> Unit = {}` that delegates to the outer NavController, OR introduce a `LocalOuterNavController` `CompositionLocal` provided in `AppNavHost` and consumed in `HomeNavigation`. **Recommended**: add a `CompositionLocal` rather than threading a callback through every screen — the project will need this for every authed screen that navigates to something outside the shell.

#### 2a. `LocalOuterNavController` plumbing
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/LocalOuterNavController.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

val LocalOuterNavController = staticCompositionLocalOf<NavHostController> {
    error("LocalOuterNavController not provided. Wrap with CompositionLocalProvider in AppNavHost.")
}
```

In `AppNavHost.kt`, wrap the entire `NavHost { ... }` (or each `composable<Route.AuthedShell> { ... }` body) with:

```kotlin
CompositionLocalProvider(LocalOuterNavController provides navController) {
    ...
}
```

Update `HomeNavigation.kt` reducer so that `NavigateToCreateSubject` uses `LocalOuterNavController.current` (read inside the `composable<Route.Home>` lambda before calling the reducer) instead of the inner nav controller.

#### 3. Rewire `AppNavHost`
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt`

- Remove the call to `homeScreen(navController)`.
- Add an inline `composable<Route.AuthedShell>` block that renders `AuthedShell(onSignOut = { navController.navigate(Route.GetStarted) { popUpTo(0) { inclusive = true } } })`.
- Wrap the whole `NavHost { ... }` body with `CompositionLocalProvider(LocalOuterNavController provides navController) { ... }` (or apply per-`composable`).

Resulting outer routes: `GetStarted`, `AuthedShell`, `Register`, `RegisterSuccess`, `SelectEstablishment`, `CreateSubject`, `ResetPassword`, `ResetPasswordSuccess`, `ForgotPassword`, `Login`. `Home` and `Settings` live inside the shell.

#### 4. Update start-destination + login redirect
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt`

In `startDestinationFor(...)` change the `Authenticated` branch from `Route.Home` to `Route.AuthedShell`.

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginNavigation.kt`

Change the `LoginDestination.Home` branch to navigate to `Route.AuthedShell` (keep the `popUpTo(0) { inclusive = true }`). Rename the destination later if desired; for now keep the sealed entry name as-is to keep scope small.

#### 5. Wire `SettingsScreenRoot` to the outbound callback
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/settings/SettingsScreen.kt`

`SettingsScreenRoot(onLoggedOut: () -> Unit)`:
- Collect state.
- `LaunchedEffect(state.destination)` → if `LoggedOut`, call `onLoggedOut()` then `viewModel.clearDestination()`.
- Render `SettingsScreen(state, viewModel::onAction)` inside an `AlScaffold`. The screen body is a single centered `AlButton(text = "Logout", onClick = { onAction(SettingsAction.SignOutClick) })`. Disable the button (or render a spinner alongside) when `state.isSigningOut` is true — `AlButton` has no `enabled` parameter today, so either:
  - Add an `enabled: Boolean = true` param to `AlButton`, OR
  - Conditionally swallow the click in the screen's onClick.

  Choose the parameter-add path: minimal, additive, and keeps the screen declarative.

#### 6. `AlButton` gets an `enabled` parameter
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/button/AlButton.kt`

Add `enabled: Boolean = true` parameter; when `false`, set background to `AlTheme.colors.surfaceMuted` and don't fire `onClick`. Existing call sites (Home) pass the default and remain unchanged.

### Success Criteria

#### Automated Verification
- [ ] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Android debug builds: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification
- [ ] Cold-launch the app while logged in (existing stored session) → land on Home tab inside `AuthedShell`; bottom bar visible.
- [ ] Cold-launch logged out → land on `GetStarted`; bottom bar **not** visible.
- [ ] After logging in via the Login screen → land on Home tab inside `AuthedShell`; bottom bar visible; back press does not return to Login.
- [ ] Tap Settings tab → Settings screen appears; bottom bar still visible; tab indicator moves to Settings.
- [ ] Tap Home tab → returns to Home; tab indicator moves; no duplicated entries on inner back stack (verify with multiple back-and-forth taps).
- [ ] Tap Logout in Settings → button disables, network call fires, app returns to `GetStarted`; back press does not re-enter the shell; `sessionStatus` flips to `NotAuthenticated(isSignOut = true)`.
- [ ] Logging in again immediately after sign-out works (no stale bearer cache).
- [ ] On iOS simulator: bottom bar respects safe-area inset (Material3 `Scaffold` does this by default; verify visually).
- [ ] Home → "Crear Tema" still navigates to `CreateSubject` (which lives outside the shell) and back-press returns to the shell.

---

## Testing Strategy

### Manual Testing Steps
1. Fresh install → Get Started → Login → land on shell with Home selected.
2. Tap Settings → Logout → verify return to Get Started and stale-session cleanup (relaunch app, confirm still logged out).
3. Network proxy (e.g. Charles or `adb logcat`) check: confirm `POST /api/v1/auth/logout` body contains `device_id` + `refresh_token` and response is 204.
4. Disable network mid-logout → still logged out locally; relaunch reflects logged-out state.
5. Rapid tab switching (Home/Settings/Home/Settings) does not crash and inner back stack remains depth-1 at the start destination.
6. Login → CreateSubject from Home → back-press → return to Home tab inside shell (verifies outer NavController is reachable from inner screens via `LocalOuterNavController`).

## Performance Considerations
- Nested `NavHost` adds minimal overhead — one extra controller and back-stack per authed session.
- `currentBackStackEntryAsState()` triggers recomposition of the shell on every inner navigation; the bar is cheap to recompose.

## Migration Notes
- No data migrations.
- Users already signed in keep their session; on next launch they will be routed to the new `AuthedShell` rather than directly to `Home`. No state to preserve across the boundary.

## References

- Existing 6-file MVVM reference: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/`
- Existing clear-back-stack idiom: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/auth/login/LoginNavigation.kt:25-30`
- Existing best-effort logout (to be replaced): `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/auth/AuthRepositoryImpl.kt:52-58`
- Already-wired `SignOutUseCase`: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/auth/SignOutUseCase.kt`
- Backend logout contract: `POST /api/v1/auth/logout` body `{device_id, refresh_token}` → 204; from `https://backend-production-b512.up.railway.app/openapi.json`.
