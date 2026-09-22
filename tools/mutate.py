#!/usr/bin/env python3
"""
Does anything notice if this guard vanishes?

`tools/spec_trace.py` says which declarations no test *names*. That is a real gap but a weak
claim, because naming is not asserting: a test that mentions `launchAndTouch` and checks nothing
counts there exactly like one that pins it. The only thing that settles it is changing the code
and watching a test go red — which this repository does by hand on every fix and records in the
commit message, where nothing can re-check it later when someone deletes the assertion.

This does that one check, mechanically and on demand:

    delete one guard  ->  run only the tests naming that symbol  ->  expect red

    KILLED    a test failed. The guard is pinned. This is the good outcome.
    SURVIVED  every test still passed. Nothing in the suite notices if the guard goes.
    INVALID   the mutant did not compile, so the run says nothing either way.

Usage:

    python3 tools/mutate.py --symbol launchAndTouch --list   # what it would do, no build
    python3 tools/mutate.py --symbol launchAndTouch          # run it
    python3 tools/mutate.py --section 3.7 --list             # every guard a section's code carries
    python3 tools/mutate.py --restore                        # after a crash

**It never touches your working tree.** Every mutation is applied inside a throwaway `git
worktree`, and the run refuses to start unless that worktree is clean. `tools/hooks/` exists
because an unsupervised session once edited `shared/` while the parent reported the run as
read-only; a tool whose whole job is to break the code on purpose does not get to do that in the
tree you are working in.

**What it costs.** One mutation is one Gradle run. A guard in `shared/` recompiles the shared
module and the app: about **four minutes**. A guard in `Tendril android/` recompiles only the
app and is faster. So this is a deliberate question you ask about one claim, not a gate — a
section with eighteen declarations is an hour, and `--list` is there so you can choose.

**What it does not do.** One operator, `delete a single-line guard`: `if (…) return`,
`return@label`, `continue`, `break`. That is the defect shape this repository actually keeps
having — the alarm seam, the View-Only gates, the last-write-wins skip, the self-connection
refusal are all one line of guard — and deleting it is a mutation whose meaning is obvious. A
multi-line `if (…) { … }`, an elvis `?: return`, an arithmetic or boundary mutation: none are
supported, and they are reported as unsupported rather than silently skipped, so the absence of
a result is never mistaken for a pass.
"""
from __future__ import annotations

import argparse
import io
import os
import re
import subprocess
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import audit  # noqa: E402  - strip_literals, for brace counting that ignores text
import spec_trace  # noqa: E402  - declarations_citing, for --section

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WORKTREE = os.environ.get(
    "TENDRIL_MUTATE_WT", os.path.join(tempfile.gettempdir(), "tendril-mutate-wt")
)

PRODUCTION = (
    os.path.join("shared", "src"),
    os.path.join("Tendril android", "app", "src", "main"),
    os.path.join("Tendril windows", "src"),
)

# A guard that fits on one line and whose deletion has an obvious meaning: the refusal stops
# happening. `return@launchAndTouch`, `continue`, `break` all count — the last two are how the
# merge skips a record it should not write (the last-write-wins guard is a `continue`).
GUARD = re.compile(
    # `return` may carry a value: `return false`, `return null`, `return emptyList()` are guards
    # too, and requiring a bare `return` made the tool plan nothing for them while reporting "no
    # single-line guard in its body" — which reads like a clean result rather than a blind spot.
    # `[^{]` keeps the multi-line `if (…) {` form out, since deleting its first line orphans a
    # block and only ever produces INVALID.
    r"^(\s*)if\s*\(.+\)\s*(?:return|continue|break)(?:@\w+)?(?:\s+[^{]*?)?\s*(?://.*)?$"
)

# What `--list` shows as present-but-unsupported, so the silence is explained.
UNSUPPORTED = re.compile(r"\?:\s*(?:return|continue|break)(?:@\w+)?\b|^\s*if\s*\(.+\)\s*\{\s*$")

DECL_START = re.compile(
    r"^(\s*)(?:(?:public|private|internal|protected|abstract|open|override|suspend|inline|operator|data|sealed|value|expect|actual)\s+)*"
    r"(?:fun|class|object|interface)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)\b"
)


def say(*a):
    """Print and flush. stdout is block-buffered when piped, so a long run showed nothing at
    all until it finished — and a tool that looks hung is one people kill."""
    print(*a)
    sys.stdout.flush()


