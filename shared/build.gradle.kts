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

// §9.10 — Room writes the schema of every version it compiles to `shared/schemas/`, and those
// files are committed. Room needs the *previous* version's JSON on disk to generate an
// @AutoMigration to the next one, so an unexported version is a version nothing can migrate
// from: the file has to exist before the bump that needs it, not after.
//
// Set as a KSP argument rather than through the `androidx.room` Gradle plugin. The plugin is
// the documented route on Room 2.8, but it is another plugin to resolve, and this argument is
// all it configures here. Both `kspAndroid` and `kspDesktop` write the same path, which is
// correct: one @Database compiled for two targets has one schema, and two copies that could
// ever disagree would be the bug, not the safeguard.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ...and because they write the *same* file, they must not write it at the same time.
// `org.gradle.parallel=true` (gradle.properties) lets both KSP tasks run concurrently, and
// Room reads an existing schema JSON before replacing it — so one target can read the file
// in the instant the other has truncated it and not yet refilled it, which surfaces as
// `Expected start of the object '{', but had 'EOF' instead` with an empty JSON input.
//
// It is latent until a version actually moves: when `schemas/<version>.json` is already
// present and current, neither task writes, so nothing races. v9 (S2) is the first bump
// since `exportSchema = true` landed in S1a, and therefore the first build ever to ask two
// parallel tasks to create the same schema file. Every future bump would hit it identically.
//
// Ordered rather than deduplicated: the two targets producing one identical file is the
// property §9.10 wants (see above), so the fix is to stop them overlapping, not to give
// each its own copy that could then silently disagree.
tasks.matching { it.name == "kspKotlinDesktop" }.configureEach {
    mustRunAfter("kspAndroidMain")
}

// Milestone 3 — explicit package for the generated Res class (fonts/strings for the ported
// Workbench UI); default resolves from group+module name, pinned here so it doesn't shift if
// either changes later. Stays module-internal (publicResClass defaults to false) since only
// code inside this module's own commonMain (the moved composables) references Res.*.
compose.resources {
    packageOfResClass = "com.tendril.app.generated.resources"
}
