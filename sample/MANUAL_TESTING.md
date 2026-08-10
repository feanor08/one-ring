# One Ring Showcase — manual QA checklist

The showcase app doubles as the library's manual QA playground. Automated
coverage lives in `:one-ring` (geometry, animation continuity, seek mapping)
and in `sample/src/androidTest` (screens compose, controls don't crash);
everything below needs a human finger, because it's about *feel* — timing,
continuity, and whether a gesture ever gets stuck.

```bash
./gradlew :sample:installDebug
```

Run through this on at least one phone and, if available, one large/automotive
display. Tick both portrait and landscape.

## Basic

- [ ] Press and hold: the wedge under the finger highlights and grows outward.
- [ ] Scrub between all sectors without lifting: highlight follows exactly one
      wedge at a time; the previous one shrinks back.
- [ ] Release: the highlighted wedge commits and `Last selected` updates to it.
- [ ] Release outside the ring band: nothing commits.
- [ ] Event log shows `HighlightChanged` on crossings and `ActionCommitted`
      once, on release — not repeatedly while inside one wedge.

## Nested

- [ ] Committing a menu grows that wedge into the X and fades every sibling out
      *in place* (they must not slide or rotate).
- [ ] The submenu band reveals inward from the outer edge.
- [ ] The parent's own angular gap stays empty — children never occupy it.
- [ ] Child count is respected: Audio 2, Bluetooth 3, Messages 3, Music 4,
      Settings 6, each filling the complement arc evenly.
- [ ] Pressing X returns to the main ring with every sibling restored.
- [ ] `path:` in the status line reads `[(root)]` before and after, and the
      menu id while open.

## Main-ring seek

- [ ] Committing the seek wedge grows it to the X, siblings fade, and the rest
      of the ring becomes the track.
- [ ] With the intro hint ON, the fill sweeps `0 → value + overshoot → value`.
- [ ] With the intro hint OFF, the fill appears immediately at value.
- [ ] The track starts at the X wedge's **clockwise** edge.
- [ ] The X wedge's own arc is never fillable.
- [ ] Dragging anywhere on the track moves the thumb and updates the number
      below the ring live.
- [ ] Releasing fires `onValueChangeFinished` once (visible in the event log).
- [ ] Pressing X exits back to the normal ring.
- [ ] Every range preset maps end-to-end correctly: Volume 0–100, Temperature
      16–30 °C, Zoom 1–5x, Fan 0–5, Media position 0–300 s.
- [ ] Step presets quantize: `Step 5` only ever lands on multiples of 5.

## Submenu seek

- [ ] Music → Volume: the Music parent stays the outer X.
- [ ] The Volume child wedge itself stays visible and grows to hover only.
- [ ] Sibling children fade; their arc becomes track.
- [ ] Progress skips **both** excluded gaps — the parent's gap and Volume's own
      wedge — and stays visually continuous across the separate arc segments.
- [ ] Drag works across a gap without the value jumping.

## Weighted ring

Asymmetrical / Weighted:

- [ ] The 50/25/25 preset renders as a half-ring plus two quarters; the
      breakdown text under it agrees with what's drawn.
- [ ] 40/30/20/10 and 5/1/1/1/2 render in the stated proportions.
- [ ] The Equal preset is indistinguishable from a ring with no weights at all.
- [ ] Scrub hit testing follows the **visual** sector boundaries — pressing
      just inside a large sector's edge selects it, and one pixel past selects
      the neighbour.
- [ ] Release selects the sector actually under the finger, including the
      narrow 10% ones.
- [ ] Dragging a live weight slider re-lays the ring out immediately.
- [ ] Driving a weight to its 0.5 minimum still leaves a usable, hit-testable
      sector.
- [ ] Partial-sweep weights work (Geometry demo: reduce total sweep, weights
      still divide only what's available).
- [ ] Nested weighted children divide only the parent's complement, and the
      parent's own gap stays empty.
- [ ] A weighted Seek item excludes its own full weighted wedge from the
      progress track — the track starts at that wedge's clockwise edge, not at
      an average sector's edge.

## Nested growth

Nested Growth, in Slow mode (1200 ms reveal). Use **Reset to main ring
(replay)** to return to the main menu and trigger the open again without
leaving the screen:

- [ ] The submenu does **not** pop in at full size.
- [ ] `mode:` reads `Transitioning` for a visible interval during the open —
      not an instant jump from `Idle` to `Submenu`.
- [ ] Children begin as near-zero-thickness arcs at the submenu's outer radius.
- [ ] They grow outer -> inner; the outer edge stays put, the inner edge moves.
- [ ] Icons fade and scale up with their sector rather than appearing instantly
      at full opacity.
- [ ] Weighted child boundaries stay stationary during the growth — a wide
      child and a narrow one both grow radially, with no angular sliding.
- [ ] Closing collapses the wedges radially rather than making them vanish.

Staged glyphs and back-control styling (Slow mode makes each stage separable):

- [ ] On open: the parent's icon fades **out**, then the wedge grows, then the
      X fades **in**. There is never a frame with both visible at once.
- [ ] Once settled, the X wedge is in **normal** styling -- not the accent
      colour, not the accent stroke, not looking pressed.
- [ ] Touching the X highlights it; moving off it returns it to normal; its
      geometry does not change either way.
- [ ] Pressing the X and scrubbing onto a submenu child moves the highlight to
      the child, and releasing there does **not** close the menu.
- [ ] Pressing a child and scrubbing onto the X, then releasing, **does** close.
- [ ] On close: the X fades out, then the submenu collapses and the parent
      shrinks, then the original icon fades back in -- again with no overlap.
- [ ] The same staging applies to a main-ring Seek: speaker out, wedge grows, X
      in; X rests in normal styling; releasing on X exits and the speaker
      fades back.
- [ ] Rapid open / close / open-a-different-parent never freezes interaction.
- [ ] Releasing before the parent's hover animation completes does not stutter:
      the parent continues outward from its current radius.
- [ ] Selecting the submenu's weighted Volume seek grows it from its current
      size while siblings fade in place.

## Animation continuity (the regression that mattered)

Stress Test → *Animation continuity*, where motion is deliberately slowed:

- [ ] Scrub onto a sector and release **before** its hover animation finishes.
- [ ] The committed sector continues growing from exactly where it was.
- [ ] It must **not** snap inward to the rest radius first.
- [ ] It must **not** jump outward to the hover radius before continuing.
- [ ] Repeat at several release timings, including immediately after touch-down.

## Rapid input

Stress Test → *Rapid input*:

- [ ] Hammer open / close / open-a-different-menu as fast as possible.
- [ ] Scrub violently and release early, repeatedly.
- [ ] Press X the instant a submenu starts opening.
- [ ] Enter the seek and immediately press X.
- [ ] `mode` in the debug overlay always settles back to `Idle` or `Submenu` —
      it must never stay `Transitioning`.
- [ ] The ring never stops responding to a fresh press.

## Item counts

- [ ] 2, 3, 4, 5, 6, 8, 10, 12 all render evenly with no overlap or gap drift.
- [ ] Hit testing stays aligned with what's drawn at every count (press each
      wedge and confirm the committed label matches the one under the finger).
- [ ] Icons shrink to stay inside their wedge at 10 and 12.

## Geometry / rotation / sweep

- [ ] Every slider changes the ring live with no crash or flicker.
- [ ] Start angle 0 / 30 / 45 / 90 / 180: the ring rotates and **hit testing
      rotates with it** — press the visually top wedge and confirm the correct
      item commits.
- [ ] Total sweep below 360 (e.g. 180, 240, 270, 300) leaves a real gap, and
      pressing inside that gap commits nothing.
- [ ] Inner radius above outer radius degrades without crashing.

## Styling

- [ ] All six colour presets render, including seek track/progress/thumb.
- [ ] Highlight swatch overrides apply immediately.
- [ ] High Contrast stays legible with the ring both idle and highlighted.

## Motion

- [ ] Instant: transitions complete with no visible animation.
- [ ] Fast / Default / Slow visibly differ on open, close, and submenu reveal.
- [ ] Switching preset mid-animation doesn't leave a wedge stranded.

## Dynamic

- [ ] Add / Remove / Shuffle re-lay the ring out immediately, no restart.
- [ ] Disabling an item dims it; releasing on it does **not** commit and fires
      the `Rejected` haptic instead.
- [ ] Scrubbing *across* a disabled sector still highlights it (so the user can
      see why nothing happens) but never commits it.
- [ ] The hoisted Brightness slider below and the ring's seek stay in sync in
      both directions.

## Dynamic content

- [ ] Media: Play/Pause swaps both icon and label from app state.
- [ ] Media: Mute/Unmute likewise.
- [ ] Smart Home: the AC wedge's custom status dot changes colour with state.

## Center content

- [ ] Game Wheel: the centre label tracks the highlighted item while scrubbing
      and falls back to the equipped item on release.
- [ ] The centre content never intercepts ring gestures.

## Notifications

- [ ] On opening the Notifications demo, after a brief pause the Adarsh
      notification arrives: a fully opaque circle grows from the center,
      shows only a large green message icon (nothing behind it is visible
      through it), holds briefly, then fades away to reveal the Adarsh card.
- [ ] Tap "Adarsh +1" repeatedly: the messages accumulate into the **same**
      card (`WhatsApp · N messages`), never a second card. The card grows
      taller as messages accumulate rather than getting its own scrollbar.
- [ ] Tap "WhatsApp · Varsha" and "WhatsApp · Family": all three land in one
      "WhatsApp" source group, reachable in order by scrolling the single feed.
- [ ] Tap each of Software Update / Tyre Pressure / Service Reminder / Cloud
      Notification / Battery Alert: each gets its own source group with its
      own accent color (border/icon/subtitle), and scrolling the feed reaches
      every group in most-recently-updated-first order.
- [ ] Cards narrow and dim slightly as they scroll toward the top/bottom edge
      of the circular viewport; scrolling stays smooth throughout (no jitter).
- [ ] Tap a card's X: that card disappears; everything else is unaffected. If
      it was the last card in its source, that source's header disappears too.
- [ ] Swipe a card left past roughly a third of its width: it dismisses.
      Swiping left a shorter distance springs it back. Swiping *right* never
      dismisses. A mostly-vertical drag scrolls the feed instead of dragging
      the card sideways.
- [ ] Open Music (or the main Volume seek): the feed fades out and stays
      hidden for the whole time the submenu/seek is open, including while
      scrubbing between children.
- [ ] Close Music (via the X) / exit the seek: the feed only fades back in
      *after* the main ring has fully finished animating back — not before,
      not mid-collapse. Scroll position and every card are exactly as left.
- [ ] While Music is open, trigger another notification button off-screen (or
      via "Adarsh +1"): no arrival circle appears behind the submenu. Closing
      the submenu shows the updated card directly, with no stale arrival
      animation replaying.
- [ ] "Clear All" empties the whole feed (all groups gone); "Reset Demo" also
      resets the Adarsh follow-up script back to its first line.
- [ ] Enable TalkBack: each card announces title, subtitle, and every message
      as one node; the X announces "Dismiss <title> notification" and has a
      working custom dismiss action; source headers read as headings; a new
      arrival announces "New notification: <title>" once, without re-reading
      every other card.
- [ ] System Settings → "Remove animations": the arrival sequence is skipped
      (the card just appears) and the hide/show fade snaps instead of easing;
      swipe-to-dismiss still works.

## Haptics

- [ ] With haptics ON: one tick per wedge crossing, a distinct commit pulse, a
      distinct rejection on a disabled item.
- [ ] Scrubbing within a single wedge fires **nothing** continuously.
- [ ] With the toggle OFF: complete silence.

## Accessibility

Enable TalkBack, then:

- [ ] Every wedge is reachable by swipe, announced with its label or explicit
      content description.
- [ ] Disabled items are announced as disabled.
- [ ] Double-tap activates an action, opens a menu, and closes one via the
      "Close …" node.
- [ ] A seek can be entered and exited.
- [ ] **Known gap:** a seek's *value* cannot be changed via TalkBack — it has no
      range semantics, so adjusting it still needs a real drag.
- [ ] **Known gap:** semantics nodes are 48 dp targets at wedge midpoints, not
      wedge-shaped, so explore-by-touch between them hits empty space.
- [ ] System Settings → "Remove animations": transitions become instant and
      nothing breaks.

## Layout / responsiveness

- [ ] Portrait and landscape both keep the ring centred and fully visible.
- [ ] Rotating the device mid-submenu preserves `path` (the submenu stays open).
- [ ] Rotating mid-drag doesn't leave the ring stuck in `Seek` or `Tracking`.
- [ ] Small (Compact) through Large geometry presets all remain operable.

## Known interaction hazard

- [ ] Pressing the X close control and then *dragging* (rather than lifting)
      lets the enclosing scrollable page take over the gesture, because the ring
      returns early from a close press without claiming the rest of the drag.
      Harmless here, but worth knowing if you embed a ring in a scrolling
      screen — prefer giving the ring a non-scrolling area.
