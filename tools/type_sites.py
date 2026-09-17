#!/usr/bin/env python3
"""
The type-site inventory and classifier — the audit's fixes (2026-09-17).

Every piece of chrome text in `shared/…/ui/` is a *site*: a `Text(` call, a text field's
`textStyle`, or a component slot (a chip's label, a menu item's text, a dialog's title).
A site has features the code states — the style it names, its `color` token, its `maxLines`,
its position among the Texts of its Column or Row, what kind of content it shows, which
component slot it fills — and from those features one **class** follows by rules, in a fixed
order, the first that matches. A class has exactly one style (`tools/type_table/table.json`,
chosen once by a scorer over this inventory — `docs/critiques/desktop-audit-fixes.md`), and
`tools/audit.py`'s *type class* rule fails any site whose declared style is not its class's.
So the question "which style does this text take" is answered by its kind, never per line;
the one escape hatch is an explicit `// type: CLASS` comment on the site's line, which the
classifier reads first — a judgement written down where the audit can see it.

    python3 tools/type_sites.py            # the inventory as JSON on stdout
    python3 tools/type_sites.py --summary  # counts per class, per style, the UNKNOWN list

The parser is a bracket-aware scan of Kotlin, not a Kotlin parser: enough to know, for every
`Text(`, the call that owns the block it sits in (`Column`, `Row`, a `label = {` slot of a
`FilterChip`), how many Texts came before it in that block, and its own arguments. It reads
`shared/src/commonMain/kotlin/com/tendril/app/ui/` outside `ui/theme/`.
"""
from __future__ import annotations
import argparse, json, os, re, sys
from dataclasses import dataclass, asdict, field

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI_DIR = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "com", "tendril", "app", "ui")
TABLE_PATH = os.path.join(ROOT, "tools", "type_table", "table.json")

VOCAB = ("pageTitle", "heading", "body", "label", "description", "caption", "eyebrow", "clock", "clockSmall",
         "editorBody", "editorH1", "editorH2", "editorH3", "editorQuote", "editorCode")
RAW_ROLES = ("displayLarge", "displayMedium", "displaySmall", "headlineLarge", "headlineMedium", "headlineSmall",
             "titleLarge", "titleMedium", "titleSmall", "bodyLarge", "bodyMedium", "bodySmall",
             "labelLarge", "labelMedium", "labelSmall")
# The alias each raw role resolves to in `ui/theme/TendrilType.kt` — the mechanical rename.
ALIAS = {"titleLarge": "pageTitle", "titleMedium": "heading", "titleSmall": "label", "bodyLarge": "editorBody",
         "bodyMedium": "body", "bodySmall": "description", "labelLarge": "label", "labelMedium": "caption",
         "labelSmall": "eyebrow", "headlineSmall": "editorH1", "headlineMedium": "editorH2"}

CONTAINERS = {"Column", "Row", "Box", "LazyColumn", "LazyRow", "FlowRow", "item", "items", "itemsIndexed",
              "Surface", "Card", "OutlinedCard", "ElevatedCard", "BoxWithConstraints", "LazyVerticalGrid"}