def run(cmd, cwd=None, timeout=1800):
    return subprocess.run(
        cmd, cwd=cwd, shell=isinstance(cmd, str),
        capture_output=True, text=True, errors="replace", timeout=timeout,
    )


def production_files():
    out = []
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in spec_trace.SKIP_DIRS]
        for f in files:
            if not f.endswith(".kt"):
                continue
            p = os.path.join(base, f)
            r = spec_trace.rel(p)
            if any(r.startswith(s.replace(os.sep, "/")) for s in PRODUCTION):
                out.append(p)
    return sorted(out)


def code_only(lines: list[str]) -> list[str]:
    """The same lines with comments and string literals blanked — one line in, one line out.

    `audit.strip_literals` does this job better for its own purposes but consumes a block
    comment's newlines along with its text, so its output has fewer lines than its input. Indexing
    that against the raw lines made `addEdge`'s body never close: brace depth was read off
    whichever line had drifted into position, and the range swallowed the next three functions
    (and their KDoc, which is how a `?: return` written in prose came to be listed as code).
    Line numbers are the whole currency here, so the blanker has to preserve them.
    """
    out, in_block = [], False
    for line in lines:
        buf, i, n = [], 0, len(line)
        while i < n:
            if in_block:
                if line.startswith("*/", i):
                    in_block = False
                    i += 2
                else:
                    i += 1
                continue
            if line.startswith("//", i):
                break
            if line.startswith("/*", i):
                in_block = True
                i += 2
                continue
            if line[i] == '"':
                i += 1
                while i < n and line[i] != '"':
                    i += 2 if line.startswith("\\", i) else 1
                i += 1
                continue
            if line[i] == "'":
                i += 4 if line.startswith("\\", i + 1) else 3
                continue
            buf.append(line[i])
            i += 1
        out.append("".join(buf))
    return out


def find_declaration(symbol: str):
    """Locate `symbol`'s declaration and the line range of its body.

    Braces are counted over [code_only], so a `{` inside a KDoc or a `"…${…}…"` template cannot
    close the body early or hold it open.
    """
    for path in production_files():
        raw = io.open(path, encoding="utf-8", errors="replace").read()
        lines = raw.split("\n")
        blanked = code_only(lines)
        for i, line in enumerate(lines):
            m = DECL_START.match(line)
            if not m or m.group(2) != symbol:
                continue
            depth, started, end = 0, False, None
            for j in range(i, min(i + 400, len(lines))):
                probe = blanked[j] if j < len(blanked) else ""
                depth += probe.count("{") - probe.count("}")
                if "{" in probe:
                    started = True
                if started and depth <= 0:
                    end = j
                    break
            if end is None:
                end = min(i + 400, len(lines) - 1)
            return path, i, end
    return None


def guards_in(path: str, start: int, end: int):
    """Guards inside one declaration's body, matched against code rather than prose.

    Matching the raw lines listed a `?: return` that appeared in a sentence *about* the elvis
    operator in a KDoc block. A comment cannot be a guard, so the match runs on [code_only] and
    the raw line is kept only for display.
    """
    lines = io.open(path, encoding="utf-8", errors="replace").read().split("\n")
    blanked = code_only(lines)
    found, skipped = [], []
    for j in range(start, min(end + 1, len(lines))):
        if GUARD.match(blanked[j]):
            found.append((j + 1, lines[j].strip()))
        elif UNSUPPORTED.search(blanked[j]):
            skipped.append((j + 1, lines[j].strip()))
    return found, skipped


def owner_of(path: str, line_index: int) -> str | None:
    """The top-level type enclosing `line_index` — what a test constructs to reach inside it.

    Scoping a mutation to tests naming the mutated declaration alone is right for a public entry
    point and wrong for everything a private helper does. The last-write-wins `continue` lives in
    `private suspend fun mergeEntryContent`; the suites that exercise it call
    `orchestrator.readAndMerge(...)` and never write the private name, so the scope collapsed to
    one unrelated class and the guard came back SURVIVED — an artefact reported as a finding.

    Scanning *backward* from the declaration rather than forward from the top of the file, because
    a file often opens with something else: `SnapshotSyncOrchestrator.kt` begins with
    `data class SnapshotMergeResult`, and taking the first match attributed every symbol in it to
    that instead of to the orchestrator.
    """
    lines = io.open(path, encoding="utf-8", errors="replace").read().split("\n")
    for j in range(min(line_index, len(lines) - 1), -1, -1):
        m = DECL_START.match(lines[j])
        if m and not m.group(1) and re.search(r"\b(?:class|object|interface)\b", lines[j]):
            return m.group(2)
    return None


