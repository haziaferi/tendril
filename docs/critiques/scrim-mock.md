# Critique — `docs/mockups/scrim.html` (the scrim under a slide-over, pre-build)

*2026-09-21. The user: "could the overlay that appears when a side window is opened be darker and
less opaque?" The slide-over (14b) and the centred card (L6) draw `onSurface` at 32 % — Material's
share on Material's black scrim, applied to the text colour. Grounds read from the shipped bundles
[Verified]: Notion `overlayBackground` #0F0F0F at 60 % light / 30 % dark (the side peek itself has
no scrim); Obsidian `--background-modifier-cover` #0A0A0A 40 % dark, #DCDCDC 40 % light; Material 3
black at 32 %. The phone's `ModalBottomSheet` already takes Material's black at 32 %.*

## Top priorities

1. **[High · measured] The text-colour scrim lightens a dark ground** — Ink dark's #1B1D21 becomes
   #57595C under it, and the panel (#1B1D21) reads 2.40 : 1 *darker* than the page it covers: the
   depth cue inverted. Every ground darkens (Notion, Obsidian, Material). Fix: a black scrim.
2. **[Med · measured] On a dark ground the scrim can barely darken** — black at 16–32 % leaves the
   panel only 1.05–1.09 : 1 lighter than the scrimmed page; the separation is the shadow's, as it is
   in Notion (30 %). The share is therefore chosen for the light ground, where it does the work:
   black 24 % → the page's text 7.9 : 1 through it (readable, plainly behind); 32 % → 6.7.
3. **[Low · judged] One rule for both frames** — the slide-over and the centred card share the
   scrim; the phone's sheet keeps Material's (black 32 %) unless one token is wanted everywhere.

## Cannot verify

- Hover states through the scrim, the fade-in — the build's walk.
