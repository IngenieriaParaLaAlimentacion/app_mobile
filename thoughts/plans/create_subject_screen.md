# Create Subject Screen Implementation Plan

## Overview

Add a single screen for an authenticated establishment manager to upload a new subject (`POST /api/v1/subjects`). Follows the existing MVVM + clean-architecture layout used by the `register` feature: domain → data → presentation, wired through Koin. UI uses the KomoUI components already in use elsewhere (`Input`, `Button`, `Spinner`), wrapped in the project's IME-aware scroll pattern from `CLAUDE.md`.

## Current State Analysis

- **Endpoint** lives in `docs/backend.openapi.generated.json:447` as `POST /api/v1/subjects` (plural), summary "Register a new subject", "Accessible by establishment managers only". The user wrote `/subject` (singular) — the spec is the source of truth.
- **`SubjectRequest`** (`docs/backend.openapi.generated.json:739`):
  - `dni: int [1, 99_999_999]`
  - `sex: Sex` enum (`male` | `female`) — `docs/backend.openapi.generated.json:730`
  - `name: string [1, 50]`
  - `last_name: string [1, 50]`
  - `birth_date: string (date)` ISO `YYYY-MM-DD`
  - `is_rural: bool`
  - `is_indigenous: bool`
- **`SubjectResponse` 201**: `id (uuid)` + all input fields echoed (`docs/backend.openapi.generated.json:790`).
- **Error responses** declared: `400 Invalid data`, `401 Missing or invalid token`. The spec doesn't enumerate `403`, but the endpoint description ("establishment managers only") implies one — we handle it defensively.
- **Auth**: secured by `OAuth2PasswordBearer`. So this call goes through the default authed `HttpClient` (Koin: no qualifier), **not** the `"refresh"` client used by `RegistrationApi`. See `di/ClientsModule.kt:32`.
- **Closest precedent for the full stack**: `register` feature — `data/registration/{RegistrationApi.kt, RegistrationRepositoryImpl.kt, RegistrationFailure}` + `domain/model/registration/{ManagerRegistration, RegistrationError, RegistrationResult}` + `domain/usecase/registration/RegisterManagerUseCase.kt` + `presentation/feature/register/Register{Action,State,ViewModel,Screen,Navigation,Destination}.kt`. We mirror this layout.
- **`kotlinx-datetime`** is on the commonMain classpath (`composeApp/build.gradle.kts:65`), so `LocalDate` is available for `birth_date` parsing.
- **`feature-screen-generator` skill** is local at `.claude/skills/feature-screen-generator/SKILL.md` and defines the 6-file presentation layout; templates live in the same folder. **Note:** its template paths assume the `com.extra.marketplace` package — we substitute `ar.com.ingenieriaalimentacion` and the project's actual integration-file locations.
- **No subject UI exists today**. `HomeScreen.kt` is an almost-empty placeholder, so it's the natural entry point for manual testing.

### Key Discoveries

- KomoUI's `MaterialTheme.styles` token surface (e.g. `background`, `foreground`, `mutedForeground`, `border`, `destructive`) is the source for colors in KomoUI-based screens — see `presentation/feature/register/RegisterScreen.kt:84,167,168,199`. The project also keeps an `AlTheme` (used by `Home`/`AlScaffold`), but new screens that use KomoUI components should also use KomoUI styles for consistency.
- The IME-aware scroll-to-field helper `RegisterField` in `presentation/feature/register/RegisterScreen.kt:215-267` already implements the CMP pattern from `CLAUDE.md`. Copy it (renamed) into the new screen rather than extracting prematurely — there's only one other caller and the abstraction can come later once a third use shows up.
- Repositories return `Result<T>` and wrap typed errors via a `*Failure : Exception(...)` carrier (`RegistrationRepositoryImpl.kt:37`). The ViewModel reads `(throwable as? RegistrationFailure)?.error` to switch on the sealed error type. We replicate this pattern verbatim.
- DI registration goes in four files: `ClientsModule.kt` (Api), `RepositoriesModule.kt` (Repository binding), `UseCasesModule.kt` (UseCase factory), `ViewModelsModule.kt` (ViewModel).

## Desired End State

A signed-in `establishment_manager` can:

1. Tap a "Cargar sujeto" button on `Home` and land on `CreateSubjectScreen`.
2. Fill DNI, Sex (segmented selector), Name, Last name, Birth date (`DD/MM/YYYY`), and two switches (rural / indigenous).
3. See per-field validation errors before any network call.
4. Submit; on `201` see an inline success banner, the form clears, and they remain on the screen to add another.
5. On `400/401/403/network/unknown`, see a user-friendly Spanish error message; the form is preserved.

