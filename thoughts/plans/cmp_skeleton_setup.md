# Compose Multiplatform Skeleton Setup — Implementation Plan

## Overview

Bootstrap a new Compose Multiplatform (Android + iOS) app at `/Users/juanduzac/dev/ingenieria-alimentacion/app_mobile`, mirroring the skeleton patterns from `/Users/juanduzac/dev/extra` (Clean Architecture + MVVM + Koin + jetbrains-navigation-compose), stripped of all app-specific integrations (Supabase, Firebase, Mixpanel, MercadoPago, Wire, SQLDelight, BuildKonfig, product flavors). Ship a single working `Home` screen on Android and iOS as the baseline for future features.

## Current State Analysis

- Target directory `/Users/juanduzac/dev/ingenieria-alimentacion/app_mobile` exists as a fresh git repo containing only `README.md` and `.git/`.
- Source skeleton in `/Users/juanduzac/dev/extra` uses:
  - Gradle 8.10.0 (AGP), Kotlin 2.3.20, Compose Multiplatform 1.10.3, Koin 4.2.1, Ktor 3.4.2, jetbrains-lifecycle 2.10.0, jetbrains navigation-compose 2.9.2 (`gradle/libs.versions.toml:1-60`)
  - Java 11 target / JDK 21 to run Gradle (`scripts/compile.sh:2`)
  - Single `composeApp` module with `androidTarget`, `iosArm64`, `iosSimulatorArm64` (`composeApp/build.gradle.kts:39-54`)
  - Layered architecture under `composeApp/src/commonMain/kotlin/com/extra/marketplace/`: `data/`, `domain/`, `presentation/`, `core/`, `di/`, `app/`
  - DI graph: `InitKoin.kt` (`di/InitKoin.kt:10-30`) starts `platformModule` (`expect val`), `sharedModule`, `clientsModule`, `repositoriesModule`, `useCasesModule`, `viewModelsModule` plus app-specific modules; `KoinGlobalContext` (`di/KoinGlobalContext.kt:12-43`) is initialized once from Android `Application.onCreate` (`androidMain/.../ExtraApp.kt:64-66`) and from Swift via `doInitKoin()` exposed in `InitKoin.kt:34-38`
  - Navigation: `presentation/navigation/Route.kt` declares a sealed `@Serializable` `Route` with `@SerialName` subtypes; `app/AppNavHost.kt` calls per-feature `*Screen()` extension functions; `app/App.kt:100-106` wraps `AppNavHost` in `ExTheme`
  - Per-feature 6-file pattern (e.g. `presentation/feature/auth/sign_in/`): `SignInAction.kt` (sealed actions), `SignInDestination.kt` (sealed nav targets), `SignInNavigation.kt` (`navigateToX()` + `xScreen(NavGraphBuilder)` + internal `navigate(destination, navController)`), `SignInScreen.kt` (`SignInScreenRoot` collects state and observes `state.destination`, `SignInScreen` is stateless), `SignInState.kt` (data class), `SignInViewModel.kt` (`MutableStateFlow` + `onAction(action)` reducer)
  - Design system: `core/design_system/theme/Theme.kt` provides `ExTheme { content }` via `CompositionLocalProvider` for `Colors`, `Typography`, `Spacing`, `Corner`, `Border`; matching `Color.kt`, `Type.kt`, `Spacing.kt`, `Corner.kt`, `Border.kt`
  - Platform entries: Android `MainActivity.kt` (ComponentActivity, `setContent { App() }`) + `ExtraApp.kt` Application class; iOS `iosMain/.../app/MainViewController.kt` (`ComposeUIViewController { App() }`) consumed by Swift `iosApp/iosApp/iOSApp.swift` + `ContentView.swift` + `AppDelegate.swift`

### Key Discoveries

