#!/usr/bin/env python3
"""
The figures this repository's documents keep quoting, measured on demand.

    python tools/facts.py            # every figure, one line each
    python tools/facts.py --json     # machine-readable

Why this exists: a count written into prose is true on the day it is written and silently false
after. By 2026-09-24 the docs said 598 LF files (615), four and five nested sessions (eighteen),
261, 321 and 596 unit tests (956), "schema v24" beside `version = 25`, and a `--unpinned` flag
the tool never had. Each was a recollection standing where a measurement belonged. So documents
cite this command instead of a number, and a session that needs the number runs it.

It is a lookup, not a gate: nothing here can fail a build. A figure that moved is not a
regression, and a check that fails on every honest tree gets suppressed (see `CLAUDE.md`).
"""
from __future__ import annotations
import argparse, glob, json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_EXT = (".kt", ".kts", ".py", ".md", ".json", ".jsonl", ".toml", ".xml", ".yml")
ROW = re.compile(r"^\| (\d+\.\d+) \|")
DB_FILE = "shared/src/commonMain/kotlin/com/tendril/app/data/TendrilDatabase.kt"
JUNIT = {
    "android": "Tendril android/app/build/test-results/testDebugUnitTest/*.xml",
    "windows": "Tendril windows/build/test-results/test/*.xml",
}


def tracked(root: str) -> list[str]:
    out = subprocess.run(["git", "ls-files", "-z"], cwd=root, capture_output=True, check=True).stdout
    return [p for p in out.decode("utf-8").split("\0") if p]


def line_endings(root: str, files: list[str]) -> tuple[list[str], list[str]]:
    """Source/doc files split by the bytes: any CRLF makes a file CRLF."""
    lf, crlf = [], []
    for rel in files:
        if not rel.endswith(SOURCE_EXT):
            continue
        try:
            with open(os.path.join(root, rel), "rb") as f:
                data = f.read()
        except OSError:
            continue
        (crlf if b"\r\n" in data else lf).append(rel)
    return lf, crlf


def audit_rows(path: str) -> dict:
    """Rows of an audit table, by what their status proves. "Open" in prose has meant both
    *unproven* and *unfixed*; these are kept apart."""
    hyp, unchanged, done, total = [], [], 0, 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = ROW.match(line)
            if not m:
                continue
            cells = [c.strip() for c in line.split(" | ")]
            status = cells[3].strip("* ").lower() if len(cells) > 3 else ""
            total += 1
            if status.startswith("hypothesis"):
                hyp.append(m.group(1))
            elif "not changed" in status or "not fixed" in status:
                unchanged.append(m.group(1))
            else:
                done += 1
    return {"total": total, "hypothesis": hyp, "not_changed": unchanged, "fixed_or_clean": done}


def junit_counts(pattern: str) -> tuple[int, int, int, float]:
    files = tests = failures = 0
    newest = 0.0
    for f in glob.glob(pattern):
        with open(f, encoding="utf-8") as h:
            head = h.read(4000)
        m = re.search(r'tests="(\d+)".*?failures="(\d+)"', head)
        if m:
            files += 1
            tests += int(m.group(1))
            failures += int(m.group(2))
            newest = max(newest, os.path.getmtime(f))
    return files, tests, failures, newest


def schema_version(path: str) -> int:
    with open(path, encoding="utf-8") as f:
        m = re.search(r"@Database\((?:.|\n)*?\bversion\s*=\s*(\d+)", f.read())
    return int(m.group(1)) if m else -1


def latest_audit(root: str) -> str:
    docs = sorted(glob.glob(os.path.join(root, "docs", "audit-????-??-??.md")))
    return docs[-1]


def gather(root: str) -> dict:
    files = tracked(root)
    lf, crlf = line_endings(root, files)
    audit = latest_audit(root)
    return {
        "tracked": len(files),
        "lf": lf,
        "crlf": crlf,
        "schema_version": schema_version(os.path.join(root, DB_FILE)),
        "schema_exports": sum(1 for p in files if re.fullmatch(r"shared/schemas/.*/\d+\.json", p)),
        "audit_doc": os.path.relpath(audit, root).replace(os.sep, "/"),
        "audit": audit_rows(audit),
        "junit": {k: junit_counts(os.path.join(root, v)) for k, v in JUNIT.items()},
    }


def spec_totals(root: str) -> dict:
    r = subprocess.run([sys.executable, os.path.join(root, "tools", "spec_trace.py"), "--all", "--json"],
                       cwd=root, capture_output=True, text=True, encoding="utf-8")
    rows = json.loads(r.stdout) if r.returncode == 0 else []
    return {"sections": len(rows), "claims": sum(x["claims"] for x in rows),
            "unnamed": sum(len(x.get("unnamed", [])) for x in rows)}


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[1])
    ap.add_argument("--json", action="store_true", help="machine-readable")
    args = ap.parse_args()
    f = gather(ROOT)
    f["spec_trace"] = spec_totals(ROOT)
    if args.json:
        print(json.dumps(f, ensure_ascii=False, indent=1))
        return 0
    a = f["audit"]
    print(f"tracked files         {f['tracked']}")
    print(f"LF source/doc files   {len(f['lf'])}  ({', '.join(SOURCE_EXT)})")
    print(f"CRLF source/doc files {len(f['crlf'])}  {' '.join(f['crlf']) or ''}")
    print(f"Room schema version   {f['schema_version']}  ({f['schema_exports']} tracked exports)")
    print(f"audit rows            {a['total']} in {f['audit_doc']}: "
          f"{len(a['hypothesis'])} unproven ({', '.join(a['hypothesis'])}), "
          f"{len(a['not_changed'])} proved but not changed ({', '.join(a['not_changed'])}), "
          f"{a['fixed_or_clean']} fixed or clean")
    for k, (n, t, fl, newest) in f["junit"].items():
        import time
        when = time.strftime("%Y-%m-%d %H:%M", time.localtime(newest)) if newest else "never"
        print(f"{k + ' unit tests':<21} {t} in {n} files, {fl} failures (XML newest {when}; "
              f"check the stamp against your last edit before calling it a run)")
    s = f["spec_trace"]
    print(f"spec trace            {s['sections']} sections, {s['claims']} claims, "
          f"{s['unnamed']} declarations named by no unit test")
    return 0


if __name__ == "__main__":
    sys.exit(main())
