# Notifications

A generic notification/feed capability for `OneRing`'s empty core, additive to
every existing interaction (submenu growth, weighted sectors, seek controls,
icon↔X staging, sibling-return animations, press/scrub/release, accessibility).
Nothing described elsewhere in this repo changes because of this feature.

## Quick start

```kotlin
val ringState = rememberOneRingState()
val notifications = rememberOneRingNotificationState()

OneRing(
    items = items,
    state = ringState,
    onAction = { /* ... */ },
    coreOverlay = {
        OneRingNotificationHost(state = notifications)
    },
)

notifications.post(
    OneRingNotificationPost(
        threadKey = "chat:alex",
        sourceKey = "messaging",
        sourceLabel = "Messages",
        sourceIcon = OneRingIcon.Vector(Icons.Rounded.Chat),
        icon = OneRingIcon.Vector(Icons.Rounded.Chat),
        accentColor = Color(0xFF56C985),
        title = "Alex",
        message = "Running 5 minutes late.",
    ),
)
```

## `coreOverlay` vs the existing `center` slot

`OneRing` already had a small `center` slot, sized by
`OneRingDimensions.centerRadius` (default ~0.216 of the base radius) —
unchanged by this feature. `coreOverlay` is a new, independent, much larger
slot sized by the new `OneRingDimensions.coreOverlayRadius` (default 0.43),
intended for real content like a notification feed. Both are optional and can
be used together (`coreOverlay` draws above `center`). Consumers of the
existing `center` slot don't need to change anything.

`coreOverlayRadius` must stay smaller than `mainInner` so overlay content
never overlaps the main ring; every built-in preset (`style()`,
`compactStyle()`, `automotiveStyle()`, `gameStyle()`) leaves real clearance.
If you tune `mainInner`/`coreOverlayRadius` yourself (e.g. via the Geometry
demo's sliders) far enough to violate that, a one-time `Log.w` fires — it's
diagnostic only, never blocking.

## Notification state vs `OneRing` navigation state

`OneRingNotificationState` (hoisted via `rememberOneRingNotificationState()`)
is entirely separate from `OneRingState` (the ring's navigation/highlight
state). Posting, dismissing, and clearing notifications never touches which
menu is open or what's highlighted, and opening/closing a submenu never
touches notification data. `OneRingNotificationHost` is what bridges the two
— it reads the ring's `OneRingMode` (via its `OneRingCenterScope` receiver)
purely to decide whether to *show* the feed; the data itself is never
affected by that mode.

## Same-thread grouping

Post again with the same `threadKey` and it's treated as a new message on the
same thread — the message is appended, and the card's identity fields
(title, icons, accent) take the newer post's value while `subtitle` /
`timestampLabel` only change if the newer post actually supplies one:

```kotlin
notifications.post(OneRingNotificationPost(threadKey = "chat:adarsh", title = "Adarsh", message = "I'll reach in 10 minutes.", /* ... */))
notifications.post(OneRingNotificationPost(threadKey = "chat:adarsh", title = "Adarsh", message = "I'm outside.", /* ... */))
```

renders as **one** card:

```
Adarsh
WhatsApp · 2 messages

I'll reach in 10 minutes.
I'm outside.
```

Never two. Ordering is driven by an internal monotonically increasing
sequence, never wall-clock time — an updated thread (and its source group)
always moves to the front.

A caller-supplied `subtitle` is a **base label**, not a full override of the
count: `subtitle = "WhatsApp"` with one message reads "WhatsApp"; the same
thread updated to two messages reads "WhatsApp · 2 messages" regardless. The
count is always appended once a thread has more than one message.

## Source grouping

Threads are grouped by `sourceKey` — a plain string the library never
interprets (`"whatsapp"`, `"vehicle"`, whatever the consumer wants), purely
to decide ordering: groups are ordered most-recently-updated first, and
within a group, threads are also newest-first. There's no separate header row
per group — each card already carries its own source via `subtitle`.

## Multi-thread stacks

A source with two or more active threads collapses into an Apple-style stack
instead of one card per thread: the newest-updated thread is the front card,
older threads peek from behind it as a real (not decorative) backing strip —
up to two visible rear depths regardless of how many threads the source
actually has. Tapping the front card expands the stack in place (every active
thread becomes a full, independently interactive card); tapping the front
card again collapses it. Only one source is ever expanded at a time —
expanding a different source implicitly collapses whatever was open.

```kotlin
notifications.post(OneRingNotificationPost(threadKey = "chat:adarsh", sourceKey = "messaging", title = "Adarsh", message = "I'll reach in 10 minutes.", /* ... */))
notifications.post(OneRingNotificationPost(threadKey = "chat:varsha", sourceKey = "messaging", title = "Varsha", message = "Are we still on for tonight?", /* ... */))
```

renders as one collapsed stack, front card:

```
Varsha                         Messages · 2
Messages
Are we still on for tonight?
```

The front card's own `"Messages · 2"` label is the **source's active-thread
count** — a fact independent of, and additive to, the existing per-thread
`displaySubtitle()` message count described above. A three-thread source
whose front thread has accumulated five messages shows both simultaneously:
`"Messages · 3"` (source) and `"Messages · 5 messages"` (thread), never one
substituting for the other.

Every card in a stack — front and every rear position — is a real,
persistently-keyed `NotificationCard` for a real thread, never a decorative
placeholder; a thread's own remembered state (its live swipe offset, its
Compose node identity) survives collapse, expand, insertion, dismissal, and
reordering intact. Dismissing the front thread promotes the next-newest
thread to front; dismissing any thread while expanded removes only that
thread; a source dropping to one thread becomes an ordinary card (no stack
chrome); a source dropping to zero threads disappears entirely, same as any
other source.

