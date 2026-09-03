pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tendril"
include(":app")

// §12.5 — shared KMP domain/data/sync-merge core, consumed via composite build rather than a
// monorepo submodule (a sibling folder, not nested under this project — see tendril-spec.md §11).
includeBuild("../shared")
