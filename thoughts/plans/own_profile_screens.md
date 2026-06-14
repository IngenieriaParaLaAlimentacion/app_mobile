# Own Profile — Three Screens Implementation Plan

## Overview

Add the manager/user "Own Profile" surface backed by the `/api/v1/users/me*` endpoints, as **three new screens**:

1. **Profile (tab)** — a top-level bottom-nav destination showing the user's own info; for managers it also shows their establishment relations with status badges and a "Solicitar acceso" entry. **Replaces the existing Settings tab** and absorbs its logout action.
2. **Edit Profile** — a single pushed screen editing `name` + `last_name` (email/role read-only), PATCHing only changed fields.
3. **Request Establishment Access** — a manager-only pushed screen: search + infinite-scroll catalog of requestable establishments (`GET /establishments?available=true`), multi-select, then `POST /users/me/establishments`.

All three live under a new `presentation/feature/profile/` area, follow the repo's strict six-file MVVM convention, and are backed by one new authed data vertical (`data/profile/`, `domain/.../profile/`). The existing establishment/registration code is **left untouched**.

## Current State Analysis

- **Bottom nav already exists.** `app/AuthedShell.kt:24` is a Material3 `Scaffold` whose `bottomBar` renders `AlBottomNavBar` and whose **inner** `NavHost` (`AuthedShell.kt:53`) holds `Route.Home` + `Route.Settings`. Tabs come from the hardcoded enum `AlBottomNavTab { Home, Settings }` (`core/design_system/components/navbar/AlBottomNavBar.kt:27`). Selected tab is derived via `entry?.destination?.hasRoute<Route.Settings>()` (`AuthedShell.kt:28`).
- **Two NavControllers.** The outer one (`app/AppNavHost.kt:23`) hosts full-screen routes and is exposed to inner screens through `LocalOuterNavController` (`app/LocalOuterNavController.kt`). Home already pushes a full-screen route through it: `outerNav.navigateToCreateSubject()` (`AuthedShell.kt:64`). Edit/Request will use the same mechanism.
- **Logout lives in Settings.** `SettingsViewModel.performSignOut()` calls `SignOutUseCase` then emits `SettingsDestination.LoggedOut` (`SettingsViewModel.kt:33-45`); `AuthedShell` passes `onSignOut` which navigates to `Route.GetStarted` with `popUpTo(0){inclusive=true}` (`AppNavHost.kt:36-40`). The new Profile screen must reproduce this exactly.
- **The user is already in the session.** `AuthUser(id, email, name, lastName, role)` (`domain/model/auth/AuthUser.kt`) is nested in `UserSession.user` and persisted. But `GET /users/me` is the source of truth and the **only** place a manager's establishments-with-status come from.
- **Authed-endpoint template = Subject.** `SubjectApi` uses the default authed `HttpClient` via `get()` (`di/ClientsModule.kt:44`), and `SubjectRepositoryImpl` does `runCatching { api.x().toDomain() }.recoverCatching { throw SubjectFailure(mapToError(it), it) }` with HTTP-status→typed-error mapping (`SubjectRepositoryImpl.kt:14-27`). This is the exact shape to copy.
- **Search/debounce template = SelectEstablishment.** `SelectEstablishmentViewModel` debounces a `MutableStateFlow` query (`.debounce(300).distinctUntilChanged().collectLatest`) and folds a `Result` (`SelectEstablishmentViewModel.kt:37-98`); the screen switches on `isLoading / loadError / empty / list` inline (`SelectEstablishmentScreen.kt:93-137`). The Request screen extends this with pagination.