- The single-screen pattern requires **6 files per feature** plus a registration in `Route.kt` and `AppNavHost.kt` — the plan reproduces this exactly so future features can follow `composeApp/src/commonMain/kotlin/com/extra/marketplace/presentation/feature/auth/sign_in/` as a template.
- `KoinGlobalContext.initializeGlobalContext()` is the single iOS entry point and is invoked from Swift via `doInitKoin()` (`composeApp/src/commonMain/kotlin/com/extra/marketplace/di/InitKoin.kt:32-38`). The skeleton must keep this exact ObjC name so iOS Swift code can call it.
- The skeleton uses `expect val platformModule: Module` in `commonMain/.../di/Module.kt:40` with `actual` declarations in `androidMain/.../di/Modules.android.kt` and `iosMain/.../di/Modules.ios.kt`. We keep this even when both files are nearly empty for the skeleton — it's the documented extension point for platform code.
- Compose Multiplatform Resources are declared via `implementation(compose.components.resources)` plus a `composeApp/src/commonMain/composeResources/` directory; the skeleton initially ships with one string resource (`app_name`) so the `Res` API is available from day one without requiring a later restructuring.
- Java 11 JVM target but Java 21 to run Gradle is a hard requirement for Kotlin 2.3.20 + AGP 8.10 + Compose 1.10. `scripts/compile.sh` exports `JAVA_HOME=$(/usr/libexec/java_home -v 21)` before invoking `./gradlew`.

## Desired End State

A buildable Android+iOS app at `/Users/juanduzac/dev/ingenieria-alimentacion/app_mobile` where:

- `./scripts/compile.sh` (sets JDK 21, runs `:composeApp:compileCommonMainKotlinMetadata --no-daemon`) succeeds with zero warnings.
- `./gradlew :composeApp:assembleDebug --no-daemon` produces an APK that launches into a Compose-rendered `HomeScreen` showing a centered "Ingeniería en Alimentación" greeting on a themed `AlScaffold`.
- The iOS Xcode project at `iosApp/iosApp.xcodeproj` builds for the iOS simulator (arm64) and renders the same screen.
- The package layout, file organization, and per-feature 6-file pattern match the source skeleton exactly so any future feature can be added by replicating `presentation/feature/home/`.

### Verification

- `./scripts/compile.sh` exits 0
- `./gradlew :composeApp:assembleDebug --no-daemon` exits 0
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "generic/platform=iOS Simulator" build` exits 0 (manual)
- App launches on Android emulator; manual visual check shows the Home greeting

## What We're NOT Doing

- ❌ No Supabase, Firebase, Mixpanel, MercadoPago, Google Places, Coil, Wire/proto, SQLDelight, BuildKonfig, Crashlytics, deep links, splash screens, FCM, analytics, paging, shimmer, blurhash, reorderable, webview
- ❌ No product flavors, no signing config — single debug build only, no release/keystore wiring
- ❌ No DataStore (will be added in the first feature that needs it)
- ❌ No tests scaffolded in this plan — only the structure; tests will be added per feature using `kotlin-test` + Turbine + kotlinx-coroutines-test (per `~/.claude/rules/kotlin/testing.md`) once the skeleton compiles
- ❌ No domain/repository/use-case implementations — only the empty package directories so future features have the place to land
- ❌ No CI / GitHub Actions wiring
- ❌ No CLAUDE.md, no docs beyond the README — we'll add CLAUDE.md once the project takes shape

## Implementation Approach

Follow the source repo bottom-up: build configuration first (so Gradle resolves), then DI scaffolding (so `App()` can be composed), then theme + navigation + the single feature, then platform entry points. Verify after each phase by compiling.

All code lives under package root **`ar.com.ingenieriaalimentacion`** and namespace **`ar.com.ingenieriaalimentacion`**. Design system prefix is **`Al`** (e.g., `AlTheme`, `AlButton`). iOS framework baseName is **`IngenieriaAlimentacion`**.

---

## Phase 1: Build Configuration

### Overview
Lay down Gradle wrapper, version catalog, settings, root build file, and `composeApp` module build file. Same plugin set, target set, and core dependency set as the source skeleton, minus all app-specific extras.

### Changes Required

#### 1.1 Gradle wrapper

Copy `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat` from `/Users/juanduzac/dev/extra/`. Make `gradlew` executable.

#### 1.2 `gradle.properties`

**File**: `gradle.properties` (new)

```
kotlin.code.style=official
kotlin.daemon.jvmargs=-Xmx8g -XX:+UseG1GC -XX:CICompilerCount=4
kotlin.native.jvmArgs=-Xmx16g -XX:+UseG1GC -XX:MaxHeapFreeRatio=40