Verification:
- `./scripts/compile.sh` passes.
- `./gradlew :composeApp:assembleDebug --no-daemon` passes.
- Manually adding then submitting a valid subject hits the backend and returns a created `Subject` (verifiable in network logs via `HttpClientFactory` logging plugin and/or backend logs).

## What We're NOT Doing

- No native date picker — plain `DD/MM/YYYY` text input parsed to `LocalDate`. (`expect`/`actual` date pickers are a separate, larger piece of work.)
- No list / detail / edit / delete screens for subjects.
- No offline queue, draft persistence, retry policy, or optimistic UI.
- No new design-system component (segmented control, switch, banner). We implement those inline in the screen file; extract later if reused.
- No changes to `AlTheme` tokens.
- No tests in this plan beyond what the existing precedent has — the project currently has no `commonTest` ViewModel/repository tests; adding the first test harness is out of scope here.
- No new permission for `establishment_manager` role gating in the navigation layer — we let the backend reject with `403` and surface a friendly message. Role-aware nav is a separate concern.

## Implementation Approach

Build bottom-up so each layer compiles before the next is added. Phases 1–2 are non-UI and verifiable purely via `./scripts/compile.sh`. Phase 3 is the screen itself, invoking the `feature-screen-generator` skill for boilerplate but customizing it for the form needs. Phase 4 is the minimal Home wiring needed to manually exercise the flow.

---

## Phase 1: Domain + Data layer

### Overview

Add the domain models, repository contract, use case, DTOs, mapper, API client, and repository impl. No UI, no DI changes yet. Mirrors `data/registration/*` + `domain/{model,usecase,repository}/registration/*`.

### Changes Required

#### 1. Domain models — Sex, Subject, NewSubject, SubjectError

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/subject/Sex.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.subject

enum class Sex { Male, Female }
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/subject/Subject.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.datetime.LocalDate

data class Subject(
    val id: String,
    val dni: Int,
    val sex: Sex,
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val isRural: Boolean,
    val isIndigenous: Boolean,
)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/subject/NewSubject.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.subject

import kotlinx.datetime.LocalDate

data class NewSubject(
    val dni: Int,
    val sex: Sex,
    val name: String,
    val lastName: String,
    val birthDate: LocalDate,
    val isRural: Boolean,
    val isIndigenous: Boolean,
)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/model/subject/SubjectError.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.model.subject

sealed interface SubjectError {
    data object InvalidData : SubjectError      // 400
    data object Unauthorized : SubjectError     // 401
    data object Forbidden : SubjectError        // 403 (not in spec but implied by role gating)
    data object Network : SubjectError
    data class Unknown(val cause: Throwable?) : SubjectError
}
```

#### 2. Domain repository contract + use case

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/repository/SubjectRepository.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.repository

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject

interface SubjectRepository {
    suspend fun create(newSubject: NewSubject): Result<Subject>
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/domain/usecase/subject/CreateSubjectUseCase.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.domain.usecase.subject

import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository

class CreateSubjectUseCase(private val repository: SubjectRepository) {
    suspend operator fun invoke(newSubject: NewSubject): Result<Subject> =
        repository.create(newSubject)
}
```

#### 3. Data DTOs + mapper

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/dto/SexDto.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SexDto {
    @SerialName("male") MALE,
    @SerialName("female") FEMALE,
}
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/dto/SubjectRequest.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectRequest(
    val dni: Int,
    val sex: SexDto,
    val name: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String, // ISO YYYY-MM-DD
    @SerialName("is_rural") val isRural: Boolean,
    @SerialName("is_indigenous") val isIndigenous: Boolean,
)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/dto/SubjectResponse.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.subject.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectResponse(
    val id: String,
    val dni: Int,
    val sex: SexDto,
    val name: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("birth_date") val birthDate: String,
    @SerialName("is_rural") val isRural: Boolean,
    @SerialName("is_indigenous") val isIndigenous: Boolean,
)
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/mapper/SubjectMapper.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.subject.mapper

import ar.com.ingenieriaalimentacion.data.subject.dto.SexDto
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectResponse
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import kotlinx.datetime.LocalDate

