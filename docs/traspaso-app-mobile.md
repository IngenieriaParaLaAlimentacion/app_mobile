# Traspaso de la app mobile y alternativas de distribución

Ingeniería en Alimentación. Fuentes consultadas el 28 de octubre de 2026.

## 1. Qué se entrega y cómo se descarga hoy

Android se descarga por Firebase App Distribution. El equipo administra el servicio con un Gmail dedicado al proyecto. La entrega incluye esa cuenta, el control del proyecto Firebase, las claves de firma, el código fuente y las instrucciones para publicar nuevas versiones. El acta de la sección 9 registra la recepción.

Para iOS se entrega el proyecto de Xcode. Todavía no hay una descarga disponible para usuarios. Falta elegir el canal, contratar la membresía Apple, configurar la firma y validar la versión que se distribuirá.

La publicación en Google Play y App Store queda a elección del cliente. Los costos de las cuentas figuran en la sección 2 y los pasos de publicación, en la sección 8.

## 2. Costos de distribución y publicación

| Concepto | Arancel de referencia | Qué cubre |
| --- | --- | --- |
| Firebase App Distribution | Sin cargo | Distribución de versiones de prueba |
| Google Play Console | USD 25, pago único | Cuenta para publicar y gestionar apps en Google Play |
| Apple Developer Program | USD 99 por año | Distribución con TestFlight, App Store y Ad Hoc |

