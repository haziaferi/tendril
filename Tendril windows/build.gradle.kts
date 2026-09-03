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
}

compose.desktop {
    application {
        mainClass = "com.tendril.desktopapp.MainKt"

        nativeDistributions {
            // Same mark as the window/taskbar icon, as a multi-size .ico so the installer
            // and Start-menu shortcut pick the right resolution.
            windows {
                iconFile.set(project.file("src/main/resources/tendril_icon.ico"))
            }
        }
    }
}
