plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.lex.editor"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.lex.editor"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"
    }

    signingConfigs {
        // Checked into the repo on purpose, and safe to publish: it signs debug builds
        // only, so every CI debug APK installs over the last one instead of failing on a
        // signature mismatch. Release builds never touch it -- see below.
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            // Deliberately no fallback to the debug key. This repository is public and
            // app/debug.keystore is in it, so signing a release with it would let anyone
            // build an APK that Android accepts as an update to an installed Lex.
            // A release therefore either has a real key or is not signed at all.
            val keystore = System.getenv("LEX_KEYSTORE_FILE")?.let(::file)
            if (keystore?.exists() == true) {
                storeFile = keystore
                storePassword = System.getenv("LEX_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LEX_KEY_ALIAS")
                keyPassword = System.getenv("LEX_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // Null when no key was supplied, which leaves the APK unsigned rather than
            // silently signing it with the public debug key.
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile?.exists() == true }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.jsoup)

    testImplementation(libs.junit)
}
