# Critique — the Calendar's week grid, on the 14f·2 build

*2026-09-16 · `design-critique-plus`, the post-build function walk for 14f·2. Read-only;
observable-level findings. **Desktop** at 1200×800, Compact: Ctrl+2 (opens on Week); the grid;
a long-press drag of Wednesday's *Standup* to Thursday 20:15 → *this one or all?* → *This one*;
the bar's *Quick add* → `Dentist fri 14:30` → the chips → Enter → the block; Esc; Settings →
*Opens on: Day* → the stored key; Ctrl+3 → the Tasks filters over the list only. **Phone: not
walked** — testing paused; the Week strip and the Day view's field should be unchanged there,
and the *Opens on* row sits in the Calendar settings sheet.*

## Top priorities

1. **[Med · fixed before merge]** The grid opened at 07:00 with the week's only blocks at 22:00
   — the occurrences arrive a frame after the first composition, and the opening scroll had run
   on the empty pass. **Fix taken:** the scroll effect is keyed on the week *and* on whether it
   has blocks, so it lands an hour before the first block once they exist.
2. **[Med · environment, recorded]** The app died on a paste into the quick-add field:
   `IllegalStateException: cannot open system clipboard` from Compose's `TextFieldSelectionManager.paste`
   on the AWT thread (the driver's clipboard-backed typing raced the clipboard). Not this PR's
   code, but a clipboard hiccup should not end the app: an uncaught-exception handler on the
   desktop that logs and keeps the window is one line in `Main.kt` — **§0.10 item 21 candidate**
   (the windows spec's row says so).
3. **[Low · judged]** The strip stays open after Enter, its field cleared — right for a burst
   of entries; a second Enter on an empty field does nothing (guarded). Esc closes it.
4. **[Low · judged]** The all-day row's chips carry the task tint (*Keyboard task*, *Pane task*
   on `primaryContainer`); an untimed *event* would take `secondaryContainer` — the layer's
   colour, as the timed blocks do. Consistent; the legend is the layer chips above.
5. **[Low · judged]** *Planned 30m* under every weekday for the same recurring *Standup* reads
   as noise in a week with nothing else; with real weeks it is the number Sunsama shows per day.
   Kept.
6. **[Low · by design]** The week bar (‹ 14 – 20 settembre 2026 ›) sits under the layer chips,
   a third row before the grid — the Day view has the same stack. 14g's registers can revisit
   the tab's vertical budget; not here.

## Dimension by dimension

- **Affordance** — the drag ghost names the target (*→ gio 17 20:15*) while the block dims;
  the series prompt names both the day and the time. The now-line runs across today's lane
  only, with the dot at its left edge.
- **Reach** — the bar's ⊕ and the strip; the header's click to the Day; ‹ › a week at a time;
  the Settings row and the sheet's row write one key.
- **Fit for purpose** — Fantastical's week, at the Day view's own geometry: the same hour
  height, the same block shapes (dashed when estimated), the same quarter-hour snap. The strip
  stays below 840 dp, so a narrow window keeps a week it can read.
- **Colour** — blocks in the layer's tint with `onSurface` text (the mock's 2.70:1 chips did
  not recur); the gutter `onSurfaceVariant`; today's lane at a quarter of `primaryContainer`.
- **Copy** — *Quick add…* as the field's placeholder; *Previous week* / *Next week*; *Move
  "…" to 2026-09-17 20:15?*.

## What's working

- Date and time in one drag, through one editor call.
- The grid as seven Plan lanes: nothing new in the block model, the overlap packing, the snap.
- The quick-add strip's chips: *Event · ven 18 set · 14:30* read back what will be written.

## Cannot verify

- **The phone** (paused): the strip and the Day field unchanged; *Opens on* in the sheet.
- Overlapping blocks side by side (no two timed things at one hour in the dev data) — the
  packing is `timelineBlocks`', unit-tested for the Day view.
- A relaunch on the stored *Day* choice (the key was written and the rule is unit-tested).

## Disposition

#1 fixed. #2 → §0.10 item 21. #3–#6 stand. The phone walk → the
PR's open item.
