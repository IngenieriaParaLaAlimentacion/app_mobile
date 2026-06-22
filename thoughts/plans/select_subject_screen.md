# Seleccionar Sujeto a Medir (Historia 1) — Implementation Plan

## Overview

Construir una pantalla de **selección de sujeto** que el relevador abre para
elegir, con búsqueda y scroll infinito, un sujeto ya existente en el servidor (su
`subject_id` real). Es la Historia 1 de la épica "Cargar Medición Antropométrica"
(`thoughts/stories/create_measurement_screen.md`) y el insumo de la futura
pantalla de carga de medición (Historia 2, fuera de este plan).

La pantalla replica el patrón ya probado de `SelectEstablishment` (búsqueda con
debounce + retorno por `savedStateHandle`), con **dos diferencias** que impone el
endpoint y la story: **selección única** (tocar una fila retorna el sujeto) y
**scroll infinito real** (el endpoint pagina; `SelectEstablishment` traía una sola
página y descartaba el resto).

## Current State Analysis

- **Endpoint nuevo disponible:** `GET /api/v1/subjects?page=&page_size=&search=`
  (paginado; `page_size` 20 por defecto, 100 máx.; `search` máx. 100 chars; matchea
  nombre/apellido/DNI). Devuelve un envelope `{ items, total, page, page_size,
  total_pages }` con resúmenes livianos `{ id, dni, name, last_name, sex }`. Sin
  scoping por establecimiento. Auth: cualquier rol autenticado.
- **`SubjectApi`** (`data/subject/SubjectApi.kt`) hoy solo tiene `create()` (POST) y
  usa el **`HttpClient` autenticado** (sin `expectSuccess`, por eso ramifica sobre
  `response.status.value`).
- **`SubjectRepository`** es una cola de subida write-only (`enqueue`,
  `observeUploads`, `syncPending`…); **no** tiene método de lectura/list → hace
  falta una interfaz nueva, separada.
- **`SelectEstablishment`** es el molde: debounce de 300 ms con
  `MutableStateFlow + .debounce + .distinctUntilChanged + collectLatest` en `init`;
  retorno por `previousBackStackEntry.savedStateHandle`; UI con `AlScaffold` +
  `AlBackTopAppBar` + `AlInput` + lista. Trae **una sola página** (`page_size=100`).
- **Navegación:** flujos full-screen (`createSubject`, `selectEstablishment`) se
  registran en el **outer** `NavHost` (`app/AppNavHost.kt`), expuesto como
  `LocalOuterNavController`. El **inner** `NavHost` (`app/AuthedShell.kt`) solo tiene
  los tabs (`Home`, `SubjectUploads`, `Profile`). El botón provisional de Home vive
  en el inner nav y navega vía `outerNav` (igual que `Cargar sujeto`).

### Key Discoveries:
- Idioma de GET con query params: `client.get("api/v1/...") { parameter("page", n);
  search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) } }`
  (`data/establishment/EstablishmentApi.kt:?`). El cliente autenticado **no** lanza
  en non-2xx, así que `list()` debe chequear `response.status.isSuccess()` antes de
  `.body()` (espejo del branching de `create()`).
- `AlListRow(title, subtitle, onClick)` (`core/design_system/components/list/
  AlListRow.kt`) ya renderiza fila tappable con chevron + divisor → ideal para
  selección única (no usar `AlSelectableCard`, que trae checkbox de multi-selección).
- `AlInfoCard(title, description, trailing)`
  (`core/design_system/components/card/AlInfoCard.kt`) sirve para el banner
  provisional de Home.
- Retorno cruzando outer/inner nav (patrón documentado en `CLAUDE.md` para
  `EditProfile`→`Profile`): el selector escribe en su `previousBackStackEntry`
  (= la entry de `AuthedShell` en el **outer** nav); el consumidor (Home, inner nav)
  debe **leer del outer nav** (`LocalOuterNavController.current.currentBackStackEntry`),
  no de su entry interna.
- `viewModelOf(::X)` para VMs sin parámetro de ruta; el selector no necesita
  parámetro (no hay preselección en selección única) → `Route.SelectSubject` es un
  `data object`.