org.gradle.jvmargs=-Xmx8g -XX:MaxMetaspaceSize=2g -XX:+UseG1GC -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.daemon=true
org.gradle.configureondemand=true

android.nonTransitiveRClass=true
android.useAndroidX=true
```

#### 1.3 `settings.gradle.kts`

**File**: `settings.gradle.kts` (new)

```kotlin
rootProject.name = "ingenieria-alimentacion"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include("composeApp")
```

#### 1.4 `gradle/libs.versions.toml`

**File**: `gradle/libs.versions.toml` (new)

```toml
[versions]
agp = "8.10.0"
android-compileSdk = "36"
android-minSdk = "26"
android-targetSdk = "36"
androidx-activityCompose = "1.13.0"
jetbrains-lifecycle = "2.10.0"
compose-multiplatform = "1.10.3"
compose-foundation = "1.10.3"
kotlin = "2.3.20"
ktor = "3.4.2"
koin = "4.2.1"
kotlinx-coroutines = "1.10.2"
kotlinx-serialization = "1.11.0"
kotlinx-datetime = "0.7.1"
navigationCompose = "2.9.2"

[libraries]
compose-ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "compose-multiplatform" }
compose-ui-backhandler = { module = "org.jetbrains.compose.ui:ui-backhandler", version.ref = "compose-multiplatform" }

androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activityCompose" }
jetbrains-lifecycle-viewmodel = { group = "org.jetbrains.androidx.lifecycle", name = "lifecycle-viewmodel", version.ref = "jetbrains-lifecycle" }
jetbrains-lifecycle-runtime-compose = { group = "org.jetbrains.androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "jetbrains-lifecycle" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }

# Coroutines
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }

# Serialization
kotlinx-serialization = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-core", version.ref = "kotlinx-serialization" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-androidx-compose = { module = "io.insert-koin:koin-androidx-compose", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }

# Navigation
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-foundation" }

[plugins]
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
jetbrains-kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

#### 1.5 Root `build.gradle.kts`

**File**: `build.gradle.kts` (new)

```kotlin
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrains-kotlin-serialization) apply false
}
```

#### 1.6 `composeApp/build.gradle.kts`

**File**: `composeApp/build.gradle.kts` (new)

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "IngenieriaAlimentacion"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.ui.backhandler)
            implementation(compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.runtime.compose)

            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.navigation.compose)
            implementation(libs.compose.foundation)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "ar.com.ingenieriaalimentacion"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ar.com.ingenieriaalimentacion"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}
