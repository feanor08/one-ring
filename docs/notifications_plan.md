# One Ring Notification Stack — Revised Implementation Plan (v4)

## 1. CURRENT STATE

### ALREADY IMPLEMENTED

- Grouping by `sourceKey` → `OneRingNotificationGroup { sourceKey, sourceLabel, sourceIcon, threads }` from `NotificationFeed.groupedBySource()`, exposed as `OneRingNotificationState.groups`.
- Group ordering by most-recent activity; threads are newest-first inside each group.
- Same-`threadKey` merge — appends messages, refreshes identity fields, bumps sequence, and moves the thread and its source group to the front.
- Dismiss by `threadKey` through the existing `NotificationCard` → `NotificationFeedList` → `OneRingNotificationHost` → `OneRingNotificationState.dismiss()` → `NotificationFeed.dismiss()` chain.
- Arrival splash with strict splash-before-message ordering via the existing safe-groups/on-reveal mechanism and sequence-watermark gating.
- Visibility gating by `OneRingMode`; the interactive branch uses the `LazyColumn`, while the hidden/non-interactive branch preserves composition without competing with ring gestures.
- Circular-edge scale/alpha transformation through the existing pure `circleEdgeTransform(...)` logic and deferred layer reads.
- Reduced-motion handling for arrival and visibility transitions.
- Accessibility semantics for cards, dismiss actions, arrival live region, and hidden-host state.
- Swipe-to-dismiss uses one unified gesture loop with directional locking, which is suitable for adding tap-to-expand without introducing a competing gesture detector.

### MISSING — THIS FEATURE SCOPE

- A collapsed Apple-style visual stack for multi-thread source groups.
- Rear notification cards physically peeking behind the newest/front card.
- Expand/collapse presentation state.
- Tap front card → expand.
- Tap front card again → collapse.
- A source-level active-thread count such as `WhatsApp · 3`.

### PARTIALLY IMPLEMENTED

The domain/data layer is already sufficient. This feature is primarily a presentation and interaction change.

---

## 2. PROPOSED UX

### One thread in a source

Render the current normal `NotificationCard`.

No stack treatment, no source-thread count, and no expand/collapse behavior.

### Two or more threads in a source — collapsed by default

Render one Apple-style stack as a single visual unit.

Example:

```text
        ┌─────────────────────────┐
        │ A  Adarsh            ×  │
        │    WhatsApp · 3          │
        │ I'll reach in 10 min...  │
        └─────────────────────────┘
           ┌───────────────────┐
              ┌─────────────┐
```

Rules:

- The newest thread is the readable front card.
- The front card shows the source-level thread count, e.g. `WhatsApp · 3`.
- Up to two older thread cards visibly peek behind it.
- Rear cards are slightly lower and narrower.
- Rear-card message content is hidden while collapsed.
- Stack depth represents **threads**, not individual messages.
- The exact source-thread count remains visible even when there are more threads than visible backing layers.

### Visible backing-layer cap

Maximum physical stack depth:

- front card
- back layer 1
- back layer 2

So the maximum visible stack depth is 3 physical card surfaces.

Examples:

- 2 threads → front + 1 backing layer
- 3 threads → front + 2 backing layers
- 8 threads → still front + 2 visible backing layers, but count says `· 8`

### Tap stack → expand

Tap anywhere appropriate on the readable front card.

The source expands **in place inside the same notification feed**.

Example:

```text
        ┌─────────────────────────┐
        │ A  Adarsh            ×  │
        │    WhatsApp · 3          │
        │ I'll reach in 10 min...  │
        └─────────────────────────┘

        ┌─────────────────────────┐
        │ V  Varsha            ×  │
        │    WhatsApp              │
        │ Pick something up...     │
        └─────────────────────────┘

        ┌─────────────────────────┐
        │ F  Family            ×  │
        │    WhatsApp              │
        │ Dinner is at 8...        │
        └─────────────────────────┘
```

This is **not navigation to another screen**.

There is still only one overall vertical notification feed.

### Expanded source → collapse

Tap the front card again.

The same cards physically move back underneath the newest/front card.

### One source expanded at a time

At most one source may be expanded.

Behavior:

- all sources start collapsed
- tap WhatsApp → WhatsApp expands
- tap WhatsApp again → WhatsApp collapses
- tap Software while WhatsApp is expanded → WhatsApp collapses and Software expands