internal fun NewSubject.toRequest(): SubjectRequest = SubjectRequest(
    dni = dni,
    sex = sex.toDto(),
    name = name,
    lastName = lastName,
    birthDate = birthDate.toString(), // LocalDate.toString() emits ISO YYYY-MM-DD
    isRural = isRural,
    isIndigenous = isIndigenous,
)

internal fun SubjectResponse.toDomain(): Subject = Subject(
    id = id,
    dni = dni,
    sex = sex.toDomain(),
    name = name,
    lastName = lastName,
    birthDate = LocalDate.parse(birthDate),
    isRural = isRural,
    isIndigenous = isIndigenous,
)

private fun Sex.toDto(): SexDto = when (this) {
    Sex.Male -> SexDto.MALE
    Sex.Female -> SexDto.FEMALE
}

private fun SexDto.toDomain(): Sex = when (this) {
    SexDto.MALE -> Sex.Male
    SexDto.FEMALE -> Sex.Female
}
```

#### 4. API client

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/SubjectApi.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectRequest
import ar.com.ingenieriaalimentacion.data.subject.dto.SubjectResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Subjects endpoint. Requires an authenticated establishment_manager — uses the
 * default authed [HttpClient] (no Koin qualifier), unlike [ar.com.ingenieriaalimentacion.data.registration.RegistrationApi].
 */
class SubjectApi(private val client: HttpClient) {

    suspend fun create(body: SubjectRequest): SubjectResponse =
        client.post("api/v1/subjects") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
}
```

#### 5. Repository impl + typed failure

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/subject/SubjectRepositoryImpl.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.data.subject

import ar.com.ingenieriaalimentacion.data.subject.mapper.toDomain
import ar.com.ingenieriaalimentacion.data.subject.mapper.toRequest
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.Subject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectError
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

class SubjectRepositoryImpl(private val api: SubjectApi) : SubjectRepository {
    override suspend fun create(newSubject: NewSubject): Result<Subject> =
        runCatching { api.create(newSubject.toRequest()).toDomain() }
            .recoverCatching { throwable -> throw SubjectFailure(mapToError(throwable), throwable) }

    private fun mapToError(t: Throwable): SubjectError = when (t) {
        is ClientRequestException -> when (t.response.status) {
            HttpStatusCode.BadRequest -> SubjectError.InvalidData
            HttpStatusCode.Unauthorized -> SubjectError.Unauthorized
            HttpStatusCode.Forbidden -> SubjectError.Forbidden
            else -> SubjectError.Network
        }
        is ResponseException -> SubjectError.Network
        else -> SubjectError.Unknown(t)
    }
}

class SubjectFailure(val error: SubjectError, cause: Throwable?) :
    Exception(error.toString(), cause)
```

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- None (no UI yet).

---

## Phase 2: DI wiring

### Overview

Register the new `SubjectApi` (against the authed `HttpClient`), `SubjectRepository`, and `CreateSubjectUseCase`. The `ViewModel` isn't built yet — added in Phase 3.

### Changes Required

#### 1. Clients module — add SubjectApi against the authed client

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ClientsModule.kt`

After `single { RegistrationApi(refreshClient = get(named("refresh"))) }` (line 29) and inside the same `module { ... }`, add **after** the `single<HttpClient> { HttpClientFactory.createAuthedClient(...) }` block so the authed client exists when resolved:

```kotlin
single { SubjectApi(client = get()) } // authed client (no qualifier)
```

This goes at the very end of the module body so the default `HttpClient` binding (line 32) is registered before resolution. Koin resolves lazily so ordering only matters for readability, but keeping it grouped with other authed Apis (we have none yet) is fine — appending to the bottom is the lowest-risk placement.

#### 2. Repositories module — bind SubjectRepository

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/RepositoriesModule.kt`

After `single<RegistrationRepository> { ... }` (line 29), add:

```kotlin
single<SubjectRepository> { SubjectRepositoryImpl(api = get()) }
```

Plus the import:

```kotlin
import ar.com.ingenieriaalimentacion.data.subject.SubjectRepositoryImpl
import ar.com.ingenieriaalimentacion.domain.repository.SubjectRepository
```

#### 3. Use cases module — add CreateSubjectUseCase

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/UseCasesModule.kt`

After `factory { RegisterManagerUseCase(get()) }` (line 17), add:

```kotlin
factory { CreateSubjectUseCase(get()) }
```

Plus:

```kotlin
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
```

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`

#### Manual Verification:
- None.

---

## Phase 3: Presentation — CreateSubject screen

### Overview

Invoke the **`feature-screen-generator`** skill to scaffold the 6-file MVVM bundle in `presentation/feature/subject/create_subject/`, then specialize per the form. Use KomoUI `Input` and `Button` (as in `RegisterScreen`), the local IME-aware scroll-to-focused-field helper, Material3 `Switch` for the two booleans, a 2-button segmented selector for sex, and a plain Spanish `DD/MM/YYYY` text input for birth date.

> Implementer: call the skill with `feature_group=subject`, `feature_path=create_subject`, `FeatureName=CreateSubject`, no route params. The skill templates assume the `com.extra.marketplace` package — substitute `ar.com.ingenieriaalimentacion`. Strip all `GUIDELINES` comment blocks from generated output as the skill instructs.

### Changes Required

#### 1. Action

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/subject/create_subject/CreateSubjectAction.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex

sealed interface CreateSubjectAction {
    data class DniChanged(val value: String) : CreateSubjectAction
    data class SexChanged(val value: Sex) : CreateSubjectAction
    data class NameChanged(val value: String) : CreateSubjectAction
    data class LastNameChanged(val value: String) : CreateSubjectAction
    data class BirthDateChanged(val value: String) : CreateSubjectAction
    data class IsRuralChanged(val value: Boolean) : CreateSubjectAction
    data class IsIndigenousChanged(val value: Boolean) : CreateSubjectAction
    data object Submit : CreateSubjectAction
    data object DismissSuccess : CreateSubjectAction
    data object BackClick : CreateSubjectAction
}
```

#### 2. Destination

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/subject/create_subject/CreateSubjectDestination.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

sealed class CreateSubjectDestination {
    data object Back : CreateSubjectDestination()
}
```

#### 3. State

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/subject/create_subject/CreateSubjectState.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import ar.com.ingenieriaalimentacion.domain.model.subject.Sex

data class CreateSubjectState(
    val destination: CreateSubjectDestination? = null,
    val dni: String = "",
    val sex: Sex? = null,
    val name: String = "",
    val lastName: String = "",
    val birthDate: String = "",       // DD/MM/YYYY as typed
    val isRural: Boolean = false,
    val isIndigenous: Boolean = false,
    val isSubmitting: Boolean = false,
    val fieldErrors: CreateSubjectFieldErrors = CreateSubjectFieldErrors(),
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = dni.isNotBlank() && sex != null && name.isNotBlank() &&
            lastName.isNotBlank() && birthDate.isNotBlank() && !isSubmitting
}

data class CreateSubjectFieldErrors(
    val dni: String? = null,
    val sex: String? = null,
    val name: String? = null,
    val lastName: String? = null,
    val birthDate: String? = null,
)
```

