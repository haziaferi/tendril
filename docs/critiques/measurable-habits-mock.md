# Critique — `docs/mockups/measurable-habits.html` (§0.10 item 3, pre-build)

*2026-09-19 · `craftkit ui` + `render` on the mock; the ground measured live on the phone
(TickTick 7, Italian, `uiautomator` dumps: a habit *Bevi acqua* made from its gallery with the
goal *Raggiungere una certa quantità* — 3 *Tazza* a day, 1 recorded per check-in, *Auto*; one
check-in; the detail and its statistics). TickTick's Windows app carries the same model
[Assumed — not measured; the phone's is the reference]. Measured and judged kept apart.*

## Ground (TickTick's amount habit — measured)

| | measured |
|---|---|
| the goal | two kinds: *Realizza tutto* (a check) or an amount — **daily 3 · unit *Tazza* · 1 recorded per check-in · *Auto*** (the tap records it) or manual |
| the row | *Bevi acqua* with **'0 / 3 Tazza'** under it; a tap on the icon adds one → **'1 / 3 Tazza'**; 68 dp rows |
| the detail | a hero, a slide-to-check, a **progress bar** under the same fraction |
| the statistics | *Controllo mensile* (days), *Check-in totali*, **Tasso di registrazione mensile %**, **Serie attuale** (the streak), **Completamento mensile 1 Tazza**, **Totale completamento** |
| the free tier | the habit tab is off until turned on under *Barra delle schede* (turned on for this ground — the user may turn it off) |

## Top priorities

1. **[High · measured] The row's pill is under both minimums.** The mock draws *+ 1 cup* as a 22 dp pill where the checkbox stood: 28 dp is the pointer profile's interactive minimum (`listInteractiveMinDp`) and 48 the phone's target (L·P1: the checkbox at 40 in a 48 target). **Fix:** the leading control is the checkbox's own slot — a **28 dp `+` disc** under a pointer, 40 in a 48 dp target on the phone — and the words go to the meta (*2 cups today*); the pane's chip carries *+ 1 cup* in full.
2. **[Med · judged] A pill wider than a checkbox shifts the title.** Two habits in one list would start their titles at different x — no list in the app does that. The fix in #1 (a disc in the checkbox's slot) removes it.
3. **[Low · judged] *You set 3 cups a day* sits four lines above *2 cups today*.** Read together they make the fraction the rule forbids. Keep the number in the pane and the sheet as the mock has it — once, as a sentence, in `onSurfaceVariant`, **after** the presence lines, not before them — so the count is what the eye lands on. Applied to the build; the mock's order stands as the record.

## Dimension-by-dimension

**Hierarchy** — the count (*2 cups today*) is the row's second line and the sheet's first sentence: presence first. The optional number is last and dim.
**Colour and contrast** — no new tokens; the meta on `dim` as every row's; the dots unchanged (a day's dot is presence, never a size — no heat).
**Typography** — the row at `body` / `description`, the pane's rows at `body`, the eyebrow *PRESENCE*; on the scale.
**Layout** — the pane's *Counts* and *You set* rows as the task pane's `DetailRow`s; the sheet's sentences at the phone's 16/24.
**Copy** — *cups, 1 a check-in*; *2 cups today*; *41 cups this month, on 12 days*; *Undo the last cup*; the New habit switch *Counts something* with *Unit · Each check-in · A day, if you like* — the third field's placeholder says it is optional. The unit is written as the person types it (*cups*), never pluralised by the app: one word, no grammar to get wrong in two languages.
**Interactivity** — a tap on the disc adds one check-in with the habit's amount (TickTick's *Auto*); a second tap adds another (repeatable, unlike a plain habit's one-a-day); *Undo the last cup* tombstones the last check-in — the undo that exists.

## What's working
- The value lives on the log entry (`habit_completions.value`), which is what the item asked; a plain habit's completions carry null and nothing changes for them.
- The presence rule survives: every number shown is something done; the one the person set is a sentence, once.
- TickTick's two useful statistics (the month's and the total amount) are the two new sentences; its rate and streak are not taken.

## Cannot verify
- TickTick's desktop form of the row; Loop Habit Tracker's measurable habits (not installed) [Assumed: a numeric target per day with a bar — the same pressure form].

## Decisions for the user (one batch, before the build)
1. The value's source — a fixed amount per check-in, added by the tap (TickTick's *Auto*), or a number typed at each check-in.
2. The number the person sets — none, or optional and shown once as a sentence (never a fraction, bar or rate), or TickTick's fraction on the row.
3. The row's control for a counting habit — the `+` disc in the checkbox's slot, repeatable, or the checkbox marks the day and the amount is added in the sheet.
4. Schema v21 — `habits.unit`, `habits.amountPerCheckIn`, `habits.dailyAmount` (nullable) and `habit_completions.value` (nullable), carried by the snapshot as nullable fields an older peer ignores.
