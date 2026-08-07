# Interaction spec (extracted from the HTML prototype)

Source: [`prototype/radial-wheel-final-prototype.html`](prototype/radial-wheel-final-prototype.html).
This is the authoritative behavioural reference for `one-ring`. Native
implementation choices that deviate from it are called out explicitly with a reason.

## Geometry constants (500×500 viewBox, base radius 250)

| Constant | px | Fraction of base radius |
|---|---|---|
| `mainInner` | 138 | 0.552 |
| `mainOuter` | 202 | 0.808 |
| `mainHover` | 220 | 0.880 |
| `selectedOuter` | 238 | 0.952 |
| `subInner` | 70 | 0.280 |
| `subOuter` | 128 | 0.512 |
| `subHover` | 140 | 0.560 |
| `center` | 54 | 0.216 |

These fractions are the defaults in `OneRingDefaults.dimensions()`.

## Core interaction model

Press → scrub → highlight → release-to-commit. Not tap-to-select, not hover
buttons layered on a canvas. While a pointer is held: the wedge under it is
highlighted (grows outward); moving across wedges moves the highlight; releasing
over a wedge commits it. Releasing off the ring cancels.

## Main menu

- N items divide the ring into equal wedges (`360/N`, item 0 centred at 12
  o'clock: prototype `mainGeo` starts at `-sweep/2`).
- Hovering a wedge grows its outer radius from `mainOuter` to `mainHover`, eased
  (`a += (target-a)*0.28`, an exponential smoother — reproduced natively with a
  `spring` `Animatable`, not a fixed-duration tween, because it must be interrupted
  smoothly on every wedge crossing).
- Alternating base fill colour per wedge index.

## Selecting a `Menu` item

1. The selected wedge **stays at its angle** and grows further, to
   `selectedOuter`.
2. Its icon cross-fades to a `×`.
3. Other main items fade/collapse away.
4. A submenu appears **inside the original main wheel's angular complement**:
   `360° - parentSweep`.
5. The parent's angular region remains a reserved gap — never covered by submenu
   content.
6. The parent wedge (now showing ×) **is** the back/close control. There is no
   separate centre close button.

## Submenu geometry

For a 60° parent (6-item main wheel) and 4 children: available space is
`360 - 60 = 300°`, divided evenly → 75° per child, starting at the parent wedge's
clockwise edge (`parent.endDeg`).

Generalised in `SubmenuLayout`: arbitrary parent sweep, arbitrary child count, with
a configurable max child sweep and alignment (Fill / CenterOpposite /
StartAtParentEnd) to avoid absurd wedges when child count is very small — a case
the prototype never exercises (always 3–4 children) but the library must handle.

## Submenu open/close animation

- **Open:** children start as a thin strip pinned at `subOuter` and expand
  **inward** — the outer edge is fixed, only the inner radius animates from
  `subOuter` down to `subInner`.
- **Close:** the reverse. Parent wedge shrinks from `selectedOuter` back to
  `mainOuter`, × morphs back to the icon, siblings return, children collapse
  outward back to a strip and fade out.

## No selection stutter (critical invariant)

If the user releases while a wedge's hover-expansion animation is mid-flight, the
selection animation **must continue from the exact current value** — not reset to
base, not snap to full hover, not restart. The prototype solves this manually with
a `state.snapshot` field captured at commit time. The native implementation solves
it structurally: every animated quantity is an `Animatable` owned by
`OneRingState` with a lifetime spanning every mode change, so `animateTo()` always
continues from wherever the value currently is.

## Main-ring seek item (Phase 6, designed not yet built)

A main-menu item can itself be a seek control (e.g. Volume). On selection: the
wedge grows and morphs to ×, its own angular region is excluded from the seek
track, and the remaining `360 - parentSweep` becomes a radial seek track. Progress
fills **clockwise starting at the × wedge's clockwise edge**; the × gap can never
become progress.

## Submenu seek item (Phase 6, designed not yet built)

Music → Volume: the parent's × wedge stays put; the Volume child wedge itself grows
and stays visible (not morphed to ×, since the parent is still the close control);
sibling children fade and their angular regions become the seek track, which is
therefore built from `SeekTrack` with **two exclusions** (parent gap + the Volume
wedge itself) and may be **1 or 2 physical arc segments** representing one logical
0–1 range (see `docs/geometry.md`).

## Seek intro hint (Phase 6, designed not yet built)

On entering a seek control: value animates `0 → clamp(original + overshoot, range)
→ original`, never below/above range, and never the prototype's older
`67→72→62→67` shape. Runs on a separate `displayValue`, distinct from the real
controlled value — no `onValueChange` calls fire during the hint.

## Known prototype defects intentionally NOT ported

- Input blocked entirely during the seek hint (`if(state.hint)return`) — the
  native default cancels the hint on touch instead, so the control never feels
  dead.
- `subSeekInfo()` hardcodes a child named `id==='volume'` and throws otherwise —
  the native `SeekTrack` is built generically from an anchor span, no id lookup.
- `pointercancel` doesn't exit seek mode in the prototype — native cancel handling
  resets `OneRingState` from every mode uniformly.
- Font-dependent Unicode glyph icons — native icons are `ImageVector`/`Painter`/
  composable content, resolution-independent.