```

#### 1.7 `.gitignore`

**File**: `.gitignore` (new)

```
.gradle/
build/
local.properties
.idea/
*.iml
.kotlin/
.fleet/
.DS_Store
xcuserdata/
*.xcuserstate
DerivedData/
Pods/
```

#### 1.8 Compile script

**File**: `scripts/compile.sh` (new, executable)

```bash
#!/bin/bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
exec ./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon 2>&1 | tail -5
```

### Success Criteria

#### Automated Verification
- [x] `gradle/wrapper/gradle-wrapper.jar` and `gradlew` exist; `gradlew` is executable
- [x] `chmod +x scripts/compile.sh`
- [x] After Phase 2's source files exist, `./scripts/compile.sh` exits 0

#### Manual Verification
- [ ] `./gradlew tasks --no-daemon` lists `composeApp` tasks (run after Phase 2 stub sources are in place)

---

## Phase 2: Source Tree Skeleton (DI + Theme + Navigation + Home Feature)

### Overview
Create the package layout under `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/`, mirroring the source skeleton folder shape. Implement DI scaffolding, theme system, navigation, and one `home/` feature.

### Changes Required

#### 2.1 Package layout

Create empty `.gitkeep`-tracked or directly populated directories:

```
composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/
├── app/
│   ├── App.kt
│   ├── AppNavHost.kt
│   └── AppViewModel.kt
├── core/
│   └── design_system/
│       ├── components/
│       │   ├── button/AlButton.kt
│       │   └── scaffold/AlScaffold.kt
│       └── theme/
│           ├── Border.kt
│           ├── Color.kt
│           ├── Corner.kt
│           ├── Spacing.kt
│           ├── Theme.kt
│           └── Type.kt
├── data/                       (empty — placeholder for DTOs, mappers, repositories impl)
├── domain/
│   ├── model/                  (empty)
│   ├── repository/             (empty)
│   └── usecase/                (empty)
├── di/
│   ├── ClientsModule.kt
│   ├── InitKoin.kt
│   ├── KoinGlobalContext.kt
│   ├── Module.kt
│   ├── RepositoriesModule.kt
│   ├── UseCasesModule.kt
│   └── ViewModelsModule.kt
└── presentation/
    ├── feature/
    │   └── home/
    │       ├── HomeAction.kt
    │       ├── HomeDestination.kt
    │       ├── HomeNavigation.kt
    │       ├── HomeScreen.kt
    │       ├── HomeState.kt
    │       └── HomeViewModel.kt
    └── navigation/
        └── Route.kt

composeApp/src/commonMain/composeResources/
└── values/
    └── strings.xml             (with <string name="app_name">Ingeniería en Alimentación</string>)
```

#### 2.2 `Route.kt` — single Home route

**File**: `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/presentation/navigation/Route.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    @SerialName("home")
    data object Home : Route()
}
```

#### 2.3 Design system theme

**File**: `core/design_system/theme/Color.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class AlColors(
    val surfaceBase: Color,
    val surfaceMuted: Color,
    val textBase: Color,
    val textMuted: Color,
    val textInverted: Color,
    val accent: Color,
    val border: Color,
)

val LocalAlColors = compositionLocalOf<AlColors> {
    error("AlColors not provided. Wrap your content in AlTheme.")
}
```

**File**: `core/design_system/theme/Type.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AlTypography(
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
)

@Composable
fun defaultAlTypography() = AlTypography(
    title = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
)

val LocalAlTypography = compositionLocalOf<AlTypography> {
    error("AlTypography not provided. Wrap your content in AlTheme.")
}
```

**File**: `core/design_system/theme/Spacing.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AlSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
)

val LocalAlSpacing = compositionLocalOf { AlSpacing() }
```

**File**: `core/design_system/theme/Corner.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AlCorners(
    val sm: Dp = 4.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 16.dp,
)

val LocalAlCorners = compositionLocalOf { AlCorners() }
```

**File**: `core/design_system/theme/Border.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class AlBorders(
    val thin: Dp = 1.dp,
    val medium: Dp = 2.dp,
)

val LocalAlBorders = compositionLocalOf { AlBorders() }
```

**File**: `core/design_system/theme/Theme.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val lightColors = AlColors(
    surfaceBase = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF4F4F5),
    textBase = Color(0xFF18181B),
    textMuted = Color(0xFF71717A),
    textInverted = Color(0xFFFFFFFF),
    accent = Color(0xFF15803D),
    border = Color(0xFFE4E4E7),
)