This is presentation state only and never alters notification data.

### Thread-count behavior

- 0 threads → source absent
- 1 thread → normal card
- 2 threads → stack with one visible rear card
- 3 threads → stack with two visible rear cards
- 4+ threads → still only two visible backing depths; expand reveals all threads

---

## 3. STATE MODEL

Use one stored presentation-intent value:

```text
storedExpandedSourceKey: String?
```

Initial value:

```text
null
```

This lives in `OneRingNotificationHost`, not in `NotificationFeed` or `OneRingNotificationState`.

### Effective expansion

Rendering should not directly trust the stored key.

Derive:

```text
effectiveExpandedSourceKey =
    storedExpandedSourceKey
    only if the corresponding displayed source still exists
    and still has at least 2 active threads
```

This derivation must be pure.

The displayed source set should be the arrival-safe group snapshot already used by the host, so expansion state always matches what is actually being presented.

### Cleanup lifecycle

1. User taps source S.
2. Event callback updates `storedExpandedSourceKey`.
3. Rendering uses the pure effective value immediately.
4. If the stored key is stale because the source disappeared or dropped to one thread, rendering already behaves as collapsed/single-card on that frame.
5. A post-composition effect clears the stale stored key to prevent future accidental re-expansion.

This avoids state mutation during composition and prevents resurrection bugs.

### Toggle semantics

Conceptually:

```text
current == tapped  -> null
current != tapped  -> tapped
```

Expanding a new source therefore collapses the previously expanded source in the same interaction.

---

## 4. COMPONENT ARCHITECTURE

### Core rule: one stable LazyColumn item per source group

Every source occupies exactly one lazy item:

```text
"group:${sourceKey}"
```

This remains true whether the group is collapsed or expanded.

The lazy list should conceptually be:

```text
if group has 1 thread:
    NotificationCard(...)
else:
    NotificationGroupSlot(
        group = group,
        expanded = group.sourceKey == effectiveExpandedSourceKey
    )
```

There must **not** be separate mutually-exclusive `NotificationStack(...)` and `ExpandedNotificationGroup(...)` trees for the same multi-thread source.

The entire purpose of the architecture is to let the same thread-card nodes persist across:

```text
collapsed -> expanding -> expanded -> collapsing -> collapsed
```

### `NotificationGroupSlot`

New internal composable responsible for every multi-thread source.

Responsibilities:

- own all active thread-card children for that source
- keep those child identities stable across expand/collapse
- drive collapsed/expanded geometry
- control per-child clipping/visible height
- control content visibility
- control semantics/interaction enablement
- apply per-child circular-edge transformation
- expose tap-to-expand/collapse through the front card
- preserve normal X/swipe behavior where appropriate

### Persistent real thread cards

For a 3-thread source:

```text
child 0 = real Adarsh NotificationCard
child 1 = real Varsha NotificationCard
child 2 = real Family NotificationCard
```

Those same three nodes exist in both collapsed and expanded states.

#### Collapsed

Child 0:

- full width
- full height/content
- fully interactive
- stack count visible

Child 1:

- physically positioned behind child 0
- narrower
- only a backing strip visible
- internal content hidden
- semantics hidden
- not interactive

Child 2:

- physically positioned behind child 1
- narrower again
- only a backing strip visible
- internal content hidden
- semantics hidden
- not interactive

#### Expanding

Children 1+:

- move downward
- widen toward normal card width
- increase their visible/clipped height
- reveal content gradually
- remain non-interactive until expansion completes

#### Expanded

All children:

- normal card width
- full natural height
- full content
- individual semantics
- individual X dismissal
- individual swipe-left dismissal

The first/front card also carries the collapse action.

#### Collapsing

The same child nodes reverse back underneath the front card.

### Four or more threads

All active thread cards remain children of `NotificationGroupSlot`.

Example with 8 threads:

- child 0 = front position
- child 1 = visible backing depth 1
- child 2 = visible backing depth 2
- child 3+ = parked at/deeper than the final backing depth, fully clipped/hidden and semantically invisible

When expansion starts, all children receive unique expanded positions and become visible as they move into the expanded list.

This is intentionally preferred over composing only the first three while collapsed because:

1. it preserves node identity for the physical animation
2. it avoids visible pop-in when expanding
3. expanded state needs those cards anyway
4. expected source-thread counts are small
5. fidelity is more important than premature optimization here