## Desired End State

Desde Home, un botón provisional **"Cargar medición"** abre la pantalla de
selección. El relevador escribe en la barra de búsqueda, ve una lista de sujetos
(nombre/apellido + DNI + sexo) que se acota server-side con debounce y crece con
scroll infinito; al **tocar una fila** vuelve a Home, que muestra el sujeto elegido
en un banner de confirmación. El `subject_id` queda disponible vía la key de
resultado `SELECTED_SUBJECT_RESULT` para que la Historia 2 lo consuma.

Verificación: `./scripts/compile.sh` pasa; los tests nuevos de ViewModel,
repositorio y use case pasan; en simulador iOS/Android el flujo round-trip funciona.

## What We're NOT Doing

- **No** se construye la pantalla de carga de medición (Historia 2) ni su cola
  offline-first. El consumo del resultado en Home es **provisional** y será
  reemplazado en Historia 2 por la pantalla de medición (que, al estar también en el
  outer nav, usará el `previousBackStackEntry` simple, como `Register`↔
  `SelectEstablishment`).
- **No** se cachea localmente el catálogo de sujetos: `GET /subjects` es en vivo;
  sin conexión la búsqueda muestra error/vacío (ver *Notas* de la story).
- **No** se trae el detalle del sujeto (`GET /subjects/{id}` con birth_date/is_rural/
  is_indigenous); eso lo necesitará Historia 2 para las alertas, no la selección.
- **No** multi-selección, **no** botón "Listo": tocar una fila confirma y retorna.

## Implementation Approach

Tres fases incrementales, cada una compilable: (1) capa data+domain de lectura de
sujetos, con tests; (2) el feature `select_subject/` (UI + nav + DI), con test de
ViewModel; (3) la entrada provisional en Home + banner de resultado. Se copia el
patrón de `SelectEstablishment` salvo el envelope paginado (que ahora se conserva
para el scroll infinito) y la selección única.

---

## Phase 1: Capa de datos + dominio (lectura de sujetos)

### Overview
Modelo de resumen, DTOs del envelope paginado, método GET en `SubjectApi`,
repositorio de consulta y use case, más su wiring en Koin.

### Changes Required:

#### 1. Modelos de dominio
**File**: `domain/model/subject/SubjectSummary.kt` (nuevo)
```kotlin
@Serializable
data class SubjectSummary(
    val id: String,
    val dni: Int,
    val name: String,
    val lastName: String,
    val sex: Sex,
)
```
**File**: `domain/model/subject/SubjectPage.kt` (nuevo)
```kotlin
data class SubjectPage(
    val items: List<SubjectSummary>,
    val page: Int,
    val totalPages: Int,
)
```
**File**: `domain/model/subject/Sex.kt` (editar) — anotar `@Serializable` para que
`SubjectSummary` pueda round-trippear por JSON en `savedStateHandle`:
```kotlin
@Serializable
enum class Sex { Male, Female }
```

#### 2. DTOs + mapper
**File**: `data/subject/dto/PaginatedSubjectsResponse.kt` (nuevo)
```kotlin
@Serializable
data class SubjectSummaryResponse(
    val id: String,
    val dni: Int,
    val name: String,
    @SerialName("last_name") val lastName: String,
    val sex: SexDto,
)

@Serializable
data class PaginatedSubjectsResponse(
    val items: List<SubjectSummaryResponse>,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    @SerialName("total_pages") val totalPages: Int,
)
```
**File**: `data/subject/mapper/SubjectMapper.kt` (editar) — reusar `SexDto.toDomain()`:
```kotlin
fun SubjectSummaryResponse.toDomain() = SubjectSummary(
    id = id, dni = dni, name = name, lastName = lastName, sex = sex.toDomain(),
)
fun PaginatedSubjectsResponse.toDomain() = SubjectPage(
    items = items.map { it.toDomain() }, page = page, totalPages = totalPages,
)
```

