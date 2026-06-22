# Historia de Usuario — Cargar Medición Antropométrica

## Épica

**Carga de mediciones antropométricas por el relevador** — Permitir que un
relevador autenticado registre una medición de **peso y talla** para un sujeto
ya existente en el sistema, eligiendo el sujeto y el establecimiento, con carga
**offline-first** (cola local + sincronización idempotente), para que las
mediciones queden registradas aunque no haya conexión en el momento de la
captura.

> **Flujo y separación.** "Cargar sujeto" y "cargar medición" son **dos flujos
> offline-first independientes**, cada uno con su propia cola. La medición
> referencia el `subject_id` **de servidor** de un sujeto que ya existe; medir un
> sujeto recién creado y todavía no sincronizado queda **fuera de alcance**.

---

## Historia 1 — Elegir el sujeto a medir

> **Como** relevador autenticado
> **quiero** buscar y seleccionar un sujeto ya registrado por nombre, apellido o
> DNI
> **para** asociarle una medición sin tener que recordar ni tipear su id.

### Contexto
- El endpoint de mediciones exige un `subject_id` que es el **UUID de servidor**
  del sujeto; por eso el sujeto debe **existir ya en el servidor**.
- La lista sale de `GET /api/v1/subjects?page=&page_size=&search=` (paginado +
  búsqueda por nombre / apellido / DNI). Devuelve resúmenes livianos:
  `id, dni, name, last_name, sex`. **No hay scoping por establecimiento**: todo
  usuario autenticado ve todos los sujetos.
- La pantalla replica el patrón del **selector de establecimientos**
  (`SelectEstablishment`): barra de búsqueda fija arriba + `LazyColumn` de
  tarjetas a todo el ancho, y devuelve el resultado por `savedStateHandle`.

### Criterios de Aceptación
- [ ] Barra de búsqueda **fija arriba**; al escribir se aplica **debounce
      (~300 ms)** y se vuelve a pedir `GET /subjects` con `search`; las peticiones
      en curso se cancelan ante cada nueva tecla.
- [ ] Cada resultado es una **tarjeta a todo el ancho** con nombre, apellido,
      DNI y sexo.
- [ ] Búsqueda vacía → trae la **primera página** (`page_size` por defecto, p. ej.
      20), ordenada por fecha de creación.
- [ ] **Paginación**: al llegar al final de la lista se pide la página siguiente
      (`page + 1`) hasta `total_pages`.
- [ ] Tocar una tarjeta **selecciona** ese sujeto y vuelve a la pantalla de carga
      de medición con el sujeto elegido.
- [ ] El campo `search` admite máximo **100 caracteres**.

### Estados
- [ ] **Cargando** → spinner.
- [ ] **Error de carga** → mensaje + reintentar.
- [ ] **Sin resultados** → "Sin resultados".
- [ ] **Resultados** → lista de tarjetas seleccionables.
- [ ] **Sin conexión** → como `GET /subjects` es una llamada en vivo, sin red la
      búsqueda muestra error/vacío (ver *Notas / Fuera de Alcance*).

---

## Historia 2 — Cargar la medición antropométrica

> **Como** relevador autenticado
> **quiero** registrar el peso, la talla y la fecha de medición de un sujeto,
> indicando el establecimiento
> **para** que la medición quede cargada de inmediato y se sincronice sola,
> incluso sin conexión.

### Contexto
- Se entra con un sujeto ya elegido (Historia 1). La pantalla muestra el sujeto
  seleccionado (nombre / apellido / DNI) y permite **cambiarlo** (vuelve al
  selector).
- El **establecimiento** se elige en esta pantalla, de la lista de
  establecimientos del perfil del usuario (`/me`). El body **exige
  `establishment_id`** (a diferencia de sujetos, donde el backend lo infiere del
  token).
- La carga es **offline-first**, replicando la cola de sujetos: se genera un
  **UUID v4 en el dispositivo** al capturar, se inserta una fila `PENDING` al
  instante y un coordinador de sync la empuja con reintentos / backoff. Mismo
  `id` ⇒ **idempotente** (200 en replay; 201 en alta nueva, se tratan igual).
- `measured_at` se setea en el **momento de la captura**, en **UTC con zona**
  (`…Z`), para evitar `422` por fecha futura por desfase de reloj.
- El sistema de diseño y la validación por campo siguen el patrón **blur-aware**
  ya usado en `create_subject`.

### Criterios de Aceptación
- [ ] Muestra el **sujeto seleccionado** (nombre, apellido, DNI); un control
      permite **volver al selector** para cambiarlo.
- [ ] **Establecimiento**: selector con los establecimientos del perfil del
      usuario; **obligatorio**.
- [ ] **Peso (kg)**: numérico decimal, teclado numérico; válido **> 0,5 y ≤ 300**.
- [ ] **Talla (cm)**: numérico decimal, teclado numérico; válido
      **> 20,0 y ≤ 250,0**.
- [ ] **Fecha de medición**: por defecto = **ahora**; **no puede ser futura**; se
      envía como **ISO 8601 UTC con zona**.
- [ ] El **BMI no se ingresa** (lo calcula el servidor; no se manda en el body).
- [ ] El botón de **guardar está deshabilitado** hasta que haya sujeto,
      establecimiento, peso y talla válidos.
- [ ] **Validación local antes de la red**: rangos de peso y talla, fecha no
      futura.
- [ ] Al guardar se **encola localmente** (UUID v4 cliente como `id`) y se dispara
      el sync; el usuario **no espera la red**.
- [ ] El body lleva `id`, `subject_id`, `establishment_id`, `weight_kg`,
      `height_cm`, `measured_at` (snake_case); **no** lleva `bmi`.