### Key Discoveries
- The existing `EstablishmentResponse(id, name, address)` (`data/establishment/dto/EstablishmentResponse.kt:6`) and domain `Establishment(id, name, address)` are a **thin subset** used by the no-auth registration picker. The new API returns much richer items (`formatted_address`, `latitude`, `longitude`, `place_id`, `city`, `province`, plus `status`) and `available=true` is **manager-only/authed**. We add new models rather than mutate the registration vertical.
- **No status badge / chip / pill component exists** anywhere; design-system loading/error/empty states are hand-rolled per screen. We add `AlStatusBadge`.
- The `Json` config is `encodeDefaults = true` (`HttpClientFactory.kt:26-30`), so a naive nullable PATCH DTO would serialize `last_name: null`. The PATCH body is `extra="forbid"` and rejects empty/extra keys → we must **omit** unchanged fields. Solved with `@EncodeDefault(NEVER)`.
- Three-state `establishments` (`null` / `[]` / `[…]`) maps cleanly to a nullable Kotlin list: JSON `null`→`null`, `[]`→empty list, `[…]`→list. Must not be collapsed.

## Desired End State

- The bottom nav shows **Home | Perfil**. The Perfil tab loads `GET /users/me`, renders name, last_name, email, role (localized), a "Editar perfil" button, and a logout button. For managers (`establishments != null`) it shows the establishments section: status badges per relation, and a "Solicitar acceso" entry; when `establishments == []` it shows the section with only the CTA; when `establishments == null` the whole section is hidden.
- "Editar perfil" opens a single screen prefilled with name/last_name (email/role read-only); Save PATCHes only changed non-empty fields, skips the call entirely if nothing changed, and pops back. The tab reflects the change.
- "Solicitar acceso" (managers only) opens a searchable, infinitely-scrolling catalog; selecting establishments and confirming POSTs the ids; on success it pops back and the new relations appear as **Pendiente**.
- All new ViewModels + repository have unit tests (Turbine + fakes / Ktor `MockEngine`), keeping the established per-feature test coverage.

**Verification:** commonMain compiles, commonTest passes, and the three flows work on a simulator for both a manager account (establishments section visible) and a non-manager account (section hidden).

## What We're NOT Doing

- **Not** parsing `application/problem+json` or the FastAPI `detail[]` 422 body. We follow the existing codebase convention of HTTP-status→typed-error mapping only. The practical 422 cases (empty name, forbidden field) are prevented client-side by form validation and the strict PATCH builder, so they remain defensive.
- **Not** touching the existing `EstablishmentApi` / `Establishment` / `select_establishment` registration flow. New richer models are introduced separately.
- **Not** wiring `GET /users/me/establishments` (partial refresh). The tab reloads the whole `GET /users/me` on resume and the Request screen uses the `201` response directly, so the partial endpoint is unused for now (documented for future use).
- **Not** adding role-based route guards beyond UI gating (the Request screen is only reachable from the manager-only profile section; the repo still maps `403` defensively).
- **Not** adding a dark theme or offline caching.

## Implementation Approach

Build bottom-up so every phase compiles on its own: **(1)** data+domain vertical, **(2)** the `AlStatusBadge` design-system primitive, **(3)** the Profile tab + bottom-nav swap, **(4)** Edit Profile, **(5)** Request Establishment Access. Each new screen is a faithful copy of the six-file `home`/`create_subject`/`select_establishment` patterns; navigation reuses the existing inner-tab (`AuthedShell`) vs outer-push (`LocalOuterNavController`) split.

Package roots:
- Data: `data/profile/` (`ProfileApi`, `ProfileRepositoryImpl`, `dto/`, `mapper/`)
- Domain: `domain/model/profile/`, `domain/repository/ProfileRepository.kt`, `domain/usecase/profile/`
- Presentation: `presentation/feature/profile/` (`view/`, `edit/`, `request_establishment/`)

---

## Phase 1: Data + Domain Vertical (`profile`)

### Overview
Stand up the authed `/users/me*` + available-catalog integration end to end with tests, no UI.

### Changes Required

#### 1. Domain models
**Files**: `domain/model/profile/*.kt`

