# Historia de Usuario — Cargar Sujeto

## Épica

**Carga de sujetos por el encargado de establecimiento** — Permitir que un
encargado de establecimiento ya autenticado dé de alta un nuevo sujeto desde la
app mobile, con sus datos personales y demográficos, para que queden registrados
en el sistema.

---

## Historia 1 — Cargar un sujeto desde la app

> **Como** encargado de establecimiento autenticado
> **quiero** cargar un sujeto con su DNI, sexo, nombre, apellido, fecha de
> nacimiento y condición rural/indígena
> **para** registrarlo en el sistema sin tener que crearlo a mano desde un panel
> externo.

### Contexto
- La pantalla solo es alcanzable estando **autenticado**; hoy se entra desde un
  botón **"Cargar sujeto"** en la pantalla de Home (entrada provisoria para poder
  probar el flujo manualmente).
- La pantalla está construida con el sistema de diseño **KomoUI** (`Input`,
  `Button`) más `Switch` de Material3 para los toggles y un selector segmentado
  propio para el sexo.
- El endpoint `POST /api/v1/subjects` está protegido por token y es **accesible
  solo para encargados de establecimiento**; la llamada usa el cliente HTTP
  autenticado por defecto.

### Criterios de Aceptación
- [ ] El formulario toma **DNI, sexo, nombre, apellido, fecha de nacimiento**,
      y dos condiciones booleanas: **población rural** y **población indígena**.
- [ ] El **DNI** solo acepta dígitos (los no-numéricos se descartan al tipear) y
      usa teclado numérico.
- [ ] El **sexo** se elige con un selector de dos opciones: **Masculino** /
      **Femenino**.
- [ ] La **fecha de nacimiento** se ingresa como `DD/MM/AAAA`: los `/` se
      insertan automáticamente mientras se tipea y usa teclado numérico.
- [ ] **Rural** e **indígena** son interruptores (`Switch`) con valor por defecto
      en apagado.
- [ ] El botón de **guardar está deshabilitado** hasta que DNI, sexo, nombre,
      apellido y fecha de nacimiento estén completos (los dos switches no son
      obligatorios).
- [ ] La **validación es local antes de la llamada de red**:
  - DNI parsea a entero en el rango **1 a 99.999.999** (1 a 8 dígitos).
  - Nombre y apellido: **1 a 50 caracteres** (recortando espacios).
  - Fecha con formato `DD/MM/AAAA`, que sea una fecha válida y **no futura**.
- [ ] Al enviar se hace `POST /api/v1/subjects` con `last_name`, `birth_date`
      (ISO `AAAA-MM-DD`), `is_rural` e `is_indigenous` en snake_case, y `sex` como
      `male` / `female`.
- [ ] Mientras la petición está en curso, el botón de guardar muestra un estado
      de **carga**.
- [ ] Ante un `201`, se muestra un **banner de éxito** arriba con el nombre del
      sujeto cargado, el **formulario se limpia por completo** y el usuario
      **permanece en la pantalla** para cargar otro sujeto.
- [ ] El banner de éxito se puede **descartar** con la "×", y también desaparece
      solo apenas se modifica cualquier campo.
- [ ] El **botón atrás** (gesto/sistema) vuelve a la pantalla anterior (Home).

### Manejo de Errores
- [ ] **400 (datos inválidos)** → "Los datos ingresados no son válidos. Revisá el
      formulario."; el formulario se conserva.
- [ ] **401 (token faltante o inválido)** → "Tu sesión expiró. Iniciá sesión
      nuevamente."
- [ ] **403 (sin permisos)** → "No tenés permisos para cargar sujetos." (la spec
      no lo enumera, pero el rol lo implica; se maneja defensivamente).
- [ ] **Errores de red** → "No pudimos conectarnos. Verificá tu conexión e
      intentá nuevamente."
- [ ] **Errores desconocidos** → "Ocurrió un error inesperado. Intentá
      nuevamente."
- [ ] En cualquier error, el formulario **no se limpia** y los errores **nunca se
      descartan en silencio**.

### Estados
- [ ] **Editando** → formulario con validación inline por campo.
- [ ] **Enviando** → botón en estado de carga, deshabilitado.
- [ ] **Éxito** → banner de confirmación + formulario limpio.
- [ ] **Error** → mensaje inline; formulario preservado.

### Comportamiento del teclado (CMP)
- [ ] Cuando un campo inferior enfocado quedaría tapado por el teclado, el
      formulario lo desplaza para que quede visible (patrón `ScrollState` de CMP),
      tanto en Android como en iOS.

---

## Notas / Fuera de Alcance
- **No hay date picker nativo** — la fecha se ingresa como texto `DD/MM/AAAA` y se
  parsea a `LocalDate`.
- No hay pantallas de **listado / detalle / edición / borrado** de sujetos.
- No hay **cola offline**, persistencia de borrador, política de reintentos ni UI
  optimista.
- El **gating de rol** no se hace en la capa de navegación: se deja que el backend
  rechace con `403` y se muestra un mensaje amigable.
- La **entrada desde Home** es provisoria (un botón para poder probar el flujo);
  un Home consciente del rol queda fuera de alcance.
- `ApiConfig.BASE_URL` sigue siendo el host placeholder hasta que se configure un
  servidor real.

## Planes de Origen
- `thoughts/plans/create_subject_screen.md`