CHIP_COMPONENTS = {"FilterChip", "AssistChip", "InputChip", "SuggestionChip", "ElevatedFilterChip", "SegmentedButton"}
MENU_COMPONENTS = {"DropdownMenuItem", "TendrilMenuItem", "SubmenuItem", "TextButton", "Button", "OutlinedButton", "FilledTonalButton", "Snackbar"}
TEXT_FIELDS = {"BasicTextField", "OutlinedTextField", "TextField"}
SLOT_COMPONENTS = CHIP_COMPONENTS | MENU_COMPONENTS | TEXT_FIELDS | {"AlertDialog", "ListItem", "NavigationRailItem", "NavigationBarItem", "ShellTopBar"}
# An empty state says what is absent; the words are the feature (the audit reads them too).
EMPTY_WORDS = ("No ", "Nothing", "Empty", "Loading", "Choose ", "Here ", "Not ", "Nessun")
GREY_TOKENS = {"onSurfaceVariant", "outlineVariant", "textDim", "textFaint", "outline"}
PLAIN_TOKENS = {"onSurface", "text", "primary", "onBackground"}
BADGE_COLOURS = {"onErrorContainer", "onPrimaryContainer", "onTertiaryContainer", "onAccent", "onError"}
# Text drawn inside a node or a bar of a diagram: sized by the diagram, not the list.
NODE_COMPOSABLES = {"RoadMapNode", "RoadMapNeighbourhood", "MapLayer", "TimelineBarRow"}
# A sheet's own header and a key chip: their composables say what they are.
SHEET_COMPOSABLES = {"TendrilSheet", "SlideOver", "ShelfGraph", "ShelfPane"}
BADGE_COMPOSABLES = {"KeyChip", "BlockedChip"}
SMALL_STYLES = {"labelSmall", "labelMedium", "caption", "eyebrow"}
# The hover card's lines and the rail's labels: chrome with its own register, one class each.
PREVIEW_COMPOSABLES = {"HoverPreviewCard"}
ICON_LABEL_COMPOSABLES = {"ShellRail", "ShellItem", "ShellBottomBar"}
# The calendar grids' gutters, block titles and day cells — dense by design.
GRID_COMPOSABLES = {"PlanView", "WeekGridView", "MonthGridView", "MonthGrid", "TimelineBody"}
# The phone's week strip and the Timeline's day header: every text in them is a cell's.
DENSE_COMPOSABLES = {"WeekStripView", "DayHeader", "OccurrenceChip"}
# A grid's weekday header: a DayOfWeek's short or narrow name in a grid composable is its eyebrow row.
WEEKDAY_RE = re.compile(r"getDisplayName\(\s*TextStyle\.")
STYLE_RE = re.compile(r"\bstyle\s*=\s*([^,()]+(?:\([^()]*\))?)")
TYPO_RE = re.compile(r"typography\.(\w+)")
COLOR_RE = re.compile(r"\bcolor\s*=\s*([^,()]+(?:\([^()]*\))?)")
TOKEN_RE = re.compile(r"colorScheme\.(\w+)|palette\.(\w+)|\b(\w+)\s*$")
MAXLINES_RE = re.compile(r"\bmaxLines\s*=\s*([^,)]+)")
OVERFLOW_RE = re.compile(r"\boverflow\s*=")
LETTERSPACING_RE = re.compile(r"letterSpacing")
TYPE_COMMENT_RE = re.compile(r"//\s*type:\s*([A-Z_]+)")
IDENT_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
TIME_DATE_RE = re.compile(r"format\(|LocalDate|LocalTime|\.time\b|Minutes|dayOfMonth|dayOfWeek|month|Month|relativeTime|HH|\"%02d\"|date\b|Date\b|edited|clock", re.I)
COUNT_RE = re.compile(r"\.size\b|count|Count|\bn\b|rows|Label\(|\+\s*\d|\d+\s*\)|of \$|\bsteps\b|logged|Planned|plural\(")
WORD_RE = re.compile(r"[A-Za-z][A-Za-z'’]+")


@dataclass
class Site:
    file: str
    line: int
    kind: str                 # Text | field | slot-only (a Text inside a slot is kind "Text" with slot set)
    style: str | None         # the style name declared (vocabulary or raw role), None when inherited
    raw_style: str | None     # the raw role when one is named
    color: str | None
    max_lines: str | None
    overflow: bool
    content: str              # LITERAL | VAR | COUNT | TIME_DATE | EXPR
    text: str                 # the first argument, trimmed to 60 chars
    container: str | None     # the nearest Column/Row/Box/… block
    index: int                # this Text's index among the Texts of its container block
    slot: str | None          # Component.arg when the Text fills a component's slot
    composable: str | None    # the enclosing @Composable function
    annotation: str | None    # an explicit `// type: X`
    siblings: list = field(default_factory=list)   # the calls in the site's own block (filled as the block closes)
    styles_named: list = field(default_factory=list)  # every typography name in the style expression (a conditional names two)
    indirect: bool = False    # `style = someVal` — named elsewhere; the raw-role rule reads that line
    cls: str = ""
    explicit: bool = False

    def key(self) -> str:
        return f"{self.file}:{self.line}"