@Composable
fun AlTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAlColors provides lightColors,
        LocalAlTypography provides defaultAlTypography(),
        LocalAlSpacing provides AlSpacing(),
        LocalAlCorners provides AlCorners(),
        LocalAlBorders provides AlBorders(),
    ) {
        content()
    }
}

object AlTheme {
    val colors @Composable @ReadOnlyComposable get() = LocalAlColors.current
    val typography @Composable @ReadOnlyComposable get() = LocalAlTypography.current
    val spacing @Composable @ReadOnlyComposable get() = LocalAlSpacing.current
    val corners @Composable @ReadOnlyComposable get() = LocalAlCorners.current
    val borders @Composable @ReadOnlyComposable get() = LocalAlBorders.current
}
```

#### 2.4 Design system components (minimal)

**File**: `core/design_system/components/scaffold/AlScaffold.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.components.scaffold

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

@Composable
fun AlScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AlTheme.colors.surfaceBase),
    ) {
        content()
    }
}
```

**File**: `core/design_system/components/button/AlButton.kt`

```kotlin
package ar.com.ingenieriaalimentacion.core.design_system.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme

@Composable
fun AlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = AlTheme.colors.textInverted,
        style = AlTheme.typography.body,
        modifier = modifier
            .clip(RoundedCornerShape(AlTheme.corners.md))
            .background(AlTheme.colors.accent)
            .clickable(enabled = enabled) { onClick() }
            .padding(
                horizontal = AlTheme.spacing.lg,
                vertical = AlTheme.spacing.md,
            ),
    )
}
```

#### 2.5 DI

**File**: `di/Module.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val sharedModule = module {
    // Place app-wide non-platform-specific singletons here as the project grows.
}
```

**File**: `di/ClientsModule.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val clientsModule = module {
    single {
        HttpClient(get()) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) = println(message)
                }
                level = LogLevel.NONE
            }
        }
    }
}
```

**File**: `di/RepositoriesModule.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import org.koin.dsl.module

val repositoriesModule = module {
    // Place repository bindings here as features land.
}
```

**File**: `di/UseCasesModule.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import org.koin.dsl.module

val useCasesModule = module {
    // Place use case factories here as features land.
}
```

**File**: `di/ViewModelsModule.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import ar.com.ingenieriaalimentacion.app.AppViewModel
import ar.com.ingenieriaalimentacion.presentation.feature.home.HomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelsModule = module {
    viewModelOf(::AppViewModel)
    viewModelOf(::HomeViewModel)
}
```

**File**: `di/InitKoin.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    config: KoinAppDeclaration? = null,
): KoinApplication {
    return startKoin {
        config?.invoke(this)
        modules(
            listOf(
                platformModule,
                sharedModule,
                clientsModule,
                repositoriesModule,
                useCasesModule,
                viewModelsModule,
            )
        )
    }
}

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@kotlin.native.ObjCName("doInitKoin")
fun doInitKoin() {
    if (!KoinGlobalContext.isInitialized) {
        KoinGlobalContext.initializeGlobalContext()
    }
}
```

**File**: `di/KoinGlobalContext.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import org.koin.core.KoinApplication
import org.koin.dsl.KoinAppDeclaration

object KoinGlobalContext {
    private lateinit var koinApplicationInstance: KoinApplication

    val isInitialized: Boolean
        get() = this::koinApplicationInstance.isInitialized

    fun initializeGlobalContext(config: KoinAppDeclaration? = null) {
        if (isInitialized) return
        koinApplicationInstance = initKoin(config)
    }

    fun koin() = if (isInitialized) {
        koinApplicationInstance.koin
    } else {
        throw IllegalStateException("KoinGlobalContext has not been initialized.")
    }
}

internal fun getKoinInstance() = KoinGlobalContext.koin()
```

#### 2.6 App layer

**File**: `app/AppViewModel.kt`

```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {
    private val _initialized = MutableStateFlow(true)
    val initialized = _initialized.asStateFlow()
}
```

**File**: `app/AppNavHost.kt`

```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import ar.com.ingenieriaalimentacion.presentation.feature.home.homeScreen
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

