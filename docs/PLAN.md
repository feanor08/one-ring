# one-ring — Implementation Blueprint

Native Android (Kotlin + Jetpack Compose) radial interaction library, generalised
from the interaction prototype at [`docs/prototype/radial-wheel-final-prototype.html`](prototype/radial-wheel-final-prototype.html).

Confirmed project decisions:

| Decision | Value |
|---|---|
| Group / package namespace | `com.feanor08.onering` |
| Library artifact id | `one-ring` |
| minSdk | 24 |
| targetSdk / compileSdk | 36 |
| Java / Kotlin target | 17 |
| Licence | Apache-2.0, public OSS |
| Modules | `:one-ring` (library), `:sample` (demo app) |

See [`docs/geometry.md`](geometry.md) for the angle/geometry conventions and
[`docs/interaction-spec.md`](interaction-spec.md) for the behavioural spec extracted
from the prototype. This file is the phase-by-phase engineering plan; the full
rationale (architecture, API design, testing strategy, risks, open questions) was
worked out in design discussion before implementation began and is summarised below.

## Architecture

Three layers, one-directional dependency:

```
L3  Public Compose surface     OneRing(), rememberOneRingState(), OneRingItem, OneRingStyle
L2  Internal Compose runtime   RingAnimations (Animatable) · RingInteraction (pointer) ·
                                RingFrameBuilder (pure) · RingRenderer (dumb draw)
L1  Geometry engine            pure Kotlin, zero Compose/Android imports
```

Key decisions:

- **Two-layer composition.** Arcs are painted via `drawBehind`; per-item icon/label
  content is composed as real children of a custom `Layout` positioned at polar
  coordinates. This is what makes `@Composable` content slots *and* TalkBack
  semantics possible at once.
- **`Animatable` registry with item-id-keyed lifetime that outlives every mode
  change**, owned by `OneRingState`. This is what makes the prototype's "selection
  stutter" bug structurally impossible: `Animatable.animateTo()` always continues
  from the current value and velocity, so a commit mid-hover-animation never snaps.
