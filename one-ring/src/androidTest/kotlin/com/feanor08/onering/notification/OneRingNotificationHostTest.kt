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
import androidx.compose.ui.test.hasText
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

    /** See [NotificationStackComposeTest.assertCardVisible] -- avoids the same card-vs-dismiss-button substring ambiguity. */
    private fun assertCardVisible(substring: String) {
        composeRule.onAllNodesWithContentDescription(substring, substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

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

        // The oldest group's (whatsapp) card is still reachable by scrolling the single feed.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Adarsh"))
        composeRule.onNodeWithContentDescription("Adarsh. WhatsApp. hi").assertExists()
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

    /**
     * The regression this session fixed: a distinct new thread's card must
     * never be exposed before its own arrival splash has started -- the
     * order must be strictly splash-then-message, never
     * message-then-splash-then-message.
     */
    @Test
    fun aNewThreadsCardIsNeverExposedBeforeItsArrivalSplashStarts() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        // An existing, already-settled notification.
        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "hi"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()

        // A brand new, distinct thread arrives.
        composeRule.runOnIdle {
            state.post(post("software:update", "software", "Software", "Software Update", "v2.4 ready"))
        }

        // Matches on the card's own message text, not its title -- the
        // arrival's own live-region banner is "New notification: Software
        // Update", which would itself satisfy a substring match on the title.
        val cardMarker = "v2.4 ready"

        // Checked BEFORE waiting for the splash: catches the window between
        // post() and the splash actually appearing, where the old (buggy)
        // implementation let the new card flash into view.
        composeRule.onAllNodesWithContentDescription(cardMarker, substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithContentDescription("New notification: Software Update", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Still not exposed while the splash is actually up.
        composeRule.onAllNodesWithContentDescription(cardMarker, substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription(cardMarker, substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    /** Same ordering guarantee, for a same-thread update ("2 messages" must never appear before its splash). */
    @Test
    fun aSameThreadUpdateIsNeverExposedBeforeItsArrivalSplash() {
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

        composeRule.onAllNodesWithContentDescription("2 messages", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithContentDescription("New notification: Adarsh", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription("2 messages", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("2 messages", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    /**
     * A dismiss on an already-revealed card must still work immediately,
     * proving the arrival-ordering fix's `safeGroups` snapshot never goes
     * stale for unrelated changes (only pinned while an arrival is actually
     * in flight -- see [OneRingNotificationHost]'s "Arrival ordering" doc).
     */
    @Test
    fun dismissingAnUnrelatedCardWorksImmediatelyAndIsNotBlockedByArrivalPinning() {
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

        composeRule.onNodeWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        assert(state.notifications.isEmpty())
    }

    // ---------------------------------------------------------------------
    // Stack-aware extensions to arrival-safe update ordering
    // (docs/notifications-plan.md section 16, "Extend host tests for").
    // ---------------------------------------------------------------------

    /** A message arriving on an already-expanded source's front thread must still obey splash-before-message ordering. */
    @Test
    fun anUpdateToAnExpandedSourcesFrontThreadIsNeverExposedBeforeItsArrivalSplash() {
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
        composeRule.runOnIdle {
            state.post(post("whatsapp:varsha", "whatsapp", "WhatsApp", "Varsha", "hey"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        // Expand the stack (front is Varsha).
        composeRule.onNodeWithContentDescription("Varsha. WhatsApp. hey. WhatsApp · 2", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertCardVisible("Adarsh")

        // A second message lands on the expanded front thread (Varsha).
        composeRule.runOnIdle {
            state.post(post("whatsapp:varsha", "whatsapp", "WhatsApp", "Varsha", "still there?"))
        }
        composeRule.onAllNodesWithContentDescription("still there?", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("still there?", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
        // Still expanded afterwards -- the update must not have reset expansion.
        assertCardVisible("Adarsh")
    }

    /** A new post to an unrelated source must not disturb another source's expansion state. */
    @Test
    fun anUpdateToAnUnrelatedSourceDoesNotAffectAnotherSourcesExpansion() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "hi"))
            state.post(post("whatsapp:varsha", "whatsapp", "WhatsApp", "Varsha", "hey"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Varsha. WhatsApp. hey. WhatsApp · 2", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertCardVisible("Adarsh")

        composeRule.runOnIdle {
            state.post(post("software:update", "software", "Software", "Software Update", "v2.4 ready"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        // The WhatsApp stack is still expanded; the unrelated Software card just appears alongside it.
        assertCardVisible("Adarsh")
        assertCardVisible("Software Update")
    }

    /** A source-count label ("WhatsApp · 2") must obey the same splash-before-reveal ordering as any other content. */
    @Test
    fun aSourceBecomingAStackNeverExposesItsCountLabelBeforeTheArrivalSplash() {
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

        // A second thread arrives, turning this source into a 2-thread stack.
        composeRule.runOnIdle {
            state.post(post("whatsapp:varsha", "whatsapp", "WhatsApp", "Varsha", "hey"))
        }
        composeRule.onAllNodesWithContentDescription("WhatsApp · 2", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)

        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("WhatsApp · 2", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    /** One [androidx.compose.foundation.lazy.LazyColumn] carries both plain and stacked sources -- there is no separate scroll container per group. */
    @Test
    fun oneScrollSurfaceReachesBothPlainAndStackedSources() {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(200.dp))
        }

        composeRule.runOnIdle {
            state.post(post("whatsapp:adarsh", "whatsapp", "WhatsApp", "Adarsh", "hi"))
            state.post(post("whatsapp:varsha", "whatsapp", "WhatsApp", "Varsha", "hey"))
            state.post(post("software:update", "software", "Software", "Software Update", "v2.4 ready"))
            state.post(post("cloud:sync", "cloud", "Cloud", "Cloud Sync", "backed up"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        // The oldest group (the WhatsApp stack) is still reachable by scrolling the one feed.
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Varsha"))
        assertCardVisible("Varsha")
    }
}
