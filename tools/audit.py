#!/usr/bin/env python3
"""
Static hygiene checks for the Tendril repo — the mechanical half of a code audit,
so review attention goes to the parts that need judgement.

Every check encodes a defect this repository actually had, so a finding here is a
regression rather than a style opinion. All checks are plain-text analysis: no
Gradle, no Android SDK, no network — they run in seconds on any machine.

    python3 tools/audit.py            # report; exit 1 if anything is found
    python3 tools/audit.py -v         # also print what each check scanned

Checks
  1. commented-out code        a `//` line that is really a statement
  2. leftover markers          TODO / FIXME / HACK / XXX in a comment
  3. dead declarations         top-level fun/class/val referenced nowhere else
  4. unused DAO methods        a @Query/@Insert/... with no production caller
  5. dangling KDoc links       [Symbol] naming nothing declared or imported
  6. leaked MutableStateFlow   `val x: StateFlow<T> = _x` without .asStateFlow()
  7. Regex built per call      allocated in a function body instead of a top-level val
  8. unguarded throwing I/O    a call to a documented-throwing file API with no try/catch
 10. write-only entity field  stored and synced, never read outside the sync mappers
  9. imported-name shadowed    `viewModel.x` in a function where `viewModel` is only the
                               imported *function* of that name, never a parameter or local
 11. hardcoded colour          `Color(0x…)` or `Color.Gray`/`Blue`/… in shared UI outside the
                               theme package — every colour is a solved token (B§13.8.3, 14g·2)
 12. literal type              a literal `fontSize = N.sp`, `N.sp` or `fontWeight = FontWeight.X`
                               in shared UI outside the theme package — every text takes one of
                               the seven styles of `ui/theme/TendrilType.kt` (the type PR,
                               2026-09-16); the editor's span transformation is content and exempt
 13. material type role       `typography.bodyMedium` and the other raw Material roles in shared UI
                               outside the theme package — the seven styles are their only spelling
                               (the audit's fixes, 2026-09-17: 196 sites renamed onto them)
 14. type class               a text site whose declared style is not its class's, by
                               `tools/type_sites.py`'s rules and `tools/type_table/table.json` —
                               the kind of a text decides its style, never the line; `// type: X`
                               on the line is the one written-down exception
 15. bare menu                `DropdownMenu(` / `DropdownMenuItem(` outside `ui/components/` — every
                               menu goes through `TendrilMenu` so its rows take the profile's height
 16. second month grid        `GridCells.Fixed(7)` outside `ui/calendar/` — the Month is one composable
                               (`MonthGrid`; the phone's dot grid beside it) with two homes, never a third

Things invoked by a framework rather than by name — JUnit tests, Room converters
and DAOs, Compose @Composable, Android manifest components, `fun main` — are
excluded from the dead-code checks; they have no in-repo caller by design.
"""
from __future__ import annotations
import argparse, os, re, sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".claude"}
TEST_PATH = re.compile(r"/(test|androidTest)/")
HARD_COLOUR = re.compile(r"\bColor\s*\(\s*0x|\bColor\.(?:Gray|LightGray|DarkGray|Blue|Red|Green|Yellow|Magenta|Cyan|Black|White)\b")
# 14h·2 — `TypeScale.SIZES` in `ui/theme/Type.kt`, kept in step by hand (the audit cannot run Kotlin).
LITERAL_TYPE = re.compile(r"\bfontSize\s*=\s*\d|\b\d+(?:\.\d+)?\.sp\b|\bfontWeight\s*=\s*FontWeight\.")
TYPE_EXEMPT = ("SpanVisualTransformation.kt",)
MATERIAL_ROLE = re.compile(r"\btypography\.(?:display|headline|title|body|label)[A-Z]\w*")
BARE_MENU = re.compile(r"\bDropdownMenu(?:Item)?\s*\(")
MONTH_GRID = re.compile(r"\bGridCells\.Fixed\(\s*7\s*\)")


def rel(p: str) -> str:
    return os.path.relpath(p, ROOT).replace(os.sep, "/")


