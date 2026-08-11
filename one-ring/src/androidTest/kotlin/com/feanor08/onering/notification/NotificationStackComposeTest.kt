package com.feanor08.onering.notification

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRingIcon
import org.junit.Rule
import org.junit.Test

/**
 * Compose-level coverage of the stacked-source presentation
 * ([NotificationGroupSlot], and [NotificationFeedList]'s per-group branch
 * into it) added by docs/notifications-plan.md -- observable content,
 * semantics, actions, and cardinality transitions, per that plan's section 16
 * test list. Most tests here assert only endpoint behavior (the same either
 * way whether the transition between endpoints is instant or animated); the
 * "Phase 4b" section at the bottom of this file additionally asserts
 * observable behavior *during* a real, running transition.
 *
 * All finders use `useUnmergedTree = true` for the same reason
 * [OneRingNotificationHostTest] does: a card's own contentDescription is set
 * directly on its own semantics node (not merged up from children), but the
 * small dismiss-button target needs to stay independently addressable rather
 * than being swallowed into the card's merged bounds.
 */
class NotificationStackComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun waitForArrivalToSettle() {
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithContentDescription("New notification:", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun whatsApp(threadKey: String, title: String, message: String) = OneRingNotificationPost(
        threadKey = threadKey,
        sourceKey = "whatsapp",
        sourceLabel = "WhatsApp",
        sourceIcon = OneRingIcon.None,
        icon = OneRingIcon.None,
        accentColor = Color(0xFF56C985),
        title = title,
        message = message,
    )

    private fun setUpHost(): OneRingNotificationState {
        lateinit var state: OneRingNotificationState
        composeRule.setContent {
            state = rememberOneRingNotificationState()
            OneRingNotificationHost(state = state, visible = true, modifier = Modifier.size(300.dp))
        }
        return state
    }

    /**
     * Asserts a node whose content description contains [substring] exists,
     * without requiring uniqueness -- a bare name like "Varsha" also matches
     * that same card's own "Dismiss Varsha notification" button, so a plain
     * `onNodeWithContentDescription(substring = true)` is ambiguous here (see
     * [OneRingCoreOverlayIntegrationTest.cardIsInvisibleToUser]'s doc for the
     * same pitfall). `onFirst()` sidesteps that since existence alone doesn't
     * care which of the matches it resolves to.
     */
    private fun assertCardVisible(substring: String) {
        composeRule.onAllNodesWithContentDescription(substring, substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    /** Posts [count] distinct WhatsApp threads, oldest first -- the last one posted ends up front (index 0). */
    private fun postStack(state: OneRingNotificationState, count: Int, messagesForFirst: Int = 1) {
        val names = listOf("Adarsh", "Varsha", "Family", "Priya", "Rohit", "Kiran", "Meera", "Dev", "Asha", "Ravi")
        repeat(count) { i ->
            composeRule.runOnIdle {
                state.post(whatsApp("whatsapp:${names[i]}", names[i], "message $i"))
            }
            waitForArrivalToSettle()
            composeRule.waitForIdle()
        }
        if (messagesForFirst > 1) {
            // Front-most thread is the last one posted -- pile extra messages onto it.
            val frontName = names[count - 1]
            repeat(messagesForFirst - 1) { i ->
                composeRule.runOnIdle {
                    state.post(whatsApp("whatsapp:$frontName", frontName, "extra ${i + 1}"))
                }
                waitForArrivalToSettle()
                composeRule.waitForIdle()
            }
        }
    }

    // ---------------------------------------------------------------------
    // Single-thread source stays a plain card (not a stack).
    // ---------------------------------------------------------------------

    @Test
    fun aSingleThreadSourceIsAnOrdinaryCardNotAStack() {
        val state = setUpHost()
        postStack(state, count = 1)

        composeRule.onNodeWithContentDescription("Adarsh. WhatsApp. message 0", useUnmergedTree = true).assertExists()
        // No stack label ("WhatsApp · N") is ever appended for a lone thread.
        composeRule.onAllNodesWithContentDescription("WhatsApp · 1", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    // ---------------------------------------------------------------------
    // Static 2/3/8-thread stacks.
    // ---------------------------------------------------------------------

    @Test
    fun aTwoThreadSourceShowsTheFrontCardWithASourceCountLabel() {
        val state = setUpHost()
        postStack(state, count = 2)

        // Front is the most-recently-posted thread ("Varsha").
        composeRule.onNodeWithContentDescription(
            "Varsha. WhatsApp. message 1. WhatsApp · 2",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun aThreeThreadSourceCollapsedShowsOnlyTheFrontThreadsSemantics() {
        val state = setUpHost()
        postStack(state, count = 3)

        composeRule.onNodeWithContentDescription(
            "Family. WhatsApp. message 2. WhatsApp · 3",
            useUnmergedTree = true,
        ).assertExists()
        // The two rear threads (Varsha, Adarsh) expose no semantics while collapsed.
        composeRule.onAllNodesWithContentDescription("Varsha", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun anEightThreadSourceCollapsedStillShowsOnlyTheFrontThread() {
        val state = setUpHost()
        postStack(state, count = 8)

        composeRule.onNodeWithContentDescription("WhatsApp · 8", substring = true, useUnmergedTree = true)
            .assertExists()
        // Only one thread's content is readable while collapsed, regardless of how many exist.
        composeRule.onAllNodesWithContentDescription(". WhatsApp.", substring = true, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    // ---------------------------------------------------------------------
    // Source count and per-thread message count are independent facts.
    // ---------------------------------------------------------------------

    @Test
    fun sourceCountAndFrontThreadMessageCountAreBothVisibleAndIndependent() {
        val state = setUpHost()
        postStack(state, count = 3, messagesForFirst = 5)

        // Front thread ("Family") has 5 accumulated messages; the source has 3 active threads.
        composeRule.onAllNodesWithContentDescription("WhatsApp · 5 messages", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
        composeRule.onAllNodesWithContentDescription("WhatsApp · 3", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    // ---------------------------------------------------------------------
    // Collapsed rear interaction disabled: no dismiss action on rear cards.
    // ---------------------------------------------------------------------

    @Test
    fun collapsedRearCardsHaveNoDismissAction() {
        val state = setUpHost()
        postStack(state, count = 3)

        composeRule.onAllNodesWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Dismiss Varsha notification", useUnmergedTree = true)
            .assertCountEquals(0)
        // The front card's own dismiss action is still present.
        composeRule.onNodeWithContentDescription("Dismiss Family notification", useUnmergedTree = true).assertExists()
    }

    // ---------------------------------------------------------------------
    // Tap to expand / tap to collapse; one expanded source at a time.
    // ---------------------------------------------------------------------

    @Test
    fun tappingTheFrontCardExpandsAndRevealsAllThreadsSemantics() {
        val state = setUpHost()
        postStack(state, count = 3)

        composeRule.onNodeWithContentDescription(
            "Family. WhatsApp. message 2. WhatsApp · 3",
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()

        assertCardVisible("Varsha")
        assertCardVisible("Adarsh")
        composeRule.onAllNodesWithContentDescription("Dismiss Varsha notification", useUnmergedTree = true)
            .onFirst().assertExists()
        composeRule.onAllNodesWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .onFirst().assertExists()
    }

    @Test
    fun tappingAnExpandedFrontCardAgainCollapsesIt() {
        val state = setUpHost()
        postStack(state, count = 2)

        val frontDescription = "Varsha. WhatsApp. message 1. WhatsApp · 2"
        composeRule.onNodeWithContentDescription(frontDescription, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertCardVisible("Adarsh")

        composeRule.onNodeWithContentDescription(frontDescription, useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun onlyOneSourceIsExpandedAtATime() {
        val state = setUpHost()
        composeRule.runOnIdle {
            state.post(whatsApp("whatsapp:adarsh", "Adarsh", "hi"))
            state.post(whatsApp("whatsapp:varsha", "Varsha", "hey"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            state.post(OneRingNotificationPost(
                threadKey = "software:update",
                sourceKey = "software",
                sourceLabel = "Software",
                sourceIcon = OneRingIcon.None,
                icon = OneRingIcon.None,
                accentColor = Color(0xFF5B8FF5),
                title = "Software Update",
                message = "v2.4 ready",
            ))
            state.post(OneRingNotificationPost(
                threadKey = "software:security",
                sourceKey = "software",
                sourceLabel = "Software",
                sourceIcon = OneRingIcon.None,
                icon = OneRingIcon.None,
                accentColor = Color(0xFF5B8FF5),
                title = "Security Patch",
                message = "patch ready",
            ))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        // Expand the WhatsApp group.
        composeRule.onNodeWithContentDescription(
            "Varsha. WhatsApp. hey. WhatsApp · 2",
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        assertCardVisible("Adarsh")

        // Expanding the Software group must collapse WhatsApp back.
        composeRule.onNodeWithContentDescription(
            "Security Patch. Software. patch ready. Software · 2",
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        assertCardVisible("Software Update")
    }

    // ---------------------------------------------------------------------
    // Dismissal / cardinality transitions.
    // ---------------------------------------------------------------------

    @Test
    fun frontDismissalPromotesTheNextThread() {
        val state = setUpHost()
        postStack(state, count = 3)

        // Front is "Family"; dismissing it should promote "Varsha" to front.
        composeRule.onNodeWithContentDescription("Dismiss Family notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(
            "Varsha. WhatsApp. message 1. WhatsApp · 2",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun expandedNonFrontDismissalRemovesOnlyThatThread() {
        val state = setUpHost()
        postStack(state, count = 3)

        composeRule.onNodeWithContentDescription(
            "Family. WhatsApp. message 2. WhatsApp · 3",
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Dismiss Varsha notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // Family (front) and Adarsh (still-expanded rear) remain; Varsha is gone.
        composeRule.onAllNodesWithContentDescription("Varsha", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        assertCardVisible("Adarsh")
        assertCardVisible("Family")
    }

    @Test
    fun droppingToOneThreadBecomesAnOrdinaryCard() {
        val state = setUpHost()
        postStack(state, count = 2)

        composeRule.onNodeWithContentDescription("Dismiss Varsha notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // Down to one thread: no stack label, plain card semantics.
        composeRule.onNodeWithContentDescription("Adarsh. WhatsApp. message 0", useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithContentDescription("WhatsApp · 1", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
    }

    @Test
    fun droppingToZeroThreadsRemovesTheSource() {
        val state = setUpHost()
        postStack(state, count = 1)

        composeRule.onNodeWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        assert(state.notifications.isEmpty())
    }

    @Test
    fun aStaleExpandedSourceThatShrinksToOneThreadDoesNotReExpandWhenItGrowsAgain() {
        val state = setUpHost()
        postStack(state, count = 2)

        // Expand, then dismiss the rear thread so this source drops to one thread.
        composeRule.onNodeWithContentDescription(
            "Varsha. WhatsApp. message 1. WhatsApp · 2",
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        // A new thread arrives on the same source -- it must come back collapsed, not re-expanded.
        composeRule.runOnIdle {
            state.post(whatsApp("whatsapp:priya", "Priya", "hello"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Varsha", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        assertCardVisible("Priya")
    }

    // ---------------------------------------------------------------------
    // Phase 4b: observable animation-safety behavior (docs/notifications-plan.md
    // section 18, "add observable animation-safety tests without asserting
    // implementation internals"). These don't assert progress/easing/z-index
    // -- only content, semantics, and gating at a real mid-transition point.
    // ---------------------------------------------------------------------

    /**
     * Whether [NotificationGroupSlot]'s own reduced-motion check would return
     * true on this device -- mirrors
     * [com.feanor08.onering.internal.rememberAnimationsEnabled] exactly, so
     * the test below can assert the *correct* behavior for either branch
     * instead of assuming a particular device/CI animator-scale setting.
     */
    private fun deviceAnimationsEnabled(): Boolean {
        val resolver = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .contentResolver
        return android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }

    /**
     * Secondary (rear) interaction and semantics must stay gated for as long
     * as the expand transition is actually running -- "enable it only when
     * expansion is at its final endpoint and the transition is not running"
     * (docs/notifications-plan.md section 11). On a device with animations
     * enabled, a partial time-advance must still show the rear thread gated;
     * with animations disabled (reduced motion), the same partial advance
     * must already show it fully settled, since reduced motion jumps
     * straight to the endpoint with no intermediate frames.
     */
    @Test
    fun secondaryInteractionStaysGatedForAsLongAsTheExpandTransitionIsRunning() {
        val state = setUpHost()
        postStack(state, count = 2)

        val animationsEnabled = deviceAnimationsEnabled()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription(
            "Varsha. WhatsApp. message 1. WhatsApp · 2",
            useUnmergedTree = true,
        ).performClick()
        // Partway into the default 260ms stack-expand tween -- short of a
        // full frame's settle, but enough for the transition to have started
        // if it's running at all.
        composeRule.mainClock.advanceTimeBy(80L)

        if (animationsEnabled) {
            composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
                .assertCountEquals(0)
            composeRule.onAllNodesWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
                .assertCountEquals(0)
        } else {
            assertCardVisible("Adarsh")
        }

        // Let it fully settle either way, then confirm the endpoint is reached.
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertCardVisible("Adarsh")
        composeRule.onAllNodesWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .onFirst().assertExists()
    }

    /**
     * A rapid re-tap mid-transition (expand, then immediately collapse again
     * before the first animation would have settled) must still land on the
     * correct final state -- proving `updateTransition`'s own interruption
     * handling (retargeting mid-flight) isn't defeated by anything in this
     * slot's own state plumbing. Only the observable end state is asserted,
     * never the interruption mechanics themselves.
     */
    @Test
    fun rapidlyToggingExpansionMidTransitionEndsInTheCorrectFinalState() {
        val state = setUpHost()
        postStack(state, count = 2)
        val frontDescription = "Varsha. WhatsApp. message 1. WhatsApp · 2"

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription(frontDescription, useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(80L)
        // Collapse again before the first transition would have settled.
        composeRule.onNodeWithContentDescription(frontDescription, useUnmergedTree = true).performClick()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        // Net effect of expand-then-collapse is collapsed: no rear semantics.
        composeRule.onAllNodesWithContentDescription("Adarsh", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        composeRule.onNodeWithContentDescription(frontDescription, useUnmergedTree = true).assertExists()
    }
}
