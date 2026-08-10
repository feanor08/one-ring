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

## Source grouping

Threads are grouped by `sourceKey` — a plain string the library never
interprets (`"whatsapp"`, `"vehicle"`, whatever the consumer wants). Each
group gets a small header (source icon, label, active-thread count). Groups
are ordered most-recently-updated first; within a group, threads are also
newest-first. A source's header disappears the moment its last thread is
dismissed — there's no separate "empty group" state.

## Whole-feed scrolling

The entire feed — every source header and every card — is **one**
`LazyColumn`. There is no per-card scroll container: a card taller than the
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

## Incoming icon-first arrival animation

A new post that arrives *while the feed is visible* plays a brief, fully
opaque arrival sequence: a small circle grows to fill the whole `coreOverlay`,
shows only a large centered icon (that notification's `sourceIcon`, falling
back to `icon`), holds briefly, then fades/scales away to reveal the updated
feed underneath. The arrival circle is genuinely opaque for its entire
grow-and-hold phase — only the final exit phase ever reduces its alpha — so
existing cards can never show through behind the icon.

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
notification color; cards themselves stay a consistent near-black regardless
of source.

## Reduced motion

`OneRingNotificationHost` reuses the ring's own reduced-motion detection
(the system "Remove animations" setting). With it on, the arrival sequence is
skipped entirely (the feed just updates), and the visibility fade snaps
instead of animating. Swipe-to-dismiss still works normally either way.

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
