# Traspaso de la app mobile y alternativas de distribución

**Aplicación:** Ingeniería en Alimentación.  
**Fecha de consulta de las fuentes:** 28 de octubre de 2026.  
**Estado de distribución:** Android disponible mediante Firebase App Distribution; distribución iOS pendiente de habilitación.

## 1. Qué se entrega y cómo se descarga hoy

Android se distribuye mediante **Firebase App Distribution**, administrado desde **un Gmail creado específicamente para este proyecto**. La entrega incluye el control de esa cuenta y del proyecto Firebase, las claves de firma, el código fuente y las instrucciones para generar y distribuir nuevas versiones. La recepción de los accesos y materiales queda registrada en el acta de la sección 9.

Para **iOS se entrega el proyecto de Xcode**. La habilitación de descargas para usuarios queda pendiente de la elección del canal, el alta de la membresía Apple, la configuración de firma y la validación de la versión de distribución.

| Plataforma | Situación actual | Descarga actual | Próxima decisión |
| --- | --- | --- | --- |
| Android | Distribución por Firebase App Distribution | Invitación por correo y descarga del APK autorizado | Mantener este canal de pruebas o preparar publicación en Google Play |
| iOS | Proyecto disponible; distribución pendiente | Todavía no hay una descarga para usuarios | Definir membresía, firma y canal; validar la versión de entrega |

**Continuidad de las descargas:** Android mantiene Firebase App Distribution como canal actual. Para iOS, TestFlight permite distribuir versiones de prueba y App Store permite publicar la versión estable; ambos canales están pendientes de habilitación. La publicación en Google Play y App Store es opcional y queda sujeta a la decisión del cliente, la contratación de las cuentas y la asignación de responsables de operación.

Pagar las cuentas de desarrollador habilita los canales correspondientes, pero **la publicación también requiere preparar la app, completar las fichas y superar las revisiones aplicables**. La app puede ofrecerse gratuitamente a los usuarios aunque la cuenta del desarrollador tenga costo.

## 2. Costos de distribución y publicación

| Concepto | Arancel de referencia | Qué cubre |
| --- | --- | --- |
| Firebase App Distribution | Sin cargo por el servicio | Distribución de versiones de prueba; otros productos de Firebase pueden tener cargos propios |
| Google Play Console | **USD 25, pago único** | Registro de la cuenta para publicar y gestionar apps en Google Play |
| Apple Developer Program | **USD 99 por año** o precio local | Acceso a distribución con TestFlight, App Store y Ad Hoc, entre otras capacidades |
| Apple Developer Enterprise Program | **USD 299 por año** o precio local | Distribución interna a empleados en organizaciones elegibles; alternativa sujeta a los requisitos específicos del programa |

