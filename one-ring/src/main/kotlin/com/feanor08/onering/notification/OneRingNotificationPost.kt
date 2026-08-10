package com.feanor08.onering.notification

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon

/**
 * One notification event, posted to [OneRingNotificationState.post]. The
 * library never interprets [sourceKey] -- consumers group by whatever it
 * means to them (an app, a device, a category); see
 * [OneRingNotificationGroup].
 *
 * @param threadKey identifies one ongoing conversation/notification. Posting
 *   again with the same [threadKey] appends [message] to that thread's
 *   existing [OneRingNotification.messages] instead of creating a new card --
 *   see docs/notifications.md's "same-thread grouping" section.
 * @param sourceKey groups threads for the feed's source headers (e.g.
 *   `"whatsapp"`, `"vehicle"`) -- entirely consumer-defined.
 * @param sourceLabel the human-readable label shown in that source's header.
 * @param sourceIcon the icon shown in that source's header, and preferred for
 *   the arrival animation if present (falling back to [icon]).
 * @param icon this notification's own icon (e.g. a per-sender avatar glyph).
 * @param accentColor this notification's accent -- its card border, icon,
 *   subtitle, dismiss affordance, and arrival border/icon all derive from
 *   this, never a library-wide color.
 * @param title the card's title (e.g. a sender's name).
 * @param subtitle optional secondary line; when a later post to the same
 *   thread omits this, the existing value is kept rather than cleared.
 * @param message the message appended to the thread on this post.
 * @param timestampLabel optional caller-formatted timestamp; like
 *   [subtitle], a later post omitting this keeps the existing value.
 */
@Immutable
public data class OneRingNotificationPost(
    public val threadKey: String,
    public val sourceKey: String,
    public val sourceLabel: String,
    public val sourceIcon: OneRingIcon,
    public val icon: OneRingIcon,
    public val accentColor: Color,
    public val title: String,
    public val subtitle: String? = null,
    public val message: String,
    public val timestampLabel: String? = null,
)
