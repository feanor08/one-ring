# One Ring Notification Stack — Canonical Implementation Plan

## 1. Purpose and status

This is the canonical implementation plan for the Apple-style notification
stack in One Ring. It records the approved product behavior, the architecture
validated against the current repository, implementation phases, test gates,
and repository-safety constraints.

This document is an implementation specification, not evidence that the
feature has already been implemented. Production and test code must not be
changed until implementation is explicitly requested.

The implementation must preserve the current notification-domain behavior,
arrival ordering, One Ring gesture integration, and all pre-existing
user-owned worktree changes.

---

## 2. Validated repository baseline

### Existing notification domain

The following behavior already exists and remains authoritative:

- `NotificationFeed.groupedBySource()` groups active threads by `sourceKey`.
- `OneRingNotificationGroup` represents one source and its active threads.
- Source groups are ordered by their newest thread's monotonic `sequence`.
- Threads within a source are ordered newest-updated first.
- Posting the same `threadKey` appends a message to the existing thread,
  refreshes its identity fields, advances its sequence, and moves its source
  group to the front.
- Dismissal removes one complete thread by `threadKey`.
- Removing the final thread removes the source group automatically.
- `OneRingNotificationState` owns notification data independently of
  `OneRingState` navigation state.

These domain files do not require redesign:

- `OneRingNotification.kt`
- `OneRingNotificationPost.kt`
- `OneRingNotificationState.kt`
- `NotificationFeed.kt`

No expansion state belongs in these files.

### Existing presentation and integration

The current presentation is implemented by:

- `OneRingNotificationHost.kt`
- `NotificationFeedList.kt`
- `NotificationCard.kt`
- `NotificationArrivalOverlay.kt`
- `CircleEdgeTransform.kt`
- `NotificationVisibility.kt`
- `ArrivalGate.kt`
- `OneRingNotificationStyle.kt`
- `OneRingNotificationDefaults.kt`

The current interactive feed uses one `LazyColumn`, but currently emits one
lazy item per thread. When interaction is disabled, `NotificationFeedList`
uses a non-scrollable `Column` instead of the `LazyColumn`. This branch swap is
intentional: merely setting `userScrollEnabled = false` leaves a scrollable
node that can still win hit testing over submenu and Seek gestures.

The feature must preserve that interactivity gate. Both branches must use the
same source-rendering rules, but Compose node identity is not guaranteed across
the existing `LazyColumn`/`Column` replacement while the feed is hidden or
covered. The required persistent-node guarantee applies to a continuously
composed multi-thread `NotificationGroupSlot` across collapsed and expanded
presentation states.

### Existing arrival behavior

`OneRingNotificationHost` deliberately renders an arrival-safe `safeGroups`
snapshot rather than blindly rendering live groups:

1. A post updates notification-domain state immediately.
2. Synchronous `arrivalPending` logic prevents the new data from entering the
   displayed snapshot before the arrival effect starts.
3. The old `safeGroups` remain visible while the splash grows and holds.
4. `NotificationArrivalOverlay.onReveal` updates `safeGroups` only while the
   splash is fully opaque.
5. The exit fade reveals the already-updated feed.
6. The sequence watermark prevents a notification posted while hidden from
   replaying a stale arrival later.

This splash-before-message architecture must not be redesigned.

### Existing gesture and edge behavior

- `NotificationCard` uses one low-level `awaitEachGesture` loop for directional
  swipe locking.
- A leftward horizontal gesture beyond the dismiss threshold removes one
  thread.
- Vertical movement is left unconsumed so the feed can scroll.
- The X is a separate child action that removes one thread.
- `circleEdgeTransform(...)` is a pure scale/alpha calculation.
- The current adapter applies it at lazy-item level through a deferred layer
  read.
- One Ring's `RingInteractionController` cedes touches inside the active
  `coreOverlay` while the top-level ring is settled.
- The notification host removes its interactive scroll/card handlers while a
  submenu, Seek, or arrival owns the surface.

