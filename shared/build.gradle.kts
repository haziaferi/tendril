plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

group = "com.tendril"
version = "0.1.0"

kotlin {
    jvmToolchain(21)

    android {
        namespace = "com.tendril.app.shared"
        compileSdk = 37
        minSdk = 30
        // Milestone 3 — the Workbench UI (theming/nav/block editor) moved here needs its
        // strings.xml/font resources to compile through Compose Multiplatform resources.
        androidResources.enable = true
    }

    jvm("desktop")

    // §12.5/Milestone 2 — a manual dependsOn edge (below) disables the free
    // commonMain->androidMain/desktopMain edges unless this is called first.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // javax.crypto/java.security (SnapshotEncryption) aren't visible from true commonMain even
        // though both current targets are JVM-based — jvmCommon is the standard fix, shared by both
        // without being reachable from a hypothetical future non-JVM target.
        val jvmCommon by creating { dependsOn(commonMain.get()) }
        androidMain { dependsOn(jvmCommon) }
        getByName("desktopMain") { dependsOn(jvmCommon) }

        commonMain.dependencies {
            // api, not implementation: consumers (:app, Tendril windows) reference TendrilDatabase
            // (a RoomDatabase subclass) and the DAO/entity types directly, so Room's own types need
            // to be visible on their compile classpath too, not just this module's internals.
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Milestone 3 — the Workbench UI (theming, nav shell, Pages/PageDetail/PageDatabase
            // block editor) lives here now so it genuinely renders on both android and desktop,
            // not two hand-maintained copies (tendril-windows-spec.md §6 step 3).
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            implementation(compose.components.resources)
            // api, not implementation: consumers need ViewModelStoreOwner/LocalViewModelStoreOwner
            // at their own app-entry call sites too (see Tendril windows' DesktopViewModelStoreOwner).
            api(libs.androidx.lifecycle.viewmodel.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.sqlite.framework)
            implementation(libs.androidx.documentfile)
        }
        getByName("desktopMain") {
            dependencies {
                implementation(libs.androidx.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}

// Milestone 3 — explicit package for the generated Res class (fonts/strings for the ported
// Workbench UI); default resolves from group+module name, pinned here so it doesn't shift if
// either changes later. Stays module-internal (publicResClass defaults to false) since only
// code inside this module's own commonMain (the moved composables) references Res.*.
compose.resources {
    packageOfResClass = "com.tendril.app.generated.resources"
}
