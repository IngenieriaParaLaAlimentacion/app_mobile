# Medición History — UX/UI Rework Implementation Plan

## Overview

Fix two problems on the **Historial → Mediciones** segment, reported by the user and
confirmed by code analysis:

1. **Mixed language** — the clinical-alert pill renders raw English/enum strings
   (`"BMI · high"`) on an otherwise Spanish screen, and demotes the meaningful Spanish
   text to a muted secondary line.
2. **Inverted information hierarchy** — operational metadata (the colored sync badge)
   is the loudest element, while the actual clinical reading (IMC + nutritional alert)
   is rendered as a throwaway caption. Identity, measurement, clinical result, and
   actions are flattened into seven undifferentiated vertical bands with no grouping.

The rework localizes the alert vocabulary against the **canonical backend enums**,
re-ranks the row so the clinical reading anchors it, gives each row a discrete card
surface, and demotes the destructive **Eliminar** action behind a confirmation.

All changes are in the **presentation layer** plus two small additive design-system
components. No domain, data, DTO, or backend changes are required.

## Current State Analysis

- **The row's localization seam is `MeasurementUpload.toUi()`**
  (`presentation/feature/measurement/uploads/MeasurementUploadUi.kt:31-78`). It already
  localizes sync-state → Spanish badge labels and builds the `subtitle`. The alert
  mapping at `:70-78` is the single English leak:
  ```kotlin
  label = "$alertType · $severity"   // "overweight · high" → shown verbatim
  ```
  `alertType` / `severity` come straight from `AlertDto`
  (`data/measurement/dto/MeasurementResponse.kt:6-11`, `@SerialName("alert_type")`),
  persisted to `alertsJson` and decoded in
  `data/measurement/mapper/MeasurementUploadMapper.kt:61-64`.
- **The subtitle is a single run-on string**
  (`MeasurementUploadUi.kt:39-54`): `"DNI 42 · 70 kg · 172 cm · 21/06 18:30"` — an
  administrative ID, two clinical inputs, and a timestamp all at one muted caption rank.
- **The row composable** `MeasurementUploadRow`
  (`MeasurementUploadsContent.kt:55-147`) stacks: name+spinner+badge → subtitle →
  `bmiLabel` caption → per-alert `AlPill` + detail → inline `Reintentar`/`Eliminar`
  `AlButton`s → a hairline divider `Box`. The colored `AlStatusBadge` (`:97`) is the
  visual focal point; `bmiLabel` (`:100-106`) is a 12sp caption.
- **`Eliminar` is a full `Destructive` button on every synced row**
  (`:129-136`), equal weight to `Reintentar`, with no confirmation — easy mis-tap on a
  synced clinical record.
- **Canonical backend enums (confirmed by user, from `app/models/measurement_alert.py`
  / `app/schemas/measurement_schema.py`)** — persisted lowercase via `values_callable`:
  - `alert_type` ∈ { `severely_wasted`, `wasted`, `underweight`, `severe_underweight`,
    `overweight`, `obese` }
  - `severity` ∈ { `critical`, `high`, `medium` }
- **Design-system primitives available** (`core/design_system/`):
  - Reuse as-is: `AlPill` (tones Neutral/Success/Warning/Danger), `AlStatusBadge`,
    `AlButton` (`Default`/`Outline`/`Destructive`/`Secondary`), `AlConfirmDialog`
    (`components/dialog/AlConfirmDialog.kt`, `AlConfirmTone.Destructive`, `loading`,
    `cancelText = "Cancelar"`).
  - Tokens: colors `textBase`/`textMuted`/`accent`/`accentContainer`/`warning`/
    `warningContainer`/`danger`/`dangerContainer`/`border`/`surfaceBase`/`surfaceMuted`;
    typography `title`(28)/`body`(16)/`caption`(12) only; spacing `xs/sm/md/lg`;
    corners `sm`(4)/`md`(8)/`lg`(16); borders `thin`(1)/`medium`(2).
  - **Gaps to fill (additive):** no generic `AlCard` surface and no `AlIconButton`.
    KomoUI ships a raw `Card(modifier, radius)` primitive to wrap. Icons
    `ic_mingcute_delete` and `ic_mingcute_alert` exist (all MingCute fills are black →
    must pass `tint`).
- **Tests** `MeasurementUploadUiTest.kt` asserts the old subtitle shape (`:114-127`)
  and the old alert label `"BMI · high"` (`:95`) — both will be updated to the new
  structured model and Spanish labels. `MeasurementUploadsViewModelTest.kt` only tests
  newest-first ordering + retry/delete delegation and is unaffected.