### Accessibility issue found in the current repository

`NotificationCard` currently exposes its custom accessibility dismiss action
even when pointer interaction is disabled. This conflicts with:

- the arrival-safe snapshot's no-mutation requirement while covered;
- collapsed rear-card interaction suppression; and
- transition interaction suppression.

The approved implementation fix is internal:

- condition the accessibility dismiss action on effective interaction state;
- add explicit semantics enable/disable support;
- expose no semantics for collapsed or transitioning rear cards; and
- ensure arrival-covered/non-interactive cards cannot mutate notification data
  through accessibility actions.

No public API change is required.

### Compose and Gradle baseline

- Compose BOM: `2025.09.00`
- Resolved Compose UI/Foundation version: `1.9.1`
- Kotlin: `2.2.20`
- Android Gradle Plugin: `9.1.1`
- Minimum SDK: 24
- Compile/target SDK: 37

Compose 1.9.1 provides the APIs required by this plan, including `Layout`,
`placeRelativeWithLayer`, `GraphicsLayerScope.clip`, layer `shape`,
`clipToBounds`, and `zIndex`.

### Known repository and test baseline

At inspection time:

- the branch was `main`;
- it was two commits ahead of and two commits behind `origin/main`;
- `NotificationCard.kt` contained a user-owned change making the dismiss glyph
  7 dp rather than 14 dp;
- untracked repository files already existed, including `.idea/` and
  `docs/notifications_plan.md`;
- the selected existing notification/core-overlay connected tests passed on
  the USB-connected Pixel 6a; and
- the JVM suite had one unrelated pre-existing failure:
  `ResolvedRingTest` expected the obsolete `coreOverlayRadius` value `0.43`,
  while the current default is `0.54`.

The notification-stack implementation must preserve the 7 dp dismiss glyph
and all other pre-existing changes. It must not fix or absorb ownership of the
unrelated `ResolvedRingTest` failure unless explicitly requested.

---

## 3. Final product behavior

### Grouping and ordinary cards

1. Notifications continue to be grouped by the existing `sourceKey`.
2. Domain grouping, ordering, merging, and dismissal remain unchanged.
3. A source with one active thread renders a normal `NotificationCard`.
4. A source with two or more active threads renders a collapsed Apple-style
   stack by default.

### Collapsed stack

- The newest thread is the readable front card.
- At most two older thread depths are visibly represented.
- Rear layers are the real older `NotificationCard` nodes, not decorative
  substitutes.
- Rear cards sit lower and are slightly narrower.
- Only a bottom backing strip of each visible rear card is exposed.
- Rear notification content is hidden.
- Rear semantics are hidden.
- Rear input, X, swipe, and click behavior are disabled.
- Stack depth represents threads, never individual messages.

For 4+ threads, all active card nodes remain children of the slot. Only the
first two rear depths are visible; child 3+ is fully clipped, non-semantic, and
non-interactive while collapsed.

### Expansion and collapse

- Tapping the front collapsed card expands that source in place.
- Tapping the expanded front card collapses that source.
- At most one source may be expanded.
- Tapping a different source collapses the current source and expands the
  tapped source in the same state update.
- Expansion stays in the same vertical notification feed.
- There is no new screen, horizontal pager, or nested `LazyColumn`.

When fully expanded:

- every thread is rendered at full size and normal width;
- all notification content is visible;
- every card has normal semantics;
- every card has its individual X and swipe-left dismissal;
- only the front card has the collapse tap action; and
- non-front taps do not collapse the source.

### Dismissal and cardinality transitions

Dismissal always removes one thread only.

- Front dismissal promotes the next-newest thread.
- `3 -> 2` remains a stack.
- `2 -> 1` becomes a normal `NotificationCard`.
- `1 -> 0` removes the source.
- Expanded non-front dismissal removes only that thread.
- If an expanded source shrinks to one thread, effective expansion ends
  immediately and stale stored intent is cleared after composition.

