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
- **`:sample`** — a demo app exercising the library, including a preset
  switcher (Default / Compact / Automotive / Game).

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