# ---------------------------------------------------------------- the scan

def _scan(src: str, file: str) -> list[Site]:
    """One pass over the file with a stack of open blocks. Strings and comments are skipped."""
    sites: list[Site] = []
    n = len(src)
    i = 0
    line = 1
    # stacks of open '(' and '{' with the identifier (or `arg=`) that owns them
    paren: list[dict] = []
    brace: list[dict] = []
    composable: str | None = None
    fun_re = re.compile(r"fun\s+(?:\w+\.)?(\w+)\s*\(")

    def prev_ident(pos: int) -> str | None:
        j = pos - 1
        while j >= 0 and src[j] in " \t\r\n": j -= 1
        k = j
        while k >= 0 and (src[k].isalnum() or src[k] == "_"): k -= 1
        return src[k + 1:j + 1] or None

    def owner_of_brace(pos: int) -> tuple[str | None, str | None]:
        """The call that owns a `{` at pos: `Name(...) {` → (Name, None); `arg = {` → (enclosingCallee, arg);
        `Name {` → (Name, None)."""
        j = pos - 1
        while j >= 0 and src[j] in " \t\r\n": j -= 1
        if j >= 0 and src[j] == ")":
            depth = 0
            k = j
            while k >= 0:
                if src[k] == ")": depth += 1
                elif src[k] == "(":
                    depth -= 1
                    if depth == 0: break
                k -= 1
            name = prev_ident(k)
            # a modifier chain `Modifier.foo(...)` or a `.let {`: not an owner
            return (name, None) if name and name[0].isupper() else (None, None)
        if j >= 0 and src[j] == "=":
            arg = prev_ident(j)
            callee = paren[-1]["name"] if paren else None
            return (callee, arg)
        name = prev_ident(j + 1)
        return (name, None) if name and name[0].isupper() else (None, None)

    while i < n:
        c = src[i]
        if c == "\n":
            line += 1; i += 1; continue
        if src.startswith("//", i):
            j = src.find("\n", i); i = n if j < 0 else j; continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2); nl = src.count("\n", i, j if j > 0 else n); line += nl; i = n if j < 0 else j + 2; continue
        if c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3); nl = src.count("\n", i, j if j > 0 else n); line += nl; i = n if j < 0 else j + 3; continue
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\": j += 1
                j += 1
            i = j + 1; continue
        if c == "(":
            name = prev_ident(i)
            k = i - 1
            while k >= 0 and src[k] in " \t\r\n": k -= 1
            k -= len(name or "")
            paren.append({"name": name, "line": line, "start": i, "member": k >= 0 and src[k] == "."})
            if name and name[0].isupper() and brace: brace[-1]["calls"].append(name)
            if name == "fun" or (name and (m := fun_re.match(src, max(0, i - 80)))):
                pass
            i += 1; continue
        if c == ")":
            if paren:
                p = paren.pop()
                if (p["name"] in ("Text",) or p["name"] in TEXT_FIELDS) and not p.get("member"):
                    args = src[p["start"] + 1:i]
                    sites.append(_site(file, p["line"], p["name"], args, paren, brace, composable, src, p["start"]))
            i += 1; continue
        if c == "{":
            owner, arg = owner_of_brace(i)
            brace.append({"owner": owner, "arg": arg, "texts": 0, "calls": []})
            i += 1; continue
        if c == "}":
            if brace: brace.pop()
            i += 1; continue
        if c == "f" and src.startswith("fun ", i) and (i == 0 or not (src[i - 1].isalnum() or src[i - 1] == "_")):
            m = fun_re.match(src, i)
            if m and not brace: composable = m.group(1)
        i += 1
    return sites


def _named_arg(args: str, name: str) -> str | None:
    """The expression of `name = …` up to the next top-level comma, parens and strings balanced."""
    m = re.search(r"\b" + name + r"\s*=\s*", args)
    if not m: return None
    return _first_arg(args[m.end():])