While collapsed, a rear card exposes no readable or actionable accessibility
semantics at all (not merely disabled actions — it's absent from the
semantics tree until the stack is fully expanded), and the front card's own
message content is capped at a standard visible height
(`OneRingNotificationDimensions.collapsedCardMaxHeightFraction`, default
`0.55` of the core overlay diameter) regardless of how long the message is —
a long message is visually capped while collapsed, never left to grow the
collapsed stack without bound. Expanding always reveals every card's full,
uncapped content.

## Whole-feed scrolling

The entire feed — every card — is **one** `LazyColumn`. There is no
per-card scroll container: a card taller than the
visible circular viewport simply extends past it, and the user continues
scrolling the *feed* to read the rest and carry straight on into the next
card. Cards narrow and dim slightly as they scroll toward the circular
viewport's top/bottom edge (via a `graphicsLayer` transform, not a layout
change, so scrolling stays smooth).

## Dismissal

Every card has an X at the top-right (`Role.Button`, content description
`"Dismiss <title> notification"`, plus a custom accessibility action) and
supports swipe-left-to-dismiss with a directional lock against the feed's own
vertical scroll — only a swipe that clearly wins horizontally (past touch
slop, `|dx| > |dy| * 1.15`, moving left) claims the gesture; a swipe that
turns out to be vertical hands off to the scroll untouched. Only swiping
*left* dismisses; a rightward swipe always springs back. Dismissing removes
the whole thread (all its accumulated messages), not just the latest message.

## Automatic hide during submenu/seek

The feed is visible only while `OneRingMode` is `Idle` or `Tracking` — the
moment a submenu or seek starts opening (`Transitioning`, then `Submenu` /
`Seek`), it fades out (~140ms) and stays hidden until the main ring is fully
settled again. Notification **data** is never cleared while hidden — posting
while a submenu is open updates the feed immediately; the user just sees the
updated content once they return, with no stale visual replay.