def test_classes_naming(symbol: str):
    """Test classes that mention the symbol, split by which suite they live in.

    Returns `(unit, instrumented)`. Only the first is the scope of a run: `testDebugUnitTest`
    never sees `androidTest/`. The split exists because "nothing names this" and "the only thing
    naming this is a test no gate runs" are different facts and lead somewhere different.
    `OverdueAlarmReceiver` is the second kind — `AlarmSchedulerInstrumentedTest` names it, and
    that suite is reachable only through `adb shell am instrument`, since AGP routes
    `connectedAndroidTest` through artifacts this offline cache does not have.
    """
    unit, instrumented = [], []
    pat = re.compile(r"\b%s\b" % re.escape(symbol))
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in spec_trace.SKIP_DIRS]
        r = spec_trace.rel(base) + "/"
        bucket = unit if "/test/" in r else (instrumented if "/androidTest/" in r else None)
        if bucket is None:
            continue
        for f in files:
            if not f.endswith(".kt"):
                continue
            src = io.open(os.path.join(base, f), encoding="utf-8", errors="replace").read()
            if not pat.search(src):
                continue
            pkg = re.search(r"^package\s+([\w.]+)", src, re.M)
            for cls in re.finditer(r"^(?:internal\s+|private\s+)?class\s+(\w*Test)\b", src, re.M):
                bucket.append("%s.%s" % (pkg.group(1), cls.group(1)) if pkg else cls.group(1))
    return sorted(set(unit)), sorted(set(instrumented))


# --------------------------------------------------------------------- the worktree

def worktree_ready(verbose=True):
    """A clean, synced worktree at HEAD, with the gitignored files a build needs."""
    head = run(["git", "rev-parse", "HEAD"], cwd=ROOT).stdout.strip()
    if not os.path.isdir(os.path.join(WORKTREE, ".git")) and not os.path.isfile(os.path.join(WORKTREE, ".git")):
        if verbose:
            print("creating the mutation worktree at %s (first run is slower)" % WORKTREE)
        r = run(["git", "worktree", "add", "--detach", WORKTREE, head], cwd=ROOT)
        if r.returncode:
            print("could not create the worktree:\n" + r.stderr)
            return False

    # Hard reset, not `checkout -- .`: the latter leaves a staged change and any file carried in
    # by a previous run behind, and the next run's `git apply` then failed with "patch does not
    # apply" against residue rather than against HEAD. This is a scratch copy — nothing here is
    # anyone's work, and a half-reset worktree silently tests the wrong tree.
    run(["git", "checkout", "--detach", head], cwd=WORKTREE)
    run(["git", "reset", "--hard", head], cwd=WORKTREE)
    # `-fd` and not `-fdx`: ignored files stay, because `local.properties` is one of them and a
    # build without it fails before it compiles anything.
    run(["git", "clean", "-fd"], cwd=WORKTREE)

    # Mirror the working tree, not just the last commit. The question this tool answers is
    # "does anything notice if this guard goes", and it is usually asked about code that is
    # still being written — including the test just added to close a SURVIVED. Syncing to HEAD
    # alone would run the old suite against the new code and quietly answer the wrong question.
    # Bytes, never text. `subprocess` with `text=True` decodes using the locale encoding — cp1252
    # on this machine — and this repository's comments are full of `§` and em-dashes. Round-
    # tripping them through cp1252 mangled the patch, and `git apply` rejected it with "patch
    # does not apply", which reads like worktree residue rather than an encoding bug.
    patch = subprocess.run(
        ["git", "diff", "HEAD"], cwd=ROOT, capture_output=True
    ).stdout
    carried = 0
    if patch.strip():
        tmp = os.path.join(tempfile.gettempdir(), "tendril-mutate-uncommitted.patch")
        with open(tmp, "wb") as fh:
            fh.write(patch)
        r = run(["git", "apply", tmp], cwd=WORKTREE)
        if r.returncode:
            print("could not carry uncommitted changes into the worktree:\n" + r.stderr)
            return False
        carried = len([l for l in patch.split(b"\n") if l.startswith(b"+++ ")])
    untracked = [
        f for f in run(["git", "ls-files", "--others", "--exclude-standard"], cwd=ROOT).stdout.split("\n")
        if f.strip().endswith((".kt", ".kts"))
    ]
    for f in untracked:
        src, dst = os.path.join(ROOT, f), os.path.join(WORKTREE, f)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        io.open(dst, "w", encoding="utf-8", newline="").write(
            io.open(src, encoding="utf-8", newline="").read()
        )
        carried += 1
    if verbose:
        print("worktree at %s%s" % (head[:7],
                                    " + %d uncommitted file(s)" % carried if carried else " (clean tree)"))

    # local.properties is gitignored, so a fresh worktree has no SDK location and every Gradle
    # invocation fails before it compiles anything. Seed it from the real tree.
    for rel_p in ("local.properties", os.path.join("Tendril android", "local.properties"),
                  os.path.join("shared", "local.properties"),
                  os.path.join("Tendril windows", "local.properties")):
        src = os.path.join(ROOT, rel_p)
        dst = os.path.join(WORKTREE, rel_p)
        if os.path.isfile(src) and not os.path.isfile(dst):
            io.open(dst, "w", encoding="utf-8", newline="").write(
                io.open(src, encoding="utf-8", newline="").read()
            )
    return True