#### 4. ViewModel

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/subject/create_subject/CreateSubjectViewModel.kt` (new)

Validation rules derived directly from the OpenAPI spec:
- DNI must parse to `Int` in `1..99_999_999`.
- Sex must be selected.
- Name and Last name: 1..50 chars (trimmed).
- Birth date: matches `DD/MM/YYYY`, parses to a valid `LocalDate`, not in the future.

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.com.ingenieriaalimentacion.data.subject.SubjectFailure
import ar.com.ingenieriaalimentacion.domain.model.subject.NewSubject
import ar.com.ingenieriaalimentacion.domain.model.subject.SubjectError
import ar.com.ingenieriaalimentacion.domain.usecase.subject.CreateSubjectUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CreateSubjectViewModel(
    private val createSubject: CreateSubjectUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateSubjectState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: CreateSubjectAction) {
        when (action) {
            is CreateSubjectAction.DniChanged ->
                _state.update { it.copy(dni = action.value.filter(Char::isDigit), fieldErrors = it.fieldErrors.copy(dni = null), successMessage = null) }
            is CreateSubjectAction.SexChanged ->
                _state.update { it.copy(sex = action.value, fieldErrors = it.fieldErrors.copy(sex = null), successMessage = null) }
            is CreateSubjectAction.NameChanged ->
                _state.update { it.copy(name = action.value, fieldErrors = it.fieldErrors.copy(name = null), successMessage = null) }
            is CreateSubjectAction.LastNameChanged ->
                _state.update { it.copy(lastName = action.value, fieldErrors = it.fieldErrors.copy(lastName = null), successMessage = null) }
            is CreateSubjectAction.BirthDateChanged ->
                _state.update { it.copy(birthDate = formatBirthDate(action.value), fieldErrors = it.fieldErrors.copy(birthDate = null), successMessage = null) }
            is CreateSubjectAction.IsRuralChanged ->
                _state.update { it.copy(isRural = action.value, successMessage = null) }
            is CreateSubjectAction.IsIndigenousChanged ->
                _state.update { it.copy(isIndigenous = action.value, successMessage = null) }
            CreateSubjectAction.Submit -> submit()
            CreateSubjectAction.DismissSuccess -> _state.update { it.copy(successMessage = null) }
            CreateSubjectAction.BackClick -> _state.update { it.copy(destination = CreateSubjectDestination.Back) }
        }
    }

    fun clearDestination() { _state.update { it.copy(destination = null) } }

    private fun submit() {
        val current = _state.value
        val parsed = parseAndValidate(current)
        if (parsed.errors != CreateSubjectFieldErrors()) {
            _state.update { it.copy(fieldErrors = parsed.errors) }
            return
        }
        val newSubject = parsed.value ?: return

        _state.update { it.copy(isSubmitting = true, submitError = null, successMessage = null) }
        viewModelScope.launch {
            createSubject(newSubject).fold(
                onSuccess = { subject ->
                    _state.update {
                        CreateSubjectState(
                            successMessage = "Sujeto ${subject.name} ${subject.lastName} cargado correctamente.",
                        )
                    }
                },
                onFailure = { throwable ->
                    _state.update { it.copy(isSubmitting = false, submitError = throwable.toUserMessage()) }
                },
            )
        }
    }

    private data class Parsed(val value: NewSubject?, val errors: CreateSubjectFieldErrors)

    private fun parseAndValidate(state: CreateSubjectState): Parsed {
        val dniInt = state.dni.toIntOrNull()
        val dniError = when {
            state.dni.isBlank() -> "Ingresá el DNI."
            dniInt == null || dniInt !in 1..99_999_999 -> "Ingresá un DNI válido (1 a 8 dígitos)."
            else -> null
        }
        val sexError = if (state.sex == null) "Seleccioná el sexo." else null
        val nameError = when {
            state.name.trim().isBlank() -> "Ingresá el nombre."
            state.name.trim().length > 50 -> "Máximo 50 caracteres."
            else -> null
        }
        val lastNameError = when {
            state.lastName.trim().isBlank() -> "Ingresá el apellido."
            state.lastName.trim().length > 50 -> "Máximo 50 caracteres."
            else -> null
        }
        val (birthDate, birthError) = parseBirthDate(state.birthDate)

        val errors = CreateSubjectFieldErrors(
            dni = dniError, sex = sexError, name = nameError,
            lastName = lastNameError, birthDate = birthError,
        )
        val value = if (errors == CreateSubjectFieldErrors() &&
            dniInt != null && state.sex != null && birthDate != null) {
            NewSubject(
                dni = dniInt,
                sex = state.sex,
                name = state.name.trim(),
                lastName = state.lastName.trim(),
                birthDate = birthDate,
                isRural = state.isRural,
                isIndigenous = state.isIndigenous,
            )
        } else null
        return Parsed(value, errors)
    }

    /** Keep digits + automatic slashes for `DD/MM/YYYY` mask while typing. */
    private fun formatBirthDate(input: String): String {
        val digits = input.filter(Char::isDigit).take(8)
        return buildString {
            digits.forEachIndexed { i, c ->
                if (i == 2 || i == 4) append('/')
                append(c)
            }
        }
    }

    private fun parseBirthDate(input: String): Pair<LocalDate?, String?> {
        if (input.isBlank()) return null to "Ingresá la fecha de nacimiento."
        val match = BIRTH_DATE_REGEX.matchEntire(input)
            ?: return null to "Formato esperado DD/MM/AAAA."
        val (d, m, y) = match.destructured
        val parsed = runCatching {
            LocalDate(year = y.toInt(), monthNumber = m.toInt(), dayOfMonth = d.toInt())
        }.getOrNull() ?: return null to "Fecha inválida."
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (parsed > today) return null to "La fecha no puede ser futura."
        return parsed to null
    }

    private fun Throwable.toUserMessage(): String {
        val error = (this as? SubjectFailure)?.error
        return when (error) {
            SubjectError.InvalidData -> "Los datos ingresados no son válidos. Revisá el formulario."
            SubjectError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
            SubjectError.Forbidden -> "No tenés permisos para cargar sujetos."
            SubjectError.Network -> "No pudimos conectarnos. Verificá tu conexión e intentá nuevamente."
            is SubjectError.Unknown, null -> "Ocurrió un error inesperado. Intentá nuevamente."
        }
    }

    private companion object {
        val BIRTH_DATE_REGEX = Regex("""^(\d{2})/(\d{2})/(\d{4})$""")
    }
}
```

