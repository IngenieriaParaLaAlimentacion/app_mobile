# Crear sujeto sin salir del flujo de medición — Implementation Plan

## Historia

> **Como** relevador
> **quiero** buscar un sujeto por DNI al cargar una medición y, si no existe,
> crearlo sin salir del flujo
> **para** no tener que abandonar la carga de la medición y volver a entrar después.

## Overview

Hoy, si el relevador está cargando una medición y el sujeto no existe, el flujo se
corta: tiene que salir del selector, volver a Home, tocar "Cargar sujeto", crearlo,
y recién ahí volver a empezar la medición desde cero. Este plan cierra ese hueco
agregando un CTA **"Crear sujeto"** dentro del selector (`SelectSubject`) que abre
`CreateSubject` **con el DNI ya precargado** y, al guardar, **vuelve directo a la
pantalla de medición con el sujeto nuevo ya seleccionado**.

## Current State Analysis

**La buena noticia: toda la maquinaria offline ya existe y funciona.** Esto es un
hueco puramente de presentación/navegación — no hay trabajo de sync ni de datos.

- **La cadena offline sujeto→medición ya está implementada** (ADR-001). Un sujeto
  creado offline queda `PENDING` con un `localId` (UUID v4 cliente, que es también
  la idempotency key del POST). `MeasurementRepositoryImpl.syncPending()`
  (`data/measurement/MeasurementRepositoryImpl.kt:50-71`) ya:
  - **retiene** la medición mientras el sujeto está `PENDING`/`SYNCING` (para no
    quemar reintentos en 404s garantizados),
  - **remapea** a `subject.canonicalId` cuando un 409 por DNI duplicado mergea, y
  - **dead-lettea** la medición si el sujeto dead-leteó.
  → Medir un sujeto recién creado y todavía no sincronizado **ya está soportado**.
- **El picker ya es union read** (ADR-004): `SubjectQueryRepositoryImpl` mezcla la
  página remota con las filas locales `PENDING`/`SYNCING`/`SYNCED`, así que un
  sujeto creado offline aparece en el selector inmediatamente.
- **La búsqueda por DNI ya funciona**: `SelectSubjectScreen.kt:89-97` tiene un campo
  único "Buscar por nombre o DNI" con debounce de 300 ms; el `search` va al server y
  las filas locales se filtran client-side por `dni.toString().contains(q)`
  (`SubjectQueryRepositoryImpl.kt:56-62`).

### Los tres huecos reales

1. **No hay ningún acceso a "crear sujeto" desde el selector.**
   `SelectSubjectScreen.kt:126-131` muestra un `Text("Sin resultados")` pelado, sin
   salida.
2. **`enqueue` no devuelve el id del sujeto creado.**
   `SubjectRepository.enqueue(NewSubject): Result<Unit>`
   (`domain/repository/SubjectRepository.kt`) descarta el `localId` que
   `SubjectRepositoryImpl.kt:29-31` acaba de generar. Sin ese id no se puede armar el
   `SubjectSummary` para devolver a la medición.
3. **`CreateSubject` no devuelve resultado y no es parametrizable.**
   `CreateSubjectDestination` solo tiene `Back`; `CreateSubjectNavigation.kt:24-26`
   solo hace `popBackStack()`. `Route.CreateSubject` es un `data object` (sin DNI de
   prefill, sin flag de "devolvé el resultado").

### Descubrimiento crítico: el CTA no puede vivir solo en el estado vacío

Offline, si la query no matchea ninguna fila local, `SubjectQueryRepositoryImpl`
devuelve `Result.failure` (`:40-47`), lo que lleva al estado **`loadError`**, no al
estado vacío. Es decir: **el escenario exacto de la historia** (relevador en el
campo, sin señal, sujeto que no existe) cae en la rama de error, no en "Sin
resultados". Si el CTA se gatea solo en `results.isEmpty()`, la historia queda
bloqueada justo donde más importa. **El CTA va en las dos ramas.**

## Desired End State

```
CreateMeasurement ──[tocar "Sujeto"]──▶ SelectSubject (busca por DNI/nombre)
                                          ├─ lo encuentra → toca la fila ──────┐
                                          └─ no existe → "Crear sujeto"        │
                                               → CreateSubject(dni precargado) │
                                                  → Guardar ───────────────────┤
                                                                               ▼
                                          CreateMeasurement con el sujeto ya seleccionado
```