```kotlin
// EstablishmentStatus.kt
enum class EstablishmentStatus { Approved, Pending, Rejected, Unknown }

// EstablishmentWithStatus.kt
data class EstablishmentWithStatus(
    val id: String,
    val name: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String,
    val city: String,
    val province: String,
    val status: EstablishmentStatus,
)

// AvailableEstablishment.kt  (catalog item — no status)
data class AvailableEstablishment(
    val id: String,
    val name: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val placeId: String,
    val city: String,
    val province: String,
)

// Profile.kt  — establishments is three-state: null = not a manager
data class Profile(
    val id: String,
    val email: String,
    val name: String,
    val lastName: String,
    val role: String,
    val establishments: List<EstablishmentWithStatus>?,
) {
    val isManager: Boolean get() = establishments != null
}

// UpdateProfile.kt — only changed fields are non-null
data class UpdateProfile(val name: String? = null, val lastName: String? = null) {
    val isEmpty: Boolean get() = name == null && lastName == null
}

// PagedEstablishments.kt
data class PagedEstablishments(
    val items: List<AvailableEstablishment>,
    val page: Int,
    val totalPages: Int,
    val total: Int,
)

// ProfileError.kt
sealed interface ProfileError {
    data object Unauthorized : ProfileError       // 401
    data object UserNotFound : ProfileError        // 404
    data object ValidationError : ProfileError     // 422 (defensive)
    data object Network : ProfileError
    data class Unknown(val cause: Throwable?) : ProfileError
}

// EstablishmentRequestError.kt
sealed interface EstablishmentRequestError {
    data object Unauthorized : EstablishmentRequestError       // 401
    data object Forbidden : EstablishmentRequestError          // 403 non-manager
    data object EstablishmentNotFound : EstablishmentRequestError // 404 (all-or-nothing)
    data object Network : EstablishmentRequestError
    data class Unknown(val cause: Throwable?) : EstablishmentRequestError
}
```

#### 2. Repository interface
**File**: `domain/repository/ProfileRepository.kt`
```kotlin
interface ProfileRepository {
    suspend fun getMe(): Result<Profile>
    suspend fun updateMe(update: UpdateProfile): Result<Profile>
    suspend fun getAvailableEstablishments(
        search: String?, page: Int, pageSize: Int,
    ): Result<PagedEstablishments>
    suspend fun requestEstablishmentAccess(ids: List<String>): Result<List<EstablishmentWithStatus>>
}
```

#### 3. DTOs
**Files**: `data/profile/dto/*.kt`
```kotlin
@Serializable
data class ProfileResponse(
    val id: String,
    val email: String,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val role: String,
    val establishments: List<EstablishmentWithStatusResponse>? = null, // three-state preserved
)

@Serializable
data class EstablishmentWithStatusResponse(
    val id: String,
    val name: String,
    @SerialName("formatted_address") val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_id") val placeId: String,
    val city: String,
    val province: String,
    val status: String,
)

@Serializable
data class AvailableEstablishmentResponse(
    val id: String,
    val name: String,
    @SerialName("formatted_address") val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("place_id") val placeId: String,
    val city: String,
    val province: String,
)

@Serializable
data class PaginatedAvailableResponse(
    val items: List<AvailableEstablishmentResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)

// Strict PATCH body: @EncodeDefault(NEVER) omits unchanged (null) fields despite encodeDefaults=true
@Serializable
data class UpdateProfileRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val name: String? = null,
    @SerialName("last_name") @EncodeDefault(EncodeDefault.Mode.NEVER) val lastName: String? = null,
)

@Serializable
data class RequestEstablishmentsRequest(
    @SerialName("establishment_ids") val establishmentIds: List<String>,
)
```

#### 4. Mappers
**File**: `data/profile/mapper/ProfileMapper.kt` — `internal` extension funcs `ProfileResponse.toDomain()`, `EstablishmentWithStatusResponse.toDomain()`, `AvailableEstablishmentResponse.toDomain()`, `PaginatedAvailableResponse.toDomain()`, and a private `String.toEstablishmentStatus()` (`"approved"→Approved`, `"pending"→Pending`, `"rejected"→Rejected`, else `Unknown`).

