# Scope decisions

**What this file is.** The register of what is deliberately *not* built, and why.
`tendril-spec.md` §10 stopped tracking this on 2026-09-06 — correctly, since an inline
register of that size goes stale faster than it can be read — but it then declined to
name where the worklist had gone. It had gone into a session scratchpad outside version
control. This file is the answer §10 refused to give.

**Reading rule.** Every row says how its status was established. `verified` means measured
against the tree on the date given. `asserted` means a plan document claims it and nobody
has checked. Do not promote an `asserted` row to fact by citing it.

---

## Rulings — 2026-09-08

| # | Item | Ruling | Note |
|---|---|---|---|
| DB1–DB5 | Relation, rollup, formula, summary/explain, computed grouping | **Build** — **DB1 done (`b26c92d`), DB2 done (`5595563`), DB3 core done (`d224e4b`), DB3 wiring done, DB4 done, DB5 done** | Reversal of §5.4's original deferral, already recorded 2026-09-06. Zero schema change: relation edges encode into the existing `PropertyValue.value`, rollups compute on read, the expression lives in `Property.config`. Gated on Milestone 0 reaching **every** device first — see the precondition below, confirmed satisfied 2026-09-08 (both installs verified as descendants of Milestone 0's commit). DB1 and DB2 shipped with zero *sync* code changes: a relation cell and a rollup's own definition are both plain strings in columns the sync layer already carries opaquely, so `PagesSyncEngine` needed nothing for either. **DB2 ships under the type name `COMPUTED`, not `ROLLUP`** — ruled 2026-09-08 (see below), and its config is a structured picker descriptor, not yet the expression text §5.4 describes. **DB3 is sliced in two, the same way S1 split into S1a/S1b**: the core evaluator (lexer, parser, type checker, dependency graph with named-path cycle rejection) shipped first, fully tested with nothing wired to a real database yet. **The wiring PR, ruled 2026-09-08 (see below), gives `COMPUTED` a real `ƒ` formula editor over this database's own plain properties and other formula-authored `COMPUTED` properties — cross-formula references and cycle rejection both work — but not yet a relation or a rollup.** Rollups keep using [`computeRollupValue`][com.tendril.app.ui.pages.PageDatabaseViewModel.computeRollupValue] exactly as DB2 shipped it; a `COMPUTED` property's config is tagged (`"formula:"` prefix vs. DB2's untagged three-field string) so the two authoring paths coexist without either one needing to know the other exists. Relation-aware formulas (`relation()`/aggregate functions inside the expression language, or migrating rollups onto it) are explicitly deferred to a later PR, not started. **DB4 (per-column Summary footer + "Explain this value")**: `TableBody` gets a footer row — `NUMBER` and `COMPUTED` columns show `Σ sum · ⌀ average` over the currently *displayed* (filtered) rows, every other type reads as the acceptance test's "empty" case. Tapping a `COMPUTED` cell opens a read-only sheet naming what it was built from — a formula's direct `prop()` references and their current values, or a rollup's related rows and the value read from each — never an editor, matching `ComputedCell`'s existing "no tap-to-edit" rule. **DB5 (formula-driven view grouping)**: `boardColumns` groups by a formula-authored `COMPUTED` property too, not only `SELECT` — its columns are the distinct non-empty values the formula actually produced across the displayed rows (there is no fixed option list the way `SELECT.config` has one), computed via the same row-local, synchronous `resolvePropertyFormulaValue` the evaluator and "Explain this value" already share, so no suspend call is needed inside the `combine` block. **Rollup-driven grouping is explicitly out of scope, not merely unstarted** — a rollup aggregates across a relation, which needs suspend DAO calls per row, and `boardColumns` recomputes on every displayed-row change; the group-by picker (`ViewConfigSheet`) simply never offers a rollup-authored `COMPUTED` property, the same "not yet, needs its own PR" cut `addFormulaProperty` already made for a formula referencing a relation. A `BoardCard`'s "Move…" reassignment menu is hidden for a `COMPUTED`-grouped board — a derived value has no cell to write a new value into, matching `ComputedCell`'s own no-tap-to-edit rule — closing out the DB1–DB5 chain §5.4 originally deferred, short of relation-aware formulas and rollup/formula unification, both still explicitly deferred per the DB3-wiring ruling above. |
| — | DB3 wiring: unify rollups into the formula language now vs. ship plain-property formulas only and leave rollups on their existing picker | **Plain-property formulas only** | A relation's related rows live on a different database; resolving them needs suspend DAO calls, which `FormulaPropertyResolver` — a synchronous, row-local function by design (see `FormulaEvaluator.kt`'s own note) — cannot make. Making it able to would mean extending `FormulaAst`/`FormulaEvaluator`/`FormulaTypeChecker` to special-case `relation()`+aggregate calls, a materially larger and separate piece of work from wiring the already-shipped, already-tested core evaluator up to a real ƒ editor. Chosen over unifying now for the same reason S1/DB1/DB2/DB3-core were each their own PR: the smallest slice that is real and shippable on its own, with zero risk to DB2's already-working rollups. |
| — | DB2's shape: `PropertyType.ROLLUP` now with a rename later, vs. `PropertyType.COMPUTED` now with structured (non-expression) config | **`COMPUTED` now, structured config** | §5.4 states the final shape as one type with two authoring paths onto one evaluator — pickers that write an expression, revealed and editable as text behind a `ƒ` toggle. Building that literally would require at least a minimal expression parser inside DB2, which is DB3's declared scope. Shipping `ROLLUP` now would mean DB3 renaming the enum member and migrating every stored property's config — exactly the churn §5.4's own reasoning was written to avoid. `COMPUTED`'s meaning is *extended* by DB3, not replaced. |
| — | `INTERVAL` in the general New-property picker | **Stays hidden** | Confirmed as intentional, not a gap. §4's reasoning stands: a general-purpose Interval is a second, uglier way to represent a plain number when `NUMBER` exists. The two `filter { it != PropertyType.INTERVAL }` calls in `PageDatabaseScreen.kt` are the design, and removing them would be a regression. |
| X3 | Wallpaper contrast for widgets | **Build the hinted half only** | `WallpaperManager.getDrawable()` is restricted to the default launcher from Android 13 and is genuinely unbuildable here. `getWallpaperColors(FLAG_SYSTEM)` is API 27+, needs no permission, and returns `HINT_SUPPORTS_DARK_TEXT`. Build that; do not plan the sampled half. |
| X2 | Live embed blocks | **Build as static preview cards** | Compose Desktop has no WebView and `PageDetailScreen` now renders on both platforms, so a live embed cannot work on one of its two targets. A preview card renders identically on both. |
| X4 | Desktop App Lock | **Build as a passphrase gate** | `BiometricPrompt` has no JVM equivalent. Passphrase needs zero new dependencies; the Windows Hello / JNA route would contradict the no-non-AndroidX-dependency posture the desktop spec enforces elsewhere. |
| C4 | AI features using the user's own API key | **Build — the exclusion was miscategorised** | Filed under "needs a server" by an audit sentence that bundled it with real-time collaboration. The user's own key going directly to Anthropic needs no Tendril server and no Tendril account — the same shape as the shipped Google Calendar integration. The plan excluded this in one section while scheduling the identical `SecretStore` path in another. |
| C2 | Template sharing | **Build the substitute, not the gallery** | A *hosted* gallery genuinely needs a server and a publishing identity. Export/import of a single template as a `.tendril` file through the existing `PortableArchive`, plus a bundled starter set, needs neither. |

