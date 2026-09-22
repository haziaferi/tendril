# The widget readout on the real wallpaper — the build walked

*2026-09-22. §8.6's deferral and §10's two widget bullets, answered by one addition to the
configure screen: the system's own reading of the wallpaper (`WallpaperManager.getWallpaperColors`
— its primary colour and its dark-text hint; API 27+, no permission), the five roles audited on
it beside the adversarial bracket, and the smallest opacity at which every role passes there,
offered as a tap. The mode stays the app's (§8.7 keeps it off this screen), so the "mode
auto-suggestion" becomes an opacity suggestion — the one control the screen has. Phone only; the
desktop has no widgets. No mock (a readout over an existing screen).*

## The phone (the OnePlus; dumps and screencaps)

No Tendril widget is placed on the home screen, so the activity was opened with the launcher's
clock id (`am start … --ei appWidgetId 2`) — which found a robustness defect: the saved-state read
called `getGlanceIdBy` outside its `runCatching` and threw for a foreign id. Fixed (the defaults
stand, as for a first configuration); Save was never pressed.

| tried | observed |
|---|---|
| the readout at the default 88 % | the bracket: every role passes (8.3 / 5.0 / 3.3 / 5.3 / 5.9 : 1); **On your wallpaper**: *A dark wallpaper, its main colour #AC0B23* (the phone's red), every role passes (11.5 / 7.0 / 4.6 / 7.4 / 8.2), and the line *Every role passes here — 35 % would too, and shows more wallpaper* |
| tap it | the slider drops to 35 %; the bracket fails all five (1.0–1.6 : 1 — the adversarial extreme), the wallpaper section passes all five (7.7 / 4.7 / 3.1 / 5.0 / 5.5) — *Every role passes on this wallpaper at 35 %.* |
| Cancel | nothing written; the clock widget untouched |

The two sections say different things on purpose: the bracket is the worst any wallpaper could
be, the second is this phone. The suggestion is a multiple of 5 and the smallest that passes
(`WallpaperAuditTest`: on a white wallpaper a light widget needs less than on a black one; the
step below the suggestion fails a role; an impossible shade suggests nothing).

## Cannot verify

- A live wallpaper or a phone whose system reports no colours — the branch reads *The system
  reports no wallpaper colours here … the bracket above is the guide*; not reproducible on this
  phone without changing its wallpaper.
- The suggestion's *tap* on a real placed widget's save path — the same `opacity` state the
  slider writes; Save is unchanged.

Tests 930 → 934 (`WallpaperAuditTest` 4 — the first tests over the widget module's pure half);
audit PASS.
