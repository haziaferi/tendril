# Tendril

A personal notes / calendar / tasks / habits app. Android app, Windows desktop companion, and the
shared Kotlin Multiplatform core they both build on.

Sideloaded and personal — not distributed through any store.

| folder | what it is | builds |
|---|---|---|
| `Tendril android/` | Kotlin + Compose Android app, single-module Gradle project | the APK |
| `Tendril windows/` | Compose Multiplatform desktop companion, JVM | the Windows EXE |
| `shared/` | Kotlin Multiplatform domain / data / sync-merge core (Room, `android` + `desktop` targets) | consumed by both |

---

## The one structural rule

**Do not move, rename or nest these three folders.** Both consumers resolve the shared core by
relative path:

```kotlin
// Tendril android/settings.gradle.kts  and  Tendril windows/settings.gradle.kts
includeBuild("../shared")
```

So `shared/` must stay a *sibling* of the two apps, never inside either one. A git submodule cannot
satisfy this — a submodule lives inside its superproject — which is why all three live in one
repository. Clone the repo and the arrangement is already correct; nothing needs wiring.

Design reasoning for this lives in the spec, §11 and §12.5. This section is the rule, not the
argument for it.

---

## Building

**Requirements**

- JDK 21 (AGP's floor is 17; the project is built on 21)
- Android SDK, with a platform matching `compileSdk` (currently 37)
- Android Studio optional — everything below is command line

**There are three Gradle wrappers, one per build.** Run the one for what you want; there is no
wrapper at the repository root.

```bash
cd "Tendril android"  &&  ./gradlew :app:assembleDebug     # APK
cd "Tendril windows"  &&  ./gradlew run                    # desktop app
cd shared             &&  ./gradlew compileKotlinDesktop   # shared core only
```

On Windows PowerShell use `.\gradlew.bat` in place of `./gradlew`.

**SDK location.** `local.properties` is machine-specific and deliberately not committed, so a fresh
clone has no `sdk.dir`. Either let Android Studio generate it on first open, or set the environment
variable:

```bash
export ANDROID_SDK_ROOT=/path/to/Android/Sdk        # bash
```
```powershell
$env:ANDROID_SDK_ROOT = "C:\path\to\Android\Sdk"    # PowerShell
```

**Debug signing.** `Tendril android/keystores/my-shared-debug.keystore` *is* committed, deliberately.
`app/build.gradle.kts` references it, so without it a fresh clone cannot assemble a debug build; and
committing it means every machine signs with the same key, so moving between them never forces an
uninstall on a signature mismatch. Alias `androiddebugkey`, password `android` — both public by
definition, and a debug key cannot sign for release. Release keys are ignored by `.gitignore` and
must never be committed.

---

## Tests

```bash
cd "Tendril android"
./gradlew :app:testDebugUnitTest        # JVM unit tests
./gradlew :app:connectedDebugAndroidTest  # instrumented — needs a device or emulator
```

`connectedDebugAndroidTest` **uninstalls the app when it finishes**, which also wipes its database.
Reinstall with `./gradlew :app:installDebug` afterwards, and note that runtime permissions
(`POST_NOTIFICATIONS`) are lost with the uninstall.

It targets *every* connected device by default. To pick one:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Some alarm behaviour is API-level specific — the exact-alarm permission path differs on API 31–32,
where `USE_EXACT_ALARM` does not exist — so `AlarmSchedulerInstrumentedTest` is worth running against
an API 32 image as well as a current one.

---

## Checks

```bash
python3 tools/audit.py        # static hygiene checks; exit 1 on any finding
```

Needs only Python 3 — no Gradle, no Android SDK, no network — so it runs anywhere in
seconds, including on a machine that cannot build the app. It checks for commented-out code,
leftover TODO/FIXME markers, unreferenced declarations and DAO methods, KDoc links naming
symbols that don't exist, `StateFlow`s that leak their mutable backing, `Regex` allocated per
call instead of once, and calls to the documented-throwing file APIs with no `try`/`catch`.

Every check corresponds to a defect this repository has actually had, so a finding is a
regression rather than a style opinion. `.github/workflows/audit.yml` runs it on every push.
The list and the reasoning behind each check live in the script's own module docstring.

---

## Documentation

| document | what it is for |
|---|---|
| `Tendril android/tendril-spec.md` | the design record — decisions, reasoning, open questions, and a Revision Log. Start here for *why*. |
| `Tendril windows/tendril-windows-spec.md` | the same, for the desktop companion |
| `docs/audit-2026-09-04.md` | findings from the 2026-09-04 code audit that were *not* fixed — open bugs, sync gaps, security residue, and where Tendril sits against Notion and its open-source peers |
| this README | how to get it building. Nothing else. |

The specs are the source of truth for design decisions; keep adding to them as decisions get made.
This file only changes when the setup does.