#### 5. Navigation

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/subject/create_subject/CreateSubjectNavigation.kt` (new)

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToCreateSubject(navOptions: NavOptions? = null) =
    navigate(Route.CreateSubject, navOptions)

fun NavGraphBuilder.createSubjectScreen(navController: NavController) {
    composable<Route.CreateSubject> {
        CreateSubjectScreenRoot(
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(
    destination: CreateSubjectDestination,
    navController: NavController,
) {
    when (destination) {
        CreateSubjectDestination.Back -> navController.popBackStack()
    }
}
```

#### 6. Screen

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/subject/create_subject/CreateSubjectScreen.kt` (new)

Design notes:
- Reuse the same IME-aware `RegisterField`-style helper, renamed `LabeledInput`, **inline in this file** (do not extract to `core/design_system` yet — only one feature uses it today; once we hit a third reuse, lift it).
- Sex selector: two `Row`-laid clickable cards mirroring the establishment-trigger pattern at `RegisterScreen.kt:169-193`, but laid out side-by-side. Filled background when selected (`styles.primary` + `styles.primaryForeground`), bordered otherwise (`styles.border`). Error: red border + helper text.
- Switches: `androidx.compose.material3.Switch` with a label `Text` to its left.
- Success banner: a `Row` at the top with `styles.primary` background + `styles.primaryForeground` text + a small "×" close affordance that dispatches `DismissSuccess`. Hidden when `successMessage == null`.
- Submit `Button` is KomoUI's, full width, `loading = state.isSubmitting`, `enabled = state.canSubmit`.

Sketch (abbreviated — implementer should follow `RegisterScreen.kt` closely and copy its `RegisterField` helper renamed `LabeledInput`):

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.domain.model.subject.Sex
import com.komoui.components.Button
import com.komoui.components.Input
import com.komoui.themes.styles
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CreateSubjectScreenRoot(
    viewModel: CreateSubjectViewModel = koinViewModel(),
    navigate: (CreateSubjectDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        state.destination?.let { navigate(it); viewModel.clearDestination() }
    }
    CreateSubjectScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun CreateSubjectScreen(
    state: CreateSubjectState,
    onAction: (CreateSubjectAction) -> Unit,
) {
    val styles = MaterialTheme.styles
    val scrollState = rememberScrollState()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cargar sujeto", style = MaterialTheme.typography.headlineMedium, color = styles.foreground)

        state.successMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(styles.primary)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(msg, color = styles.primaryForeground, modifier = Modifier.weight(1f))
                Text("×", color = styles.primaryForeground,
                    modifier = Modifier.clickable { onAction(CreateSubjectAction.DismissSuccess) })
            }
        }

        LabeledInput(
            label = "DNI", value = state.dni,
            onValueChange = { onAction(CreateSubjectAction.DniChanged(it)) },
            error = state.fieldErrors.dni, enabled = !state.isSubmitting,
            scrollState = scrollState, isImeVisible = isImeVisible,
            placeholder = "Sin puntos",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        SexSelector(
            selected = state.sex,
            onSelected = { onAction(CreateSubjectAction.SexChanged(it)) },
            error = state.fieldErrors.sex,
            enabled = !state.isSubmitting,
        )

        LabeledInput(label = "Nombre", value = state.name,
            onValueChange = { onAction(CreateSubjectAction.NameChanged(it)) },
            error = state.fieldErrors.name, enabled = !state.isSubmitting,
            scrollState = scrollState, isImeVisible = isImeVisible,
            placeholder = "Nombre del sujeto")

        LabeledInput(label = "Apellido", value = state.lastName,
            onValueChange = { onAction(CreateSubjectAction.LastNameChanged(it)) },
            error = state.fieldErrors.lastName, enabled = !state.isSubmitting,
            scrollState = scrollState, isImeVisible = isImeVisible,
            placeholder = "Apellido del sujeto")

        LabeledInput(label = "Fecha de nacimiento", value = state.birthDate,
            onValueChange = { onAction(CreateSubjectAction.BirthDateChanged(it)) },
            error = state.fieldErrors.birthDate, enabled = !state.isSubmitting,
            scrollState = scrollState, isImeVisible = isImeVisible,
            placeholder = "DD/MM/AAAA",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

        ToggleRow(label = "Población rural", checked = state.isRural, enabled = !state.isSubmitting,
            onChange = { onAction(CreateSubjectAction.IsRuralChanged(it)) })
        ToggleRow(label = "Población indígena", checked = state.isIndigenous, enabled = !state.isSubmitting,
            onChange = { onAction(CreateSubjectAction.IsIndigenousChanged(it)) })

        state.submitError?.let { Text(it, color = styles.destructive, style = MaterialTheme.typography.bodyMedium) }

        Button(
            onClick = { onAction(CreateSubjectAction.Submit) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            loading = state.isSubmitting,
            fullWidth = true,
        ) { Text("Guardar") }
    }
}

@Composable
private fun SexSelector(
    selected: Sex?,
    onSelected: (Sex) -> Unit,
    error: String?,
    enabled: Boolean,
) {
    val styles = MaterialTheme.styles
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Sexo", style = MaterialTheme.typography.labelLarge, color = styles.foreground)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SexOption("Masculino", Sex.Male, selected, enabled, error != null, Modifier.weight(1f), onSelected)
            SexOption("Femenino", Sex.Female, selected, enabled, error != null, Modifier.weight(1f), onSelected)
        }
        error?.let { Text(it, color = styles.destructive, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SexOption(
    label: String, value: Sex, selected: Sex?, enabled: Boolean, hasError: Boolean,
    modifier: Modifier, onSelected: (Sex) -> Unit,
) {
    val styles = MaterialTheme.styles
    val isSelected = selected == value
    val bg = if (isSelected) styles.primary else styles.background
    val fg = if (isSelected) styles.primaryForeground else styles.foreground
    val borderColor = when {
        isSelected -> styles.primary
        hasError -> styles.destructive
        else -> styles.border
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onSelected(value) }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) { Text(label, color = fg) }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.styles.foreground)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LabeledInput(
    label: String, value: String, onValueChange: (String) -> Unit,
    error: String?, enabled: Boolean,
    scrollState: ScrollState, isImeVisible: Boolean,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val styles = MaterialTheme.styles
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var fieldScrollOffset by remember { mutableStateOf(0) }
    LaunchedEffect(isFocused, isImeVisible) {
        if (isFocused && isImeVisible) scrollState.animateScrollTo(fieldScrollOffset)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { fieldScrollOffset = it.boundsInRoot().top.toInt() + scrollState.value },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = styles.foreground)
        Input(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder, enabled = enabled,
            isError = error != null, singleLine = true,
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            supportingText = error?.let {
                { Text(it, color = styles.destructive, style = MaterialTheme.typography.bodySmall) }
            },
        )
    }
}
```