Performance should still be measured during the animation spike.

### Decorative backing-card composable

A separate fake `NotificationStackBackCard` should **not** be the primary backing surface.

The real rear `NotificationCard` surfaces should provide the visible backing layers.

A decorative helper should only be introduced later if on-device visual testing proves that the real card surfaces cannot produce the desired visual treatment cleanly.

Avoid rendering both:

```text
fake back card
+
hidden real card
```

at the same depth.

### `NotificationCard` internal extensions

Allow small internal additions only.

Likely needs:

- optional source-stack metadata for `WhatsApp · 3`
- optional `onTap`
- internal content-visibility/progress control for rear cards
- internal interaction-enabled flag
- internal semantics-enabled flag if needed

These remain internal and defaulted so existing call sites retain current behavior.

Do not duplicate the card implementation.

### Pure presentation logic file

`NotificationStackPresentation.kt` remains Compose-free.

It should contain only:

- single-vs-stack decision
- front-thread selection
- visible backing depth count
- source-thread count
- expansion toggle helper
- effective-expansion predicate
- collapsed geometry math
- expanded geometry math
- interpolation math where useful

No composables belong in this file.

---

## 5. CUSTOM LAYOUT AND EDGE TRANSFORM

### One custom layout for the multi-thread group

`NotificationGroupSlot` should use one custom `Layout` for all real thread-card children.

The layout measures all children, then computes per-child placement from expansion progress.

For child `i`, interpolate:

```text
y
horizontal inset / width
visible height / clipping
content alpha
```

between:

```text
collapsedGeometry(i)
```

and:

```text
expandedGeometry(i)
```

The front child remains anchored.

### Collapsed geometry

Conceptually:

```text
thread 0:
    y = 0
    width = full
    visibleHeight = full card height

thread 1:
    y = stack offset 1
    width = reduced
    visibleHeight = peek strip

thread 2:
    y = stack offset 2
    width = reduced further
    visibleHeight = peek strip

thread 3+:
    y = at/deeper than final visible depth
    width = final/reduced backing width
    visibleHeight = 0
```

The reported collapsed group height includes the front card plus only the visible backing peek depth.

### Expanded geometry

Each thread gets its own full position:

```text
expandedY(i) =
    sum(previous full card heights + card spacing)
```

All widths return to normal.

All cards use natural content-driven height.

No card becomes an independently scrolling surface.

### Circular-edge transform

The current one-item transform is not sufficient for an expanded multi-card group.

The custom layout should apply the transform per child during placement.

Use the group's lazy-item viewport offset plus each child's placement offset to derive each child's center in the viewport.

Then reuse the existing pure:

```text
circleEdgeTransform(...)
```

logic.

#### Collapsed

All visible stack children use the same effective group center.

The entire collapsed stack narrows/fades as one physical object.

#### Expanded

Each full card uses its own center.

Cards near the circular top/bottom edge narrow/fade independently, matching the existing flat-feed behavior.

#### During transition

Interpolate between group-center treatment and child-center treatment using the same expansion progress so there is no visual pop when the stack opens or closes.

No layout-state feedback loops or writes during placement.

---

## 6. GESTURE ARBITRATION

### Tap whole front card

Desired behavior:

```text
tap front card       -> expand/collapse
left swipe           -> dismiss front thread
tap X                -> dismiss front thread
vertical drag        -> feed scroll
```

Do not add a second competing tap detector.

The existing swipe loop already resolves gesture direction, so integrate tap detection into that same gesture lifecycle.

A gesture counts as a tap only when:

- it never locked horizontal
- it never settled into vertical scrolling
- it stayed within touch slop
- it ended within an appropriate tap-duration bound

### X button

The X remains an independent child action.

It must dismiss the thread and must never expand/collapse the source.

Verify the existing parent/child pointer consumption assumptions during implementation.

### Rear cards while collapsed

Rear thread cards must not be interactive.

No:

- X
- swipe
- tap
- accessibility actions

### During transition

Safest behavior:

- only front card interactive while collapsed
- while expand/collapse animation is in progress, secondary cards remain non-interactive
- once fully expanded, all cards become normally interactive
- during collapse, disable secondary-card interaction as soon as the collapse begins

This prevents accidental dismissals while cards are moving.

### Expanded state

