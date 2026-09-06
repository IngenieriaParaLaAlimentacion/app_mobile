# Cargar un ingreso de alimentos — Implementation Plan

**Story**: `thoughts/stories/create_food_intake_screen.md` (+ contract research in
`thoughts/stories/food_stock_management.md`)
**Research**: `thoughts/research/2026-09-06_14-29-17_food_intake_logging_screens.md`
**Branch**: `feature/food-intake-logging` · **Base**: `develop`
**Date**: 2026-09-06

---

## Overview

Add a single online-only screen, `create_food_intake`, where an authenticated
relevador records what an establishment received: an establishment, a reception
date, optional comments and **one or more lines of food + quantity**. Foods are
picked with an **inline typeahead** against the merged food catalog, and the
whole intake is submitted live to `POST /api/v1/food-intakes` with a
client-generated UUID as an idempotency key.

**No SQLDelight.** This is the first write screen in the app that does not touch
the local database: no table, no migration, no `SyncEngine`, no offline queue.

---

## Deviation from the story: one screen, not two

The story specifies **two** screens — a full-screen `select_food` picker that
returns a pick through `savedStateHandle`, plus the form. **Product decided on
2026-09-06 to replace the picker with an inline typeahead** inside the form: the
user types into the "Agregar alimento" field, matches appear in a dropdown, and
tapping one adds the line in place.

Consequences, all deliberate:

- **No `select_food` route, no `savedStateHandle` round-trip, no picker
  package.** `select_subject`'s result-return pattern is *not* used here.
- **No category chips.** With a typeahead the category filter is dead weight;
  `GET /food-catalog/categories` is not called at all, so no chip-row component
  is needed (the repo has none, and now does not need one).
- **No pagination.** The dropdown fetches `page_size=8` and shows the count
  ("8 de 143") to nudge a more specific query. The `page`/`totalPages`
  machinery from `request_establishment` is **not** carried over; only its
  debounce + `collectLatest` cancellation is.

What *is* still copied from `request_establishment`: the second
`MutableStateFlow<String>` for debouncing (`RequestEstablishmentViewModel.kt:28-36`),
`DEBOUNCE_MS = 300L`, and the `if (throwable is CancellationException) throw throwable`
guard so `collectLatest` cancellation is not swallowed into an error state.

---

## Current State Analysis

### What already exists and will be reused verbatim

| Need | Existing piece |
|---|---|
| Debounced live search | `RequestEstablishmentViewModel.kt:28-36,81-100` |
| Status-branching authed API + `type`-discriminated problem+json | `SurveyResponseApi.kt:33-53` + `SurveyResponseConflictProblem.kt:17-33` |
| Blur-aware per-field validation | `core/validation/TextFieldState.kt`, `core/validation/Rules.kt` |
| IME scroll-into-view | `AlInput.kt:69-88` (screens supply `scrollState` + `isImeVisible`) |
| Establishment pre-fill (not gate) | `MeasurementViewModel.kt:109-136`, line `:122` |
| In-form establishment selector | `AlSelectField` + `ModalBottomSheet` — `MeasurementScreen.kt:122-143,173-188` |
| Client-generated idempotency UUID | `domain/util/IdGenerator.kt`, DI at `RepositoriesModule.kt:75` |
| ISO-8601 UTC wire dates | `Instant.fromEpochMilliseconds(ms).toString()` — `MeasurementRepositoryImpl.kt:35-41` |
| Date entry (DD/MM/AAAA digits buffer) | `DateVisualTransformation.kt` + `CreateSubjectScreen.kt:141-153` |
| Not-future date validation | `CreateSubjectViewModel.kt:207-229` (`parseBirthDate` + `today()`) |
| Connectivity | `ConnectivityObserver.isOnline: Flow<Boolean>` — consumed like `HomeViewModel.kt:36,61` |
| Success feedback | `AlToast` (BoxScope extension), `AlInlineBanner` |
| Delete icon | `ic_mingcute_delete.xml` (exists) |

### Gaps that this plan closes

1. **No suffix slot on `AlInput`** — only `trailingIcon`. → new `AlUnitInput`.
2. **No autocomplete/dropdown component anywhere.** → new `AlAutocompleteInput`.
3. **`sanitizeDecimalInput` caps nothing** (`DecimalInput.kt:7-14`) — no decimal-place
   limit, no digit limit, no integer variant. → two new sanitizers.
4. **No food icon** among the 95 MingCute drawables. → one new icon via the
   `add-mingcute-icon` skill.
5. **Home's quick actions are a fixed 3-wide `Row`** (`HomeScreen.kt:243-268`). → 2×2 grid.

### Backend state (verified against `origin` on 2026-09-06 via `gh`)