Fuentes: [precios de Firebase](https://firebase.google.com/pricing), [registro de Play Console](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en) e [inscripción al programa de Apple](https://developer.apple.com/programs/enroll/).

### Posible exención del arancel de Apple

Las instituciones educativas acreditadas, entidades gubernamentales y organizaciones sin fines de lucro pueden solicitar la exención al inscribirse. Si Apple la aprueba, la cuenta queda sin arancel. La entidad confirma que sigue cumpliendo los requisitos en cada renovación anual. [Exención del arancel de Apple](https://developer.apple.com/help/account/membership/fee-waivers).

## 3. Alternativas investigadas para Android

| Alternativa | Cómo recibe la app el usuario | Ventajas y límites | Aplicación al proyecto |
| --- | --- | --- | --- |
| Firebase App Distribution | Acepta una invitación por correo, ingresa con Google y descarga el APK | Gestiona grupos y versiones. La instalación ocurre fuera de Google Play | Canal actual |
| Google Play público | Busca la app o abre su enlace en Play Store | La tienda gestiona la descarga y las actualizaciones. Exige una cuenta y revisión | Publicación estable, a elección del cliente |
| Pruebas de Google Play | Acepta una invitación y descarga desde Play Store | Prueba interna de hasta 100 testers. También ofrece pruebas cerradas y abiertas | Alternativa a Firebase con una cuenta Play Console |
| APK por web o enlace directo | Descarga el archivo y autoriza su instalación | El administrador gestiona el alojamiento, los accesos y las actualizaciones | Posible, con más trabajo manual que Firebase |

Fuentes: [Firebase para Android](https://firebase.google.com/docs/app-distribution/android/distribute-console), [creación de una app en Play](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [canales de prueba de Play](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en) y [distribución alternativa Android](https://developer.android.com/distribute/marketing-tools/alternative-distribution).

### Vigencia y límites de Firebase

Firebase conserva cada versión para descarga durante 150 días. El administrador guarda los APK originales y mantiene una versión vigente en el servicio. El APK instalado no caduca por ese plazo. Las invitaciones vencen a los 30 días y el administrador puede reenviarlas. [Disponibilidad e invitaciones](https://firebase.google.com/docs/app-distribution/android/distribute-console).

Los límites estándar son 500 testers por proyecto y 200 por grupo. Firebase permite solicitar un aumento sin cargo. [Gestión y límites de testers](https://firebase.google.com/docs/app-distribution/add-remove-testers).

### Verificación de desarrolladores Android

Google está incorporando la verificación de identidad de desarrolladores y el registro de apps. Su cronograma fija el inicio para el 30 de septiembre de 2026 en tiendas participantes de Brasil, Indonesia, Singapur y Tailandia, y prevé una ampliación global durante 2027. La distribución por APK o Firebase depende de los requisitos vigentes para cada país y dispositivo. [Cronograma oficial de Android](https://developer.android.com/developer-verification).

## 4. Alternativas investigadas para iOS

### Requisitos de cuenta Apple

El desarrollo no requiere una membresía paga. Una cuenta gratuita permite instalar la app en un dispositivo personal mediante un Personal Team. Sus perfiles de aprovisionamiento vencen a los 7 días y exigen volver a compilar e instalar. Esta opción cubre desarrollo y pruebas personales. [Cuenta gratuita y Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account).

TestFlight, App Store y Ad Hoc requieren una membresía activa del Apple Developer Program. Cuesta USD 99 anuales, salvo exención aprobada. Los testers y usuarios no pagan esa membresía. [Recursos de las cuentas Apple](https://developer.apple.com/help/account/basics/about-your-developer-account) e [inscripción y arancel](https://developer.apple.com/programs/enroll/).

| Alternativa | Cómo recibe la app el usuario | Requisitos y límites principales | Aplicación al proyecto |
| --- | --- | --- | --- |
| Xcode y Personal Team gratuito | El desarrollador instala desde Xcode en un dispositivo personal | Perfiles de 7 días | Desarrollo y pruebas personales |
| TestFlight | Instala TestFlight desde App Store y acepta una invitación o enlace | Membresía del programa. Cada compilación dura hasta 90 días | Pruebas con usuarios, pendiente de habilitación |
| App Store público | Descarga desde la tienda o su enlace | Membresía, ficha y revisión de Apple | Publicación estable |
| App Store no listado | Abre un enlace directo a la ficha | Apple revisa la app y aprueba el modo no listado. Cualquiera con el enlace puede descargarla | Distribución estable sin aparecer en búsquedas |
| Ad Hoc con Firebase o entrega directa | Instala un IPA firmado para su dispositivo registrado | Membresía y registro del identificador del dispositivo, UDID. Hasta 100 dispositivos por familia y año de membresía | Pocos equipos conocidos, con administración de sus registros |
| Custom Apps para organizaciones | La organización distribuye por Apple Business o Apple School Manager | Membresía, revisión y cuenta de la organización destinataria | Distribución institucional privada |
| Marketplaces alternativos y distribución web nativa | Usa un canal autorizado en su región | Reglas regionales, autorización y notarización de Apple | No permite la distribución general en Argentina |

Fuentes: [Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account), [TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview), [apps no listadas](https://developer.apple.com/support/unlisted-app-distribution/), [dispositivos Ad Hoc](https://developer.apple.com/help/account/devices/devices-overview), [Custom Apps](https://developer.apple.com/support/volume-purchase-and-custom-apps/) y [distribución alternativa por región](https://support.apple.com/en-us/118110).

### TestFlight y Firebase en iOS

TestFlight admite hasta 100 testers internos con acceso a App Store Connect y 10.000 externos sin permisos de administración. Cada compilación vence a los 90 días, por lo que el administrador publica su reemplazo antes de esa fecha. Apple realiza una revisión completa de la primera compilación enviada a pruebas externas. Puede omitir esa revisión completa en las siguientes compilaciones de la misma versión. [Funcionamiento y límites](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview) y [revisión de pruebas externas](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers).

Firebase también distribuye iOS y exige la firma de Apple. Para Ad Hoc, Firebase recoge el UDID de cada dispositivo. El desarrollador lo registra en Apple, lo incorpora al perfil de aprovisionamiento y vuelve a generar el paquete. También controla los vencimientos de los perfiles y certificados y el cupo de dispositivos. [Registro de dispositivos con Firebase](https://firebase.google.com/docs/app-distribution/register-additional-devices) y [cupo de Apple](https://developer.apple.com/help/account/devices/devices-overview).

TestFlight evita el registro de cada dispositivo. Ad Hoc permite controlar qué equipos instalan la app, a costa de administrar esos registros. El cliente elige el canal antes de habilitar las descargas de iOS.

### Opciones para una distribución estable y restringida

Una app no listada se descarga desde App Store por un enlace y no aparece en búsquedas ni categorías. Apple exige una versión final y aprueba tanto la app como ese modo de distribución. No admite versiones beta. Cualquiera puede compartir el enlace. La autenticación y los permisos de la app controlan el acceso a sus funciones. [Distribución no listada](https://developer.apple.com/support/unlisted-app-distribution/).

Una Custom App está disponible solo para las organizaciones designadas en Apple Business o Apple School Manager. Apple también la revisa. [Distribución a organizaciones específicas](https://developer.apple.com/support/volume-purchase-and-custom-apps/).

### Canales regionales y alternativa web

Apple informa distribución alternativa en Brasil, Japón y la Unión Europea, con condiciones distintas en cada región. Este canal no cubre la distribución general en Argentina. [Disponibilidad regional](https://support.apple.com/en-us/118110). En la Unión Europea, la distribución web de apps nativas exige autorización, notarización y un dominio registrado en App Store Connect. [Distribución web en la UE](https://developer.apple.com/support/web-distribution-eu/).

La opción web o PWA requiere desarrollar o validar por separado las funciones móviles y offline de esta app.

## 5. Cuentas, accesos y materiales incluidos en el traspaso

### Gmail dedicado y proyecto Firebase

El acta de la sección 9 identifica el Gmail del proyecto, el Project ID, los Firebase App ID y los enlaces de descarga.

El cliente recibe el control del Gmail y acceso a Firebase desde sus propias cuentas. El rol `Firebase App Distribution Admin`, con identificador `roles/firebaseappdistro.admin`, permite gestionar versiones y testers. La administración de propietarios, permisos y otros recursos requiere roles adicionales. Una invitación como tester solo da acceso a la descarga. [Roles oficiales de Firebase](https://firebase.google.com/docs/projects/iam/roles-predefined-product).

Procedimiento de recepción:

1. El cliente ingresa al Gmail y comprueba el acceso a Firebase, las invitaciones y el historial de versiones.
2. El cliente y el equipo saliente actualizan la contraseña, el correo y teléfono de recuperación, la verificación en dos pasos y los códigos de respaldo. El cliente conserva el control de esos métodos. Las credenciales se entregan por un canal seguro, fuera de Git y de este documento.
3. El administrador actual agrega las cuentas del cliente con permiso para gestionar el proyecto y distribuir versiones. El acta identifica quién administra usuarios y permisos.
4. Ambas partes registran los servicios asociados al Gmail, los recursos del proyecto y su facturación. El traspaso conserva el proyecto existente.
5. El cliente agrega un tester y distribuye una versión para comprobar el acceso. Las partes acuerdan qué permisos conserva el equipo saliente y cierran las sesiones restantes.

### Inventario técnico de entrega

| Elemento | Alcance de la entrega |
| --- | --- |
| Repositorio | Acceso al código, commit de entrega, instrucciones de compilación y responsable técnico |
| Firma Android | Keystore original `.jks` o `.keystore`, alias, contraseñas, respaldo y huella SHA-256 del certificado. Las claves se entregan por un canal seguro |
| `keystore.properties` | Configuración local basada en `keystore.properties.example`. El archivo real queda fuera de Git |
| Firebase | Gmail dedicado, Project ID, Firebase App ID de cada entorno, grupos de testers y enlaces vigentes |
| `firebase.properties` | Valores de `appId` y `groups`, basados en `firebase.properties.example` |
| Entornos | `local.properties` y URLs de API/backoffice para `tst` y `prod`, según `local.properties.example` |
| Artefactos | APK entregado, versión, `versionCode`, entorno, certificado firmante y notas de versión |
| Automatizaciones | Inventario de las tareas de publicación existentes, accesos y responsables. Renovación de credenciales al cerrar el traspaso |
| Backend y dominios | Responsables de la API, el correo y los enlaces de verificación y recuperación. Los accesos de infraestructura constan en su traspaso correspondiente |
| Apple y Play | Alta y configuración pendientes de la decisión del cliente. Cada cuenta registra su titular, administradores, facturación y renovaciones |

La clave de firma de Android permite actualizar las instalaciones existentes. El cliente recibe el keystore además del acceso a Firebase. Al configurar Google Play, la clave que firma la app instalada puede ser distinta de la clave usada para subir versiones. La sección 8 explica cómo mantener la compatibilidad entre ambos canales. [Firma Android y Play App Signing](https://developer.android.com/studio/publish/app-signing).

Las cuentas de las tiendas se registran a nombre del cliente. Un responsable autorizado administra la cuenta e invita al equipo técnico. Apple solicita datos de la entidad legal, autoridad para representarla, información de contacto y número D-U-N-S cuando corresponda. [Inscripción de organizaciones](https://developer.apple.com/programs/enroll/).

Esta titularidad evita transferir la app desde la cuenta de un proveedor. Apple exige al menos una versión publicada en App Store para aceptar una transferencia entre cuentas. [Criterios de transferencia](https://developer.apple.com/help/app-store-connect/transfer-an-app/app-transfer-criteria/).

## 6. Cómo continuar las descargas de Android

### Para quienes usan la app

1. El administrador incorpora su correo al grupo correcto de Firebase.
2. El usuario abre la invitación en el celular Android, ingresa con una cuenta Google y acepta el acceso.
3. Descarga la versión indicada y autoriza la instalación desde esa fuente si Android lo solicita.
4. Instala la app e ingresa con sus credenciales de la aplicación. La cuenta Google da acceso a la descarga. El acceso a las funciones de la app requiere su propia cuenta.
5. Cuando el administrador publica otra versión, recibe el aviso y actualiza sobre la instalación existente. Si necesita desinstalar, primero sincroniza los datos offline pendientes.

La guía detallada está en [Cómo instalar la app de prueba](guia-instalacion-testers.md). Referencia del proveedor: [configuración como tester de Firebase](https://firebase.google.com/docs/app-distribution/get-set-up-as-a-tester).

### Para quienes generan y distribuyen versiones

La configuración del repositorio está en [composeApp/build.gradle.kts](../composeApp/build.gradle.kts). Hay dos identidades Android:

| Entorno | Application ID | Nombre visible |
| --- | --- | --- |
| `tst` | `ar.com.ingenieriaalimentacion.tst` | Ing. Alimentación (Test) |
| `prod` | `ar.com.ingenieriaalimentacion` | Ingeniería en Alimentación |

Las variantes pueden coexistir y guardan sus datos por separado. La publicación de producción usa `prod`. El código entregado define `versionName = 0.1.1` y `versionCode = 2`. La siguiente versión toma como referencia la última distribuida en Firebase.

Procedimiento de generación y distribución de nuevas versiones:

1. Preparar JDK 21, Android SDK y los archivos locales del inventario. Configurar las URLs del entorno elegido.
2. Comprobar el certificado de las instalaciones actuales y actualizar el número de versión. Cada ejecución de Gradle compila un solo entorno.

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ./gradlew :composeApp:assembleTstRelease --no-daemon
   ```

   Para producción, ejecutar `./gradlew :composeApp:assembleProdRelease --no-daemon` por separado. Los APK quedan en `composeApp/build/outputs/apk/`, dentro de la carpeta del entorno y tipo de compilación. Comprobar la firma del APK antes de distribuirlo.

3. Probar la instalación, la actualización sobre la versión anterior, el inicio de sesión, los enlaces y las operaciones offline con sincronización.
4. En Firebase Console, elegir el proyecto y la app. Subir el APK, seleccionar el grupo, escribir las notas y distribuir la versión. Un usuario del cliente comprueba la descarga. [Distribución por consola](https://firebase.google.com/docs/app-distribution/android/distribute-console).

El proyecto también incluye el plugin de Firebase para publicar desde Gradle. Este método requiere autenticación con Firebase CLI o credenciales de servicio, y la tarea de subida del entorno elegido. Esa tarea distribuye la versión a los usuarios, después de comprobar el APK y sus destinatarios. [Autenticación y publicación con Gradle](https://firebase.google.com/docs/app-distribution/android/distribute-gradle).

`firebase.properties` contiene un único `appId` para las compilaciones `release`. Antes de cada subida, el administrador comprueba que ese valor corresponda al paquete del APK. Para operar los dos entornos, Firebase necesita el registro de ambas apps Android y la configuración del App ID de cada una.

## 7. Cómo completar iOS y habilitar sus descargas

### Preparación de la versión de entrega

El proyecto [iosApp.xcodeproj](../iosApp/iosApp.xcodeproj/project.pbxproj) contiene los esquemas `iosApp-tst` e `iosApp-prod`, con entornos y Bundle IDs separados. Ambos utilizan la configuración de API compartida con Android.

Para habilitar las descargas de iOS:

1. Elegir el canal de distribución y registrar la cuenta Apple del cliente.
2. Definir las versiones de iOS soportadas y validar la app en dispositivos de destino.
3. Confirmar los Bundle ID, el nombre y la versión. Completar los íconos y configurar la firma con el equipo Apple. Incorporar el `GoogleService-Info.plist` de cada entorno y configurar APNs para notificaciones. Probar los enlaces de verificación y recuperación en iOS.
4. Verificar las URLs y las configuraciones Release antes de generar el archivo de distribución. La fase de compilación pasa `-PtargetEnvironment=tst` o `prod` según el esquema. Los entornos se compilan por separado porque comparten la configuración Kotlin generada.
5. Validar autenticación, persistencia de sesión, base local, operaciones offline y sincronización, navegación y comportamiento al volver del segundo plano.
6. Crear el archivo de distribución y subirlo al canal elegido. Comprobar la instalación y la actualización con una cuenta del cliente.

### Descarga mediante TestFlight

Si el cliente elige TestFlight, el administrador sube la versión a App Store Connect, prepara el grupo externo y solicita la revisión. El usuario instala TestFlight desde App Store, abre la invitación o enlace y toca instalar. Luego actualiza desde TestFlight. El administrador publica los reemplazos antes del vencimiento de 90 días. [Flujo de TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview).

Para distribuir la versión estable por App Store hace falta una publicación adicional. Después de la aprobación, los usuarios descargan y actualizan desde la ficha de la app.

## 8. Publicación opcional en las tiendas

El cliente puede publicar en una plataforma o en ambas. Para cada tienda define el titular de la cuenta, el presupuesto y el responsable de mantenimiento.

### Google Play

1. Crear la cuenta del cliente, verificar su identidad y pagar el registro. [Alta de Play Console](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en).
2. Mantener el paquete de producción, generar un Android App Bundle `.aab` firmado y configurar Play App Signing. El comando es `./gradlew :composeApp:bundleProdRelease --no-daemon`. La generación requiere las claves de firma.
3. Comprobar la compatibilidad de firma antes de la primera subida. Para actualizar desde Play sobre el APK de Firebase, ambos canales necesitan el mismo paquete y un certificado compatible. Google permite aportar la clave propia al configurar Play App Signing. La clave de subida puede ser distinta de la que firma el APK instalado. [Identidades y continuidad de firma](https://developer.android.com/studio/publish/app-signing).
4. Preparar la ficha, imágenes, contacto de soporte, clasificación, acceso del revisor y declaraciones. [Preparación de la app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en). Completar Data safety con los datos que trata la app y sus SDKs. [Declaración de datos](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
5. Comprobar las opciones de eliminación de cuenta. Cuando la app permite crear cuentas, Google exige una vía para solicitar su eliminación dentro y fuera de la app. [Requisitos de eliminación de cuentas](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en-gb).
6. Completar las pruebas y la revisión de la cuenta. Las cuentas personales creadas después del 13 de noviembre de 2023 requieren una prueba cerrada con al menos 12 testers durante 14 días continuos. Después, el titular solicita acceso a producción. Las pruebas de Firebase no cuentan para ese requisito de Play. [Requisitos de prueba](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Antes de publicar, el equipo prueba la actualización desde Firebase a Play en dispositivos con la versión existente. Sincroniza los datos pendientes antes de desinstalar o cambiar la identidad de la app. También actualiza la guía de descarga y los certificados de App Links del dominio. El archivo `assetlinks.json` debe reconocer el certificado de la app instalada para que funcionen esos enlaces.

### App Store

1. Inscribir al cliente en el Apple Developer Program y solicitar la exención si corresponde. Identificar al titular, Account Holder, y al responsable de renovar la membresía.
2. Completar y validar la implementación iOS de la sección 7.
3. Elegir entre app pública, no listada o Custom App según el público. Preparar la ficha, capturas, contacto de soporte y acceso del revisor.
4. Comprobar la política de privacidad en la app y en la ficha. Si la app permite crear cuentas, Apple también exige que el usuario pueda eliminarlas desde la app. [Directrices de App Review](https://developer.apple.com/app-store/review/guidelines/).
5. Subir la versión final, completar las declaraciones y solicitar la revisión. El modo no listado requiere una solicitud adicional. [Solicitud de app no listada](https://developer.apple.com/support/unlisted-app-distribution/).
6. Comprobar la descarga y la actualización con una cuenta de usuario y entregar el enlace definitivo.

### Mantenimiento posterior

El titular renueva la membresía Apple cada año. Si vence, Apple retira la app de nuevas descargas e impide enviar actualizaciones. Las instalaciones existentes siguen funcionando. [Renovación de Apple](https://developer.apple.com/help/account/membership/renewal/). El administrador de pruebas también mantiene las compilaciones de TestFlight y las descargas de Firebase dentro de sus plazos de vigencia.

## 9. Decisiones y acta de recepción

### Decisiones registradas y pendientes

| Tema | Estado |
| --- | --- |
| Distribución Android actual | Firebase App Distribution |
| Cuenta Firebase actual | Gmail dedicado al proyecto, incluido en el traspaso |
| Distribución iOS actual | Sin canal de descarga habilitado para usuarios |
| Pruebas iOS | Elección entre TestFlight y Firebase Ad Hoc pendiente. Ninguno está habilitado |
| Publicación Google Play | A elección del cliente. Cuenta y responsable pendientes |
| Publicación App Store | A elección del cliente. Membresía o exención y preparación de iOS pendientes |

### Registro de recepción

El acta identifica las cuentas, los materiales, los responsables y los accesos. El equipo saliente entrega las contraseñas, claves privadas y códigos de recuperación por un canal seguro. El acta registra dónde se guardan y quién puede acceder.

| Dato | Registro |
| --- | --- |
| Responsables del equipo saliente y del cliente | Pendiente |
| Correo del Gmail dedicado | Pendiente |
| Recuperación y verificación en dos pasos bajo control del cliente | Pendiente de verificar |
| Firebase Project ID y enlace de consola | Pendiente |
| Firebase App ID por entorno y grupo de testers | Pendiente |
| Enlace vigente de descarga Android | Pendiente |
| Versión, entorno, commit y fecha del APK entregado | Pendiente |
| Huella SHA-256 del certificado que firma ese APK | Pendiente |
| Resguardo y recepción del keystore y propiedades locales | Pendiente |
| Responsables de backend, dominios y soporte | Pendiente |
| Cuenta Play, titular y decisión de publicación | Pendiente |
| Cuenta Apple, Team ID, Bundle ID y decisión de distribución | Pendiente |
| Responsable del pago o exención y renovación Apple | Pendiente |
| Accesos que conserva el equipo saliente y fecha de retiro | Pendiente |

### Comprobación de recepción

- [ ] El cliente controla el Gmail, su recuperación y segundo factor.
- [ ] Puede administrar el proyecto Firebase desde sus cuentas y gestionar testers.
- [ ] Recibió código, configuración y respaldo de firma por los canales acordados.
- [ ] Generó un APK firmado y distribuyó una versión al grupo de recepción.
- [ ] Un usuario del cliente instaló y actualizó la app, y probó acceso, operación offline y sincronización.
- [ ] Quedaron registrados los enlaces reales y los responsables de soporte e infraestructura.
- [ ] Las partes acordaron el canal de distribución iOS y los responsables de su preparación, firma y validación.
- [ ] Quedó documentada la decisión sobre las tiendas, quién paga y quién mantiene cada canal.
- [ ] Se acordó el cierre de los accesos del equipo saliente.
