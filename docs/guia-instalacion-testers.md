# Cómo instalar la app de prueba (Ingeniería en Alimentación)

Esta guía es para las personas que van a probar la app antes de que esté disponible en Google Play. Como todavía no está publicada en la tienda, la instalación se hace por **Firebase App Distribution**, una herramienta oficial de Google para distribuir versiones de prueba. El celular puede pedirte permiso para instalar desde el navegador. Descargá únicamente la versión indicada por el equipo responsable.

Para administrar las cuentas, continuar la distribución o evaluar la publicación en las tiendas, consultá el [documento de traspaso](traspaso-app-mobile.md).

Vas a necesitar:
- Un celular Android.
- Una cuenta de Google (Gmail). Puede ser cualquiera, no hace falta que sea la misma a la que te invitamos.
- Conexión a internet.

---

## Paso 1 — Abrí el mail de invitación

Te va a llegar un mail de **Firebase App Distribution** (a veces el remitente aparece como `firebase-app-distribution` o similar) avisando que fuiste invitado a probar la app.

1. Abrí ese mail **desde el celular** (no desde la compu, porque después vas a instalar la app en el teléfono).
2. Tocá el botón **"Get started"** o **"Comenzar"**.

> **¿No te llegó el mail?** Revisá la carpeta de spam/correo no deseado. Si tampoco está ahí, avisanos para reenviarte la invitación.

## Paso 2 — Iniciá sesión con Google

Se va a abrir una página en el navegador pidiendo que inicies sesión.

1. Tocá **"Sign in with Google"** (Iniciar sesión con Google).
2. Elegí tu cuenta de Gmail (o agregá una si no tenés ninguna cargada en el teléfono).

## Paso 3 — Aceptá la invitación

1. Vas a ver una casilla de consentimiento (checkbox) — marcala.
2. Tocá **"Accept invitation"** (Aceptar invitación).

Con esto ya te dieron acceso para instalar la app. Vas a llegar a una pantalla que lista las apps disponibles para vos — ahí debería aparecer **"Ingeniería en Alimentación"**.

## Paso 4 — Descargá la app

1. Tocá la app en la lista.
2. Tocá **"Download"** (Descargar) en la versión disponible (por ejemplo, `0.1.0`).

Acá es donde el celular puede empezar a mostrarte avisos de seguridad. Son esperables — están explicados abajo.

---

## Los avisos que te puede mostrar el celular (y qué hacer)

### ⚠️ "Por seguridad, tu teléfono no tiene permitido instalar aplicaciones desconocidas de esta fuente"

Esto pasa porque estás instalando algo fuera de Google Play. Es la primera vez que hacés esto con este navegador, así que Android pide tu confirmación explícita.

1. Tocá **"Configuración"** (o "Ajustes") en el mismo aviso — te lleva directo a la pantalla correcta.
2. Vas a ver una opción tipo **"Permitir desde esta fuente"** (el nombre exacto varía: puede decir "Chrome", "Instalar apps desconocidas", etc., según el navegador que usaste).
3. Activá el interruptor/switch.
4. Volvé atrás (botón de retroceder) y probá instalar de nuevo.

> Esta configuración se hace **una sola vez** para esa app instaladora (por ejemplo Chrome). Las próximas actualizaciones no te la van a volver a pedir.

### 📥 Aviso para descargar "Firebase App Tester"

En algún momento del proceso puede aparecer un cartel ofreciendo descargar una app llamada **"Firebase App Tester"**, con el botón **"DOWNLOAD"**.

- Es una app auxiliar de Google que te va a servir después, para recibir las **próximas actualizaciones** de la app sin tener que buscar el mail de invitación cada vez.
- Se puede instalar en ese momento o más adelante — no es obligatoria para instalar la primera versión, pero **te recomendamos instalarla** para que actualizar sea más fácil en el futuro.

### 🛡️ "Google Play Protect" escaneando o advirtiendo

Antes de instalar, el teléfono puede tardar unos segundos "escaneando la app" (Play Protect). Es automático, no tenés que hacer nada, solo esperar.

Si Play Protect ofrece analizar una app desconocida, dejá que termine y revisá el resultado. Si advierte que la app es potencialmente dañina o bloquea la instalación, detené el proceso y consultá al equipo responsable indicando el mensaje y la versión descargada. Esa advertencia no debe atribuirse automáticamente a que la app se distribuye fuera de la tienda.

Referencia: [cómo funciona Google Play Protect](https://support.google.com/googleplay/answer/2812853?hl=es).

## Paso 5 — Instalá y abrí la app

1. Tocá **"Instalar"**.
2. Cuando termine, tocá **"Abrir"**.

Listo — ya tenés la app funcionando en el celular.

---

## Cómo actualizar cuando haya una versión nueva

Cuando subamos una versión nueva de la app, tenés dos formas de enterarte e instalarla:

- **Si instalaste "Firebase App Tester"**: abrí esa app, vas a ver la nueva versión disponible ahí, tocá **Download** e instalá (mismos pasos de más arriba, ya no debería pedirte permisos de nuevo).
- **Si no la instalaste**: te va a llegar un mail nuevo avisando que hay una versión para probar — repetís los pasos 1 a 5 de esta guía.

Actualizá sobre la app existente. Si te pide desinstalarla primero, consultá al equipo responsable y sincronizá los datos pendientes antes de hacerlo.

---

## Preguntas frecuentes

**¿Es seguro instalar esto si no viene de Google Play?**
Firebase App Distribution es una herramienta oficial de Google para distribuir pruebas, pero el canal de descarga por sí solo no garantiza la seguridad del archivo. Usá la invitación y versión confirmadas por el equipo responsable y seguí las indicaciones de Play Protect explicadas arriba.

**Cambié de celular / perdí el acceso, ¿qué hago?**
Avisanos y te reenviamos la invitación a tu mail.

**No encuentro la opción "Instalar apps desconocidas" en Configuración.**
Varía un poco según la marca del celular (Samsung, Xiaomi, Motorola, etc.). Buscá en el buscador de Configuración la palabra "desconocidas" o "unknown apps" y debería aparecerte directo.