- ✅ `GET /api/v1/food-catalog/foods` — **merged to `develop`** (PR #119).
  `search` (max 200), `category_id`, `sort_order=name_asc|name_desc`,
  `page` (≥1), `page_size` (1–100, default 20).
  Envelope: `{items, total, page, page_size, total_pages}`.
  Item: `{id, code, name, base_unit, category:{id,name}}`,
  `base_unit ∈ "unidades" | "kilogramos" | "litros"`.
- ✅ `GET /api/v1/food-catalog/categories` — merged. **Not used by this plan.**
- ⏳ `POST /api/v1/food-intakes` — **only in PR #126, still a DRAFT**, stacked on
  the force-push-rebased #123. Contract pinned from
  `app/schemas/food_intakes/food_intake_schema.py@feat/food-stock-per-establishment`:
  - `id: UUID | None` — optional client-generated idempotency key. When present
    and already stored, the service returns the existing intake with **200** and
    **does not touch the stock** (`food_intake_service.py`, `register()`).
  - `establishment_id: UUID` (required)
  - `received_at: datetime` — validated **not future** (`validate_not_future_datetime`)
  - `comments: str | None`, max 500
  - `source: FoodIntakeSource = MANUAL`
  - `items: list[{food_id: UUID, quantity: Decimal(gt=0, max_digits=10, decimal_places=3)}]`, min 1
- Error `type`s confirmed in `docs/errors.json@develop`:
  `403 /errors/establishment-access-denied`, `404 /errors/establishment-not-found`,
  `404 /errors/food-not-found` (already registered on `develop`).

**The submit path is built now against this pinned contract.** It compiles and is
unit-testable end to end with a `MockEngine`; only real device verification of
the POST waits on #123 + #126 merging.

### Known backend limitations we ship around (recorded, not acted on)

- Search is `ILIKE %term%` on `name` only, **no `unaccent`** — "azucar" will not
  find "Azúcar". With no local cache the app cannot compensate. Shipping as-is.
- **No relevance ranking.** `FoodRepository.list` orders strictly by
  `lower(name) ASC` — page 1 of a search is the alphabetically first matches, not
  the best ones. This is why the dropdown shows a total count and asks the user
  to keep typing rather than paginating.
- The backend accepts `12.5` for a `unidades` food. The integer rule is
  **client-side only**.
- There is **no intake list endpoint**, so the app cannot show "what I loaded".

---

## Desired End State

From Home, a relevador taps **Alimentos**, lands on *Cargar ingreso*, sees their
establishment already selected, types "pollo", taps a suggestion, types the
kilos, adds a second line, and saves. The button spins, the form clears, a toast
confirms, a fresh idempotency id is generated and they can load the next intake.
Offline, the screen says so and refuses to load.

Verified by: `./scripts/compile.sh` green, the new unit tests green, and the
manual checklist at the bottom.

---

## What We're NOT Doing

- No `select_food` screen, no category filter, no chip-row component.
- No pagination / "load more" in the dropdown.
- No SQLDelight table, migration, `SyncEngine`, or offline queue.
- No `GET /food-catalog/categories` call, no `GET /food-catalog/foods/{id}`
  (nutrient composition), no stock screen, no intake history, no edit/delete of a
  saved intake, no egresos (`POST /food-outflows`), no photo capture (US-4).
- No unit selector — `base_unit` is displayed, never chosen, never sent.
- No `grams_per_unit` conversion ("3 zapallos" → kg). Backend decision A stands.
- No backend issues filed.

---

## Implementation Approach

Bottom-up in six phases, each independently compilable:
data → design system → screen → submit → entry point → tests.

Two decisions worth calling out before the phases:

**Where the idempotency UUID lives.** Every existing user of `IdGenerator`
(subjects, measurements, survey responses, survey edits) parks the id in a
SQLDelight `localId`. This feature has no DB, so the id lives in
**ViewModel state** (`CreateFoodIntakeState.intakeId`), generated in the VM's
`init` and regenerated only after a **successful** save. It therefore survives
configuration changes and every retry, which is the whole point — a retry after
a timeout cannot double-count stock. It does *not* survive process death, and
that is fine: nothing else in the form does either, so there would be nothing to
re-submit. This is a deliberate deviation from the four existing call sites and
is the reason `IdGenerator` is injected into a ViewModel for the first time.

**How `received_at` is built.** The repo has no date picker (zero
`DatePicker`/`rememberDatePickerState` matches) and all date entry is a
digits-only `DDMMYYYY` buffer behind `DateVisualTransformation`. So:

- The field is a `DDMMYYYY` buffer pre-filled with **today**, rendered `DD/MM/AAAA`.
- If the entered date is **today**, `received_at` = `Clock.System.now()` — the
  actual capture instant, never future.
- If the entered date is **in the past**, `received_at` =
  `date.atStartOfDayIn(TimeZone.currentSystemDefault())` — unambiguously
  non-future.
- A **future** date fails validation client-side ("La fecha no puede ser futura."),
  matching `CreateSubjectViewModel.kt:207-218`.

The ViewModel computes epoch millis; the **repository** formats the ISO string
with `Instant.fromEpochMilliseconds(ms).toString()`, exactly like the three
existing sites.

---

## Phase 1 — Data + domain layer

### Overview
Everything below the ViewModel: DTOs, two APIs, mappers, domain models, two
repositories, two use cases, DI. No UI.

### Changes Required

#### 1. Domain models
**File**: `domain/model/food/Food.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.food

/** Unit a food's quantity is expressed in. Comes from the catalog's `base_unit`
 *  and is never chosen by the user (see the story's "opción A" decision). */
enum class FoodUnit { Unidades, Kilogramos, Litros }

/** A catalog row as the typeahead needs it. */
data class Food(
    val id: String,
    val code: String,
    val name: String,
    val unit: FoodUnit,
    val categoryName: String,
)

/** One page of catalog matches. [total] drives the "8 de 143" hint — the dropdown
 *  never paginates, it asks the user to type more. */
data class FoodSearchResult(
    val items: List<Food>,
    val total: Int,
)
```

**File**: `domain/model/food/NewFoodIntake.kt` (new)
```kotlin
package ar.com.ingenieriaalimentacion.domain.model.food

/**
 * An intake ready to send. [id] is the client-generated UUID v4 idempotency key,
 * held by the ViewModel across retries so a retried submit cannot inflate stock.
 * [quantity] stays a normalized `String` (dot separator, ≤3 decimals) all the way
 * to the wire so no float rounding reaches a balance the server accumulates.
 */
data class NewFoodIntake(
    val id: String,
    val establishmentId: String,
    val receivedAtEpochMillis: Long,
    val comments: String?,
    val items: List<NewFoodIntakeLine>,
)

data class NewFoodIntakeLine(
    val foodId: String,
    val quantity: String,
)
```

**File**: `domain/model/food/FoodIntakeError.kt` (new) — mirrors
`domain/model/survey/SurveyFeatureError.kt:5-13`.
```kotlin
sealed interface FoodIntakeError {
    data object Unauthorized : FoodIntakeError               // 401
    data object EstablishmentAccessDenied : FoodIntakeError  // 403 + type
    data object EstablishmentNotFound : FoodIntakeError      // 404 + type
    data object FoodNotFound : FoodIntakeError               // 404 + type
    data object InvalidData : FoodIntakeError                // 400 / 422
    data object Network : FoodIntakeError                    // transport / 5xx
    data class Unknown(val cause: Throwable?) : FoodIntakeError
}

class FoodIntakeFailure(val error: FoodIntakeError, cause: Throwable?) :
    Exception(error.toString(), cause)
```

#### 2. DTOs
**File**: `data/food/dto/FoodDto.kt` (new)
```kotlin
@Serializable
data class FoodCategoryRefDto(val id: String, val name: String)

@Serializable
data class FoodListItemDto(
    val id: String,
    val code: String,
    val name: String,
    @SerialName("base_unit") val baseUnit: String,   // "unidades" | "kilogramos" | "litros"
    val category: FoodCategoryRefDto,
)

@Serializable
data class PaginatedFoodsResponse(
    val items: List<FoodListItemDto>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```

**File**: `data/food/dto/FoodIntakeRequest.kt` (new)
```kotlin
/**
 * [id] is the client-generated UUID (idempotency key): a replay of the same id
 * answers 200 and leaves the stock untouched. [receivedAt] is ISO 8601 UTC with
 * zone (`…Z`) and must not be in the future. The line carries **no unit** — the
 * server resolves it from the referenced food's `base_unit`.
 */
@Serializable
data class FoodIntakeRequest(
    val id: String,
    @SerialName("establishment_id") val establishmentId: String,
    @SerialName("received_at") val receivedAt: String,
    val comments: String? = null,
    val source: String = "manual",
    val items: List<FoodLineRequest>,
)

@Serializable
data class FoodLineRequest(
    @SerialName("food_id") val foodId: String,
    @Serializable(with = QuantitySerializer::class) val quantity: String,
)
```

**File**: `data/food/dto/QuantitySerializer.kt` (new)
```kotlin
/**
 * Writes an already-normalized decimal `String` ("12.5", "0.750") as a **JSON
 * number**, not a quoted string, without ever converting through `Double`.
 * The server stores quantity in a `Numeric(10,3)` balance it accumulates, so no
 * float noise may reach it. Reading accepts either a JSON number or a string.
 */
@OptIn(ExperimentalSerializationApi::class)
object QuantitySerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("Quantity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        val json = encoder as? JsonEncoder
            ?: error("QuantitySerializer requires a JSON encoder")
        json.encodeJsonElement(JsonUnquotedLiteral(value))
    }

    override fun deserialize(decoder: Decoder): String = decoder.decodeString()
}
```
> kotlinx-serialization is 1.11.0 (`libs.versions.toml:15`), so
> `JsonUnquotedLiteral` is available. It is `@ExperimentalSerializationApi`.

**File**: `data/food/dto/FoodIntakeResponse.kt` (new) — only what the app reads back.
```kotlin
@Serializable
data class FoodIntakeResponse(val id: String)
```

**File**: `data/food/dto/FoodIntakeProblem.kt` (new) — the `type` discriminator.
Mirrors `data/survey/dto/SurveyResponseConflictProblem.kt:17-33`, including its
KDoc rationale: two different 404s share a status code, so branching on the
status alone conflates them.
```kotlin
@Serializable
data class FoodIntakeProblem(val type: String? = null)

const val ESTABLISHMENT_NOT_FOUND_TYPE = "/errors/establishment-not-found"
const val FOOD_NOT_FOUND_TYPE = "/errors/food-not-found"
const val ESTABLISHMENT_ACCESS_DENIED_TYPE = "/errors/establishment-access-denied"
```

#### 3. APIs
**File**: `data/food/FoodCatalogApi.kt` (new) — read, `SubjectApi.list` style
(`SubjectApi.kt:49-57`): check `isSuccess()`, else `error(...)`, let the
repository's `runCatching` turn it into a failed `Result`.
```kotlin
class FoodCatalogApi(private val client: HttpClient) {
    suspend fun searchFoods(search: String?, pageSize: Int): PaginatedFoodsResponse {
        val response = client.get("api/v1/food-catalog/foods") {
            parameter("page", 1)
            parameter("page_size", pageSize)
            parameter("sort_order", "name_asc")
            search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
        }
        if (!response.status.isSuccess()) error("food search failed: ${response.status.value}")
        return response.body()
    }
}
```

**File**: `data/food/FoodIntakeApi.kt` (new) — write, full status branching with
`type` discrimination, modelled on `SurveyResponseApi.kt:27-57`.
```kotlin
class FoodIntakeApi(private val client: HttpClient) {

    suspend fun create(body: FoodIntakeRequest): FoodIntakeUploadOutcome = runCatching {
        val response = client.post("api/v1/food-intakes") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        when (response.status.value) {
            // 201 = created, 200 = idempotent replay of the same id. Same meaning
            // to the app: the intake is registered.
            200, 201 -> FoodIntakeUploadOutcome.Created(response.body())
            400, 422 -> FoodIntakeUploadOutcome.InvalidData
            401 -> FoodIntakeUploadOutcome.Unauthorized
            403 -> FoodIntakeUploadOutcome.EstablishmentAccessDenied
            404 -> when (response.problemType()) {
                FOOD_NOT_FOUND_TYPE -> FoodIntakeUploadOutcome.FoodNotFound
                ESTABLISHMENT_NOT_FOUND_TYPE -> FoodIntakeUploadOutcome.EstablishmentNotFound
                // A 404 this build doesn't recognize is still terminal — retrying
                // the same body can only 404 again.
                else -> FoodIntakeUploadOutcome.InvalidData
            }
            else -> FoodIntakeUploadOutcome.Retryable(null)   // 5xx
        }
    }.getOrElse { t ->
        if (t is CancellationException) throw t
        FoodIntakeUploadOutcome.Retryable(t)                  // transport failures
    }

    private suspend fun HttpResponse.problemType(): String? =
        runCatching { body<FoodIntakeProblem>() }.getOrNull()?.type
}

sealed interface FoodIntakeUploadOutcome {
    data class Created(val response: FoodIntakeResponse) : FoodIntakeUploadOutcome
    data object EstablishmentAccessDenied : FoodIntakeUploadOutcome
    data object EstablishmentNotFound : FoodIntakeUploadOutcome
    data object FoodNotFound : FoodIntakeUploadOutcome
    data object InvalidData : FoodIntakeUploadOutcome
    data object Unauthorized : FoodIntakeUploadOutcome
    data class Retryable(val cause: Throwable?) : FoodIntakeUploadOutcome
}
```

> **Do not** copy `ProfileRepositoryImpl`'s `is ClientRequestException ->` style.
> Both APIs sit on the authed client, which does **not** set `expectSuccess`
> (`HttpClientFactory.kt:57` vs `:38`), so Ktor never throws there — that mapping
> is dead code in production today (`ProfileRepositoryImpl.kt:106-140`).

#### 4. Mapper
**File**: `data/food/mapper/FoodMapper.kt` (new)
```kotlin
internal fun FoodListItemDto.toDomain(): Food = Food(
    id = id,
    code = code,
    name = name,
    unit = baseUnit.toFoodUnit(),
    categoryName = category.name,
)

internal fun PaginatedFoodsResponse.toDomain(): FoodSearchResult =
    FoodSearchResult(items = items.map { it.toDomain() }, total = total)

private fun String.toFoodUnit(): FoodUnit = when (this) {
    "unidades" -> FoodUnit.Unidades
    "litros" -> FoodUnit.Litros
    else -> FoodUnit.Kilogramos   // 767 of 898 catalog rows; the safe default
}

internal fun NewFoodIntake.toRequest(receivedAtIso: String): FoodIntakeRequest = ...
```

#### 5. Repositories
**File**: `domain/repository/FoodCatalogRepository.kt` (new)
```kotlin
interface FoodCatalogRepository {
    suspend fun searchFoods(search: String?, pageSize: Int): Result<FoodSearchResult>
}
```
**File**: `domain/repository/FoodIntakeRepository.kt` (new)
```kotlin
interface FoodIntakeRepository {
    /** Live POST. Failure carries a [FoodIntakeFailure] with a typed [FoodIntakeError]. */
    suspend fun createIntake(intake: NewFoodIntake): Result<Unit>
}
```

**File**: `data/food/FoodCatalogRepositoryImpl.kt` (new) — `runCatching { api.searchFoods(...).toDomain() }`.

**File**: `data/food/FoodIntakeRepositoryImpl.kt` (new) — stamps the ISO date and
folds the outcome, in the `SurveyRepositoryImpl.kt:44-57` style:
```kotlin
class FoodIntakeRepositoryImpl(private val api: FoodIntakeApi) : FoodIntakeRepository {
    override suspend fun createIntake(intake: NewFoodIntake): Result<Unit> {
        // ISO 8601 UTC with zone (…Z) — same stamping as MeasurementRepositoryImpl:35-41.
        val receivedAt = Instant.fromEpochMilliseconds(intake.receivedAtEpochMillis).toString()
        return when (val outcome = api.create(intake.toRequest(receivedAt))) {
            is FoodIntakeUploadOutcome.Created -> Result.success(Unit)
            FoodIntakeUploadOutcome.EstablishmentAccessDenied -> fail(FoodIntakeError.EstablishmentAccessDenied)
            FoodIntakeUploadOutcome.EstablishmentNotFound -> fail(FoodIntakeError.EstablishmentNotFound)
            FoodIntakeUploadOutcome.FoodNotFound -> fail(FoodIntakeError.FoodNotFound)
            FoodIntakeUploadOutcome.InvalidData -> fail(FoodIntakeError.InvalidData)
            FoodIntakeUploadOutcome.Unauthorized -> fail(FoodIntakeError.Unauthorized)
            is FoodIntakeUploadOutcome.Retryable -> fail(FoodIntakeError.Network, outcome.cause)
        }
    }
}
```

#### 6. Use cases
**File**: `domain/usecase/food/SearchFoodsUseCase.kt` (new) — pass-through with the
page-size default, mirroring `GetAvailableEstablishmentsUseCase.kt:7-11`.
```kotlin
class SearchFoodsUseCase(private val repository: FoodCatalogRepository) {
    suspend operator fun invoke(search: String?): Result<FoodSearchResult> =
        repository.searchFoods(search, pageSize = SUGGESTION_PAGE_SIZE)

    companion object { const val SUGGESTION_PAGE_SIZE = 8 }
}
```
**File**: `domain/usecase/food/CreateFoodIntakeUseCase.kt` (new) — pass-through.

#### 7. DI
- `di/ClientsModule.kt` — after `:56`:
  `single { FoodCatalogApi(client = get()) }`, `single { FoodIntakeApi(client = get()) }`
  (default authed client, **no** `named("refresh")`).
- `di/RepositoriesModule.kt` —
  `single<FoodCatalogRepository> { FoodCatalogRepositoryImpl(api = get()) }`,
  `single<FoodIntakeRepository> { FoodIntakeRepositoryImpl(api = get()) }`.
- `di/UseCasesModule.kt` — `factory { SearchFoodsUseCase(get()) }`,
  `factory { CreateFoodIntakeUseCase(get()) }`.
- **No** `di/DatabaseModule.kt` change — there is no table.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh` passes
- [x] `./gradlew :composeApp:testProdDebugUnitTest --no-daemon` still green

#### Manual Verification:
- [x] None yet — no UI in this phase.

---

## Phase 2 — Design-system pieces

### Overview
Three reusable additions plus one icon. Everything lands in
`core/design_system/` except the sanitizers, which extend the existing
`domain/util/DecimalInput.kt`.

### Changes Required

#### 1. Quantity sanitizers
**File**: `domain/util/DecimalInput.kt` (extend — keep `sanitizeDecimalInput` untouched,
it has two live call sites at `MeasurementViewModel.kt:51,62` and `SurveyQuestionForm.kt:79-84`)
```kotlin
/**
 * Like [sanitizeDecimalInput] but bounded: at most [maxIntegerDigits] before the
 * separator and [maxDecimals] after it. The food-intake quantity is a server-side
 * `Numeric(10,3)`, so 7 integer digits + 3 decimals is the hard ceiling
 * (`9999999.999`); typing past it is dropped rather than rejected on submit.
 */
fun sanitizeQuantityInput(
    raw: String,
    maxIntegerDigits: Int = 7,
    maxDecimals: Int = 3,
): String

/** Digits only, capped at [maxDigits] — for foods whose `base_unit` is `unidades`. */
fun sanitizeIntegerInput(raw: String, maxDigits: Int = 7): String =
    raw.filter(Char::isDigit).take(maxDigits)
```

#### 2. `AlUnitInput` — the quantity field with its unit
**File**: `core/design_system/components/input/AlUnitInput.kt` (new)

A thin wrapper over `AlInput` that renders the unit in the trailing slot (the
same trick `AlSelectField.kt:68-74` uses for its chevron) using the mono data
face, so the unit reads as a fixed property of the field rather than a choice.
```kotlin
/**
 * Numeric field with a fixed, non-editable unit suffix (kg / L / u). The unit is
 * a property of the value, never a choice — the catalog decides it — so it is
 * rendered inside the field rather than in a selector next to it.
 *
 * Forwards [scrollState] / [isImeVisible] to [AlInput] so the IME
 * scroll-into-view behavior works unchanged inside a scrolling form.
 */
@Composable
fun AlUnitInput(
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    error: String? = null,
    enabled: Boolean = true,
    integerOnly: Boolean = false,
    scrollState: ScrollState? = null,
    isImeVisible: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
) = AlInput(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    label = label,
    placeholder = placeholder,
    error = error,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(
        keyboardType = if (integerOnly) KeyboardType.Number else KeyboardType.Decimal,
    ),
    trailingIcon = {
        Text(text = unit, style = AlTheme.typography.data, color = AlTheme.colors.textMuted)
    },
    scrollState = scrollState,
    isImeVisible = isImeVisible,
    onFocusChange = onFocusChange,
)
```
> `integerOnly` picks `KeyboardType.Number` (no decimal separator) vs `Decimal`,
> matching the census at `SurveyQuestionForm.kt:128` / `MeasurementScreen.kt:199`.

#### 3. `AlAutocompleteInput` — the typeahead
**File**: `core/design_system/components/input/AlAutocompleteInput.kt` (new)

The one genuinely new interaction in the app. Deliberately dumb: it owns *no*
search state, only layout and the dropdown. Debounce, cancellation and fetching
stay in the ViewModel.
```kotlin
/** One row of the suggestion dropdown. */
data class AlSuggestion(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val trailing: String? = null,
)

/**
 * Text field with a suggestion dropdown anchored underneath it.
 *
 * State-free by design: the caller owns [value], [suggestions] and the loading /
 * error flags, so debouncing and request cancellation live in the ViewModel
 * where they can be tested. The dropdown renders only while [expanded] — the
 * caller ties that to focus.
 *
 * The list is capped to a fixed max height and scrolls; it deliberately does not
 * paginate. The backend orders matches alphabetically, not by relevance, so
 * [footer] is where the caller tells the user to type more (e.g. "8 de 143").
 */
@Composable
fun AlAutocompleteInput(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<AlSuggestion>,
    onSuggestionClick: (AlSuggestion) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    expanded: Boolean = false,
    isLoading: Boolean = false,
    emptyText: String? = null,
    errorText: String? = null,
    onRetry: (() -> Unit)? = null,
    footer: String? = null,
    onFocusChange: (Boolean) -> Unit = {},
)
```
Rendering rules:
- The field itself is an `AlInput` with `leadingIcon`-equivalent search affordance
  via `trailingIcon = { Icon(vectorResource(Res.drawable.ic_mingcute_search_line), tint = AlTheme.colors.textMuted) }`.
- Dropdown is a `Column` inside a `Surface`-like `Box` with
  `AlTheme.colors.surfaceBase`, `AlCorners.md`, `AlBorders.thin` border, capped at
  `MAX_DROPDOWN_HEIGHT = 280.dp`, scrolling with `LazyColumn`.
- Four states inside the dropdown, same shape as the three existing pickers:
  loading (`Spinner`), error (message + `AlButton("Reintentar")` when `onRetry != null`),
  empty (`emptyText`), results (`AlListRow(horizontalPadding = 0.dp, …)` per suggestion).
- `footer` renders as an `AlTheme.typography.caption` line under the list.

#### 4. New icon
Run the `add-mingcute-icon` skill for a food icon for Home's quick action —
`bowl_line` preferred, `shopping_bag_1_line` as the fallback if unavailable.
Then rebuild once so the `Res.drawable.*` accessor is generated. Existing icons
cover the rest: `ic_mingcute_delete` (remove a line), `ic_mingcute_search_line`
(the typeahead), `ic_mingcute_add_line`.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh` passes
- [x] `Res.drawable.ic_mingcute_bowl_line` resolves after
      `./gradlew :composeApp:generateResourceAccessorsForCommonMain --no-daemon`

> The `add-mingcute-icon` driver had to be pinned to MingCute `v2.97` first:
> upstream `v3.0.0` restructured the repo to
> `assets/svg/core/{regular,filled}/<category>/<name>.svg`, dropping the flat
> `svg/<category>/<name>_line.svg` layout every existing `ic_mingcute_*` came from,
> so the driver could not find *any* icon. Pre-existing breakage, fixed in passing.

#### Manual Verification:
- [ ] Dropdown does not clip at the screen edge. **Design change:** it expands
      *inline* in the layout flow rather than floating over the content — it lives
      in a `verticalScroll` column whose fields already scroll themselves clear of
      the keyboard, and an inline panel cannot clip or fight that scroll.
- [ ] Unit suffix in `AlUnitInput` is visually a suffix, not a tappable affordance
- [x] Typing `1.2.3` in a quantity field yields `1.23`; typing a 4th decimal is
      dropped — covered by `DecimalInputTest`

---

## Phase 3 — The `create_food_intake` screen

### Overview
The six canonical files plus one feature-local component, the route, and the
`AppNavHost` wiring. Everything except the network submit, which is Phase 4.

Package: `presentation/feature/food/create_food_intake/`

### Changes Required

#### 1. `CreateFoodIntakeState.kt`
```kotlin
/** One line of the intake. The food's [Food.unit] drives both the keyboard and
 *  the validator, so a line owns its own validation rule. */
data class FoodIntakeLine(
    val food: Food,
    val quantity: TextFieldState = TextFieldState(),
)

data class FoodSearchState(
    val query: String = "",
    val results: List<Food> = emptyList(),
    val total: Int = 0,
    val isSearching: Boolean = false,
    val error: Boolean = false,
    val isFocused: Boolean = false,
) {
    val isExpanded: Boolean get() = isFocused && query.isNotBlank()
    /** "8 de 143" — the backend sorts alphabetically, so a deeper match needs a
     *  narrower query, not another page. */
    val footer: String? get() =
        if (total > results.size) "Mostrando ${results.size} de $total · seguí escribiendo" else null
}

data class CreateFoodIntakeState(
    val destination: CreateFoodIntakeDestination? = null,
    val intakeId: String = "",
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val establishmentOptions: List<EstablishmentWithStatus> = emptyList(),
    val selectedEstablishment: EstablishmentWithStatus? = null,
    val establishmentError: String? = null,
    val showEstablishmentPicker: Boolean = false,
    val receivedAt: TextFieldState = TextFieldState(),   // raw DDMMYYYY digits
    val comments: TextFieldState = TextFieldState(),
    val lines: List<FoodIntakeLine> = emptyList(),
    val search: FoodSearchState = FoodSearchState(),
    val isOnline: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val commentsCount: String get() = "${comments.value.length}/$MAX_COMMENTS"
    val canSubmit: Boolean get() =
        isOnline && !isSubmitting && lines.isNotEmpty() && selectedEstablishment != null
}
```
> `canSubmit` gates on *presence*, not validity, mirroring
> `MeasurementState.kt:23-26`; `submit()` force-shows errors and refuses to build
> the model if anything is invalid.

#### 2. `CreateFoodIntakeAction.kt`
```kotlin
sealed interface CreateFoodIntakeAction {
    data object EstablishmentPickerOpen : CreateFoodIntakeAction
    data object EstablishmentPickerDismiss : CreateFoodIntakeAction
    data class EstablishmentSelected(val establishment: EstablishmentWithStatus) : CreateFoodIntakeAction
    data class ReceivedAtChanged(val value: String) : CreateFoodIntakeAction
    data class ReceivedAtFocusChanged(val focused: Boolean) : CreateFoodIntakeAction
    data class CommentsChanged(val value: String) : CreateFoodIntakeAction
    data class CommentsFocusChanged(val focused: Boolean) : CreateFoodIntakeAction
    data class SearchQueryChanged(val value: String) : CreateFoodIntakeAction
    data class SearchFocusChanged(val focused: Boolean) : CreateFoodIntakeAction
    data object SearchRetry : CreateFoodIntakeAction
    data class FoodPicked(val food: Food) : CreateFoodIntakeAction
    data class LineQuantityChanged(val foodId: String, val value: String) : CreateFoodIntakeAction
    data class LineQuantityFocusChanged(val foodId: String, val focused: Boolean) : CreateFoodIntakeAction
    data class LineRemoved(val foodId: String) : CreateFoodIntakeAction
    data object Submit : CreateFoodIntakeAction
    data object DismissSuccess : CreateFoodIntakeAction
    data object RetryProfile : CreateFoodIntakeAction
    data object BackClick : CreateFoodIntakeAction
}
```

#### 3. `CreateFoodIntakeDestination.kt`
```kotlin
sealed class CreateFoodIntakeDestination {
    data object Back : CreateFoodIntakeDestination()
}
```
Only `Back` — success keeps the user on the screen to load another intake, and
there is no food picker to navigate to.

#### 4. `CreateFoodIntakeNavigation.kt`
The three-part idiom (`SelectSubjectNavigation.kt`): `navigateToCreateFoodIntake()`,
`NavGraphBuilder.createFoodIntakeScreen(navController)`, and the internal
`navigate(destination, navController)` overload.

#### 5. `CreateFoodIntakeViewModel.kt`
Constructor: `searchFoods`, `createFoodIntake`, `getMyEstablishments`,
`getCurrentEstablishment`, `connectivity: ConnectivityObserver`,
`idGenerator: IdGenerator`.

Key pieces:

**Idempotency id** — generated in `init`, regenerated only after a successful save:
```kotlin
private val _state = MutableStateFlow(
    CreateFoodIntakeState(intakeId = idGenerator.newId(), receivedAt = TextFieldState(value = todayDigits())),
)
```

**Debounced search** — the `request_establishment` spine, minus pagination:
```kotlin
private val query = MutableStateFlow("")

init {
    viewModelScope.launch {
        query.debounce(DEBOUNCE_MS).distinctUntilChanged().collectLatest { search(it) }
    }
    viewModelScope.launch {
        connectivity.isOnline.collect { online -> _state.update { it.copy(isOnline = online) } }
    }
    loadEstablishments()
}

private suspend fun search(value: String) {
    if (value.isBlank()) { /* clear results, don't call */ ; return }
    _state.update { it.copy(search = it.search.copy(isSearching = true, error = false)) }
    searchFoods(value).fold(
        onSuccess = { r -> _state.update { it.copy(search = it.search.copy(results = r.items, total = r.total, isSearching = false)) } },
        onFailure = { t ->
            if (t is CancellationException) throw t   // don't swallow collectLatest cancellation
            _state.update { it.copy(search = it.search.copy(isSearching = false, error = true)) }
        },
    )
}
```

**Establishment pre-fill** — verbatim from `MeasurementViewModel.kt:109-136`,
including the `current ?: approved.singleOrNull() ?: it.selectedEstablishment`
chain and the `EstablishmentStatus.Approved` filter.

**Adding a line, with dedup** — the story's "no repetir el mismo alimento":
```kotlin
is CreateFoodIntakeAction.FoodPicked -> _state.update { current ->
    // Backend accepts duplicate lines and sums them into one balance, but two
    // rows for the same food read as a mistake — edit the existing line instead.
    val alreadyThere = current.lines.any { it.food.id == action.food.id }
    current.copy(
        lines = if (alreadyThere) current.lines else current.lines + FoodIntakeLine(action.food),
        search = FoodSearchState(),   // collapse the dropdown, clear the query
        successMessage = null,
    )
}
```
When `alreadyThere`, the Screen scrolls that line into view (see below).

**Per-line quantity validator** — chosen from the line's unit:
```kotlin
private fun validatorFor(unit: FoodUnit): Validator = when (unit) {
    // `digitsInRange` uses toIntOrNull, so "12.5" fails it — exactly the integer rule.
    // The backend does NOT enforce this (it accepts 12.5 eggs); the check is client-side only.
    FoodUnit.Unidades -> Rules.of(
        Rules.required("Ingresá la cantidad."),
        Rules.digitsInRange(1..MAX_INTEGER_QUANTITY, "Ingresá una cantidad entera."),
    )
    else -> Rules.of(
        Rules.required("Ingresá la cantidad."),
        Rules.decimalInRange(0.0, MAX_QUANTITY, "Cantidad entre 0 y 9.999.999,999."),
    )
}
```
Sanitizing happens in the reducer, like `MeasurementViewModel.kt:51`:
`sanitizeIntegerInput(value)` for `Unidades`, `sanitizeQuantityInput(value)` otherwise.

**`received_at` validator + instant** — `Rules.required` plus a raw lambda for the
date parse and the not-future check, the one place `CreateSubjectViewModel.kt:207-218`
already mixes the two:
```kotlin
private val receivedAtValidator: Validator = { raw ->
    when {
        raw.isBlank() -> "Ingresá la fecha."
        parseDate(raw) == null -> "Fecha inválida."
        parseDate(raw)!! > today() -> "La fecha no puede ser futura."
        else -> null
    }
}

/** Today → the real capture instant (never future). A past date → local midnight,
 *  which is unambiguously non-future in any timezone the device reports. */
private fun receivedAtMillis(date: LocalDate): Long {
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    return if (date == now.toLocalDateTime(zone).date) now.toEpochMilliseconds()
    else date.atStartOfDayIn(zone).toEpochMilliseconds()
}
```

**Comments** — `Rules.of(Rules.maxLength(500, "Máximo 500 caracteres."))`, the same
optional-field shape as `NotifyEstablishmentViewModel.kt:29,38`. Truncation is not
applied; the counter plus the validator carry it.

#### 6. `FoodLineRow.kt` (feature-local component)
**File**: `presentation/feature/food/create_food_intake/components/FoodLineRow.kt`
```kotlin
/**
 * One loaded line: the food's name and category, its quantity field with the
 * catalog unit as a fixed suffix, and a remove affordance. Feature-local rather
 * than a design-system component — it encodes this form's rules (unit comes from
 * the catalog, quantity is integer-only for `unidades`), not a general shape.
 */
@Composable
fun FoodLineRow(
    line: FoodIntakeLine,
    enabled: Boolean,
    scrollState: ScrollState,
    isImeVisible: Boolean,
    onQuantityChange: (String) -> Unit,
    onQuantityFocusChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
)
```
Layout: a `Column` with the food name (`AlTheme.typography.bodyStrong`), category
(`caption`, `textMuted`), then a `Row` of `AlUnitInput(weight = 1f)` and an
`IconButton` with `ic_mingcute_delete` tinted `AlTheme.colors.danger`. Removal is
immediate — no confirm dialog; re-adding the food costs two taps.

#### 7. `CreateFoodIntakeScreen.kt`
Structure follows `MeasurementScreen.kt` exactly:
```
AlScaffold
└─ Column(fillMaxSize + imePadding)
   ├─ AlBackTopAppBar("Cargar ingreso", enabled = !isSubmitting)
   └─ when {
        isLoadingProfile -> Spinner
        profileError != null -> message + Reintentar
        else -> Form
      }
```
The form column:
```
Box(fillMaxSize)
├─ Column(padding lg)
│  ├─ Column(weight(1f).verticalScroll(scrollState))   // ← the ScrollState AlInput needs
│  │  ├─ offline banner (AlInlineBanner Danger) when !isOnline
│  │  ├─ AlSelectField "Establecimiento"  → ModalBottomSheet of AlListRow
│  │  ├─ AlInput "Fecha de recepción" (DateVisualTransformation, KeyboardType.Number)
│  │  ├─ AlAutocompleteInput "Agregar alimento"
│  │  ├─ lines.forEach { FoodLineRow(...) }
│  │  └─ AlInput "Comentarios" (singleLine = false) + counter caption
│  └─ Column   // pinned footer
│     ├─ submitError Text (danger)
│     └─ AlButton "Guardar" (enabled = canSubmit, loading = isSubmitting, fullWidth)
└─ AlToast(successMessage)
```
Standard boilerplate, identical to `MeasurementScreen.kt:76-78`:
```kotlin
val scrollState = rememberScrollState()
val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
```
Every `AlInput`/`AlUnitInput` inside the scrolling column receives both, so the
CMP keyboard pattern works unchanged for a dynamic list of lines — that is the
whole reason the pattern lives inside `AlInput` (`AlInput.kt:69-88`) rather than
in the screen.

**Full-form lock while submitting.** The story asks for the whole form to be
blocked, which `request_establishment` does *not* do (it only disables the back
button, `RequestEstablishmentScreen.kt:89`). Here every field, the dropdown and
the delete buttons take `enabled = !state.isSubmitting`.

**Bottom-sheet establishment picker.** This is the **third** copy of the
`AlSelectField` + `ModalBottomSheet` + `AlListRow` block (Home and Measurement
are the first two). `thoughts/plans/home_current_establishment.md:26-30` recorded
the decision to duplicate rather than extract; a third copy is where extraction
becomes defensible. **Out of scope here** — noted for a follow-up so this plan
does not turn into a design-system refactor.

#### 8. Route + wiring
- `presentation/navigation/Route.kt` — add:
  ```kotlin
  @Serializable
  @SerialName("create_food_intake")
  data object CreateFoodIntake : Route()
  ```
- `app/AppNavHost.kt` — add `createFoodIntakeScreen(navController)` to the outer
  `NavHost` (it is a full-screen push off the shell, like `CreateMeasurement`).
- `di/ViewModelsModule.kt` — `viewModelOf(::CreateFoodIntakeViewModel)` (every
  constructor param is Koin-resolvable; there is no route argument).

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh` passes
- [x] `./gradlew :composeApp:assembleProdDebug --no-daemon` builds.
      `assembleTstDebug` is **not** runnable here: `api_url_tst` has no default and
      this worktree's `local.properties` has no tst host — the documented guardrail
      fails the build rather than shipping a blank URL.

#### Manual Verification:
- [ ] Typing "poll" shows suggestions after ~300 ms; typing fast issues no stale results
- [ ] Tapping a suggestion adds a line, clears the query and collapses the dropdown
- [ ] Re-picking a food already loaded does **not** add a second row
- [ ] A `unidades` food (e.g. *Huevo de gallina, entero, crudo*) shows a keyboard
      with no decimal separator and rejects `12,5`
- [ ] A `kilogramos` food accepts `12,5` and `12.5` alike
- [ ] Focusing any quantity field with several lines loaded scrolls it clear of the
      keyboard, **on both Android and iOS**
- [ ] The establishment arrives pre-selected when Home has a current establishment
- [ ] Comments counter updates live and errors past 500 only after blur
- [ ] A future date errors on blur; a past date is accepted

---

## Phase 4 — Submit, errors, offline gating

### Overview
Wire `CreateFoodIntakeUseCase`, map every typed error to Spanish copy, and make
the offline state honest.

### Changes Required

#### 1. `submit()` in the ViewModel
Follows `MeasurementViewModel.kt:138-179`: force-show every error, build the
model, bail if it is null, then launch.
```kotlin
private fun submit() {
    val current = _state.value
    if (!current.canSubmit) return              // also guards the double-tap
    val validated = current.copy(
        receivedAt = current.receivedAt.showError(receivedAtValidator),
        comments = current.comments.showError(commentsValidator),
        lines = current.lines.map { it.copy(quantity = it.quantity.showError(validatorFor(it.food.unit))) },
        establishmentError = if (current.selectedEstablishment == null) "Elegí el establecimiento." else null,
    )
    val intake = buildIntake(validated) ?: run { _state.update { validated }; return }

    _state.update { validated.copy(isSubmitting = true, submitError = null, successMessage = null) }
    viewModelScope.launch {
        createFoodIntake(intake).fold(
            onSuccess = {
                _state.update {
                    // Keep the establishment, reset everything else, and mint a NEW
                    // idempotency id — the previous one is now spent server-side.
                    it.copy(
                        intakeId = idGenerator.newId(),
                        lines = emptyList(),
                        comments = TextFieldState(),
                        receivedAt = TextFieldState(value = todayDigits()),
                        search = FoodSearchState(),
                        isSubmitting = false,
                        submitError = null,
                        successMessage = "Ingreso registrado",
                    )
                }
            },
            onFailure = { t ->
                if (t is CancellationException) throw t
                val error = (t as? FoodIntakeFailure)?.error ?: FoodIntakeError.Unknown(t)
                // The form and the intakeId are untouched: Reintentar re-sends the
                // same id, which the server answers 200 to if the first attempt
                // actually landed. A retry can never inflate the stock.
                _state.update { it.copy(isSubmitting = false, submitError = error.toUserMessage()) }
            },
        )
    }
}
```
`buildIntake` returns `null` unless the establishment is set, every line's
quantity `isValid`, the date parses and is not future, and `lines` is non-empty —
mirroring `MeasurementViewModel.buildMeasurement` (`:181-195`).

Quantity normalization at the boundary: `"12,5"` → `"12.5"` (the sanitizer already
did this at typing time), and a trailing separator is trimmed. The `String` goes
straight into `NewFoodIntakeLine.quantity` and out through `QuantitySerializer`
as a JSON number — no `Double` anywhere in the path.

#### 2. `FoodIntakeErrorMessage.kt`
**File**: `presentation/feature/food/create_food_intake/FoodIntakeErrorMessage.kt` (new)

The seventh-file precedent from `request_establishment`
(`EstablishmentRequestErrorMessage.kt:5-13`):
```kotlin
internal fun FoodIntakeError.toUserMessage(): String = when (this) {
    FoodIntakeError.EstablishmentAccessDenied -> "No tenés acceso a ese establecimiento."
    FoodIntakeError.EstablishmentNotFound -> "El establecimiento no existe."
    FoodIntakeError.FoodNotFound -> "Un alimento de la carga ya no está en el catálogo."
    FoodIntakeError.InvalidData -> "Revisá los datos de la carga e intentá nuevamente."
    FoodIntakeError.Network -> "No se pudo enviar. Revisá tu conexión."
    // 401 is handled by the session machinery (the authed client's refreshTokens
    // path); this copy only shows if the user somehow sees the screen after it.
    FoodIntakeError.Unauthorized -> "Tu sesión expiró. Iniciá sesión nuevamente."
    is FoodIntakeError.Unknown -> "Ocurrió un error inesperado. Intentá nuevamente."
}
```

#### 3. Offline gating
`isOnline` is collected from `ConnectivityObserver.isOnline` in `init` (the
`Flow`, not the suspending one-shot — `IosConnectivityObserver.kt:32` suspends
until the first `nw_path_monitor` emission, so a one-shot check at screen entry
would stall the first frame on iOS). While offline:

- an `AlInlineBanner(variant = Danger)` at the top of the form reads
  *"Necesitás conexión para cargar un ingreso."*
- the autocomplete field is `enabled = false` (a live call it cannot make)
- `canSubmit` is `false`, so **Guardar** is disabled

The form is **not** cleared and already-loaded lines stay put — regaining signal
re-enables everything with the work intact.

#### 4. Retry
There is no separate retry action: the same **Guardar** button re-submits with
the unchanged `intakeId`. The error text tells the user to retry; the button is
the affordance. This is simpler than a dedicated `RetryClick` and is exactly what
the story's "Reintentar conservando el formulario y el mismo `id`" asks for.

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh` passes
- [x] New `FoodIntakeApiTest` covers 201 / 200 / 403 / both 404 `type`s / 422 / 500 /
      transport (12 tests), plus the unquoted-`quantity` serialization assertion

#### Manual Verification (blocked on backend #123 + #126 merging + deploy to tst):
- [ ] A successful save clears the form, shows the toast, and keeps the establishment
- [ ] Airplane mode mid-submit shows the connection message with the form intact;
      restoring signal and tapping Guardar again registers exactly **one** intake
- [ ] Submitting for an establishment the user lacks access to shows the 403 copy
- [ ] Tapping Guardar twice quickly registers one intake, not two

---

## Phase 5 — Home entry point

### Overview
Turn Home's fixed 3-wide `Row` of quick actions into a 2×2 grid with a fourth
**Alimentos** action.

### Changes Required

**File**: `presentation/feature/home/HomeScreen.kt:243-268`
Replace the single `Row` with two `Row`s of two `AlCardIconButton(Modifier.weight(1f))`
each, keeping `Arrangement.spacedBy(AlTheme.spacing.sm)`:
```
Medición   | Sujeto
Encuesta   | Alimentos
```
The fourth uses the icon added in Phase 2 and `HomeAction.NewFoodIntakeClick`.

**File**: `presentation/feature/home/HomeAction.kt` — add `data object NewFoodIntakeClick : HomeAction`.

**File**: `presentation/feature/home/HomeDestination.kt` — add
`data object NavigateToCreateFoodIntake : HomeDestination()`.

**File**: `presentation/feature/home/HomeViewModel.kt` — one reducer line.

**File**: `app/AuthedShell.kt:88-105` — add the branch to the **inline** `when`.
Home's destinations are dispatched here, not in `HomeNavigation.kt`, which
`HomeNavigation.kt:3-7` documents as dead code for the running app:
```kotlin
HomeDestination.NavigateToCreateFoodIntake -> outerNav.navigateToCreateFoodIntake()
```

### Success Criteria

#### Automated Verification:
- [x] `./scripts/compile.sh` passes
- [x] `HomeViewModelTest` still green

#### Manual Verification:
- [ ] The 2×2 grid is balanced on a small phone; no label truncates
- [ ] Tapping **Alimentos** opens the form; back returns to Home with its state intact

---

## Phase 6 — Tests

### Overview
Three new test files, following the existing conventions in `commonTest`.

### Changes Required

#### 1. `FoodIntakeApiTest.kt`
`commonTest/.../data/food/FoodIntakeApiTest.kt` — the `MockEngine` shape from
`SubjectUploadOutcomeTest.kt:53-59`, with a client that deliberately does **not**
set `expectSuccess`, so the test proves the status branching rather than masking
it (the mistake `ProfileRepositoryImplTest.kt:38-45` makes).

Cases: `201 → Created`; `200 → Created` (replay); `403 → EstablishmentAccessDenied`;
`404` + `{"type":"/errors/food-not-found"}` → `FoodNotFound`;
`404` + `{"type":"/errors/establishment-not-found"}` → `EstablishmentNotFound`;
`404` with an unrecognized type → `InvalidData`; `422 → InvalidData`;
`500 → Retryable`; a throwing engine → `Retryable`.
One serialization assertion: the request body's `quantity` is the **unquoted**
`12.5`, not `"12.5"`.

#### 2. `CreateFoodIntakeViewModelTest.kt`
`commonTest/.../presentation/feature/food/create_food_intake/` — modelled on
`RequestEstablishmentViewModelTest.kt` and `MeasurementViewModelTest.kt`, with a
`FakeFoodCatalogRepository`, a `FakeFoodIntakeRepository`, the existing
`FakeConnectivityObserver`, and a deterministic `IdGenerator`.

Cases:
- debounce: three rapid `SearchQueryChanged` produce one repository call
- `FoodPicked` of an already-loaded food does not duplicate the line
- a `unidades` line rejects `"12.5"` and accepts `"12"`
- a `kilogramos` line accepts `"12,5"` and normalizes it to `"12.5"` in the model
- submit with zero lines is a no-op (`canSubmit == false`)
- **the id is stable across a failed submit and a retry**, and **changes after a
  successful one** — the single most important behavior in the feature
- offline → `canSubmit == false`
- a future date blocks submit and shows the error

#### 3. `DecimalInputTest.kt`
`commonTest/.../domain/util/DecimalInputTest.kt` — `sanitizeDecimalInput` has no
test today; add one for it alongside `sanitizeQuantityInput` /
`sanitizeIntegerInput` (4-decimal truncation, 8th integer digit dropped, comma →
dot, `"1.2.3"` → `"1.23"`, letters stripped).

### Success Criteria

#### Automated Verification:
- [x] `./gradlew :composeApp:testProdDebugUnitTest --no-daemon` green — 402 tests,
      0 failures (34 new: 12 API, 15 ViewModel, 7 sanitizer)
- [x] `./scripts/compile.sh` passes

#### Manual Verification:
- [x] None — this phase is automated by definition.

---

## Testing Strategy

### Manual testing steps (in order)
1. With no network, open **Alimentos** from Home → the banner shows, the search
   field is disabled, **Guardar** is disabled.
2. Restore network → everything enables; the establishment is pre-filled from Home.
3. Type `poll` → suggestions after a beat; type `pollo pech` → the list narrows;
   note the "8 de N" footer when N > 8.
4. Tap *Pollo, pechuga sin piel, crudo* → a line appears with `kg` as the suffix.
5. Enter `12,5` → accepted; the field shows `12,5` and the model holds `12.5`.
6. Search the same food again and tap it → **no second row appears**.
7. Add *Huevo de gallina, entero, crudo* → keyboard has no decimal separator;
   typing `12.5` is impossible and `12` is accepted.
8. Focus the second line's quantity with the keyboard up → it scrolls clear.
   **Repeat on iOS.**
9. Set the date to tomorrow → error on blur. Set it to last week → accepted.
10. Paste 501 characters into comments → the counter reads `501/500` and errors on blur.
11. Delete a line → it disappears immediately.
12. Tap **Guardar** → button spins, every field is locked, toast confirms, form
    clears, establishment stays.
13. Turn off the network mid-submit → connection error, form intact. Restore and
    tap **Guardar** → exactly one intake in the backend for that id.

### Automated coverage
`FoodIntakeApiTest` (status/type branching + quantity serialization),
`CreateFoodIntakeViewModelTest` (debounce, dedup, per-unit validation, id
lifecycle, offline gating), `DecimalInputTest` (sanitizers).

---

## Performance Considerations

- One in-flight search at a time: `collectLatest` cancels the previous request on
  every keystroke past the 300 ms debounce, so a fast typist issues one request,
  not one per character.
- `page_size=8` keeps each response tiny (8 rows × ~5 fields) — an order of
  magnitude smaller than the 20-row establishment pages.
- No local cache and no DB writes, so nothing competes with the SQLDelight sync
  engines running for subjects/measurements/surveys.
- The dropdown is a `LazyColumn` capped at 280.dp; the lines list lives in the
  outer `verticalScroll` column (not a nested `LazyColumn`, which would conflict
  with the scroll-into-view pattern `AlInput` depends on).

---

## Migration Notes

None. No schema, no persisted state, no stored preference. The feature can be
reverted by deleting the new packages plus the `Route`/`AppNavHost`/DI/Home
lines; nothing else in the app takes a dependency on it.

---

## Backend limitations shipped around (recorded, no action taken)

Not blockers, but they shape what the screen can promise:

1. **No `unaccent`** — "azucar" will not find "Azúcar". No local cache means no
   client-side workaround.
2. **No relevance ranking** — `sort_order` is only `name_asc|name_desc`; matches
   come back alphabetically. This is why the dropdown shows a count and asks for a
   narrower query instead of paginating.
3. **`quantity` integer-ness for `unidades` is unenforced server-side** — the API
   accepts `12.5` eggs from any other client.
4. **No intake list endpoint** — the app cannot show "what I loaded", not even online.
5. **`is_active` was removed** by #119, so a food can be deleted outright, which is
   what makes the `food-not-found` 404 a live race rather than a theoretical one.

---

## References

- Story: `thoughts/stories/create_food_intake_screen.md`
- Contract research: `thoughts/stories/food_stock_management.md`
- Codebase research: `thoughts/research/2026-09-06_14-29-17_food_intake_logging_screens.md`
- Search + submit precedent: `presentation/feature/profile/request_establishment/`
- Form + establishment precedent: `presentation/feature/measurement/create_measurement/`
- `type`-discriminated problem+json: `data/survey/SurveyResponseApi.kt:33-53`
- Pre-fill-not-gate ADR: `thoughts/grilling/adr-012-current-establishment-prefill-not-gate.md`
- Backend contract, pinned:
  `app/api/v1/food_catalog/foods.py@develop`,
  `app/schemas/food_intakes/food_intake_schema.py@feat/food-stock-per-establishment`,
  `docs/errors.json@develop`

---

## Implementation notes (2026-09-06)

Every phase is implemented and verified; only the manual/on-device checks below
remain, and the POST ones stay blocked on backend #123 + #126.

Deviations from the plan as written, all small and deliberate:

1. **The suggestion dropdown expands inline, not as a floating overlay.** It sits
   inside the form's `verticalScroll` column, where `AlInput`'s scroll-into-view
   already keeps the focused field clear of the keyboard. An inline panel cannot
   clip at a screen edge and cannot fight that scroll; a `Popup` would have to
   re-derive both. It is still capped at `MAX_DROPDOWN_HEIGHT = 280.dp` and
   scrolls internally with a `LazyColumn` (the `heightIn(max = …)` is what makes
   that legal inside a `verticalScroll` parent).
2. **`ESTABLISHMENT_ACCESS_DENIED_TYPE` was not added.** 403 is unambiguous, so
   nothing branches on its `type` — the constant would have been dead code. The
   two 404 `type`s are there, which is the case that actually needs discriminating.
3. **`AlIconButton` gained an `enabled` parameter.** The story's full-form lock
   needs the per-line delete button to lock too, and the component had no way to
   express that.
4. **The `add-mingcute-icon` driver had to be repaired first.** MingCute `v3.0.0`
   restructured upstream to `assets/svg/core/{regular,filled}/<category>/<name>.svg`,
   dropping the flat `svg/<category>/<name>_line.svg` layout every existing
   `ic_mingcute_*` came from — the driver could not resolve *any* icon. It is now
   pinned to `v2.97`, the last release with that layout. Pre-existing breakage,
   unrelated to this feature.
5. **`local.properties` was created** (git-ignored) with only `sdk.dir`, since this
   worktree had none and the Android test/assemble tasks need it. `api_url_tst` is
   still unset, so `assembleTstDebug` correctly refuses to build; `assembleProdDebug`
   is what was used to prove the app assembles.

One behavior worth flagging for the manual pass: a `unidades` quantity field
strips the separator as the user types, so `12.5` becomes `125` rather than
erroring. The keyboard has no decimal key either, so the invalid value is
unreachable in practice — the `digitsInRange` rule is the backstop for a paste.