### Still excluded, unchanged

| # | Item | Why |
|---|---|---|
| C1 | Real-time collaboration | Needs a server, accounts and presence. A different product. |
| C3 | Embedded P2P / IPFS sync | Reverses the founding "Syncthing replicates a folder" decision, needs per-ABI native binaries, and is redundant — the existing design works. |
| C5 | Desktop home-screen widgets | Windows offers no surface a sideloaded JVM app can occupy. A tray popup is new design, not a port. |
| X1 | Character-level text CRDT | Every mature implementation is JS or Rust; a KMP consumer needs native binaries for four Android ABIs plus a Windows JVM under the 16 KB page-alignment requirement, and nothing here ships a `.so`. **This does not block per-block merge — they are separate questions.** |

### Open design question — not cut, not scheduled

**Desktop calendar (was X5 and C6).** Neither proposed answer is accepted. A desktop OAuth
loopback flow works but would hold a persistent refresh token on disk, which is precisely
what the Android design was built never to do; emitting `.ics` into a folder is a
degradation, not a design. Ruled 2026-09-08: **the desktop needs a different structure for
calendar, to be designed separately.** Filed here as an open question so it is not mistaken
for either a cut feature or scheduled work.

---

## Preconditions that outrank the features

**No new `PropertyType` member may ship until every device runs Milestone 0.** Before
Milestone 0, a device receiving an enum value it cannot parse aborted the entire sync
pass — pages, entries, habits, purges — rather than degrading one column. After it, the
column is quarantined and everything else syncs. For DB1 this means Milestone 0 must be
*installed* on both machines, not merely merged.

**Room comes off destructive migration before any schema-changing item.** Split in two,
because only one half can be finished responsibly today.

