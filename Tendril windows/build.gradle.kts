import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

// Audit 2026-09-23 — the JetBrains Runtime is *preferred*, not required, and this is the line that
// says so. L5 pinned `vendor = JETBRAINS` unconditionally in both places below, which is true of
// the developer's machine and of nowhere else: a Linux runner has Temurin, so configuration failed
// outright — "Cannot find a Java installation … matching {languageVersion=21, vendor=JetBrains}" —
// and this module did not build on CI once between 2026-09-17 and the day this was written.
//
// Nobody saw it. `build.yml` had been failing to *start* since 2026-09-12 for an unrelated billing
// reason, so a hundred consecutive red runs all said "job was not started" and the live fault
// arrived five days into that blackout with nothing left to report it. A dead signal is worse than
// no signal: it is indistinguishable from the real one.
//
// The fallback was always the intent — "on a plain JDK everything still runs" is L5's own comment,
// two lines down. Resolving the launcher once, here, is what turns that sentence into behaviour.
// `runCatching` rather than `.orNull`, because a toolchain provider with no match throws when it
// is queried rather than yielding null.
val jetbrainsRuntime: JavaLauncher? = runCatching {
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }.get()
}.getOrNull()

kotlin {
    // L5 (2026-09-17) — a JetBrains Runtime, not any JDK 21: the borderless window's custom title
    // bar is a JBR service (`TitleBar.kt`). Android Studio's JBR is registered in
    // `gradle.properties` (`org.gradle.java.installations.paths`); jpackage bundles the same
    // runtime, so the installed app has the service too. On a plain JDK everything still runs —
    // the window keeps the OS title bar.
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        if (jetbrainsRuntime != null) vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

dependencies {
    implementation("com.tendril:shared")
    // L5 — the JBR API (compile-time only; the runtime provides the service, or not).
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
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
    // B§13.6 #7 (tendril-spec.md §0.10 item 20) — the global quick-add chord is `User32.RegisterHotKey`
    // on a message-loop thread (`GlobalHotkey.kt`). 5.6.0 is what the Gradle cache holds, so the
    // offline build resolves it; nothing else here is Windows-only by dependency.
    implementation("net.java.dev.jna:jna-platform:5.6.0")

    // The test source set (`src/test/kotlin`), added 2026-09-23. This module had none, and the
    // cost of that is written down: audit rows 1.5, 1.6 and 1.8 are all desktop-only defects that
    // sat as hypotheses through five audits because nothing here could execute a check. 1.5 and
    // 1.8 were eventually settled by walking the dev build by hand, and their fixes are pinned by
    // that walk rather than by anything a gate runs; 1.6 was walked once and would not reproduce.
    //
    // What a JVM test can reach here is the logic that does not draw: `DesktopReminderScheduler`,
    // the firing arithmetic, the toast strings — and anything a composable was merely *holding*
    // rather than drawing, which is how 1.8's one-write guard came out into `QuickAddGate.kt` and
    // got a test on 2026-09-23. Compose UI is the real limit: the `ui-test-junit4` artifact for
    // desktop is not in the offline cache, so row 1.5 — a duplicated `Text` — stays walk-pinned,
    // and that is the line this source set genuinely does not cross.
    //
    // This comment used to put 1.8 on the far side of that line too, which was never quite true:
    // a flag is not a `Text`. It was corrected by the sweep that went looking for exactly this —
    // reasons that were sound when written and had quietly stopped being so.
    //
    // Every version here is one the Gradle cache already holds, since `dl.google.com` is blocked
    // and an offline build is the only kind that runs.
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlinx.coroutines.test)
    // `TendrilDatabase` is a Room class with no desktop-constructible form; the DAOs the scheduler
    // reads are stubbed rather than a database being stood up.
    testImplementation("io.mockk:mockk:1.13.13")
}

compose.desktop {
    application {
        mainClass = "com.tendril.desktopapp.MainKt"
        // L5 — `run` and jpackage use the toolchain's JBR, not the JVM running Gradle (Temurin):
        // the custom title bar is that runtime's service (`TitleBar.kt`). Audit 2026-09-23 — this
        // is the line the CI build actually died on, because it is `.get()` at *configuration*
        // time: not a task anyone asked for, but a value computed on every invocation of every
        // task in this project, `test` included. Falling back to any JDK 21 costs the title bar on
        // a machine with no JBR, which is precisely what L5 said was acceptable, and buys the
        // module the ability to be built anywhere.
        javaHome = (jetbrainsRuntime ?: javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get()).metadata.installationPath.asFile.absolutePath

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
