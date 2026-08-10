package com.feanor08.onering.notification

/**
 * The pure, Compose-free data model behind [OneRingNotificationState]:
 * posting/merging/dismissing/grouping, expressed as plain functions over an
 * immutable value so it's unit-testable on a plain JVM (this module has no
 * Robolectric -- see [OneRingNotificationState]'s class doc). The Compose
 * layer is a thin `mutableStateOf(NotificationFeed)` wrapper around this.
 */
internal data class NotificationFeed(
    private val threadsByKey: Map<String, OneRingNotification> = emptyMap(),
    /** Monotonically increasing; never wall-clock-derived, so ordering stays deterministic. */
    val nextSequence: Long = 1L,
) {
    val isEmpty: Boolean get() = threadsByKey.isEmpty()

    val latestSequence: Long get() = threadsByKey.values.maxOfOrNull { it.sequence } ?: 0L

    /**
     * Merges [post] into this feed. A [OneRingNotificationPost.threadKey]
     * matching an existing thread appends its message and refreshes that
     * thread's identity fields (title/icons/accent always take the newer
     * post's value; [OneRingNotificationPost.subtitle] and
     * [OneRingNotificationPost.timestampLabel] only overwrite when the newer
     * post actually supplies one) and bumps it to the newest [sequence] --
     * moving both the thread and its source group to the front of the feed.
     * A new [threadKey] creates a new one-message thread. Never drops data:
     * every message posted survives in [OneRingNotification.messages].
     */
    fun post(post: OneRingNotificationPost): NotificationFeed {
        val existing = threadsByKey[post.threadKey]
        val merged = if (existing != null) {
            existing.copy(
                sourceKey = post.sourceKey,
                sourceLabel = post.sourceLabel,
                sourceIcon = post.sourceIcon,
                icon = post.icon,
                accentColor = post.accentColor,
                title = post.title,
                subtitle = post.subtitle ?: existing.subtitle,
                messages = existing.messages + post.message,
                timestampLabel = post.timestampLabel ?: existing.timestampLabel,
                sequence = nextSequence,
            )
        } else {
            OneRingNotification(
                threadKey = post.threadKey,
                sourceKey = post.sourceKey,
                sourceLabel = post.sourceLabel,
                sourceIcon = post.sourceIcon,
                icon = post.icon,
                accentColor = post.accentColor,
                title = post.title,
                subtitle = post.subtitle,
                messages = listOf(post.message),
                timestampLabel = post.timestampLabel,
                sequence = nextSequence,
            )
        }
        return copy(threadsByKey = threadsByKey + (post.threadKey to merged), nextSequence = nextSequence + 1)
    }

    /** Removes [threadKey]'s thread entirely, if present. A no-op sequence number otherwise. */
    fun dismiss(threadKey: String): NotificationFeed =
        if (threadsByKey.containsKey(threadKey)) copy(threadsByKey = threadsByKey - threadKey) else this

    /** Drops every thread. [nextSequence] is preserved, so ordering stays monotonic across a clear. */
    fun clear(): NotificationFeed = copy(threadsByKey = emptyMap())

    /** All threads, newest-updated first. */
    fun threadsNewestFirst(): List<OneRingNotification> = threadsByKey.values.sortedByDescending { it.sequence }

    /**
     * Threads grouped by [OneRingNotificationPost.sourceKey]. Groups are
     * ordered by their newest thread's sequence (most-recently-updated
     * source first); within a group, threads are newest-first. A source with
     * zero remaining threads simply isn't present -- there's no separate
     * "empty group" state to clean up.
     */
    fun groupedBySource(): List<OneRingNotificationGroup> = threadsByKey.values
        .groupBy { it.sourceKey }
        .map { (sourceKey, threads) ->
            val sorted = threads.sortedByDescending { it.sequence }
            OneRingNotificationGroup(
                sourceKey = sourceKey,
                sourceLabel = sorted.first().sourceLabel,
                sourceIcon = sorted.first().sourceIcon,
                threads = sorted,
            )
        }
        .sortedByDescending { group -> group.threads.first().sequence }
}
