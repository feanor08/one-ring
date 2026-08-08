package com.feanor08.onering

import androidx.compose.runtime.Immutable

/**
 * The optional event firehose for a ring, passed to [OneRing]'s `onEvent`
 * parameter. Most consumers never need this -- the common case (leaf actions)
 * is covered by `onAction` alone; this exists for analytics, sound design, or
 * custom haptics that need to react to navigation itself.
 */
@Immutable
public sealed interface OneRingEvent {
    @Immutable
    public data class HighlightChanged(public val itemId: String?) : OneRingEvent

    @Immutable
    public data class ActionCommitted(public val itemId: String) : OneRingEvent

    @Immutable
    public data class MenuOpened(public val itemId: String) : OneRingEvent

    @Immutable
    public data class MenuClosed(public val itemId: String) : OneRingEvent

    @Immutable
    public data class SeekEntered(public val itemId: String) : OneRingEvent

    @Immutable
    public data class SeekExited(public val itemId: String) : OneRingEvent
}
