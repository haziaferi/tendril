# Working in this repository

Tendril is a sideloaded personal notes / calendar / tasks / habits app: an Android app, a Windows
desktop companion, and the Kotlin Multiplatform core they both build on. `README.md` has the
layout and the one structural rule; `tendril-spec.md` §0 has what the app is for. This file is for
the things that are true about *working here* and that cost something to rediscover.

## Read first

- **`tendril-spec.md` §0 Objectives** before any design decision. Every later section is filtered
  through it.
- **`docs/audit-2026-09-22.md`** for the current known-defect list: seventeen findings — seven
  fixed, five read and reported without a change, five still open hypotheses (1.5, 1.6, 1.7,
  1.8, 1.11), each with the command or walk that would settle it. Three of the fixed rows are
  pinned only by the compiler and a walk, not by a test; the rows say which.
- **`/tendril-audit`** (`.claude/skills/tendril-audit/`) is the audit-and-perfect procedure — two
  modes, five passes, four gate commands.

## The gate

Nothing is "done" until these four pass. They run offline; `dl.google.com` is blocked, but the
Gradle cache holds every pinned version, so `--offline` works and a network failure is never the
explanation for a red build.

```bash
cd "Tendril android" && ./gradlew --offline :app:assembleDebug :app:testDebugUnitTest
cd shared && ./gradlew --offline build
cd "Tendril windows" && ./gradlew --offline build
python tools/audit.py && python -m pytest -q tools/tests
```

`Tendril windows` gained a test source set on 2026-09-23 (`src/test/kotlin`), so its `build`
now *runs* tests where before it compiled and stopped — a green desktop build means more than it
used to. Count those from `Tendril windows/build/test-results/test/*.xml`. What it does not reach
is Compose UI: the desktop `ui-test-junit4` artifact is not in the offline cache, so anything that
draws is still pinned by a walk.

**Count tests from the XML, not from Gradle's summary.** A cached `testDebugUnitTest` reports a
pass without executing anything — the first gate run on a clean tree is usually `FROM-CACHE`. Only
a run after an edit executes. Count with:

```bash
python - <<'PY'
import glob, re
tot = fail = files = 0
for f in glob.glob("Tendril android/app/build/test-results/testDebugUnitTest/*.xml"):
    s = open(f, encoding="utf-8").read(4000); files += 1
    m = re.search(r'tests="(\d+)".*?failures="(\d+)"', s)
    if m: tot += int(m.group(1)); fail += int(m.group(2))
print(files, "files,", tot, "tests,", fail, "failures")
PY
```

## Two questions the gate does not ask

The gate asks whether the code compiles and the tests pass. Neither says whether the app does what
it promised, or whether a test would notice if a guard disappeared.

```bash
python tools/spec_trace.py                        # which declarations no test even names
python tools/mutate.py --symbol addEdge --list    # which guards there are, and what would run
python tools/mutate.py --symbol addEdge           # delete one, run its tests, expect red
python tools/mutate.py --self-check --quick       # before trusting a batch (free)
```

`spec_trace.py` resolves each spec section to the production declarations that cite it — a `§` in
KDoc sits directly above what it describes — and reports the ones nothing names. `mutate.py` takes
one single-line guard, comments it out inside a throwaway `git worktree` that carries your
uncommitted work, and runs only the tests naming that symbol: **KILLED** means pinned, **SURVIVED**
means the guard can be deleted and the suite stays green. One mutation is one Gradle run, roughly
four minutes for `shared/`, so `--list` first.

Neither belongs in `audit.py`'s PASS/FAIL. An unpinned claim is a question, not a regression, and a
check that fails on every honest tree gets suppressed within a week — taking the real findings with
it.

**A SURVIVED is a claim about the tool before it is a claim about the code.** `mutate.py` has
produced one wrong report, and the cause was not a coding mistake: it was validated with `--symbol`
on a public function, then trusted with `--section` over private helpers — a shape it had never
been asked — and scoping collapsed to one unrelated test class. So the rule, which generalises past
this tool: **validate an instrument in the same mode, and on the same shape of input, as the run
you are about to trust, including one case whose answer you already know.** `--self-check` is that
rule made executable; its static tier runs automatically before any batch and aborts on failure.

Three specific ways a SURVIVED can still be an artefact rather than a finding, all of them now
reported rather than silent: the symbol has no unit test at all (skipped); its only coverage is in
`androidTest/`, which this tool does not run (skipped, or flagged on the verdict); or the guard is
inside a private helper whose callers are what the tests actually name (fixed by scoping through
the enclosing type).

## A finding is a hypothesis until a command proves it

This is the house rule, and it was learned the expensive way: the 2026-09-04 audit shipped a
compile break through a green CI run because `viewModel.deleteForever(ids)` in a composable
resolved to an *imported function* of that name. It read correctly. Only a compiler distinguished
it. `tools/audit.py` check 9 exists for exactly that, and CI now runs the unit tests beside the
static checks.

