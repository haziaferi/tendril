# The scrim, the build walked

*2026-09-21. Built on the mock's two decisions (`scrim-mock.md`): black at 24 %, the slide-over
and the centred card. The desktop at the dev window (91,35 – 1098,920), native grabs, Ink dark
then Chalk light; the phone's sheet untouched (Material's black at 32 %).*

## What was built

`Scrim` in `ui/theme/Palette.kt` (`Color.Black.copy(alpha = SCRIM_SHARE)`, `SCRIM_SHARE = 0.24f`),
read by `SlideOver` and `CentredCard` in place of `onSurface.copy(alpha = 0.32f)`. The theme folder
is the one home the audit allows a literal colour.

## The walk

| ground | frame | scrimmed ground | panel / card | the mock said |
|---|---|---|---|---|
| Ink dark | Pages `+` → the New sheet | **#151619** | #1B1D21 — lighter than the ground, its shadow reads | #151619 |
| Ink dark | Ctrl+K → the switcher | **#151619** | #1B1D21 | #151619 |
| Chalk light | Pages `+` → the New sheet | **#BFBFBE** | #FBFBFA; the tree's titles read through | #BFBFBE |

Pixel-identical to the mock's arithmetic on both grounds. Esc, the scrim and × close as before.

## Findings

- None. The panel's separation on a dark ground is its shadow's (1.07 : 1 from the scrim alone), as
  the critique said it would be; it reads.