The feed is also completely non-hit-testable the whole time it's hidden (or
covered by an arrival splash — see below). `OneRing`'s `coreOverlay` slot has
no built-in mechanism for this; it's `OneRingNotificationHost`'s own
responsibility, using `OneRingCenterScope.mode`. It's done by *composition*,
not by consuming pointer events: an `interactive` flag
(`visible && activeArrival == null`) is threaded down to `NotificationFeedList`,
which uses it to decide whether its `pointerInput` modifiers -- the list's
own scroll (`LazyColumn`'s `userScrollEnabled`), each card's swipe, each
card's dismiss button -- are even *installed* at all. When not interactive,
there is nothing there for hit-testing to find, full stop -- so a hidden feed
can never intercept a submenu wedge press or a seek drag that geometrically
overlaps its radius (which it does by design -- `coreOverlayRadius` sits
inside the submenu band). See `OneRingNotificationHost`'s "coreOverlay
interactivity" doc for why this is preferred over a consumption-based
approach: it doesn't depend on any assumption about how Compose orders or
delivers pointer events across sibling subtrees (this feed vs. the ring's own
`Canvas`).

## Incoming icon-first arrival animation

A new post that arrives *while the feed is visible* plays a brief, fully
opaque arrival sequence: a small circle grows to fill the whole `coreOverlay`,
shows only a large centered icon (that notification's `sourceIcon`, falling
back to `icon`), holds briefly, then fades/scales away to reveal the updated
feed underneath. The arrival circle is genuinely opaque for its entire
grow-and-hold phase — only the final exit phase ever reduces its alpha — so
existing cards can never show through behind the icon.
`OneRingNotificationColors.arrivalBackground` enforces this: its `init`
block requires full opacity (alpha ~1), rejecting a translucent value at
construction rather than allowing a silent regression.

**Ordering is strictly splash-then-message, never message-then-splash.**
`OneRingNotificationHost` never renders `OneRingNotificationState`'s live
data directly — it renders a `safeGroups` snapshot that only ever mirrors the
live data while nothing is (or is about to be) mid-arrival. The moment a post
makes an arrival necessary, that mirroring stops immediately (computed
synchronously in the same composition pass the change is first observed in,
not one frame later), so the new/updated card is never exposed even for a
single frame before its own splash starts covering it.
`NotificationArrivalOverlay` only calls back to catch `safeGroups` up to the
live value once it is already fully opaque and covering the screen (right
after the icon-only hold, before the exit fade starts) — so the swap itself
is invisible too. See `OneRingNotificationHost`'s "Arrival ordering" class
doc for the full mechanism.

A post that arrives *while the feed is hidden* (behind a submenu/seek) never
plays this animation there; the data updates silently, and no stale arrival
replays once the user returns to the main ring. This is tracked via an
internal sequence watermark, not a simple "was hidden" flag, so a post that
lands right as the feed becomes visible again still gets its arrival.

## Per-notification accent colors

Each notification carries its own `accentColor`, which drives its card
border, its own icon and its source header, its subtitle, its X's hover/press
tint, and its swipe-dismiss affordance — and the arrival animation's icon and
border, when that notification is the one arriving. There is no library-wide
*business* notification color (no source ever gets hardcoded into the
library); the neutral surface/text colors themselves come from whichever
`OneRingNotificationColors` preset the host theme picks (see below).

## Light and dark hosts

`OneRingNotificationColors` (`cardBackground`, `title`, `body`,
`secondaryText`, `arrivalBackground`) is entirely caller-suppliable — none of
it is source-specific. `OneRingNotificationDefaults.colors()` is a dark
preset (near-black cards); `OneRingNotificationDefaults.lightColors()` is a
light one (near-white cards, near-black title) for a light host background,
used by the sample's own Notifications demo. Every notification's own
`accentColor` drives its border/icon/subtitle regardless of which neutral
preset is active, so switching between them (or supplying entirely custom
colors to match some other theming convention) never touches notification
logic.

## Reduced motion

`OneRingNotificationHost` reuses the ring's own reduced-motion detection
(the system "Remove animations" setting). With it on, the arrival sequence is
skipped entirely (the feed just updates), and the visibility fade snaps
instead of animating. Swipe-to-dismiss still works normally either way. A
stack's own expand/collapse transition (`OneRingNotificationMotion.stackExpand`)
follows the same rule — with animations off, it jumps directly to the
collapsed/expanded endpoint with no intermediate frames.

## Library boundaries

`:one-ring` still doesn't depend on Material3 — the notification feed is
built entirely from Compose Foundation/UI primitives (`LazyColumn`,
`BasicText`, `Canvas`, `pointerInput`, `semantics`, `Animatable`). The
`:sample` app's `NotificationDemo` uses Material3/Material Icons freely, as
any consumer app would; none of that leaks into the library. The library
never interprets `sourceKey`/`threadKey`/`payload`-style data — WhatsApp,
vehicle alerts, and the rest are demo concepts only, defined in
`sample/.../ui/demos/NotificationDemoData.kt`.

## Out of scope for this feature (deliberately)

Call UI, media-player center UI, navigation center UI, event-priority
arbitration, an actual Android `NotificationListenerService`, persistence,
per-message (as opposed to per-thread) dismissal, and reply/action buttons on
a card. This feature is the generic presentation/state capability plus a
dummy showcase — nothing more.
