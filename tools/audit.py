#!/usr/bin/env python3
"""
Static hygiene checks for the Tendril repo — the mechanical half of a code audit,
so review attention goes to the parts that need judgement.

Checks 1-10 each encode a defect this repository actually had, so a finding there is a
regression rather than a style opinion. Checks 11-19 and the sheet check hold a design
decision in place (they cite the PR that made it); a finding there is drift from it. All
checks are plain-text analysis: no Gradle, no Android SDK, no network — they run in seconds
on any machine.

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
  9. imported-name shadowed    `viewModel.x` in a function (top-level or member, block or
                               expression body) where `viewModel` is only the imported
                               *function* of that name - one the code calls bare - never a
                               parameter, local, lambda parameter, property or constructor param
 10. write-only entity field  stored and synced, never read outside the sync mappers
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
 17. radius family            `RoundedCornerShape(N.dp)` outside `ui/theme/` with N not in 2 · 4 · 6 · 8 ·
                               10 · 12 — the family Material's shapes carry too (`TendrilShapes`)
 18. material field           `OutlinedTextField(` / `TextField(` outside `ui/components/` — every text
                               field is a `TendrilField` (36 dp under a pointer, 48 under Touch)
 19. clip                     a `maxLines = 1` whose call has no `overflow` — a one-line title clips
                               without an ellipsis (`desktop-type-full.md` #4)
  -  sheet scroll             a lazy list inside a scrolling `TendrilSheet` (pass `scrolls = false`),
                               or `scrolls = false` on a sheet with no lazy list (P2, 2026-09-18)

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
# The design layer (2026-09-18) — 4 · 6 · 8 · 10 · 12, plus 2 for a 4 dp stripe's ends; kept in step with `TendrilShapes` by hand.
RADIUS = re.compile(r"\bRoundedCornerShape\(\s*(\d+(?:\.\d+)?)\.dp\s*\)")
RADIUS_FAMILY = {"2", "4", "6", "8", "10", "12"}
MATERIAL_FIELD = re.compile(r"\b(?:Outlined)?TextField\s*\(")
MAX_LINES_ONE = re.compile(r"\bmaxLines\s*=\s*1\b")   # any spacing, since 2026-09-24
SHEET_CALL = re.compile(r"\bTendrilSheet\s*\(")
LAZY_LIST = re.compile(r"\bLazy(?:Column|VerticalGrid|Row)\s*[({]")


def call_span(src: str, pos: int) -> tuple[int, int]:
    """The innermost `(...)` around pos — the call a `maxLines = 1` argument belongs to."""
    depth = 0; i = pos
    while i >= 0:
        if src[i] == ")": depth += 1
        elif src[i] == "(":
            if depth == 0: break
            depth -= 1
        i -= 1
    depth = 0; j = pos
    while j < len(src):
        if src[j] == "(": depth += 1
        elif src[j] == ")":
            if depth == 0: break
            depth -= 1
        j += 1
    return max(i, 0), j


def sheet_spans(src: str, pos: int) -> tuple[str, str]:
    """For a `TendrilSheet(` at pos: the argument text inside its parens and its trailing lambda's body."""
    i = src.index("(", pos); depth = 0; j = i
    while j < len(src):
        if src[j] == "(": depth += 1
        elif src[j] == ")":
            depth -= 1
            if depth == 0: break
        j += 1
    args = src[i + 1:j]
    k = j + 1
    while k < len(src) and src[k] in " \t\r\n": k += 1
    if k >= len(src) or src[k] != "{":
        return args, ""
    depth = 0; m = k
    while m < len(src):
        if src[m] == "{": depth += 1
        elif src[m] == "}":
            depth -= 1
            if depth == 0: break
        m += 1
    return args, src[k:m]


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
# Any modifier that exposes the flow counts (`private` does not leak it) - `override val` was
# invisible until 2026-09-24.
LEAKED_FLOW = re.compile(r"^\s*(?:(?:public|internal|override|open)\s+)*val\s+\w+\s*:\s*StateFlow<.*>\s*=\s*_\w+\s*$")
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
    "BlockFtsEntry.plainText",  # the same, one row per block (v25)
}

