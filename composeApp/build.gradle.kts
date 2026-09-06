import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.revenuecat.purchases)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

/**
 * Upload-key credentials, read from local.properties (gitignored).
 *
 * Never hardcoded and never committed: anyone with the keystore and its password
 * can publish an update to your app. Absent credentials fall back to debug
 * signing so the project still builds on a fresh clone.
 */
val signingProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasUploadKey = signingProps.getProperty("CURIO_KEYSTORE_FILE") != null

android {
    namespace = "app.curio"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.curio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Every upload to Play needs a HIGHER versionCode than the last, even a
        // rejected one — the number is consumed on upload, not on release.
        // Bump this before every single bundle you send.
        versionCode = 3
        versionName = "0.1.0"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        if (hasUploadKey) {
            create("upload") {
                storeFile = rootProject.file(signingProps.getProperty("CURIO_KEYSTORE_FILE"))
                storePassword = signingProps.getProperty("CURIO_KEYSTORE_PASSWORD")
                keyAlias = signingProps.getProperty("CURIO_KEY_ALIAS")
                keyPassword = signingProps.getProperty("CURIO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Minification off for now. R8 needs keep rules for kotlinx.serialization
            // and Compose, and debugging a stripped release build three weeks from
            // a deadline is not a trade worth making for a few MB.
            isMinifyEnabled = false
            if (hasUploadKey) signingConfig = signingConfigs.getByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

/**
 * Refuse to build a release bundle that could ship a Test Store key.
 *
 * MainActivity already picks the key from the debuggable flag, so this should
 * never fire. It exists because the failure it guards against is silent and
 * expensive: an app on Play configured with a `test_` key shows a paywall,
 * accepts a "purchase", grants the entitlement, and never charges anyone. There
 * is no crash and no error — you find out from the revenue graph.
 *
 * A build that fails loudly on the machine is a better outcome than one that
 * fails quietly in production.
 */
tasks.register("checkReleaseKeys") {
    val keysFile = file("src/commonMain/kotlin/app/curio/billing/BillingKeys.kt")
    inputs.file(keysFile)

    doLast {
        val text = keysFile.readText()
        val androidKey = Regex("""ANDROID_API_KEY:\s*String\s*=\s*"([^"]*)"""")
            .find(text)?.groupValues?.get(1).orEmpty()

        check(androidKey.isNotBlank()) {
            "ANDROID_API_KEY is blank — a release build would ship with billing disabled."
        }
        check(!androidKey.startsWith("test_")) {
            "ANDROID_API_KEY is a Test Store key. Release builds must use the goog_ key, " +
                "or customers will appear to pay and no money will arrive."
        }
    }
}

tasks.matching { it.name == "bundleRelease" || it.name == "assembleRelease" }.configureEach {
    dependsOn("checkReleaseKeys")
}