def restore():
    if not os.path.isdir(WORKTREE):
        print("no worktree at %s — nothing to restore" % WORKTREE)
        return 0
    run(["git", "checkout", "--", "."], cwd=WORKTREE)
    left = run(["git", "status", "--porcelain"], cwd=WORKTREE).stdout.strip()
    print("worktree restored" if not left else "still dirty after restore:\n" + left)
    return 0 if not left else 1


# --------------------------------------------------------------------- one mutation

def apply_deletion(rel_path: str, lineno: int) -> str:
    """Comment the guard out rather than removing the line, so every later line keeps its
    number and a compiler error points where a reader expects."""
    p = os.path.join(WORKTREE, rel_path)
    s = io.open(p, encoding="utf-8", newline="").read()
    lines = s.split("\n")
    original = lines[lineno - 1]
    indent = len(original) - len(original.lstrip())
    lines[lineno - 1] = original[:indent] + "// MUTANT-DELETED: " + original.strip()
    io.open(p, "w", encoding="utf-8", newline="").write("\n".join(lines))
    return original


def run_scoped(classes, quiet=True):
    # Absolute: Windows does not resolve a relative executable against `cwd`, so "gradlew.bat"
    # with cwd set to the module is a FileNotFoundError rather than a build.
    module = os.path.join(WORKTREE, "Tendril android")
    gradlew = os.path.join(module, "gradlew.bat" if os.name == "nt" else "gradlew")
    cmd = [gradlew, "--offline", ":app:testDebugUnitTest"]
    for c in classes:
        cmd += ["--tests", c]
    r = run(cmd, cwd=module)
    out = r.stdout + r.stderr
    if r.returncode == 0:
        return "SURVIVED", out
    if re.search(r"^e: |error: |Compilation error", out, re.M):
        return "INVALID", out
    if "FAILED" in out or "tests completed" in out:
        return "KILLED", out
    return "INVALID", out


def first_failure(out: str) -> str:
    m = re.search(r"^(\S.*? > .*?) FAILED$", out, re.M)
    return m.group(1).strip() if m else ""


def mutate_one(rel_path, lineno, text, classes, index, total):
    say("\n[%d/%d] %s:%d" % (index, total, rel_path, lineno))
    say("        %s" % text)
    started = time.time()
    original = apply_deletion(rel_path, lineno)
    try:
        verdict, out = run_scoped(classes)
    finally:
        p = os.path.join(WORKTREE, rel_path)
        s = io.open(p, encoding="utf-8", newline="").read().split("\n")
        s[lineno - 1] = original
        io.open(p, "w", encoding="utf-8", newline="").write("\n".join(s))
    mins = (time.time() - started) / 60.0
    note = first_failure(out) if verdict == "KILLED" else ""
    say("        %-9s %.1f min  %s" % (verdict, mins, note))
    return verdict


# --------------------------------------------------------------------- cli

