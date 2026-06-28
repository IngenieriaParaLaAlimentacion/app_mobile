# v2 Field-Worker Registration Migration Implementation Plan

## Overview

The backend renamed its role vocabulary (`manager`/`establishment-manager`/`professional` → `field_worker`/`analyst`) and re-created the affected endpoints under `/api/v2`. v2 is a **pure URL rename** — request bodies, response bodies, status codes, and auth are byte-for-byte identical to v1. The only endpoint that matters for mobile is self-registration:

- **Old:** `POST /api/v1/auth/register/establishment-manager`
- **New:** `POST /api/v2/auth/register/field-worker`

Beyond the path swap, the user has chosen to **fully realign the mobile code's internal vocabulary** from the now-misleading "manager" terminology to "field-worker", and to **update the role-string → Spanish-label mapping** to the new role names. No DTO field shapes change (the wire contract is identical).

## Current State Analysis

- The register call lives in a self-contained **registration** vertical slice, separate from the `auth` slice. It is the **only** endpoint affected by v2.
- The v1 path is a hardcoded literal at a single call site: `RegistrationApi.kt:19`. Every endpoint in the app hardcodes its own full relative path (e.g. `"api/v1/..."`) against a centralized host (`ApiConfig.BASE_URL`). There is **no shared `/api/v1` constant**, so changing the register path cannot affect any other endpoint.
- The slice is named with "manager" vocabulary throughout: `RegistrationApi.registerManager`, `RegisterManagerRequest`, `ManagerRegistration`, `RegisterManagerUseCase`, plus the `registerManager` use-case property in `RegisterViewModel`. The generic `Registration*` names (`RegistrationApi`, `RegistrationRepository(Impl)`, `RegistrationMapper`, `RegistrationResult`, `RegistrationError`, `RegistrationFailure`, `RegistrationResponse`) are **not** misleading and will be kept.
- One production site pattern-matches role strings: `RoleLabel.kt:4-10` maps `"professional"` and `"establishment_manager"` to Spanish labels. The v2 backend returns `"field_worker"`/`"analyst"`, so these branches currently fall through to `else -> role` (raw string shown to the user).
- Several test fixtures hardcode old role strings (`"establishment_manager"`, `"professional"`) and old symbol names.

### Key Discoveries:
- Single path literal: `data/registration/RegistrationApi.kt:19`
- Host-only config, no version constant: `data/network/ApiConfig.kt:3-5`
- Role label mapping: `presentation/feature/profile/view/RoleLabel.kt:4-10`
- DTO shapes are unchanged: `data/registration/dto/RegisterManagerRequest.kt`, `data/registration/dto/RegistrationResponse.kt`
- Register call chain: `RegisterViewModel.kt:79-86` → `RegisterManagerUseCase` → `RegistrationRepository(Impl)` → `RegistrationApi`

## Desired End State

- Mobile registers field workers against `POST /api/v2/auth/register/field-worker`.
- All other calls (login, refresh, logout, password-recovery, establishments, measurements, subjects, users) remain on `/api/v1` — **unchanged**.
- The registration slice's "manager" symbols are renamed to "field-worker" vocabulary; the app compiles and all tests pass.
- `RoleLabel.kt` maps the new role strings (`field_worker`, `analyst`) instead of the old ones.

Verification: `./scripts/compile.sh` passes; full test suite green; register flow succeeds end-to-end against the backend (201 → "pending validation" success state).

## What We're NOT Doing

- **Not** touching the auth slice (login/refresh/logout/password-recovery) — no v2 equivalents exist.
- **Not** touching establishments, measurements, subjects, or users endpoints — all stay on v1.
- **Not** changing any DTO field name, JSON key, or request/response shape — the wire contract is identical.
- **Not** adopting the v2 admin/backoffice endpoints (approve/reject/invite-analyst) — those are backoffice web concerns, not mobile.
- **Not** introducing a shared `/api/v1` or `/api/v2` base-path constant (out of scope; each path stays a full literal as today).
- **Not** renaming the generic `Registration*` classes (they are accurate as-is).

## Implementation Approach

Two independent, low-risk changes plus mechanical renames:
1. **Phase 1** — the functional change: swap the path string to v2. This alone makes registration work correctly.
2. **Phase 2** — vocabulary rename of the `Manager`-named symbols across the registration slice (compile-driven; mechanical).
3. **Phase 3** — role-label mapping update and test-fixture alignment to the new role strings.

