package com.feanor08.onering.notification

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.feanor08.onering.OneRing
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingMode
import com.feanor08.onering.OneRingState
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.internal.resolveRing
import com.feanor08.onering.internal.submenuChildSpans
import com.feanor08.onering.pointAtPolar
import com.feanor08.onering.pointInWedge
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
 * lower-level unit coverage of the ring's own half of the contract
 * (`isInsideActiveCoreOverlay`); this file covers the *other* half --
 * [OneRingNotificationHost]'s own composition-level interactivity gating --
 * end to end, with real touch input.
 */
class OneRingCoreOverlayIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

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
     * The *real* angular spans of [parentId]'s submenu children, computed via
     * [resolveRing]/[submenuChildSpans] -- the exact same internal functions
     * `RingInteractionController` itself calls -- rather than re-deriving the
     * geometry by hand in the test. A hand-rolled version previously omitted
     * `resolved.submenuGapDeg` (and the main ring's own sector gap, folded
     * into `resolved.mainSpans`), both of which shrink each child's sweep
     * slightly and shift its midpoint versus a naive gap-free calculation --
     * a real, previously undiagnosed defect that silently landed test taps
     * off by enough to miss the wedge. Calling the library's own resolver
     * makes this test immune to that whole class of drift by construction.
     */
    private fun realSubmenuChildSpans(items: List<OneRingItem>, parentId: String): List<AngularSpan> {
        val dimensions = OneRingDefaults.dimensions()
        val rootSize = composeRule.onRoot().fetchSemanticsNode().size
        val sizePx = Size(rootSize.width.toFloat(), rootSize.height.toFloat())
        val resolved = resolveRing(sizePx, composeRule.density, dimensions, items.map { it.weight })
        val parentIndex = items.indexOfFirst { it.id == parentId }
        val parentSpan = resolved.mainSpans[parentIndex]
        val parent = items[parentIndex] as OneRingItem.Menu
        return submenuChildSpans(resolved, dimensions, parentSpan, parent.children)
    }

    /**
     * Targets the card's own node by its exact description, not a substring
     * match on [title] -- the dismiss button's description ("Dismiss $title
     * notification") also contains [title], and an ambiguous match risks
     * silently checking the wrong node.
     *
     * `OneRingNotificationHost` sets `invisibleToUser()` on the *feed's
     * outer `Box`*, not on each card individually (see its "coreOverlay
     * interactivity" doc) -- that's how real accessibility services treat
     * it too: a subtree under an invisible ancestor is unreachable, without
     * every descendant needing to repeat the property. In the unmerged
     * semantics tree the card's own node config never carries the property
     * itself, so this walks up from the card to its ancestors looking for
     * the node that actually declared it, instead of only checking the
     * card's own local config.
     */
    private fun cardIsInvisibleToUser(title: String, sourceLabel: String, message: String): Boolean {
        val exactDescription = "$title. $sourceLabel. $message"
        var node: SemanticsNode? = composeRule.onNodeWithContentDescription(exactDescription, useUnmergedTree = true)
            .fetchSemanticsNode()
        while (node != null) {
            if (node.config.getOrNull(SemanticsProperties.InvisibleToUser) != null) return true
            node = node.parent
        }
        return false
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
        lateinit var state: OneRingState
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            state = rememberOneRingState()
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = menuItems,
                onAction = {},
                state = state,
                modifier = Modifier.size(300.dp),
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
        lateinit var state: OneRingState
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            state = rememberOneRingState()
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = seekItems,
                onAction = {},
                state = state,
                modifier = Modifier.size(300.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.runOnIdle { notifications.post(post("Adarsh")) }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = seekItems.size))
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
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(300.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 1, count = items.size))
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

    /**
     * With [OneRingNotificationHost] populated and visible (`Idle`), the
     * overlay's own scroll/X/swipe must all keep working normally while
     * sitting above the ring -- proving the composition-level interactivity
     * gate (see that composable's "coreOverlay interactivity" doc) didn't
     * simply put the overlay permanently behind the ring, or break it in the
     * one mode it's supposed to be fully usable in.
     */
    @Test
    fun theOverlayRemainsFullyInteractiveWhileIdle() {
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

        composeRule.runOnIdle {
            notifications.post(post("Adarsh"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            notifications.post(post("Varsha"))
        }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        // X-dismiss still works with a coreOverlay-hosting ring around it.
        composeRule.onNodeWithContentDescription("Dismiss Varsha notification", useUnmergedTree = true)
            .performClick()
        composeRule.waitForIdle()
        assert(notifications.notifications.none { it.title == "Varsha" }) {
            "expected X-dismiss to remove Varsha while idle"
        }

        // Swipe-left dismiss still works too. Matches the card's exact
        // description, not a substring on "Adarsh" -- its own dismiss
        // button's description ("Dismiss Adarsh notification") also
        // contains that substring and would make the match ambiguous.
        composeRule.onNodeWithContentDescription("Adarsh. Demo. hi", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assert(notifications.notifications.isEmpty()) {
            "expected swipe-left dismiss to remove Adarsh while idle, had ${notifications.notifications}"
        }
    }

    /**
     * `coreOverlayRadius` (0.43 of base radius, by default) sits *inside* the
     * submenu band (`submenuInner` 0.28 .. `submenuOuter` 0.512), by design --
     * see docs/notifications.md. That means a real submenu child's hit region
     * can extend into the same radial band the (hidden) notification overlay
     * occupies. This proves a press there still reaches the submenu child
     * normally: [OneRingNotificationHost]'s composition-level interactivity
     * gate must only ever affect the *overlay's own* content, never the ring
     * underneath it.
     */
    @Test
    fun pressingASubmenuChildInsideTheCoreOverlayRadiusStillCommitsWhileNotificationsAreHidden() {
        val menuItems = listOf(
            OneRingItem.Menu(
                id = "menu",
                label = "Menu",
                children = listOf(
                    OneRingItem.Action(id = "child0", label = "Child0"),
                    OneRingItem.Action(id = "child1", label = "Child1"),
                    OneRingItem.Action(id = "child2", label = "Child2"),
                ),
            ),
            OneRingItem.Action(id = "b", label = "B"),
        )
        var lastAction: String? = null
        lateinit var state: OneRingState
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            state = rememberOneRingState()
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = menuItems,
                onAction = { lastAction = it.id },
                state = state,
                modifier = Modifier.size(300.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.runOnIdle { notifications.post(post("Adarsh")) }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        assert(state.mode == OneRingMode.Submenu) { "expected Submenu mode, was ${state.mode}" }
        assert(cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected notifications hidden while the submenu is open" }

        // A point inside the submenu band AND inside the default
        // coreOverlayRadius (~0.43 * baseRadius) -- 0.35 * baseRadius sits
        // between submenuInner (0.28) and coreOverlayRadius (0.43).
        val childMidDeg = realSubmenuChildSpans(menuItems, "menu")[0].midDeg

        composeRule.onRoot().performTouchInput {
            down(pointAtPolar(0.35f, childMidDeg))
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == "child0") {
            "expected the submenu child inside the coreOverlay radius to commit normally, got $lastAction"
        }
        assert(state.path.isEmpty()) { "expected the submenu to close after the child commit, was ${state.path}" }
    }

    /**
     * Same idea as the submenu-child test above, but for a submenu *seek*:
     * dragging inside the same overlapping radial band must still adjust the
     * seek's value, proving the (hidden) notification overlay never claims
     * that drag for itself.
     */
    @Test
    fun draggingASubmenuSeekInsideTheCoreOverlayRadiusStillWorksWhileNotificationsAreHidden() {
        var seekValue = 0.2f
        val menuItems = listOf(
            OneRingItem.Menu(
                id = "menu",
                label = "Menu",
                children = listOf(
                    OneRingItem.Seek(id = "vol", label = "Volume", value = seekValue, onValueChange = { seekValue = it }),
                ),
            ),
            OneRingItem.Action(id = "b", label = "B"),
        )
        lateinit var state: OneRingState
        lateinit var notifications: OneRingNotificationState

        composeRule.setContent {
            state = rememberOneRingState()
            notifications = rememberOneRingNotificationState()
            OneRing(
                items = menuItems,
                onAction = {},
                state = state,
                modifier = Modifier.size(300.dp),
                coreOverlay = { OneRingNotificationHost(state = notifications) },
            )
        }

        composeRule.runOnIdle { notifications.post(post("Adarsh")) }
        waitForArrivalToSettle()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()

        val trackMidDeg = realSubmenuChildSpans(menuItems, "menu")[0].midDeg

        // Enter the submenu seek with a real tap on the child's own wedge --
        // not a semantics performClick(), which on Android dispatches an
        // actual coordinate-based tap via performTouchInput rather than
        // invoking the SemanticsActions.OnClick lambda directly. Real touch
        // input is also what every other interaction test in this module
        // already uses to commit a wedge, so this keeps the same one
        // mechanism throughout instead of introducing a second one.
        composeRule.onRoot().performTouchInput {
            val commitPoint = pointAtPolar(0.35f, trackMidDeg)
            down(commitPoint)
            up()
        }
        composeRule.waitForIdle()
        assert(state.mode == OneRingMode.Seek) { "expected Seek mode after committing the submenu seek, was ${state.mode}" }
        assert(cardIsInvisibleToUser("Adarsh", "Demo", "hi")) { "expected notifications hidden during a submenu seek" }
        // 180 degrees away from the seek's own wedge, safely inside the
        // shared submenu/coreOverlay-radius band, and free of the wedge's own
        // excluded region.
        val dragDeg = trackMidDeg + 180f

        composeRule.onRoot().performTouchInput {
            val dragPoint = pointAtPolar(0.35f, dragDeg)
            down(dragPoint)
            moveTo(dragPoint)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assert(seekValue != 0.2f) {
            "expected the drag inside the coreOverlay radius to change the seek value, stayed at $seekValue"
        }
    }
}