def _first_arg(args: str) -> str:
    """The first positional argument: up to the first top-level comma (parens, braces and strings balanced)."""
    depth = 0; i = 0; n = len(args); instr = False
    while i < n:
        ch = args[i]
        if instr:
            if ch == "\\": i += 1
            elif ch == '"': instr = False
        elif ch == '"': instr = True
        elif ch in "([{": depth += 1
        elif ch in ")]}": depth -= 1
        elif ch == "," and depth == 0: return args[:i]
        i += 1
    return args


def _site(file, line, kind, args, paren, brace, composable, src, start) -> Site:
    style = raw = None
    styles_named: list[str] = []
    style_expr = _named_arg(args, "textStyle" if kind != "Text" else "style")
    indirect = False
    if style_expr is not None:
        styles_named = TYPO_RE.findall(style_expr)
        if not styles_named: indirect = True   # `style = style`, a variable defined elsewhere
    if styles_named:
        style = styles_named[0]
        if style in RAW_ROLES: raw = style
    color = None
    cexpr = _named_arg(args, "color")
    if cexpr is not None:
        expr = cexpr.strip()
        tm = re.search(r"colorScheme\.(\w+)|palette\.(\w+)|\b(\w+)$", expr)
        color = next((g for g in (tm.groups() if tm else ()) if g), expr)
    ml = MAXLINES_RE.search(args)
    named = re.match(r"\s*(text|value|modifier)\s*=", args)
    if named:
        m2 = re.search(r"\b(?:text|value)\s*=\s*(.+?)(?:,\s*\w+\s*=|$)", args, re.S)
        first = (m2.group(1) if m2 else args).strip()
    else:
        first = _first_arg(args).strip()
    text = " ".join(first.split())[:60]
    # `if (…) "a" else "b"` is a literal that chose between two literals
    choice = re.match(r'^if\s*\(.*?\)\s*("[^"$]*")\s*else\s*"[^"$]*"$', " ".join(first.split()), re.S)
    if choice: first = choice.group(1); text = first
    if re.match(r'^"[^"$]*"$', first.strip()): content = "LITERAL"
    elif kind != "Text": content = "VAR"
    elif TIME_DATE_RE.search(first): content = "TIME_DATE"
    elif COUNT_RE.search(first): content = "COUNT"
    elif first.startswith('"') or "+" in first or "${" in first: content = "EXPR"
    else: content = "VAR"
    # position: the nearest enclosing brace whose owner is a container, and the slot if any
    container = None; index = 0; slot = None
    for b in reversed(brace):
        if b["owner"] in SLOT_COMPONENTS and slot is None:
            slot = f"{b['owner']}.{b['arg'] or 'content'}"
        if b["owner"] in CONTAINERS and container is None:
            container = b["owner"]; index = b["texts"]; b["texts"] += 1
        if container and slot is not None: break
    if container is None and brace:
        # count it in the innermost block anyway so a later Text in the same block reads as second
        brace[-1]["texts"] += 1
    # a slot the Text fills directly through a paren (e.g. `label = { Text(` handled above; `text = { Text(` too)
    line_text = src[src.rfind("\n", 0, start) + 1: src.find("\n", start) if src.find("\n", start) > 0 else len(src)]
    ann = TYPE_COMMENT_RE.search(line_text)
    return Site(file=file, line=line, kind=("field" if kind in TEXT_FIELDS else "Text"), style=style, raw_style=raw,
                color=color, max_lines=(ml.group(1).strip() if ml else None), overflow=bool(OVERFLOW_RE.search(args)),
                content=content, text=text, container=container, index=index, slot=slot, composable=composable,
                annotation=(ann.group(1) if ann else None), siblings=(brace[-1]["calls"] if brace else []),
                styles_named=styles_named, indirect=indirect)


# ---------------------------------------------------------------- the classes

