# Changelog

All notable changes to this project are documented in this file. Format
follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning
follows [Semantic Versioning](https://semver.org/) (pre-1.0: breaking changes
bump the minor version).

## [Unreleased]

Everything below has landed on `main` but nothing has been published anywhere
other than a local Maven cache (`./gradlew :one-ring:publishToMavenLocal`).
There is no `0.1.0` tag/release yet.

### Added

- Core radial interaction engine: press-scrub-highlight-release, with a
  stutter-safe `Animatable`-based animation model (see `docs/PLAN.md` section
  10) -- a committed selection always continues smoothly from wherever its
  hover animation currently was, never snapping.
- Nested submenus, one level deep: a `Menu` item grows into the close control
  on commit and reveals its children as a submenu band expanding inward from
  a pinned outer edge.
- Radial seek controls (`OneRingItem.Seek`), both on the main ring and nested
  inside a submenu, with an intro affordance hint and step quantization.
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