@Composable
fun AppNavHost(
    navController: NavHostController,
    startScreen: Route,
) {
    NavHost(
        navController = navController,
        startDestination = startScreen,
    ) {
        homeScreen(navController)
    }
}
```

**File**: `app/App.kt`

```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import ar.com.ingenieriaalimentacion.presentation.navigation.Route
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(
    appViewModel: AppViewModel = koinViewModel(),
    navController: NavHostController = rememberNavController(),
) {
    AlTheme {
        AppNavHost(
            navController = navController,
            startScreen = Route.Home,
        )
    }
}
```

#### 2.7 Home feature (the 6-file pattern)

**File**: `presentation/feature/home/HomeAction.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.home

sealed interface HomeAction {
    data object PrimaryClick : HomeAction
}
```

**File**: `presentation/feature/home/HomeDestination.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.home

sealed class HomeDestination {
    data object Back : HomeDestination()
}
```

**File**: `presentation/feature/home/HomeState.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.home

data class HomeState(
    val destination: HomeDestination? = null,
    val title: String = "Ingeniería en Alimentación",
    val subtitle: String = "Hola, mundo",
)
```

**File**: `presentation/feature/home/HomeViewModel.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        _state.value,
    )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.PrimaryClick -> Unit
        }
    }

    fun clearDestination() {
        _state.update { it.copy(destination = null) }
    }
}
```

**File**: `presentation/feature/home/HomeNavigation.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import ar.com.ingenieriaalimentacion.presentation.navigation.Route

fun NavController.navigateToHome(navOptions: NavOptions? = null) =
    navigate(Route.Home, navOptions)

fun NavGraphBuilder.homeScreen(navController: NavController) {
    composable<Route.Home> {
        HomeScreenRoot(
            navigate = { destination ->
                navigate(destination, navController)
            },
        )
    }
}

internal fun navigate(
    destination: HomeDestination,
    navController: NavController,
) {
    when (destination) {
        HomeDestination.Back -> navController.popBackStack()
    }
}
```

**File**: `presentation/feature/home/HomeScreen.kt`

```kotlin
package ar.com.ingenieriaalimentacion.presentation.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.com.ingenieriaalimentacion.core.design_system.components.scaffold.AlScaffold
import ar.com.ingenieriaalimentacion.core.design_system.theme.AlTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreenRoot(
    viewModel: HomeViewModel = koinViewModel(),
    navigate: (HomeDestination) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.destination) {
        state.destination?.let {
            navigate(it)
            viewModel.clearDestination()
        }
    }

    HomeScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
fun HomeScreen(
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    AlScaffold {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.title,
                style = AlTheme.typography.title,
                color = AlTheme.colors.textBase,
            )
            Text(
                text = state.subtitle,
                style = AlTheme.typography.body,
                color = AlTheme.colors.textMuted,
            )
        }
    }
}
```

#### 2.8 Compose resources

**File**: `composeApp/src/commonMain/composeResources/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Ingeniería en Alimentación</string>
</resources>
```

### Success Criteria

#### Automated Verification
- [x] `./scripts/compile.sh` exits 0
- [x] `./gradlew :composeApp:compileCommonMainKotlinMetadata --no-daemon` exits 0

#### Manual Verification
- [x] `find composeApp/src/commonMain/kotlin -name "*.kt" | wc -l` ≥ 24 (actual: 25)
- [x] All packages match `ar.com.ingenieriaalimentacion`

---

## Phase 3: Android Platform Wiring

### Overview
Add Android-specific source set: `Application` class that bootstraps Koin via `KoinGlobalContext`, `MainActivity` that hosts the Compose `App()`, `AndroidManifest.xml`, and `actual platformModule`.

### Changes Required

#### 3.1 `actual platformModule` (Android)

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.android.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { OkHttp.create() }
}
```

