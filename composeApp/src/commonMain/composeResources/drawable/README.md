# Iconos vectoriales

Esta carpeta contiene los iconos MingCute como Android Vector Drawables XML. El código compartido los usa mediante los accesores generados `Res.drawable.ic_mingcute_<nombre>`.

## Usar un icono existente

```kotlin
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_home_line
import org.jetbrains.compose.resources.vectorResource

Icon(
	imageVector = vectorResource(Res.drawable.ic_mingcute_home_line),
	contentDescription = "Inicio",
	tint = AlTheme.colors.textMuted,
)
```

También se pueden cargar con `painterResource`. Usá los colores de `AlTheme` para aplicar el tinte. Los XML son monocromos, con `android:fillColor="#FF000000"`.

## Agregar un icono

Revisá primero si ya existe un recurso adecuado en esta carpeta. La fuente es [MingCute](https://github.com/mingcute-design/mingcute-icons), con licencia Apache-2.0.

El importador local está en `.claude/skills/add-mingcute-icon/driver.mjs`, relativo a la raíz del repositorio. Ese script espera la estructura histórica `svg/<categoría>/<nombre>.svg`. El repositorio de MingCute actualmente publica sus fuentes bajo `assets/svg/`, por lo que las descargas del importador requieren actualizar esa integración antes de usarla con el upstream actual.

Para convertir un SVG descargado, guardalo como `.context/nombre.svg` y ejecutá desde la raíz del repositorio, reemplazando `nombre` por el nombre del icono:

```bash
npx --yes svg2vectordrawable@latest -i .context/nombre.svg -o .context
mv .context/nombre.xml composeApp/src/commonMain/composeResources/drawable/ic_mingcute_nombre.xml
```

La conversión requiere Node.js, npm y acceso al registro de paquetes. Revisá el XML generado:

- Conservá el tamaño del viewport del SVG.
- Eliminá cualquier path transparente usado solamente como rectángulo de encuadre. No lo conviertas en un relleno visible.
- Usá `android:fillColor="#FF000000"` en los paths visibles y aplicá el color desde Compose.

Compilá nuevamente para generar el accesor `Res.drawable.*` y revisá el icono en la pantalla que lo utiliza.
