import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Release signing secrets live in keystore.properties (git-ignored), never in this
// file or in git. Absent on machines that only build debug (e.g. CI without the key).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Per-environment URLs live in local.properties (git-ignored). See local.properties.example
// for the full key list. Absent on a fresh clone, which is why prod carries a default below.
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// ---------------------------------------------------------------------------
// Environment selection (tst / prod)
//
// BuildKonfig generates a SINGLE `AlEnv` object into commonMain per Gradle
// invocation, so exactly one environment can be resolved per build — building
// both flavors at once would bake one env into both APKs. Resolution order:
//   1. explicit -PtargetEnvironment=tst|prod   (used by CI and by iOS later)
//   2. inferred from the requested Android variant task (assembleTstDebug -> tst)
//   3. "tst" fallback — never silently prod.
// ---------------------------------------------------------------------------
val requestedTasks: String = gradle.startParameter.taskNames.joinToString(" ").lowercase()
val explicitEnv: String? = (project.findProperty("targetEnvironment") as String?)?.lowercase()
val tasksWantProd: Boolean = requestedTasks.contains("prod")
val tasksWantTst: Boolean = requestedTasks.contains("tst")

if (explicitEnv == null && tasksWantProd && tasksWantTst) {
    error(
        "Both 'tst' and 'prod' tasks were requested in one Gradle invocation. BuildKonfig can only " +
            "bake one environment per build, so this would ship the wrong API_URL in one of them. " +
            "Run them as separate invocations, or pass -PtargetEnvironment explicitly."
    )
}

val targetEnv: String = explicitEnv ?: when {
    tasksWantProd -> "prod"
    tasksWantTst -> "tst"
    else -> "tst"
}

require(targetEnv == "tst" || targetEnv == "prod") {
    "Unknown -PtargetEnvironment='$targetEnv'. Expected 'tst' or 'prod'."
}

// Reads `key` from local.properties, treating a present-but-empty value as absent.
fun localProperty(key: String, default: String = ""): String =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() } ?: default

val apiUrl: String = when (targetEnv) {
    // Keep the known prod host as the default so a fresh clone (no local.properties)
    // still builds and runs. tst has no default — it must be supplied.
    "prod" -> localProperty("api_url_prod", "https://inpla.up.railway.app/")
    else -> localProperty("api_url_tst")
}

// A blank base URL yields an app where every request fails in a confusing way, so hard-fail
// as soon as the build would produce an installable artifact. Bare compile / metadata tasks
// (scripts/compile.sh, CI type checks) stay green without a local.properties.
if (apiUrl.isBlank()) {
    val producesArtifact = listOf("assemble", "bundle", "install", "embedandsign")
        .any { requestedTasks.contains(it) }
    val message = "API base URL for environment '$targetEnv' is missing. Add 'api_url_$targetEnv=...' " +
        "to local.properties (copy local.properties.example to get started)."
    if (producesArtifact) error(message) else logger.warn("WARNING: $message")
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.buildkonfig)
}

buildkonfig {
    packageName = "ar.com.ingenieriaalimentacion.env"
    objectName = "AlEnv"

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "APP_ENVIRONMENT", targetEnv)
        buildConfigField(FieldSpec.Type.STRING, "API_URL", apiUrl)
    }
}

// Pin targetEnv as an explicit task input so flipping environments always marks
// generateBuildKonfig out-of-date. Without this, an env switch whose field values happen
// to be unchanged can leave the task UP-TO-DATE and bake a STALE environment into the
// build — the failure mode is a "prod" APK still pointing at the tst backend.
tasks.matching { it.name.startsWith("generateBuildKonfig") }
    .configureEach { inputs.property("targetEnvironment", targetEnv) }

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("ar.com.ingenieriaalimentacion.data.db")
            // Emit a .db snapshot per schema version so migrations have a baseline to
            // diff against. Committed alongside the .sqm files.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            // Fail the build if a .sq schema change has no matching .sqm migration
            // (the exact mistake that shipped PendingMeasurement without a migration).
            verifyMigrations.set(true)
        }
    }
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
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
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
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.navigation.compose)
            implementation(libs.compose.foundation)

            implementation(libs.komoui)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        // JVM-only JdbcSqliteDriver for in-memory DB tests — kept out of commonTest so
        // the native (iOS) test source sets still compile.
        androidUnitTest.dependencies {
            implementation(libs.sqldelight.jvm.driver)
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
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        // Only wire the release signing config when keystore.properties is present,
        // so debug-only builds (and CI without the key) still configure cleanly.
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // Environment flavors. `applicationIdSuffix` is what lets a tst and a prod build sit on
    // the same device without sharing the SQLDelight offline queue or the auth prefs — both
    // are sandboxed per package, so queued writes can never flush to the wrong backend.
    flavorDimensions += "environment"
    productFlavors {
        create("tst") {
            dimension = "environment"
            applicationIdSuffix = ".tst"
            versionNameSuffix = "-tst"
            resValue("string", "app_name", "Ing. Alimentación (Test)")
            resValue("string", "deep_link_scheme", "ingenieriaalimentacion")
        }
        create("prod") {
            dimension = "environment"
            resValue("string", "app_name", "Ingeniería en Alimentación")
            resValue("string", "deep_link_scheme", "ingenieriaalimentacion")
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
        getByName("release") {
            // Keep R8/minification off for the first distributable builds — enabling it
            // needs keep rules for Koin + kotlinx.serialization and can break silently.
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.findByName("release")
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

// Run the SQLDelight migration check on every compile (assembleDebug skips `check`,
// which is exactly when a missing migration would otherwise slip through to a device).
// Incremental: re-runs only when a .sq / .sqm / schema .db file actually changes.
tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }
    .configureEach { dependsOn("verifyCommonMainAppDatabaseMigration") }
