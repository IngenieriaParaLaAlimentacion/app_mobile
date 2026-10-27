"""Check local links, figures, chapter coverage and accidental credential exports."""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / "docs/manual-usuario"


def main():
    errors, figures = [], set()
    for source in DOC.rglob("*.md"):
        content = source.read_text()
        for target in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)", content):
            if target.startswith(("http://", "https://", "#")):
                continue
            path = source.parent / target.split("#")[0]
            if not path.is_file():
                errors.append(f"{source.relative_to(DOC)}: missing {target}")
            if path.suffix == ".png":
                figures.add(path.resolve())
                audience = "app"
                prefix = {"app": "app-", "backoffice": "web-"}.get(audience)
                if prefix and not path.name.startswith(prefix):
                    errors.append(f"{source.relative_to(DOC)}: wrong audience {target}")
        if re.search(
            r"eyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]+\.|[?&]token=[^\s)]+", content
        ):
            errors.append(f"{source.relative_to(DOC)}: possible authentication token")
    inventory = json.loads((DOC / "inventario.json").read_text())
    for screen in inventory["screens"]:
        audience = "backoffice" if screen["repository"] == "backoffice" else "app"
        if not (DOC / screen["chapter"]).is_file():
            errors.append(f"Unmapped screen: {screen['source']}")
    for figure in figures:
        if figure.is_file() and figure.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
            errors.append(f"Invalid PNG: {figure}")
    if errors:
        raise SystemExit("\n".join(errors))
    print(
        f"PASS: links, {len(figures)} figures, "
        f"{len(inventory['screens'])} screen mappings; no token patterns"
    )


if __name__ == "__main__":
    main()
