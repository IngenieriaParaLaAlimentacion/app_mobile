# Traspaso de la app mobile y alternativas de distribución

Ingeniería en Alimentación. Fuentes consultadas el 28 de octubre de 2026.

## 1. Qué se entrega y cómo se descarga hoy

Android se descarga por Firebase App Distribution. El equipo administra el servicio con un Gmail dedicado al proyecto. La entrega incluye esa cuenta, el control del proyecto Firebase, las claves de firma, el código fuente y las instrucciones para publicar nuevas versiones.

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
| TestFlight | Instala TestFlight desde App Store y acepta una invitación o enlace | La misma membresía que App Store. Cada compilación dura hasta 90 días | Pruebas previas opcionales, sin habilitar |
| App Store público | Descarga desde la tienda o su enlace | Membresía, ficha y revisión de Apple | Publicación estable |
| App Store no listado | Abre un enlace directo a la ficha | Apple revisa la app y aprueba el modo no listado. Cualquiera con el enlace puede descargarla | Distribución estable sin aparecer en búsquedas |
| Ad Hoc con Firebase o entrega directa | Instala un IPA firmado para su dispositivo registrado | Membresía y registro del identificador del dispositivo, UDID. Hasta 100 dispositivos por familia y año de membresía | Pocos equipos conocidos, con administración de sus registros |
| Custom Apps para organizaciones | La organización distribuye por Apple Business o Apple School Manager | Membresía, revisión y cuenta de la organización destinataria | Distribución institucional privada |

Fuentes: [Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account), [TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview), [apps no listadas](https://developer.apple.com/support/unlisted-app-distribution/), [dispositivos Ad Hoc](https://developer.apple.com/help/account/devices/devices-overview) y [Custom Apps](https://developer.apple.com/support/volume-purchase-and-custom-apps/).

## 5. Cuentas, accesos y materiales incluidos en el traspaso

### Gmail dedicado y proyecto Firebase

El cliente recibe el control del Gmail y acceso a Firebase desde sus propias cuentas. El rol `Firebase App Distribution Admin`, con identificador `roles/firebaseappdistro.admin`, permite gestionar versiones y testers. La administración de propietarios, permisos y otros recursos requiere roles adicionales. Una invitación como tester solo da acceso a la descarga. [Roles oficiales de Firebase](https://firebase.google.com/docs/projects/iam/roles-predefined-product).

Procedimiento de recepción:

1. El cliente ingresa al Gmail y comprueba el acceso a Firebase, las invitaciones y el historial de versiones.
2. El cliente y el equipo saliente actualizan la contraseña, el correo y teléfono de recuperación, la verificación en dos pasos y los códigos de respaldo. El cliente conserva el control de esos métodos. Las credenciales se entregan por un canal seguro, fuera de Git y de este documento.
3. El administrador actual agrega las cuentas del cliente con permiso para gestionar el proyecto y distribuir versiones. Ambas partes acuerdan quién administra usuarios y permisos.
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

Las variantes pueden coexistir y guardan sus datos por separado. La publicación de producción usa `prod`. La versión Android se define con `versionName` y `versionCode` en [composeApp/build.gradle.kts](../composeApp/build.gradle.kts). Antes de distribuir, compará esos valores con la última versión publicada en Firebase y actualizalos. La versión iOS se configura por separado con `MARKETING_VERSION` y `CURRENT_PROJECT_VERSION` en [Base.xcconfig](../iosApp/Configuration/Base.xcconfig).

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

## 7. Cómo habilitar las descargas de iOS

### Qué incluye la entrega

La entrega incluye el proyecto de iOS, la separación entre pruebas y producción y la configuración del acceso al backend que usa Android. Esa preparación ya está hecha.

### Qué falta para distribuirla

1. El cliente elige el canal de distribución entre las alternativas de la sección 4.
2. El cliente registra su cuenta Apple y paga la membresía o solicita la exención.
3. Con esa cuenta, el equipo técnico completa la preparación de la versión para distribuir, configura las notificaciones y realiza las pruebas de funcionamiento en iPhone.
4. El equipo técnico sube la versión al canal elegido, tramita la revisión correspondiente y entrega el enlace de descarga o la invitación.

### Descarga desde App Store

Con la membresía Apple activa, el equipo puede enviar la versión final directamente a revisión para publicarla en App Store. No hace falta pasar por TestFlight. Después de la aprobación, el cliente recibe el enlace de la ficha y los usuarios descargan y actualizan desde la tienda. [Publicación en App Store](https://developer.apple.com/help/app-store-connect/manage-your-apps-availability/overview-of-publishing-your-app-on-the-app-store).

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
2. Preparar la versión para distribución y completar las pruebas indicadas en la sección 7.
3. Elegir entre app pública, no listada o Custom App según el público. Preparar la ficha, capturas, contacto de soporte y acceso del revisor.
4. Comprobar la política de privacidad en la app y en la ficha. Si la app permite crear cuentas, Apple también exige que el usuario pueda eliminarlas desde la app. [Directrices de App Review](https://developer.apple.com/app-store/review/guidelines/).
5. Subir la versión final, completar las declaraciones y solicitar la revisión. El modo no listado requiere una solicitud adicional. [Solicitud de app no listada](https://developer.apple.com/support/unlisted-app-distribution/).
6. Comprobar la descarga y la actualización con una cuenta de usuario y entregar el enlace definitivo.

### Mantenimiento posterior

El titular renueva la membresía Apple cada año. Si vence, Apple retira la app de nuevas descargas e impide enviar actualizaciones. Las instalaciones existentes siguen funcionando. [Renovación de Apple](https://developer.apple.com/help/account/membership/renewal/). El administrador mantiene las descargas vigentes en Firebase y, si usa TestFlight para pruebas, reemplaza las compilaciones antes de su vencimiento.

## 9. Decisiones registradas y pendientes

| Tema | Estado |
| --- | --- |
| Distribución Android actual | Firebase App Distribution |
| Cuenta Firebase actual | Gmail dedicado al proyecto, incluido en el traspaso |
| Distribución iOS actual | Sin canal de descarga habilitado para usuarios |
| Pruebas iOS | TestFlight es opcional para pruebas previas. Sin habilitar |
| Publicación Google Play | A elección del cliente. Cuenta y responsable pendientes |
| Publicación App Store | A elección del cliente. Membresía o exención y preparación de iOS pendientes |