El relevador nunca vuelve a Home ni pierde el establecimiento/peso/talla que ya
había cargado (el `MeasurementViewModel` conserva su estado: la pantalla sigue en el
back stack).

### Verificación
`./scripts/compile.sh` pasa; `:composeApp:testDebugUnitTest` pasa; en simulador el
round-trip completo funciona online y en modo avión.

## What We're NOT Doing

- **No** se agrega un endpoint ni un repo method de lookup exacto por DNI
  (`findByDni`). El `search` genérico ya matchea DNI server-side y client-side; un
  lookup exacto sería una segunda ruta de datos redundante.
- **No** se cambia el campo de búsqueda a DNI-only — sería una regresión frente a
  buscar por nombre/apellido.
- **No** se toca la capa de sync: la cadena offline sujeto→medición ya está resuelta
  (ADR-001) y verificada en `MeasurementRepositoryImpl.syncPending()`.
- **No** se agrega detección de duplicados en el cliente antes de crear. Si el DNI ya
  existía server-side, el POST devuelve 409 y el merge canónico ya está manejado
  (ADR-001, regla 2).

---

## Phase 1: `enqueue` devuelve el `localId`

### Overview
Sin el id no hay `SubjectSummary` para devolver. Cambio mínimo: propagar el id que
el repo ya genera.

### Changes Required

**`domain/repository/SubjectRepository.kt`**
```kotlin
/** Persist a new subject locally as PENDING. Returns its localId (offline-first). */
suspend fun enqueue(newSubject: NewSubject): Result<String>
```

**`data/subject/SubjectRepositoryImpl.kt:29-31`**
```kotlin
override suspend fun enqueue(newSubject: NewSubject): Result<String> = runCatching {
    val localId = idGenerator.newId()
    local.insert(newSubject.toPendingUpload(id = localId, now = now()))
    localId
}
```

**`domain/usecase/subject/CreateSubjectUseCase.kt`** — `Result<Unit>` → `Result<String>`.

Único call site: `CreateSubjectViewModel.submit()` (Phase 3). Los fakes de test
(`FakeSubjectRepository`) se ajustan al nuevo tipo.

### Success Criteria
- [ ] `./scripts/compile.sh`
- [ ] `./gradlew :composeApp:testDebugUnitTest --no-daemon`
- [ ] Test nuevo: `enqueue` devuelve el mismo id que quedó persistido en el store.

---

## Phase 2: `CreateSubject` parametrizable y con resultado

### Overview
La pantalla de alta pasa a aceptar un DNI de prefill y un flag de "devolvé el sujeto
creado". Sin parámetros se comporta exactamente como hoy (entrada desde Home).

### Changes Required

**`presentation/navigation/Route.kt`** — `data object` → `data class` con defaults,
para no romper los call sites existentes:
```kotlin
@Serializable
@SerialName("create_subject")
data class CreateSubject(
    val prefillDni: String = "",
    val returnsResult: Boolean = false,
) : Route()
```
> Ojo: `AuthedShell.kt:91` y `HomeNavigation.kt:37` llaman
> `navigateToCreateSubject()`; la firma del helper absorbe el cambio (abajo), así que
> no se tocan. Sí hay que revisar cualquier `Route.CreateSubject` usado como valor
> (pasa a `Route.CreateSubject()`).

**`CreateSubjectDestination.kt`**
```kotlin
sealed class CreateSubjectDestination {
    data object Back : CreateSubjectDestination()
    /** Alta exitosa en un flujo que espera el sujeto de vuelta (medición). */
    data class Created(val subjectJson: String) : CreateSubjectDestination()
}
```