- front card tap → collapse
- front card X/swipe → dismiss front thread
- non-front card tap → no collapse action
- non-front card X/swipe → dismiss that individual thread

---

## 7. ANIMATION PLAN

### Required visual behavior

Collapsed → expanded:

- front card remains visually anchored
- real rear thread cards physically peel downward
- they widen toward full card width
- their visible height grows
- their content fades in
- surrounding source groups smoothly move downward

Expanded → collapsed:

- real secondary cards travel back underneath the front
- their content fades out
- they narrow into backing layers
- their visible height shrinks to peek strips or zero
- surrounding groups close the space smoothly

### Explicitly unacceptable

Do not implement:

```text
collapsed subtree fades out
expanded subtree fades in
```

This would defeat the persistent-node design.

### Animation primitive

The plan should not hard-lock to a primitive before visual validation.

Leading candidate:

- one `updateTransition` for each multi-thread group
- one expansion-progress value
- custom `Layout` consumes that progress
- child geometry and content visibility derive from it

Why it fits:

- persistent nodes
- one source of truth
- declarative
- appropriate for tap-driven open/close
- custom layout already controls geometry

Alternatives:

- `Animatable` if interruptibility/velocity becomes necessary
- custom lower-level control if a specific effect cannot be achieved cleanly

`AnimatedContent` is not the default choice because its natural model swaps subtrees.

`animateContentSize` is not sufficient by itself.

### Animation spike gate

Before final animation implementation, build an on-device spike for one multi-thread source.

Judge:

- does the front card feel anchored?
- do rear cards visibly peel rather than appear?
- does collapse feel like the exact reverse?
- does the circular-edge transform stay coherent?
- is frame timing acceptable?

Do not proceed to final motion implementation until the spike passes visual review.

### Reduced motion

When animations are disabled:

- use the same persistent architecture
- expansion progress jumps directly to collapsed/expanded target
- semantics and interaction switch correctly
- no alternate UI tree is used

---

## 8. DISMISS / UPDATE BEHAVIOR

### Collapsed front dismissal

Dismiss only the visible front thread.

After dismissal:

- next-newest thread becomes front
- source remains a stack if 2+ threads remain
- 2 → 1 becomes a normal single card
- 1 → 0 removes the source

### Expanded dismissal

Each full card retains normal X/swipe dismissal.

Removing one thread only removes that thread.

If the source drops to one active thread:

- effective expansion becomes null immediately
- it renders as a normal single card
- stored expanded intent is cleaned up afterwards

### Same-thread update

Same `threadKey`:

- updates that existing thread
- does not create another stack layer
- stack depth unchanged
- thread moves/becomes newest according to current existing ordering behavior

### New thread while collapsed

After the existing arrival splash:

- new thread becomes front
- previous front moves into rear depth
- no existing thread disappears
- count updates

### New notification in currently expanded source

- keep that source expanded
- reorder threads newest-first
- newest card moves to top
- no automatic collapse

### New notification in another source

- receiving source reorders according to normal source recency
- receiving source stays collapsed
- current expanded source remains expanded
- incoming notification does not steal the user's expansion state

### Source disappears

If the expanded source disappears entirely:

- effective expanded source becomes null immediately
- stale stored intent is cleared after composition

---

## 9. STYLE / GEOMETRY

Proposed additive `OneRingNotificationDimensions` fields:

- `stackBackLayerCount: Int = 2`
- `stackBackLayerOffset: Dp = 8.dp`
- `stackBackLayerWidthReduction: Float = 0.06f`
- `stackBackLayerVisibleHeight: Dp = 10.dp`

Validation:

- visible back-layer count constrained to supported range
- width reduction must remain sensible
- dimensions non-negative

Do not expose every animation/internal number publicly.

Private implementation details should include:

- transition curves/spec construction
- stack count typography details
- backing dim amount
- intermediate animation thresholds
- semantics/interaction threshold if one is required

Do not add a new motion-style field unless on-device iteration proves existing timing is unsuitable.

---

## 10. ACCESSIBILITY PLAN

### Collapsed stack

Expose one meaningful stack/front-card node.

Conceptual description:

```text
WhatsApp, 3 notifications. Adarsh: I'll reach in 10 minutes.
```

Primary accessibility click:

```text
Expand
```

Front-thread dismiss remains available as a dismiss/custom action.

Rear cards:

- no readable semantics
- no dismiss action
- no click
- no separate focus nodes

