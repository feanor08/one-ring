package com.feanor08.onering.notification

/**
 * Whether the arrival animation should actually play for a feed currently at
 * [latestSequence], given [lastAnnouncedSequence] (the newest sequence the
 * host has already shown an arrival for, or 0 initially) and whether the host
 * is [visible] right now.
 *
 * A post that arrives while hidden (behind an open submenu/seek) must update
 * the data immediately but never play its arrival behind that submenu, and
 * must not replay a stale arrival once the user returns to the main ring --
 * see docs/notifications.md's "no stale replay" section. Modeling this as an
 * explicit sequence watermark (rather than a "was hidden" boolean) is what
 * makes that distinguishable from "became visible right as a post landed",
 * which *should* still animate.
 *
 * Pure so the gating logic itself -- the part most likely to regress into a
 * stale replay or a missed arrival -- is unit-testable without a Compose
 * runtime; see [OneRingNotificationHost] for where this is actually driven
 * from a `LaunchedEffect`.
 */
internal fun shouldPlayArrival(
    visible: Boolean,
    latestSequence: Long,
    lastAnnouncedSequence: Long,
): Boolean = visible && latestSequence > lastAnnouncedSequence

/**
 * The watermark to advance [lastAnnouncedSequence] to after this evaluation --
 * always [latestSequence], regardless of whether an arrival actually played.
 * This is what prevents a post received while hidden from later triggering a
 * "stale" arrival once the host becomes visible again: the watermark is kept
 * current even on the frames where the arrival was skipped.
 */
internal fun advancedWatermark(latestSequence: Long): Long = latestSequence