**`CreateSubjectNavigation.kt`** — helper con parámetros + el doble pop:
```kotlin
fun NavController.navigateToCreateSubject(
    prefillDni: String = "",
    returnsResult: Boolean = false,
    navOptions: NavOptions? = null,
) = navigate(Route.CreateSubject(prefillDni, returnsResult), navOptions)

fun NavGraphBuilder.createSubjectScreen(navController: NavController) {
    composable<Route.CreateSubject> { entry ->
        val route = entry.toRoute<Route.CreateSubject>()
        CreateSubjectScreenRoot(
            route = route,
            navigate = { destination -> navigate(destination, navController) },
        )
    }
}

internal fun navigate(destination: CreateSubjectDestination, navController: NavController) {
    when (destination) {
        CreateSubjectDestination.Back -> navController.popBackStack()
        // Stack: CreateMeasurement → SelectSubject → CreateSubject. Soltamos el alta,
        // dejamos el sujeto nuevo en la entry de la medición y soltamos el picker, para
        // que el relevador aterrice en el formulario con el sujeto ya elegido.
        is CreateSubjectDestination.Created -> {
            navController.popBackStack()
            navController.previousBackStackEntry
                ?.savedStateHandle?.set(SELECTED_SUBJECT_RESULT, destination.subjectJson)
            navController.popBackStack()
        }
    }
}
```
> **Trade-off elegido.** La alternativa "más pura" es reenviar en cadena
> (`CreateSubject` → clave propia → `SelectSubject` la reemite como `Confirm`), donde
> cada pantalla solo conoce a su padre inmediato. Cuesta ~20 líneas en 3 archivos y
> una segunda result key. El doble pop resuelve lo mismo en 4 líneas localizadas y
> reusa `SELECTED_SUBJECT_RESULT`, que es exactamente el contrato que
> `MeasurementNavigation.kt:20-28` ya lee. Si en el futuro el picker se abre desde
> otro flujo con alta inline, conviene migrar al reenvío en cadena.

**`CreateSubjectViewModel.kt`**
- Nuevos params de constructor: `private val route: Route.CreateSubject`.
- Estado inicial con el DNI precargado y ya validado:
  ```kotlin
  private val _state = MutableStateFlow(
      CreateSubjectState(dni = TextFieldState(value = route.prefillDni)),
  )
  ```
- En `submit()`, la rama `onSuccess` ramifica sobre `route.returnsResult`:
  ```kotlin
  onSuccess = { localId ->
      syncCoordinator.requestSync()
      if (route.returnsResult) {
          val summary = SubjectSummary(
              id = localId,                       // ADR-001: la medición referencia el localId
              dni = newSubject.dni,
              name = newSubject.name,
              lastName = newSubject.lastName,
              sex = newSubject.sex,
          )
          _state.update {
              it.copy(
                  isSubmitting = false,
                  destination = CreateSubjectDestination.Created(Json.encodeToString(summary)),
              )
          }
      } else {
          _state.update { CreateSubjectState(successMessage = "Sujeto guardado") }
      }
  }
  ```
  > `requestSync()` se adelanta al branch para que dispare igual en los dos caminos.

**`CreateSubjectScreen.kt`** — `CreateSubjectScreenRoot` recibe `route` y lo inyecta:
```kotlin
viewModel: CreateSubjectViewModel = koinViewModel { parametersOf(route) },
```
(mismo idioma que `SurveyResponseFormScreen.kt:50`).

**`di/ViewModelsModule.kt:50`** — `viewModelOf(::CreateSubjectViewModel)` pasa a la
forma parametrizada, como `SurveyResponseFormViewModel` (`:69`):
```kotlin
viewModel { (route: Route.CreateSubject) ->
    CreateSubjectViewModel(route = route, createSubject = get(), syncCoordinator = get())
}
```

### Success Criteria
- [ ] `./scripts/compile.sh`
- [ ] Tests: con `returnsResult = false` el comportamiento actual no cambia (banner +
      form limpio); con `true` emite `Created` con el JSON del `SubjectSummary` y el
      `id` = `localId` devuelto por `enqueue`.
- [ ] Manual: Home → "Cargar sujeto" sigue funcionando igual que antes.

---

## Phase 3: CTA "Crear sujeto" en el selector

### Overview
El acceso desde el picker, disponible **tanto en "Sin resultados" como en el estado
de error** (ver *Descubrimiento crítico*).

### Changes Required

**`SelectSubjectState.kt`** — un derivado, sin campos nuevos:
```kotlin
/** Precarga el alta solo si lo tipeado es un DNI (todo dígitos). */
val createDniPrefill: String
    get() = query.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }.orEmpty()
```