---

## 4. Source count and thread subtitle are separate

The source-level active-thread count is additive stack metadata. It must not
replace, rewrite, or suppress the existing thread subtitle produced by
`displaySubtitle()`.

These are independent facts:

- `WhatsApp · 3` means three active threads/conversations in the source.
- `WhatsApp · 5 messages` means five messages in the front Adarsh thread.

A front stack card may communicate them conceptually as:

```text
Adarsh                         WhatsApp · 3
WhatsApp · 5 messages
I'll reach in 10 minutes.
```

Exact visual placement is refined at the static stack/device checkpoint, but
the architecture is fixed:

- `displaySubtitle()` remains intact;
- source count is separate internal stack metadata;
- source count comes from `group.threads.size`;
- thread message count comes from `notification.messages.size`; and
- neither value may be derived from or substituted for the other.

Tests must include a three-thread source whose front thread contains five
messages and prove that both facts remain visible and accessible.

---

## 5. Final component architecture

### One stable source item

The interactive feed emits exactly one lazy item per source, keyed:

```text
"group:${sourceKey}"
```

Within that item:

```text
thread count == 1  -> NotificationCard
thread count >= 2  -> NotificationGroupSlot
```

The hidden non-interactive `Column` branch uses the same source renderer and
the same single-vs-group decision, without introducing a scrollable node.

### One persistent multi-thread tree

`NotificationGroupSlot` is the only multi-thread UI tree. It serves collapsed,
expanding, expanded, and collapsing states.

Do not create mutually exclusive:

- `NotificationStack(...)`
- `ExpandedNotificationGroup(...)`

Do not use `NotificationStackBackCard` or another fake decorative card as the
normal backing implementation.

### Thread-card identity

Inside `NotificationGroupSlot`, wrap each entire `NotificationCard` subtree in:

```text
key(thread.threadKey)
```

The keyed scope must contain all remembered card/swipe state. Changing
expansion changes geometry and presentation values only; it must not select a
different composable tree.

The same nodes survive:

```text
collapsed
-> expanding
-> expanded
-> collapsing
-> collapsed
```

Insertion creates only the new keyed card. Dismissal disposes only the removed
card. Reordering changes keyed cards' indices and geometry without assigning
one thread's remembered state to another.

The approved one-card behavior means the surviving card is reconstructed when
a source crosses from two threads to one. Likewise, the repository's existing
interactive/non-interactive feed branch can reconstruct a source tree while
the feed is hidden or covered. Neither boundary permits mutually exclusive
collapsed and expanded trees inside `NotificationGroupSlot`.

---

## 6. Persistent rear-card behavior

For a group ordered:

```text
Adarsh
Varsha
Family
```

the real children are:

```text
child 0 = Adarsh NotificationCard
child 1 = Varsha NotificationCard
child 2 = Family NotificationCard
```

Collapsed:

- Adarsh is full, readable, semantic, and interactive.
- Varsha is the real card at rear depth 1, narrower and bottom-strip clipped;
  its content, semantics, and input are disabled.
- Family is the real card at rear depth 2 with the same restrictions.

Expanded:

- the same Varsha and Family nodes move to full positions;
- the same cards widen to normal width;
- their clips open to full natural height;
- their content becomes visible; and
- they become individually semantic and interactive only at the final
  expanded endpoint.

Collapse reverses those same nodes back under the front card.

### Four or more threads

Compose and measure all active thread cards inside the slot.

- Child 0: front.
- Child 1: visible rear depth 1.
- Child 2: visible rear depth 2.
- Child 3+: parked at/deeper than the deepest rear position, with zero visible
  clip height, semantics, and interaction.

Expanded state gives every child a unique full position. This preserves
physical continuity and prevents expansion pop-in. Performance is measured
with 8- and 20-thread groups before considering any architectural
optimization.

---

## 7. State model

### Stored intent

`OneRingNotificationHost` owns one presentation value:

```text
storedExpandedSourceKey: String?
```

It starts as null and never enters `NotificationFeed`,
`OneRingNotificationState`, or another public/domain model.

### Effective expansion

Rendering derives a pure:

```text
effectiveExpandedSourceKey
```

The stored source is effective only when:

1. it exists in the currently displayed arrival-safe groups; and
2. it has at least two active threads.

Otherwise effective expansion is null on that frame.

Eligibility must use `displayedGroups`, not live `state.groups` while an
arrival is pending. Expansion must always describe what the user is actually
being shown.

### Toggle and cleanup

- Tapping the stored/effective source sets stored intent to null.
- Tapping another eligible source stores that source key.
- Incoming notifications never steal or overwrite expansion intent.
- Derivation must not mutate state during composition.
- If stored intent becomes stale, rendering first behaves correctly from the
  effective value, then a post-composition effect clears the stored key.

Clearing stale intent prevents a source that shrank to one thread from
spontaneously re-expanding if it later gains another thread.

### Hide and restore

Stored intent remains host-owned while submenu/Seek visibility hides the feed.
Returning to the top-level ring restores the eligible source at its stored
endpoint. A source that disappeared or shrank while hidden restores as
collapsed/single and cleans stale intent.

---

## 8. Arrival-safe update behavior

Do not redesign `safeGroups`, `onReveal`, the watermark, or splash ordering.

- Expansion eligibility is derived from displayed/safe groups.
- A new thread in the expanded source keeps that source expanded.
- Existing newest-first ordering moves the new/updated thread to the front.
- A notification in another source does not collapse or replace the current
  expanded source.
- The receiving source remains collapsed unless it was already the expanded
  source.
- Existing source-recency ordering may move the receiving source item.
- Thread reorder does not receive a separate animation in this iteration.
- With motion enabled, the opaque splash covers the index/order change.
- With reduced motion, the keyed cards reorder instantaneously.
- Source thread count and changed message content must not appear before the
  existing splash reveal point.

The current synchronous `arrivalPending` gate must remain so the feature never
regresses to card-update -> splash -> card-update ordering.

---

## 9. NotificationGroupSlot custom layout

### Measurement

Use one custom Compose `Layout` for all real cards.

- Measure all cards at the same stable full-width constraints.
- Preserve the current minimum-height floor.
- Preserve every card's natural content-driven height.
- Do not assume equal heights.
- Do not introduce a nested scroll surface.

Expanded positions use cumulative natural heights:

```text
expandedY(0) = 0
expandedY(i) = sum(previous card heights + card spacing)
```

### Unequal-height collapsed geometry

Use bottom alignment for rear cards:

```text
collapsedBottom(depth) =
    frontHeight + depth * backLayerOffset

collapsedY =
    collapsedBottom - childNaturalHeight
```

This produces consistent visible backing depths even when older cards are
taller or shorter than the front.

Child 3+ uses the deepest supported parked position but zero collapsed clip
height.

### Group height

- Collapsed height is front height plus one offset per visible rear depth.
- Expanded height is the sum of all natural card heights plus inter-card
  spacing.
- Transition height interpolates between those endpoints.
- The slot top remains fixed so the front card stays anchored.
- Following source items move as the slot reports changing height.

### Drawing order

Compose cards newest-first for traversal, then use descending `zIndex` so the
front surface draws above rear depths and nearer rear depths draw above deeper
cards.

---

## 10. Width, clipping, and content visibility

### Width handling

Measure all cards at fixed full width. Do not animate measurement constraints
or text width.

Rear narrowing uses centered layer `scaleX`:

- collapsed rear scale reflects its depth;
- expanding scale interpolates toward 1;
- expanded scale is 1; and
- effective scale combines stack-width scale with circular-edge scale.

This avoids text reflow, changing natural height, and unstable geometry.

### Layer clipping

Use the Compose 1.9.1-compatible placement layer:

- place children with `placeRelativeWithLayer`;
- set layer clipping with `GraphicsLayerScope.clip = true`;
- provide a dynamic rectangular layer shape for the visible portion; and
- use `clipToBounds()` on the overall slot as a safety boundary.

Collapsed visible rear cards expose only their bottom backing strip. Child 3+
has zero visible clip height. During expansion the clip's top edge opens toward
the full card.

The implementation must prevent:

- X leakage;
- icon, title, subtitle, message, or timestamp leakage;
- partial hidden actions;
- doubled/stray borders; and
- damaged rounded-corner treatment.

Current cards have no shadow elevation. If a future shadow is introduced, its
interaction with the child and slot clips requires separate visual review.

### Content visibility

Internally structure `NotificationCard` conceptually as:

```text
persistent outer card surface
+
inner notification content
```

The outer surface contains the card background, rounded surface, and accent
border. The inner content contains icon, title, existing subtitle, messages,
timestamp, source-count metadata, and X.

Use an internal presentation/content progress:

- front content remains fully visible;
- collapsed rear content is fully hidden;
- expanding rear content reveals after enough card surface is visible;
- expanded rear content is fully visible; and
- collapsing rear content begins fading immediately.

Do not duplicate `NotificationCard`, replace it with a separate rear-card
component, or expose these presentation controls publicly.

---

## 11. Interaction model

### One gesture loop

Do not add a second body tap detector. Extend the existing low-level card
swipe loop into a unified state machine.

Conceptual states:

- `Pending`
- `HorizontalSwipe`
- `VerticalHandedOff`

Track initial position, maximum displacement, elapsed time, accumulated X/Y,
descendant consumption, cancellation, and pointer count.

Required resolution:

- A front-card tap inside touch slop and the tap-duration bound toggles the
  source.
- A leftward horizontal gesture that wins directional locking can settle or
  dismiss, but never toggle.
- A vertical gesture beyond slop hands off unconsumed to feed scrolling and
  never toggles.
- A resolved rightward or diagonal drag is not a tap.
- A canceled or multi-pointer gesture is not a tap.
- The X's consumed child sequence makes the parent tap ineligible.

### Interaction by state

Collapsed front:

- tap expands;
- swipe-left dismisses that thread;
- X dismisses that thread; and
- normal stack/front semantics are exposed.

Collapsed rear:

- no pointer input;
- no X action;
- no swipe;
- no click; and
- no semantics.

Expanding:

- only the front remains interactive;
- secondary cards remain non-semantic and non-interactive.

Fully expanded:

- front tap collapses;
- front X/swipe dismisses the front thread;
- non-front X/swipe dismisses each corresponding thread;
- all cards expose normal semantics; and
- non-front cards have no collapse tap.

Collapsing:

- disable secondary semantics and interaction as soon as the collapse target
  is selected.

Do not enable a secondary card merely because its alpha is high. Enable it only
when expansion is at its final endpoint and the transition is not running.

---

## 12. Accessibility

### Collapsed

Expose one meaningful front stack node that independently communicates:

- source identity;
- active source-thread count;
- front thread identity;
- the unchanged per-thread subtitle/message count; and
- front thread message content.

Its primary action is `Expand`. Its custom dismiss action removes the front
thread. Rear cards expose no readable or actionable semantics.

### Expanded

- Front primary action is `Collapse`.
- Front retains source-count metadata, per-thread subtitle/message count, and
  its dismiss action.
- Non-front cards expose their ordinary notification descriptions and dismiss
  actions.
- Traversal remains newest-first.

### Transition and host gates

- Rear semantics appear only after expansion fully completes.
- Rear semantics disappear immediately when collapse begins.
- Alpha alone never controls semantics.
- Accessibility dismiss actions obey effective interaction state.
- The hidden host remains `invisibleToUser`.
- Arrival-covered cards cannot perform accessibility mutations behind the
  splash.

Reduced motion uses the same endpoint semantics without intermediate frames.

---