#### 5. Api (authed client, like `SubjectApi`)
**File**: `data/profile/ProfileApi.kt`
```kotlin
class ProfileApi(private val client: HttpClient) {
    suspend fun getMe(): ProfileResponse =
        client.get("api/v1/users/me").body()

    suspend fun updateMe(body: UpdateProfileRequest): ProfileResponse =
        client.patch("api/v1/users/me") {
            contentType(ContentType.Application.Json); setBody(body)
        }.body()

    suspend fun getAvailableEstablishments(search: String?, page: Int, pageSize: Int): PaginatedAvailableResponse =
        client.get("api/v1/establishments") {
            parameter("available", true)
            parameter("page", page)
            parameter("page_size", pageSize)
            search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
        }.body()

    suspend fun requestEstablishmentAccess(body: RequestEstablishmentsRequest): List<EstablishmentWithStatusResponse> =
        client.post("api/v1/users/me/establishments") {
            contentType(ContentType.Application.Json); setBody(body)
        }.body()
}
```

#### 6. Repository impl
**File**: `data/profile/ProfileRepositoryImpl.kt` — mirrors `SubjectRepositoryImpl`. Each method: `runCatching { api.x().toDomain() }.recoverCatching { throw ProfileFailure(mapProfileError(it), it) }` (and `EstablishmentRequestFailure` / `mapRequestError` for the request method). Status maps: `401→Unauthorized`, `404→UserNotFound`/`EstablishmentNotFound`, `403→Forbidden`, `422→ValidationError`, other `ResponseException → Network`, else `Unknown`. The `updateMe` builds `UpdateProfileRequest(update.name, update.lastName)`.

#### 7. Use cases
**Files**: `domain/usecase/profile/*.kt` — pass-through `operator fun invoke`s: `GetMyProfileUseCase`, `UpdateMyProfileUseCase` (`suspend operator fun invoke(update: UpdateProfile)`), `GetAvailableEstablishmentsUseCase` (`(search, page, pageSize)`), `RequestEstablishmentAccessUseCase` (`(ids: List<String>)`).

#### 8. DI wiring
- `di/ClientsModule.kt`: add `single { ProfileApi(client = get()) }` (authed, alongside `SubjectApi` at line 44).
- `di/RepositoriesModule.kt`: add `single<ProfileRepository> { ProfileRepositoryImpl(api = get()) }`.
- `di/UseCasesModule.kt`: add the four `factory { ... }` lines.

#### 9. Tests
**Files**: `commonTest/.../data/profile/ProfileRepositoryImplTest.kt` (Ktor `MockEngine`: getMe three-state cases, PATCH omits unchanged field, status→error mapping, paginated mapping, request returns statuses), `commonTest/.../domain/usecase/profile/*Test.kt`, and `commonTest/.../fake/FakeProfileRepository.kt`.

### Success Criteria

#### Automated Verification:
- [x] Compilation passes: `./scripts/compile.sh`
- [x] Metadata compiles: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`
- [x] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`
- [x] `UpdateProfileRequest(name="X").toJson()` serializes to `{"name":"X"}` (no `last_name` key) — asserted in a serialization test.

#### Manual Verification:
- [x] N/A (no UI yet).

---

## Phase 2: Design System — `AlStatusBadge` + status tokens

### Overview
A generic, tokenized status pill so the Profile screen can render Aceptado / Pendiente / Rechazado.

### Changes Required

#### 1. Color tokens
**Files**: `core/design_system/theme/Color.kt`, `theme/Theme.kt` — add semantic status tokens to `AlColors` and `lightColors`: `warning` + `warningContainer` (amber, e.g. `0xFFB45309` on `0xFFFEF3C7`) and `danger` + `dangerContainer` (red, e.g. `0xFFB91C1C` on `0xFFFEE2E2`). Approved reuses existing `accent` (with a light green container, e.g. `0xFFDCFCE7`); add `accentContainer` for that.