Fuentes: [precios de Firebase](https://firebase.google.com/pricing), [registro de Play Console](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en), [inscripción al programa de Apple](https://developer.apple.com/programs/enroll/) y [programa Enterprise](https://developer.apple.com/programs/enterprise/).

Los importes son referencias del proveedor a la fecha indicada. El importe final se confirma al contratar, según región, moneda, impuestos y medio de pago. No incluyen desarrollo, equipos, infraestructura del backend, soporte, servicios de compilación ni eventuales cargos por monetización.

### Posible exención del arancel de Apple

Una **institución educativa acreditada, entidad gubernamental u organización sin fines de lucro** puede solicitar una exención si reúne los requisitos de Apple. No es automática: Apple evalúa la entidad y las condiciones sobre venta de bienes y servicios digitales y acuerdos de apps pagas. La elegibilidad debe confirmarse en la renovación anual.

La aprobación de la exención evita el arancel; la organización mantiene la inscripción en el Apple Developer Program y el cumplimiento de sus requisitos. La solicitud puede realizarse durante el alta de una entidad elegible. [Requisitos oficiales de exención](https://developer.apple.com/help/account/membership/fee-waivers).

## 3. Alternativas investigadas para Android

| Alternativa | Cómo recibe la app el usuario | Ventajas y límites | Aplicación al proyecto |
| --- | --- | --- | --- |
| **Firebase App Distribution** | Invitación por correo, ingreso con Google y descarga de APK | Gestiona grupos, versiones y notas; requiere instalación fuera de Google Play | **Canal actual de distribución Android** |
| **Google Play público** | Busca la app o abre su enlace en Play Store | Facilita instalación y actualizaciones; requiere cuenta, preparación y revisión | Opción para publicación estable si la aprueba el receptor |
| **Pruebas de Google Play** | Acepta una invitación y descarga desde Play Store | Prueba interna de hasta 100 testers; también hay pruebas cerradas y abiertas | Alternativa a Firebase cuando se disponga de Play Console |
| **APK por web o enlace directo** | Descarga el archivo y autoriza su instalación | Evita depender de una tienda para entregar el archivo; exige gestionar alojamiento, acceso y actualizaciones | Posible, con más operación manual que Firebase |
| **Otras tiendas Android** | Instala desde la tienda elegida | Permiten otros canales; cada proveedor tiene condiciones y revisión propias | Alternativa disponible según los dispositivos y el público; sin implementación en esta entrega |
| **Managed Google Play privado** | Recibe una app habilitada para su organización | Orientado a distribución empresarial con administración de dispositivos | Alternativa para organizaciones con solución EMM/MDM; sin configuración en esta entrega |

Fuentes: [Firebase para Android](https://firebase.google.com/docs/app-distribution/android/distribute-console), [creación de una app en Play](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en), [canales de prueba de Play](https://support.google.com/googleplay/android-developer/answer/9845334?hl=en), [distribución alternativa Android](https://developer.android.com/distribute/marketing-tools/alternative-distribution) y [apps privadas empresariales](https://developers.google.com/android/work/play/emm-api/private-apps).

En Managed Google Play, el mecanismo de publicación privada integrado en una consola EMM puede crear la cuenta y eximir el registro de USD 25. Esto no equivale a una cuenta gratuita para publicación pública y puede haber costos del servicio EMM/MDM. [Publicación privada mediante EMM](https://developers.google.com/android/work/play/emm-api/private-apps).

### Límites de Firebase que afectan la continuidad

Las versiones quedan disponibles para descargar durante **150 días**. Las invitaciones tienen una vigencia de **30 días** y pueden reenviarse. La operación del canal incluye conservar los APK originales y mantener una versión descargable vigente; los 150 días corresponden a disponibilidad en Firebase, no a una caducidad automática del APK ya instalado. [Disponibilidad e invitaciones](https://firebase.google.com/docs/app-distribution/android/distribute-console).

Los límites estándar son **500 testers por proyecto y 200 por grupo**, con posibilidad de solicitar un aumento sin cargo. [Gestión y límites de testers](https://firebase.google.com/docs/app-distribution/add-remove-testers).

### Verificación de desarrolladores Android

Google está desplegando la verificación de identidad de desarrolladores y registro de apps. Su cronograma indica protecciones desde el 30 de septiembre de 2026 para tiendas participantes en Brasil, Indonesia, Singapur y Tailandia, y una ampliación global durante 2027. La ampliación de la distribución por APK/Firebase queda sujeta a los requisitos vigentes para los dispositivos y países de destino. [Cronograma oficial de Android](https://developer.android.com/developer-verification).

## 4. Alternativas investigadas para iOS

### Requisitos de cuenta Apple

**El desarrollo no requiere una membresía paga.** Una cuenta gratuita permite instalar en un dispositivo personal mediante un Personal Team, con restricciones: los perfiles de aprovisionamiento vencen a los **7 días**, lo que exige volver a compilar e instalar. Su alcance es el desarrollo y las pruebas personales. [Cuenta gratuita y Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account).

**Para distribuir mediante TestFlight, App Store o Ad Hoc se necesita una membresía activa del Apple Developer Program**, cuyo arancel habitual es USD 99 anuales, salvo exención aprobada. Los testers y usuarios no necesitan comprar esa membresía. [Recursos de las cuentas Apple](https://developer.apple.com/help/account/basics/about-your-developer-account) e [inscripción y arancel](https://developer.apple.com/programs/enroll/).

| Alternativa | Cómo recibe la app el usuario | Requisitos y límites principales | Aplicación al proyecto |
| --- | --- | --- | --- |
| **Xcode y Personal Team gratuito** | El desarrollador instala desde Xcode en un dispositivo personal | Perfiles de 7 días; requiere intervención frecuente | Desarrollo y pruebas personales |
| **TestFlight** | Instala TestFlight desde App Store y acepta una invitación o enlace | Membresía del programa; cada build dura hasta 90 días | Canal de pruebas con usuarios; pendiente de habilitación |
| **App Store público** | Descarga desde la tienda o su enlace | Membresía, ficha y revisión de Apple | Opción de publicación estable |
| **App Store no listado** | Abre un enlace directo a la ficha | Revisión y aprobación del modo no listado; cualquiera con el enlace puede acceder | Distribución estable a una audiencia acotada, sin aparecer en búsquedas |
| **Ad Hoc con Firebase o entrega directa** | Instala un IPA firmado para su dispositivo registrado | Membresía; UDID en el perfil; hasta 100 dispositivos por familia y año de membresía | Viable para pocos equipos conocidos; implica más administración |
| **Custom Apps para organizaciones** | La organización distribuye mediante Apple Business o Apple School Manager | Membresía del desarrollador, revisión y organización receptora configurada | Alternativa para distribución institucional privada |
| **Enterprise interno** | Instalación mediante sistemas internos o MDM | Programa separado, elegibilidad y al menos 100 empleados; uso interno por empleados | Limitado a empleados de la organización; excluye distribución a beneficiarios, clientes o público externo |
| **Marketplaces alternativos y distribución web nativa** | Canal habilitado en una región admitida | Reglas regionales, autorización y notarización; no es subir libremente un IPA a cualquier web | No resuelve la distribución general en Argentina |

Fuentes: [Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account), [TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview), [apps no listadas](https://developer.apple.com/support/unlisted-app-distribution/), [dispositivos Ad Hoc](https://developer.apple.com/help/account/devices/devices-overview), [Custom Apps](https://developer.apple.com/support/volume-purchase-and-custom-apps/), [Enterprise](https://developer.apple.com/programs/enterprise/) y [distribución alternativa por región](https://support.apple.com/en-us/118110).

### TestFlight y Firebase en iOS

**TestFlight** permite hasta **100 testers internos** con acceso a App Store Connect y **10.000 externos**. Los testers externos acceden a las pruebas sin permisos de administración. Cada build se puede probar por hasta **90 días**; la continuidad de las pruebas requiere publicar reemplazos antes del vencimiento. La primera build enviada a revisión externa requiere revisión completa; las posteriores de la misma versión pueden no requerirla. [Funcionamiento y límites](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview) y [revisión de pruebas externas](https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers).

**Firebase también distribuye iOS**, pero no reemplaza la firma de Apple. En Ad Hoc, Firebase recoge el identificador del dispositivo (UDID); el desarrollador debe registrarlo, incorporarlo al perfil y volver a generar/distribuir el paquete. También se deben controlar los vencimientos de los perfiles y certificados. El límite de dispositivos de Apple sigue aplicándose. [Registro de dispositivos con Firebase](https://firebase.google.com/docs/app-distribution/register-additional-devices) y [cupo de Apple](https://developer.apple.com/help/account/devices/devices-overview).

**TestFlight permite incorporar usuarios sin registrar cada dispositivo.** Firebase Ad Hoc exige administrar los dispositivos autorizados y sus perfiles. Ambos canales quedan disponibles como alternativas para iOS; su configuración está pendiente de la elección del cliente.

### Opciones para una distribución estable y restringida

Una app **no listada** se descarga desde App Store mediante un enlace, sin aparecer en búsquedas o categorías. Requiere una versión final, revisión y aprobación de Apple; no se acepta como sustituto de una beta. El enlace no controla quién puede usarla: debe mantenerse la autenticación y autorización de la app. [Distribución no listada](https://developer.apple.com/support/unlisted-app-distribution/).

Una **Custom App** se ofrece únicamente a las organizaciones designadas en Apple Business o Apple School Manager y pasa por revisión. Es una opción distinta de Enterprise: puede atender distribución institucional sin recurrir al programa interno para empleados. [Distribución a organizaciones específicas](https://developer.apple.com/support/volume-purchase-and-custom-apps/).

### Canales regionales y alternativa web

A la fecha de consulta, Apple informa distribución alternativa en **Brasil, Japón y la Unión Europea**, con funciones y requisitos que varían por región. No constituye una vía general para instalar la app nativa en Argentina. [Disponibilidad regional](https://support.apple.com/en-us/118110). La distribución web nativa de la UE exige autorización, notarización y un dominio registrado en App Store Connect. [Web Distribution en la UE](https://developer.apple.com/support/web-distribution-eu/).

Una **web o PWA** podría ser otra solución de producto si se decide atender usuarios desde el navegador. No es distribuir esta build nativa: requiere desarrollar o validar por separado las funciones móviles y offline necesarias. No se entrega una PWA como parte de este traspaso.

## 5. Cuentas, accesos y materiales incluidos en el traspaso

### Gmail dedicado y proyecto Firebase

La administración de Firebase utiliza un **Gmail dedicado al proyecto**. La cuenta, el Project ID, los Firebase App ID y los enlaces de descarga se identifican en el acta de recepción de la sección 9.

La entrega incluye **el control del Gmail dedicado** y **el acceso al proyecto desde las cuentas del cliente**. El rol `Firebase App Distribution Admin` (`roles/firebaseappdistro.admin`) permite operar versiones y testers; la administración de propietarios, permisos y otros recursos del proyecto utiliza los roles correspondientes. Una invitación como tester solo permite probar la app. [Roles oficiales de Firebase](https://firebase.google.com/docs/projects/iam/roles-predefined-product).

Procedimiento de recepción:

1. El receptor ingresa al Gmail dedicado y verifica acceso a Firebase, invitaciones e historial de versiones.
2. Las partes actualizan contraseña, correo/teléfono de recuperación, verificación en dos pasos y códigos de respaldo, dejando esos controles en manos del receptor. Las credenciales se entregan por un canal seguro, fuera de Git y de este documento.
3. Se agregan las cuentas del equipo receptor con los roles necesarios para administrar el proyecto y distribuir versiones. Se identifica quién puede gestionar usuarios y permisos.
4. Se registran los servicios asociados al Gmail y al proyecto, junto con la facturación existente. La transferencia conserva el proyecto y sus recursos.
5. El receptor demuestra que puede agregar un tester y distribuir una versión. Luego se acuerda qué accesos conserva el equipo saliente y se cierran las sesiones que correspondan.

### Inventario técnico de entrega

| Elemento | Alcance de la entrega |
| --- | --- |
| Repositorio | Acceso, revisión/commit entregado, instrucciones de compilación y responsable técnico |
| Firma Android | Keystore original `.jks`/`.keystore`, alias y contraseñas por canal seguro; respaldo recuperable y huella SHA-256 del certificado |
| `keystore.properties` | Configuración local basada en `keystore.properties.example`; no se incorpora el archivo real a Git |
| Firebase | Gmail dedicado, Project ID, Firebase App ID de cada entorno, grupos de testers y enlaces vigentes |
| `firebase.properties` | Valores de `appId` y `groups`, basados en `firebase.properties.example` |
| Entornos | `local.properties` y URLs de API/backoffice para `tst` y `prod`, según `local.properties.example` |
| Artefactos | APK entregado, versión, `versionCode`, entorno, certificado firmante y notas de versión |
| Automatizaciones | Inventario de las automatizaciones de publicación existentes, sus accesos y responsables; renovación de credenciales al cierre del traspaso |
| Backend y dominios | Identificación de responsables y dependencias de API, correo y enlaces de verificación/recuperación; los accesos de infraestructura se registran en su traspaso correspondiente |
| Apple y Play | Alta y configuración pendientes de la decisión de publicación; titularidad, administradores, facturación y renovaciones se registran al habilitar cada cuenta |

**La cuenta de Firebase no reemplaza el keystore.** Para actualizar las instalaciones Android se debe conservar la identidad de firma compatible. Antes de publicar en Play, distinguir la clave que firma las apps instaladas de la clave de subida. [Firma Android y Play App Signing](https://developer.android.com/studio/publish/app-signing).

Para la publicación, las cuentas de las tiendas se registran **a nombre de la entidad cliente**, con un responsable autorizado y acceso del equipo técnico mediante invitaciones. Apple permite la inscripción de organizaciones con requisitos de entidad legal, autoridad, contacto y D-U-N-S cuando corresponda. [Inscripción de organizaciones](https://developer.apple.com/programs/enroll/). La titularidad del cliente desde el alta evita una transferencia posterior entre cuentas. Apple exige al menos una versión publicada en App Store para realizar esa transferencia. [Criterios de transferencia](https://developer.apple.com/help/app-store-connect/transfer-an-app/app-transfer-criteria/).

## 6. Cómo continuar las descargas de Android

### Para quienes usan la app

1. El administrador incorpora su correo al grupo correcto de Firebase.
2. La persona abre la invitación en el celular Android, ingresa con una cuenta Google y acepta el acceso.
3. Descarga la versión indicada y autoriza la instalación desde esa fuente si Android lo solicita.
4. Instala y utiliza las credenciales de la aplicación. La cuenta Google da acceso a la descarga; el acceso funcional a la app se gestiona por separado.
5. Cuando se distribuye otra versión, recibe el aviso y realiza la actualización. No debe desinstalar para actualizar de forma rutinaria, especialmente si tiene datos offline pendientes.

La guía detallada está en [Cómo instalar la app de prueba](guia-instalacion-testers.md). Referencia del proveedor: [configuración como tester de Firebase](https://firebase.google.com/docs/app-distribution/get-set-up-as-a-tester).

### Para quienes generan y distribuyen versiones

La configuración del repositorio está en [composeApp/build.gradle.kts](../composeApp/build.gradle.kts). Hay dos identidades Android:

| Entorno | Application ID | Nombre visible |
| --- | --- | --- |
| `tst` | `ar.com.ingenieriaalimentacion.tst` | Ing. Alimentación (Test) |
| `prod` | `ar.com.ingenieriaalimentacion` | Ingeniería en Alimentación |

Las variantes pueden coexistir y tienen almacenamiento separado. La publicación de producción utiliza `prod`. El código entregado define `versionName = 0.1.1` y `versionCode = 2`; la numeración de las siguientes versiones toma como referencia la última versión distribuida en Firebase.

Procedimiento de generación y distribución de nuevas versiones:

1. Preparar JDK 21, Android SDK y los archivos locales del inventario. Configurar las URLs del entorno elegido.
2. Confirmar el certificado usado por las instalaciones actuales, incrementar la versión según corresponda y compilar **un solo entorno por invocación**.

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 21)
   ./gradlew :composeApp:assembleTstRelease --no-daemon
   ```

   Para producción, ejecutar `./gradlew :composeApp:assembleProdRelease --no-daemon` en una invocación separada. Los APK se generan bajo `composeApp/build/outputs/apk/`, dentro del entorno y tipo de build correspondientes. Verificar que el APK de entrega esté firmado con la clave acordada.

3. Probar instalación, actualización sobre la versión anterior, login, enlaces y operaciones offline con sincronización.
4. En Firebase Console, elegir el proyecto y la app correctos; subir el APK, seleccionar el grupo, escribir notas y distribuir. Confirmar la descarga desde un usuario receptor. [Distribución por consola](https://firebase.google.com/docs/app-distribution/android/distribute-console).

El plugin de Firebase ya está integrado, por lo que también se puede preparar publicación mediante Gradle. Requiere autenticación de Firebase CLI o credenciales de servicio y la tarea de subida de la variante elegida. **Los comandos de subida distribuyen una versión a usuarios**, por lo que se ejecutan después de validar el artefacto y sus destinatarios. [Autenticación y publicación con Gradle](https://firebase.google.com/docs/app-distribution/android/distribute-gradle).

**Configuración de Firebase:** `firebase.properties` tiene un único `appId`, que se aplica a los builds `release`. El App ID se selecciona según el paquete del APK que se distribuye. La operación de ambos entornos requiere registrar las dos apps Android y configurar el App ID de cada una.

## 7. Cómo completar iOS y habilitar sus descargas

### Preparación de la versión de entrega

El proyecto [iosApp.xcodeproj](../iosApp/iosApp.xcodeproj/project.pbxproj) contiene los esquemas `iosApp-tst` e `iosApp-prod`, con entornos y Bundle IDs separados. Ambos utilizan la configuración de API compartida con Android.

La habilitación de la distribución iOS comprende las siguientes tareas:

1. Elegir el canal de distribución y registrar la cuenta Apple de la entidad receptora.
2. Definir las versiones de iOS soportadas y validar la app en dispositivos de destino.
3. Confirmar los Bundle IDs definidos en el proyecto, nombre y versión; completar íconos, configuración de firma y equipo Apple. Incorporar el `GoogleService-Info.plist` de cada entorno y configurar APNs para notificaciones. Configurar y probar los enlaces de verificación y recuperación en iOS.
4. Verificar las URLs de ambos entornos y las configuraciones Release antes de archivar. La fase de build ya pasa `-PtargetEnvironment=tst` o `prod` según el esquema; se deben compilar secuencialmente por compartir la configuración Kotlin generada.
5. Validar autenticación, persistencia de sesión, base local, operaciones offline y sincronización, navegación y comportamiento al volver del segundo plano.
6. Crear el archivo de distribución, subirlo al canal elegido y verificar instalación y actualización desde una cuenta del receptor.

### Descarga mediante TestFlight

Una vez habilitado TestFlight, el administrador sube la build a App Store Connect, prepara el grupo externo y tramita la revisión aplicable. El usuario instala **TestFlight** desde App Store, abre la invitación o enlace y toca instalar. Para las siguientes versiones actualiza desde TestFlight. El administrador mantiene builds vigentes y publica sus reemplazos antes del vencimiento de 90 días. [Flujo de TestFlight](https://developer.apple.com/help/app-store-connect/test-a-beta-version/testflight-overview).

Si se elige App Store para la versión estable, los usuarios pasarán a descargar y actualizar desde su ficha. Ese cambio exige una publicación adicional; no ocurre automáticamente por haber probado con TestFlight.

## 8. Publicación opcional en las tiendas

El cliente puede publicar en una sola plataforma o en ambas. La habilitación de cada tienda incluye la definición de titularidad, presupuesto y responsable de mantenimiento.

### Google Play

1. Crear/verificar la cuenta del receptor y pagar el registro. [Alta de Play Console](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en).
2. Mantener el paquete de producción, preparar un **Android App Bundle (`.aab`) firmado** y configurar Play App Signing. Para generar el bundle, el proyecto permite `./gradlew :composeApp:bundleProdRelease --no-daemon`; se debe validar con las claves reales.
3. **Resolver la continuidad de firma antes de la primera subida.** Si se quiere actualizar desde Play sobre el APK de Firebase, comprobar el mismo paquete y un certificado compatible. Para conservar la misma clave entre canales, Android documenta aportar la clave propia al configurar Play App Signing. Una clave de subida no es necesariamente la que firma el APK instalado. [Identidades y continuidad de firma](https://developer.android.com/studio/publish/app-signing).
4. Preparar ficha, imágenes, soporte, clasificación, acceso del revisor y declaraciones aplicables. [Preparación de la app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en). Completar Data safety según los datos realmente tratados, incluidos SDKs. [Declaración de datos](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
5. Si permite crear cuentas en la app, revisar el requisito de eliminación, que incluye vías dentro de la app y fuera de ella. [Requisitos de eliminación de cuentas](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en-gb).
6. Completar las pruebas y revisión exigidas para la cuenta. Las cuentas **personales creadas después del 13 de noviembre de 2023** necesitan una prueba cerrada con al menos **12 testers durante 14 días continuos** y luego solicitar acceso a producción. Las pruebas por Firebase no sustituyen ese requisito de Play. [Requisitos de prueba](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Antes de lanzar, probar el paso de Firebase a Play sobre dispositivos con la versión existente. Sincronizar datos pendientes antes de cualquier desinstalación o cambio de identidad. Actualizar la guía de descarga y los certificados de App Links del dominio cuando corresponda; el manifiesto Android exige que `assetlinks.json` reconozca el certificado de la app instalada.

### App Store

1. Inscribir a la entidad receptora en el Apple Developer Program; solicitar exención si corresponde. Identificar al Account Holder y a quien gestionará la renovación.
2. Completar y validar la implementación iOS de la sección 7.
3. Elegir entre app pública, no listada o Custom App según la audiencia. Preparar la ficha, capturas, soporte y acceso de revisión.
4. Revisar las obligaciones de privacidad y la funcionalidad de eliminación de cuenta si existe creación de cuentas. La política de privacidad debe estar disponible en la app y en la ficha. [Directrices de App Review](https://developer.apple.com/app-store/review/guidelines/).
5. Subir la versión final, completar las declaraciones aplicables y tramitar la revisión. Si se elige no listada, solicitar además ese modo de distribución. [Solicitud de app no listada](https://developer.apple.com/support/unlisted-app-distribution/).
6. Confirmar descarga y actualización desde una cuenta usuaria y entregar el enlace definitivo.

Las fechas de aprobación dependen de las verificaciones y revisiones de cada proveedor. **La entrega del código y el pago de la cuenta no garantizan publicación inmediata.**

### Mantenimiento posterior

La operación posterior a la publicación comprende soporte, nuevas versiones, compatibilidad con Android/iOS, actualización de SDKs, backend y enlaces de acceso. Los responsables de cada servicio se registran junto con las versiones, claves y permisos de administración.

Apple requiere renovar la membresía. Si vence, la app deja de estar disponible para nuevas descargas en App Store y no se pueden enviar actualizaciones; las instalaciones existentes no desaparecen por ese motivo. [Renovación de Apple](https://developer.apple.com/help/account/membership/renewal/). Para las pruebas, mantener además el calendario de builds de TestFlight y la disponibilidad de descargas en Firebase.

## 9. Decisiones y acta de recepción

### Decisiones registradas y pendientes

| Tema | Estado |
| --- | --- |
| Distribución Android actual | Firebase App Distribution |
| Cuenta Firebase actual | Gmail dedicado al proyecto, incluido en el traspaso |
| Distribución iOS actual | Sin canal de descarga habilitado para usuarios |
| Pruebas iOS | TestFlight y Firebase Ad Hoc disponibles como alternativas; elección y habilitación pendientes |
| Publicación Google Play | Opcional; requiere decisión, cuenta y responsable del receptor |
| Publicación App Store | Opcional; requiere decisión, membresía o exención y desarrollo iOS pendiente |
| Entidad cliente y titulares de las cuentas | Pendientes de identificación en el acta |

### Registro de recepción

El acta identifica las cuentas, materiales, responsables y accesos recibidos. Las contraseñas, claves privadas y códigos de recuperación se entregan por un canal seguro; el acta registra su ubicación de resguardo y las personas autorizadas.

| Dato | Registro |
| --- | --- |
| Institución/equipo receptor | Pendiente |
| Responsable saliente y responsable receptor | Pendiente |
| Correo del Gmail dedicado | Pendiente |
| Recuperación y verificación en dos pasos bajo control del receptor | Pendiente de verificar |
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

- [ ] El receptor controla el Gmail dedicado, su recuperación y segundo factor.
- [ ] Puede administrar el proyecto Firebase desde sus cuentas y gestionar testers.
- [ ] Recibió código, configuración y respaldo de firma por los canales acordados.
- [ ] Generó un APK firmado y distribuyó una versión al grupo de recepción.
- [ ] Un usuario receptor instaló y actualizó la app; probó acceso, operación offline y sincronización.
- [ ] Quedaron registrados los enlaces reales y los responsables de soporte e infraestructura.
- [ ] Las partes acordaron el canal de distribución iOS y los responsables de su preparación, firma y validación.
- [ ] Quedó documentada la decisión sobre las tiendas, quién paga y quién mantiene cada canal.
- [ ] Se acordó el cierre de los accesos del equipo saliente.

La conformidad de recepción registra el control de las cuentas, la recepción de los materiales y la continuidad de la distribución. Los requisitos y aranceles de cada proveedor corresponden a la fecha de consulta indicada; la contratación y publicación utilizan las condiciones vigentes de las fuentes oficiales citadas.
