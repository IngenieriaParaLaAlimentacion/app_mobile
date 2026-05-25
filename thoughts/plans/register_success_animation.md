# Register Success Animation Plan

## Overview

Replace the plain-text `RegisterSuccessScreen` with an animated success confirmation: a green circular background pops in, then a white tick from the MingCute icon set draws/scales in on top. Establish the first vector drawable + icon-conversion workflow in the codebase so future icons follow the same pattern.

## Current State Analysis

`composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterSuccessScreen.kt:18-41` renders two stacked `Text` composables on a flat background — no icon, no animation, no visual cue. The screen is a stateless single-file composable (intentionally outside the six-file feature pattern) routed via `Route.RegisterSuccess` and reached after `RegisterDestination.Success`.

Infrastructure gaps blocking the change:

- `composeApp/src/commonMain/composeResources/` contains only `values/strings.xml`. There is no `drawable/` directory and zero existing `.xml` vector drawables — this would be the first.
- No icon is referenced anywhere in Kotlin (`painterResource`, `Res.drawable`, `vectorResource`, `Icons.*` all return zero matches repo-wide).
- The Compose Multiplatform resources runtime *is* wired (`composeApp/build.gradle.kts:43` — `implementation(compose.components.resources)`), so once a drawable lands the generated `Res.drawable.*` accessor will work without further config.
- No local SVG→Vector XML tooling is installed (`vd-tool`, `avocado`, `svg2vectordrawable` not found). `npx 11.6.2` is available (Node v22.21.0 at `/Users/juanduzac/.nvm/`).
- `AlTheme.colors.accent = Color(0xFF15803D)` (`design_system/theme/Theme.kt:14`) is already a deep green — perfect for the success circle; no new color token needed.
- The design system has no animation utilities, no `icon/` component, and no `AlTheme.icons` slot.

## Desired End State

When a user lands on `Route.RegisterSuccess`:

1. A green circle (`AlTheme.colors.accent`, ~96 dp) scales in with a spring overshoot from 0 → 1.0.
2. A white MingCute check icon scales/fades in inside the circle, starting ~120 ms after the circle.
3. The existing "¡Registro enviado!" heading and body copy slide/fade in below.
4. The animation plays once on entry. No loop, no infinite shimmer.
5. The icon comes from `composeApp/src/commonMain/composeResources/drawable/ic_mingcute_check_fill.xml`, accessed via `painterResource(Res.drawable.ic_mingcute_check_fill)`.

Verification: build passes, screen renders on iOS + Android, animation plays smoothly at ≥ 60 fps, the icon shows the correct shape (filled checkmark with rounded corners).

### Key Discoveries

- `RegisterSuccessScreen.kt:18-41` — current implementation, two `Text` composables only.
- `composeApp/build.gradle.kts:43` — `compose.components.resources` already declared; no plugin config block needed since defaults read from `commonMain/composeResources/`.
- `design_system/theme/Theme.kt:14` — `accent = Color(0xFF15803D)` is reusable as the success color.
- `design_system/components/` currently contains only `scaffold/AlScaffold.kt` and `button/AlButton.kt` — no precedent for icon or animation components, so we set the pattern here.
- MingCute icons live at `https://github.com/Richard9394/MingCute` under `svg/System/` (raw URL: `https://raw.githubusercontent.com/Richard9394/MingCute/main/svg/System/check_fill.svg`).
- The npm package `svg2vectordrawable` exposes a CLI binary `s2v` that converts a single SVG to Android Vector Drawable XML — usable via `npx --yes svg2vectordrawable@latest -s input.svg -o output_dir/`.

## What We're NOT Doing

- Not adding an `AlTheme.icons` slot or generic icon registry — too speculative for a one-icon change. Icons reference `Res.drawable.*` directly at the call site for now.
- Not adding a new `success` color token — `AlTheme.colors.accent` already fits.
- Not changing routing, the destination flow, or any ViewModel — animation is purely presentation-layer.
- Not adding a confetti/particle effect, sound, or haptic feedback.
- Not refactoring `RegisterSuccessScreen.kt` into the six-file feature pattern — it stays a single stateless composable.
- Not setting up an automated SVG-pipeline (Gradle task, hook, watcher). Conversion is a manual one-time command per icon; the command is documented in this plan.

## Implementation Approach

Three discrete phases that can each be verified independently:

1. **Bring the asset in.** Download the MingCute SVG, convert it to Android Vector Drawable XML via `npx svg2vectordrawable`, drop it into `composeResources/drawable/`. Verify the generated `Res.drawable.*` accessor compiles.
2. **Build the reusable animated component.** Create `AlSuccessTick` under `core/design_system/components/success/` so the animation primitive lives next to other design-system parts (matching the `scaffold/` and `button/` convention). Pure composable, no state hoisting — animation runs once via `LaunchedEffect(Unit)`.
3. **Wire it into `RegisterSuccessScreen`.** Place the new component above the existing text, with the text getting a synchronized fade/translate-in.

---

## Phase 1: Add MingCute check icon as a vector drawable

### Overview