So: fix what a failing test, a red gate or a reproduced run has proved. Report everything else as a
hypothesis with the command that *would* prove it. This applies to claims about the environment as
much as to claims about the code — see the harness rule below.

## Both platforms, every finding

Two apps consume `shared/`. A defect in a path either reaches is usually present in both with
different spellings: Android's `reconcileAlarms` is the desktop's `DesktopReminderScheduler.replan`,
Android's `SyncCoordinator` is the desktop's `SyncBar`, the widgets have no desktop twin at all.
Check the other side before writing the row, and say what you found there even when the answer is
"no equivalent". Five audit runs recorded that a merged reminder is never armed on the phone and
none of them looked at the desktop's identical merge, which had the same defect.

## Conventions that bite

- **Line endings are LF in every source and doc file** — all 598 `.kt`, `.kts`, `.py`, `.md`,
  `.json`, `.toml`, `.xml` and `.yml` files, checked. The exceptions are deliberate or vendored:
  the three `gradlew.bat` (a batch file needs CRLF), `THIRD_PARTY_NOTICES/OFL-*.txt`, and eight
  `docs/mockups/*.html`. So write with `newline=""` in Python — it preserves whatever the file
  already has instead of imposing a guess. A `grep -c $''` matches every line and will tell you
  everything is CRLF; it is not evidence.
- **A `shared/` change owes a Revision Log row in both spec files** — `tendril-spec.md` and
  `Tendril windows/tendril-windows-spec.md` — plus the amendment in the home section the row points
  at. Comment-only changes get a row too, saying plainly that no decision changed.
- **`tools/audit.py` strips string literals**, so a declaration used only inside a Kotlin string
  template reads as dead code (check 3). Concatenate instead of suppressing, or amend the check
  with a test in `tools/tests`.
- **`shared/schemas/` is Room's KSP output directory** (`shared/build.gradle.kts:95`). An Android
  build deletes files there it did not generate — never park a scratch file in it. The 18 tracked
  exports survive.
- **A feature ends with a walk**, on the phone and the desktop, written up in
  `docs/critiques/<name>-function.md` as a tried/observed table. Hardware runs go through
  `uiautomator` dumps rather than screenshots, so the result is diffable.
- **Never launch the installed `Tendril.exe`.** A stale preview build shares the dev database and
  wipes it on launch. Front the dev window through `user32`; kill it by window title.

## Shells: three conventions on one machine

The single largest source of wasted commands here. Pick the form for the consumer, not the shell
you happen to be typing in:

| Consumer | Path form | Example |
|---|---|---|
| bash's own redirects, `cd`, globs | POSIX | `> /c/Users/.../out.log` |
| any argument to Windows Python | Windows | `python x.py "C:\Users\...\cases.jsonl"` |
| anything reaching `cmd.exe` (a `--cmd` string, `shell=True`) | Windows | `--cmd "python C:\...\stub.py"` |
| `adb` device paths under Git Bash | POSIX, with `MSYS_NO_PATHCONV=1` | see `adb` note below |

Bash heredocs mangle backslashes and non-ASCII: a script containing `\n`, `\\`, `§` or an arrow
should be written with the editor tool and then run, never pasted into `python - <<'EOF'`.

`adb`: export `MSYS_NO_PATHCONV=1` for device paths, pass `run-as sh -c` as one double-quoted
string, and md5-check every push and pull.

## Nested agents and harnesses

**`claude -p --allowedTools …` does not restrict what the child session may do.** A nested session
spawned from this repo inherits full permissions: on 2026-09-22 five of them ran Gradle builds,
edited `shared/` domain code, removed a dependency, wrote documents and wrote to agent memory —
while the parent session was telling the user the run was read-only. One survived the first kill
and was still editing minutes later.

- Never describe a nested-session harness as sandboxed. It is not.
- Sandbox it with a throwaway `git worktree`, or write cases that need no repository at all.
- `git status` before **and** after any such run.
- Kill strays by matching the full command line (`Get-CimInstance Win32_Process | … Stop-Process`);
  `Get-Process` on the name alone misses them. Confirm zero remain before reporting.
- Unreviewed machine output goes to `git stash -u`, never straight into the tree.

`tools/hooks/block_nested_agents.py` is a `PreToolUse` guard that blocks `claude -p` from Bash
here, with `tools/hooks/settings.hooks.json` holding the settings block that enables it (copy it
into `.claude/settings.json`, which is gitignored — hence the proposal living under `tools/`). It
is **inert until someone enables it**. If it fires, that is the rule working: do not route around
it. Its behaviour in both directions is pinned by `tools/tests/test_block_nested_agents.py`.

## Unattended runs

This repo's workflows assume someone is reading. When a question would normally be asked and there
is nobody to ask — no reply is possible, the run is unattended, the mode was inferred rather than
given — write the findings and stop. Do not edit, do not commit, do not write to memory. Reporting
an unfixed finding costs a session; an unreviewed edit to this app's sync or scheduling code costs
data, and the person finds out when something silently does not happen.