## Desired End State

A synced measurement row reads (clinical reading anchored, Spanish throughout):

```
┌──────────────────────────────────────────────────────────────┐
│  Ana García                              ✓ Sincronizado   🗑   │  name leads; status quiet; trash icon
│  DNI 42                                                        │  identity grouped under name
│                                                                │
│  IMC 23,7  ·  Sobrepeso                       70 kg · 172 cm   │  clinical reading anchors; inputs recede
│                                               21/06 · 18:30    │
│                                                                │
│  ⬤ Sobrepeso                                                   │  ONE Spanish pill; tone = severity
└──────────────────────────────────────────────────────────────┘
```

A failed row keeps a loud red badge + a prominent **Reintentar** button; `Eliminar`
always lives behind the trash icon + `AlConfirmDialog`.

**Verification:** no English alert strings render anywhere; IMC/category is visually
heavier than the sync status; deleting any row requires confirmation; all common
compiles, Android assembles, and the updated unit tests pass.

### Key Discoveries
- Single localization seam already exists: `MeasurementUploadUi.toUi()` — all label
  logic stays there (`MeasurementUploadUi.kt:31-78`).
- `AlConfirmDialog` exists and is purpose-built for this delete confirmation
  (`Destructive` tone, `loading` flag) — no dialog to build.
- Only the 6 `alert_type` × 3 `severity` values are possible, so the dictionary is
  finite and exact; a defensive fallback still guards against future backend additions.
- `Delete`/`Retry` actions already exist (`MeasurementUploadsAction.kt`); the
  confirmation is pure local UI state, so the ViewModel is untouched.

## What We're NOT Doing
- No backend, DTO, domain-model, mapper, or DB-schema changes.
- No changes to the **Sujetos** segment (`subject/uploads/`) or the shared
  `HistoryHubScreen` header / `Sincronizar` button.
- No new dropdown/overflow "⋯" menu component — a single trash icon-button covers the
  one secondary action; a full menu is deferred (out of scope).