- **S1a — done 2026-09-08.** `exportSchema = true`, and
  `shared/schemas/com.tendril.app.data.TendrilDatabase/8.json` is committed. Eight versions
  had been compiled and none could be migrated *from*, because Room builds an
  `@AutoMigration` by diffing the previous version's JSON and there was none. Both KSP
  targets write one file, verified byte-identical rather than assumed.
- **S1b — not done.** `fallbackToDestructiveMigration(dropAllTables = true)` is still live.
  Removing it is safe while the version does not move, and its value only arrives at the
  first bump — so it belongs immediately before DB6/DB12/DB13, which change the schema, and
  is not a gate on DB1/DB2/DB3, which do not.

**Open, and I cannot close it.** §9.10's acceptance criterion for S1b asks that the
snapshot-restore fallback be exercised once on a *populated* device against a deliberately
broken migration. §9.10's own 2026-09-06 correction records that the path does not do what
the section describes: Restore merges page-shaped data rather than wiping, and the
"snapshot-folder export" recovery it names does not exist. The two cases are
indistinguishable on a fresh install, because a destructive migration has already emptied
Room by the time Restore runs — so testing it the easy way returns green and proves nothing.
This needs real hardware with real data on it, and until then S1b removes a safety net whose
replacement is unverified.

**A related fragility, found while verifying the above.** `room.schemaLocation` is a KSP
argument, not a declared Gradle output. Deleting a committed schema file does not invalidate
any task, so no ordinary build restores it — it comes back only when KSP itself re-runs. A
schema JSON deleted by accident stays deleted through a green build.

---

## Verified findings — 2026-09-08

Measured, not asserted.

**Four fields are stored, synced across devices, and never displayed.** Every reference in
the repo outside build output:

| Field | Entity | Snapshot | Sync mapper | Importer | Composable |
|---|---|---|---|---|---|
| `Block.calloutColor` | 1 | 1 | 2 | — | **0** |
| `Block.imagePath` | 1 | 2 | 3 | 2 | **0** |
| `Block.codeLanguage` | 1 | 1 | 2 | 2 | **0** |
| `PageDatabaseView.visiblePropertyIds` | 1 | — | 2 | — | **0** |

An imported picture is invisible in the app that stores it and travels faithfully to a
second device that also cannot show it.

**A fifth, found 2026-09-08 by the check written to catch this class.** `Tag.color` is
computed from an eight-colour palette whose own comment records tuning every pair to at
least 12 CIEDE2000 apart under normal vision, deuteranopia and protanopia — and nothing
reads it. `PageDetailViewModel` re-reads a tag from the DAO rather than constructing one,
specifically to preserve the derived colour it then does not use. Careful accessibility
work on a value that renders nowhere.

**`audit.py` was weaker locally than in CI.** Its scan walked `.claude/worktrees/`, a
gitignored full copy of the repository, so every symbol counted twice on a developer
machine and once on the runner. The dead-declaration check can never fire on a doubled
symbol, which is the dangerous direction: a local PASS that CI would not give. Fixed by
excluding `.claude`; the check is now stronger locally than it has ever been, and still
finds nothing.

**The mechanism is a tooling gap, not carelessness.** `tools/audit.py`'s dead-declaration
check anchors its regex at column 0 — its own comment says so. Every entity field, member
function and enum member is indented, so the audit has never checked one. It reports clean
because it structurally cannot look where these live.

**A naive fix would still miss them.** `SnapshotMappers` and `PagesSyncEngine` reference
every one of these fields exactly twice, encoding and decoding. Sync makes dead data look
alive, so a "field with no readers" check has to discount the sync lane specifically.

**A recorded limitation, disclosed rather than found by accident: a DB2 rollup cell can go
stale across databases.** `ComputedCell` recomputes when the row it sits on changes, since
that is what its `LaunchedEffect` key observes. It does not recompute when a *related* row's
target property changes on the database the rollup points at — this ViewModel only observes
its own database's `property_values`, so an edit on the other side produces no signal here at
all. Re-opening the page always shows the current truth; a live cross-database update does
not arrive on its own. Accepted for now because full reactivity would mean watching every
possible target row, which "compute on read" was chosen specifically to avoid — and because
DB3's evaluator will need real dependency-graph invalidation for arbitrary formulas anyway,
which is the more natural place to solve this once, rather than build a rollup-specific
partial answer now.

---

## Provenance

The stage-by-stage build order and per-item size estimates live in `build-order.md`,
imported from the planning artifact that produced them. Its per-item claims are `asserted`
in the sense above unless a row here says otherwise.