#### 3. `SubjectApi.list()` (cliente autenticado)
**File**: `data/subject/SubjectApi.kt` (editar) — agregar imports `get`, `parameter`,
`isSuccess`; nuevo método que chequea el status (el cliente autenticado no lanza):
```kotlin
suspend fun list(page: Int, pageSize: Int, search: String?): PaginatedSubjectsResponse {
    val response = client.get("api/v1/subjects") {
        parameter("page", page)
        parameter("page_size", pageSize)
        search?.takeIf { it.isNotBlank() }?.let { parameter("search", it) }
    }
    if (!response.status.isSuccess()) error("subjects list failed: ${response.status.value}")
    return response.body()
}
```

#### 4. Repositorio de consulta + use case
**File**: `domain/repository/SubjectQueryRepository.kt` (nuevo)
```kotlin
interface SubjectQueryRepository {
    suspend fun searchSubjects(query: String?, page: Int, pageSize: Int): Result<SubjectPage>
}
```
**File**: `data/subject/SubjectQueryRepositoryImpl.kt` (nuevo)
```kotlin
class SubjectQueryRepositoryImpl(private val api: SubjectApi) : SubjectQueryRepository {
    override suspend fun searchSubjects(query: String?, page: Int, pageSize: Int): Result<SubjectPage> =
        runCatching { api.list(page = page, pageSize = pageSize, search = query).toDomain() }
}
```
**File**: `domain/usecase/subject/GetSubjectsUseCase.kt` (nuevo)
```kotlin
class GetSubjectsUseCase(private val repository: SubjectQueryRepository) {
    suspend operator fun invoke(query: String?, page: Int, pageSize: Int = DEFAULT_PAGE_SIZE): Result<SubjectPage> =
        repository.searchSubjects(query, page, pageSize)
    companion object { const val DEFAULT_PAGE_SIZE = 20 }
}
```

#### 5. Koin DI
- `di/RepositoriesModule.kt`: `single<SubjectQueryRepository> { SubjectQueryRepositoryImpl(api = get()) }`
  (`api` = el `SubjectApi` ya registrado en `ClientsModule`; sin cambios de cliente).
- `di/UseCasesModule.kt`: `factory { GetSubjectsUseCase(get()) }`

#### 6. Tests
**File**: `commonTest/.../fake/FakeSubjectQueryRepository.kt` (nuevo) — fake con
`pages: Map<Int, SubjectPage>` y `error: Throwable?`.
**File**: `commonTest/.../data/subject/SubjectApiTest.kt` (nuevo) — Ktor `MockEngine`
devolviendo el JSON del envelope; verifica que `list()` parsea `items`/`totalPages`
y que un 500 produce `Result.failure` vía el repo.
**File**: `commonTest/.../domain/usecase/GetSubjectsUseCaseTest.kt` (nuevo) — delega
al repo (éxito + fallo). Modelar sobre `GetEstablishmentsUseCaseTest`.

### Success Criteria:

#### Automated Verification:
- [x] Compila commonMain: `./scripts/compile.sh`
- [x] Tests pasan: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [x] (n/a en esta fase — sin UI)

---

## Phase 2: Feature `select_subject/` (UI + navegación)

### Overview
Las 6 piezas del patrón MVVM + su `*Navigation.kt`, con debounce, scroll infinito y
selección única, registradas en el **outer** nav.

### Changes Required:

#### 1. Ruta
**File**: `presentation/navigation/Route.kt` (editar)
```kotlin
@Serializable @SerialName("select_subject")
data object SelectSubject : Route()
```

#### 2. State / Action / Destination
**File**: `presentation/feature/subject/select_subject/SelectSubjectState.kt`
```kotlin
data class SelectSubjectState(
    val destination: SelectSubjectDestination? = null,
    val query: String = "",
    val results: List<SubjectSummary> = emptyList(),
    val isLoading: Boolean = true,       // carga inicial / nueva búsqueda
    val isLoadingMore: Boolean = false,  // anexando página siguiente
    val loadError: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
) {
    val canLoadMore: Boolean
        get() = page < totalPages && !isLoading && !isLoadingMore && !loadError
}
```
**File**: `.../SelectSubjectAction.kt`
```kotlin
sealed interface SelectSubjectAction {
    data class QueryChanged(val value: String) : SelectSubjectAction
    data class SubjectClicked(val subject: SubjectSummary) : SelectSubjectAction
    data object LoadMore : SelectSubjectAction
    data object BackClick : SelectSubjectAction
    data object RetryClick : SelectSubjectAction
}
```
**File**: `.../SelectSubjectDestination.kt`
```kotlin
sealed class SelectSubjectDestination {
    data object Back : SelectSubjectDestination()
    data class Confirm(val subjectJson: String) : SelectSubjectDestination()
}
```

