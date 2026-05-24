# GetStarted Screen + Auth-Gated Entry Implementation Plan

## Overview

Add a new `GetStarted` screen as the unauthenticated entry point of the app. It surfaces two CTAs — "Sign up" (navigates to the existing `Register` flow) and "Log in" (visible but disabled, for the not-yet-implemented login flow). Authentication gating remains driven by `SessionStatus`: authenticated users still land directly on `Home`; everyone else now lands on `GetStarted` instead of being dropped straight into the `Register` form.

## Current State Analysis

- `app/App.kt:43-47` already centralizes the auth gate: `Authenticated → Route.Home`, everything else → `Route.Register`. There is no welcome / onboarding / login screen today.
- `SessionStatus` (`domain/model/auth/SessionStatus.kt:3-11`) is observed by `AppViewModel` and exposed as a `StateFlow`, then read by `App.kt` to compute the start destination once per recomposition. The status sink (`di/ClientsModule.kt:21`) is shared with the Ktor refresh callback, so sign-out / refresh failures cleanly transition the start destination — no in-graph guards needed.
- The 6-file feature convention (`Action`, `Destination`, `Navigation`, `Screen`, `State`, `ViewModel`) is well-established. `presentation/feature/home/` is the canonical template; `register/` adds use-case injection + form state.
- `Route.kt:6-27` is a sealed `Route` registry using `@SerialName` keys. `AppNavHost.kt:17-25` wires features via the per-feature `<name>Screen(navController)` extension.
- `core/design_system/components/button/AlButton.kt:13-33` already supports `enabled: Boolean = true` (controls `clickable`) but does **not** visually change when disabled.
- `App.kt:29-30` wraps the tree in both `KomoTheme` and `AlTheme`, so either design system is reachable from any screen. CLAUDE.md says reuse `core/design_system/` first — so the new screen uses `AlTheme` / `AlScaffold` / `AlButton`.

### Key Discoveries:
- Auth-gating is a "compute `startDestination` once" pattern (`App.kt:43-47`). Adding `GetStarted` only requires editing that one mapping.
- `RegisterDestination.Success` already does `popUpTo(Route.Register) { inclusive = true }` (`RegisterNavigation.kt:39-42`). With `GetStarted` as the new start destination, the back stack after submit naturally becomes `[GetStarted, RegisterSuccess]` — system back from `RegisterSuccess` lands on `GetStarted`, which matches the chosen UX with **zero code changes** in the register flow.
- `HomeViewModel` takes no constructor params (`HomeViewModel.kt:10`), making it a clean template for `GetStartedViewModel` (also pure UI, no use cases needed).
- `App.kt` already imports `Route` so adding a new branch is a single-line change.

## Desired End State

When the user opens the app:
- Bootstrap runs (existing `InitializeAuthUseCase` flow) and `App.kt` shows `InitializingScreen` while `SessionStatus.Initializing`.
- If authenticated → `Home` (unchanged).
- Otherwise → **new** `GetStarted` screen with:
  - App title / brand line via `AlTheme` typography
  - A primary "Crear cuenta" button → navigates to `Route.Register`
  - A secondary "Iniciar sesión" button rendered with muted styling and `enabled = false` (no-op tap)
- The existing `Register → RegisterSuccess` flow continues to work; on success the back stack collapses to `[GetStarted, RegisterSuccess]`, so system-back returns to `GetStarted`.

### Verification

Authenticated path: clear app data on a device, sign in via `Register` once it's possible to do so (or stub `SessionStatus.Authenticated` in DI for manual test). Relaunch → app skips `GetStarted` and lands on `Home`.

Unauthenticated path: fresh install. App lands on `GetStarted`. Tap "Crear cuenta" → `Register`. Submit → `RegisterSuccess`. System-back from `RegisterSuccess` → `GetStarted`. Tap "Iniciar sesión" → no-op (disabled).

## What We're NOT Doing

- **No `Login` screen / route / feature folder**. The button is a placeholder only.
- **No `SignInUseCase` wiring from the UI**. It already exists in the domain layer (`SignInUseCase.kt`) and stays untouched.
- **No changes to `RegisterNavigation.kt`**. The `popUpTo(Route.Register) { inclusive = true }` already produces the desired back stack once `GetStarted` becomes the start destination.
- **No redesign of `Home` or `RegisterSuccess`**.
- **No changes to `AlButton`**. We achieve the disabled "Log in" appearance with a separate styled `Text` composable inside `get_started/`, not by extending the design system.
- **No tests** for now — `HomeViewModel` has none either, and `GetStartedViewModel` is pure UI with no logic worth testing.

## Implementation Approach

