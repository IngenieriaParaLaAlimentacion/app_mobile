# Compilar y ejecutar iOS sin una cuenta Apple

El proyecto de Xcode está en `iosApp/iosApp.xcodeproj`. El simulador permite compilar, instalar y ejecutar la app **sin iniciar sesión en Apple y sin membresía paga**. Esta validación local no habilita la distribución a usuarios.

## Preparación

- Mac con Xcode y un runtime de iOS Simulator instalado desde los ajustes de Xcode.
- Xcode seleccionado para las herramientas de línea de comandos: comprobar `xcode-select -p` y `xcodebuild -version`.
- JDK 21. La fase de compilación de Kotlin lo selecciona mediante `/usr/libexec/java_home -v 21`.
- Android SDK, requerido para configurar el módulo Gradle compartido que también contiene Android. Indicar su ubicación mediante `sdk.dir` en `local.properties` o `ANDROID_HOME` en el entorno.
- Android `tst` e iOS `iosApp-tst` comparten **la misma propiedad `api_url_tst` en `local.properties`**. Si ya hay una configuración Android en otro checkout, reutilizar ese valor en el workspace nuevo. Si no existe, copiar `local.properties.example` y completarla. No reemplazar un archivo local ya configurado.

No se necesitan CocoaPods, XcodeGen ni herramientas de generación para abrir o compilar el proyecto entregado.

El proyecto resuelve FirebaseCore y FirebaseMessaging mediante Swift Package Manager para integrar las notificaciones existentes en `develop`. La primera apertura también descarga esas dependencias.

## Configuración de Firebase para notificaciones

Cada entorno utiliza su propio archivo, descargado al registrar el Bundle ID correspondiente en Firebase:

- `iosApp/Configuration/tst/GoogleService-Info.plist`
- `iosApp/Configuration/prod/GoogleService-Info.plist`

La fase de build copia el archivo del entorno al bundle y comprueba que su `BUNDLE_ID` coincida con el de la app. Estos archivos están ignorados por Git.

