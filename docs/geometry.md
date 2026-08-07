# Geometry conventions

All geometry in `geometry/` is pure Kotlin — no Compose, no Android imports. This
file documents the conventions so the numbers in code are legible without
re-deriving them.

## Angle convention

**Degrees, clockwise, 0° = 12 o'clock (screen up).** This matches the HTML
prototype's `polar()`/`point()` functions, not Compose's `drawArc` (which is
0° = 3 o'clock). The `-90°` conversion happens exactly once, at the point where a
resolved angle is handed to `drawArc`/`Path.arcTo` — nowhere else in the codebase
should that offset appear.

```
        0°
         |
270° ----+---- 90°
         |
       180°
```

## `AngularSpan.endDeg` is deliberately not normalised

A span starting at 330° with a 60° sweep has `endDeg = 390f`, not `30f`. Keeping the
un-normalised end turns every span operation (containment, splitting, subtraction)
into ordinary 1-D interval arithmetic with no wraparound branch. Normalisation
(`normalizeDegrees`, → `[0, 360)`) is applied **only** when converting an incoming
pointer angle (`Angles.angleOfOffset`) — never to a span's own coordinates.

## The seek-track trick: relative-from-origin space

A seek track may be built from multiple physical arc segments (e.g. the volume
child's own wedge, and the parent's close wedge, are both excluded from a submenu
seek track — see the prototype's `subSeekInfo()`). The naive approach — walking
absolute angles and special-casing the 0°/360° crossing — is where wraparound bugs
live.

Instead, `SeekTrack.build` re-expresses every excluded span as an interval on the
number line `[0, trackLength)`, measured as **clockwise degrees from the track's
origin** (`relativeDegrees(origin, angle)`). In that space:

- the full candidate track is just `[0, 360 - anchorSweep)`
- each exclusion is `[relStart, relStart + sweep)`, clamped into range
- the free (drawable/seekable) segments are the **complement** of the merged
  exclusions within that interval

Because segments are produced by subtraction, an excluded angle is *structurally*
incapable of ever mapping to a progress fraction — there is no runtime guard to
forget, unlike the prototype's manual bounds checks.

`fractionAtOrNull(angle)` walks the resulting segments (already sorted clockwise by
construction) accumulating free sweep so far; the mapping is continuous across a
segment boundary because both sides of the boundary are defined as the same
cumulative value, not derived independently from absolute angle. This is the
property verified by the round-trip and monotonicity tests in
`SeekTrackTest`.

## Base radius

The prototype's 500×500 viewBox constants (`mainInner=138`, `mainOuter=202`, …) are
kept only as the **default fractions** in `OneRingDefaults` (`138/250 = 0.552`,
etc.) — see `OneRingDimensions`. The geometry engine itself never hard-codes a
canvas size; `ResolvedRing` (Phase 2) is what turns a fraction/Dp config into pixels
for the actual Compose canvas, using `min(width, height) / 2` as the base radius.

## Half-open wedge ownership

A wedge's span owns `[start, start+sweep)` — half-open. This means adjacent wedges
never both claim a boundary angle, and no angle is orphaned. Stated once here,
enforced by `HitTest`, and exercised exhaustively in `HitTestTest` at 0.1° steps.
