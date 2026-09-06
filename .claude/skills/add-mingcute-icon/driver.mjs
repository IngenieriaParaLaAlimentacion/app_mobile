#!/usr/bin/env node
// Deterministic MingCute -> Android Vector Drawable importer for this project.
//
// Usage:
//   node driver.mjs <icon_name> [category]
//   node driver.mjs eye_line system
//   node driver.mjs eye_close_line          # auto-discovers the category
//
// <icon_name> is the MingCute SVG basename WITHOUT extension, e.g. `eye_line`,
// `check_fill`, `user_3_line`. The trailing `_line` / `_fill` is the variant and
// is kept verbatim, matching this project's `ic_mingcute_<name>` convention.
//
// What it does, deterministically:
//   1. Fetches the source SVG from Richard9394/MingCute (raw.githubusercontent).
//   2. Converts to an Android Vector Drawable via `npx svg2vectordrawable`.
//   3. Strips MingCute's transparent 24x24 bounding <path> (the one with no
//      fillColor) and forces every remaining fill to #FF000000 so the icon is
//      tinted at the call site via Icon(tint = ...) / ColorFilter.tint(...).
//   4. Writes composeResources/drawable/ic_mingcute_<icon_name>.xml.
//
// Same input -> byte-identical output. Re-running overwrites in place.

import { execFileSync } from "node:child_process";
import { mkdtempSync, writeFileSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const REPO = "Richard9394/MingCute";
// Pinned: v3.0.0 restructured the repo to assets/svg/core/{regular,filled}/<category>/<name>.svg,
// dropping the flat svg/<category>/<name>_{line,fill}.svg layout every icon in this project was
// imported from. v2.97 is the last release with that layout, so pinning it keeps `ic_mingcute_*`
// names meaningful and every existing drawable byte-identically regenerable.
const REF = "v2.97";
const RAW = `https://raw.githubusercontent.com/${REPO}/${REF}/svg`;
const API_TREE = `https://api.github.com/repos/${REPO}/git/trees/${REF}?recursive=1`;

// Resolve composeResources/drawable relative to this script (skill lives at
// <unit>/.claude/skills/add-mingcute-icon/, drawables at <unit>/composeApp/...).
const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const UNIT = join(SCRIPT_DIR, "..", "..", "..");
const DRAWABLE_DIR = join(
  UNIT,
  "composeApp/src/commonMain/composeResources/drawable",
);

function die(msg) {
  console.error(`✗ ${msg}`);
  process.exit(1);
}

async function fetchText(url, accept) {
  const res = await fetch(url, accept ? { headers: { Accept: accept } } : undefined);
  if (!res.ok) return null;
  return await res.text();
}

async function resolveCategory(name) {
  const tree = await fetchText(API_TREE, "application/vnd.github+json");
  if (!tree) die("could not query MingCute repo tree to auto-discover category");
  const want = `${name}.svg`;
  const hit = JSON.parse(tree).tree.find(
    (e) => e.path.startsWith("svg/") && e.path.endsWith(`/${want}`),
  );
  if (!hit) {
    die(
      `icon "${name}" not found in any category. Browse names at ` +
        `https://github.com/${REPO}/tree/${REF}/svg`,
    );
  }
  return hit.path.split("/")[1]; // svg/<category>/<name>.svg
}

// Deterministic clean: keep only <path> elements that carry a fill, and pin that
// fill to #FF000000. MingCute's bounding rectangle path has no fillColor.
function cleanVectorXml(xml) {
  const paths = [...xml.matchAll(/<path\b[^>]*\/>/g)].map((m) => m[0]);
  const kept = paths
    .filter((p) => /android:fillColor=/.test(p))
    .map((p) => p.replace(/android:fillColor="[^"]*"/, 'android:fillColor="#FF000000"'));
  if (kept.length === 0) die("conversion produced no fillable paths — unexpected SVG shape");
  const body = kept.map((p) => "    " + p).join("\n");
  return (
    `<vector xmlns:android="http://schemas.android.com/apk/res/android"\n` +
    `    android:width="24dp"\n` +
    `    android:height="24dp"\n` +
    `    android:viewportWidth="24"\n` +
    `    android:viewportHeight="24">\n` +
    `${body}\n` +
    `</vector>\n`
  );
}

async function main() {
  const [name, categoryArg] = process.argv.slice(2);
  if (!name) die("usage: node driver.mjs <icon_name> [category]");
  if (!/^[a-z0-9_]+$/.test(name)) die(`invalid icon name "${name}" (use lowercase a-z0-9_)`);

  const category = categoryArg ?? (await resolveCategory(name));
  const svg = await fetchText(`${RAW}/${category}/${name}.svg`);
  if (!svg) die(`SVG not found at svg/${category}/${name}.svg`);

  const work = mkdtempSync(join(tmpdir(), "mingcute-"));
  const svgPath = join(work, `${name}.svg`);
  const vdPath = join(work, `${name}.xml`);
  writeFileSync(svgPath, svg);

  execFileSync("npx", ["--yes", "svg2vectordrawable@latest", "-i", svgPath, "-o", vdPath], {
    stdio: ["ignore", "ignore", "inherit"],
  });

  const cleaned = cleanVectorXml(readFileSync(vdPath, "utf8"));
  rmSync(work, { recursive: true, force: true });

  const outName = `ic_mingcute_${name}.xml`;
  const outPath = join(DRAWABLE_DIR, outName);
  writeFileSync(outPath, cleaned);

  console.log(`✓ wrote composeApp/src/commonMain/composeResources/drawable/${outName}`);
  console.log(`  source: svg/${category}/${name}.svg`);
  console.log(`  use:    Res.drawable.ic_mingcute_${name}  (rebuild to generate the accessor)`);
}

main();