#### 2. Badge component
**File**: `core/design_system/components/badge/AlStatusBadge.kt`
```kotlin
enum class AlBadgeTone { Success, Warning, Danger, Neutral }

@Composable
fun AlStatusBadge(text: String, tone: AlBadgeTone, modifier: Modifier = Modifier) {
    val (bg, fg) = when (tone) {
        AlBadgeTone.Success -> AlTheme.colors.accentContainer to AlTheme.colors.accent
        AlBadgeTone.Warning -> AlTheme.colors.warningContainer to AlTheme.colors.warning
        AlBadgeTone.Danger  -> AlTheme.colors.dangerContainer to AlTheme.colors.danger
        AlBadgeTone.Neutral -> AlTheme.colors.surfaceMuted to AlTheme.colors.textMuted
    }
    Box(
        modifier
            .clip(RoundedCornerShape(AlTheme.corners.sm))
            .background(bg)
            .padding(horizontal = AlTheme.spacing.sm, vertical = AlTheme.spacing.xs),
    ) { Text(text, style = AlTheme.typography.caption, color = fg) }
}
```
Status→(label, tone) localization stays in the **profile presentation** layer, not the DS component.

### Success Criteria
#### Automated Verification:
- [x] `./scripts/compile.sh` passes.
#### Manual Verification:
- [ ] Badge renders all four tones legibly (verified visually in Phase 3).

---

## Phase 3: Profile Tab + Bottom-Nav Swap

### Overview
The new top-level tab replacing Settings; loads `GET /users/me`, renders the three-state establishments section, owns logout, and routes to Edit / Request via the outer nav.

### Changes Required

#### 1. Routes
**File**: `presentation/navigation/Route.kt` — add:
```kotlin
@Serializable @SerialName("profile") data object Profile : Route()
@Serializable @SerialName("edit_profile")
data class EditProfile(val name: String, val lastName: String, val email: String, val role: String) : Route()
@Serializable @SerialName("request_establishment") data object RequestEstablishment : Route()
```
Remove `Settings` (see step 6).

#### 2. Bottom-nav tab enum
**File**: `core/design_system/components/navbar/AlBottomNavBar.kt` — replace the `Settings` entry:
```kotlin
enum class AlBottomNavTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Profile("Perfil", Icons.Default.Person),
}
```

#### 3. Profile feature (six files)
**Files**: `presentation/feature/profile/view/Profile{Action,Destination,State,ViewModel,Screen,Navigation}.kt`
- **State**: `destination`, `isLoading`, `loadError`, `profile: Profile?`, `isSigningOut`.
- **Action**: `Load`, `RetryClick`, `EditClick`, `RequestEstablishmentClick`, `SignOutClick`.
- **Destination**: `EditProfile(name,lastName,email,role)`, `RequestEstablishment`, `LoggedOut`.
- **ViewModel** (`GetMyProfileUseCase`, `SignOutUseCase`): `init { load() }`; `load()` folds `getMyProfile()` into state; `EditClick` emits `EditProfile(...)` from current profile; `RequestEstablishmentClick` emits `RequestEstablishment`; `SignOutClick` mirrors `SettingsViewModel.performSignOut()` → emits `LoggedOut`; `clearDestination()`. Expose a public `refresh()` calling `load()` for reload-on-resume.
- **Screen**: `AlScaffold { Column { ... } }`. Header `AlScreenHeader(title=name+lastName, subtitle=localizedRole)`. Read-only email row. "Editar perfil" `AlButton`. Establishments section branches on `profile.establishments`: `null`→render nothing; non-null→section title + (if empty) a muted "Aún no tenés establecimientos" + always a "Solicitar acceso" `AlButton`; (if non-empty) a list of rows each with name/city + `AlStatusBadge` via `establishmentStatus.toBadge()`. Logout `AlButton(variant = Secondary, enabled = !isSigningOut)`. Inline loading/error states copied from `SelectEstablishmentScreen.kt:93-112`.
- **Navigation**: `navigateToProfile()` + `internal fun navigateProfile(destination, outerNav, onSignOut)` dispatching `EditProfile→outerNav.navigateToEditProfile(...)`, `RequestEstablishment→outerNav.navigateToRequestEstablishment()`, `LoggedOut→onSignOut()`.
- **Reload-on-resume**: in `ProfileScreenRoot`, add `LifecycleResumeEffect(Unit) { viewModel.refresh(); onPauseOrDispose { } }` so returning from Edit/Request refreshes the tab.
- **Role localization helper**: `presentation/feature/profile/view/RoleLabel.kt` mapping `superadmin→"Superadministrador"`, `admin→"Administrador"`, `professional→"Profesional"`, `establishment_manager→"Encargado de establecimiento"`, else the raw role.

