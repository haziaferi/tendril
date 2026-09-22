plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tendril.app"
    compileSdk = 37

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystores/my-shared-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.tendril.app"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // P11 (2026-09-18): Robolectric reads the merged manifest (the debug one declares the activity
    // `createComposeRule()` launches) and the resources a composed screen needs.
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// PopulatedMigrationTest builds its starting file from `shared/schemas/<version>.json`, read off
// the disk rather than from the test classpath, so Gradle does not see those files as inputs of
// the unit-test task and keeps a cached pass through a schema-only change (2026-09-22 audit: a
// byte appended to 8.json, `testDebugUnitTest UP-TO-DATE`). Declared here so the task re-runs.
tasks.withType<Test>().configureEach {
    inputs.dir(rootProject.file("../shared/schemas")).withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.play.services.auth)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    // Only the page-side collaborators are mocked — PagesSyncEngine and PageDao, neither of
    // which the sync-conflict tests assert on. The DAOs those tests *do* care about are
    // hand-written in-memory fakes, so the behaviour under test stays readable.
    testImplementation("io.mockk:mockk:1.13.13")
    // WritePathSyncTest drives the real ViewModels rather than fabricating the snapshot records
    // they are supposed to produce, and `viewModelScope` dispatches on Dispatchers.Main — which
    // a JVM unit test has to install for itself. Version-less: coroutines' own BOM aligns every
    // kotlinx-coroutines-* module, so this tracks whatever `libs.versions.toml` pins for core.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    // DatabaseFileTest opens the *real* Android database file through the production path, with
    // the framework's own SQLiteDatabase underneath — the one place a JVM fake cannot stand in,
    // because the failure it guards against lives inside android.database.sqlite itself.
    testImplementation("org.robolectric:robolectric:4.16.1")
    // The phone's fix PR (P11, 2026-09-18): a Compose layout test under Robolectric — the one check
    // that would have caught a Touch-only negative padding (`docs/critiques/phone-catch-up.md`).
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // ui-test-junit4 asks for androidx.test 1.5.0, which is not in the offline cache; the 1.7.0 the
    // on-device tests already use is, and Gradle takes the higher.
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test:runner:1.7.0")
    // On-device tests for the SAF write path — the one thing JVM unit tests can't reach,
    // since DocumentFile's behaviour is what SYNC-02 turns on.
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")

    // §12.5 — shared KMP domain/data/sync-merge core (composite build, ../shared)
    implementation("com.tendril:shared")
}