### Expanded

All real thread cards become individually accessible.

Front card:

- source count retained
- primary action = Collapse
- own dismiss action retained

Non-front cards:

- normal notification semantics
- normal dismiss action
- no collapse click

### During transition

Rear cards remain hidden from accessibility until the expanded state is complete.

During collapse, hide secondary-card semantics as soon as collapse begins.

This avoids TalkBack focus moving onto visually moving/partially clipped cards.

### Reduced motion

Same semantic state changes, just without intermediate animation frames.

---

## 11. TEST PLAN

### Unit / pure tests

New `NotificationStackPresentationTest.kt` should cover:

- 1 thread → single card
- 2 threads → stack with 1 visible rear depth
- 3 threads → stack with 2 visible rear depths
- 8 threads → still only 2 visible backing depths
- source count reports exact thread count
- source thread count remains distinct from per-thread message count
- newest thread is always front
- front removal promotes next-newest
- 2 → 1 changes stack → single
- same-thread update does not increase stack depth
- new thread does increase thread count
- expansion toggle semantics
- effective expansion for missing/1-thread/multi-thread groups
- collapsed geometry for all child indices
- 4+ hidden-depth geometry
- expanded cumulative offsets
- midpoint interpolation

### Compose tests — collapsed

For a 3-thread source:

- newest thread is readable
- exactly two rear surfaces are visibly represented
- rear thread message content is absent from semantics
- rear X actions unavailable
- rear swipe unavailable
- source count says 3

### Compose tests — expand

- tap front card
- same source expands
- all three existing thread cards become readable
- notification data unchanged
- rear cards gain normal dismiss behavior only after fully expanded

### Compose tests — collapse

- tap front card again
- same cards return to collapsed geometry
- rear semantics disappear again
- newest remains front
- count remains correct

### One-expanded-source rule

- all sources start collapsed
- expand WhatsApp
- collapse WhatsApp by tapping it again
- expand WhatsApp then Software
- WhatsApp collapses and Software expands
- notification data remains unchanged by presentation toggles

### Gesture arbitration

- tap front body → expand
- left swipe front → dismiss only front thread and do not expand
- tap X → dismiss only front thread and do not expand
- vertical drag → scroll and do not expand
- rear collapsed card interaction does nothing

### Expanded dismissal

- dismiss one non-front expanded card
- only that thread disappears
- others remain
- if source falls to one thread, it becomes a plain card and stale expanded intent is cleaned up

### 8-thread persistence behavior

- collapsed source shows only 2 visible backing depths
- source count says 8
- expand reveals all 8 threads
- no notification data changes
- no visible semantic pop from newly-created card nodes because all cards were already part of the persistent group slot
- collapse returns all secondary cards to hidden/deeper states

### New arrivals

- new thread in collapsed source becomes front after splash
- same-thread update does not increase stack depth
- new notification in expanded source keeps it expanded and reorders cards
- new notification in another source leaves current expanded source expanded

### Transition safety

While expansion/collapse animation is in progress:

- secondary cards cannot dismiss
- secondary cards cannot trigger actions
- X/swipe only become active when expanded state is complete
- accessibility does not expose partially-open cards

### Circular-edge transform

Collapsed:

- front + visible rear layers share one effective transform

Expanded:

- top and bottom cards in a tall group receive different scale/alpha based on their own viewport positions

Transition:

- transform interpolation does not pop between group-center and child-center treatment

### Scrolling

- mixed single-card and stacked sources remain reachable in the one `LazyColumn`
- expanded source remains part of the same scroll feed
- no nested scroll surface is introduced

### Ring integration / connected tests

- stack tap does not leak into ring interaction
- expanded source survives submenu hide/restore
- expanded source survives Seek hide/restore
- existing arrival-ordering tests remain green
- notification overlay/ring gesture behavior remains unchanged

---

## 12. FILE-BY-FILE CHANGE PLAN

### Modified

#### `NotificationFeedList.kt`

- keep one `LazyColumn`
- one item per source keyed `"group:${sourceKey}"`
- single thread → normal `NotificationCard`
- multi-thread → persistent `NotificationGroupSlot`
- pass effective expanded key / toggle callback
- preserve interactive and non-interactive branches
- group reordering animation may use item-placement animation if appropriate

#### `OneRingNotificationHost.kt`