- **Seek tracks built by angular complement in relative-from-origin space.**
  Wraparound is eliminated algebraically; excluded gaps (the parent close-wedge, the
  seek item's own wedge) cannot become progress because they are subtracted out
  during construction, not filtered at draw/hit-test time.
- **Two Gradle modules only** (`:one-ring`, `:sample`). No `-core`/`-compose` split
  in v1 — the boundary is enforced by an architecture unit test instead.

## Phases in this pass

Built so far: **Phases 0–5, 7, 8**. **Phase 6 (seek controls) is the one gap** —
Phases 7–8 were built ahead of it, so `OneRingItem.Seek` exists in the data model
and the DSL, but committing one is currently a no-op (see Phase 3/4's commit
branches). Phase 9 (full sample polish, Dokka) and Phase 10 (publishing prep) are
designed below but not yet built.

### Phase 0 — Bootstrap
Git init; rename `app/` → `one-ring/` (`com.android.library`, namespace
`com.feanor08.onering`, minSdk 24, Java 17, `explicitApi()`); create `sample/`
(`com.android.application`); wire `settings.gradle.kts` / version catalog / Compose
BOM; add LICENSE, README skeleton, CI workflow.

### Phase 1 — Geometry engine
`geometry/` package: `Angles`, `AngularSpan`, `RadialBand`, `RingLayout`,
`SubmenuLayout`, `SeekTrack`, `HitTest`. Pure Kotlin, fully unit tested
(angle normalisation, wedge allocation for arbitrary counts, submenu complement-arc
allocation, multi-segment seek mapping with continuity/round-trip properties, hit
testing including wraparound). ~70% of this phase's code is tests, deliberately.

### Phase 2 — Model, style, static rendering
`OneRingItem` (Action/Menu/Seek), `OneRingIcon`, `OneRingStyle` and its
`Colors`/`Dimensions`/`Motion`/`Behavior` components, `OneRingDefaults` (fractions
sourced directly from the prototype's 500×500 constants). `ResolvedRing` (px
resolution of the style against a real canvas size). `RingFrame` (immutable visual
snapshot) + `RingFrameBuilder` (pure function, idle state only this phase) +
`RingRenderer` (dumb `DrawScope` draw pass) + `RingPathPool` (allocation-free path
reuse). First `OneRing()` composable — static, non-interactive.

### Phase 3 — Pointer scrub + animation foundation
`ItemAnimation` (three `Animatable<Float>`s: expansion, contentAlpha, closeMorph)
and `RingAnimations` registry, owned by `OneRingState` so lifetime spans every mode
change. `RingInteractionController` driven by a low-level `awaitEachGesture` loop
(not `detectDragGestures`, which applies touch slop the interaction can't afford).
Highlight-follows-scrub, release-off-ring cancels, `onAction` fires for leaf items.

### Phase 4 — Selection transition
Commit → the selected wedge's `expansion` **continues from its current animated
value** toward the "selected" target — the fix for the prototype's stutter bug,
achieved structurally rather than by a manual snapshot. Icon → × morph. Siblings
fade/collapse. The parent wedge becomes the close control (hit-tested on **down**,
not release, for an instant-feeling back action). Frame-delta continuity is
unit-testable because `RingFrameBuilder` is pure.

### Phase 5 — Submenu
`OneRingState.path` navigation; `SubmenuLayout` allocates the child arcs across the
360° − parent-sweep complement; submenu reveal animates the **inner** radius inward
while the outer edge stays pinned (matching the prototype exactly); close reverses
through the same `Animatable`s.

### Phase 7 — Public API hardening
`oneRingItems { }` DSL (action/menu/seek builders, duplicate-sibling-id validation
at construction time). `OneRingEvent` (HighlightChanged/ActionCommitted/
MenuOpened/MenuClosed) via an optional `onEvent` callback. `OneRingCenterScope` +
`OneRing(center = ...)` slot, which is why `OneRing()` now wraps its `Canvas` in a
`BoxWithConstraints` (also needed for Phase 8's semantics overlay). Three style
presets (`compactStyle`/`automotiveStyle`/`gameStyle`) on `OneRingDefaults`.
`binary-compatibility-validator` was evaluated and **not applied** — it registers
no `apiDump`/`apiCheck` tasks against an AGP-9-built-in-Kotlin module (needs the
`org.jetbrains.kotlin.android` target, which AGP 9 forbids applying alongside its
own Kotlin support); documented in `build.gradle.kts`. ABI hygiene stays manual:
`@ConsistentCopyVisibility` + internal constructors + `OneRingDefaults` factories.

### Phase 8 — Accessibility & haptics
Real (invisible) per-item semantics nodes (`RingSemantics.kt`), a fixed-size
target centered on each wedge's midpoint with description/role/state/
`traversalIndex`/`onClick` — a second, complete input path, since explore-by-touch
consumes pointer events and there's still no icon/content layer to shape nodes to
the wedge itself. `RingInteractionController.commitDirectly()`/`closeDirectly()`
let a semantic click run the exact same commit/close logic a pointer release
would. `OneRingHaptics` (fun interface) + `rememberDefaultOneRingHaptics()`
(`View.performHapticFeedback`, API-level gated), wired into highlight-change/
commit/reject/open/close and naturally deduped by `setHighlight`'s existing
no-change early return. Reduced motion via
`Settings.Global.ANIMATOR_DURATION_SCALE` → `effectiveMotion()` swapping every
spec for `snap()`. A one-time-per-item-count Logcat warning when a wedge's arc
length falls under 48dp.

### Phases 9–10 (designed, not built)
9 Full sample polish (preset gallery, screenshots) + docs + Dokka · 10 Publishing
prep (`publishToMavenLocal` smoke test, Maven Central metadata, signing).

## Testing strategy (applies from Phase 1 onward)

- **Layer 1 (JVM, `src/test/`):** exhaustive geometry tests — angle normalisation
  including wraparound and exact 360°, ring/submenu layout for arbitrary item
  counts, seek-track continuity and round-trip properties, hit testing at every
  0.1° for n = 1..16. An architecture test greps `geometry/*.kt` for
  `import androidx.compose` and fails the build if found.
- **Layer 2 (JVM):** `RingFrameBuilder` output tested as pure data — no Compose
  runtime needed to assert which arcs exist, at what radius, with what alpha.
- **Layer 3 (`androidTest/`):** Compose UI tests for the full press→scrub→release
  flow, submenu open/close, and (once built) seek adjustment and TalkBack semantic
  actions.
- **Stutter regression, specifically:** a pure-state test drives an `Animatable`
  mid-flight, interrupts it, and asserts the next frame's value is close to — not
  equal to — the interrupted value (frame-delta bound, not exact-match, since a
  spring is still moving). A Compose-level test repeats this through real pointer
  input. A static rule (documented, enforced by review + a detekt ban in later
  phases) forbids `animateFloatAsState`/`updateTransition`/`snapTo` inside the
  animation-owning package, since those are the exact APIs that reintroduce the bug.

## Open items deferred past this pass

**Seek controls (Phase 6)** are the one designed-but-unbuilt gap in the
implemented range — `OneRingItem.Seek` exists but committing one is a no-op.
Publishing metadata (Phase 10) and the automated binary-compatibility freeze are
also still open; the latter is blocked on tooling (see Phase 7 above), not on
unfinished work here.
