# Mantener el manual de App móvil

## Fuentes y entregas

Los capítulos editables y las capturas de esta plataforma viven en `docs/manual-usuario/`. `index.html` incluye las imágenes; se puede compartir sin carpetas adicionales. `manual.pdf` se genera a partir de ese HTML. El PDF incluye las figuras de los recorridos; la galería complementaria se conserva en HTML.

## Regenerar

Desde la raíz del repositorio:

```bash
python3 -m venv .context/manual-venv
.context/manual-venv/bin/pip install -r scripts/manual/requirements.txt
npm ci --prefix scripts/manual
npx --prefix scripts/manual playwright install chromium
.context/manual-venv/bin/python scripts/manual/render.py
node scripts/manual/export-pdf.mjs
python3 scripts/manual/verify.py
```

Para exportar con un Chrome ya instalado, definir `CHROME_EXECUTABLE` con la ruta del ejecutable. El HTML también permite imprimir desde el navegador.

Al actualizar una pantalla, revisar sus pasos, permisos, mensajes y capturas. Regenerar HTML y PDF y comprobar enlaces, búsqueda, ampliación, vista angosta e impresión. Revisar que ninguna captura incluya datos reales ni enlaces de autenticación con tokens. No hace falta levantar el backend para regenerar los documentos con las imágenes existentes.

## Procedencia de las capturas

Esta entrega separa el material preparado originalmente en [backend#221](https://github.com/IngenieriaParaLaAlimentacion/backend/pull/221). Las capturas históricas conservan los datos ficticios del escenario Semillas del Sur y su fecha del 25/10/2026. Trasladarlas a este repositorio no implica que se hayan vuelto a ejecutar contra la revisión actual.

| Componente | Revisión de la captura |
|---|---|
| backend | `99a1acbfee78b021558155bbae13c2aa882b6bf2` |
| app_mobile | `b856633c35da5d452ef7a2dffb015bd6c55bf795` |

El [inventario](inventario.json) conserva las pantallas de esta plataforma, los enlaces a las revisiones exactas y los registros de captura. Antes de una edición nueva, actualizar esas evidencias junto con los ejemplos y las imágenes, sin atribuir capturas históricas a otro dataset o revisión.

## Comprobaciones y limitaciones

Las capturas Android verifican pantallas y varios recorridos con persistencia: medición sin conexión y sincronización, encuestas e ingresos/egresos. No constituyen una prueba exhaustiva de todas las validaciones y permisos.

La incompatibilidad histórica del campo `needs_review` se corrigió en app_mobile#97. La guía ya no la presenta como una limitación vigente. El reconocimiento real de imágenes, el proveedor de IA, el almacenamiento externo, la entrega de correos y las notificaciones siguen requiriendo comprobación con sus servicios configurados.

## Actualización del 29/10/2026

El texto se contrastó con `develop` de app_mobile, revisión `7f787be685ba1ed764feba8c1a385ca04aef7da2`. Se incorporaron los selectores de fecha y hora de mediciones (#100), el recorrido de recetas dentro de Alimentos (#96) y las correcciones de recibos y mensajes de IA (#97). #101 modifica la cobertura de CI y no agrega pasos de usuario.

Las 17 capturas nuevas o renovadas se identifican individualmente en `inventario.json`. Se ejecutó la app Android de esa revisión en el AVD aislado `IPA_Manual`, con un servidor HTTP local de datos ficticios. Las respuestas de recetas son fijas: las capturas verifican navegación, formularios y revisión visual; no prueban la generación por IA ni movimientos persistidos en el backend. Las imágenes no renovadas conservan su fecha y procedencia del 25/10/2026.

#103 incorpora el proyecto ejecutable de iOS. La preparación técnica y distribución se describen en [desarrollo local iOS](../ios-desarrollo-local.md) y [traspaso](../traspaso-app-mobile.md). Esta actualización del manual conserva instrucciones y capturas de Android; no afirma validación funcional en iOS ni disponibilidad en las tiendas.
