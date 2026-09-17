"""Scorers for the class→style table (the audit's fixes, 2026-09-17).

A candidate is one style per *open* class (the spec's dimensions); the fixed classes come from
`FIXED` below. Every signal is arithmetic over `tools/type_sites.py`'s inventory — nothing is
judged — so the search runs to convergence in seconds and the table it returns is the one that
best keeps the rules the type PR wrote down: a later line never louder than its first, one style
per kind of place, few styles per screen, and the title-to-meta ratio Notion draws (measured
12 : 14 on this machine, `docs/critiques/desktop-audit-fixes.md`).
"""
from __future__ import annotations
import functools, os, sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import type_sites as ts  # noqa: E402

# size, weight — `ui/theme/Type.kt`'s scale as the seven styles resolve it.
STYLES = {
    "pageTitle": (18.0, 600), "heading": (14.0, 600), "body": (14.0, 400), "label": (12.5, 500),
    "description": (12.5, 400), "caption": (11.0, 400), "eyebrow": (11.0, 500),
}
# Fixed by convention (the plan): what a component draws, a field, a title, a cell.
FIXED = {
    "SLOT_CHIP": "label", "SLOT_MENU": "body", "SLOT_DIALOG_TITLE": "heading", "SLOT_DIALOG_TEXT": "body",
    "SLOT_FIELD_LABEL": "body", "FIELD": "body", "BAR_TITLE": "pageTitle", "EYEBROW": "eyebrow",
    "TITLE": "body", "CELL": "body", "HEADER_DATE": "heading", "BADGE": "caption", "PLACEHOLDER": "body",
}
# Their own register, outside the table: the tabular clock, the editor's content, a bare glyph.
OWN = {"CLOCK", "EDITOR", "GLYPH"}
OPEN = ["META", "EXPLAINER", "EMPTY_STATE", "GREY_LABEL", "SUBSECTION", "SECTION_HEADING", "SHEET_HEADER",
        "CARD_TITLE", "BODY_LINE", "GRID_DENSE", "NODE_LABEL", "PREVIEW_LINE", "ACTION_ROW", "ICON_LABEL", "ERROR_LINE",
        "GROUP_HEADER"]
HEADER_CLASSES = {"SECTION_HEADING", "SHEET_HEADER", "CARD_TITLE", "SUBSECTION", "GROUP_HEADER"}
GREY_CLASSES = {"META", "EXPLAINER", "EMPTY_STATE", "GREY_LABEL", "PREVIEW_LINE"}


@functools.lru_cache(maxsize=1)
def _sites():
    return [s for s in ts.inventory() if s.cls not in OWN]


def _table(vector: dict) -> dict:
    t = dict(FIXED)
    for k in OPEN: t[k] = vector.get(k, "body")
    return t


def _style_of(site, table):
    return table.get(site.cls)


def hierarchy(vector: dict, context: dict) -> float:
    """A later Text in the same block is never larger or heavier than the block's first."""
    table = _table(vector)
    firsts = {}
    pairs = ok = 0
    for s in _sites():
        st = _style_of(s, table)
        if st is None: continue
        key = (s.file, s.composable, s.container)
        if s.index == 0:
            firsts[key] = st
        elif key in firsts:
            a, b = STYLES[firsts[key]], STYLES[st]
            pairs += 1
            if b[0] <= a[0] and b[1] <= a[1]: ok += 1
    return ok / pairs if pairs else 1.0


def uniqueness(vector: dict, context: dict) -> float:
    """One style per (colour token, position) — the same kind of place never reads two ways."""
    table = _table(vector)
    buckets = defaultdict(set); weights = defaultdict(int)
    for s in _sites():
        st = _style_of(s, table)
        if st is None or s.slot: continue
        pos = "first" if s.index == 0 and s.container != "Row" else ("row" if s.container == "Row" else "later")
        k = (s.color or "-", pos)
        buckets[k].add(st); weights[k] += 1
    tot = sum(weights.values())
    return sum(weights[k] / len(v) for k, v in buckets.items()) / tot if tot else 1.0


def screen_budget(vector: dict, context: dict) -> float:
    """At most four styles on a screen; a fifth costs, a seventh is zero."""
    table = _table(vector)
    per = defaultdict(set)
    for s in _sites():
        st = _style_of(s, table)
        if st and not s.slot: per[s.file].add(st)
    scores = [1.0 if len(v) <= 4 else max(0.0, 1 - (len(v) - 4) / 3) for v in per.values()]
    return sum(scores) / len(scores) if scores else 1.0


def ground_ratio(vector: dict, context: dict) -> float:
    """META's size over TITLE's, against the measured ground (Notion 12/14 → 0.857)."""
    table = _table(vector)
    r = float(context.get("ground_ratio", 0.857))
    got = STYLES[table["META"]][0] / STYLES[table["TITLE"]][0]
    return max(0.0, 1 - 4 * abs(got - r))


def header_weight(vector: dict, context: dict) -> float:
    """A heading of any rank is at least Medium; grey text is never larger than body."""
    table = _table(vector)
    checks = [STYLES[table[c]][1] >= 500 for c in HEADER_CLASSES] + [STYLES[table[c]][0] <= 14 for c in GREY_CLASSES]
    checks.append(STYLES[table["META"]][0] < STYLES[table["TITLE"]][0])  # meta reads under its title
    return sum(checks) / len(checks)


def preservation(vector: dict, context: dict) -> float:
    """The share of sites whose style the table leaves as it is — the search improves what is
    built, it does not redraw it; a change has to be paid for by the other signals."""
    table = _table(vector)
    n = same = 0
    for s in _sites():
        st = _style_of(s, table)
        if st is None or s.style is None: continue
        n += 1
        if ts.ALIAS.get(s.style, s.style) == st: same += 1
    return same / n if n else 1.0


def distinct_sizes(vector: dict, context: dict) -> float:
    """The table spends at most five sizes."""
    table = _table(vector)
    n = len({STYLES[v][0] for v in table.values()})
    return 1.0 if n <= 5 else max(0.0, 1 - (n - 5) / 2)


SCORERS = {
    "type.hierarchy": hierarchy, "type.uniqueness": uniqueness, "type.screen_budget": screen_budget,
    "type.ground_ratio": ground_ratio, "type.header_weight": header_weight, "type.distinct_sizes": distinct_sizes,
    "type.preservation": preservation,
}