def targets_for(args):
    """[(symbol, path, start, end)] for whatever was asked for."""
    if args.symbol:
        hit = find_declaration(args.symbol)
        if not hit:
            print("no production declaration named %r" % args.symbol)
            return []
        path, start, end = hit
        return [(args.symbol, path, start, end)]

    decls = spec_trace.declarations_citing(args.section)
    if not decls:
        print("no production declaration cites section %s" % args.section)
        return []
    out = []
    for name in sorted(decls):
        hit = find_declaration(name)
        if hit:
            out.append((name, hit[0], hit[1], hit[2]))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--symbol", help="one declaration, by name")
    g.add_argument("--section", help="every declaration citing this spec section")
    g.add_argument("--restore", action="store_true", help="clean the worktree after a crash")
    ap.add_argument("--list", action="store_true", help="show what would run; build nothing")
    ap.add_argument("--max", type=int, default=0, help="stop after this many mutations")
    args = ap.parse_args()

    if args.restore:
        return restore()

    found = targets_for(args)
    if not found:
        return 1

    plan = []
    for symbol, path, start, end in found:
        guards, skipped = guards_in(path, start, end)
        classes, instrumented = test_classes_naming(symbol)
        owner = owner_of(path, start)
        if owner and owner != symbol:
            o_unit, o_instr = test_classes_naming(owner)
            classes = sorted(set(classes) | set(o_unit))
            instrumented = sorted(set(instrumented) | set(o_instr))
        rel_path = spec_trace.rel(path)
        if args.list:
            print("\n%s  (%s:%d)" % (symbol, rel_path, start + 1))
            if not guards and not skipped:
                print("   no single-line guard in its body")
            for no, text in guards:
                print("   guard   :%-5d %s" % (no, text[:100]))
            for no, text in skipped:
                print("   skipped :%-5d %s" % (no, text[:80]))
            if guards:
                if classes:
                    print("   tests   %s" % ", ".join(c.split(".")[-1] for c in classes))
                elif instrumented:
                    print("   tests   NONE in the unit suite; named only by %s, which runs on the"
                          " phone via `am instrument` and not here."
                          % ", ".join(c.split(".")[-1] for c in instrumented))
                else:
                    print("   tests   NONE — a mutation here cannot be killed by anything")
        for no, text in guards:
            plan.append((symbol, rel_path, no, text, classes, instrumented))

    if args.list:
        shared = sum(1 for p in plan if p[1].startswith("shared/"))
        print("\n%d mutation(s); roughly %d min (%d in shared/ at ~4 min, %d elsewhere at ~2)"
              % (len(plan), shared * 4 + (len(plan) - shared) * 2, shared, len(plan) - shared))
        return 0

    if args.max:
        plan = plan[: args.max]
    runnable = [p for p in plan if p[4]]
    for symbol, rel_path, no, text, classes, instrumented in plan:
        if classes:
            continue
        if instrumented:
            print("\n%s %s:%d — no *unit* test names this symbol, only %s, which this tool "
                  "does not run. Skipped: a SURVIVED here would be an artefact of the scope rather "
                  "than a finding." % (symbol, rel_path, no,
                                       ", ".join(c.split(".")[-1] for c in instrumented)))
        else:
            print("\n%s %s:%d — no test names this symbol at all, so nothing could kill a "
                  "mutation here. Skipped; that is spec_trace's finding, not this tool's."
                  % (symbol, rel_path, no))

    if not runnable:
        print("\nnothing to run.")
        return 0
    if not worktree_ready():
        return 1

    print("\n%d mutation(s), one Gradle run each, in %s" % (len(runnable), WORKTREE))
    tally = {}
    for i, (symbol, rel_path, no, text, classes, _instr) in enumerate(runnable, 1):
        v = mutate_one(rel_path, no, text, classes, i, len(runnable))
        tally[v] = tally.get(v, 0) + 1

    print("\n" + "  ".join("%s %d" % (k, v) for k, v in sorted(tally.items())))
    if tally.get("SURVIVED"):
        print("A SURVIVED guard is the finding: it can be deleted and the suite stays green.")

    # Not `git status` — the worktree is deliberately dirty, carrying your uncommitted work.
    # What must not survive is a mutation, so look for the marker itself.
    # `:!tools/` because this file contains the marker as a literal, and matching itself made
    # every clean run end on a warning telling you to restore a worktree that was already fine.
    stragglers = run(
        ["git", "grep", "-l", "MUTANT-DELETED", "--", ":!tools/"], cwd=WORKTREE
    ).stdout.strip()
    if stragglers:
        print("WARNING: a mutation was left in place — run `python tools/mutate.py --restore`\n"
              + stragglers)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