- No dark theme (the app has only `lightColors`).
- No per-segment count badges (`Mediciones · 2 pendientes`) — deferred (recommendation #8).
- No change to date semantics beyond the existing `dd/MM HH:mm` format (year omission
  unchanged).

## Implementation Approach

Four phases, ordered so the **bug fix ships first and standalone**: Phase 1 kills the
English with the smallest possible diff (mapper + test only, no layout change). Phases
2–4 are the hierarchy rework, each independently compilable and reviewable. Two tiny
additive DS components (`AlCard`, `AlIconButton`) are introduced where first needed and
reused.

**Confirmed Spanish dictionary** (used across all phases):

`alert_type` → label:
| Backend | Spanish |
|---|---|
| `severely_wasted` | Emaciación grave |
| `wasted` | Emaciación |
| `underweight` | Bajo peso |
| `severe_underweight` | Bajo peso grave |
| `overweight` | Sobrepeso |
| `obese` | Obesidad |

`severity` → tone (text dropped; tone encodes severity):
| Backend | Tone |
|---|---|
| `critical` | Danger |
| `high` | Danger |
| `medium` | Warning |
| _unknown_ | Neutral |

> Terminology note: these are RAE-correct WHO renderings ("grave", not the anglicism
> "severo"; "emaciación" for wasting). Trivially swappable in review if the team uses
> different in-house wording — they live in one `when` block.

---

## Phase 1: Spanish alert dictionary (kills the English)

### Overview
Replace the raw `"$alertType · $severity"` pill label with a localized Spanish
alert-type label, and derive the pill tone from the canonical 3-value severity. Drop
the English severity word entirely (tone carries it). Smallest diff that fixes the
reported bug; no layout change.

### Changes Required

#### 1. Alert localization
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/measurement/uploads/MeasurementUploadUi.kt`
**Changes**: Rewrite `ClinicalAlert.toUi()` and its severity matcher; add an
`alert_type` dictionary with a defensive fallback.

```kotlin
private fun ClinicalAlert.toUi() = ClinicalAlertUi(
    label = alertTypeLabel(alertType),
    detail = detail,
    tone = severityTone(severity),
)

/** Canonical backend `alert_type` (lowercase enum .value) → Spanish clinical label. */
private fun alertTypeLabel(raw: String): String = when (raw.trim().lowercase()) {
    "severely_wasted" -> "Emaciación grave"
    "wasted" -> "Emaciación"
    "underweight" -> "Bajo peso"
    "severe_underweight" -> "Bajo peso grave"
    "overweight" -> "Sobrepeso"
    "obese" -> "Obesidad"
    // Defensive: future/unknown codes render readably instead of leaking raw enums.
    else -> raw.trim().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Canonical backend `severity` (critical|high|medium) → pill tone; tone encodes severity. */
private fun severityTone(raw: String): AlPillTone = when (raw.trim().lowercase()) {
    "critical", "high" -> AlPillTone.Danger
    "medium" -> AlPillTone.Warning
    else -> AlPillTone.Neutral
}
```

#### 2. Tests
**File**: `composeApp/src/commonTest/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/measurement/uploads/MeasurementUploadUiTest.kt`
**Changes**: Update the `synced row with bmi and alerts` test (`:83-100`) to use real
enum values and assert Spanish labels + tones:

```kotlin
alerts = listOf(
    ClinicalAlert(alertType = "overweight", severity = "high", detail = "Sobrepeso"),
    ClinicalAlert(alertType = "underweight", severity = "medium", detail = "Vigilar"),
    ClinicalAlert(alertType = "obese", severity = "critical", detail = "Derivar"),
),
// ...
assertEquals("Sobrepeso", ui.alerts[0].label)
assertEquals(AlPillTone.Danger, ui.alerts[0].tone)   // high
assertEquals(AlPillTone.Warning, ui.alerts[1].tone)  // medium
assertEquals(AlPillTone.Danger, ui.alerts[2].tone)   // critical
```
Add a case asserting an unknown `alert_type` falls back to title-cased text and tone
Neutral for an unknown severity.

### Success Criteria

#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [ ] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] On a synced row with an alert, the pill reads e.g. "Sobrepeso" (no `overweight`/
      `high`/middot).
- [ ] Critical and high alerts render red; medium renders amber.
- [ ] No English text appears anywhere on the Mediciones segment.

---

## Phase 2: Clinical reading anchor + subtitle split

### Overview
Break the run-on subtitle into structured fields (identity / inputs / timestamp), and
promote IMC + a Spanish nutritional category to the row's visual anchor.

### Changes Required

#### 1. Structured UI model + derivation
**File**: `MeasurementUploadUi.kt`
**Changes**: Replace the single `subtitle` with discrete fields; add `bmiCategory`.

```kotlin
data class MeasurementUploadUi(
    val localId: String,
    val title: String,            // "Nombre Apellido"
    val identity: String,         // "DNI 42"
    val measurements: String,     // "70 kg · 172 cm"
    val measuredAtLabel: String,  // "21/06 18:30"
    val errorMessage: String?,    // shown only on Failed
    val badgeLabel: String,
    val badgeTone: AlBadgeTone,
    val isSynced: Boolean,        // drives quiet-status vs loud-badge in the UI
    val showSpinner: Boolean,
    val bmiValue: String?,        // "23,7" when Synced & bmi != null
    val bmiCategory: String?,     // primary alert's Spanish label, else "Normal"
    val alerts: List<ClinicalAlertUi>,
    val canRetry: Boolean,
    val canDelete: Boolean,
)
```
- `identity = "DNI $subjectDni"`, `measurements = "${formatKg(weightKg)} kg · ${formatCm(heightCm)} cm"`,
  `measuredAtLabel = formatMeasuredAt(measuredAt)`, `errorMessage = errorMessage.takeIf { syncState == Failed && !it.isNullOrBlank() }`.
- `bmiValue = bmi?.takeIf { synced }?.let { formatBmi(it) }`.
- `bmiCategory`: when synced & bmi != null → `alerts.firstOrNull()?.let { alertTypeLabel(it.alertType) } ?: "Normal"`.
  > "Normal" is a derived label when the server flags no alert on a present BMI — a
  > reassuring positive state for the field worker; documented as derived, not server-sent.

#### 2. Row layout — clinical block
**File**: `MeasurementUploadsContent.kt`
**Changes**: In `MeasurementUploadRow`, replace the single subtitle `Text` and the weak
`bmiLabel` line with: identity under the name (muted caption), then a clinical row —
IMC value emphasized (`body.copy(fontWeight = SemiBold)`, `textBase`) + category, with
inputs + date right-aligned and muted.

```kotlin
// Identity (under the title, grouped with the name)
Text(upload.identity, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)

// Clinical anchor (only when synced & bmi present)
if (upload.bmiValue != null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
            Text("IMC ${upload.bmiValue}",
                style = AlTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = AlTheme.colors.textBase)
            upload.bmiCategory?.let {
                Text("· $it", style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(upload.measurements, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
            Text(upload.measuredAtLabel, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
        }
    }
} else {
    // Non-synced: no BMI yet — show inputs + date as a single muted line
    Text("${upload.measurements} · ${upload.measuredAtLabel}",
        style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
}

upload.errorMessage?.let {
    Text(it, style = AlTheme.typography.caption, color = AlTheme.colors.danger)
}
```

#### 3. Tests
**File**: `MeasurementUploadUiTest.kt`
**Changes**: Replace the old subtitle assertions (`:114-127`) with field-level
assertions:
```kotlin
assertEquals("DNI 42", ui.identity)
assertTrue(ui.measurements == "70 kg · 172 cm")
assertTrue(Regex("""\d{2}/\d{2} \d{2}:\d{2}""").matches(ui.measuredAtLabel))
// synced, no alerts → "Normal"; synced + overweight alert → "Sobrepeso"
assertEquals("Normal", upload(SyncState.Synced, bmi = 23.7).toUi().bmiCategory)
// failed → errorMessage populated; synced → null even if present
assertEquals("boom", upload(SyncState.Failed, errorMessage = "boom").toUi().errorMessage)
assertNull(upload(SyncState.Synced, errorMessage = "boom").toUi().errorMessage)
```
Keep the decimal-format test (`:130-134`) by asserting against `measurements`; keep the
garbage-date fallback test (`:137-139`) against `measuredAtLabel`.

### Success Criteria

#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [ ] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`
- [ ] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] IMC value is visibly heavier than the sync status and the inputs.
- [ ] DNI sits with the name; weight/height/date read as supporting metadata.
- [ ] A synced row with no alert shows "IMC … · Normal"; a flagged row shows the category.
- [ ] Pending/Syncing rows (no BMI yet) show inputs + date on one muted line, no "IMC".

---

## Phase 3: Recede status + card surface

### Overview
Stop the colored badge from out-shouting the name: show a quiet "✓ Sincronizado" for
the common success state, reserve the loud `AlStatusBadge` for attention states
(Pending/Syncing/Failed). Give each row a discrete card surface and drop the bare
divider.

### Changes Required

#### 1. New `AlCard` surface (fills a DS gap)
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/card/AlCard.kt`
**Changes**: Generic single-slot surface wrapping KomoUI `Card`, `corners.lg`, thin
border, internal `md` padding.

```kotlin
@Composable
fun AlCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.border(
            AlTheme.borders.thin, AlTheme.colors.border,
            RoundedCornerShape(AlTheme.corners.lg),
        ),
        radius = AlTheme.corners.lg,
    ) {
        Column(Modifier.padding(AlTheme.spacing.md), content = content)
    }
}
```
> Verify the exact KomoUI `Card(modifier, radius) { }` signature at implementation time
> (compiled artifact); if `radius` differs, adjust — the wrapper isolates that detail.

#### 2. Quiet status + card in the row
**File**: `MeasurementUploadsContent.kt`
**Changes**:
- Wrap each row body in `AlCard`; remove the trailing divider `Box` (`:140-145`).
- In the list, space cards with `Arrangement.spacedBy(AlTheme.spacing.sm)` and add
  `contentPadding = PaddingValues(AlTheme.spacing.md)` on the `LazyColumn`.
- Status: when `upload.isSynced`, render a quiet inline indicator (accent ✓ +
  `Sincronizado` in `textMuted` caption); otherwise the existing `AlStatusBadge`.

```kotlin
if (upload.isSynced) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlTheme.spacing.xs)) {
        Icon(vectorResource(Res.drawable.ic_mingcute_check_line),
            contentDescription = null, tint = AlTheme.colors.accent,
            modifier = Modifier.size(14.dp))
        Text(upload.badgeLabel, style = AlTheme.typography.caption, color = AlTheme.colors.textMuted)
    }
} else {
    AlStatusBadge(text = upload.badgeLabel, tone = upload.badgeTone)
}
```
> If `ic_mingcute_check_line` is absent, add it via the `add-mingcute-icon` skill, or
> fall back to a 6dp accent dot `Box`.

### Success Criteria

#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [ ] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification:
- [ ] Rows read as discrete cards; no ragged divider list.
- [ ] Synced rows show a quiet ✓ Sincronizado that no longer dominates the name.
- [ ] Failed/Pending/Syncing still show the loud colored badge.
- [ ] Spacing/rhythm consistent on Android and iOS.

---

## Phase 4: Demote Eliminar behind a confirmation

### Overview
Replace the always-present `Destructive` Eliminar button with a quiet trash icon-button
that opens `AlConfirmDialog`. Keep `Reintentar` prominent on failed rows.

### Changes Required

#### 1. New `AlIconButton` (fills a DS gap)
**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/button/AlIconButton.kt`
**Changes**: Minimal tappable icon with a 40dp min touch target.