Phase 1 is the only behavior change; Phases 2–3 are naming/cosmetic and verified by compilation + tests.

---

## Phase 1: Swap register endpoint to v2

### Overview
Change the single hardcoded path literal so mobile hits the v2 field-worker route. Functional, isolated, one line.

### Changes Required:

#### 1. Registration API
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/registration/RegistrationApi.kt`
**Changes**: Update the path string on line 19.

```kotlin
// before
refreshClient.post("api/v1/auth/register/establishment-manager") {
// after
refreshClient.post("api/v2/auth/register/field-worker") {
```

### Success Criteria:

#### Automated Verification:
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Existing registration tests still pass (they use a MockEngine and don't assert on the path): `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon`

#### Manual Verification:
- [ ] Submitting the register form hits `/api/v2/auth/register/field-worker` (verify via network log / backend logs).
- [ ] A successful registration returns 201 and the app shows the "pending validation" success state.
- [ ] 409 (duplicate email), 404 (establishment not found), and 400 (invalid data) still map to the correct on-screen errors.

---

## Phase 2: Rename "manager" vocabulary to "field-worker"

### Overview
Rename the misleading `Manager`-named symbols in the registration slice. Mechanical, compiler-guided. Keep the generic `Registration*` names.

### Rename map
| Old | New |
|-----|-----|
| `RegistrationApi.registerManager(...)` (method) | `registerFieldWorker(...)` |
| `RegisterManagerRequest` (DTO class + file) | `RegisterFieldWorkerRequest` |
| `ManagerRegistration` (domain model class + file) | `FieldWorkerRegistration` |
| `RegisterManagerUseCase` (class + file) | `RegisterFieldWorkerUseCase` |
| `RegisterManagerUseCaseTest` (test class + file) | `RegisterFieldWorkerUseCaseTest` |
| `registerManager` use-case property in `RegisterViewModel` | `registerFieldWorker` |

### Changes Required:

#### 1. Rename DTO
**File**: `data/registration/dto/RegisterManagerRequest.kt` → `RegisterFieldWorkerRequest.kt`
**Changes**: Rename class `RegisterManagerRequest` → `RegisterFieldWorkerRequest`. Field names/`@SerialName` unchanged (wire contract identical).

#### 2. Rename domain model
**File**: `domain/model/registration/ManagerRegistration.kt` → `FieldWorkerRegistration.kt`
**Changes**: Rename `data class ManagerRegistration` → `FieldWorkerRegistration`.

#### 3. Rename use case
**File**: `domain/usecase/registration/RegisterManagerUseCase.kt` → `RegisterFieldWorkerUseCase.kt`
**Changes**: Rename class and update its `ManagerRegistration` param type → `FieldWorkerRegistration`; `repository.registerManager` call → `registerFieldWorker`.

#### 4. Repository interface + impl
**Files**: `domain/repository/RegistrationRepository.kt`, `data/registration/RegistrationRepositoryImpl.kt`
**Changes**: Rename `registerManager(...)` → `registerFieldWorker(...)`; update `ManagerRegistration` param type → `FieldWorkerRegistration`; in the impl, `api.registerManager(...)` → `api.registerFieldWorker(...)`. `RegistrationResult`/`RegistrationError`/`RegistrationFailure` unchanged.

#### 5. API class
**File**: `data/registration/RegistrationApi.kt`
**Changes**: Rename method `registerManager` → `registerFieldWorker`; update body param type `RegisterManagerRequest` → `RegisterFieldWorkerRequest` and its import.

#### 6. Mapper
**File**: `data/registration/mapper/RegistrationMapper.kt`
**Changes**: `ManagerRegistration.toRequest()` receiver → `FieldWorkerRegistration.toRequest()`; returned `RegisterManagerRequest(...)` → `RegisterFieldWorkerRequest(...)`; update imports.

#### 7. Presentation
**File**: `presentation/feature/register/RegisterViewModel.kt`
**Changes**: Constructor property `registerManager: RegisterManagerUseCase` → `registerFieldWorker: RegisterFieldWorkerUseCase`; local `ManagerRegistration(...)` → `FieldWorkerRegistration(...)`; call site `registerManager(registration)` → `registerFieldWorker(registration)`; update imports.

#### 8. DI
**File**: `di/UseCasesModule.kt`
**Changes**: `factory { RegisterManagerUseCase(get()) }` → `factory { RegisterFieldWorkerUseCase(get()) }`; update import. (`ClientsModule`/`RepositoriesModule` reference only the generic `Registration*` types — verify, likely no change.)

#### 9. Tests
**Files**: `commonTest/.../fake/FakeRegistrationRepository.kt`, `commonTest/.../domain/usecase/RegisterManagerUseCaseTest.kt` (→ `RegisterFieldWorkerUseCaseTest.kt`), `commonTest/.../presentation/feature/register/RegisterViewModelTest.kt`, `commonTest/.../data/registration/RegistrationRepositoryImplTest.kt`
**Changes**: Update all references to the renamed symbols (`registerManager` → `registerFieldWorker`, `ManagerRegistration` → `FieldWorkerRegistration`, `RegisterManagerUseCase` → `RegisterFieldWorkerUseCase`). Rename the use-case test file/class.

### Success Criteria:

#### Automated Verification:
- [x] No remaining manager references in the registration slice: `grep -rn -E "registerManager|RegisterManager|ManagerRegistration" composeApp/src --include="*.kt"` returns nothing.
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Full test suite passes: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon` (or the project's test task).

#### Manual Verification:
- [ ] Register flow still works end-to-end (no behavior change from the rename).

---

## Phase 3: Update role labels & test fixtures to new role strings

### Overview
The v2 backend returns `field_worker`/`analyst`. Update the role→label map (user chose **replace old with new**) and align test fixtures.

### Changes Required:

#### 1. Role label mapping
**File**: `presentation/feature/profile/view/RoleLabel.kt`
**Changes**: Replace the old role branches with the new vocabulary.

```kotlin
internal fun roleLabel(role: String): String = when (role) {
    "superadmin" -> "Superadministrador"
    "admin" -> "Administrador"
    "analyst" -> "Analista"               // was "professional" -> "Profesional"
    "field_worker" -> "Trabajador de campo" // was "establishment_manager" -> "Encargado de establecimiento"
    else -> role
}
```

> Note: the new Spanish labels (`"Analista"`, `"Trabajador de campo"`) are proposed defaults — adjust if the product copy differs.

#### 2. Test fixtures using old role strings
**Files**:
- `commonTest/.../data/registration/RegistrationRepositoryImplTest.kt:42` (`"role": "establishment_manager"` → `"field_worker"`)
- `commonTest/.../data/profile/ProfileRepositoryImplTest.kt:66,81,96,136`
- `commonTest/.../presentation/feature/profile/view/ProfileViewModelTest.kt:33,38,128`
- `commonTest/.../presentation/feature/profile/edit/EditProfileViewModelTest.kt:31,36,69`
- `commonTest/.../domain/usecase/ProfileUseCasesTest.kt:23`
- `commonTest/.../presentation/feature/measurement/create_measurement/MeasurementViewModelTest.kt:64`

**Changes**: Replace `"professional"` → `"analyst"` and `"establishment_manager"` → `"field_worker"` in fixtures/assertions. These are pass-through role tests (no label assertions), so this is consistency-only, but keeps the suite aligned with the live contract.

### Success Criteria:

#### Automated Verification:
- [x] No old role strings remain in production source: `grep -rn -E "\"professional\"|\"establishment_manager\"" composeApp/src/commonMain --include="*.kt"` returns nothing.
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] Full test suite passes.

#### Manual Verification:
- [ ] Profile screen shows "Trabajador de campo" (or chosen label) for a field-worker account instead of the raw `field_worker` string.

---

## Testing Strategy

### Manual Testing Steps:
1. Register a brand-new field worker → expect 201 and the "pending validation" success state; confirm the call hit `/api/v2/auth/register/field-worker`.
2. Register with an already-used email → expect the "user already exists" error (409).
3. Register selecting a non-existent establishment (if reproducible) → expect the establishment-not-found error (404).
4. Submit invalid data the server rejects → expect the invalid-data error (400).
5. Log in (still v1) with an approved field worker and open Profile → role label reads correctly.

## Migration Notes

- v1 and v2 are mounted simultaneously on the backend, so there is no flag-day risk; the old path keeps working until this ships.
- Mixed versions are expected and correct: register on `/api/v2`, everything else on `/api/v1`.

## References

- Source of requirements: backend v2 migration TL;DR (commit "feat: create v2 endpoints for new names", PR #49 `refactor/update-roles`).
- Register call site: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/registration/RegistrationApi.kt:19`
- Role label: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/profile/view/RoleLabel.kt:4-10`
- Related plan: `thoughts/plans/establishment_manager_registration.md`