#### 3. ViewModel (debounce + scroll infinito + selección única)
**File**: `.../SelectSubjectViewModel.kt`
```kotlin
@OptIn(FlowPreview::class)
class SelectSubjectViewModel(
    private val getSubjects: GetSubjectsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SelectSubjectState())
    val state = _state.asStateFlow()
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.debounce(DEBOUNCE_MS).distinctUntilChanged().collectLatest { search(it) }
        }
    }

    fun onAction(action: SelectSubjectAction) {
        when (action) {
            is SelectSubjectAction.QueryChanged -> {
                _state.update { it.copy(query = action.value) }
                query.value = action.value
            }
            is SelectSubjectAction.SubjectClicked ->
                _state.update {
                    it.copy(destination = SelectSubjectDestination.Confirm(Json.encodeToString(action.subject)))
                }
            SelectSubjectAction.LoadMore -> loadMore()
            SelectSubjectAction.BackClick ->
                _state.update { it.copy(destination = SelectSubjectDestination.Back) }
            SelectSubjectAction.RetryClick ->
                viewModelScope.launch { search(_state.value.query) }
        }
    }

    fun clearDestination() = _state.update { it.copy(destination = null) }

    private suspend fun search(value: String) {
        _state.update { it.copy(isLoading = true, loadError = false) }
        getSubjects(value.takeIf { it.isNotBlank() }, page = 1).fold(
            onSuccess = { p -> _state.update {
                it.copy(results = p.items, page = p.page, totalPages = p.totalPages, isLoading = false)
            } },
            onFailure = { t ->
                if (t is CancellationException) throw t
                _state.update { it.copy(isLoading = false, loadError = true) }
            },
        )
    }

    private fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            getSubjects(current.query.takeIf { it.isNotBlank() }, page = current.page + 1).fold(
                onSuccess = { p -> _state.update {
                    it.copy(results = it.results + p.items, page = p.page, totalPages = p.totalPages, isLoadingMore = false)
                } },
                onFailure = { t ->
                    if (t is CancellationException) throw t
                    _state.update { it.copy(isLoadingMore = false) }   // conserva la lista; el fin se reintenta al scrollear
                },
            )
        }
    }

    private companion object { const val DEBOUNCE_MS = 300L }
}
```

#### 4. Screen (búsqueda + lista con scroll infinito)
**File**: `.../SelectSubjectScreen.kt` — `AlScaffold` + `AlBackTopAppBar("Sujeto")` +
`AlInput("Buscar por nombre o DNI")` + `Box` con estados
(loading/error+reintentar/sin resultados/lista). La lista usa `LazyColumn` con
`rememberLazyListState()`; cada item es `AlListRow`:
```kotlin
items(items = state.results, key = { it.id }) { subject ->
    AlListRow(
        title = "${subject.name} ${subject.lastName}",
        subtitle = "DNI ${subject.dni} · ${if (subject.sex == Sex.Male) "Masculino" else "Femenino"}",
        onClick = { onAction(SelectSubjectAction.SubjectClicked(subject)) },
    )
}
if (state.isLoadingMore) { item { Spinner(Modifier.fillMaxWidth().padding(16.dp)) } }
```
Detección de fin de lista para paginar (en el `Root` o el `Screen`):
```kotlin
val listState = rememberLazyListState()
LaunchedEffect(listState) {
    snapshotFlow {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        last >= state.results.lastIndex - LOAD_MORE_THRESHOLD
    }.distinctUntilChanged().collect { nearEnd ->
        if (nearEnd && state.canLoadMore) onAction(SelectSubjectAction.LoadMore)
    }
}
```
`SelectSubjectScreenRoot` replica el de `SelectEstablishment` (collect state +
`LaunchedEffect(state.destination) { navigate(it); clearDestination() }`), pero el
VM se inyecta con `koinViewModel()` (sin `parametersOf`, ruta sin parámetro).