- own `storedExpandedSourceKey`
- derive `effectiveExpandedSourceKey`
- cleanup stale stored intent in a post-composition effect
- pass expansion state/toggle callback to feed
- leave arrival safe-groups/on-reveal flow unchanged

#### `NotificationCard.kt`

Small internal, defaulted extensions only:

- source-stack metadata
- optional tap callback
- content visibility/progress control where needed
- interaction enabled/disabled
- semantics enabled/disabled if needed

Existing normal-card behavior remains the default.

#### `OneRingNotificationStyle.kt`

- add stack dimension fields and validation

#### `OneRingNotificationDefaults.kt`

- add matching defaults

#### `docs/notifications.md`

- document stacked grouping behavior after implementation

### Added

#### `NotificationGroupSlot.kt`

Persistent multi-thread UI.

Owns:

- all real thread-card children
- custom layout
- collapsed/expanded geometry
- progress-driven placement
- clipping/visible-height behavior
- per-child edge transform
- interaction enablement
- semantics enablement
- front-card tap-to-toggle

#### `NotificationStackPresentation.kt`

Pure Compose-free logic only:

- stack decision
- visible backing count
- source count
- front selection
- toggle helper
- effective expansion predicate
- collapsed/expanded geometry math
- interpolation helpers

#### `NotificationStackPresentationTest.kt`

Pure JVM tests.

#### `NotificationStackComposeTest.kt`

Compose/UI behavior tests.

### No longer planned as primary components

Do **not** create separate mutually-exclusive UI trees:

- `NotificationStack.kt`
- `ExpandedNotificationGroup.kt`

Do **not** introduce a fake `NotificationStackBackCard` as the normal backing-layer mechanism.

If small internal helpers later make `NotificationGroupSlot.kt` easier to read, they are fine, but the persistent group slot remains the single UI tree.

### Unchanged domain/public-model files

- `NotificationFeed.kt`
- `OneRingNotification.kt`
- `OneRingNotificationPost.kt`
- `OneRingNotificationState.kt`
- `NotificationArrivalOverlay.kt`
- `CircleEdgeTransform.kt`
- `NotificationVisibility.kt`
- `ArrivalGate.kt`
- `OneRing.kt`
- `OneRingCenterScope.kt`

---

## 13. PUBLIC API IMPACT

No changes required to:

- `OneRingNotificationState`
- `OneRingNotificationPost`
- `OneRingNotificationHost`
- `OneRingNotification`
- `OneRingNotificationGroup`

Existing consumers should receive the new stack presentation automatically.

`NotificationCard` additions remain internal.

Only additive public growth is expected in notification style/dimensions.

No notification-domain API changes are required.

---

## 14. RISKS / OPEN QUESTIONS

### 1. Animation fidelity

Highest risk.

The physical stack must read as the same cards opening, not as a visual swap.

Mitigation:

- persistent nodes
- one group slot
- animation spike gate
- on-device review before final motion implementation

### 2. Composing all threads inside one lazy group item

This intentionally gives up per-thread lazy virtualization within a source.

Expected impact is acceptable because:

- lazy virtualization still exists at the source-group level
- expected source thread counts are small
- the expanded state needs every thread anyway
- persistent physical expansion is the primary requirement

Still measure frame timing with a larger sample group during the animation spike.

If a pathological source contains a very large number of threads, revisit only after measuring an actual problem.

### 3. Measuring all child cards during animation

Custom `Layout` may measure several content-driven cards every frame.

Validate performance on-device with 3, 8, and a deliberately larger test group.

### 4. Per-child edge transform

New implementation pattern.

Validate early before motion complexity is layered on top.

### 5. Clipping real rear cards

Need to ensure:

- rear card surface remains visually correct when clipped
- shadows/borders do not create artifacts
- hidden content does not leak
- X is not partially visible
- shape still reads as a real backing card

This should be part of the static stack visual checkpoint.

### 6. Gesture arbitration

Verify:

- X does not expand
- left swipe does not expand on release
- vertical drag does not expand
- slow/sloppy vertical gestures do not become accidental taps

### 7. Semantics during animation

Persistent nodes make it especially important to hide semantics independently from composition.

Do not rely on alpha alone.

### 8. Thread reordering while expanded

A new message can change child order.

The implementation should preserve stable thread keys and avoid obvious identity jumps where possible.

This deserves a visual check during update testing.

---

