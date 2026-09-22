#!/usr/bin/env python3
"""
Where the spec promises something and nothing visibly checks it.

`tools/audit.py` asks whether the code is tidy. This asks a different question: of the
claims `tendril-spec.md` makes about how the app behaves, which ones does anything cite?

    python3 tools/spec_trace.py                  # the ranked worklist
    python3 tools/spec_trace.py --section 3.7    # that section's claims, and what nothing tests
    python3 tools/spec_trace.py --all            # every section, not just the top of the list
    python3 tools/spec_trace.py --json           # the same numbers, for a script

**What the `unnamed` column is, and what it is not.** For each section it resolves the
production declarations that cite it — a KDoc `§` sits directly above the thing it describes —
and reports the ones no unit test mentions by name, with file and line. That is a list of
places nothing is looking, and it is actionable: `§9.7` names `EntryActionReceiver`, whose
missing App Lock check §3.6 records as an open hole, and `HabitsWidget.onAction`, the Glance
callback of audit row 1.12.

The first version of this file counted `§` citations in *test* KDoc instead, and that was the
wrong side of the asymmetry: announcing which claim a test pins is optional and rare, while
production citations are dense (1,852) and deliberate. §3.7 read **zero** under that measure
while nine cases in `ViewOnlySurfacesGuardTest` held its View-Only half, none of them writing
"§3.7". The number sent a reading to the right section for the wrong reason.

**Naming is still not asserting.** A test that mentions `launchAndTouch` and asserts nothing
counts here exactly like one that pins it. Only changing the code and watching a test go red
settles that — this repository does it by hand on every fix and records the red message in the
commit, which no command can check. So: an unnamed declaration is genuinely unexamined; a named
one is merely *not obviously* unexamined. Read it before believing it.

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
    "0": "the umbrella over 0.1-0.11, which are traced individually; every file cites it as the anti-drift marker",
    "0.1": "constraints on the project, not behaviour of the app",
    "0.2": "purpose and non-goals, prose",
    "0.3": "blast radius of a past pass, history",
    "0.4": "the bar, a standard for writing rather than for the app",
    "0.5": "principles",
    "0.7": "explicitly out of scope, by definition unimplemented",
    "0.8": "the order the work was done in; every file cites it and it promises no behaviour",
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
        """Exact membership, never a prefix.

        A `startswith` roll-up looks tidier and is wrong here: adding "0" to exclude the
        umbrella immediately swallowed §0.6, the one child deliberately kept for its
        "Acceptance:" rows. The file's stated principle is that exclusions are named one by one
        so they can be argued with; a prefix rule is the heuristic that principle rejects.
        """
        return self.num in META

    @property
    def primary(self) -> bool:
        """Whether `§N` in source code means *this* section.

        The two specs number independently — both have a §3 and a §5 — and the `§` convention in
        Kotlin refers to `tendril-spec.md`. Feeding the desktop spec's §3 ("Follow-up risk") the
        citations belonging to the Android spec's §3 ("Page-by-Page Functional Spec") produced two
        identical-looking rows with the same 202 declarations under them. Its claims are still
        readable with `--section`; they are kept out of the ranking rather than misattributed.
        """
        return self.spec == "tendril-spec.md"


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


DECL = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:(?:public|private|internal|protected|abstract|open|override|suspend|inline|operator|data|sealed|value|expect|actual)\s+)*"
    r"(?:fun|class|object|interface|val|var)\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)"
)

# Between a KDoc citation and the declaration it describes there is only more comment,
# annotations, or blank space. Anything else means the citation was not a header for it.
BRIDGE = re.compile(r"^\s*(?:$|//|/\*|\*|@\w)")

# What can *enclose* a citation, as opposed to merely precede it. A `val` or `var` cannot: a
# citation inside a function body sat one line under `val x = 1` and attributed to `x`, which is
# a local, not the thing the claim is about. Structural declarations only.
ENCLOSING = re.compile(
    r"^(\s*)(?:(?:public|private|internal|protected|abstract|open|override|suspend|inline|data|sealed|value|expect|actual)\s+)*"
    r"(?:fun|class|object|interface)\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)"
)


def _cited_here(line: str, section: str) -> bool:
    """Exact match, deliberately — no roll-up.

    `roll()` folds children into a parent because a *citation count* is a measure of attention and
    a leaf's attention belongs to its parent too. Attribution is the opposite: a declaration
    citing §3.2 is the home of §3.2's claims, not of §3's. Rolling up here put 301 declarations
    under "§0 Objectives" (every file carries the anti-drift marker) and 202 under "§3", burying
    every leaf section that anyone can act on.
    """
    return any(m.group(1) == section for m in CITATION.finditer(line))


def declarations_citing(section: str) -> dict[str, str]:
    """The production declarations a section's citations sit on, as {name: "file:line"}.

    This is the measurement that matters, and it exists because the obvious one is wrong. Ranking
    by `§` citations in *test* KDoc asks whether a test announces which claim it pins — which is
    optional, rare, and unrelated to whether it asserts anything. §3.7 read zero while nine cases
    in `ViewOnlySurfacesGuardTest` held its View-Only half, none of them writing "§3.7".

    Production citations are the dense side: 1,852 of them, deliberate, and written directly above
    the declaration they describe. So resolve the section to *symbols* and ask whether the tests
    name those. Still a proxy — naming a symbol is not asserting on its behaviour, and only a
    mutation would settle that — but a far tighter one, and its output is a list of specific
    declarations rather than a number about a section.

    Two attribution rules, because citations appear in two places:
      - in a KDoc or comment, with nothing but comment/annotation/blank before the next
        declaration -> it is that declaration's header. Attribute forward.
      - anywhere else (inside a body, on a code line) -> attribute backward to the declaration
        it sits inside.
    """
    out: dict[str, str] = {}
    for path in walk((".kt",)):
        r = rel(path)
        if not any(r.startswith(s.replace(os.sep, "/")) for s in SOURCE_ROOTS):
            continue
        if "/test/" in r or "/androidTest/" in r:
            continue
        lines = io.open(path, encoding="utf-8", errors="replace").read().split("\n")
        for name, no in attribute(lines, section):
            out.setdefault(name, "%s:%d" % (r, no))
    return out


def attribute(lines: list[str], section: str) -> list[tuple[str, int]]:
    """The two attribution rules, over one file's lines.

    Pure and separate from the tree walk so a test can hand it a six-line snippet instead of
    asserting against 440 real files, where a regression would be invisible among the noise.
    """
    found = []
    for i, line in enumerate(lines):
        if not _cited_here(line, section):
            continue
        name = None
        if line.lstrip().startswith(("//", "*", "/*")):
            # A KDoc header: the declaration it describes follows, with nothing but more
            # comment, annotations or blank in between.
            for j in range(i + 1, min(i + 40, len(lines))):
                d = DECL.match(lines[j])
                if d:
                    name = d.group(1)
                    break
                if not BRIDGE.match(lines[j]):
                    break
        if name is None:
            # Anywhere else the citation sits *inside* something — attribute to that, not to
            # whatever happens to be declared next. An enclosing declaration is indented less
            # than the line it encloses, which is what separates the `fun` from the locals
            # between it and the citation.
            indent = len(line) - len(line.lstrip())
            for j in range(i, max(i - 200, -1), -1):
                d = ENCLOSING.match(lines[j])
                if d and (len(d.group(1)) < indent or j == i):
                    name = d.group(2)
                    break
        if name:
            found.append((name, i + 1))
    return found


def test_corpus() -> str:
    """Every unit test, as one blob — what "named by a test" is checked against."""
    parts = []
    for path in walk((".kt",)):
        r = rel(path)
        if "/test/" in r or "/androidTest/" in r:
            parts.append(io.open(path, encoding="utf-8", errors="replace").read())
    return "\n".join(parts)


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


def rows(sections, src, test, keep_meta=False, blob=None):
    """One row per section. With `blob` (the test corpus) the ranking is by *unnamed
    declarations* — the actionable number — and falls back to claims-over-citations without it,
    which is what the tests use to exercise the shape cheaply."""
    out = []
    for s in sections:
        if not s.claims or (s.meta and not keep_meta) or not s.primary:
            continue
        sc, tc = roll(src, s.num), roll(test, s.num)
        row = {
            "section": s.num,
            "title": s.title,
            "spec": s.spec.replace(os.sep, "/"),
            "line": s.line,
            "claims": len(s.claims),
            "src": sc,
            "test": tc,
        }
        if blob is None:
            row["score"] = round(len(s.claims) / (1 + tc), 2)
        else:
            decls = declarations_citing(s.num)
            unnamed = sorted(d for d in decls if not re.search(r"\b%s\b" % re.escape(d), blob))
            row["decls"] = len(decls)
            row["unnamed"] = unnamed
            # Weighted by how much the section promises: an untested declaration under 27 claims
            # is a worse place to be blind than one under three.
            row["score"] = round(len(unnamed) * (1 + len(s.claims) / 10.0), 2)
        out.append(row)
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

        decls = declarations_citing(s.num)
        blob = test_corpus()
        unnamed = {d: w for d, w in decls.items() if not re.search(r"\b%s\b" % re.escape(d), blob)}
        print("\n%d declarations carry this section; %d are named by no unit test."
              % (len(decls), len(unnamed)))
        for d in sorted(unnamed):
            print("   !  %-34s %s" % (d, unnamed[d]))
        if decls and not unnamed:
            print("   (every one is named somewhere in the suite)")
        print("   Named is not asserted — only a mutation settles that. Read before believing.")

        print()
        for no, text in s.claims:
            print("  [ ] %s:%-5d %s" % (s.spec.replace(os.sep, "/"), no, ascii_(text, 150)))
        return 0

    src, test = read_citations()
    data = rows(sections, src, test, keep_meta=args.meta, blob=test_corpus())

    if args.json:
        json.dump(data, sys.stdout, indent=2)
        print()
        return 0

    shown = data if args.all else data[:20]
    print("%-7s %-38s %6s %6s %8s  %s"
          % ("section", "title", "claims", "decls", "unnamed", "named by no test"))
    for r in shown:
        print("%-7s %-38s %6d %6d %8d  %s"
              % (r["section"], ascii_(r["title"], 38), r["claims"], r["decls"],
                 len(r["unnamed"]), ", ".join(r["unnamed"][:3])))
    total = sum(r["claims"] for r in data)
    blind = sum(len(r["unnamed"]) for r in data)
    print()
    print("%d sections, %d normative claims, %d declarations named by no unit test%s"
          % (len(data), total, blind,
             "" if args.all else "; top %d of %d sections shown" % (len(shown), len(data))))
    print("%d meta sections excluded (--meta to include, --section N to read one)" % len(META))
    print("A declaration named by a test is not a claim proved by one: naming is not asserting,")
    print("and only changing the code and watching a test go red settles that. This says where")
    print("nothing is even looking.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
