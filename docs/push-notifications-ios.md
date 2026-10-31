# Configurar notificaciones push en iOS

El proyecto `iosApp/iosApp.xcodeproj` ya incluye FirebaseCore y FirebaseMessaging mediante Swift Package Manager. La integración Swift está conectada con el código Kotlin compartido. Para recibir notificaciones, falta configurar Firebase y APNs para cada entorno, habilitar las capacidades de firma y validar en un iPhone físico.

## Integración incluida

- `iosApp/iosApp/AppDelegate.swift` configura Firebase cuando encuentra el archivo de configuración, instala `IOSFirebaseMessagingBridge`, inicializa Koin y configura `PushNotificationDelegate`. También reenvía el token APNs a Firebase Messaging.
- `iosApp/iosApp/Notifications/IOSFirebaseMessagingBridge.swift` conecta la obtención y eliminación de tokens con Kotlin.
- `iosApp/iosApp/Notifications/PushNotificationDelegate.swift` solicita permiso, registra el dispositivo, reenvía los tokens FCM a Kotlin y muestra banners en primer plano. Al tocar una notificación `stale_establishment`, transmite el establecimiento y los días sin actualizar al código compartido.
- `scripts/copy-ios-firebase-config.sh` copia al bundle el archivo Firebase del entorno seleccionado y comprueba su Bundle ID.

## Configurar Firebase por entorno

1. Registrá cada app iOS en el proyecto Firebase correspondiente:

	| Esquema | Bundle ID | Archivo local |
	| --- | --- | --- |
	| `iosApp-tst` | `ar.com.ingenieriaalimentacion.tst` | `iosApp/Configuration/tst/GoogleService-Info.plist` |
	| `iosApp-prod` | `ar.com.ingenieriaalimentacion` | `iosApp/Configuration/prod/GoogleService-Info.plist` |

2. Descargá cada `GoogleService-Info.plist` y guardalo en la ruta indicada. Los archivos están ignorados por Git.
3. Seleccioná el esquema del entorno al compilar. Las configuraciones `Tst.xcconfig` y `Prod.xcconfig` definen `APP_ENVIRONMENT` y el Bundle ID.

Una compilación Debug puede ejecutarse sin el archivo, con push deshabilitado. Una compilación Release falla si falta. Un archivo con Bundle ID incorrecto detiene la compilación en ambos casos. La [guía de desarrollo iOS](ios-desarrollo-local.md) detalla la compilación local y las verificaciones realizadas.

## Configurar APNs y firma

1. Seleccioná el equipo Apple y configurá la firma del target iOS.
2. Habilitá **Push Notifications** y **Background Modes → Remote notifications** en **Signing & Capabilities**. El repositorio todavía no incluye un archivo de entitlements para push. Incorporá el generado por Xcode al completar esta configuración.
3. Cargá la clave de autenticación APNs del equipo en la configuración de Cloud Messaging de cada proyecto Firebase.
4. Comprobá que el perfil de aprovisionamiento corresponda al Bundle ID y tenga habilitada la capacidad de push.

Referencias: [configurar Firebase en Apple](https://firebase.google.com/docs/ios/setup) y [configurar Firebase Cloud Messaging en iOS](https://firebase.google.com/docs/cloud-messaging/ios/client).

## Validar en un iPhone físico

Con Firebase, APNs y el backend configurados, comprobá:

- Solicitud inicial de permiso, aceptación y rechazo.
- Registro del token con una sesión autenticada, rotación del token y eliminación al cerrar sesión.
- Recepción de banners en primer plano.
- Apertura desde segundo plano y con la app cerrada.
- Selección del establecimiento del payload y apertura de su stock al tocar el aviso.

Las compilaciones y el arranque en simulador registrados en la guía de desarrollo no acreditan entrega push. Estas pruebas con los servicios configurados siguen pendientes.
