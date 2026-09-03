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

rootProject.name = "Tendril windows"

// §12.5 — shared KMP domain/data/sync-merge core, consumed via composite build (a sibling
// folder, not nested under this project — see tendril-spec.md §11 in "Tendril android").
includeBuild("../shared")
