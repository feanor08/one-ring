package com.feanor08.onering.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Hoistable state for one ring's notification feed. Owns every posted
 * thread; [OneRingNotificationHost] only ever *renders* what's here, so
 * posting/dismissing works identically whether the host is currently visible
 * or hidden behind an open submenu -- see docs/notifications.md's
 * "notification state vs OneRing navigation state" section.
 *
 * This is deliberately separate from [com.feanor08.onering.OneRingState]:
 * navigation state (which menu is open, what's highlighted) and notification
 * data are unrelated concerns with independent lifecycles.
 *
 * Icons/painters referenced by posted notifications are not
 * `rememberSaveable` -- like the rest of the library's icon handling, that's
 * an explicit v1 limitation (see [rememberOneRingNotificationState]), not an
 * oversight.
 */
@Stable
public class OneRingNotificationState internal constructor() {
    private var feed: NotificationFeed by mutableStateOf(NotificationFeed())

    /** Every active thread, newest-updated first. */
    public val notifications: List<OneRingNotification>
        get() = feed.threadsNewestFirst()

    /**
     * Threads grouped by [OneRingNotificationPost.sourceKey], most-recently-
     * updated source first -- what [OneRingNotificationHost] actually renders.
     */
    public val groups: List<OneRingNotificationGroup>
        get() = feed.groupedBySource()

    /** True when there are no active threads at all. */
    public val isEmpty: Boolean
        get() = feed.isEmpty

    /** The newest thread's sequence number, or 0 if empty -- used by the host to gate arrival replay. */
    internal val latestSequence: Long
        get() = feed.latestSequence

    /**
     * Posts a notification event. A [OneRingNotificationPost.threadKey]
     * matching an existing thread appends its message to that thread rather
     * than creating a new card -- see [OneRingNotificationPost]'s doc.
     */
    public fun post(post: OneRingNotificationPost) {
        feed = feed.post(post)
    }

    /** Dismisses [threadKey]'s entire card. A no-op if that thread isn't currently active. */
    public fun dismiss(threadKey: String) {
        feed = feed.dismiss(threadKey)
    }

    /** Dismisses every active thread. */
    public fun clear() {
        feed = feed.clear()
    }
}

/** Creates and remembers a [OneRingNotificationState] for the composition's lifetime. */
@Composable
public fun rememberOneRingNotificationState(): OneRingNotificationState =
    remember { OneRingNotificationState() }