```kotlin
@Composable
fun AlIconButton(
    icon: DrawableResource,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AlTheme.colors.textMuted,
) {
    Box(
        modifier = modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(vectorResource(icon), contentDescription, Modifier.size(20.dp), tint = tint)
    }
}
```

#### 2. Trash icon + confirm dialog in the row
**File**: `MeasurementUploadsContent.kt`
**Changes**:
- Remove the inline Eliminar `AlButton` (`:129-136`); place an `AlIconButton`
  (`ic_mingcute_delete`, tint `danger`) in the name row's trailing edge, shown when
  `upload.canDelete`.
- Hold local pending-delete state in `MeasurementUploadsContent`:
  ```kotlin
  var pendingDelete by remember { mutableStateOf<MeasurementUploadUi?>(null) }
  ```
  On trash tap → `pendingDelete = upload`. Render once, below the list:
  ```kotlin
  AlConfirmDialog(
      open = pendingDelete != null,
      title = "Eliminar medición",
      description = pendingDelete?.let { "Se eliminará la medición de ${it.title}." } ?: "",
      confirmText = "Eliminar",
      cancelText = "Cancelar",
      confirmTone = AlConfirmTone.Destructive,
      onConfirm = {
          pendingDelete?.let { onAction(MeasurementUploadsAction.Delete(it.localId)) }
          pendingDelete = null
      },
      onDismiss = { pendingDelete = null },
  )
  ```
