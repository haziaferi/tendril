import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("com.tendril:shared")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Milestone 3 (tendril-windows-spec.md §6 step 3) — the ported Workbench UI needs the
    // fuller icon set (compose.material3 alone doesn't bundle it) and coroutines-swing for
    // ViewModel coroutines to run correctly under Compose Desktop (see Main.kt).
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
    // tendril-spec.md 0.10 item 10 -- the desktop Back input (Main.kt's EscapeBackInput) subclasses
    // NavigationEventInput, which shared's ui-backhandler pulls in only transitively; the compiler
    // wants it named. Same version the transitive edge resolves to.
    implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0")
}

compose.desktop {
    application {
        mainClass = "com.tendril.desktopapp.MainKt"

        nativeDistributions {
            // Without a target format there is no installer task output at all: the block
            // below configured an icon for a distribution nothing was asked to build, so
            // `createDistributable` produced a portable app image and nothing produced an
            // installer. WiX is not a prerequisite to install by hand — the Compose plugin
            // has a `downloadWix` task and fetches it itself.
            targetFormats(TargetFormat.Msi)

            // Defaults to the Gradle project name, which is "Tendril windows" — a space in
            // the install path, the shortcut and the executable name. The product is called
            // Tendril; "windows" is which build it is, not what it is called.
            packageName = "Tendril"

            // MSI rejects a 0.x version: jpackage requires the major component to be greater
            // than zero. This is the *installer* version and is deliberately not the project
            // version (0.1.0), which stays where it is.
            packageVersion = "1.0.0"

            windows {
                // Same mark as the window/taskbar icon, as a multi-size .ico so the installer
                // and Start-menu shortcut pick the right resolution.
                iconFile.set(project.file("src/main/resources/tendril_icon.ico"))

                // Identifies the product across versions, so installing a newer build replaces
                // the old one instead of leaving two entries in Apps & features. It must never
                // change once a build has been installed anywhere, which is why it is written
                // here rather than derived from anything.
                upgradeUuid = "f5bbe973-8b8e-4fe2-8d99-624a4e2c7ca1"

                menuGroup = "Tendril"
                shortcut = true

                // Per-user rather than per-machine: no elevation prompt, and this is a
                // single-person sideloaded build that has no reason to write to Program Files.
                perUserInstall = true
            }
        }
    }
}