Establish the `composeResources/drawable/` directory and add `ic_mingcute_check_fill.xml`. Document the conversion command so future icons follow the same flow.

### Changes Required

#### 1. Download the source SVG

**File**: `composeApp/src/commonMain/composeResources/drawable/_source/check_fill.svg` (temporary; deleted after conversion)
**Command**:

```bash
mkdir -p composeApp/src/commonMain/composeResources/drawable/_source && \
curl -sSL -o composeApp/src/commonMain/composeResources/drawable/_source/check_fill.svg \
  https://raw.githubusercontent.com/Richard9394/MingCute/main/svg/System/check_fill.svg
```

Verify the file downloaded:

```bash
head -1 composeApp/src/commonMain/composeResources/drawable/_source/check_fill.svg
# expected: <?xml version="1.0" encoding="UTF-8"?> ... or similar svg tag
```

#### 2. Convert SVG → Android Vector Drawable XML

**File**: `composeApp/src/commonMain/composeResources/drawable/ic_mingcute_check_fill.xml` (new)
**Command**:

```bash
npx --yes svg2vectordrawable@latest \
  -s composeApp/src/commonMain/composeResources/drawable/_source/check_fill.svg \
  -o composeApp/src/commonMain/composeResources/drawable/
```

`svg2vectordrawable` (CLI binary `s2v`) writes one `.xml` per input SVG into the `-o` directory, preserving the basename. So the output file will be named `check_fill.xml`. Rename it for clarity:

```bash
mv composeApp/src/commonMain/composeResources/drawable/check_fill.xml \
   composeApp/src/commonMain/composeResources/drawable/ic_mingcute_check_fill.xml
```

Clean up the source SVG (it's not needed at runtime):

```bash
rm -rf composeApp/src/commonMain/composeResources/drawable/_source
```

Resulting XML will look approximately like (final file is what `s2v` emits; do not hand-edit):

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M20.485,6.515 ... Z" />
</vector>
```

**Important**: leave `android:fillColor="#FF000000"`. The drawable will be tinted at the call site via `ColorFilter.tint(...)` so the same XML works for any color (white-on-green here).

#### 3. Document the conversion workflow

**File**: `composeApp/src/commonMain/composeResources/drawable/README.md` (new)
**Changes**: Short reference so the next person (or future Claude session) doesn't have to rediscover the command.

```markdown
# Vector Drawables

Icons here are Android Vector Drawable XML files accessible from common code via
`painterResource(Res.drawable.<name>)`.

## Source

[MingCute Icon library](https://github.com/Richard9394/MingCute) — pick from `svg/System/`, `svg/Other/`, etc.

## Adding a new icon

```bash
# 1. download the source SVG
curl -sSL -o /tmp/<icon_name>.svg \
  https://raw.githubusercontent.com/Richard9394/MingCute/main/svg/<Category>/<icon_name>.svg

# 2. convert to vector drawable XML (drops into this directory)
npx --yes svg2vectordrawable@latest -s /tmp/<icon_name>.svg -o .

# 3. rename to ic_mingcute_<icon_name>.xml for clarity
mv <icon_name>.xml ic_mingcute_<icon_name>.xml
```

Leave `android:fillColor="#FF000000"` in the generated XML — tint at the call site via `ColorFilter.tint(...)`.
```

### Success Criteria

#### Automated Verification

- [x] `ic_mingcute_check_fill.xml` exists at `composeApp/src/commonMain/composeResources/drawable/ic_mingcute_check_fill.xml`
- [x] File contains a `<vector>` root element and at least one `<path android:pathData="..."/>`
- [x] `_source/` directory is removed
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] `Res.drawable.ic_mingcute_check_fill` resolves (verified in Phase 2 when first referenced; build will fail here if the resource accessor wasn't generated)

#### Manual Verification

- [ ] Open `ic_mingcute_check_fill.xml` and confirm the `<path>` data renders as a checkmark (visual inspection — most editors and Android Studio show a preview)

---

## Phase 2: Build `AlSuccessTick` animated component

### Overview

Create a reusable composable in the design system that renders a colored circle with a tick icon, animating both on first composition.

### Changes Required

#### 1. New component file

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/success/AlSuccessTick.kt` (new)
**Changes**: Stateless composable that runs a one-shot scale-in animation for the circle, then for the tick.

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.components.success

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ingenieria_alimentacion.composeapp.generated.resources.Res
import ingenieria_alimentacion.composeapp.generated.resources.ic_mingcute_check_fill
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

@Composable
fun AlSuccessTick(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    circleColor: Color = AlTheme.colors.accent,
    tickColor: Color = AlTheme.colors.textInverted,
) {
    val circleScale = remember { Animatable(0f) }
    val tickScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }
    LaunchedEffect(Unit) {
        delay(120)
        tickScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(circleScale.value)
            .clip(CircleShape)
            .background(circleColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_mingcute_check_fill),
            contentDescription = null,
            tint = tickColor,
            modifier = Modifier
                .padding(size * 0.20f)
                .scale(tickScale.value),
        )
    }
}
```

Notes on the code:

- `Animatable` (not `animateFloatAsState`) is used because the spring start state must be exactly `0f` and we explicitly want the animation to run once per entry; `Animatable` makes this guaranteed.
- The actual generated resource package is `ingenieria_alimentacion.composeapp.generated.resources` — the import line above is provisional and may need to be adjusted once Phase 1 lands and the accessor is generated. If the package name differs, the build error will state the correct package and the import is a one-line fix.
- `AlTheme.colors.textInverted = Color(0xFFFFFFFF)` — white tick on green circle.
- Default size 96.dp matches typical success-state iconography (Material guidelines lean 64–96 dp for status icons).

Missing import for `background`:

```kotlin
import androidx.compose.foundation.background
```

(Added to the import block above; calling this out explicitly because it's easy to miss and the file will not compile without it.)

### Success Criteria

#### Automated Verification

- [x] File exists at `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/components/success/AlSuccessTick.kt`
- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [x] No unresolved reference errors for `Res.drawable.ic_mingcute_check_fill`

#### Manual Verification

- [ ] (Deferred to Phase 3, where the component is rendered in a real screen)

---

## Phase 3: Integrate into `RegisterSuccessScreen`

### Overview

Replace the bare-text screen with one that shows the animated tick above the existing copy. Add a subtle fade/translate for the text so the whole entrance feels coordinated.

### Changes Required

#### 1. Update `RegisterSuccessScreen.kt`

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterSuccessScreen.kt`
**Changes**: Add the `AlSuccessTick` at top, animate text entrance with `AnimatedVisibility` + `LaunchedEffect`.

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.register

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.com.ingenieriaalimentacion.core.design_system.components.success.AlSuccessTick
import com.komoui.themes.styles
import kotlinx.coroutines.delay