**`SelectSubjectAction.kt`** — `data object CreateSubjectClick : SelectSubjectAction`

**`SelectSubjectDestination.kt`**
```kotlin
data class CreateSubject(val prefillDni: String) : SelectSubjectDestination()
```

**`SelectSubjectViewModel.kt`** — reducer de una línea:
```kotlin
SelectSubjectAction.CreateSubjectClick ->
    _state.update {
        it.copy(destination = SelectSubjectDestination.CreateSubject(it.createDniPrefill))
    }
```

**`SelectSubjectNavigation.kt`**
```kotlin
is SelectSubjectDestination.CreateSubject ->
    navController.navigateToCreateSubject(
        prefillDni = destination.prefillDni,
        returnsResult = true,
    )
```

**`SelectSubjectScreen.kt`** — CTA en las dos ramas (`:111-131`):
```kotlin
state.loadError -> Column(...) {
    Text("No se pudieron cargar los sujetos.", ...)
    AlButton(onClick = { onAction(SelectSubjectAction.RetryClick) }) { Text("Reintentar") }
    // Sin conexión y sin match local caemos acá, no en "Sin resultados": el alta
    // tiene que estar disponible igual, que es justo el caso de campo sin señal.
    AlButton(onClick = { onAction(SelectSubjectAction.CreateSubjectClick) }) {
        Text("Crear sujeto")
    }
}

state.results.isEmpty() -> Column(...) {
    Text("Sin resultados", ...)
    AlButton(onClick = { onAction(SelectSubjectAction.CreateSubjectClick) }) {
        Text("Crear sujeto")
    }
}
```
Jerarquía visual: en la rama de error "Reintentar" es la acción primaria y "Crear
sujeto" la secundaria (variante outline/ghost de `AlButton`); en "Sin resultados"
"Crear sujeto" es la primaria.

### Success Criteria
- [ ] `./scripts/compile.sh`; `:composeApp:assembleDebug`
- [ ] Test de VM: `CreateSubjectClick` con query `"12345678"` emite
      `CreateSubject(prefillDni = "12345678")`; con query `"perez"` emite
      `prefillDni = ""`.
- [ ] Manual (online): buscar un DNI inexistente → "Sin resultados" + "Crear sujeto"
      → el alta abre con el DNI cargado → guardar → vuelve a la **medición** con el
      sujeto seleccionado y establecimiento/peso/talla intactos.
- [ ] Manual (modo avión): mismo recorrido por la rama de error.
- [ ] Manual: entrar al alta desde el picker y volver con **atrás** deja el picker
      como estaba (no selecciona nada).

---

## Testing Strategy

### Unit
- `SubjectRepositoryImplTest` — `enqueue` devuelve el id persistido.
- `CreateSubjectViewModelTest` — prefill de DNI; `returnsResult=true` → `Created` con
  el JSON correcto; `returnsResult=false` → banner (comportamiento actual intacto).
- `SelectSubjectViewModelTest` — `CreateSubjectClick` y el gateo dígitos-only del
  prefill.

### Manual
1. Online, DNI inexistente → crear → vuelve a la medición con el sujeto.
2. Modo avión, mismo recorrido (rama `loadError`).
3. Guardar la medición con el sujeto `PENDING` → volver online → verificar en
   Historial que **primero** sincroniza el sujeto y **después** la medición
   (la retención de `MeasurementRepositoryImpl.kt:66-70`).
4. Crear un sujeto con un DNI que **ya existe** server-side → 409 → el sujeto queda
   `MERGED` y la medición se POSTea con el `canonicalId` (ADR-001, regla 2).
5. Regresión: Home → "Cargar sujeto" sin cambios.

## References
- `thoughts/grilling/adr-001-offline-subject-measurement-chain.md` — la cadena
  offline y el remap canónico (ya implementados).
- `thoughts/grilling/adr-004-subject-picker-union.md` — union read del picker.
- `thoughts/stories/create_measurement_screen.md` — la story original marcaba
  "medir sujetos no sincronizados" como fuera de alcance; ADR-001 lo habilitó y este
  plan cierra el hueco de UX que quedaba.
- `claude.md` → *Navigate-back-with-result*, *Blur-aware form validation*.
