# Historia de Usuario — Cargar un ingreso de alimentos · App móvil

## Épica

**Nutrir — Registro de ingresos de alimentos por establecimiento.** Permitir que
un relevador autenticado registre desde el celular lo que **recibió** un
establecimiento: una fecha, un comentario opcional y **una o más líneas de tipo
de alimento + cantidad**.

Contraparte móvil de la US-3 de backend ("Alta manual de un ingreso con sus
ítems"), que depende de US-1 (catálogo) y US-2 (modelo de datos) y es
prerequisito de US-4 (carga por foto).

> **Esta feature NO es offline-first.** Acordado con el equipo. El catálogo de
> alimentos **no** se descarga ni se cachea en el dispositivo, y el ingreso se
> envía **en vivo**: el usuario espera la respuesta del servidor. Sin conexión,
> la pantalla no permite cargar. Esto la separa deliberadamente de sujetos,
> mediciones y respuestas de encuesta, que sí tienen cola local.
>
> El precedente exacto en el repo es
> `presentation/feature/profile/request_establishment/`: buscador con debounce +
> `load more` paginado + submit online con estado de carga y error.

> **Estado del backend al 2026-09-06** (re-verificado contra `origin` ese día).
> El **catálogo ya está en `develop`** (PR #119 mergeado), así que las dos
> lecturas que necesita el selector existen y se pueden integrar apenas se
> deployen a `tst` y **corra el seed** (`seed_food_catalog.py`, idempotente, en
> el arranque del contenedor).
>
> **`POST /food-intakes` todavía no está en ninguna rama al día.** Ver la tabla
> de abajo: vive solo en #126, que quedó basada en la versión *pre-rebase* de
> #123 y arrastra columnas ya eliminadas. Se puede construir toda la pantalla
> contra el catálogo real y dejar el envío detrás del merge de #123 + #126.

---

## Contrato de backend (verificado, PRs sin mergear)

| PR | Rama | Base | Estado | Aporta |
|---|---|---|---|---|
| [#119](https://github.com/IngenieriaParaLaAlimentacion/backend/pull/119) | `feat/food-catalog` | `develop` | ✅ **mergeado** (`2a3f6df`, 2026-09-06); rama borrada | Catálogo (alimentos, categorías, nutrientes) + seed + los 3 `GET` |
| [#123](https://github.com/IngenieriaParaLaAlimentacion/backend/pull/123) | `feat/food-input-datamodel` | `develop` | 🔄 abierto, **rebasado con force-push** (`5a775aa`) | **Solo modelo de datos**: `food_intakes` / `food_intake_items`, repo, migración, ADR-0012, tests. **Ya no trae endpoint.** |
| [#126](https://github.com/IngenieriaParaLaAlimentacion/backend/pull/126) | `feat/food-stock-per-establishment` | #123 | ⚠️ abierto y **desactualizado** (`ebe3cde`, 2026-09-05) | `POST /food-intakes`, `POST /food-outflows`, stock materializado, movimientos, export CSV |

⚠️ **#126 quedó colgada de la versión vieja de #123.** Su merge-base con
`develop` es `8fa5644` (pre-rebase), así que todavía arrastra el `is_active` que
#119 eliminó al mergear. Necesita rebase antes de integrarse — y **es la única
rama que tiene el `POST` que esta pantalla usa**.

`origin/feat/input-through-receipt` (US-4, foto) volvió a `8fa5644`: hoy no
aporta nada.

### Los tres endpoints que esta pantalla necesita

Todos son `require_any` (admin, analyst, field_worker, superadmin).

**1. Buscar alimentos** — alimenta el selector de línea.
```
GET /api/v1/food-catalog/foods?search=&category_id=&sort_order=name_asc&page=&page_size=
```
Devuelve `PaginatedResponse<FoodListItem>`: `{items, total, page, page_size, total_pages}`,
donde cada ítem es:
```jsonc
{ "id": "…", "code": "pollo_pechuga_sin_piel_crudo",
  "name": "Pollo, pechuga sin piel, crudo",
  "base_unit": "kilogramos",           // "unidades" | "kilogramos" | "litros"
  "category": { "id": "…", "name": "Carnes" } }
```
`search` máx. 200 caracteres, `page_size` máx. **100**,
`sort_order` ∈ `name_asc|name_desc`.

⚠️ **`is_active` ya no existe.** #119 lo eliminó del modelo, del `FoodListItem` y
de los query params al mergear (commit *"remove is_active and sara_group
columns"*). No hay forma de filtrar activos/inactivos ni de retirar un alimento
del catálogo sin borrarlo.

**2. Listar categorías** — alimenta el filtro del selector.
```
GET /api/v1/food-catalog/categories?sort_order=name_asc  ->  list[{id, name}]  // sin paginar, 24 filas
```

**3. Crear el ingreso.**
```jsonc
POST /api/v1/food-intakes
{
  "id": "9f3c…",                            // UUID v4 del cliente — OPCIONAL, clave de idempotencia
  "establishment_id": "…",
  "received_at": "2026-09-06T14:03:00Z",    // NO puede ser futura -> 422
  "comments": "…",                          // opcional, máx 500
  "source": "manual",                       // "manual" | "photo"; default "manual"
  "items": [ { "food_id": "…", "quantity": 12.5 } ]   // mínimo 1
}
```
`quantity`: `> 0`, `max_digits=10`, `decimal_places=3` (máx. `9.999.999,999`).

**La unidad no se envía.** Sale de `base_unit` del alimento — el selector ya la
tiene en el `FoodListItem`, así que la pantalla la muestra pero nunca la manda.

Respuesta **201** (creado) o **200** (replay del mismo `id`); ambas devuelven el
ingreso completo con `items[].unit` resuelta.

### Errores

Envelope canónico `application/problem+json` (ya en `develop`, PR #117): hay que
ramificar por `type`, no por `title`/`detail`.

| Situación | Status | `type` |
|---|---|---|
| Token faltante/inválido | 401 | `/errors/auth/*` |
| Relevador sin relación aprobada al establecimiento | 403 | `/errors/establishment-access-denied` |
| Establecimiento inexistente | 404 | `/errors/establishment-not-found` |
| `food_id` inexistente | 404 | `/errors/food-not-found` |
| `quantity <= 0`, `received_at` futura, `items` vacío, `comments` > 500 | 422 | `/errors/validation-failed` |

### Catálogo real (datos del seed)

- **24 categorías** (grupos SARA 2): Verduras, Frutas, Legumbres/cereales…,
  Leche…, Yogures, Quesos, **Carnes**, **Huevos**, Pescados y mariscos, Aceites,
  Frutas secas…, Azúcares…, Golosinas…, Grasas, Snacks salados, Aderezos,
  Caldos…, Postres industriales…, Sales, Bebidas con azúcar, Bebidas sin azúcar,
  Bebidas alcohólicas…, Infusiones, Comidas rápidas.
- **898 alimentos**, con `base_unit`: **767 kilogramos, 94 litros, 37 unidades**.
- El "subtipo" de carne **no es un campo**: son filas distintas del catálogo
  (`Pollo, pechuga sin piel, crudo`, `Pollo con piel, crudo`, `Nuggets o patitas
  de pollo`, …), todas en la categoría `carnes`. En huevos, `Huevo de gallina,
  entero, crudo` tiene `base_unit = unidades`.
- **Cada alimento tiene UNA sola unidad.** `foods.base_unit` es una columna
  escalar: no hay `allowed_units`, ni factor de conversión, ni unidad en la línea
  del ingreso. ✅ **Decidido: la app no ofrece elegir unidad** — usa siempre la
  del catálogo. Ver *Hallazgos* #1 para el detalle y para la nota pendiente del
  lado de backend.

---

## Historia 1 — Elegir el alimento de una línea

> **Como** relevador autenticado
> **quiero** buscar un alimento del catálogo por nombre y filtrarlo por categoría
> **para** agregarlo como línea del ingreso sin tener que recordar su código.

### Contexto
- Réplica del patrón ya usado en `SelectSubject` / `RequestEstablishment`:
  buscador fijo arriba con **debounce ~300 ms**, `collectLatest` que cancela la
  petición en curso, `LazyColumn` de tarjetas a todo el ancho y **load more** al
  llegar al final.
- Devuelve el alimento elegido al formulario por `savedStateHandle`, igual que
  `SELECTED_SUBJECT_RESULT`.
- Son **898 alimentos**: sin filtro de categoría, la lista inicial no dice nada
  útil. El filtro por categoría es parte del selector, no un extra.

### Criterios de Aceptación
- [ ] Barra de búsqueda **fija arriba**; al escribir se aplica **debounce
      (~300 ms)** y se vuelve a pedir `GET /food-catalog/foods` con `search`; las
      peticiones en curso se cancelan ante cada nueva tecla.
- [ ] Fila de **chips de categoría** (de `GET /food-catalog/categories`) que
      filtra por `category_id`; se combina con la búsqueda por texto.
- [ ] Cada resultado es una **tarjeta a todo el ancho** con **nombre**,
      **categoría** y **unidad** ("kg" / "L" / "u").
- [ ] Búsqueda vacía y sin categoría → primera página ordenada por nombre
      (`sort_order=name_asc`).
- [ ] **Paginación**: al llegar al final se pide `page + 1` hasta `total_pages`.
- [ ] ~~Filtrar por activos~~ — **`is_active` ya no existe** en el catálogo
      mergeado. Todo alimento del catálogo es seleccionable.
- [ ] Tocar una tarjeta **selecciona** el alimento y vuelve al formulario.
- [ ] El campo `search` admite máximo **200** caracteres.

### Estados
- [ ] **Cargando** → spinner. **Error de carga** → mensaje + reintentar.
      **Sin resultados** → "Sin resultados". **Resultados** → lista.
- [ ] **Sin conexión** → error explícito con reintento. Es una llamada en vivo:
      sin red no hay selector.

### Riesgo conocido
- [ ] La búsqueda del servidor es `ILIKE %term%` **solo sobre `name`** y **no
      ignora acentos**: "azucar" no encuentra "Azúcar…", "higado" no encuentra
      "Hígado de pollo". Con 898 alimentos y tipeo rápido en el campo, se nota.
      **Sin caché local no lo podemos esquivar del lado de la app** → hay que
      pedirle al backend un `unaccent` (ver *Hallazgos*).

---

## Historia 2 — Cargar el ingreso con sus líneas

> **Como** relevador autenticado
> **quiero** registrar lo que recibió el establecimiento indicando fecha y una o
> más líneas de alimento + cantidad
> **para** que quede registrado manualmente lo que entró.

### Contexto
- Un ingreso es **una cabecera + N líneas**: es el primer formulario de la app
  con una lista dinámica de ítems. Todo lo existente es de un registro plano.
- El **establecimiento** se elige como en la medición: lista de establecimientos
  **aprobados** del perfil (`/me`), **pre-cargado** con el establecimiento actual
  de Home (ADR-012: *pre-fill, not gate*).
- `received_at` se setea en el momento de la captura, en **UTC con zona (`…Z`)**,
  para evitar el `422` por fecha futura por desfase de reloj — el mismo trampa
  que `measured_at`.
- Envío **en vivo**: el botón entra en estado de carga y la pantalla espera la
  respuesta.

### Criterios de Aceptación

**Cabecera**
- [ ] **Establecimiento**: selector con los establecimientos **aprobados** del
      perfil; **obligatorio**; pre-seleccionado con el actual de Home si hay uno.
- [ ] **Fecha de recepción**: por defecto = ahora; **no puede ser futura**; se
      envía ISO 8601 UTC con zona. Puede ser **retroactiva** (el backend lo
      permite explícitamente).
- [ ] **Comentarios**: opcional, **máximo 500** caracteres, con contador.

**Líneas**
- [ ] Se puede agregar **una o más líneas**; con cero líneas el botón de guardar
      está deshabilitado.
- [ ] Agregar una línea abre el **selector de alimento** (Historia 1).
- [ ] Elegido el alimento, la línea muestra **nombre + unidad fija** ("kg" / "L"
      / "u") al lado del campo de cantidad.
- [ ] ✅ **DECIDIDO: la app no elige unidad.** Se usa siempre la del catálogo
      (`base_unit`), que es además la única que el backend acepta. La unidad se
      renderiza como **sufijo del campo de cantidad, nunca como selector** — la
      UI no debe insinuar una elección que no existe. Ver *Hallazgos* #1: el
      puente unidad↔peso queda del lado del backend y **fuera del alcance de esta
      historia**.
- [ ] Como el 85 % del catálogo es `kilogramos`, el caso normal es pesar: el
      relevador que recibe 3 zapallos carga los kilos. El copy del campo debe
      dejarlo claro ("Cantidad en kg") en vez de dar a entender que puede contar.
- [ ] Una línea se puede **editar** (cambiar la cantidad) y **eliminar**.
- [ ] **No se repite el mismo alimento en dos líneas**: si ya está, se edita la
      línea existente en vez de duplicarla. *(Decisión de UX: el backend acepta
      duplicados y los suma al saldo; duplicar en la UI es confuso.)*

**Cantidad**
- [ ] Numérico, teclado numérico, **> 0**, máximo **3 decimales**, hasta
      **9.999.999,999**.
- [ ] Si `base_unit == unidades` → **entero**: teclado sin coma y validación
      local "Ingresá una cantidad entera." ⚠️ **El backend no lo valida** (acepta
      `12.5` unidades de huevo). Ver *Hallazgos*.
- [ ] Si `base_unit == kilogramos | litros` → decimal, se acepta coma o punto y
      se normaliza a punto al serializar.
- [ ] Validación **blur-aware** por campo (`core/validation/`), consistente con
      `create_subject` / `create_measurement`.

**Envío**
- [ ] El body lleva `id`, `establishment_id`, `received_at`, `comments`,
      `source: "manual"`, `items: [{food_id, quantity}]` (snake_case).
- [ ] Se genera un **UUID v4 en el dispositivo** como `id` **aunque no haya cola
      offline**: hace que el botón "Reintentar" tras un timeout sea seguro (el
      servidor no puede duplicar el ingreso ni inflar el stock). Es una línea de
      código y evita el peor bug posible de esta pantalla. El mismo `id` se
      conserva mientras el usuario no salga del formulario.
- [ ] **201 y 200 se tratan igual**: el ingreso quedó registrado.
- [ ] Durante el envío el botón muestra estado de carga y el formulario queda
      bloqueado; no se puede disparar dos veces.
- [ ] Con éxito → confirmación, el formulario se limpia (y se genera un `id`
      nuevo) y el relevador permanece para cargar otro ingreso — consistente con
      `create_subject` / `create_measurement`.

### Manejo de Errores (en vivo, sobre el formulario)
- [ ] **Sin conexión / timeout / 5xx** → "No se pudo enviar. Revisá tu conexión."
      con **Reintentar**, conservando el formulario **y el mismo `id`**.
- [ ] **403 `/errors/establishment-access-denied`** → "No tenés acceso a ese
      establecimiento."
- [ ] **404 `/errors/establishment-not-found`** → "El establecimiento no existe."
- [ ] **404 `/errors/food-not-found`** → "Un alimento de la carga ya no está en el
      catálogo." (Antes esto era casi imposible porque los alimentos se
      desactivaban en vez de borrarse; al eliminarse `is_active`, un alimento
      retirado del catálogo **sí desaparece**, así que el caso es real: el
      selector puede devolver un id que el `POST` rechaza minutos después.)
- [ ] **422** → mensaje de validación; idealmente atajado antes por la validación
      local.
- [ ] **401** → sesión expirada, se delega al manejo de auth existente.
- [ ] Los errores **nunca se descartan en silencio** y **nunca pierden el
      formulario cargado**.

### Comportamiento del teclado (CMP)
- [ ] El campo de cantidad enfocado nunca queda tapado por el teclado (patrón
      `ScrollState` + `onGloballyPositioned`, **no** `BringIntoViewRequester`),
      en Android y en iOS. Con una lista dinámica de líneas esto es más delicado
      que en los formularios planos existentes.

### Estados
- [ ] **Editando** → formulario con validación inline por campo (blur-aware).
- [ ] **Enviando** → botón en carga, formulario bloqueado.
- [ ] **Enviado** → confirmación + formulario limpio.
- [ ] **Error** → mensaje accionable, formulario intacto.
- [ ] **Sin conexión al entrar** → la pantalla avisa que hace falta conexión y no
      deja cargar (el selector de alimentos tampoco funcionaría).

---

## Hallazgos sobre el backend (para llevar al equipo)

1. 🟡 **Un alimento tiene una sola unidad posible — no se puede cargar "3
   zapallos" ni "500 g de huevo".**

   > ✅ **Decisión (2026-09-06): opción A.** La app **no** ofrece elegir unidad;
   > usa siempre la `base_unit` que devuelve el catálogo. Para móvil esto cierra
   > el tema y **no bloquea nada**: la pantalla se construye igual. Lo que sigue
   > queda como **nota para el equipo de backend**, porque el puente unidad↔peso
   > sí les hace falta para `nutrir` (ver más abajo: son 131 alimentos sin forma
   > de convertirse a gramos).

   `foods.base_unit` es **una columna escalar** en `foods`. No existe
   `allowed_units`, ni un factor de conversión, ni una unidad en la línea del
   ingreso: `FoodIntakeItem` es `{food_id, quantity}` y su docstring lo dice
   explícito — *"the line carries no unit of its own — the unit is the referenced
   food's `base_unit`"*. `food_stock` guarda **un solo saldo escalar** por
   `(establishment_id, food_id)`.

   Consecuencias concretas, con datos del seed:
   - `Zapallo, crudo` es `kilogramos`. **"3 zapallos" es irrepresentable**: el
     relevador tiene que pesar o estimar los kilos.
   - `Huevo de gallina, entero, crudo` es `unidades`. **"500 g de huevo" es
     irrepresentable.**
   - Solo **37 de 898** alimentos son `unidades`, y **28 de esos 37 son ítems de
     McDonald's**. Lo que realmente se cuenta en una cocina son 8 filas de huevo
     y un huevo Kinder. Todo lo demás que un establecimiento recibe contado
     (zapallos, lechugas, panes, latas) es **solo peso**.

   **¿Dónde vive el dato "1 unidad = N gramos"? En ningún lado.** Las columnas de
   `foods` son exactamente: `id, code, name, category_id, base_unit, source_page,
   created_at, updated_at`. No hay peso, ni densidad, ni factor de conversión. No
   es lógica sin usar: **el número no existe**, ni en el modelo, ni en el seed, ni
   en la fuente.

   **Y esto ya rompe a `nutrir`, no solo a la UX.** `food_nutrient_values` está
   expresado **por 100 g** para *todos* los alimentos — también para los 37
   `unidades` y los 94 `litros`. Sin puente, para esos **131 de 898 alimentos
   (14,6 %)** es **imposible** calcular nutrientes a partir del stock: no hay
   forma de pasar "12 huevos" ni "5 litros de aceite" a gramos. Son dos puentes
   distintos:
   - **gramos por unidad** (37 alimentos) → habilita *además* el caso del
     relevador: entrar "3 zapallos" y guardar kg.
   - **gramos por litro / densidad** (94 alimentos) → solo hace falta para el
     cálculo nutricional; el aceite se recibe en litros y está bien así.

   **No es una limitación del endpoint de ingresos** — el ingreso refleja
   fielmente al catálogo. La restricción vive en el catálogo, y **ADR-0011 la
   tomó a conciencia**: SARA 2 no tiene ningún concepto de unidad de compra o
   recepción (solo la base de 100 g para nutrientes), así que `base_unit` es "una
   decisión de dominio que el catálogo toma en nombre de SARA 2", elegida por
   alimento "para coincidir con cómo se recibe realmente". El puente entre
   `base_unit` y `amount_per_100g` (un factor peso-por-unidad) está
   **deliberadamente sin modelar**, delegado a "la feature futura que realmente
   lo necesite, acotado a los alimentos que lo requieran".

   **Esta es esa feature.** Re-verificado el 2026-09-06 contra `origin/develop`
   ya con #119 mergeado, y contra las tres ramas abiertas: **no hay una sola
   ocurrencia** de `grams_per_unit`, `allowed_units`, `conversion_factor` ni
   equivalente en ninguna. El catálogo ya está en `develop`, así que la opción B
   pasó de "cambiar un PR abierto" a "una migración aditiva sobre `develop`" —
   sigue siendo barata, pero conviene plantearla antes de que #126 mergee y
   empiece a acumular saldos.

   Opciones, de menor a mayor costo:

   | Opción | Qué implica | Riesgo |
   |---|---|---|
   | **A. Aceptar** (statu quo) | La app muestra la unidad como fija; el relevador convierte de cabeza. | Fricción y error justo en el momento de captura; calidad de dato pobre. |
   | **B. `grams_per_unit` nullable en `foods`** | El relevador entra "3 zapallos", la app muestra "≈ 4,5 kg" y **envía kg**. Un saldo por alimento, invariante de ADR-0013 intacta. Es exactamente el puente que ADR-0011 difirió. | Hace falta una **segunda fuente** de peso-por-unidad, aunque sea para un puñado de alimentos. Aditivo: se puede sumar después. |
   | **C. Varias unidades por alimento** | Unidad en la línea + un saldo por `(alimento, unidad)`. | **Rompe** el grano de `food_stock` y el consumidor de `nutrir`. Aditivo *no* es: cambiarlo después es una migración de datos. |

   **Para esta historia se adoptó A.** B queda como recomendación *para el
   backend*, desacoplada del móvil: sumar `grams_per_unit` es una migración
   aditiva y el sourcing es chico si se acota a los 8 alimentos de huevo (que son
   el 100 % de lo que una cocina cuenta de verdad — los otros 29 `unidades` son
   McDonald's y un huevo Kinder). Cambiar el grano de `food_stock` (opción C), en
   cambio, deja de ser aditivo apenas #126 mergee y empiecen a acumularse saldos.

   **Reparto de trabajo, si se avanza con B:**
   - **Catálogo** — dueño del *dato que falta*. Una columna nullable
     `grams_per_unit` en `foods` + conseguir los valores. Es un problema de
     **fuente de datos**, no de código: ADR-0011 dice que sourcearlo "requeriría
     una fuente secundaria distinta". **Es el bloqueante**: sin el número no se
     puede escribir la conversión. Se puede acotar a los ~37 alimentos `unidades`,
     o incluso a los 8 de huevo, que es lo único que una cocina cuenta de verdad.
   - **Stock / ingresos / egresos** — dueño del *comportamiento que falta*: un
     campo `unit` opcional en la línea, convertir a la unidad base y aplicar a un
     único saldo. Es chico (una validación + una multiplicación en
     `FoodIntakeService`), y **solo se puede hacer después** del punto anterior.

2. **CA 2 no se cumple del todo.** El criterio dice "al cargar huevos la cantidad
   se interpreta y valida como unidades (**entero > 0**)". El backend valida
   `quantity: Decimal(gt=0, max_digits=10, decimal_places=3)` **igual para las
   tres unidades**: acepta `12.5` unidades de huevo. La app lo valida
   localmente, pero **la API queda abierta** (backoffice, US-4 por foto, o
   cualquier otro cliente pueden meter medios huevos).
   → Sugerencia: un validador por `base_unit` en `FoodIntakeService`.

3. **"Unidad incoherente respecto al tipo → 422" es inaplicable.** La línea **no
   lleva unidad**: siempre se toma de `foods.base_unit`. El caso no puede
   ocurrir. Es una buena decisión de diseño — el criterio de aceptación está
   desactualizado respecto de la implementación.

4. **El "subtipo de carne" no existe como campo.** CA 2 pide "se permite/solicita
   el subtipo (pata de pollo, pechuga…)". En la implementación son filas
   distintas del catálogo dentro de la categoría `carnes`. La app lo resuelve con
   *categoría → alimento*; **no hay** un campo `subtype`. Conviene confirmar que
   la granularidad de SARA 2 alcanza (existe `Pollo, pechuga sin piel`, pero no
   "pata de pollo" como tal).

5. **La búsqueda del catálogo no ignora acentos.** `ILIKE %term%` sobre `name`:
   "azucar" no encuentra "Azúcar". **Al no cachear el catálogo, la app no tiene
   forma de compensarlo.** Es el pedido de backend más importante de esta
   historia: `unaccent` sobre `name` (y, si se puede, matchear también `code`,
   que ya viene sin acentos).

6. **Se eliminó `is_active` del catálogo.** #119 lo sacó del modelo, del
   `FoodListItem` y de los query params antes de mergear. Consecuencia: ya no hay
   *soft-retire* — retirar un alimento mal categorizado o duplicado obliga a
   borrar la fila, y `food_intake_items.food_id` es `ON DELETE RESTRICT`, así que
   un alimento con ingresos cargados **no se puede borrar en absoluto**. ADR-0011
   justificaba explícitamente `is_active` por este motivo. Vale preguntar si fue
   deliberado.

7. **No hay endpoint de listado de ingresos.** Existen `POST /food-intakes` y el
   historial *por alimento* (`/establishments/{id}/stock/{food_id}/movements`),
   pero **no** un `GET /food-intakes?establishment_id=…`. Sin él y sin cola local,
   **la app no puede mostrar "los ingresos que cargué"** — ni siquiera los
   propios, ni siquiera online. Si producto quiere esa vista, falta ese endpoint.

8. **`synced_at`** existe en el modelo (`food_intakes.synced_at`) pero no se
   expone ni se setea en la API. Sin impacto móvil hoy.

---

## Decisiones abiertas

1. **Dónde vive "Alimentos" en la navegación.** Home tiene hoy **3** quick
   actions en un `Row` (`Medición | Sujeto | Encuesta`). Sumar una cuarta empuja
   a 2×2 o a scroll horizontal.

2. **¿Hay pantalla de stock?** `GET /establishments/{id}/stock` existe y es la
   única forma de que el relevador vea el efecto de lo que cargó. Es una lectura
   online simple (paginada, con filtro por categoría y `only_available`). **No
   está en la US-3**; si se quiere, es una historia aparte y chica.

3. **Representación de `quantity`.** `Numeric(10,3)` del lado del servidor. Del
   lado de la app conviene mantenerla como **`String` normalizado a 3 decimales**
   hasta el borde de red, en vez de `Double`, para no arrastrar ruido de coma
   flotante en un valor que el servidor acumula en un saldo.

---

## Notas técnicas de implementación

- **Sin SQLDelight.** Esta feature **no** agrega tablas, ni migración `7.sqm`, ni
  `SyncEngine`. Es la primera pantalla de carga de la app que no toca la base
  local — vale dejarlo explícito en el plan para que nadie replique por inercia
  la maquinaria de mediciones.
- **Precedente a copiar:** `presentation/feature/profile/request_establishment/`
  (buscador con debounce + paginado + submit online con error recuperable) y
  `presentation/feature/subject/select_subject/` (selector que devuelve resultado
  por `savedStateHandle`).
- **`FoodCatalogApi` / `FoodIntakeApi`** replican `MeasurementApi`: el cliente
  authed **no** setea `expectSuccess`, así que se ramifica por
  `response.status.value` explícitamente. Con el envelope problem+json ya en
  `develop`, conviene deserializar el body de error y ramificar por **`type`**
  para distinguir `food-not-found` de `establishment-not-found` (ambos 404).
- **Pantallas nuevas** (`create_food_intake`, `select_food`): seis archivos cada
  una siguiendo `presentation/feature/home/`, registradas en `Route.kt` y
  cableadas en `AppNavHost` / `AuthedShell`.
- **Nuevos íconos MingCute** (alimentos / canasta): usar la skill
  `add-mingcute-icon`; no se usan iconos de Material.
- La cantidad, cuando se muestra como dato (líneas cargadas, confirmación), va
  con la tipografía mono `AlTheme.typography.data*`.

---

## Fuera de Alcance
- **Cola offline / catálogo cacheado**: acordado con el equipo, esta feature es
  online.
- **Carga por foto de remito** (US-4, `source: "photo"`): rama
  `feat/input-through-receipt`, todavía sin implementación propia.
- **Composición nutricional** de los alimentos (`/food-catalog/foods/{id}`).
- **Egresos** (`POST /food-outflows`) y **export CSV** del stock.
- **Historial de ingresos cargados**: no hay endpoint (ver *Hallazgos* #7).
- **Editar o borrar un ingreso ya cargado**: no hay endpoint, y el backend
  advierte que un borrado tendría que revertir el saldo (hoy nada lo garantiza).

## Referencias
- Backend: ADR-0011 (esquema del catálogo), ADR-0012 (esquema de ingresos),
  ADR-0013 (stock materializado) — en `docs/adr/` de `feat/food-stock-per-establishment`.
- App: `thoughts/stories/create_measurement_screen.md` (formulario análogo, pero
  offline-first), `thoughts/grilling/adr-012-current-establishment-prefill-not-gate.md`.