# Fields that really are write-only today, each with the reason it still is.
# This set may only SHRINK - an entry goes when its field gains a reader - with one
# exception, added 2026-09-11: a field may be ADDED when the spec has decided it is stored
# ahead of its reader and names that reader (a 0.6 row citing a 0.8 step). The citation
# goes on the line, so the entry is a promise with an address, not debt. A field not
# listed here that stops being read is a finding, which is the point of baselining
# rather than deleting the check. Baselined 2026-09-08, measured not assumed.
# The shrink rule is enforced since 2026-09-24 (`write_only_fields`'s stale half): until then an
# entry whose field gained a reader was never looked at again, and three of these four had -
# `Entry.estimate` (Plan mode, ICS), `Block.imagePath` (`BlockImage`, the exporter) and
# `Label.color` (five screens' label dots) - while their comments said nothing read them.
FIELD_WRITE_ONLY_BASELINE = {
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


FUN_HEADER = re.compile(
    r"^([ \t]*)(?:@\w+(?:\([^)\n]*\))?\s+)*"
    r"(?:(?:private|internal|public|protected|suspend|inline|override|open|operator|infix|tailrec|actual|expect)\s+)*"
    r"fun\s+", re.M)


def function_spans(code: str):
    """(fun keyword offset, indent, header, body) for every function in `code` - top-level or
    member, block body or expression body. A function with no body (abstract, interface) is
    skipped. Until 2026-09-24 check 9 saw only column-0 block bodies, about a fifth of them."""
    for m in FUN_HEADER.finditer(code):
        indent = len(m.group(1).expandtabs(4))
        fun_at = m.end() - len("fun") - 1
        while fun_at > 0 and code[fun_at:fun_at + 3] != "fun":
            fun_at -= 1
        i, depth, kind = m.end(), 0, None
        while i < len(code):
            c = code[i]
            if c in "(<[": depth += 1
            elif c in ")>]":
                if c == ">" and code[i - 1] == "-":
                    pass                                   # the arrow of a function type
                else:
                    depth -= 1
            elif depth == 0 and c == "{":
                kind = "block"; break
            elif depth == 0 and c == "=" and code[i + 1:i + 2] != "=" and code[i - 1] not in "!<>=":
                kind = "expr"; break
            elif depth == 0 and c == "\n":
                nxt = code[i + 1:].lstrip()
                if not nxt[:1] or nxt[0] not in "=:{.":
                    break                                  # no body: abstract or interface
            i += 1
        if kind is None:
            continue
        header = code[m.start():i]
        if kind == "block":
            d, j = 0, i
            while j < len(code):
                if code[j] == "{": d += 1
                elif code[j] == "}":
                    d -= 1
                    if d == 0: break
                j += 1
            body = code[i:j]
        else:
            # An expression body runs until a non-blank line indented no deeper than `fun`.
            end = code.find("\n", i)
            while end != -1:
                nl = code.find("\n", end + 1)
                line = code[end + 1: nl if nl != -1 else len(code)]
                if line.strip() and len(line) - len(line.lstrip()) <= indent:
                    break
                end = nl
            body = code[i: end if end != -1 else len(code)]
        yield fun_at, indent, header, body


def bare_calls(all_code: str) -> set[str]:
    """Names the code itself calls bare, `name(` - proof, in a tree that compiles, that the name
    is callable."""
    return set(re.findall(r"(?<![\w.])([a-z]\w*)\s*\(", all_code))


def imported_name_shadowed(src: str, code: str, called: set[str] | None = None) -> list[tuple[int, str]]:
    """Check 9: (line of `fun`, name) where an identifier that is only an imported *function*
    is used as a value (`name.`). `src` is the raw file (for imports), `code` its stripped form.

    A lowercase import is not always a function: `viewModelScope`, `lifecycleScope` and `dp` are
    extension *properties*, and `size.width` inside a `drawLine` lambda is the implicit
    DrawScope's member, not the imported `Modifier.size`. Scanning member functions (2026-09-24)
    surfaced 99 such reports and no real one. So when `called` is given, only names the codebase
    calls bare somewhere - proof they are functions - are considered."""
    fn_imports = {m.group(1) for m in re.finditer(r"^import\s+[\w.]*\.([a-z]\w*)$", src, re.M)}
    if called is not None:
        fn_imports &= called
    out = []
    for fun_at, indent, header, body in function_spans(code) if fn_imports else ():
        for name in sorted(fn_imports):
            n = re.escape(name)
            if not re.search(rf"(?<![\w.]){n}\s*\.", body):
                continue
            # In scope if it is a parameter, a local, or a lambda parameter of this function...
            if re.search(rf"(?<![\w.]){n}\s*:", header): continue
            if re.search(rf"\b(?:val|var)\s+{n}\b", body): continue
            if re.search(rf"(?<![\w.]){n}\s*(?:,[^{{}}\n]*?)?\)?\s*->", body): continue
            # ...or bound where the function sees it: a property declared no deeper than the
            # function (a sibling in the class body, or top-level), or a constructor parameter.
            # A local of another function sits deeper and does not count.
            if any(len(p.group(1).expandtabs(4)) <= indent
                   for p in re.finditer(rf"^([ \t]*)(?:[\w@]+\s+)*(?:val|var)\s+{n}\b", code, re.M)):
                continue
            if indent and re.search(rf"\bclass\s+\w+\s*(?:<[^>]*>)?\s*\([^)]*?(?<![\w.]){n}\s*:", code):
                continue
            out.append((code[:fun_at].count("\n") + 1, name))
    return out


def write_only_fields(srcs: dict[str, str], baseline=None, off_language=None) -> tuple[list[str], list[str]]:
    """Check 10 over `{repo-relative path: source}`: the unread entity fields that are not
    baselined, and the baseline entries that no longer hold.

    A baseline entry is stale when its field is read by this check's own measure, or names no
    entity field at all. Without that half the "may only SHRINK" rule was unenforced: the
    baseline was consulted only for a field with no reads, so an entry whose field had gained a
    reader silenced nothing and was never seen - three of four were, by 2026-09-24.

    The read test is by name (`.field` anywhere outside tests and the sync mappers), so two
    entities sharing a field name share its reads. That is coarse, and known.
    """
    baseline = FIELD_WRITE_ONLY_BASELINE if baseline is None else baseline
    off_language = FIELD_READ_OFF_LANGUAGE if off_language is None else off_language
    sql_text = " ".join(m.group(2) for s in srcs.values() for m in SQL_ANN.finditer(s))
    readable = [s for r, s in srcs.items()
                if not TEST_PATH.search("/" + r) and not any(r.endswith(x) for x in MAPPER_FILES)]
    found, unread, seen = [], set(), set()
    for r, src in srcs.items():
        if TEST_PATH.search("/" + r):
            continue
        for m in ENTITY_DECL.finditer(src):
            entity = m.group(1)
            for fm in ENTITY_FIELD.finditer(m.group(2)):
                field = fm.group(1)
                key = f"{entity}.{field}"
                seen.add(key)
                if key in off_language:
                    continue
                reads = sum(len(re.findall(rf"\.{re.escape(field)}\b", s)) for s in readable)
                if reads or re.search(rf"\b{re.escape(field)}\b", sql_text):
                    continue
                unread.add(key)
                if key not in baseline:
                    found.append(f"{r}  {key}")
    stale = sorted(k for k in baseline if k not in seen or k not in unread)
    return found, stale


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
    found, stale = write_only_fields({rel(f): s for f, s in srcs.items()})
    for item in found:
        rep.add("write-only entity field", item)
    for key in stale:
        rep.add("stale write-only baseline", f"tools/audit.py FIELD_WRITE_ONLY_BASELINE  {key} "
                "- its field is read now (or no longer exists); delete the entry")


    # A call on an identifier that is only ever an imported *function* of that name.
    #
    # `viewModel.deleteForever(ids)` inside a @Composable that has no `viewModel` parameter
    # does not read as an error: `androidx.lifecycle.viewmodel.compose.viewModel` is in scope,
    # so the name resolves and the mistake looks like an ordinary property access. This is the
    # shape that reached main-branch review twice — a composable was given a call meant for the
    # screen that owns the view model, and nothing but the compiler objected. Restricted to
    # names imported from a lowercase final segment (Kotlin's function-naming convention), so
    # a type used as a qualifier never trips it.
    called = bare_calls(all_code)
    for f, src in srcs.items():
        for line, name in imported_name_shadowed(src, stripped[f], called):
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
            # PR B (L6b) — one text field; Material's 56 dp `OutlinedTextField` is a size louder than every row.
            if MATERIAL_FIELD.search(line) and "/ui/components/" not in r and not line.lstrip().startswith(("//", "*", "/*", "import")):
                rep.add("material field", f"{r}:{i}  {line.strip()[:90]}")
            # D7 — one radius family; Material's 28 dp came through every dialog until the shapes were set.
            for m in RADIUS.finditer(line):
                if m.group(1) not in RADIUS_FAMILY and not line.lstrip().startswith(("//", "*", "/*")):
                    rep.add("radius family", f"{r}:{i}  {line.strip()[:90]}")

    # PR B (T4) — a one-line text without an ellipsis clips: the call around each `maxLines = 1` must name `overflow`.
    for f, src in srcs.items():
        r = rel(f)
        if "/ui/" not in r or TEST_PATH.search("/" + r):
            continue
        for m in MAX_LINES_ONE.finditer(src):
            a, b = call_span(src, m.start())
            if "overflow" not in src[a:b]:
                rep.add("clip", f"{r}:{src.count(chr(10), 0, m.start()) + 1}  {src[m.start():m.start() + 60].splitlines()[0]}")

    # The phone's fix PR (P2, 2026-09-18) — a sheet scrolls unless its content is a lazy list: the two
    # must pair, a `LazyColumn` inside a scrolling frame measures against infinity (a crash), a plain
    # column inside a bounded frame squashes when the keyboard is up (`phone-catch-up.md` #2).
    for f, src in srcs.items():
        r = rel(f)
        if "/ui/" not in r or TEST_PATH.search("/" + r) or r.endswith("TendrilSheet.kt"):
            continue
        for m in SHEET_CALL.finditer(src):
            args, body = sheet_spans(src, m.start())
            lazy = bool(LAZY_LIST.search(body))
            owns = "scrolls = false" in args
            if lazy != owns:
                line = src.count(chr(10), 0, m.start()) + 1
                rep.add("sheet scroll", f"{r}:{line}  " + ("a lazy list inside a scrolling sheet - pass scrolls = false" if lazy else "scrolls = false on a sheet with no lazy list"))

    # The audit's fixes — a text's class decides its style (`tools/type_sites.py`, `tools/type_table/table.json`).
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import type_sites  # noqa: E402
    for v in type_sites.violations(type_sites.inventory(), type_sites.load_table()):
        rep.add("type class", "shared/src/commonMain/kotlin/com/tendril/app/ui/" + v)

    return rep.emit()


if __name__ == "__main__":
    sys.exit(main())