#### 4. AuthedShell wiring
**File**: `app/AuthedShell.kt`
- `selectedTab`: `entry?.destination?.hasRoute<Route.Profile>() == true -> AlBottomNavTab.Profile`.
- `when (tab)`: `AlBottomNavTab.Profile -> Route.Profile`.
- Replace the `composable<Route.Settings>` block with:
```kotlin
composable<Route.Profile> {
    ProfileScreenRoot(
        navigate = { dest -> navigateProfile(dest, outerNav, onSignOut) },
    )
}
```
- Remove the Settings import.

#### 5. ViewModel DI
**File**: `di/ViewModelsModule.kt` — add `viewModelOf(::ProfileViewModel)`; remove `viewModelOf(::SettingsViewModel)`.

#### 6. Remove the orphaned Settings feature
Delete `presentation/feature/settings/` (all six files), the `Settings` route case, and `navigateToSettings`/`settingsScreen`. Logout is fully reproduced in Profile. (Called out as its own reviewable step.)

### Success Criteria
#### Automated Verification:
- [x] `./scripts/compile.sh` passes.
- [x] `./gradlew :composeApp:testDebugUnitTest --no-daemon` passes (new `ProfileViewModelTest`).
#### Manual Verification:
- [ ] Bottom nav shows **Home | Perfil**; tapping Perfil loads the profile.
- [ ] Manager account: establishments section shows with correct status badges; `[]` account shows section + only the CTA.
- [ ] Non-manager account (`establishments: null`): no establishments section at all.
- [ ] Logout from Profile returns to Get Started and clears the back stack.

---

## Phase 4: Edit Profile Screen

### Overview
Single pushed screen editing name + last_name, diffing against the original and PATCHing only changes.

### Changes Required

#### 1. Feature (six files)
**Files**: `presentation/feature/profile/edit/EditProfile{Action,Destination,State,ViewModel,Screen,Navigation}.kt`
- **State**: `destination`, `name`, `lastName`, the read-only `email`/`role`, original `originalName`/`originalLastName`, `isSaving`, `saveError: ProfileError?`. Computed `canSave = name.isNotBlank() && lastName.isNotBlank() && hasChanges && !isSaving`; `hasChanges = name != originalName || lastName != originalLastName`.
- **Action**: `NameChanged(String)`, `LastNameChanged(String)`, `SaveClick`, `BackClick`.
- **Destination**: `Back`, `Saved`.
- **ViewModel** (`route: Route.EditProfile`, `UpdateMyProfileUseCase`) — registered as `viewModel { (route: Route.EditProfile) -> EditProfileViewModel(route, updateMyProfile = get()) }` (mirrors `ResetPasswordViewModel`, `ViewModelsModule.kt:30`). Prefill from route. `SaveClick`: build `UpdateProfile(name = name.takeIf { it != originalName }, lastName = lastName.takeIf { it != originalLastName })`; if `isEmpty` → just emit `Back` (no call); else fold `updateMyProfile(update)` → success emits `Saved`, failure sets `saveError`.
- **Screen**: `AlScaffold { Column { AlBackTopAppBar(title="Editar perfil"); AlInput(name, label="Nombre", error=...); AlInput(lastName, label="Apellido"); read-only email/role rows; AlButton("Guardar", loading=isSaving, enabled=canSave) } }`. Pass `scrollState`/`imePadding` per the CMP keyboard pattern in CLAUDE.md.
- **Navigation**: `navigateToEditProfile(name,lastName,email,role)`, `editProfileScreen(navController)`, `internal fun navigate(dest, navController)` (`Back`/`Saved → popBackStack()`).

