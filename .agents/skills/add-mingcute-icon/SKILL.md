---
name: add-mingcute-icon
description: Add, import, or create a new MingCute icon as an Android Vector Drawable in composeResources. Use when someone asks to add an icon, import a MingCute icon, create a vector drawable, or needs an ic_mingcute_* resource for a Compose component.
---

# Add a MingCute icon

This project renders icons from the [MingCute](https://github.com/Richard9394/MingCute)
set as Android Vector Drawables under
`composeApp/src/commonMain/composeResources/drawable/`, referenced from common
code via `Res.drawable.ic_mingcute_<name>`.

Adding one by hand is fiddly (the SVG ships a transparent bounding path and a
hardcoded brand fill that both have to be removed). **Don't do it by hand — run
the driver.** It fetches, converts, cleans, names, and writes the file
deterministically.

Paths below are relative to the unit root (`app_mobile/`).

## Prerequisites

`node` (v22 here) with network access to `raw.githubusercontent.com` and
`api.github.com`. No global install — the driver invokes
`npx --yes svg2vectordrawable@latest` itself.

## Run (agent path)

Pick the MingCute SVG basename (with its `_line` / `_fill` variant suffix) from
<https://github.com/Richard9394/MingCute/tree/main/svg>, then:

```bash
# auto-discover the category:
node .Codex/skills/add-mingcute-icon/driver.mjs eye_close_line

# or pass the category explicitly (skips the GitHub tree lookup):
node .Codex/skills/add-mingcute-icon/driver.mjs eye_line system
```

Output (verified this session):

```
✓ wrote composeApp/src/commonMain/composeResources/drawable/ic_mingcute_eye_line.xml
  source: svg/system/eye_line.svg
  use:    Res.drawable.ic_mingcute_eye_line  (rebuild to generate the accessor)
```

The file name is always `ic_mingcute_<svg_basename>.xml`. Re-running overwrites
in place and produces byte-identical output for the same icon.

## Use the icon in Compose

After running the driver, **rebuild once** so the Compose Resources accessor is
generated; until then the IDE shows `Res.drawable.ic_mingcute_<name>` as
unresolved (expected, not a code error). Then:

```kotlin
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_eye_line
import org.jetbrains.compose.resources.vectorResource // or painterResource

Icon(
    imageVector = vectorResource(Res.drawable.ic_mingcute_eye_line),
    contentDescription = "...",
    tint = MaterialTheme.styles.mutedForeground, // fill is #FF000000; always tint
)
```

Note the resource package is `ingenieria_alimentacion.composeapp.generated.resources`
(underscores), not the app's source package `ar.com.ingenieriaalimentacion`.

`AlPasswordInput.kt` and `AlSuccessTick.kt` are existing call sites to copy from.

## Verify it worked

```bash
# regenerating an existing icon must be byte-identical (determinism check):
node .Codex/skills/add-mingcute-icon/driver.mjs eye_line system
git diff --stat composeApp/src/commonMain/composeResources/drawable/ic_mingcute_eye_line.xml
# ^ no output = unchanged = good
```

## Gotchas

- **Variant is part of the name, not a flag.** MingCute files are
  `eye_line.svg`, `check_fill.svg`, `user_3_line.svg`. Pass the whole basename;
  the driver keeps `_line` / `_fill` verbatim to match the
  `ic_mingcute_<name>` convention. There is **no `eye_off`** — the hidden state
  is `eye_close`.
- **The README in the drawable dir points at `mingcute-design/mingcute-icons`,
  which 404s.** The real source is `Richard9394/MingCute` (same set, Apache-2.0).
  The driver already uses the working repo.
- **Fill is forced to `#FF000000`.** The icon is monochrome and meant to be
  tinted at the call site via `Icon(tint = ...)` / `ColorFilter.tint(...)`.
  Don't bake colors into the drawable.
- **MingCute's transparent 24×24 bounding path is stripped automatically** (the
  driver keeps only `<path>` elements that carry a fill). If you ever convert by
  hand, you must delete that path yourself or the icon renders as a filled square.
- **`Icons.Filled.*` (Material) is not the house style.** Use MingCute. An eye
  toggle on Material icons was explicitly rejected in favor of these.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `icon "<x>" not found in any category` | Wrong basename. Browse <https://github.com/Richard9394/MingCute/tree/main/svg> for the exact file name (include the `_line`/`_fill` suffix). |
| `Res.drawable.ic_mingcute_<name>` unresolved in IDE | Rebuild the project — the accessor is codegen'd from `composeResources/`. Not a code error. |
| `conversion produced no fillable paths` | The SVG had an unexpected shape (e.g. stroke-only). Inspect the source SVG; this set is fill-based, so a miss usually means a wrong/renamed icon. |
