# [Móvil] Cargar un ingreso de alimentos

**Épica:** Nutrir — Registro de ingresos de alimentos por establecimiento
**Contraparte de:** US-3 backend ("Alta manual de un ingreso con sus ítems")
**Puntos:** 5 · **Tipo:** ⭐ Obligatoria

---

## Descripción

> **Como** relevador autenticado
> **quiero** registrar desde la app lo que recibió el establecimiento, indicando
> fecha y una o más líneas de tipo de alimento + cantidad
> **para** que quede registrado manualmente lo que entró.

Se agregan **dos pantallas**: el formulario de carga del ingreso y el selector de
alimento del catálogo que lo alimenta.

---

## Alcance

**Incluye**
- Formulario de ingreso: establecimiento, fecha, comentarios y N líneas.
- Selector de alimento con búsqueda por texto, filtro por categoría y paginado.
- Envío a `POST /food-intakes` con manejo de errores.

**No incluye**
- Cola offline / caché del catálogo. **Esta feature es online**: el selector y el
  envío son llamadas en vivo y sin conexión la pantalla no permite cargar.
- Elección de unidad. La unidad la define el catálogo (`base_unit`) y **no se
  envía**; la app la muestra como sufijo del campo de cantidad, nunca como
  selector.
- Egresos (`POST /food-outflows`), pantalla de stock, historial de ingresos
  (no hay endpoint de listado), carga por foto (US-4), composición nutricional.

---

## Contratos de API

Base: `/api/v1`. Todos los endpoints aceptan cualquier rol autenticado
(`admin`, `analyst`, `field_worker`, `superadmin`).

### 1. `GET /food-catalog/foods` — buscar alimentos

Alimenta el selector.

| Query param | Tipo | Default | Notas |
|---|---|---|---|
| `search` | string | — | máx. 200 caracteres |
| `category_id` | UUID | — | |
| `sort_order` | enum | `name_asc` | `name_asc` \| `name_desc` |
| `page` | int | `1` | ≥ 1 |
| `page_size` | int | `20` | 1–100 |

**200** → `PaginatedResponse<FoodListItem>`

```jsonc
{
  "items": [
    {
      "id": "3f1a…",
      "code": "pollo_pechuga_sin_piel_crudo",
      "name": "Pollo, pechuga sin piel, crudo",
      "base_unit": "kilogramos",              // "unidades" | "kilogramos" | "litros"
      "category": { "id": "9c2b…", "name": "Carnes" }
    }
  ],
  "total": 898, "page": 1, "page_size": 20, "total_pages": 45
}
```

> La búsqueda es `ILIKE %term%` **solo sobre `name`** y **no ignora acentos**
> ("azucar" no encuentra "Azúcar"). No hay filtro de activo/inactivo.

### 2. `GET /food-catalog/categories` — listar categorías

Alimenta el filtro del selector.

| Query param | Tipo | Default |
|---|---|---|
| `sort_order` | enum | `name_asc` |

**200** → `[{ "id": "…", "name": "Carnes" }]` — sin paginar, 24 filas.

### 3. `POST /food-intakes` — crear el ingreso

```jsonc
{
  "id": "9f3c…",                            // UUID v4 del cliente, opcional — clave de idempotencia
  "establishment_id": "7a2e…",              // requerido
  "received_at": "2026-09-06T14:03:00Z",    // requerido, ISO 8601 UTC, NO futura
  "comments": "Entrega semanal",            // opcional, máx. 500
  "source": "manual",                       // opcional, default "manual"
  "items": [                                // mínimo 1 elemento
    { "food_id": "3f1a…", "quantity": 12.5 }
  ]
}
```

- `quantity`: `> 0`, máx. 10 dígitos y **3 decimales** (tope `9999999.999`).
- **La línea no lleva unidad**: se resuelve del `base_unit` del alimento.

**201 Created** (alta nueva) / **200 OK** (replay idempotente del mismo `id`) →
mismo body en ambos casos:

```jsonc
{
  "id": "9f3c…",
  "establishment_id": "7a2e…",
  "responsible_id": "b41d…",
  "received_at": "2026-09-06T14:03:00Z",
  "comments": "Entrega semanal",
  "source": "manual",
  "created_at": "2026-09-06T14:03:02Z",
  "items": [
    {
      "food": { "id": "3f1a…", "name": "Pollo, pechuga sin piel, crudo" },
      "unit": "kilogramos",
      "quantity": "12.500"
    }
  ]
}
```

### Errores

Todos vienen como `application/problem+json`. **Ramificar por `type`**, no por
`title` / `detail`.

| Status | `type` | Cuándo |
|---|---|---|
| 401 | `/errors/auth/*` | token faltante, inválido o expirado |
| 403 | `/errors/establishment-access-denied` | relevador sin relación aprobada al establecimiento |
| 404 | `/errors/establishment-not-found` | el establecimiento no existe |
| 404 | `/errors/food-not-found` | una línea referencia un alimento inexistente |
| 422 | `/errors/validation-failed` | `quantity <= 0`, `received_at` futura, `items` vacío, `comments` > 500 |

---

## Pantalla 1 — Selector de alimento (`select_food`)

Réplica del patrón de `select_subject` / `request_establishment`: buscador fijo
arriba, `LazyColumn` de tarjetas a todo el ancho, `load more` al final. Devuelve
el alimento elegido por `savedStateHandle`.

### Criterios de aceptación

- [ ] Barra de búsqueda **fija arriba**; al tipear se aplica **debounce ~300 ms**
      y se vuelve a pedir `GET /food-catalog/foods` con `search`.
- [ ] Las peticiones en curso **se cancelan** ante cada nueva tecla
      (`collectLatest`).
- [ ] El campo de búsqueda admite máximo **200** caracteres.
- [ ] Fila de **chips de categoría** desde `GET /food-catalog/categories`, que
      filtra por `category_id` y **se combina** con la búsqueda por texto.
- [ ] Cada resultado es una tarjeta a todo el ancho con **nombre**, **categoría**
      y **unidad** ("kg" / "L" / "u").
- [ ] Sin búsqueda ni categoría → primera página ordenada por nombre
      (`sort_order=name_asc`).
- [ ] **Paginación**: al llegar al final de la lista se pide `page + 1` hasta
      `total_pages`.
- [ ] Tocar una tarjeta selecciona el alimento y **vuelve al formulario** con él.
- [ ] **Estados**: cargando (spinner) · error de carga (mensaje + reintentar) ·
      sin resultados ("Sin resultados") · con resultados.
- [ ] **Sin conexión** → error explícito con reintento (es una llamada en vivo).

---

## Pantalla 2 — Formulario de ingreso (`create_food_intake`)

### Criterios de aceptación — cabecera

- [ ] **Establecimiento**: selector con los establecimientos **aprobados** del
      perfil (`/me`); **obligatorio**.
- [ ] Viene **pre-seleccionado** con el establecimiento actual de Home si hay uno
      (pre-fill, no bloqueo).
- [ ] **Fecha de recepción**: por defecto = ahora; **no puede ser futura**; se
      envía ISO 8601 **UTC con zona** (`…Z`). Se admite fecha retroactiva.
- [ ] **Comentarios**: opcional, **máx. 500** caracteres, con contador visible.

### Criterios de aceptación — líneas

- [ ] Se puede agregar **una o más** líneas; con cero líneas el botón de guardar
      está deshabilitado.
- [ ] "Agregar alimento" abre la **Pantalla 1**.
- [ ] Elegido el alimento, la línea muestra **nombre** y la **unidad del
      catálogo** al lado del campo de cantidad.
- [ ] La unidad **no es elegible**: se renderiza como **sufijo del campo**
      (`Cantidad (kg)`), nunca como selector, y no viaja en el body.
- [ ] Una línea se puede **editar** (cambiar cantidad) y **eliminar**.
- [ ] **No se repite el mismo alimento en dos líneas**: si ya está, se edita la
      existente en vez de duplicarla.

### Criterios de aceptación — cantidad

- [ ] Numérico, teclado numérico, **> 0**, máx. **3 decimales**, tope
      **9.999.999,999**.