## 13. Circular-edge transform

Keep the pure behavior of:

```text
circleEdgeTransform(...)
```

unchanged.

The current adapter assumes lazy-item-level geometry and calculates viewport
center as though the viewport starts at zero. The new adapter must use:

```text
viewportCenter =
    (viewportStartOffset + viewportEndOffset) / 2
```

For each expanded child:

```text
childCenterInViewport =
    groupItemOffset
    + slotTopWithinItem
    + childY
    + childNaturalHeight / 2
```

Collapsed:

- all visible stack layers share the visual group's center;
- the collapsed stack behaves as one circular object.

Expanded:

- each card uses its own child center.

Transition:

- interpolate from group-center to child-center coordinates using the same
  expansion progress;
- feed that effective center into the existing pure transform; and
- combine its `scaleX` with stack width scale.

Read `LazyListState.layoutInfo` in the placement/layer path. Avoid
`onGloballyPositioned` state feedback, composition writes driven by layout, or
nested state writes during placement.

The hidden non-scrollable branch continues without scrolling edge transforms,
matching the current repository behavior.

---

## 14. Style and public API

For this iteration, keep the stack geometry internal:

```text
stackBackLayerCount = 2
stackBackLayerOffset = 8.dp
stackBackLayerWidthReduction = 0.06f
stackBackLayerVisibleHeight = 10.dp
```

All four are internal constants because:

- geometry still requires device tuning;
- the values are tightly coupled to each other, clipping, and edge scale;
- there is no demonstrated consumer requirement; and
- premature public API cannot be safely withdrawn later.

Do not add these fields to public `OneRingNotificationDimensions` or
`OneRingNotificationDefaults.dimensions()` in this iteration. Keep transition
curves, content-reveal thresholds, z-order values, and typography details
internal as well.

No notification-domain or host public API changes are required.

---

## 15. Animation design

Animation acceptance criteria are more important than a specific primitive.

Collapsed to expanded:

- front remains visually anchored;
- real rear cards peel downward;
- rear cards widen;
- visible clips open;
- content fades in; and
- following source items move naturally.

Expanded to collapsed:

- the same cards return under the front;
- content fades out;
- cards narrow;
- clips shrink; and
- the feed closes the released space.

Explicitly reject a collapsed subtree fading out while a separate expanded
subtree fades in.

The leading candidate is:

```text
updateTransition
+ custom Layout
+ one expansion progress
```

Use a remembered `Animatable<Float>` only if the device spike demonstrates
that `updateTransition` cannot provide acceptable interruption or velocity
behavior. The persistent keyed slot and geometry model do not change if the
primitive changes.

Do not use `AnimatedContent` as the group architecture. `animateContentSize`
alone is insufficient.

### Reduced motion

Reuse the host's existing animation-enabled detection. When animations are
disabled, expansion progress jumps directly to 0 or 1 inside the same
persistent slot. Semantics and interaction move directly to their endpoint.

---

## 16. Test plan

### Pure JVM tests

Add `NotificationStackPresentationTest.kt` for:

- 1/2/3/8-thread presentation;
- visible rear depths 0/1/2/2;
- exact source thread count;
- separation of source thread count and per-thread message count;
- newest/front selection;
- promotion after front removal;
- expansion toggle;
- effective eligibility for missing, single, and multi-thread sources;
- stale-intent cleanup inputs;
- unequal natural-height collapsed geometry;
- child 3+ zero-clip geometry;
- expanded cumulative offsets and total height;
- midpoint interpolation; and
- group/child/viewport transform-center math.

Extend existing subtitle tests with a three-thread source whose front thread
has five messages. The source count of three and thread message count of five
must remain independently available.

Retain existing `NotificationFeedTest` coverage for grouping, same-thread
merge, newest-first order, source order, and thread-level dismissal.

### Compose/device tests

Add `NotificationStackComposeTest.kt` for observable behavior:

- normal one-thread card;
- static 2/3/8-thread stacks;
- only two visible rear depths;
- collapsed rear content absent from semantics;
- collapsed rear interaction disabled;
- source count and existing message-count subtitle simultaneously present;
- tap expand and tap collapse;
- one expanded source at a time;
- all expanded threads visible and semantic;
- front top remains anchored;
- front dismissal promotion;
- expanded non-front dismissal;
- `2 -> 1` and `1 -> 0`;
- no stale re-expansion;
- tap/swipe/X/vertical/rightward arbitration;
- secondary interaction disabled during transition; and
- reduced-motion endpoint behavior.

Extend host tests for:

- same-thread update;
- new-thread update;
- expanded-source update;
- other-source update;
- splash-before-message ordering;
- source count not appearing before splash reveal; and
- one overall scroll surface with mixed single and stacked sources.

Extend core-overlay integration tests for:

- stack tap not triggering the ring;
- submenu hide/restore;
- main Seek hide/restore;
- submenu Seek hide/restore; and
- hidden stack exposing no actionable input or semantics.

### Manual and performance checks

Use the connected Pixel 6a for:

- 3 threads with unequal heights;
- 8 threads;
- 20 threads;
- light and dark styles;
- top/bottom circular scrolling;
- real-card clipping and border/corner quality;
- rapid expand/collapse reversal; and
- frame timing.

Compose tests should assert observable content, semantics, actions, bounds,
state changes, and final order. They must not assert exact internal progress,
easing, z-index values, slot-table details, or the fact that
`updateTransition` was selected.

---

## 17. Files

### New production files

- `NotificationGroupSlot.kt`
  - persistent keyed cards;
  - custom measurement and placement;
  - z-order, width scale, clipping, edge layers, and content reveal;
  - endpoint semantics and interaction.
- `NotificationStackPresentation.kt`
  - Compose-free decisions, eligibility, counts, geometry, and interpolation.

### New test files

- `NotificationStackPresentationTest.kt`
- `NotificationStackComposeTest.kt`

### Existing files planned for modification

- `OneRingNotificationHost.kt`
  - stored/effective expansion state, cleanup, and callbacks.
- `NotificationFeedList.kt`
  - one item per source and shared source renderer.
- `NotificationCard.kt`
  - additive source metadata, unchanged subtitle, content progress, tap-enabled
    gesture loop, interaction and semantics gates.
- `CircleEdgeTransform.kt`
  - pure coordinate/center helpers while retaining current transform behavior.
- `NotificationDemo.kt` / `NotificationDemoData.kt`
  - deterministic 3/8/20-thread device scenarios.
- relevant existing JVM, Compose, host, integration, and sample tests.
- `docs/notifications.md` after implementation.

Do not plan these as primary components:

- `NotificationStack.kt`
- `ExpandedNotificationGroup.kt`
- `NotificationStackBackCard.kt`

The arrival overlay, notification domain files, One Ring public API, and public
notification style/dimensions remain structurally unchanged.

---

## 18. Phased implementation and gates

### Phase 1 — Compose-free stack logic

Objective:

- implement stack decisions, count separation, eligibility, toggle, natural-
  height geometry, clipping geometry, and coordinate interpolation;
- add JVM tests;
- make no UI behavior change.

Gate:

- targeted pure tests pass;
- no composable architecture is introduced prematurely.

### Phase 2 — static persistent stack

Objective:

- emit one source item;
- add the persistent `NotificationGroupSlot`;
- use real rear cards;
- implement static bottom-strip clipping and width scaling;
- show additive source-count metadata while retaining `displaySubtitle()`;
- suppress rear semantics and input; and
- establish per-child edge-transform infrastructure.

Device checkpoint:

- 2/3/8 threads;
- unequal heights;
- both source and message counts;
- no content/X leakage;
- light/dark styling;
- circular top/bottom scrolling.

Gate:

- real cards produce acceptable backing strips;
- no fake backing component is introduced;
- notification and ring integration tests remain green.

