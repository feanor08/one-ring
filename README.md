# one-ring

A native Android (Kotlin + Jetpack Compose) radial interaction framework —
press, scrub, release-to-commit. Not a volume-control widget: a domain-neutral
building block for automotive controls, quick-action wheels, media controls,
tool palettes, and nested radial menus.

Status: **early development.** Phases 0–5, 7, and 8 of the [implementation
blueprint](docs/PLAN.md) are in place: bootstrap, geometry engine, static
rendering, pointer scrub with the stutter-safe animation model, selection
transitions, nested submenus, the `oneRingItems { }` DSL, events, style
presets, TalkBack semantics, and haptics. **Seek controls (Phase 6) are the one
gap** — `OneRingItem.Seek` exists in the model but committing one is currently
a no-op. The full sample and publishing prep are designed but not yet built —
see [`docs/PLAN.md`](docs/PLAN.md) for the phase-by-phase plan.

## Modules

- **`:one-ring`** — the library (`com.feanor08.onering`).
- **`:sample`** — a demo app exercising the library.

## Building

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

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
