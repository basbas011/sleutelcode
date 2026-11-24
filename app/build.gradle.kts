import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---- Versie -----------------------------------------------------------
// versionName komt van -PversionName=1.2.3 (CI zet dit vanuit de git-tag).
// versionCode wordt daaruit afgeleid: 1.2.3 -> 10203, zodat nieuwere
// versies altijd een hogere code hebben, ook bij lokale builds.
val appVersionName: String = (findProperty("versionName") as String?) ?: "1.0.0"

fun versionCodeFrom(name: String): Int {
    val parts = name.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    return parts.getOrElse(0) { 0 } * 10000 +
        parts.getOrElse(1) { 0 } * 100 +
        parts.getOrElse(2) { 0 }
}

// ---- Signing ----------------------------------------------------------
// Lokaal: keystore.properties in de projectmap (niet in git).
// CI: omgevingsvariabelen KEYSTORE_FILE / KEYSTORE_PASSWORD (uit GitHub Secrets).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(propName: String, envName: String): String? =
    keystoreProps.getProperty(propName) ?: System.getenv(envName)

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")

android {
    namespace = "nl.bict.sleutelcode"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.bict.sleutelcode"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCodeFrom(appVersionName)
        versionName = appVersionName

        buildConfigField(
            "String",
            "GITHUB_REPO",
            "\"${(findProperty("githubRepo") as String?) ?: ""}\""
        )
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS") ?: "app"
                keyPassword = signingValue("keyPassword", "KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "WAARSCHUWING: geen keystore gevonden, release wordt met de debug-key " +
                        "getekend. Updates over een eerdere versie heen zullen niet installeren."
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
}
