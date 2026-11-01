# Ingeniería en Alimentación · App móvil

[![en](https://img.shields.io/badge/lang-en-red.svg)](README.md)
[![Kotlin](https://img.shields.io/badge/kotlin-2.3.20-7F52FF.svg)](gradle/libs.versions.toml)
[![Compose Multiplatform](https://img.shields.io/badge/compose%20multiplatform-1.10.3-4285F4.svg)](gradle/libs.versions.toml)

App Android e iOS del proyecto **Ingeniería para la Alimentación**, desarrollada con Kotlin y Compose Multiplatform. Permite registrar sujetos, mediciones y encuestas, gestionar alimentos y consultar recetas. Los sujetos, las mediciones y las respuestas de encuestas se guardan localmente y se sincronizan con el backend.

---

## Documentación

- [Manual de usuario en línea](https://ingenieriaparalaalimentacion.github.io/app_mobile/), con búsqueda, navegación por capítulos y descarga del PDF. También está disponible en el repositorio en [HTML](docs/manual-usuario/index.html) y [PDF](docs/manual-usuario/manual.pdf). Los pasos y las capturas corresponden a Android.
- [Instalar y actualizar la app de prueba](docs/guia-instalacion-testers.md).
- [Compilar y ejecutar iOS en un simulador](docs/ios-local-development.md) (en inglés).
- [Configurar notificaciones iOS](docs/ios-push-notifications.md) (en inglés).
- [Traspaso y distribución](docs/traspaso-app-mobile.md). La última versión está en un [Google Doc](https://docs.google.com/document/d/1me0E1EmsKoR4xRyncyNFgfimNXf9t_r8/edit?usp=sharing) (requiere cuenta `@fi.uba.ar`).
- [Funcionamiento técnico de recetas](docs/recipe-recommendations.md) (en inglés).
- [Convenciones de desarrollo](claude.md).

---

## Requisitos previos

| Herramienta | Versión mínima | Notas |
|-------------|----------------|-------|
| JDK | 21 | Para ejecutar Gradle |
| Android SDK | API 36 | La app requiere Android 8.0 (API 26) o posterior |
| Android Studio | Con soporte para Kotlin Multiplatform | Para desarrollar Android desde el IDE |
| Xcode | Con un runtime de iOS Simulator | Solo para iOS; requiere macOS |

El proyecto incluye destinos para dispositivos y simuladores Apple Silicon (`iosArm64` e `iosSimulatorArm64`). Consultá el [alcance de la validación iOS](docs/ios-local-development.md#validation-performed).

---

## Instalación

```bash
# 1. Clonar el repositorio
git clone https://github.com/IngenieriaParaLaAlimentacion/app_mobile.git
cd app_mobile

# 2. Crear la configuración local
cp local.properties.example local.properties

# 3. Seleccionar JDK 21 (macOS)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## Configuración

Completá `local.properties`, que está ignorado por Git:

| Clave | Obligatoria | Descripción |
|-------|-------------|-------------|
| `sdk.dir` | Sí* | Ruta del Android SDK. *Alternativa: definir `ANDROID_HOME` |
| `api_url_tst` | Sí, para `tst` | URL del backend de prueba, incluida la barra final |
| `api_url_prod` | No | URL del backend de producción. Si está vacía, usa la definida en `composeApp/build.gradle.kts` |
| `web_host_tst`, `web_host_prod` | No | Hosts de los enlaces de verificación de email |

Para notificaciones Android, agregá el `google-services.json` del entorno en `composeApp/src/tst/` o `composeApp/src/prod/`. Las compilaciones Android que procesan Google Services requieren una configuración válida para el paquete elegido. La [guía de iOS](docs/ios-local-development.md) explica la configuración de Firebase para esa plataforma. La firma y distribución Android usan los archivos locales descritos en el [documento de traspaso](docs/traspaso-app-mobile.md).

---

## Ejecución

### Android

Las variantes `tst` y `prod` usan paquetes distintos y pueden coexistir sin compartir sesión ni datos locales.

Ejecutá cada comando por separado desde la raíz del repositorio:

```bash
# Comprobación rápida de tipos (commonMain)
./scripts/compile.sh

# APK de depuración
./gradlew :composeApp:assembleTstDebug --no-daemon
./gradlew :composeApp:assembleProdDebug --no-daemon
```

`scripts/compile.sh` selecciona JDK 21 en macOS y compila los metadatos de `commonMain`. Esa comprobación no reemplaza una compilación Android o iOS. Los APK quedan en `composeApp/build/outputs/apk/<entorno>/debug/`.

> Compilá un entorno por invocación de Gradle. BuildKonfig genera una sola configuración compartida y rechaza la selección implícita de ambos entornos. La selección sigue este orden: `-PtargetEnvironment=tst|prod`, entorno inferido del nombre de la tarea y, finalmente, `tst`.

### iOS

Abrí `iosApp/iosApp.xcodeproj`, seleccioná `iosApp-tst` o `iosApp-prod` y ejecutá sobre un iPhone Simulator. Los esquemas seleccionan explícitamente el backend; `tst` requiere `api_url_tst` en `local.properties`.

El simulador no requiere una cuenta Apple. La [guía de desarrollo local iOS](docs/ios-local-development.md) incluye los comandos de compilación sin firma y las limitaciones de las pruebas realizadas.

---

## Tests

Con JDK 21 y Android SDK configurados:

```bash
./gradlew :composeApp:testProdDebugUnitTest -PtargetEnvironment=prod --no-daemon
```

El workflow [CI](.github/workflows/ci.yml) ejecuta `commonTest` y `androidUnitTest` sobre el destino Android JVM en pull requests y pushes a `develop` y `main`. También admite ejecución manual y conserva los informes HTML y JUnit XML, incluso si fallan las pruebas. No necesita credenciales del backend, firma de distribución ni emulador.

Esta suite no ejecuta pruebas de interfaz ni pruebas en el simulador iOS.

---

## Estructura del código

La app usa Clean Architecture, MVVM, Koin para inyección de dependencias y Navigation Compose para navegación.

El código compartido está en `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/`:

| Carpeta | Contenido |
|---------|-----------|
| `app/` | `App`, `AppNavHost`, `AppViewModel` y `AuthedShell`, con la navegación de las pestañas autenticadas |
| `core/design_system/` | Tema, tipografía y componentes reutilizables |
| `core/validation/` | Reglas de validación de formularios |
| `data/` | Clientes HTTP, DTO, mappers, repositorios, almacenamiento local y sincronización. Los esquemas SQLDelight viven en `composeApp/src/commonMain/sqldelight/` |
| `domain/` | Modelos, interfaces de repositorio, casos de uso y contratos de sincronización |
| `di/` | Módulos de Koin, con implementaciones de plataforma mediante `expect` y `actual` |
| `presentation/feature/` | Pantallas agrupadas por funcionalidad, con acciones, destinos, navegación, estado y view models |
| `presentation/navigation/` | Rutas tipadas, enlaces externos y resultados de navegación |

`composeApp/src/androidMain/` e `iosMain/` contienen las implementaciones específicas de cada plataforma. `iosApp/` contiene la aplicación Swift y su proyecto Xcode.

Para agregar una pantalla, seguí una funcionalidad similar, declarala en `presentation/navigation/Route.kt` y registrala en `app/AppNavHost.kt`. Las pestañas Inicio, Historial y Perfil se registran en `app/AuthedShell.kt`. Registrá sus dependencias en `di/`.

---

## Contribuir

Leé la [guía de contribución](CONTRIBUTING.md) (en inglés) antes de abrir un PR: ramas, commits y revisión.

---

## Licencia

Proyecto académico — **grupo-193**.
