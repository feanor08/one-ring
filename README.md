# one-ring

A native Android (Kotlin + Jetpack Compose) radial interaction framework —
press, scrub, release-to-commit. Not a volume-control widget: a domain-neutral
building block for automotive controls, quick-action wheels, media controls,
tool palettes, and nested radial menus.

Status: **pre-1.0, all ten phases of the [implementation blueprint](docs/PLAN.md)
built.** Geometry engine, the stutter-safe animation model, nested submenus,
main-ring and submenu seek controls, the `oneRingItems { }` DSL, events, style
presets, TalkBack semantics, haptics, API docs, and a verified
`publishToMavenLocal`. Not yet published to any remote repository — see
[Installing](#installing) and [`docs/PLAN.md`](docs/PLAN.md)'s "Open items"
for what's genuinely left (Maven Central credentials, deeper nesting, semantic
seek adjustment).

## Modules

- **`:one-ring`** — the library (`com.feanor08.onering`).
- **`:sample`** — **One Ring Showcase**, a browsable catalog of capability
  demos that doubles as the library's manual QA playground. See
  [Sample / Showcase](#sample--showcase).

## Installing

Not published to any repository yet (see [Publishing](#publishing) below).
Until then, consume it locally:

```bash
./gradlew :one-ring:publishToMavenLocal
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        // ... your other repositories
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.feanor08.onering:one-ring:0.1.0-SNAPSHOT") // PLACEHOLDER coordinates
}
```

## Quick start

```kotlin
val items = oneRingItems {
    action(id = "torch", label = "Torch", icon = OneRingIcon.Vector(Icons.Rounded.FlashOn))
    action(id = "camera", label = "Camera", icon = OneRingIcon.Vector(Icons.Rounded.PhotoCamera))
    action(id = "navigate", label = "Navigate", icon = OneRingIcon.Vector(Icons.Rounded.Navigation))
}

OneRing(
    items = items,
    onAction = { viewModel.dispatch(it.id) },
)
```

That's the whole API surface for the common case: a flat ring of actions,
committed on release.

## Weighted (asymmetrical) sectors

By default every sector is equal. Give an item a `weight` to make it bigger or
smaller relative to its siblings:

```kotlin
val items = listOf(
    OneRingItem.Action(id = "primary",   label = "Primary",   weight = 2f),
    OneRingItem.Action(id = "secondary", label = "Secondary", weight = 1f),
    OneRingItem.Action(id = "third",     label = "Third",     weight = 1f),
)
// -> 180deg / 90deg / 90deg
```

Weights are **relative**, never percentages that must add up to anything, so
`2 / 1 / 1` and `50 / 25 / 25` describe exactly the same ring. They're
normalized against their siblings and then scaled to whatever sweep is actually
available — a partial ring (`totalSweepDeg = 300f`) with weights `2 / 1 / 1`
gives `150 / 75 / 75`.

`weight` defaults to `1f`, so a ring that never mentions weights behaves
exactly as it always did. It must be finite and positive; anything else throws
at construction rather than producing a broken arc later.

Nested children are weighted **independently of their parent** — the parent's
weight decides its share of the main ring, and the children's weights divide
the submenu's available sweep (the circle minus the parent's reserved gap):

```kotlin
oneRingItems {
    menu(id = "media", label = "Media", weight = 2f) {   // half the main ring
        action(id = "playPause", label = "Play / Pause", weight = 2f)
        action(id = "previous",  label = "Previous")     // defaults to 1f
        action(id = "next",      label = "Next")
        seek(id = "volume", label = "Volume", weight = 2f, /* ... */)
    }
    menu(id = "climate", label = "Climate") { /* ... */ }
    action(id = "navigation", label = "Navigation")
}
```

Seek items honour their weight too, and the seek track is built from the
selected wedge's *actual* span — a weight-2 seek excludes twice as much of the
ring from its own progress track as a weight-1 one would.

## Nested menus

A `menu { }` block inside the DSL becomes a `Menu` item; committing it grows
the wedge into the close control and reveals its children as a submenu band:

```kotlin
val items = oneRingItems {
    menu(id = "music", label = "Music", icon = OneRingIcon.Vector(Icons.Rounded.MusicNote)) {
        action(id = "previous", label = "Previous")
        action(id = "playPause", label = "Play / Pause")
        action(id = "next", label = "Next")
    }
}
```

## Seek controls

A `seek(...)` item is a radial slider, fully controlled like
[`Slider`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#Slider(kotlin.Float,kotlin.Function1,androidx.compose.ui.Modifier,kotlin.Boolean,kotlin.ranges.ClosedFloatingPointRange,kotlin.Int,kotlin.collections.List,androidx.compose.material3.SliderColors,androidx.compose.foundation.interaction.MutableInteractionSource))
— `one-ring` never owns the value, and never interprets what it means:

```kotlin
var volume by remember { mutableFloatStateOf(0.48f) }

val items = oneRingItems {
    seek(
        id = "volume",
        label = "Volume",
        icon = OneRingIcon.Vector(Icons.AutoMirrored.Rounded.VolumeUp),
        value = volume,
        onValueChange = { volume = it },
        valueLabel = { "${(it * 100).toInt()}%" },
    )
}
```

Committing a seek on the main ring grows it into the close control, exactly
like a menu; a `seek(...)` nested inside a `menu { }` block grows only to
hover instead, since the menu's own close wedge stays the exit control. Both
variants: drag anywhere on the ring to adjust, release to commit the final
value via `onValueChangeFinished`.

## Notifications

`OneRing`'s empty core can host a generic, scrollable notification feed via
the `coreOverlay` slot (a new, larger sibling of the existing small `center`
slot):

```kotlin
val ringState = rememberOneRingState()
val notifications = rememberOneRingNotificationState()

OneRing(
    items = items,
    state = ringState,
    onAction = { /* ... */ },
    coreOverlay = { OneRingNotificationHost(state = notifications) },
)

notifications.post(
    OneRingNotificationPost(
        threadKey = "chat:alex",
        sourceKey = "messaging",
        sourceLabel = "Messages",
        title = "Alex",
        message = "Running 5 minutes late.",
        /* ... */
    ),
)
```

Same-`threadKey` posts accumulate into one card (`"Alex · 2 messages"`, not
two cards); cards are grouped by `sourceKey`, each with its own accent color;
the whole feed is one scrollable list; and it automatically hides while a
submenu or seek is open, returning once the main ring settles. See
[`docs/notifications.md`](docs/notifications.md) for the full behavior
(same-thread grouping, source grouping, dismissal, the arrival animation,
and the library-boundary rules that keep `:one-ring` Material-free).

## Customization

Every visual and behavioral knob lives on `OneRingStyle`, built through
`OneRingDefaults`:

```kotlin
OneRing(
    items = items,
    onAction = { ... },
    style = OneRingDefaults.style(
        colors = OneRingDefaults.colors(sectorHighlighted = Color(0xFF3D8BFF)),
        dimensions = OneRingDefaults.dimensions(touchSlopRadial = 24.dp),
    ),
)
```

Four presets cover common cases out of the box:

```kotlin
OneRingDefaults.style()            // default
OneRingDefaults.compactStyle()     // tighter radii, thinner touch slop -- small screens
OneRingDefaults.automotiveStyle()  // larger touch targets, bolder highlight -- in-vehicle
OneRingDefaults.gameStyle()        // snappier, bouncier motion -- weapon wheels, quick actions
```

## State handling

Navigation state (which level is open, what's highlighted) is uncontrolled by
default — most consumers never touch it. Hoist it with `rememberOneRingState()`
to observe or drive it programmatically:

```kotlin
val state = rememberOneRingState()
OneRing(items = items, onAction = { ... }, state = state)

// state.mode: Idle, Tracking, Submenu, Seek, or Transitioning
// state.path: the current navigation path, e.g. ["music"]
```

`state.path` survives configuration change (`rememberSaveable` under the
hood); mid-animation values never are, since restoring into a half-finished
transition would be meaningless.

## Accessibility

Every wedge carries a real (invisible) semantics node with a description,
role, and click action, positioned at the wedge's actual location — a
genuine second input path, not decoration. TalkBack can navigate, open/close
menus, and enter/exit a seek control without a single scrub gesture.
(Adjusting an active seek's value via TalkBack still needs a real drag —
see `docs/PLAN.md` section 8.) System-level "remove animations" is honored
automatically.

## Haptics

```kotlin
OneRing(
    items = items,
    onAction = { ... },
    haptics = OneRingHaptics.None, // disable regardless of OneRingBehavior.hapticsEnabled
)
```

The default (`rememberDefaultOneRingHaptics()`) fires on highlight change,
commit, reject, and menu/seek open/close, deduplicated so scrubbing within one
wedge never fires continuously. Supply your own `OneRingHaptics` to route
feedback elsewhere (e.g. an automotive HMI's vehicle-side haptics).

## Sample / Showcase

```bash
./gradlew :sample:installDebug
```

The app opens on a list of demos; each one drives the **public** API only —
no screen reimplements hit testing, geometry, seek mapping, or animation.

| Screen | What it demonstrates |
| --- | --- |
| Basic | Press, scrub, highlight, hover growth, release-to-commit |
| Nested | Submenu navigation; parents with 2–6 children; reserved parent gap |
| Asymmetrical / Weighted | Weighted sectors (50/25/25, 40/30/20/10, 5/1/1/1/2, Equal) plus a live weight editor with normalized percentages |
| Nested Growth | The submenu growing outer-edge inward at Slow/Default/Fast, on a weighted parent with weighted children and a weighted seek |
| Seek | Main-ring and submenu seek; ranges (0–100, 16–30 °C, 1–5x, 0–300 s); step quantization; intro-hint toggle and overshoot |
| Item Counts | 2/3/4/5/6/8/10/12 items — the geometry engine isn't hardcoded to six |
| Geometry | Live sliders over every radius, start angle, sweep, gap, and thumb size |
| Styling | Six colour presets plus live highlight overrides, all via `OneRingDefaults.colors()` |
| Motion | Instant / Fast / Default / Slow motion presets |
| Dynamic | Add / remove / shuffle / disable at runtime; hoisted seek state shared with a plain `Slider` |
| Automotive | `automotiveStyle()`, a Climate submenu, temperature and fan seeks |
| Game Wheel | An 8-item weapon wheel with `gameStyle()` and custom centre content |
| Smart Home | A 5-child submenu with a brightness seek, and a custom `OneRingIcon.Content` wedge |
| Media | Icons and labels that swap from app state; a media-position seek |
| Stress Test | The animation-continuity regression check and a rapid-input torture test with a live state overlay |
| Accessibility | Disabled items, the haptics toggle, and an honest account of what TalkBack can and can't do today |

Most screens carry a collapsible **View usage** snippet with the minimal Kotlin
for that configuration, and several carry an **Event log** showing the
`OneRingEvent` firehose live.

Manual QA checklist: [`sample/MANUAL_TESTING.md`](sample/MANUAL_TESTING.md).

## Publishing

Not published to any remote repository yet. `com.feanor08.onering:one-ring`
in the snippets above is a **placeholder** group/artifact — revisit before
publishing anywhere beyond `mavenLocal()`.

`./gradlew :one-ring:publishToMavenLocal` works and is verified end-to-end:
it produces a real AAR, sources jar, POM (with license/developer/SCM
metadata), and Gradle module metadata under `~/.m2/repository/com/feanor08/onering/one-ring/`,
and a standalone scratch consumer project (a fresh Gradle project resolving
`com.feanor08.onering:one-ring:0.1.0-SNAPSHOT` purely from `mavenLocal()`,
not a `project()` dependency) compiled and assembled a real debug APK against
it, exercising the DSL, `OneRing()`, and a style preset.

Maven Central publishing (a verified namespace, GPG signing) is still open —
see `docs/PLAN.md` section 20's "v1 vs deferred" table for what's intentionally
not set up yet.

## Building the library itself

```bash
./gradlew build
```

> **Note for this repo's current checkout location:** if the project directory
> lives on a network/external volume without reliable POSIX file locking (as
> `/Volumes/...` mounts sometimes are on macOS), Gradle's project-local build
> cache can fail with `Bad file descriptor` / `Input/output error`, and running
> many tasks concurrently against it (multiple lint/dex workers writing at
> once) can intermittently throw `AccessDeniedException` under heavy parallel
> I/O. Work around both by pointing the project cache at local disk and
> capping concurrency:
> ```bash
> ./gradlew build \
>   --project-cache-dir /tmp/gradle-project-cache/one-ring \
>   --no-parallel --max-workers=1
> ```
> This is a filesystem quirk of the mount, not a project configuration issue —
> CI (which runs on a local disk) is unaffected and uses the normal
> `./gradlew` invocations in `.github/workflows/ci.yml`.

## Documentation

- [`docs/PLAN.md`](docs/PLAN.md) — the full engineering blueprint: architecture,
  module structure, public API design, testing strategy, risks, and the
  phase-by-phase implementation plan.
- [`docs/geometry.md`](docs/geometry.md) — the angle/geometry conventions used
  throughout the geometry engine.
- [`docs/interaction-spec.md`](docs/interaction-spec.md) — the behavioural spec
  extracted from the reference HTML prototype.
- [`docs/prototype/`](docs/prototype/) — the original interaction prototype
  (plain HTML/SVG/JS) that `one-ring` generalises into a native Compose
  library.
- API docs: `./gradlew :one-ring:dokkaGeneratePublicationHtml`, then open
  `one-ring/build/dokka/html/index.html`. Not yet hosted anywhere.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
