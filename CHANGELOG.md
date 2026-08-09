# Changelog

All notable changes to this project are documented in this file. Format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning
follows [Semantic Versioning](https://semver.org/) (pre-1.0: breaking changes
bump the minor version).

## [Unreleased]

Everything below has landed on `main` but nothing has been published anywhere
other than a local Maven cache (`./gradlew :one-ring:publishToMavenLocal`).
There is no `0.1.0` tag/release yet.

### Changed

- **The icon -> X change is staged rather than crossfaded.** Opening a menu (or
  a main-ring seek) now fades the item's own glyph out, transforms the wedge,
  and only then fades the close glyph in; closing reverses it. The two glyphs
  are separate animated layers (`iconAlpha` / `backIconAlpha`) rather than one
  slot's contents, so they are never both meaningfully visible.
- **The back (X) control no longer looks permanently selected.** Accent colour
  and stroke now follow the *pointer highlight* only; structural state (which
  parent owns the open submenu) no longer implies a highlight. The wedge keeps
  its expanded back-control geometry but rests in normal styling, and
  highlights only while touched.
- **The back control commits on release, like every other wedge.** It used to
  close on pointer-down; now it highlights on press, un-highlights if you scrub
  off it, and only closes if the finger lifts on it. Scrubbing between the X and
  a submenu child moves the highlight naturally.
- `OneRingDefaults.motion(iconMorph = ...)` now defaults to `tween(110)` (was
  `tween(200)`) and is actually used -- it drives both glyph fades, which run
  twice per transition.

### Fixed

- **The submenu popped in at full size instead of growing.** The reveal was
  animated correctly, but `state.path` -- which the renderer gates submenu arcs
  on -- was only published *after* the parent's select animation finished, so
  every intermediate reveal value was computed into a submenu that wasn't being
  drawn yet; the first frame that actually drew it was already at reveal ~1.
  The open transition now pins the reveal to zero thickness, publishes `path`,
  and only then animates it open, so the growth is genuinely rendered. Closing
  keeps `path` published until the shrink finishes, for the same reason.
- Programmatic `commitDirectly`/`closeDirectly` (the accessibility path) are
  now gated on a settled phase, matching the pointer path. Previously a
  semantics-driven close could interleave with an in-flight open and the two
  coroutines would race over `phase`/`path`.

### Added

- **Weighted (asymmetrical) sectors.** `OneRingItem.weight` (default `1f`)
  sizes a wedge relative to its siblings: `2 / 1 / 1` and `50 / 25 / 25` both
  give the first item half the ring. Weights are normalized against their
  siblings and scaled to whatever sweep is available, so they work on partial
  rings, rotated rings, and nested submenus (where children are weighted
  independently of their parent). Seek items are weighted too, and the seek
  track is built from the selected wedge's real span. Invalid weights
  (`<= 0`, NaN, infinite) throw at construction. Rings that never mention
  weights are unchanged.
- **Submenu wedges now grow into place.** Children start as zero-thickness arcs
  pinned at the submenu's outer radius and expand inward to the configured
  inner radius, with their icons riding the growing band and fading/scaling in
  (0.85 -> 1.0) rather than popping. Closing reverses it. Angular boundaries
  stay perfectly stationary throughout, which matters now that siblings can be
  different widths.

- Core radial interaction engine: press-scrub-highlight-release, with a
  stutter-safe `Animatable`-based animation model (see `docs/PLAN.md` section
  10) -- a committed selection always continues smoothly from wherever its
  hover animation currently was, never snapping.
- Nested submenus, one level deep: a `Menu` item grows into the close control
  on commit and reveals its children as a submenu band expanding inward from
  a pinned outer edge.
- Radial seek controls (`OneRingItem.Seek`), both on the main ring and nested
  inside a submenu, with an intro affordance hint and step quantization.
- Wedge content rendering: `OneRingIcon.Vector` / `Painted` / `Content` are
  drawn as real Compose child nodes at each wedge's midpoint, tinted by state
  (normal / highlighted / disabled), fading and moving outward with the wedge's
  own animation. An open menu's (or active seek's) wedge shows a drawn close
  glyph, overridable per item via `closeIcon`. Previously `OneRingIcon` was
  accepted by the model but never rendered.
- `oneRingItems { }` DSL for building item trees.
- `OneRingEvent` firehose (`onEvent`) and `OneRingHaptics` (default
  `View.performHapticFeedback`-backed, fully replaceable).
- Style presets: `OneRingDefaults.compactStyle()` / `automotiveStyle()` /
  `gameStyle()`.
- Real (invisible) per-item accessibility semantics nodes -- TalkBack can
  navigate, open/close menus, and enter/exit a seek control without any
  pointer gesture. Reduced-motion (`ANIMATOR_DURATION_SCALE`) is honored.
- Dokka-generated API documentation (`./gradlew :one-ring:dokkaGeneratePublicationHtml`).

### Known limitations

- TalkBack can enter/exit a seek control but can't yet drag-adjust its value
  via semantic actions -- a real touch drag is still required.
- Nesting is capped at one level; a `Menu` or `Seek` inside a submenu isn't
  interactive.
- Not published to any remote repository. Maven Central publishing (signing,
  a verified namespace) is still open.
- Item **labels** are never drawn -- only icons. A wedge with
  `OneRingIcon.None` renders as a bare arc (its label still reaches
  accessibility services). Text-in-wedge layout is future work.
- Pressing the close control and then dragging (instead of lifting) leaves the
  rest of that gesture unclaimed, so an enclosing scrollable can take it over.
