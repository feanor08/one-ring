package com.feanor08.onering.notification

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon

/**
 * One normalized notification thread/card, as rendered by
 * [OneRingNotificationHost]. Built internally from one or more
 * [OneRingNotificationPost]s sharing a [threadKey] -- see
 * [OneRingNotificationState.post].
 *
 * @param messages every message posted to this thread, oldest first.
 * @param sequence this thread's position in the internal monotonic post
 *   order (see [OneRingNotificationState]) -- higher is newer. Never derived
 *   from wall-clock time, so ordering stays deterministic regardless of when
 *   posts actually arrive.
 */
@Immutable
public data class OneRingNotification internal constructor(
    public val threadKey: String,
    public val sourceKey: String,
    public val sourceLabel: String,
    public val sourceIcon: OneRingIcon,
    public val icon: OneRingIcon,
    public val accentColor: Color,
    public val title: String,
    public val subtitle: String?,
    public val messages: List<String>,
    public val timestampLabel: String?,
    public val sequence: Long,
)

/**
 * One source's header plus its currently-active threads, newest-updated
 * first -- see docs/notifications.md's "source grouping" section. Produced
 * internally by [OneRingNotificationState]; a group disappears from the feed
 * as soon as its last [threads] entry is dismissed.
 */
@Immutable
public data class OneRingNotificationGroup internal constructor(
    public val sourceKey: String,
    public val sourceLabel: String,
    public val sourceIcon: OneRingIcon,
    /** This group's threads, newest-updated first. */
    public val threads: List<OneRingNotification>,
)