> If `styles.primaryForeground` is not present on this version of KomoUI, fall back to `styles.background` for selected-state text. Verify against the KomoUI 0.3.0 `Styles` surface at first compile; this is the only API surface I'm not 100% certain about. If KomoUI exposes only `primary`/`background`/`foreground`/`mutedForeground`/`border`/`destructive` (the ones I saw used), use `styles.background` for the selected-state text color.

#### 7. Route

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`

Add inside the sealed class, after `RegisterSuccess`:

```kotlin
@Serializable
@SerialName("create_subject")
data object CreateSubject : Route()
```

#### 8. ViewModelsModule

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/ViewModelsModule.kt`

Add the import and `viewModelOf` registration:

```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.CreateSubjectViewModel
// inside module:
viewModelOf(::CreateSubjectViewModel)
```

#### 9. AppNavHost

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/app/AppNavHost.kt`

Add the import and the call inside `NavHost`:

```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.createSubjectScreen
// inside NavHost { ... } block:
createSubjectScreen(navController)
```

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- (deferred to Phase 4 once there's an entry point)

---

## Phase 4: Entry point from Home (testability)

### Overview

Today `HomeScreen` is an almost-empty placeholder (`presentation/feature/home/HomeScreen.kt:42-59`). To exercise the new screen manually without rebuilding the auth flow, add a single `AlButton` on Home that emits a `NavigateToCreateSubject` destination. This is the minimum scaffolding to allow manual verification; a real role-aware home is out of scope.

### Changes Required

#### 1. Home destination + action

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeDestination.kt`

Add:

```kotlin
data object NavigateToCreateSubject : HomeDestination()
```

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeAction.kt`

Add a new action (the existing `PrimaryClick` is unused intent today — keep it; add a specific one):

```kotlin
data object CreateSubjectClick : HomeAction
```

#### 2. Home ViewModel

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeViewModel.kt`

In the `when (action)` block:

```kotlin
HomeAction.CreateSubjectClick ->
    _state.update { it.copy(destination = HomeDestination.NavigateToCreateSubject) }
```

#### 3. Home Screen — render the button

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeScreen.kt`

Add an `AlButton(text = "Cargar sujeto", onClick = { onAction(HomeAction.CreateSubjectClick) })` below the existing two `Text`s. Use `AlTheme.spacing.md` between elements; mirror `AlButton`'s padding rather than introducing new spacing constants.

#### 4. Home Navigation — handle the new destination

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/home/HomeNavigation.kt`

In `navigate(...)`, add:

```kotlin
HomeDestination.NavigateToCreateSubject ->
    navController.navigateToCreateSubject()
```

Plus the import:

```kotlin
import ar.com.ingenieriaalimentacion.presentation.feature.subject.create_subject.navigateToCreateSubject
```

### Success Criteria

#### Automated Verification:
- [x] Common compilation passes: `./scripts/compile.sh`
- [x] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification (the real test for this feature):
- [ ] Sign in as an approved establishment manager on Android and iOS.
- [ ] Tap "Cargar sujeto" on Home → lands on CreateSubject screen.
- [ ] Submitting empty form shows per-field validation errors.
- [ ] DNI input rejects non-digits; rejects 0 and > 99_999_999.
- [ ] Birth date input auto-inserts slashes; rejects invalid dates (e.g., `32/13/2020`) and future dates.
- [ ] Sex selector visually toggles between Masculino / Femenino.
- [ ] Switches toggle for rural / indigenous.
- [ ] On valid submission, button shows loading spinner, then the success banner appears at top and the form clears.
- [ ] Submitting again from the cleared form creates another subject (verifies `_state` reset is complete).
- [ ] Forcing a 401 (e.g., manually expiring the token) shows "Tu sesión expiró. Iniciá sesión nuevamente."
- [ ] Backgrounding the app and reopening while on this screen does not crash and preserves the form (via `WhileSubscribed(5000L)` behavior).
- [ ] Focusing an input near the bottom while the keyboard is open scrolls the input fully into view on **iOS** (the documented CLAUDE.md edge case).
- [ ] Back gesture / system back returns to Home.
- [ ] No regression on Register / Login / Home / Select Establishment flows.

---

## Testing Strategy

### Manual Testing Steps

Performed in Phase 4 success criteria above. Run on both an iOS simulator and an Android emulator/device.

### Automated Tests

None added in this plan. The repo currently has no `commonTest` infrastructure for ViewModels or Repositories — introducing one (with `kotlinx-coroutines-test`, `turbine`, `ktor-client-mock`, all already in the version catalog) is a separate, larger workstream. If the user wants tests as part of this feature, escalate before starting Phase 1 and we'll add a Phase 0 that stands up the test source set.

## Performance Considerations

- `WhileSubscribed(5_000L)` already applied in the ViewModel — no extra hot-flow leakage.
- Single network call on submit; no polling, no parallelism needed.
- Form recomposition is bounded by the seven `String`/`Boolean` state fields; nothing to optimize.

## Migration Notes

None — net-new files plus three small append-only edits per DI module plus three small additions in Home. Fully reversible by deleting `data/subject/`, `domain/{model,usecase,repository}/subject/`, `presentation/feature/subject/`, and reverting the four DI files + `Route.kt` + `AppNavHost.kt` + the four Home files.

## References

- OpenAPI spec: `docs/backend.openapi.generated.json:447` (endpoint), `:730` (Sex), `:739` (SubjectRequest), `:790` (SubjectResponse), `:906` (ProblemDetail)
- Closest precedent feature: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/*`
- Closest precedent data layer: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/data/registration/*`
- Scaffolding skill: `.claude/skills/feature-screen-generator/SKILL.md` (note: package substitution required — see Phase 3 callout)
- IME scroll pattern: `CLAUDE.md` "Scroll-to-field on keyboard open (CMP pattern)" + working impl at `presentation/feature/register/RegisterScreen.kt:215-267`
- DI surface to extend: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/di/{ClientsModule,RepositoriesModule,UseCasesModule,ViewModelsModule}.kt`
