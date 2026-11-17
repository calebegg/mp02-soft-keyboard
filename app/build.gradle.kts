import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningProps = Properties().apply {
    val homeProps = File(System.getProperty("user.home"), ".android/mp02-release.properties")
    val localSigning = rootProject.file("signing.properties")
    val chosen = listOf(localSigning, homeProps).firstOrNull { it.isFile }
    if (chosen != null) {
        chosen.inputStream().use { load(it) }
    }
}

fun signingProp(propKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: releaseSigningProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

android {
    namespace = "gg.lebe.mp02keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "gg.lebe.mp02keyboard"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    val releaseStoreFile = signingProp("storeFile", "MP02_KEYSTORE_PATH")?.let(::File)?.takeIf { it.isFile }
    val storePass = signingProp("storePassword", "MP02_KEYSTORE_PASS")
    val alias = signingProp("keyAlias", "MP02_KEY_ALIAS")
    val kPass = signingProp("keyPassword", "MP02_KEY_PASS")
    val hasReleaseSigning = releaseStoreFile != null && storePass != null && alias != null && kPass != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = storePass
                keyAlias = alias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
