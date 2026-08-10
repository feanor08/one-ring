package com.feanor08.onering.notification

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingIcon
import org.junit.Rule
import org.junit.Test

/**
 * Compose-level coverage of [OneRingNotificationHost] itself (rendering,
 * same-thread merging, dismiss via X and swipe, multi-source scrolling, and
 * the arrival overlay actually appearing) using the non-extension overload
 * with an explicit [visible] flag -- no [com.feanor08.onering.OneRing]
 * involved. Ring-integration behavior (hide-on-submenu, gesture arbitration)
 * is covered separately in [OneRingCoreOverlayIntegrationTest].
 *
 * All finders use `useUnmergedTree = true`: the card's own semantics node
 * merges its dismiss button's description upward by default (Compose's
 * standard `mergeDescendants` behavior), which would otherwise make
 * `onNodeWithContentDescription("Dismiss ... notification")` resolve to the
 * *card's* bounds rather than the small X target -- an unmerged tree keeps
 * each node (and its own click target) independently addressable.
 */
class OneRingNotificationHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * `waitForIdle()` alone does not reliably wait out the arrival overlay's
     * real (non-frame-clock) `delay()`-based coroutine timers -- it only
     * blocks until Espresso's own idling resources settle, which does not
     * track arbitrary suspended coroutines. Polling for the arrival's own
     * live-region node to disappear is the deterministic sync point instead.
     */
    private fun waitForArrivalToSettle() {
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithContentDescription("New notification:", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun post(threadKey: String, sourceKey: String, sourceLabel: String, title: String, message: String) =
        OneRingNotificationPost(
            threadKey = threadKey,
            sourceKey = sourceKey,
            sourceLabel = sourceLabel,
            sourceIcon = OneRingIcon.None,
            icon = OneRingIcon.None,
            accentColor = Color(0xFF56C985),
            title = title,
            message = message,
        )

    @Test
    fun aPostedNotificationRendersItsTitleAndMessage() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "I'll reach in 10 minutes."))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    @Test
    fun aSecondPostToTheSameThreadShowsAMessageCountAndBothMessages() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "I'll reach in 10 minutes."))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "I'm outside."))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        val card = composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst()
        card.assertExists()
        val node = card.fetchSemanticsNode()
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
        assert(description.contains("2 messages")) { "expected a '2 messages' subtitle, got: $description" }
        assert(description.contains("I'll reach in 10 minutes.")) {
            "expected the first message present, got: $description"
        }
        assert(description.contains("I'm outside.")) { "expected the second message present, got: $description" }
    }

    @Test
    fun tappingTheDismissButtonRemovesTheCard() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "hi"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()

        composeRule.onNodeWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        assert(state.notifications.isEmpty()) { "expected the thread to be gone from state too" }
    }

    @Test
    fun swipingACardLeftPastTheThresholdDismissesIt() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "hi"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        val card = composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst()
        card.performTouchInput {
            // Interpolated moves (not one jump) -- the gesture's directional
            // lock and offset tracking both need multiple incremental events,
            // exactly like a real drag would produce.
            swipe(start = center, end = Offset(center.x - width * 0.6f, center.y), durationMillis = 300)
        }
        composeRule.waitForIdle()

        assert(state.notifications.isEmpty()) { "expected a leftward swipe past threshold to dismiss the thread" }
    }

    @Test
    fun aRightwardSwipeDoesNotDismiss() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "hi"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        val card = composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst()
        card.performTouchInput {
            swipe(start = center, end = Offset(center.x + width * 0.4f, center.y), durationMillis = 300)
        }
        composeRule.waitForIdle()

        assert(state.notifications.isNotEmpty()) { "expected a rightward swipe to never dismiss" }
    }

    @Test
    fun multipleSourceGroupsAreAllReachableByScrolling() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(200.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:a", "whatsapp", "WhatsApp", "Adarsh", "hi"))
            state.post(post("software:update", "software", "Software", "Software Update", "v2.4 ready"))
            state.post(post("cloud:sync", "cloud", "Cloud", "Cloud Sync", "backed up"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        // The oldest group (whatsapp) is still reachable by scrolling the single feed.
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("WhatsApp, 1 active")),
        )
        composeRule.onNodeWithContentDescription("WhatsApp, 1 active").assertExists()
    }

    @Test
    fun theArrivalOverlayAppearsForANewPostAndTheFeedIsRevealedAfterwards() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "I'll reach in 10 minutes."))
        }

        // Polls without draining to full idle, so it can observe the arrival
        // mid-flight instead of only after it has already finished.
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithContentDescription("New notification: Adarsh", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Now let it run to completion and confirm the feed underneath is shown.
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("New notification: Adarsh", substring = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }
}