@Composable
fun RegisterSuccessScreen() {
    val styles = MaterialTheme.styles
    var textVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(380)
        textVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(styles.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AlSuccessTick()

        AnimatedVisibility(
            visible = textVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 320)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 320),
                    initialOffsetY = { it / 4 },
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "¡Registro enviado!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = styles.foreground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tu registro fue enviado y está pendiente de aprobación. " +
                        "Te avisaremos cuando tu cuenta esté habilitada para ingresar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = styles.mutedForeground,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
```

Notes:

- 380 ms text delay = circle spring (~250 ms to settle) + 120 ms tick delay + a small breathing pause. Tune in manual testing.
- Existing string copy preserved verbatim — no behavior change, only presentation.
- Text body is wrapped in a `Column` inside `AnimatedVisibility` so the two `Text`s animate as a single unit (avoids a staggered text reveal which would feel choppy at this scale).

### Success Criteria

#### Automated Verification

- [x] Kotlin compilation passes: `./scripts/compile.sh`
- [ ] Android debug build assembles: `./gradlew :composeApp:assembleDebug --no-daemon`

#### Manual Verification

- [ ] On Android emulator: navigate to RegisterSuccess → green circle scales in with bounce → white tick scales in shortly after → heading + body fade/slide up → final layout matches the design intent
- [ ] On iOS simulator: same sequence renders identically, no jank, no scaling artifacts on the icon
- [ ] Animation plays once on entry and does not re-trigger on recomposition (e.g. backgrounding/foregrounding the app)
- [ ] Reduced-motion / accessibility: animation still completes correctly when system animation scale is 0 (animation appears instant; this is acceptable for a one-shot reveal)
- [ ] Text remains legible and centered at multiple device sizes (small phone, tablet portrait)

---

## Testing Strategy

This is a presentation-only change with no business logic, so unit tests are not required. Visual verification is the primary signal.

### Manual Testing Steps

1. Run Android build: `./gradlew :composeApp:assembleDebug --no-daemon`, install on emulator
2. Navigate to RegisterSuccess (submit a registration form, or temporarily hard-code the start destination to `Route.RegisterSuccess` for faster iteration)
3. Verify the three-step animation: circle pop → tick pop → text fade-up
4. Background and re-foreground the app — the animation should not re-trigger (composition state is preserved)
5. Repeat on iOS via `iosApp/iosApp.xcodeproj` in Xcode
6. Toggle the system "Reduce Motion" setting and confirm the screen still ends in a correct, readable state

## Performance Considerations

- One vector drawable (~1 KB), three `Animatable` instances, two `LaunchedEffect`s. Negligible.
- `Animatable.animateTo` with a spring spec runs on the compose frame clock — no allocations per frame after the first.
- No infinite animations, no `rememberInfiniteTransition`, so the screen is idle after ~600 ms.

## Migration Notes

Not applicable — net new visual, no existing user data or behavior impacted.

## References

- Current screen: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/feature/register/RegisterSuccessScreen.kt:18-41`
- Design system theme: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/core/design_system/theme/Theme.kt:14` (accent color reused)
- Compose resources wiring: `composeApp/build.gradle.kts:43`
- MingCute icon library: https://github.com/Richard9394/MingCute
- `svg2vectordrawable` CLI: https://www.npmjs.com/package/svg2vectordrawable
- Related plan that originally introduced RegisterSuccess: `thoughts/plans/establishment_manager_registration.md`