### Phase 3 — instant persistent expand/collapse

Objective:

- add stored/effective host state;
- enforce one expanded source;
- extend the unified gesture loop;
- implement collapsed and expanded endpoint geometry without motion;
- enable secondary cards only at the expanded endpoint;
- integrate dismissal, arrival, hide, restore, and cleanup behavior.

Gate:

- all endpoint Compose behavior passes;
- tap/swipe/X/vertical arbitration passes;
- 8-thread expansion reveals already-composed cards;
- no alternate collapsed/expanded tree exists.

### Phase 4a — on-device animation spike: hard human stop

Phase 4a is a mandatory human-approval gate.

After Phases 1–3:

1. Build the animation spike.
2. Install it on the currently connected USB Pixel 6a only.
3. Evaluate a three-thread unequal-height group and an eight-thread group.
4. Check front anchoring, real-card peel, clipping, border/corners, content
   reveal, circular-edge behavior, rapid reversal, and frame timing.
5. Run the planned targeted tests.
6. Stop.

Do not decide autonomously that the spike is acceptable. Do not start Phase 4b
without explicit user approval.

The Phase 4a report must include:

- what was implemented in the spike;
- screenshots/recording details when available;
- front-card anchoring result;
- clipping result;
- real rear-card peel result;
- circular-edge result;
- 3-thread performance;
- 8-thread performance;
- interruption/reversal behavior;
- every visual artifact observed;
- tests and results; and
- exact files changed.

Wait for explicit approval after reporting.

### Phase 4b — production animation

Begin only after explicit approval of Phase 4a.

Objective:

- productionize the approved primitive and geometry;
- add interruption handling and reduced-motion snapping;
- preserve endpoint-only secondary interaction;
- add observable animation-safety tests without asserting implementation
  internals.

### Phase 5 — edge cases and quality gates

Objective:

- finish arrival/update cases;
- accessibility polish;
- dismissal reflow and cleanup;
- 8/20-thread performance checks;
- sample and user documentation;
- complete targeted and full quality checks.

The unrelated `ResolvedRingTest` baseline failure remains separately owned.
Feature-specific tests must be green; final reporting must distinguish the
known baseline failure from any notification-stack regression.

---

## 19. Git and worktree safety rules

Implementation works against the current filesystem state.

Do not perform any of the following unless explicitly instructed later:

- `git pull`
- `git fetch` followed by merge or synchronization
- `git rebase`
- `git reset`
- `git stash`
- checkout or switch to another branch
- force checkout
- clean untracked files
- amend existing commits
- commit
- push

Preserve all pre-existing user-owned changes and untracked files. In
particular, retain the existing 7 dp dismiss glyph in `NotificationCard.kt`.

Do not fix the obsolete `ResolvedRingTest`/`coreOverlayRadius` assertion as
part of this feature unless explicitly requested.

Only the connected Pixel 6a may be used for the planned device installation
and checks unless the user later authorizes another target.

---

## 20. Final implementation rules

The implementation agent must:

1. Preserve the existing notification-domain model and `sourceKey` grouping.
2. Use exactly one source item and one persistent multi-thread slot.
3. Key every entire card subtree by `threadKey`.
4. Use real older cards for rear layers.
5. Keep all active cards composed for 4+ threads.
6. Keep source thread count additive to the unchanged per-thread subtitle.
7. Measure natural card heights at stable full width.
8. Narrow rear cards with layer scale and reveal them with layer clipping.
9. Extend the current gesture loop rather than adding a competing body tap
   detector.
10. Gate pointer and accessibility interaction consistently.
11. Derive effective expansion only from displayed arrival-safe groups.
12. Preserve splash-before-message, watermark, submenu, Seek, and restoration
    behavior.
13. Keep stack geometry and motion tuning internal for this iteration.
14. Stop after Phase 4a and wait for human approval.
15. Preserve Git history, the current branch, untracked files, and all
    pre-existing edits.