#### 2. NavHost + DI
- `app/AppNavHost.kt`: add `editProfileScreen(navController)` inside the `NavHost { }` block (alongside `createSubjectScreen`).
- `di/ViewModelsModule.kt`: add the `viewModel { (route: Route.EditProfile) -> ... }` factory.

### Success Criteria
#### Automated Verification:
- [x] `./scripts/compile.sh` passes.
- [x] `./gradlew :composeApp:testDebugUnitTest --no-daemon` passes (new `EditProfileViewModelTest`: no-change skips call, only-changed-field sent, empty field disables save, success emits Saved).
#### Manual Verification:
- [ ] Editing name and saving updates the Profile tab on return.
- [ ] Saving with no changes does not hit the network and just returns.
- [ ] Email/role are visibly read-only.

---

## Phase 5: Request Establishment Access Screen (manager)

### Overview
Manager-only pushed screen: searchable, infinitely-scrolling catalog (`GET /establishments?available=true`), multi-select, then `POST /users/me/establishments`.

### Changes Required

#### 1. Feature (six files)
**Files**: `presentation/feature/profile/request_establishment/RequestEstablishment{Action,Destination,State,ViewModel,Screen,Navigation}.kt`
- **State**:
```kotlin
data class RequestEstablishmentState(
    val destination: RequestEstablishmentDestination? = null,
    val query: String = "",
    val results: List<AvailableEstablishment> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val isLoading: Boolean = true,       // initial / search reload
    val isLoadingMore: Boolean = false,  // appending next page
    val loadError: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: EstablishmentRequestError? = null,
) {
    val canLoadMore get() = page < totalPages && !isLoadingMore && !isLoading
    val selectedCount get() = selectedIds.size
    val canSubmit get() = selectedIds.isNotEmpty() && !isSubmitting
}
```
- **Action**: `QueryChanged(String)`, `LoadMore`, `EstablishmentToggled(String)`, `SubmitClick`, `RetryClick`, `BackClick`.
- **ViewModel** (`GetAvailableEstablishmentsUseCase`, `RequestEstablishmentAccessUseCase`): debounced query (`MutableStateFlow` + `.debounce(300).distinctUntilChanged().collectLatest { reload(it) }`, copied from `SelectEstablishmentViewModel.kt:37-44`). `reload(q)` fetches `page=1`, replaces `results`, sets `page/totalPages`. `LoadMore` (guarded by `canLoadMore`) fetches `page+1`, **appends**. `EstablishmentToggled` flips id in `selectedIds`. `SubmitClick` folds `requestAccess(selectedIds.toList())` → success emits `Submitted`; failure sets `submitError`. `clearDestination()`.
- **Destination**: `Back`, `Submitted` (both `popBackStack()`; the Profile tab refreshes on resume).
- **Screen**: like `SelectEstablishmentScreen` (search `AlInput` + `LazyColumn` of `AlSelectableCard(title=name, description=formattedAddress, selected=id in selectedIds)` + bottom `AlButton("Solicitar acceso (${selectedCount})", loading=isSubmitting, enabled=canSubmit)`), plus infinite scroll:
```kotlin
val listState = rememberLazyListState()
LaunchedEffect(listState, state.results.size) {
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
        .collect { last -> if (last != null && last >= state.results.size - 3 && state.canLoadMore)
            onAction(RequestEstablishmentAction.LoadMore) }
}
```
A footer item shows a small `Spinner` while `isLoadingMore`. Inline loading/error/empty states reuse the `SelectEstablishmentScreen.kt:93-119` pattern; `submitError` surfaces a localized message.
- **Navigation**: `navigateToRequestEstablishment()`, `requestEstablishmentScreen(navController)`, `internal fun navigate(dest, navController)`.

