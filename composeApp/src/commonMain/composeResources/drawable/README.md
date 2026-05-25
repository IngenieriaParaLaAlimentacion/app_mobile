# Vector Drawables

Icons here are Android Vector Drawable XML files accessible from common code via
`painterResource(Res.drawable.<name>)`.

## Source

[MingCute Icon library](https://github.com/mingcute-design/mingcute-icons) — pick from `svg/system/`, `svg/file/`, `svg/editor/`, etc. (folder names are lowercase).

## Adding a new icon

```bash
# 1. download the source SVG (replace <category> and <icon_name>)
curl -sSL -o /tmp/<icon_name>.svg \
  https://raw.githubusercontent.com/mingcute-design/mingcute-icons/main/svg/<category>/<icon_name>.svg

# 2. convert to vector drawable XML (drops into this directory)
npx --yes svg2vectordrawable@latest -i /tmp/<icon_name>.svg -o .

# 3. rename to ic_mingcute_<icon_name>.xml for clarity
mv <icon_name>.xml ic_mingcute_<icon_name>.xml
```

After conversion:

- MingCute SVGs include a 24x24 outer-rectangle path used as a sizing trick — delete it, the viewport attributes already set the size.
- Replace any brand fill color with `android:fillColor="#FF000000"` and tint at the call site via `Icon(tint = ...)` or `ColorFilter.tint(...)`.