Una build Debug puede ejecutarse sin ese archivo, con notificaciones push deshabilitadas. Las operaciones de registro de token devuelven un error de configuración y la salida de sesión puede completarse sin Firebase. Una build Release requiere el archivo del entorno: si falta o pertenece a otro Bundle ID, la compilación se detiene. Para recibir notificaciones también hace falta completar la configuración de APNs, firma y capacidades del equipo Apple; esa validación queda pendiente. [Configuración oficial de Firebase para Apple](https://firebase.google.com/docs/ios/setup).

## Elegir el entorno

| Esquema de Xcode | Ejecución local | Archivo de distribución | Bundle ID |
| --- | --- | --- | --- |
| `iosApp-tst` | `DebugTst` | `ReleaseTst` | `ar.com.ingenieriaalimentacion.tst` |
| `iosApp-prod` | `DebugProd` | `ReleaseProd` | `ar.com.ingenieriaalimentacion` |

Las configuraciones viven en `iosApp/Configuration/`. La fase de build ejecuta `scripts/build-ios-framework.sh` y pasa a Gradle el `-PtargetEnvironment` correspondiente. `KOTLIN_FRAMEWORK_BUILD_TYPE` mapea las configuraciones a Debug/Release. Compilar los entornos secuencialmente, porque ambos generan la misma configuración Kotlin compartida.

`tst` requiere una URL explícita en `local.properties`. `prod` usa `api_url_prod` si está configurada y, de lo contrario, el valor predeterminado de producción definido en Gradle. Elegir `prod` implica utilizar ese backend al realizar acciones de la app.

## Ejecutar desde Xcode

1. Abrir `iosApp/iosApp.xcodeproj`.
2. Seleccionar `iosApp-tst` o `iosApp-prod` y un **iPhone Simulator** como destino.
3. Ejecutar Product → Run. Para un simulador no hace falta seleccionar un equipo de firma ni agregar una cuenta Apple.

La primera compilación descarga dependencias y genera el framework Kotlin; puede demorar más que las siguientes. La configuración desactiva User Script Sandboxing para permitir la integración Gradle documentada por Kotlin. [Integración directa con Xcode](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html).

## Compilar desde terminal sin firma

Desde la raíz del repositorio:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp-tst \
  -configuration DebugTst \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .context/ios/DerivedData \
  CODE_SIGNING_ALLOWED=NO build
```

Para producción, cambiar el esquema por `iosApp-prod` y la configuración por `DebugProd`. El `.app` resultante queda dentro de `Build/Products/<configuración>-iphonesimulator/` bajo la carpeta de Derived Data elegida. Es una app de simulador; no es un IPA instalable en un iPhone físico.

Para instalar y abrir en un simulador encendido, luego de compilar producción:

```bash
xcrun simctl list devices available
xcrun simctl install booted \
  .context/ios/DerivedData/Build/Products/DebugProd-iphonesimulator/IngenieriaAlimentacion.app
xcrun simctl launch booted ar.com.ingenieriaalimentacion
```

Si hay más de un simulador encendido, reemplazar `booted` por el identificador del dispositivo elegido. Para `tst`, utilizar `DebugTst-iphonesimulator` y el Bundle ID con sufijo `.tst`.

## Qué requiere una cuenta después

Para probar en un iPhone físico se debe configurar firma y un equipo Apple; un Personal Team gratuito tiene restricciones y perfiles de siete días. TestFlight, App Store y Ad Hoc requieren la membresía correspondiente o una exención aprobada. [Cuenta Apple y Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account).

La preparación para distribución y las pruebas funcionales de aceptación están detalladas en el [documento de traspaso](traspaso-app-mobile.md). La primera ejecución en simulador no reemplaza las pruebas de login, almacenamiento, modo offline, sincronización, enlaces externos y dispositivo físico.

## Validación realizada el 28 de octubre de 2026

- Xcode 26.3, SDK iPhoneSimulator 26.2, simulador iPhone 17 Pro con iOS 26.3.1, arquitectura arm64.
- Builds `DebugProd` y `DebugTst` con `CODE_SIGNING_ALLOWED=NO`: `BUILD SUCCEEDED` en ambos casos.
- Instalación con `simctl install` y ejecución con `simctl launch`: la app permaneció abierta y mostró la bienvenida con las opciones Crear cuenta e Iniciar sesión.
- Se validaron la compilación Kotlin/Swift, el enlace del framework, los recursos Compose y el arranque de la interfaz en ambos entornos. No se inició sesión ni se cargaron datos.
- La URL de `tst` se obtuvo del `local.properties` del checkout principal de Android y se configuró en el archivo local de este workspace, ignorado por Git. Se verificaron `APP_ENVIRONMENT=tst`, el Bundle ID terminado en `.tst` y la URL de test dentro del framework compilado.
- Los esquemas Release y la distribución firmada todavía no se validaron; las pruebas autenticadas y de sincronización también siguen pendientes.

Ambas builds también se compilaron sobre `develop`, con las dependencias de Firebase y las fuentes de notificaciones incluidas en el proyecto, sin `GoogleService-Info.plist`. Los registros de esa verificación están en `.context/ios/build-tst-develop.log` y `.context/ios/build-prod-develop.log`. Se comprobó además el rechazo de una configuración Firebase ausente en Release o con Bundle ID incorrecto, la copia de una configuración válida y la eliminación de una configuración anterior en Debug. Estas comprobaciones no acreditan recepción de notificaciones push.

Las capturas están en `.context/ios/primera-ejecucion.png` (producción) y `.context/ios/ejecucion-tst.png` (test); los registros, en `.context/ios/build-prod.log` y `.context/ios/build-tst.log`. `.context/` no forma parte del repositorio entregado.

El proyecto declara iOS 15.0 como deployment target, pero el linker informó que un objeto ICU de una dependencia fue compilado para iOS Simulator 17.2. Antes de fijar la versión mínima soportada para distribución, comprobar esa dependencia y probar las versiones antiguas elegidas. Esta ejecución solo acredita el runtime 26.3.1 indicado arriba.