## 15. IMPLEMENTATION ORDER

### Phase 1 — pure presentation logic

Implement only:

- stack decision
- visible-depth count
- toggle helper
- effective expansion predicate
- collapsed/expanded geometry math
- unit tests

Checkpoint:

- JVM tests green
- no UI behavior changed

### Phase 2 — persistent static collapsed group

Add:

- `NotificationGroupSlot`
- all real thread cards as persistent children
- collapsed geometry
- two visible backing depths
- 4+ threads hidden at/deeper than final depth
- source count
- rear content/semantics/interaction suppression
- one source-group lazy item
- per-child edge-transform infrastructure

No expand gesture yet.

Checkpoint:

- unit tests green
- connected suite green
- on-device visual check:
  - 2 threads
  - 3 threads
  - 8 threads
  - edge scrolling
  - no rear-content leakage

### Phase 3 — instant expand/collapse, persistent nodes

Add:

- front-card tap-to-toggle
- one-expanded-source rule
- stored/effective expansion state
- cleanup effect
- expanded geometry
- rear cards become fully visible/interactive only at expanded target
- no animation yet

Important:

Do **not** swap to a different expanded UI tree.

The same `NotificationGroupSlot` and same thread-card nodes remain.

Checkpoint:

- full Compose tests
- connected tests green
- verify X/swipe/tap/vertical-scroll arbitration
- verify 8-thread expand reveals all cards without pop

### Phase 4a — animation spike gate

Prototype:

- `updateTransition`
- one expansion-progress value
- custom layout interpolation
- content alpha
- clipping
- per-child edge-transform interpolation

Test on physical device.

Judge:

- anchored front card
- real peel effect
- smooth reverse collapse
- no clipping artifacts
- acceptable frame timing for 3 and 8 threads
- coherent circular-edge behavior

Do not proceed until visually approved.

### Phase 4b — production animation

Implement the validated animation strategy.

Add reduced-motion snapping.

Add any source-group placement/reordering animation that is still needed.

Checkpoint:

- manual device review
- unit tests
- Compose tests
- connected tests

### Phase 5 — edge cases and polish

- collapsed depth reflow after dismissal
- new-arrival reorder behavior
- semantics/accessibility pass
- submenu/Seek hide-restore tests
- larger-thread-count performance check
- docs
- lint
- `git diff --check`

Final checkpoint:

- unit suite green
- library connected suite green
- sample connected suite green
- lint clean
- sample APK builds
- no unrelated changes

---

## 16. RECOMMENDED PLAN

Implement the feature with these final architectural decisions:

1. **Use existing `sourceKey` grouping and notification-domain models.**
2. **Exactly one source may be expanded at a time.**
3. **Every source is exactly one stable `LazyColumn` item.**
4. **Every multi-thread source uses one persistent `NotificationGroupSlot` in both collapsed and expanded states.**
5. **All active thread `NotificationCard` nodes remain children of that group slot.**
6. **The rear stack layers are the real older notification cards, not fake decorative replacements.**
7. **Collapsed view exposes at most two rear depths, even when more threads exist.**
8. **Threads beyond the second backing depth remain composed but fully hidden/clipped/non-semantic while collapsed.**
9. **Expansion physically moves those same cards into their full list positions.**
10. **Collapse reverses those same nodes back into backing layers.**
11. **Only the front card is interactive while collapsed or transitioning; all cards are interactive only when fully expanded.**
12. **Tap the readable front card to expand/collapse; left swipe and X continue to dismiss one thread only.**
13. **Use a custom `Layout` for persistent child geometry and per-child circular-edge treatment.**
14. **Keep `NotificationStackPresentation.kt` genuinely Compose-free.**
15. **Do not use mutually-exclusive collapsed/expanded UI subtrees.**
16. **Do not use fake backing-card layers as the normal implementation.**
17. **Keep arrival splash, splash-before-message ordering, submenu/Seek behavior, theming, scroll model, and notification APIs unchanged.**
18. **Animation is validated visually before final implementation; `updateTransition` + custom layout is the leading approach, not a hard requirement.**
19. **Measure performance rather than prematurely sacrificing persistent-card fidelity.**
20. **Do not commit or push until the complete implementation and test results have been reviewed.**

This document is the architecture/feature plan. The implementation agent should inspect the current repository against it before producing an implementation plan, and should report any concrete incompatibility before changing the architecture.
