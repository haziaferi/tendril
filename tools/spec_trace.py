#!/usr/bin/env python3
"""
Where the spec promises something and nothing visibly checks it.

`tools/audit.py` asks whether the code is tidy. This asks a different question: of the
claims `tendril-spec.md` makes about how the app behaves, which ones does anything cite?

    python3 tools/spec_trace.py                  # the ranked worklist
    python3 tools/spec_trace.py --section 3.7    # that section's claims, as a checklist
    python3 tools/spec_trace.py --all            # every section, not just the top of the list
    python3 tools/spec_trace.py --json           # the same numbers, for a script

**This ranks candidates. It does not measure coverage.** A test pins a claim by asserting
on behaviour, not by naming a section in its KDoc — `RegisterSolveTest` holds §2.3's contrast
ratios without writing "§2.3" anywhere. So a zero in the `test` column means "nothing here
announces itself", which is a good place to point a reading and a bad thing to quote as a
coverage figure. Read a section before believing its row.

Why it is not part of `audit.py`'s PASS/FAIL gate: an unpinned claim is a question, not a
regression. A check that fails on every honest tree gets suppressed within a week, and then
the real findings go with it.

Background: both recorded `--full` audits of this repo say "Spec trace — **not run**: no
`--against` given". Seventeen findings, every one of them reachable by reading code alone.
This exists so the pass that reads the *spec* has somewhere to start.
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".claude"}
SOURCE_ROOTS = (
    os.path.join("shared", "src"),
    os.path.join("Tendril android", "app", "src"),
    os.path.join("Tendril windows", "src"),
)
SPECS = ("tendril-spec.md", os.path.join("Tendril windows", "tendril-windows-spec.md"))

# A heading like `### 9.4.1 Portable export/import (Decided …)`.
HEADING = re.compile(r"^#{2,3}\s+(\d+(?:\.\d+)*)\.?\s+(.*)$")

# A sentence that promises behaviour. Deliberately narrow: "should" and "may" are
# discussion, and a spec this long has plenty of both.
NORMATIVE = re.compile(
    r"\b(?:must not|must|never|always|every|cannot|shall|refuses? to|guarantees?)\b", re.I
)

# `§9.7`, `§ 9.7`, `§9.9` — the way the code cites this document 1,852 times over.
CITATION = re.compile("§\\s?(\\d+(?:\\.\\d+)*)")

# Sections that record context, history or open questions rather than behaviour the app
# owes. Named one by one, with the reason, so the exclusions can be argued with — a
# heuristic here would quietly swallow real promises. §0.6 is deliberately NOT in this
# list: its rows carry "Acceptance:" criteria and are the most checkable text in the file.
META = {
    "0.1": "constraints on the project, not behaviour of the app",
    "0.2": "purpose and non-goals, prose",
    "0.3": "blast radius of a past pass, history",
    "0.4": "the bar, a standard for writing rather than for the app",
    "0.5": "principles",
    "0.7": "explicitly out of scope, by definition unimplemented",
    "0.9": "risks",
    "0.10": "open items, tracked elsewhere and open by definition",
    "0.11": "how this file relates to the others",
    "8.5": "reference research into another app",
    "10": "open questions, consolidated; resolved ones moved to their home sections",
    "11": "document notes",
    "12": "a pointer to the desktop spec, which is traced on its own",
}


def rel(p: str) -> str:
    return os.path.relpath(p, ROOT).replace(os.sep, "/")


def walk(exts: tuple[str, ...]) -> list[str]:
    out = []
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        out += [os.path.join(base, f) for f in files if f.endswith(exts)]
    return sorted(out)


def strip_strings(src: str) -> str:
    """Blank string literals, keep comments.

    The opposite of `audit.py`'s `strip_literals`, and for the opposite reason: almost every
    citation in this codebase lives in a KDoc block, so blanking comments would blank the
    measurement. What has to go is a *runtime* string — a user-facing message naming a
    section is not the code implementing it.

    Comments are skipped rather than stripped, which is the whole difficulty. A KDoc line
    reading `* structure" (§5.2's Database …)` has one unbalanced quote in it; a stripper
    that did not know it was inside a comment would treat it as an opening quote and swallow
    the rest of the file. That is not hypothetical — `PageCanvas.kt`, `Habit.kt`, `Block.kt`
    and five others all have one, and it is the same shape as the `'"'` char literal that
    once made every declaration after `Ics.kt` read as dead.
    """
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j == -1 else j
            out.append(src[i:j])
            i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append(src[i:j])
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = n if j == -1 else j + 3
            out.append('""')
        elif src[i] == "'":
            # A char literal, including the escaped form. Same trap as above.
            step = 4 if src.startswith("\\", i + 1) else 3
            out.append("''")
            i += step
        elif src[i] == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src.startswith("\\", i) else 1
            i += 1
            out.append('""')
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


class Section:
    def __init__(self, num: str, title: str, spec: str, line: int):
        self.num = num
        self.title = title
        self.spec = spec
        self.line = line
        self.claims: list[tuple[int, str]] = []

    @property
    def meta(self) -> bool:
        return self.num in META or any(
            self.num.startswith(m + ".") for m in META if "." not in m
        )


def read_sections() -> list[Section]:
    """Every numbered section of both specs, with the normative lines under it.

    Fenced code blocks are skipped: a snippet is an illustration of a claim, never a second
    one, and counting it would weight a section by how much code it quotes.
    """
    found: list[Section] = []
    for spec in SPECS:
        path = os.path.join(ROOT, spec)
        if not os.path.exists(path):
            continue
        current, fenced = None, False
        for no, line in enumerate(io.open(path, encoding="utf-8"), 1):
            if line.lstrip().startswith("```"):
                fenced = not fenced
                continue
            if fenced:
                continue
            head = HEADING.match(line)
            if head:
                current = Section(head.group(1), head.group(2).strip(), spec, no)
                found.append(current)
            elif current is not None and NORMATIVE.search(line):
                current.claims.append((no, line.strip()))
    return found


def read_citations() -> tuple[Counter, Counter]:
    """Citations of each section number, split into production source and tests."""
    src, test = Counter(), Counter()
    for path in walk((".kt", ".kts")):
        r = rel(path)
        if not any(r.startswith(s.replace(os.sep, "/")) for s in SOURCE_ROOTS):
            continue
        body = strip_strings(io.open(path, encoding="utf-8", errors="replace").read())
        bucket = test if ("/test/" in r or "/androidTest/" in r) else src
        for m in CITATION.finditer(body):
            bucket[m.group(1)] += 1
    return src, test


def roll(counter: Counter, num: str) -> int:
    """A citation of §9.4.1 is a citation of §9.4 — the code cites the leaf, not the parent."""
    return counter[num] + sum(v for k, v in counter.items() if k.startswith(num + "."))


def rows(sections, src, test, keep_meta=False):
    out = []
    for s in sections:
        if not s.claims or (s.meta and not keep_meta):
            continue
        sc, tc = roll(src, s.num), roll(test, s.num)
        out.append(
            {
                "section": s.num,
                "title": s.title,
                "spec": s.spec.replace(os.sep, "/"),
                "line": s.line,
                "claims": len(s.claims),
                "src": sc,
                "test": tc,
                "score": round(len(s.claims) / (1 + tc), 2),
            }
        )
    out.sort(key=lambda r: (-r["score"], -r["claims"]))
    return out


def ascii_(s: str, width: int) -> str:
    """Windows consoles here are cp1252 and choke on an arrow in a section title."""
    return s.encode("ascii", "replace").decode()[:width]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--section", help="print one section's claims as a checklist")
    ap.add_argument("--all", action="store_true", help="every section, not the top 20")
    ap.add_argument("--meta", action="store_true", help="include the excluded meta sections")
    ap.add_argument("--json", action="store_true", help="machine-readable")
    args = ap.parse_args()

    sections = read_sections()

    if args.section:
        hit = [s for s in sections if s.num == args.section]
        if not hit:
            print("no section %s; try --all for the list" % args.section)
            return 1
        s = hit[0]
        src, test = read_citations()
        print("%s %s" % (s.num, ascii_(s.title, 70)))
        print("%s:%d  |  %d claims, %d source citations, %d test citations"
              % (s.spec.replace(os.sep, "/"), s.line, len(s.claims), roll(src, s.num), roll(test, s.num)))
        if s.meta:
            print("excluded from the worklist: %s" % META.get(s.num, "meta"))
        print()
        for no, text in s.claims:
            print("  [ ] %s:%-5d %s" % (s.spec.replace(os.sep, "/"), no, ascii_(text, 150)))
        return 0

    src, test = read_citations()
    data = rows(sections, src, test, keep_meta=args.meta)

    if args.json:
        json.dump(data, sys.stdout, indent=2)
        print()
        return 0

    shown = data if args.all else data[:20]
    print("%-7s %-46s %7s %6s %5s" % ("section", "title", "claims", "src", "test"))
    for r in shown:
        print("%-7s %-46s %7d %6d %5d"
              % (r["section"], ascii_(r["title"], 46), r["claims"], r["src"], r["test"]))
    total = sum(r["claims"] for r in data)
    print()
    print("%d sections, %d normative claims%s"
          % (len(data), total, "" if args.all else "; top %d of %d shown" % (len(shown), len(data))))
    print("%d meta sections excluded (--meta to include, --section N to read one)" % len(META))
    print("Ranks candidates, not coverage: a test pins a claim by asserting, not by citing.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
