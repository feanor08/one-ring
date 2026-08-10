package com.feanor08.onering.notification

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingMode
import com.feanor08.onering.OneRingState
import com.feanor08.onering.geometry.polarOffset
import com.feanor08.onering.rememberOneRingState
import org.junit.Rule
import org.junit.Test

/**
 * Integration coverage of [OneRingNotificationHost] actually mounted in
 * [OneRing]'s `coreOverlay` slot: automatic hide/restore as
 * [com.feanor08.onering.OneRingMode] changes, and -- the regression this
 * feature could most easily have introduced -- that ordinary main-ring
 * interaction (a press on a real wedge, not the core) is completely
 * unaffected by a `coreOverlay` being present at all. See
 * [com.feanor08.onering.internal.CoreOverlayGestureGateTest] for the
 * lower-level unit coverage of the gate itself.
 */
class OneRingCoreOverlayIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun pointInWedge(index: Int, count: Int, sizeDp: Int, radiusFraction: Float = 0.7f): Offset {
        val sweep = 360f / count
        val midDeg = -sweep / 2f + index * sweep
        val offset = polarOffset(sizeDp * radiusFraction, midDeg)
        return Offset(sizeDp / 2f + offset.x, sizeDp / 2f + offset.y)
    }

    private fun post(title: String) = OneRingNotificationPost(
        threadKey = "thread:$title",
        sourceKey = "demo",
        sourceLabel = "Demo",
        sourceIcon = OneRingIcon.None,
        icon = OneRingIcon.None,
        accentColor = Color(0xFF56C985),
        title = title,
        message = "hi",
    )

    /** See [OneRingNotificationHostTest.waitForArrivalToSettle] -- same real-delay caveat applies here. */
    private fun waitForArrivalToSettle() {
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithContentDescription("New notification:", substring = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Targets the card's own node by its exact description, not a substring
     * match on [title] -- the dismiss button's description ("Dismiss $title
     * notification") also contains [title], and `InvisibleToUser` is only
     * ever present on the specific node that set it, not automatically
     * mirrored onto every descendant's own semantics config. An ambiguous
     * match risks silently checking the wrong node.
     */
    private fun cardIsInvisibleToUser(title: String, sourceLabel: String, message: String): Boolean {
        val exactDescription = "$title. $sourceLabel. $message"
        val node = composeRule.onNodeWithContentDescription(exactDescription, useUnmergedTree = true)
            .fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.InvisibleToUser) != null
    }

    @Test
    fun openingAMenuHidesTheOverlayAndClosingRestoresItOnceSettled() {
        val menuItems = listOf(
            OneRingItem.Menu(
                id = "menu",
                label = "Menu",
                children = listOf(OneRingItem.Action(id = "child", label = "Child")),
            ),
            OneRingItem.Action(id = "b", label = "B"),
        )
        val sizeDp = 300
        lateinit var state: OneRingState
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            state = rememberOneRingState()
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = menuItems,
                onAction = {},
                state = state,
                modifier = Modifier.size(sizeDp.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.runOnIdle { notifications.post(post("Adarsh")) }
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        assert(!cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected the card visible to accessibility while idle" }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()

        assert(state.mode == OneRingMode.Submenu) { "expected Submenu mode, was ${state.mode}" }
        assert(cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected the card hidden from accessibility while a submenu is open" }

        composeRule.onNodeWithContentDescription("Close Menu").performClick()
        composeRule.waitForIdle()

        assert(state.mode == OneRingMode.Idle) { "expected Idle after closing, was ${state.mode}" }
        assert(!cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected the card visible again once the main ring settled" }
    }

    @Test
    fun enteringAMainSeekHidesTheOverlayAndExitingRestoresIt() {
        val seekItems = listOf(
            OneRingItem.Seek(id = "vol", label = "Volume", value = 0.5f, onValueChange = {}),
            OneRingItem.Action(id = "b", label = "B"),
        )
        val sizeDp = 300
        lateinit var state: OneRingState
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            state = rememberOneRingState()
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = seekItems,
                onAction = {},
                state = state,
                modifier = Modifier.size(sizeDp.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.runOnIdle { notifications.post(post("Adarsh")) }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        val point = pointInWedge(index = 0, count = seekItems.size, sizeDp = sizeDp)
        composeRule.onRoot().performTouchInput {
            down(point)
            up()
        }
        composeRule.waitForIdle()
        assert(state.mode == OneRingMode.Seek) { "expected Seek mode, was ${state.mode}" }
        assert(cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected the card hidden from accessibility during a main seek" }

        composeRule.onNodeWithContentDescription("Close Volume").performClick()
        composeRule.waitForIdle()

        assert(state.mode == OneRingMode.Idle) { "expected Idle after exiting the seek, was ${state.mode}" }
        assert(!cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected the card visible again after exiting the seek" }
    }

    @Test
    fun aCoreOverlayDoesNotChangeOrdinaryMainRingWedgeInteraction() {
        var lastAction: String? = null
        val items = listOf(
            OneRingItem.Action(id = "a", label = "A"),
            OneRingItem.Action(id = "b", label = "B"),
            OneRingItem.Action(id = "c", label = "C"),
        )
        val sizeDp = 300
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(sizeDp.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        val point = pointInWedge(index = 1, count = items.size, sizeDp = sizeDp)
        composeRule.onRoot().performTouchInput {
            down(point)
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == "b") {
            "expected an ordinary wedge press to still commit normally with a coreOverlay present, got $lastAction"
        }
    }

    @Test
    fun theOverlaysOwnDismissButtonWorksInsideARealRing() {
        val items = listOf(OneRingItem.Action(id = "a", label = "A"))
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = items,
                onAction = {},
                modifier = Modifier.size(300.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.runOnIdle { notifications.post(post("Adarsh")) }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Dismiss Adarsh notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()

        assert(notifications.notifications.isEmpty()) {
            "expected the overlay's own X to dismiss the card even inside a real OneRing"
        }
    }
}
