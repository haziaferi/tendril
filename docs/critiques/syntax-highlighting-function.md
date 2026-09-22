# Syntax highlighting in code blocks, and the export's `CLAUDE.md` — the build walked

*2026-09-22. §10's "nice-to-have" built as a rule over the existing code block (no mock — the
decided pass): `domain/code/Highlight.kt`, a hand-rolled tokenizer of four kinds — keyword,
string, comment, number — with a grammar per language for the named set (Kotlin, Python,
JavaScript / TypeScript, JSON, Bash, SQL, Markdown), a family's comment shape for the C-like and
`#` languages, and a generic strings-and-numbers pass for every other tag; painted by
`spansVisualTransformation` from the token map — keyword `third`, string `event`, number `habit`,
comment `textDim`. No library: the offline cache holds none, and four kinds need none. Folded in:
item 17's one buildable remainder — the Markdown zip carries a `CLAUDE.md` at its root
(`markdown/AgentReadme.kt`) with the folder's shape and the three rules.*

## The desktop (Ink dark, Compact; native grabs at 0.832 px/dp)

| tried | observed |
|---|---|
| *Call the library* → `/` → *Code* → a Kotlin snippet pasted, the tag *Plain text* | the generic pass: `"mon"`, `"sat"` in the event hue, `9`, `10`, `2` in the habit hue, the `//` and `/* */` lines plain (no comment shape for an unknown tag — by design) |
| right-click → *Block actions…* → *Language* → **kotlin** | `val`, `fun`, `return`, `null` in the third hue; both comments dim; strings and numbers as before; `String`, `Boolean`, `hours`, `day` plain |
| measured on the grab (the block's ground #1B1D21) | keyword (209, 188, 148) hue 39° **9.11 : 1** · string (169, 209, 148) hue 99° **9.83 : 1** · number (188, 148, 209) hue 279° **6.67 : 1** · comment (169, 169, 172) **7.20 : 1** — every ink over 4.6, the three hues ≥ 60° apart and none the accent (the token map's rule) |

**Found and fixed:** `open` in `fun open(day)` came out as a keyword — Kotlin's soft keywords
that double as identifiers (`open`, `set`, `get`, `field`, `it`, `value`, `by`, `where`, `out`)
are out of the set; a tokenizer without a parser cannot tell `fun open()` from `open class`, and
a plain function name is the cheaper miss. The desktop and phone binaries walked predate that
edit (a keyword list; `HighlightTest` pins the set).

## The phone (the OnePlus, 480 dpi × 0.85; screencaps)

| tried | observed |
|---|---|
| *Trip* → *Add block* → `/` → *Code* → two Python lines typed | the generic pass under *Plain text*: `"x"` in the event hue, `3` in the habit hue |
| long-press the row's margin → *1 selected* → a second long press → the sheet → *Language* → **python** | `def`, `return` in the third hue, `# count` dim, the string and the number as before; the tag line reads *python* |

Nothing phone-specific changed; the transformation is the shared field's.

## The export's `CLAUDE.md`

`MarkdownExporterTest` gains *the zip carries a CLAUDE.md at its root with the counts and the
three rules*; the four listing assertions read the page files alone (`pageNames`). Not walked on
a device — the exporter is JVM code the test exercises end to end through a real `ZipOutputStream`.

## Cannot verify

- The other named grammars on a device (JavaScript, JSON, Bash, SQL, Markdown) — `HighlightTest`
  pins each (case-insensitive SQL, JSON's three literals, Markdown's headings and code spans, a
  glued apostrophe as prose, an unterminated string or comment running to the end, tokens never
  overlapping).
- A page titled `CLAUDE` at the tree's root would share the zip's root name with the readme —
  the exporter's collision rule does not know the reserved name. Recorded, not fixed (a one-line
  reservation in `filePathsFor` when it is ever met).

Tests 924 → 930 (`HighlightTest` 5, `MarkdownExporterTest` +1); audit PASS; both compiles.