- [ ] Si `base_unit == "unidades"` → **entero**: teclado sin separador decimal y
      error "Ingresá una cantidad entera."
- [ ] Si `base_unit == "kilogramos" | "litros"` → decimal; se acepta coma o punto
      y **se normaliza a punto** al serializar.
- [ ] Validación **blur-aware** por campo (`core/validation/`): el error aparece
      al salir del campo, nunca mientras se escribe; *Guardar* fuerza todos.

### Criterios de aceptación — envío

- [ ] El body lleva `id`, `establishment_id`, `received_at`, `comments`,
      `source: "manual"` e `items: [{food_id, quantity}]` en snake_case.
- [ ] Se genera un **UUID v4 en el dispositivo** como `id` y **se conserva entre
      reintentos**, de modo que un reintento tras timeout no pueda duplicar el
      ingreso ni inflar el stock.
- [ ] **201 y 200 se tratan igual**: el ingreso quedó registrado.
- [ ] Durante el envío el botón muestra estado de carga y el formulario queda
      bloqueado; **no se puede disparar dos veces**.
- [ ] Con éxito → confirmación, el formulario se limpia, **se genera un `id`
      nuevo** y el relevador permanece en la pantalla para cargar otro ingreso.

### Criterios de aceptación — errores

- [ ] **Sin conexión / timeout / 5xx** → "No se pudo enviar. Revisá tu conexión."
      con **Reintentar**, conservando el formulario **y el mismo `id`**.
- [ ] **403** → "No tenés acceso a ese establecimiento."
- [ ] **404 `establishment-not-found`** → "El establecimiento no existe."
- [ ] **404 `food-not-found`** → "Un alimento de la carga ya no está en el
      catálogo."
- [ ] **422** → mensaje de validación sobre el campo correspondiente.
- [ ] **401** → se delega al manejo de sesión existente.
- [ ] Ningún error descarta el formulario cargado ni se pierde en silencio.

### Criterios de aceptación — teclado (CMP)

- [ ] Un campo de cantidad enfocado **nunca queda tapado por el teclado**, en
      Android y en iOS: patrón `ScrollState` + `onGloballyPositioned`
      (**no** `BringIntoViewRequester`).

### Estados de la pantalla

- [ ] **Editando** · **Enviando** · **Enviado** · **Error** · **Sin conexión al
      entrar** (avisa que hace falta conexión y no deja cargar).

---

## Notas técnicas

- **Sin SQLDelight**: esta feature no agrega tablas, ni migración, ni
  `SyncEngine`. Es la primera pantalla de carga de la app que no toca la base
  local — no replicar la maquinaria offline de mediciones.
- **Patrones a copiar**: `presentation/feature/profile/request_establishment/`
  (búsqueda con debounce + paginado + submit online) y
  `presentation/feature/subject/select_subject/` (selector que devuelve resultado
  por `savedStateHandle`).
- **Cliente HTTP**: el cliente authed no setea `expectSuccess`; ramificar por
  `response.status.value` y deserializar el envelope para leer `type` (hace falta
  para distinguir los dos 404).
- **`quantity`** se mantiene como `String` normalizado a 3 decimales hasta el
  borde de red, para no arrastrar ruido de coma flotante.
- **Arquitectura**: seis archivos por pantalla (`Action`, `Destination`,
  `Navigation`, `Screen`, `State`, `ViewModel`), rutas en
  `presentation/navigation/Route.kt`, cableado en `AppNavHost` / `AuthedShell`.
- **Íconos**: MingCute vía la skill `add-mingcute-icon`. No usar Material Icons.
- Las cantidades, al mostrarse como dato, usan `AlTheme.typography.data*`.

---

## Dependencias

- ✅ `GET /food-catalog/foods` y `GET /food-catalog/categories` — **mergeados a
  `develop`** (backend PR #119). Requieren que corra el seed del catálogo en el
  entorno.
- ⏳ `POST /food-intakes` — backend PRs **#123** y **#126**, todavía sin mergear.
  Las dos pantallas se pueden construir contra el catálogo real; solo el envío
  queda bloqueado.