#### 3.2 Android `Application` class

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/AlApp.kt`

```kotlin
package ar.com.ingenieriaalimentacion

import android.app.Application
import ar.com.ingenieriaalimentacion.di.KoinGlobalContext
import org.koin.android.ext.koin.androidContext

class AlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        KoinGlobalContext.initializeGlobalContext {
            androidContext(this@AlApp)
        }
    }
}
```

#### 3.3 `MainActivity`

**File**: `composeApp/src/androidMain/kotlin/ar/com/ingenieriaalimentacion/MainActivity.kt`

```kotlin
package ar.com.ingenieriaalimentacion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ar.com.ingenieriaalimentacion.app.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}
```

#### 3.4 `AndroidManifest.xml`

**File**: `composeApp/src/androidMain/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name="ar.com.ingenieriaalimentacion.AlApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name="ar.com.ingenieriaalimentacion.MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

#### 3.5 Android string resource

**File**: `composeApp/src/androidMain/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Ingeniería en Alimentación</string>
</resources>
```

### Success Criteria

#### Automated Verification
- [x] `./gradlew :composeApp:assembleDebug --no-daemon` exits 0
- [x] APK exists at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`

#### Manual Verification
- [ ] App installs on Android emulator (`adb install -r <apk>`)
- [ ] App launches and shows the centered "Ingeniería en Alimentación" + "Hola, mundo" text on a white surface
- [ ] No crashes in `adb logcat` during launch

---

## Phase 4: iOS Platform Wiring

### Overview
Set up the iOS source set with `actual platformModule`, the Compose-hosting `MainViewController`, and a minimal `iosApp` Xcode project that hosts the Kotlin framework. The Xcode project is created manually using Android Studio's KMP wizard or via the Compose Multiplatform IntelliJ wizard reference template; this plan documents the file content but the `.xcodeproj` is initialized via the wizard to avoid hand-rolling project.pbxproj.

### Changes Required

#### 4.1 `actual platformModule` (iOS)

**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/di/Modules.ios.kt`

```kotlin
package ar.com.ingenieriaalimentacion.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<HttpClientEngine> { Darwin.create() }
}
```

#### 4.2 `MainViewController.kt`

**File**: `composeApp/src/iosMain/kotlin/ar/com/ingenieriaalimentacion/app/MainViewController.kt`

```kotlin
package ar.com.ingenieriaalimentacion.app

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController(
    configure = {
        onFocusBehavior = OnFocusBehavior.DoNothing
    },
) {
    App()
}
```

#### 4.3 Initialize `iosApp` Xcode project

Use Android Studio's "Kotlin Multiplatform App" wizard or the JetBrains "Compose Multiplatform Wizard" template (https://kmp.jetbrains.com) to scaffold `iosApp/iosApp.xcodeproj` configured to:

- Embed the `IngenieriaAlimentacion` framework produced by `:composeApp:embedAndSignAppleFrameworkForXcode`
- iOS Deployment Target: 15.0
- Bundle ID: `ar.com.ingenieriaalimentacion`
- Display Name: `Ingeniería en Alimentación`

After scaffolding, replace the generated `iOSApp.swift`, `ContentView.swift`, and `AppDelegate.swift` with the files below.

#### 4.4 `iOSApp.swift`

**File**: `iosApp/iosApp/iOSApp.swift`

