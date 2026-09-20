# S8 — sibling reorder by drag, the build walked

*2026-09-20. Decided in one batch: the order is the position's (no schema); a drop among siblings
under Map / Right / Down tidies at once (Xmind's, Mindomo's); under Map the side is the node's
own — a drag across the root moves it over. The desktop at the dev window, native grabs.*

What was already true: `CanvasTree.children` sorted siblings by y on a side and by x under Down —
only the MAP root's *side* was by creation order (the first half right), and nothing tidied on a
drop, so a dragged sibling sat where the hand left it until *Tidy*. Three changes:

- `sideOf` under a MAP root: the node's centre against the root's (`Tree.kt`); a new child goes to
  the side with fewer children, the right on a tie (`newChildPosition`).
- The board's drop (`onDropAt`): a tree node not dropped onto a card, whose structure is not
  *Free*, tidies its tree from the root (`CanvasViewModel.tidyTreeOf`) — from the root, not the
  parent's subtree alone, since a band that grew moves its neighbours.
- A test: the side by position, the balance rule, a root alone growing right.

| tried | observed |
|---|---|
| Garden plan (Down): *Garden waste* dragged past *LeavesKitchen scraps* | on release the tree tidied — *Garden waste* and its child in the second column, the following frame and the relationship re-drawn |
| dragged back | the first slot again |

The rule as it now stands: a dragged node is where the hand left it only on a *Free* board; under
a structure the drop is the new order. Tests 898 → 899.