def classify(s: Site) -> str:
    """The rules, in order; the first that matches wins. Every rule reads only what the code
    states: the slot, the field, the colour token, the content, the position, the length, the
    block's other calls, and — for four kinds whose composable is their name — the composable."""
    if s.annotation:
        s.explicit = True
        return s.annotation
    slot_comp = s.slot.split(".")[0] if s.slot else None
    slot_arg = s.slot.split(".")[1] if s.slot else None
    lit = s.text.strip('"') if s.content == "LITERAL" else ""
    words = WORD_RE.findall(lit)
    grey = s.color in GREY_TOKENS
    # a colour that is a variable or a branch (`colour`, `labelColor`, `if (selected) …`) says nothing about the kind
    plain = s.color is None or s.color in PLAIN_TOKENS or (s.color not in GREY_TOKENS and s.color not in BADGE_COLOURS and s.color != "error")
    small = s.raw_style in SMALL_STYLES or s.style in SMALL_STYLES
    # 1. what a component draws for itself
    if slot_comp in CHIP_COMPONENTS: return "SLOT_CHIP"
    if slot_comp in MENU_COMPONENTS: return "SLOT_MENU"
    if slot_comp == "AlertDialog": return "SLOT_DIALOG_TITLE" if slot_arg == "title" else "SLOT_DIALOG_TEXT"
    if slot_comp in TEXT_FIELDS: return "SLOT_FIELD_LABEL"
    if slot_comp == "ShellTopBar": return "SLOT_BAR_TITLE"
    if s.kind == "field":
        return "BAR_TITLE" if (s.raw_style in ("titleLarge", "titleMedium") or s.style in ("pageTitle", "heading")) else "FIELD"
    # 2. styles that are their own kind
    if s.style in ("clock", "clockSmall"): return "CLOCK"
    if s.style and s.style.startswith("editor"): return "EDITOR"
    # 3. the text itself
    if s.content == "LITERAL" and lit and not any(ch.isalpha() for ch in lit): return "GLYPH"
    if s.content in ("VAR", "EXPR") and re.search(r"[iI]con\b|[eE]moji\b", s.text): return "GLYPH"
    if (s.content == "LITERAL" and lit == lit.upper() and any(ch.isalpha() for ch in lit)) or re.search(r"\w\.uppercase\(\)\s*$", s.text): return "EYEBROW"
    if (s.color in BADGE_COLOURS and s.content == "LITERAL" and len(words) <= 2) or s.composable in BADGE_COMPOSABLES: return "BADGE"
    if s.composable in NODE_COMPOSABLES: return "NODE_LABEL"
    if s.composable in PREVIEW_COMPOSABLES and not (s.index == 0 and s.container == "Row"): return "PREVIEW_LINE"
    if s.composable in ICON_LABEL_COMPOSABLES: return "ICON_LABEL"
    if grey and s.container == "Box" and any(c in TEXT_FIELDS for c in s.siblings): return "PLACEHOLDER"
    # 4. headers and labels
    if s.content == "TIME_DATE" and s.index == 0 and plain and (s.raw_style in ("titleMedium", "titleSmall") or s.style == "heading"):
        return "HEADER_DATE"
    if s.composable in SHEET_COMPOSABLES and s.index == 0 and plain: return "SHEET_HEADER"
    if s.container == "Row" and s.index == 0 and plain and (s.raw_style == "titleMedium" or s.style == "heading"): return "SHEET_HEADER"
    if s.raw_style == "titleLarge" or s.style == "pageTitle": return "CARD_TITLE"
    if s.content == "LITERAL" and 0 < len(words) <= 4 and plain and s.max_lines is None:
        if s.raw_style in ("labelLarge", "titleSmall") or s.style == "label": return "SUBSECTION"
        if s.raw_style == "titleMedium" or s.style == "heading": return "SECTION_HEADING"
    if s.content != "LITERAL" and s.index == 0 and (plain or grey) and (s.raw_style in ("labelLarge", "titleSmall") or s.style == "label"): return "GROUP_HEADER"
    if s.content == "LITERAL" and lit.startswith(("Add ", "New ", "Choose ", "Pick ")) and len(words) <= 4 and s.container == "Row": return "ACTION_ROW"
    # 5. an empty state says what is absent
    if s.content == "LITERAL" and len(words) <= 9 and (lit.startswith(EMPTY_WORDS) or re.match(r"^\w+ needs ", lit)) and (grey or lit.endswith(".")): return "EMPTY_STATE"
    # 6. the calendar grids' dense text: gutters, block titles, day cells — and their weekday header row
    if s.composable in GRID_COMPOSABLES and WEEKDAY_RE.search(s.text): return "EYEBROW"
    if s.composable in DENSE_COMPOSABLES or (s.composable in GRID_COMPOSABLES and (small or s.container == "Box")): return "GRID_DENSE"
    # 7. cells and editors (a Table's cells, a row page's property editors)
    if s.composable and ("Cell" in s.composable or (s.composable.endswith("Editor") and plain)) and s.content != "LITERAL": return "CELL"
    # 8. the grey kinds
    if s.content == "LITERAL" and grey and len(words) > 6 and s.max_lines is None: return "EXPLAINER"
    if s.content == "LITERAL" and grey and len(words) <= 4 and s.index == 0: return "GREY_LABEL"
    if grey: return "META"
    if s.color == "error": return "ERROR_LINE"
    # 9. plain text
    if plain and s.index == 0: return "TITLE"
    if plain and s.index > 0 and s.content == "LITERAL" and len(words) <= 4: return "SUBSECTION"
    if plain and s.index > 0: return "BODY_LINE"
    return "UNKNOWN"