#### 5. Navigation + key de resultado
**File**: `.../SelectSubjectNavigation.kt`
```kotlin
const val SELECTED_SUBJECT_RESULT = "selected_subject_result"

fun NavController.navigateToSelectSubject(navOptions: NavOptions? = null) =
    navigate(Route.SelectSubject, navOptions)

fun NavGraphBuilder.selectSubjectScreen(navController: NavController) {
    composable<Route.SelectSubject> {
        SelectSubjectScreenRoot(navigate = { d -> navigate(d, navController) })
    }
}

internal fun navigate(destination: SelectSubjectDestination, navController: NavController) {
    when (destination) {
        SelectSubjectDestination.Back -> navController.popBackStack()
        is SelectSubjectDestination.Confirm -> {
            navController.previousBackStackEntry
                ?.savedStateHandle?.set(SELECTED_SUBJECT_RESULT, destination.subjectJson)
            navController.popBackStack()
        }
    }
}
```

#### 6. DI + registro en el outer nav
- `di/ViewModelsModule.kt`: `viewModelOf(::SelectSubjectViewModel)`
- `app/AppNavHost.kt`: `import ...select_subject.selectSubjectScreen` + agregar
  `selectSubjectScreen(navController)` dentro del `NavHost` (junto a
  `createSubjectScreen(navController)`).

#### 7. Test de ViewModel
**File**: `commonTest/.../select_subject/SelectSubjectViewModelTest.kt` — con Turbine
+ `FakeSubjectQueryRepository` + `runTest` (`advanceTimeBy` para el debounce):
búsqueda inicial, debounce colapsa teclas, `LoadMore` anexa la página 2 y respeta
`totalPages`, `SubjectClicked` emite `Confirm` con el JSON correcto, fallo →
`loadError`.

### Success Criteria:

#### Automated Verification:
- [x] Compila: `./scripts/compile.sh`
- [x] Tests pasan: `./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] La pantalla abre con la primera página de sujetos.
- [ ] Tipear filtra con ~300 ms de debounce; búsquedas en curso se cancelan.
- [ ] Al scrollear al fondo se cargan más páginas (spinner de "cargando más") hasta
      agotar `total_pages`; no hay doble carga ni parpadeos.
- [ ] Estados sin-resultados / error+reintentar se ven bien.
- [ ] Atrás (gesto/botón) vuelve sin retornar selección.

---

## Phase 3: Entrada provisional en Home + banner de resultado

### Overview
Botón provisional en Home que abre el selector; al volver, Home muestra el sujeto
elegido en un `AlInfoCard`. El resultado cruza outer→inner nav (patrón documentado).

### Changes Required:

#### 1. Home feature
- `feature/home/HomeAction.kt`: agregar `data object LoadMeasurementClick : HomeAction`
  y `data class SubjectPicked(val subject: SubjectSummary) : HomeAction`.
- `feature/home/HomeDestination.kt`: agregar `data object NavigateToSelectSubject`.
- `feature/home/HomeState.kt`: agregar `val pickedSubject: SubjectSummary? = null`.
- `feature/home/HomeViewModel.kt`: reducers —
  `LoadMeasurementClick -> copy(destination = NavigateToSelectSubject)` y
  `SubjectPicked -> copy(pickedSubject = action.subject)`.
- `feature/home/HomeScreen.kt`: segundo `AlButton("Cargar medición")` →
  `LoadMeasurementClick`; y, si `state.pickedSubject != null`, un `AlInfoCard`
  (`title = "${name} ${lastName}"`, `description = "DNI ${dni}"`, `trailing` = "×" que
  despacha `SubjectPicked(null)`… o un nuevo `DismissPicked`) como confirmación.

#### 2. Consumo del resultado en `AuthedShell` (outer→inner)
**File**: `app/AuthedShell.kt` (editar el `composable<Route.Home>`) — leer la key del
**outer** nav (la entry de `AuthedShell`) y pasarla a `HomeScreenRoot`:
```kotlin
composable<Route.Home> {
    val outerEntry = outerNav.currentBackStackEntry
    val pickedJson by (outerEntry?.savedStateHandle
        ?.getStateFlow<String?>(SELECTED_SUBJECT_RESULT, null)
        ?: MutableStateFlow(null)).collectAsStateWithLifecycle()

    HomeScreenRoot(
        pickedSubjectResult = pickedJson,
        onResultConsumed = { outerEntry?.savedStateHandle?.set(SELECTED_SUBJECT_RESULT, null) },
        navigate = { destination ->
            when (destination) {
                HomeDestination.Back -> outerNav.popBackStack()
                HomeDestination.NavigateToCreateSubject -> outerNav.navigateToCreateSubject()
                HomeDestination.NavigateToSelectSubject -> outerNav.navigateToSelectSubject()
            }
        },
    )
}
```
**File**: `feature/home/HomeScreen.kt` (`HomeScreenRoot`) — nuevos params
`pickedSubjectResult: String?` y `onResultConsumed: () -> Unit`; decodificar y
despachar una sola vez:
```kotlin
LaunchedEffect(pickedSubjectResult) {
    pickedSubjectResult?.let { json ->
        runCatching { Json.decodeFromString<SubjectSummary>(json) }
            .onSuccess { viewModel.onAction(HomeAction.SubjectPicked(it)) }
        onResultConsumed()
    }
}
```

### Success Criteria:

#### Automated Verification:
- [x] Compila: `./scripts/compile.sh`
- [x] Tests pasan: `./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] Home muestra "Cargar medición"; al tocarlo abre el selector.
- [ ] Elegir un sujeto vuelve a Home y muestra su nombre + DNI en el banner.
- [ ] El banner se descarta y **no reaparece** al recomponer / cambiar de tab y volver
      (consume-once: la key se limpia).
- [ ] Abrir el selector y volver con "atrás" (sin elegir) **no** dispara el banner.

---

## Testing Strategy

### Manual Testing Steps:
1. Home → "Cargar medición" → escribir un apellido conocido → ver resultados acotados.
2. Borrar la búsqueda → ver la primera página completa; scrollear hasta cargar 2+
   páginas.
3. Tocar un sujeto → volver a Home con el banner correcto.
4. Repetir y descartar el banner; cambiar de tab y volver → el banner no reaparece.
5. Modo avión → abrir el selector → ver estado de error + "Reintentar".

### Unit Tests (mínimo, por reglas Kotlin):
- `GetSubjectsUseCase` (éxito/fallo, delega al repo).
- `SubjectQueryRepositoryImpl` / `SubjectApi` con `MockEngine` (parseo del envelope,
  fallo → `Result.failure`).
- `SelectSubjectViewModel` con Turbine (debounce, scroll infinito, selección, error).

## Performance Considerations
- El debounce (300 ms) + `collectLatest` evita disparar una request por tecla y
  cancela las en vuelo. El umbral de scroll (`LOAD_MORE_THRESHOLD`, ~3 ítems) dispara
  la página siguiente antes de tocar el fondo. `key = { it.id }` mantiene estable el
  `LazyColumn` al anexar páginas.

## Migration Notes
- Ninguna migración de datos: solo lectura. No toca la cola offline-first de sujetos
  ni su tabla SQLDelight.

## References
- Historia: `thoughts/stories/create_measurement_screen.md` (Historia 1)
- Patrón a copiar: `presentation/feature/establishment/select_establishment/` y
  `thoughts/plans/select_establishment_search_screen.md`
- Retorno outer↔inner nav: `CLAUDE.md` (sección *Navigate-back-with-result*).
- Idioma GET paginado: `data/establishment/EstablishmentApi.kt`,
  `data/establishment/dto/EstablishmentResponse.kt`
- Componentes: `core/design_system/components/list/AlListRow.kt`,
  `core/design_system/components/card/AlInfoCard.kt`
