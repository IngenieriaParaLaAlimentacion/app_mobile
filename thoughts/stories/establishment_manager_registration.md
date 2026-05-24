# Historia de Usuario — Autorregistro de Encargado de Establecimiento

## Épica

**Onboarding del encargado de establecimiento** — Permitir que un futuro
encargado de establecimiento cree su propia cuenta desde la app mobile, se
asocie a uno o más establecimientos y la envíe para aprobación del
administrador.

---

## Historia 1 — Autorregistrarse como encargado de establecimiento

> **Como** futuro encargado de establecimiento
> **quiero** registrarme desde la app con mis datos y los establecimientos que
> manejo
> **para** que un administrador pueda revisar y aprobar mi cuenta sin que tenga
> que crearse a mano.

### Contexto
- Al abrir la app **sin estar autenticado**, se entra directamente a la pantalla
  de Registro (el destino inicial se decide según el estado de sesión:
  `Autenticado → Home`, en otro caso → `Registro`, con un spinner de carga
  mientras la sesión todavía está inicializando).
- La pantalla está construida con el sistema de diseño **KomoUI**.

### Criterios de Aceptación
- [ ] El formulario toma **email, contraseña, nombre, apellido** y al menos
      **un establecimiento**.
- [ ] El email usa teclado de tipo email; la contraseña se muestra oculta.
- [ ] El botón de **enviar está deshabilitado** hasta que todos los campos estén
      completos y haya al menos un establecimiento seleccionado.
- [ ] Al enviar se hace `POST /api/v1/auth/register/establishment-manager` con
      `establishments` como arreglo de ids y `last_name` en snake_case.
- [ ] Mientras la petición está en curso, el botón de enviar muestra un estado
      de **carga**.
- [ ] Ante un `201`, la cuenta se crea como `pending_validation` y se lleva al
      usuario a una **pantalla de confirmación** que explica que la cuenta está
      pendiente de aprobación — todavía no puede iniciar sesión.
- [ ] Después del éxito, el **botón atrás no debe volver al formulario cargado**
      (el formulario se saca del back stack).
- [ ] Cuando un campo inferior enfocado quedaría tapado por el teclado, el
      formulario lo desplaza para que quede visible (patrón `ScrollState` de
      CMP), tanto en Android como en iOS.

### Manejo de Errores
- [ ] **409 (el usuario ya existe)** → mensaje claro de "el usuario ya existe".
- [ ] **404 (establecimiento no encontrado)** → mensaje claro de establecimiento
      no encontrado.
- [ ] **400 (datos inválidos)** → validación inline.
- [ ] **Errores de red / desconocidos** → mensaje claro inline; los errores
      nunca se descartan en silencio.

---

## Historia 2 — Elegir establecimientos desde una pantalla dedicada con búsqueda

> **Como** futuro encargado de establecimiento
> **quiero** una lista de establecimientos a pantalla completa y con búsqueda en
> lugar de un desplegable apretado
> **para** poder encontrar y seleccionar los establecimientos correctos
> fácilmente, incluso cuando hay muchos.

### Contexto
- El desplegable multi-selección inline se reemplaza por una pantalla dedicada
  **`SelectEstablishment`**.
- Se mantiene la multi-selección; la selección se lleva al selector y se trae de
  vuelta usando el patrón de resultado de navegación `savedStateHandle`.

### Criterios de Aceptación
- [ ] En la pantalla de registro, el campo "Establecimientos" es un **disparador
      de solo lectura** que muestra la selección actual (cantidad/nombres) o
      "Seleccionar establecimiento" cuando está vacío.
- [ ] Al tocar el campo se navega al selector, **pre-marcando** los
      establecimientos ya seleccionados.
- [ ] El selector muestra una **barra de búsqueda fija arriba** y una
      `LazyColumn` de **tarjetas de establecimiento a todo el ancho** (nombre +
      dirección) debajo.
- [ ] Al escribir se aplica un **debounce (~300 ms)** y se vuelve a pedir el
      endpoint de establecimientos con el parámetro `search`; las peticiones en
      curso se cancelan ante cada nueva tecla.
- [ ] Tocar una tarjeta **alterna** su selección; las selecciones **persisten**
      aunque la tarjeta quede filtrada por una búsqueda posterior.
- [ ] Una acción de confirmación **"Listo"** devuelve los establecimientos
      seleccionados a la pantalla de registro y vuelve atrás; el campo de
      registro se actualiza con los nombres seleccionados.
- [ ] Volver atrás **sin confirmar** deja la selección del registro sin cambios.
- [ ] El **error de validación** del establecimiento se limpia una vez hecha una
      selección.
- [ ] Al enviar el registro se mandan los **ids de establecimiento correctos**
      sin alterar.

### Estados
- [ ] **Cargando** → spinner.
- [ ] **Error de carga** → mensaje + reintentar.
- [ ] **Sin resultados** → "Sin resultados".
- [ ] **Resultados** → lista de tarjetas seleccionables.

---

## Notas / Fuera de Alcance
- No hay login ni autenticación después del registro (la cuenta queda
  pendiente).
- Solo el rol `establishment_manager` puede autorregistrarse (no `professional`
  ni `admin`).
- No hay paginación / scroll infinito — se trae una sola página
  (`page_size = 100`); la búsqueda acota los resultados del lado del servidor una
  vez que el backend lo soporte.
- El parámetro `search` ya se envía pero el backend lo ignora hasta que se
  implemente (devuelve la lista completa, así que la pantalla igual funciona).
- `ApiConfig.BASE_URL` sigue siendo el host placeholder — el registro no va a
  pegarle a un servidor real hasta que se configure.

## Planes de Origen
- `thoughts/plans/establishment_manager_registration.md`
- `thoughts/plans/select_establishment_search_screen.md`
