# Ingeniería en Alimentación · App móvil

App Android e iOS desarrollada con Kotlin y Compose Multiplatform. Permite registrar sujetos, mediciones y encuestas, gestionar alimentos y consultar recetas. Los sujetos, las mediciones y las respuestas de encuestas se guardan localmente y se sincronizan con el backend.

## Documentación

- [Manual de usuario](docs/manual-usuario/README.md), disponible en [HTML](docs/manual-usuario/index.html) y [PDF](docs/manual-usuario/manual.pdf). Los pasos y las capturas corresponden a Android.
- [Instalar y actualizar la app de prueba](docs/guia-instalacion-testers.md).
- [Compilar y ejecutar iOS en un simulador](docs/ios-desarrollo-local.md).
- [Configurar notificaciones iOS](docs/push-notifications-ios.md).
- [Traspaso y distribución](docs/traspaso-app-mobile.md).
- [Funcionamiento técnico de recetas](docs/recipe-recommendations.md).
- [Convenciones de desarrollo](claude.md).

`thoughts/` conserva investigaciones, planes y decisiones de diseño. Los planes describen el contexto de su elaboración y pueden incluir tareas ya implementadas. Para compilar y operar la app, usá las guías anteriores.

## Requisitos

- JDK 21 para ejecutar Gradle.
- Android SDK con API 36. La app requiere Android 8.0 (API 26) o posterior.
- Android Studio con soporte para Kotlin Multiplatform para desarrollar Android desde el IDE.
- Para iOS: Mac con Xcode y un runtime de iOS Simulator. El proyecto incluye destinos para dispositivos y simuladores Apple Silicon (`iosArm64` e `iosSimulatorArm64`). Consultá el [alcance de la validación iOS](docs/ios-desarrollo-local.md#validación-realizada-el-28-de-octubre-de-2026).

## Configurar el proyecto

1. Copiá `local.properties.example` a `local.properties` si todavía no existe.
2. Completá `sdk.dir` con la ruta de tu Android SDK, o definí `ANDROID_HOME`.
3. Para el entorno de prueba, completá `api_url_tst` con la URL del backend, incluida la barra final.
4. Seleccioná JDK 21 antes de ejecutar Gradle. En macOS:

	```bash
	export JAVA_HOME=$(/usr/libexec/java_home -v 21)
	```

`local.properties` está ignorado por Git. `api_url_prod` es opcional y usa la URL de producción definida en `composeApp/build.gradle.kts` cuando está vacío. `web_host_tst` y `web_host_prod` permiten cambiar los hosts de los enlaces de verificación de email.

Para notificaciones Android, agregá el `google-services.json` del entorno en `composeApp/src/tst/` o `composeApp/src/prod/`. Las compilaciones Android que procesan Google Services requieren una configuración válida para el paquete elegido. La [guía de iOS](docs/ios-desarrollo-local.md) explica la configuración de Firebase para esa plataforma. La firma y distribución Android usan los archivos locales descritos en el [documento de traspaso](docs/traspaso-app-mobile.md).

## Compilar Android

Las variantes `tst` y `prod` usan paquetes distintos y pueden coexistir sin compartir sesión ni datos locales.

Ejecutá cada comando por separado desde la raíz del repositorio:

```bash
./scripts/compile.sh
./gradlew :composeApp:assembleTstDebug --no-daemon
./gradlew :composeApp:assembleProdDebug --no-daemon
```

`scripts/compile.sh` selecciona JDK 21 en macOS y compila los metadatos de `commonMain`. Esa comprobación no reemplaza una compilación Android o iOS. Los APK de depuración quedan en `composeApp/build/outputs/apk/<entorno>/debug/`.

Compilá un entorno por invocación de Gradle. BuildKonfig genera una sola configuración compartida y rechaza la selección implícita de ambos entornos en la misma invocación. La selección sigue este orden: `-PtargetEnvironment=tst|prod`, entorno inferido del nombre de la tarea y, finalmente, `tst`.

## Ejecutar iOS

Abrí `iosApp/iosApp.xcodeproj`, seleccioná `iosApp-tst` o `iosApp-prod` y ejecutá sobre un iPhone Simulator. Los esquemas seleccionan explícitamente el backend; `tst` requiere `api_url_tst` en `local.properties`.

El simulador no requiere una cuenta Apple. La [guía de desarrollo local iOS](docs/ios-desarrollo-local.md) incluye los comandos de compilación sin firma y las limitaciones de las pruebas realizadas.

## Ejecutar las pruebas

Con JDK 21 y Android SDK configurados:

```bash
./gradlew :composeApp:testProdDebugUnitTest -PtargetEnvironment=prod --no-daemon
```

El workflow [CI](.github/workflows/ci.yml) ejecuta `commonTest` y `androidUnitTest` sobre el destino Android JVM en pull requests y pushes a `develop` y `main`. También admite ejecución manual y conserva los informes HTML y JUnit XML, incluso si fallan las pruebas. No necesita credenciales del backend, firma de distribución ni emulador.

Esta suite no ejecuta pruebas de interfaz ni pruebas en el simulador iOS.

## Estructura del código

La app usa Clean Architecture, MVVM, Koin para inyección de dependencias y Navigation Compose para navegación.

El código compartido está en `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/`:

- `app/`: `App`, `AppNavHost`, `AppViewModel` y `AuthedShell`, con la navegación de las pestañas autenticadas.
- `core/design_system/`: tema, tipografía y componentes reutilizables. `core/validation/` contiene las reglas de formularios.
- `data/`: clientes HTTP, DTO, mappers, repositorios, almacenamiento local y sincronización. Los esquemas SQLDelight viven en `composeApp/src/commonMain/sqldelight/`.
- `domain/`: modelos, interfaces de repositorio, casos de uso y contratos de sincronización.
- `di/`: módulos de Koin, con implementaciones de plataforma mediante `expect` y `actual`.
- `presentation/feature/`: pantallas agrupadas por funcionalidad, con acciones, destinos, navegación, estado y view models.
- `presentation/navigation/`: rutas tipadas, enlaces externos y resultados de navegación.

`composeApp/src/androidMain/` e `iosMain/` contienen las implementaciones específicas de cada plataforma. `iosApp/` contiene la aplicación Swift y su proyecto Xcode.

Para agregar una pantalla, seguí una funcionalidad similar, declarala en `presentation/navigation/Route.kt` y registrala en `app/AppNavHost.kt`. Las pestañas Inicio, Historial y Perfil se registran en `app/AuthedShell.kt`. Registrá sus dependencias en `di/`.