Two phases. The feature folder is purely additive; the wiring change is a one-line edit in `App.kt` plus three additions in `Route.kt`, `AppNavHost.kt`, and `ViewModelsModule.kt`.

---

## Phase 1: Create the GetStarted feature folder

### Overview
Create `presentation/feature/get_started/` with the 6 canonical files, mirroring `home/`. No external dependencies — the ViewModel is a pure UI state holder.

### Changes Required:

#### 1. New folder
**Path**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/get_started/`

#### 2. GetStartedAction.kt
**File**: `presentation/feature/get_started/GetStartedAction.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.get_started

sealed interface GetStartedAction {
    data object SignUpClick : GetStartedAction
    data object LogInClick : GetStartedAction
}
```

#### 3. GetStartedDestination.kt
**File**: `presentation/feature/get_started/GetStartedDestination.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.get_started

sealed class GetStartedDestination {
    data object NavigateToRegister : GetStartedDestination()
}
```

(No `Login` destination — the button is disabled and produces no action in the VM.)

#### 4. GetStartedState.kt
**File**: `presentation/feature/get_started/GetStartedState.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.get_started

data class GetStartedState(
    val destination: GetStartedDestination? = null,
    val title: String = "Ingeniería en Alimentación",
    val subtitle: String = "Gestioná tu establecimiento desde tu teléfono.",
    val signUpLabel: String = "Crear cuenta",
    val logInLabel: String = "Iniciar sesión",
    val isLoginEnabled: Boolean = false,
)
```

#### 5. GetStartedViewModel.kt
**File**: `presentation/feature/get_started/GetStartedViewModel.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.get_started

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class GetStartedViewModel : ViewModel() {

    private val _state = MutableStateFlow(GetStartedState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: GetStartedAction) {
        when (action) {
            GetStartedAction.SignUpClick ->
                _state.update { it.copy(destination = GetStartedDestination.NavigateToRegister) }
            GetStartedAction.LogInClick -> Unit
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }
}
```

#### 6. GetStartedNavigation.kt
**File**: `presentation/feature/get_started/GetStartedNavigation.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.get_started

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.feature.register.navigateToRegister
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToGetStarted(navOptions: NavOptions? = null) =
    navigate(Route.GetStarted, navOptions)

fun NavGraphBuilder.getStartedScreen(navController: NavController) {
    composable<Route.GetStarted> {
        GetStartedScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: GetStartedDestination,
    navController: NavController,
) {
    when (destination) {
        GetStartedDestination.NavigateToRegister -> navController.navigateToRegister()
    }
}
```

#### 7. GetStartedScreen.kt
**File**: `presentation/feature/get_started/GetStartedScreen.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.get_started

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.button.AlButton
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GetStartedScreenRoot(
    viewModel: GetStartedViewModel = koinViewModel(),
    navigate: (GetStartedDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    GetStartedScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun GetStartedScreen(
    state: GetStartedState,
    onAction: (GetStartedAction) -> Unit,
) {
    AlScaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AlTheme.spacing.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.title,
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(AlTheme.spacing.sm))
            Text(
                text = state.subtitle,
                style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(AlTheme.spacing.xl))
            AlButton(
                text = state.signUpLabel,
                onClick = { onAction(GetStartedAction.SignUpClick) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(AlTheme.spacing.md))
            Text(
                text = state.logInLabel,
                style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.isLoginEnabled) {
                        onAction(GetStartedAction.LogInClick)
                    }
                    .padding(vertical = AlTheme.spacing.md),
            )
        }
    }
}
```

Notes on visual treatment:
- "Crear cuenta" uses the primary `AlButton`.
- "Iniciar sesión" is rendered as a muted, centered `Text` (no background), conveying secondary affordance. `clickable(enabled = state.isLoginEnabled)` keeps it inert until login lands. We deliberately avoid touching `AlButton`'s visuals — that's out of scope. Once the login feature lands, the secondary CTA can be promoted to its own `AlButton` variant.
- Both `AlTheme.spacing.*` and `AlTheme.typography.*` keys (`md`, `lg`, `xl`, `sm`, `title`, `body`) are already used by `HomeScreen.kt`, so no design-token additions are needed.

### Success Criteria:

#### Automated Verification:
- [x] Files exist at expected paths.
- [x] Kotlin compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- [x] Folder structure mirrors `presentation/feature/home/`.
- [x] No new design tokens were introduced; only existing `AlTheme.*` keys are used.

---

## Phase 2: Wire the feature into navigation, DI, and the auth gate

### Overview
Register `Route.GetStarted`, add the screen to `AppNavHost`, register the ViewModel with Koin, and update `startDestinationFor()` so unauthenticated users land on `GetStarted` instead of `Register`.

### Changes Required:

#### 1. Add the route
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`

Insert a new `data object` inside the sealed `Route`, immediately above `Home` (so the unauth entry point reads first):

```kotlin
@Serializable
@SerialName("get_started")
data object GetStarted : Route()
```

#### 2. Wire into AppNavHost
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt`

Add the import:
```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.get_started.getStartedScreen
```

Add the call inside the `NavHost { ... }` block (line 20 area), grouped with other feature wires:
```kotlin
NavHost(
    navController = navController,
    startDestination = startScreen,
) {
    getStartedScreen(navController)
    homeScreen(navController)
    registerScreen(navController)
    selectEstablishmentScreen(navController)
    createSubjectScreen(navController)
}
```

#### 3. Register the ViewModel
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`

Add the import:
```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.get_started.GetStartedViewModel
```

Add `viewModelOf(::GetStartedViewModel)` inside the `module { ... }` block, immediately after `AppViewModel`:
```kotlin
val viewModelsModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::GetStartedViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::RegisterViewModel)
    viewModelOf(::CreateSubjectViewModel)
    viewModel { (route: Route.SelectEstablishment) ->
        SelectEstablishmentViewModel(route = route, getEstablishments = get())
    }
}
```

#### 4. Update the auth gate
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt`

Change `startDestinationFor` (lines 43-47) so the unauthenticated fallback is `GetStarted`:

```kotlin
private fun startDestinationFor(sessionStatus: SessionStatus): Route = when (sessionStatus) {
    is SessionStatus.Authenticated -> Route.Home
    // NotAuthenticated / RefreshFailure / Initializing → get_started (Initializing handled separately).
    else -> Route.GetStarted
}
```

No other change is needed in `App.kt`. The `KomoTheme { AlTheme { ... } }` wrap at `App.kt:29-30` already covers the new screen.

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Android build passes: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Fresh-install / cleared-data launch: app lands on `GetStarted` after the initializing spinner.
- [ ] Tapping "Crear cuenta" navigates to `Register`.
- [ ] System-back from `Register` returns to `GetStarted`.
- [ ] Submitting the register form (when possible) takes the user to `RegisterSuccess`; system-back from there lands on `GetStarted`.
- [ ] Tapping "Iniciar sesión" does nothing (button is disabled).
- [ ] With a stored authenticated session, app skips `GetStarted` and lands on `Home`.
- [ ] After signing out from `Home` (if a sign-out path exists), the app re-routes to `GetStarted`.
- [ ] iOS build runs end-to-end the same way (theme, layout, navigation).

---

## Testing Strategy

### Manual Testing Steps:
1. **Cold start, no session**: clear app data on Android (or delete app on iOS), relaunch. Expect spinner → `GetStarted`.
2. **CTA: Crear cuenta**: tap → `Register`. Tap back → `GetStarted`.
3. **CTA: Iniciar sesión**: tap → no navigation, no toast, no crash.
4. **Register success flow**: complete a registration. Land on `RegisterSuccess`. System-back → `GetStarted` (new back-stack behaviour vs. previous dead-end).
5. **Authenticated cold start**: with a stored session (e.g., from a prior successful sign-in or a debug fixture), relaunch. Expect spinner → `Home` directly, skipping `GetStarted`.
6. **Mid-session sign-out** (if a sign-out trigger exists in the app): observe the start destination flipping to `GetStarted` on the next `App` recomposition.
7. **Both platforms**: run on Android emulator and iOS simulator. Verify layout, typography, and spacing look intentional with `AlTheme` tokens.

### Automated Verification:
- The 6-file feature scaffold compiles (covered by `./scripts/compile.sh`).
- Android assembly builds (`./gradlew :composeApp:assembleDebug --no-daemon`).
- Kotlin tests aren't added; `HomeViewModel` has none, and `GetStartedViewModel` carries no logic beyond a one-line `onAction` mapping that the manual flow already exercises.

## Performance Considerations

None. The screen is a static composition with two text strings and two clickable surfaces. `GetStartedViewModel` has no I/O.

## Migration Notes

- **Existing installs**: users who already had the app installed and were unauthenticated were landing directly on `Register`. After this change, they'll see `GetStarted` first. No data migration required — `SessionStatus` already drives the decision and is computed each launch.
- **Existing authenticated installs**: no change; they still land on `Home`.

## References

- App entry / auth gate: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/App.kt:43-47`
- Nav host wiring: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt:17-25`
- Route registry: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt:6-27`
- VM DI: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt:13-21`
- Feature template (Home): `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/`
- Register success back-stack behavior (unchanged): `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterNavigation.kt:39-42`
- Session model: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/auth/SessionStatus.kt:3-11`
- AlButton signature (already supports `enabled`): `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/button/AlButton.kt:13-33`