- [ ] **201 (creado)** y **200 (replay idempotente)** se tratan **igual**: la
      medición queda persistida y sincronizada.
- [ ] Tras encolar con éxito → **confirmación**, el formulario se limpia y el
      relevador permanece para cargar otra medición (consistente con
      `create_subject`).
- [ ] Las **alertas clínicas** que devuelve el servidor (`alerts[]`:
      `alert_type`, `severity`, `detail`) se **guardan junto con la medición** y se
      muestran en el historial / detalle cuando la fila sincroniza (puede no haber
      conexión al capturar, así que la alerta aparece al sincronizar).

### Manejo de Errores (en el sync)
- [ ] **404 (sujeto o establecimiento inexistente)** → fila a **Error** con
      mensaje claro; no se reintenta en loop.
- [ ] **403 (encargado sin acceso aprobado al establecimiento)** → **Error**:
      "No tenés acceso a ese establecimiento."
- [ ] **422 (validación: peso/talla fuera de rango o fecha futura)** → **Error**
      con mensaje; idealmente atajado antes por la validación local.
- [ ] **401 (token faltante / inválido)** → la fila se mantiene **PENDING** y
      reintenta; tras N intentos, dead-letter a **Error**.
- [ ] **Errores de red / 5xx** → **PENDING** con **backoff exponencial**;
      reintento automático al volver la conexión.
- [ ] Los errores **nunca se descartan en silencio**.

### Estados
- [ ] **Editando** → formulario con validación inline por campo (blur-aware).
- [ ] **Guardado local** → confirmación inmediata (no espera la red).
- [ ] **En cola / Sincronizando / Sincronizado / Error** → reflejado en el
      historial (Historia 3).

### Comportamiento del teclado (CMP)
- [ ] Cuando un campo inferior enfocado quedaría tapado por el teclado, el
      formulario lo desplaza para que quede visible (patrón `ScrollState` de CMP),
      tanto en Android como en iOS.

---

## Historia 3 — Historial de mediciones (cola y estados)

> **Como** relevador autenticado
> **quiero** ver el estado de sincronización de las mediciones que cargué y poder
> reintentarlas o descartarlas
> **para** confiar en que ninguna medición se pierde y resolver las que fallan.

### Contexto
- Replica la pantalla de **Historial de sujetos** (`uploads`), pero sobre una
  cola **separada** de mediciones (otra tabla SQLDelight, otro repositorio, otra
  ruta).
- Observa la base local con un flujo reactivo: la lista se actualiza sola a
  medida que las filas cambian de estado.

### Criterios de Aceptación
- [ ] Lista las mediciones cargadas en este dispositivo, **más recientes
      primero**.
- [ ] Cada ítem muestra el sujeto (nombre / DNI), **peso**, **talla**, **fecha**
      y un **badge de estado**: Pendiente / Sincronizando / Sincronizado / Error.
- [ ] Las filas en **Sincronizado** muestran el **BMI** calculado por el servidor
      y, si las hay, las **alertas** clínicas (tipo + severidad + detalle).
- [ ] **Reintentar** disponible en filas en **Error**; **Eliminar** disponible en
      **Pendiente** / **Error** (y en sincronizadas para limpiar el historial).
- [ ] Una acción **"Sincronizar ahora"** dispara un intento manual.
- [ ] **Recuperación ante cierre**: las filas que quedaron "Sincronizando" se
      resetean a **Pendiente** al iniciar.
- [ ] **GC**: las filas terminales (Sincronizado) se purgan después de un tiempo.

### Estados
- [ ] **Cargando** / **Vacío** / **Con datos** (igual que el historial de
      sujetos).

---

## Notas / Fuera de Alcance
- **Medir sujetos no sincronizados (encadenado):** fuera de alcance. La medición
  referencia un `subject_id` de servidor; "cargar sujeto" y "cargar medición" son
  flujos offline-first **independientes**. Un sujeto creado offline debe
  sincronizar antes de poder medirse.
- **Búsqueda de sujetos sin conexión (a definir):** `GET /subjects` es una
  llamada en vivo; sin conexión no se puede buscar/seleccionar un sujeto. La
  *captura y el envío* de la medición sí son offline-first una vez elegido el
  sujeto, pero **iniciar** una medición en el campo sin señal requeriría cachear
  localmente el catálogo de sujetos (read-through). Ese caché **no** está en esta
  story — es la principal limitación a resolver para uso 100% offline en campo.
- **Establecimiento para roles no-manager (asunción):** la lista de
  establecimientos sale del perfil (`/me`), que hoy lista los establecimientos
  del **encargado**. Para `professional` / `admin` el backend no exige relación
  aprobada, pero la app igual necesita de dónde tomar el `establishment_id`; si
  esos roles no tienen establecimientos en su perfil, hace falta otra fuente (a
  definir).
- **Edición de `measured_at`:** por defecto = ahora. Permitir editarla a mano
  queda opcional; si se edita, igual debe ser **≤ ahora**.
- **Sin date picker nativo**, consistente con el resto de la app.
- Reutiliza toda la maquinaria offline-first de sujetos (SQLDelight,
  `SyncCoordinator` / equivalente, `IdGenerator`, backoff, dead-letter) sobre una
  **tabla y un repositorio nuevos** para mediciones — no se mezcla con la cola de
  sujetos.
- `ApiConfig.BASE_URL` sigue siendo el host placeholder hasta configurar un
  servidor real.

## Planes de Origen
- _(a generar)_ `thoughts/plans/create_measurement_screen.md`
- Referencia de sync offline-first: `thoughts/plans/subjects_offline_first_sync.md`
- Referencia de selección con búsqueda:
  `thoughts/plans/select_establishment_search_screen.md`
</content>
</invoke>