def walk(exts: tuple[str, ...]) -> list[str]:
    out = []
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
        out += [os.path.join(base, f) for f in files if f.endswith(exts)]
    return sorted(out)


def strip_literals(src: str) -> str:
    """Blank out comments and string literals so searching sees only code."""
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith('"""', i):
            j = src.find('"""', i + 3)
            i = (j + 3) if j != -1 else n
            out.append('""')
        elif src[i] == "'" and (src.startswith("'", i + 2) or (src.startswith("\\", i + 1) and src.startswith("'", i + 3))):
            # A char literal — `'"'` in particular, which otherwise opens a string that
            # swallows the rest of the file (every declaration after `Ics.kt`'s
            # `trim('"')` read as dead until this case existed).
            i += 4 if src[i + 1] == "\\" else 3
            out.append("''")
        elif src[i] == '"':
            i += 1
            buf = []
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    i += 2
                    continue
                buf.append(src[i])
                i += 1
            i += 1
            # A `${...}` template is code, not text: keep it so that a function called
            # only from inside a string still counts as referenced.
            out.append(" ".join(re.findall(r"\$\{([^{}]*)\}", "".join(buf))) or '""')
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = j if j != -1 else n
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = (j + 2) if j != -1 else n
        else:
            out.append(src[i])
            i += 1
    return "".join(out)


# --- patterns -----------------------------------------------------------------
# A commented-out statement, as opposed to prose. Keywords must be followed by the
# punctuation that makes them code ("for (" is code; "for Toggles" is a sentence).
CODEY = re.compile(
    r"^\s*//\s*(?:"
    r"(?:private |internal |public |override |suspend )*(?:val|var|fun|class|object|interface)\s+\w+\s*[:(=]"
    r"|import\s+\w+(?:\.[\w*]+)+"
    r"|(?:if|for|while|when)\s*\("
    r"|return\s+\w+"
    r"|\w+(?:\.\w+)*\([^)]*\)\s*[;{]?\s*$"
    r"|\w+(?:\.\w+)*\s*=\s*\w+.*[;)]\s*$"
    r")"
)
ENUM_HEAD = re.compile(r"\benum\s+class\s+\w+[^{]*\{")


def enum_members(src: str) -> set[str]:
    """Constant names from every `enum class` body.

    The line-anchored pattern in main() only sees a member alone on its own line ending
    in `,` or `(`. A one-line body — `enum class EntrySource { MANUAL, DATABASE_SYNC }` —
    puts every member mid-line, and the last has no trailing comma, so none was ever
    collected and every KDoc link naming an enum constant read as dangling.
    """
    out: set[str] = set()
    for m in ENUM_HEAD.finditer(src):
        i, depth = m.end(), 1
        while i < len(src) and depth:
            depth += (src[i] == "{") - (src[i] == "}")
            i += 1
        body = src[m.end(): i - 1].split(";", 1)[0]   # constants precede any member fun
        body = re.sub(r"//[^\n]*", " ", body)
        part, nest = [], 0
        for ch in body + ",":
            nest += (ch in "([") - (ch in ")]")
            if ch == "," and nest == 0:
                n = re.match(r"\s*(?:@\w+\s+)*([A-Za-z_]\w*)", "".join(part))
                if n:
                    out.add(n.group(1))
                part = []
            else:
                part.append(ch)
    return out