```swift
import SwiftUI
import IngenieriaAlimentacion

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

#### 4.5 `AppDelegate.swift`

**File**: `iosApp/iosApp/AppDelegate.swift`

```swift
import UIKit
import IngenieriaAlimentacion

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        InitKoinKt.doInitKoin()
        return true
    }
}
```

#### 4.6 `ContentView.swift`

**File**: `iosApp/iosApp/ContentView.swift`

```swift
import SwiftUI
import UIKit
import IngenieriaAlimentacion

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(edges: .all)
            .ignoresSafeArea(.keyboard)
    }
}
```

### Success Criteria

#### Automated Verification
- [x] `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --no-daemon` exits 0
- [ ] `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -destination "generic/platform=iOS Simulator" -configuration Debug build` exits 0 — **blocked: requires manually scaffolding `iosApp.xcodeproj` via the JetBrains CMP wizard or Android Studio KMP wizard before this can run**

#### Manual Verification
- [ ] App boots in iOS simulator and shows the same Home screen content as Android
- [ ] No Swift compile warnings about missing symbols (e.g., `MainViewControllerKt`, `InitKoinKt`)
- [ ] No runtime Koin error about uninitialized graph

---

## Phase 5: Project README

### Overview
A short README so the next developer knows how to build and where the patterns live.

### Changes Required

**File**: `README.md` (replace the existing 12-byte placeholder)

```markdown
# Ingeniería en Alimentación — Mobile App

Compose Multiplatform app (Android + iOS).

## Requirements

- JDK 21 (Gradle uses it; `JAVA_HOME` is set automatically by `./scripts/compile.sh`)
- Android Studio with Kotlin Multiplatform plugin
- Xcode 15+ for iOS

## Build

```bash
./scripts/compile.sh                                      # quick common-main type check
./gradlew :composeApp:assembleDebug --no-daemon           # Android APK
```

For iOS, open `iosApp/iosApp.xcodeproj` in Xcode and run the `iosApp` scheme on a simulator.

## Architecture

Clean Architecture + MVVM with Koin for DI and jetbrains-navigation-compose for navigation.

- `composeApp/src/commonMain/kotlin/ar/com/ingenieriaalimentacion/`
  - `app/` — root `App()` composable, `AppNavHost`, `AppViewModel`
  - `core/design_system/` — `AlTheme`, color/spacing/typography tokens, components
  - `data/`, `domain/` — placeholders for layered code
  - `di/` — Koin modules (`platformModule` is `expect`/`actual`)
  - `presentation/feature/` — one folder per feature, six files: `*Action`, `*Destination`, `*Navigation`, `*Screen`, `*State`, `*ViewModel`
  - `presentation/navigation/Route.kt` — sealed `Route` registry

To add a feature, copy `presentation/feature/home/` and rename, then register the route in `Route.kt` and the screen in `app/AppNavHost.kt`.
```

### Success Criteria

#### Manual Verification
- [x] README exists and accurately describes the project layout

---

## Testing Strategy

No tests are scaffolded in this plan. Tests will be added per feature using `kotlin-test` + Turbine + kotlinx-coroutines-test (per `~/.claude/rules/kotlin/testing.md`) once the first real feature lands. The `HomeViewModel` is intentionally trivial and would only be worth testing once it does real work.

### Manual Testing Steps

1. Clone, run `./scripts/compile.sh` → expect exit 0
2. Run `./gradlew :composeApp:assembleDebug --no-daemon` → expect APK
3. Install APK, launch app → expect themed Home screen with title + subtitle
4. Open `iosApp/iosApp.xcodeproj`, build for simulator → expect same screen

## Performance Considerations

None at this stage — the skeleton has no work to do.

## Migration Notes

Not applicable — net-new project.

## References

- Source skeleton: `/Users/juanduzac/dev/extra/composeApp/src/commonMain/kotlin/com/extra/marketplace/`
- Per-feature pattern reference: `presentation/feature/auth/sign_in/` (cleanest 6-file example)
- DI pattern reference: `di/InitKoin.kt:10-30`, `di/KoinGlobalContext.kt:12-43`
- iOS bridge reference: `iosApp/iosApp/iOSApp.swift`, `iosMain/.../app/MainViewController.kt`
- Project conventions: `/Users/juanduzac/dev/extra/CLAUDE.md`