#### 2. NavHost + DI
- `app/AppNavHost.kt`: add `requestEstablishmentScreen(navController)`.
- `di/ViewModelsModule.kt`: add `viewModelOf(::RequestEstablishmentViewModel)`.

### Success Criteria
#### Automated Verification:
- [x] `./scripts/compile.sh` passes.
- [x] `./gradlew :composeApp:testDebugUnitTest --no-daemon` passes (new `RequestEstablishmentViewModelTest`: page-1 load, search resets to page 1, `LoadMore` appends and respects `totalPages`, toggle add/remove, submit success emits `Submitted`, error sets `submitError`).
#### Manual Verification:
- [ ] Scrolling loads more pages; search resets the list; no duplicate rows.
- [ ] Selecting establishments and confirming returns to Profile; the new relations show as **Pendiente**.
- [ ] Re-requesting a previously rejected establishment shows it as **Pendiente** (idempotent).

---

## Testing Strategy

### Unit (commonTest, kotlin.test + Turbine + kotlinx-coroutines-test)
- `ProfileRepositoryImplTest` — Ktor `MockEngine` per `rules/kotlin/testing.md`: three-state `establishments`, `UpdateProfileRequest` omits unchanged fields, status→error mapping for `getMe`/`updateMe`/`requestAccess`, paginated mapping.
- Use-case tests (thin) + `FakeProfileRepository`.
- `ProfileViewModelTest` — initial/loading/loaded, three-state branching, logout path, refresh.
- `EditProfileViewModelTest` — diff/skip-empty/validation/success.
- `RequestEstablishmentViewModelTest` — pagination + search + select + submit.

### Manual (simulator, both platforms)
1. Manager login → Perfil tab → establishments with badges.
2. `[]`-manager → section + CTA only.
3. Non-manager → no section.
4. Edit name/last_name → returns updated; no-op save makes no call.
5. Request access → search + scroll + select + confirm → Pendiente on return.
6. Logout from Perfil → Get Started, back stack cleared.

## Performance Considerations
- Catalog uses real pagination (page_size ~20) instead of the existing single-page `page_size=100`, avoiding large payloads.
- Search is debounced (300ms) and uses `collectLatest` to cancel stale requests.
- Reload-on-resume of the Profile tab is a single lightweight `GET /users/me`.

## Migration Notes
- Replacing the Settings tab removes the `settings` feature, `Route.Settings`, and `SettingsViewModel` registration. No persisted state references them; the only wiring is in `AuthedShell` and `ViewModelsModule`. Logout behavior is preserved verbatim via `SignOutUseCase` in `ProfileViewModel`.

## References
- API guide: provided in the task (Own Profile — Mobile Integration Guide)
- Authed endpoint template: `data/subject/SubjectApi.kt`, `data/subject/SubjectRepositoryImpl.kt:14-27`
- Search/debounce + inline states: `presentation/feature/establishment/select_establishment/SelectEstablishmentViewModel.kt:37-98`, `SelectEstablishmentScreen.kt:93-137`
- Tab vs outer-push nav: `app/AuthedShell.kt:24-74`, `app/AppNavHost.kt:23-44`, `app/LocalOuterNavController.kt`
- Logout to reproduce: `presentation/feature/settings/SettingsViewModel.kt:33-45`
- Param-route ViewModel registration: `di/ViewModelsModule.kt:27-32`
- Six-file convention reference: `presentation/feature/home/`, `presentation/feature/subject/create_subject/`