- Keep `Reintentar` as the only inline `AlButton` (`Outline`), shown when `canRetry`.

### Success Criteria

#### Automated Verification:
- [x] Common compiles: `./scripts/compile.sh`
- [ ] Android assembles: `./gradlew :composeApp:assembleDebug --no-daemon`
- [ ] Tests pass: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && ./gradlew :composeApp:testDebugUnitTest --no-daemon`

#### Manual Verification:
- [ ] Eliminar is a quiet trash icon, not a full red button.
- [ ] Tapping it opens the confirm dialog; Cancelar aborts; Eliminar removes the row.
- [ ] Reintentar remains prominent on failed rows.
- [ ] No way to delete a row without confirming.

---

## Testing Strategy

### Unit (commonTest)
- `MeasurementUploadUiTest` updated for the Spanish dictionary (Phase 1) and the
  structured model + `bmiCategory`/`errorMessage` (Phase 2). Cover: each `alert_type`
  → Spanish label; each `severity` → tone; unknown-code fallbacks; `Normal` derivation;
  error shown only on Failed.
- `MeasurementUploadsViewModelTest` unchanged (ordering + retry/delete delegation).

### Manual (Android + iOS simulator)
1. Capture measurements that trigger each `alert_type`/`severity`; confirm Spanish
   labels and correct pill tones, no English.
2. Confirm a synced no-alert row shows "Normal" and IMC anchors the row.
3. Walk a row through Pending → Syncing → Synced/Failed; verify quiet ✓ vs loud badge.
4. Delete a synced row → confirm dialog → Eliminar; and Cancelar path.
5. Verify card spacing/rhythm at small and large text sizes on both platforms.

## Performance Considerations
- Pure presentation re-layout; `LazyColumn` keying by `localId` is unchanged. Adding
  per-row `AlCard` surfaces is negligible. Confirm-dialog state is a single nullable
  hoisted in the content composable — no extra recomposition of list items beyond the
  tapped row.

## Migration Notes
- No data migration. `alertsJson` already persists `alert_type`/`severity` values; the
  rework only changes how they're displayed. Old rows render correctly because the
  dictionary maps the same canonical lowercase enum values.

## References
- Reported issue + analysis: this session (English alert strings + inverted hierarchy).
- Original build plan: `thoughts/plans/measurement_history_screen.md`.
- Localization seam: `presentation/feature/measurement/uploads/MeasurementUploadUi.kt:31-78`.
- Row composable: `presentation/feature/measurement/uploads/MeasurementUploadsContent.kt:55-147`.
- Confirm dialog: `core/design_system/components/dialog/AlConfirmDialog.kt`.
- Backend enums: `app/models/measurement_alert.py`, `app/schemas/measurement_schema.py`.