MARKER = re.compile(r"^\s*(?://|/?\*+)\s.*\b(FIXME|HACK|XXX|WIP)\b|^\s*(?://|/?\*+)\s*TODO[: ]")
# Top-level only: column 0, optionally with modifiers. Extension receivers included.
TOP_DECL = re.compile(
    # "fun" is in the modifier list as well as the keyword list, because `fun interface Name`
    # (a SAM/functional interface) is two keywords in a row — without it, "fun" satisfies the
    # keyword alternative on its own and "interface" is captured as if it were the declared
    # name. An ordinary `fun foo()` still matches correctly: Python's `re` backtracks off
    # treating "fun" as a modifier the moment nothing at the keyword position follows it.
    r"^(?:(?:private|internal|public|abstract|open|sealed|data|enum|expect|actual|inline|suspend|fun)\s+)*"
    r"(?:fun|class|object|interface)\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)"
)
TOP_VAL = re.compile(r"^(?:(?:private|internal|public|const|expect|actual)\s+)*va[lr]\s+(\w+)")
DAO_ANN = re.compile(r"^\s*@(?:Query|Insert|Update|Delete|Upsert)\b")
DAO_FUN = re.compile(r"^\s*(?:suspend\s+)?fun\s+(\w+)")
KDOC_LINK = re.compile(r"\[([A-Za-z][\w]*(?:\.[A-Za-z][\w]*)*)\]")
LEAKED_FLOW = re.compile(r"^\s*val\s+\w+\s*:\s*StateFlow<.*>\s*=\s*_\w+\s*$")
# Indentation stands in for "inside a function body", but a `val` in a nested companion
# object is indented identically and is allocated once. A per-call allocation is by
# definition not a declaration, so exclude those outright.
REGEX_IN_BODY = re.compile(r"^\s{8,}(?!.*\b(?:val|var)\s)[^*/].*\bRegex\(")
FUN_DECL = re.compile(r"^\s*(?:@\w+\s+)*(?:private |internal |public |protected )?(?:suspend )?fun")
THROWING = re.compile(r"\b(?:importAdditive|restoreFromBackup|readAndMerge|writeSnapshots)\s*\(|\b\w+\.(?:import|export)\s*\(")
# --- check 10: an entity field stored, synced, and never read ------------------
# The defect this encodes: a field with no `.name` access anywhere outside the sync
# mappers, named in no @Query, is written to the database and replicated to every
# device while being displayed to nobody. Sync is precisely what hides it from every
# other check here — SnapshotMappers and PagesSyncEngine reference each dead field
# exactly twice, encoding and decoding, so a plain reference count reads as healthy.
# Discounting those files is therefore not an optimisation, it is the whole check.
ENTITY_DECL = re.compile(r"@Entity[\s\S]{0,400}?data class (\w+)\(([\s\S]*?)\n\)", re.M)
ENTITY_FIELD = re.compile(r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*val\s+(\w+)\s*:", re.M)
SQL_ANN = re.compile(r'@(?:Query|DatabaseView)\s*\(\s*(?:value\s*=\s*)?("""|")([\s\S]*?)\1')
MAPPER_FILES = ("SnapshotMappers.kt", "PagesSyncEngine.kt", "SnapshotRecords.kt",
                "PageSnapshotRecords.kt", "SnapshotSyncOrchestrator.kt", "PortableArchive.kt")

# Read by something this text-level check cannot see. Permanent, not debt.
FIELD_READ_OFF_LANGUAGE = {
    "PageFtsEntry.plainText",   # SQLite's FTS engine reads the column via MATCH, never by name
}

# Fields that really are write-only today, each with the reason it still is.
# This set may only SHRINK - an entry goes when its field gains a reader - with one
# exception, added 2026-09-11: a field may be ADDED when the spec has decided it is stored
# ahead of its reader and names that reader (a 0.6 row citing a 0.8 step). The citation
# goes on the line, so the entry is a promise with an address, not debt. A field not
# listed here that stops being read is a finding, which is the point of baselining
# rather than deleting the check. Baselined 2026-09-08, measured not assumed.
FIELD_WRITE_ONLY_BASELINE = {
    "Entry.estimate",                       # spec 0.6.4: stored now, read by 0.8 step 7 (Plan mode, tracking)
    "Block.imagePath",                      # P2 - the importer writes it; nothing draws it
    "Label.color",                            # a palette tuned for dichromacy that renders nowhere
    "EntryCompletion.occurrenceDate",       # written at both resolve sites, never read back
}

FRAMEWORK_ANN = ("@Test", "@Before", "@After", "@BeforeClass", "@AfterClass", "@RunWith",
                 "@Composable", "@TypeConverter", "@Dao", "@Database", "@Entity", "@Preview")


class Report:
    def __init__(self):
        self.items = defaultdict(list)

    def add(self, check, msg):
        self.items[check].append(msg)

    def emit(self):
        total = sum(len(v) for v in self.items.values())
        for check in sorted(self.items):
            print(f"\n{check}  ({len(self.items[check])})")
            for r in self.items[check]:
                print(f"    {r}")
        print()
        print(f"FAIL  {total} finding(s)" if total else "PASS  no findings")
        return 1 if total else 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    files = walk((".kt", ".kts"))
    srcs = {f: open(f, encoding="utf-8").read() for f in files}
    stripped = {f: strip_literals(s) for f, s in srcs.items()}
    all_code = "\n".join(stripped.values())
    manifests = "\n".join(open(f, encoding="utf-8").read() for f in walk((".xml",)))
    rep = Report()

    if args.verbose:
        print(f"scanning {len(files)} Kotlin files under {ROOT}")

    # names that KDoc may legitimately reference: anything declared or imported anywhere
    imported = set(re.findall(r"^import\s+(?:[\w.]+\.)?(\w+)", all_code, re.M))
    imported |= set(re.findall(r"^import\s+([\w.]+)", all_code, re.M))
    declared = set(re.findall(r"\b(?:fun|class|object|interface|val|var)\s+(?:<[^>]+>\s+)?(?:[\w.]+\.)?(\w+)", all_code))
    # `fun interface Name` (a SAM/functional interface) is two keywords in a row. The pattern
    # above matches starting at "fun", then expects a name right after — "interface" fills that
    # slot instead, wrongly consumed as if it were the declared symbol, and the actual name is
    # never captured. First one this repository has ever declared (`FormulaPropertyResolver`,
    # `FormulaPropertyTypeLookup`) is what surfaced it — nothing existing depended on this gap.
    declared |= set(re.findall(r"\bfun\s+interface\s+(\w+)", all_code))
    declared |= set(re.findall(r"^\s*(\w+)\s*[,(]\s*$", all_code, re.M))          # enum members
    declared |= enum_members(all_code)                                             # one-line enum bodies
    declared |= set(re.findall(r"^\s*(?:val|var)?\s*(\w+)\s*:", all_code, re.M))  # params/properties
    declared |= set(re.findall(r"[(,]\s*(?:val\s+|var\s+|vararg\s+)?(\w+)\s*:", all_code))  # inline params
    # Kotlin's own prelude — every one of these resolves in any file with no import statement,
    # so a KDoc link to one has nothing to import or declare. The set was five names, missing
    # `Double` among others; a KDoc link naming a whole-file first-of-its-kind stdlib reference
    # (this repo's first `[Double]`) read as dangling for exactly that reason. Kotlin's own
    # implicit-import list (`kotlin.*`, `kotlin.collections.*`) is the actual boundary, not a
    # handful of names someone happened to need before.
    KOTLIN_PRELUDE = {
        "Dispatchers", "Any", "Unit", "Nothing", "Boolean", "Byte", "Short", "Int", "Long",
        "Float", "Double", "Char", "String", "Array", "List", "MutableList", "Set", "MutableSet",
        "Map", "MutableMap", "Pair", "Triple", "Comparable", "Iterable", "Iterator", "Sequence",
        "Function", "Throwable", "Exception", "RuntimeException", "IllegalArgumentException",
        "IllegalStateException", "IntArray", "LongArray", "DoubleArray", "BooleanArray",
    }
    known = imported | declared | KOTLIN_PRELUDE

    for f, src in srcs.items():
        lines = src.splitlines()
        is_test = bool(TEST_PATH.search("/" + rel(f)))
        for i, line in enumerate(lines, 1):
            if CODEY.match(line):
                rep.add("commented-out code", f"{rel(f)}:{i}  {line.strip()[:90]}")
            if MARKER.match(line) and "BlockType" not in line and "TODO only" not in line:
                rep.add("leftover marker", f"{rel(f)}:{i}  {line.strip()[:90]}")
            if LEAKED_FLOW.match(line):
                rep.add("leaked MutableStateFlow", f"{rel(f)}:{i}  {line.strip()[:90]}")
            if REGEX_IN_BODY.match(line):
                rep.add("Regex built per call", f"{rel(f)}:{i}  {line.strip()[:90]}")
            s = line.strip()
            if s.startswith(("*", "/**")):
                for m in KDOC_LINK.finditer(line):
                    parts = m.group(1).split(".")
                    if not (set(parts) & known) and m.group(1) not in known:
                        rep.add("dangling KDoc link", f"{rel(f)}:{i}  [{m.group(1)}]")
            if not is_test and THROWING.search(line) and not s.startswith(("*", "//", "suspend fun", "fun", "private")):
                # Back to the start of the enclosing function rather than a fixed number of
                # lines: a try that wraps the whole body opens far above its call and its catch
                # sits below, which a fixed window reported as unguarded.
                start = 0
                for j in range(i - 1, max(0, i - 200), -1):
                    if FUN_DECL.match(lines[j]):
                        start = j
                        break
                window = "\n".join(lines[start:i + 4])
                if "runCatching" not in window and "try {" not in window and "catch" not in window:
                    rep.add("unguarded throwing I/O call", f"{rel(f)}:{i}  {s[:90]}")

    # dead top-level declarations
    for f, src in srcs.items():
        if TEST_PATH.search("/" + rel(f)):
            continue
        lines = src.splitlines()
        for i, line in enumerate(lines, 1):
            m = TOP_DECL.match(line) or TOP_VAL.match(line)
            if not m:
                continue
            prev = "\n".join(lines[max(0, i - 5):i - 1])
            if any(a in prev for a in FRAMEWORK_ANN):
                continue
            name = m.group(1)
            if len(name) < 4 or name == "main":
                continue
            if re.search(rf"\b{re.escape(name)}\b", manifests):   # Android component
                continue
            if len(re.findall(rf"\b{re.escape(name)}\b", all_code)) <= 1:
                rep.add("dead declaration", f"{rel(f)}:{i}  {name}")

    # entity fields that are stored and synced but never read
    sql_text = " ".join(m.group(2) for s in srcs.values() for m in SQL_ANN.finditer(s))
    readable = {f: s for f, s in srcs.items()
                if not TEST_PATH.search("/" + rel(f))
                and not any(rel(f).endswith(x) for x in MAPPER_FILES)}
    for f, src in srcs.items():
        if TEST_PATH.search("/" + rel(f)):
            continue
        for m in ENTITY_DECL.finditer(src):
            entity = m.group(1)
            for fm in ENTITY_FIELD.finditer(m.group(2)):
                field = fm.group(1)
                key = f"{entity}.{field}"
                if key in FIELD_READ_OFF_LANGUAGE:
                    continue
                reads = sum(len(re.findall(rf"\.{re.escape(field)}\b", s)) for s in readable.values())
                if reads or re.search(rf"\b{re.escape(field)}\b", sql_text):
                    continue
                if key in FIELD_WRITE_ONLY_BASELINE:
                    continue
                rep.add("write-only entity field", f"{rel(f)}  {key}")


    # A call on an identifier that is only ever an imported *function* of that name.
    #
    # `viewModel.deleteForever(ids)` inside a @Composable that has no `viewModel` parameter
    # does not read as an error: `androidx.lifecycle.viewmodel.compose.viewModel` is in scope,
    # so the name resolves and the mistake looks like an ordinary property access. This is the
    # shape that reached main-branch review twice — a composable was given a call meant for the
    # screen that owns the view model, and nothing but the compiler objected. Restricted to
    # names imported from a lowercase final segment (Kotlin's function-naming convention), so
    # a type used as a qualifier never trips it.
    for f, src in srcs.items():
        code = stripped[f]
        fn_imports = {m.group(1) for m in re.finditer(r"^import\s+[\w.]*\.([a-z]\w*)$", src, re.M)}
        if not fn_imports:
            continue
        for fm in re.finditer(r"^(?:@\w+\s*\n)*(?:(?:private|internal|public|suspend|inline)\s+)*fun\s+[^\n{]*\{", code, re.M):
            start = fm.end() - 1
            depth, i = 0, start
            while i < len(code):
                if code[i] == "{": depth += 1
                elif code[i] == "}":
                    depth -= 1
                    if depth == 0: break
                i += 1
            header, body = fm.group(0), code[start:i]
            for name in fn_imports:
                if not re.search(rf"(?<![\w.]){re.escape(name)}\s*\.", body):
                    continue
                # In scope if it is a parameter of this function or bound inside its body.
                if re.search(rf"(?<![\w.]){re.escape(name)}\s*:", header): continue
                if re.search(rf"\b(?:val|var)\s+{re.escape(name)}\b", body): continue
                if re.search(rf"(?<![\w.]){re.escape(name)}\s*(?:,|\))\s*->", body): continue
                line = code[:start].count("\n") + 1
                rep.add("imported-name shadowed", f"{rel(f)}:{line}  {name} is the imported function here, not a value")

    # DAO methods with no production caller
    for f, src in srcs.items():
        lines = src.splitlines()
        for i, line in enumerate(lines):
            if not DAO_ANN.match(line):
                continue
            for j in range(i + 1, min(i + 4, len(lines))):
                fm = DAO_FUN.match(lines[j])
                if not fm:
                    continue
                name = fm.group(1)
                callers = [g for g, s in stripped.items()
                           if g != f and not TEST_PATH.search("/" + rel(g))
                           and re.search(rf"\.{re.escape(name)}\s*\(", s)]
                if not callers:
                    rep.add("unused DAO method", f"{rel(f)}:{j+1}  {name}")
                break

    # A hardcoded colour in shared UI: two blues in the span transformation and three
    # `Color.Gray` canvas edges survived until 14g·2 because nothing scanned for them; the
    # theme engine (`ui/theme/`) is the one place a literal belongs, and a widget's bitmap
    # puck (`ShadeHueWheel`) is drawn over every hue by design.
    for f, src in srcs.items():
        r = rel(f)
        if "/ui/theme/" in r or TEST_PATH.search("/" + r) or "/widget/" in r or "/ui/" not in r:
            continue
        for i, line in enumerate(src.splitlines(), 1):
            if HARD_COLOUR.search(line):
                rep.add("hardcoded colour", f"{r}:{i}  {line.strip()[:90]}")
            # The type PR — 45 literal sizes and 31 weights in 14 files were the same things
            # spelled differently per file (`docs/critiques/type-vocabulary-mock.md`); every
            # text takes a style now, and a style's weight is read from the style, never typed.
            if LITERAL_TYPE.search(line) and not r.endswith(TYPE_EXEMPT) and not line.lstrip().startswith(("//", "*", "/*")):
                rep.add("literal type", f"{r}:{i}  {line.strip()[:90]}")
            # The audit's fixes (2026-09-17) — the seven styles are aliases of Material's roles, so a
            # raw role name is the same style spelled a second way; one spelling per kind.
            if MATERIAL_ROLE.search(line) and not line.lstrip().startswith(("//", "*", "/*")):
                rep.add("material type role", f"{r}:{i}  {line.strip()[:90]}")
            # L1 — one `TendrilMenu` under a pointer profile; a bare Material menu keeps 48 dp rows.
            if BARE_MENU.search(line) and "/ui/components/" not in r and not line.lstrip().startswith(("//", "*", "/*", "import")):
                rep.add("bare menu", f"{r}:{i}  {line.strip()[:90]}")
            # L4 — one Month grid: a seven-column grid outside `ui/calendar/` is a second one.
            if MONTH_GRID.search(line) and "/ui/calendar/" not in r and not line.lstrip().startswith(("//", "*", "/*")):
                rep.add("second month grid", f"{r}:{i}  {line.strip()[:90]}")

    # The audit's fixes — a text's class decides its style (`tools/type_sites.py`, `tools/type_table/table.json`).
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import type_sites  # noqa: E402
    for v in type_sites.violations(type_sites.inventory(), type_sites.load_table()):
        rep.add("type class", "shared/src/commonMain/kotlin/com/tendril/app/ui/" + v)

    return rep.emit()


if __name__ == "__main__":
    sys.exit(main())