def inventory() -> list[Site]:
    out: list[Site] = []
    for base, dirs, files in os.walk(UI_DIR):
        if os.path.basename(base) == "theme": dirs[:] = []; continue
        for f in sorted(files):
            if not f.endswith(".kt"): continue
            path = os.path.join(base, f)
            with open(path, encoding="utf-8") as fh: src = fh.read()
            relf = os.path.relpath(path, UI_DIR).replace(os.sep, "/")
            for s in _scan(src, relf):
                s.cls = classify(s)
                out.append(s)
    return out


def load_table() -> dict[str, str]:
    if not os.path.exists(TABLE_PATH): return {}
    with open(TABLE_PATH, encoding="utf-8") as fh: return json.load(fh)["classes"]


INHERITS = {"SLOT_CHIP", "SLOT_MENU", "SLOT_DIALOG_TITLE", "SLOT_DIALOG_TEXT", "SLOT_FIELD_LABEL", "SLOT_BAR_TITLE", "GLYPH"}


def violations(sites: list[Site], table: dict[str, str]) -> list[str]:
    """The audit's *type class* rule: a site's declared style must be its class's; a slot may
    inherit its component's; UNKNOWN needs an explicit `// type:` comment."""
    out = []
    for s in sites:
        if s.cls == "UNKNOWN":
            out.append(f"{s.key()}  no class — add `// type: CLASS` ({s.text})"); continue
        want = table.get(s.cls)
        if want is None: continue
        if s.indirect or (s.style is None and s.cls in INHERITS): continue
        if s.style != want and want not in s.styles_named:
            out.append(f"{s.key()}  {s.cls} takes {want}, declares {s.style or 'nothing'} ({s.text})")
    return out


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("--summary", action="store_true")
    ap.add_argument("--check", action="store_true", help="print the type-class violations against the table")
    a = ap.parse_args()
    sites = inventory()
    if a.check:
        v = violations(sites, load_table())
        print("\n".join(v)); return 1 if v else 0
    if a.summary:
        from collections import Counter
        print("sites:", len(sites))
        print("by class:", dict(Counter(s.cls for s in sites).most_common()))
        print("by style:", dict(Counter(s.style or "-" for s in sites).most_common()))
        print("raw roles:", sum(1 for s in sites if s.raw_style))
        for s in sites:
            if s.cls == "UNKNOWN": print("  UNKNOWN", s.key(), s.container, s.index, s.color, s.content, s.text)
        return 0
    print(json.dumps([asdict(s) for s in sites], indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
