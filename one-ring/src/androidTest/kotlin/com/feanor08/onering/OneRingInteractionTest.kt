package com.feanor08.onering

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.SubmenuLayout
import com.feanor08.onering.geometry.polarOffset
import org.junit.Rule
import org.junit.Test

/**
 * Compose-level (Level 2) coverage of the press-scrub-release flow, exercising
 * the real [RingInteractionController] wiring rather than the pure animation
 * state directly -- see docs/PLAN.md sections 17.3 and 18.
 */
class OneRingInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = listOf(
        OneRingItem.Action(id = "a", label = "A"),
        OneRingItem.Action(id = "b", label = "B"),
        OneRingItem.Action(id = "c", label = "C"),
    )

    /** A point inside item index [index]'s wedge, in a canvas of side [sizeDp]. */
    private fun pointInWedge(index: Int, count: Int, sizeDp: Int): Offset {
        val sweep = 360f / count
        val midDeg = -sweep / 2f + index * sweep
        val radiusFraction = 0.7f // inside the main band's mid area
        val offset = polarOffset(sizeDp * radiusFraction, midDeg)
        return Offset(sizeDp / 2f + offset.x, sizeDp / 2f + offset.y)
    }

    @Test fun releaseOverAnActionItemCommitsItAndClearsHighlight() {
        var lastAction: String? = null
        val sizeDp = 300

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(sizeDp.dp),
            )
        }

        val point = pointInWedge(index = 1, count = items.size, sizeDp = sizeDp)
        composeRule.onRoot().performTouchInput {
            down(point)
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == "b") { "expected commit on item b, got $lastAction" }
    }

    @Test fun releaseOutsideTheRingCancelsWithoutFiringAnAction() {
        var lastAction: String? = null
        val sizeDp = 300

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(sizeDp.dp),
            )
        }

        composeRule.onRoot().performTouchInput {
            down(Offset(sizeDp / 2f, sizeDp / 2f)) // dead center, inside the empty core
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == null) { "expected no action from a center press, got $lastAction" }
    }

    @Test fun scrubbingBetweenWedgesUpdatesTheHighlightedState() {
        val sizeDp = 300
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = items, onAction = {}, state = state, modifier = Modifier.size(sizeDp.dp))
        }

        val first = pointInWedge(index = 0, count = items.size, sizeDp = sizeDp)
        val second = pointInWedge(index = 2, count = items.size, sizeDp = sizeDp)

        composeRule.onRoot().performTouchInput {
            down(first)
        }
        composeRule.waitForIdle()
        assert(state.highlightedId == "a") { "expected highlight on a, was ${state.highlightedId}" }

        composeRule.onRoot().performTouchInput {
            moveTo(second)
        }
        composeRule.waitForIdle()
        assert(state.highlightedId == "c") { "expected highlight to move to c, was ${state.highlightedId}" }

        composeRule.onRoot().performTouchInput { up() }
    }

    @Test fun committingAMenuItemEntersSubmenuModeAndPressingTheParentWedgeAgainCloses() {
        val menuItems = listOf(
            OneRingItem.Menu(
                id = "menu",
                label = "Menu",
                children = listOf(OneRingItem.Action(id = "child", label = "Child")),
            ),
            OneRingItem.Action(id = "b", label = "B"),
            OneRingItem.Action(id = "c", label = "C"),
        )
        val sizeDp = 300
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = menuItems, onAction = {}, state = state, modifier = Modifier.size(sizeDp.dp))
        }

        val point = pointInWedge(index = 0, count = menuItems.size, sizeDp = sizeDp)

        composeRule.onRoot().performTouchInput {
            down(point)
            up()
        }
        composeRule.waitForIdle()

        assert(state.path == listOf("menu")) { "expected path=[menu], was ${state.path}" }
        assert(state.mode == OneRingMode.Submenu) { "expected Submenu mode, was ${state.mode}" }

        // The parent wedge has grown to the selected radius and is now the
        // close control; pressing the same point again closes it.
        composeRule.onRoot().performTouchInput {
            down(point)
            up()
        }
        composeRule.waitForIdle()

        assert(state.path.isEmpty()) { "expected empty path after closing, was ${state.path}" }
        assert(state.mode == OneRingMode.Idle) { "expected Idle after closing, was ${state.mode}" }
    }

    @Test fun selectingASubmenuChildFiresOnActionAndClosesBackToIdle() {
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
            OneRingItem.Action(id = "c", label = "C"),
        )
        val sizeDp = 300
        lateinit var state: OneRingState
        var lastAction: String? = null

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(
                items = menuItems,
                onAction = { lastAction = it.id },
                state = state,
                modifier = Modifier.size(sizeDp.dp),
            )
        }

        val parentPoint = pointInWedge(index = 0, count = menuItems.size, sizeDp = sizeDp)
        composeRule.onRoot().performTouchInput {
            down(parentPoint)
            up()
        }
        composeRule.waitForIdle()
        assert(state.path == listOf("menu")) { "expected path=[menu], was ${state.path}" }

        // Compute the first child's wedge midpoint the same way the library
        // does (SubmenuLayout, the same defaults OneRing itself uses).
        val parentSweep = 360f / menuItems.size
        val parentSpan = AngularSpan(-parentSweep / 2f, parentSweep)
        val defaults = OneRingDefaults.dimensions()
        val childSpans = SubmenuLayout.spans(
            parent = parentSpan,
            childCount = 3,
            maxChildSweepDeg = defaults.maxSubmenuItemSweepDeg,
            alignment = defaults.submenuAlignment,
        )
        val childMidDeg = childSpans[0].midDeg
        val submenuMidRadiusFraction = (0.280f + 0.512f) / 2f // OneRingDefaults submenuInner/submenuOuter
        val offset = polarOffset(sizeDp * submenuMidRadiusFraction, childMidDeg)
        val childPoint = Offset(sizeDp / 2f + offset.x, sizeDp / 2f + offset.y)

        composeRule.onRoot().performTouchInput {
            down(childPoint)
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == "child0") { "expected commit on child0, got $lastAction" }
        assert(state.path.isEmpty()) { "expected the submenu to close after a child commit, was ${state.path}" }
        assert(state.mode == OneRingMode.Idle) { "expected Idle after child commit, was ${state.mode}" }
    }

    @Test fun talkBackSemanticClickCommitsAnActionWithoutAnyPointerGesture() {
        var lastAction: String? = null
        val sizeDp = 300

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(sizeDp.dp),
            )
        }

        // No touch input at all -- proves the semantics layer is a complete,
        // independent input path, not decoration on top of the pointer flow.
        composeRule.onNodeWithContentDescription("B").performClick()
        composeRule.waitForIdle()

        assert(lastAction == "b") { "expected the semantic click on B to commit item b, got $lastAction" }
    }

    @Test fun talkBackCanOpenAndCloseAMenuWithoutAnyPointerGesture() {
        val menuItems = listOf(
            OneRingItem.Menu(
                id = "menu",
                label = "Menu",
                children = listOf(OneRingItem.Action(id = "child", label = "Child")),
            ),
            OneRingItem.Action(id = "b", label = "B"),
        )
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = menuItems, onAction = {}, state = state, modifier = Modifier.size(300.dp))
        }

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.waitForIdle()
        assert(state.path == listOf("menu")) { "expected the semantic click to open the menu, path=${state.path}" }

        composeRule.onNodeWithContentDescription("Close Menu").performClick()
        composeRule.waitForIdle()
        assert(state.path.isEmpty()) { "expected the semantic click on the close node to close it, path=${state.path}" }
    }

    @Test fun hapticsFireOnHighlightChangeAndOnCommitButNotContinuouslyWithinTheSameWedge() {
        val events = mutableListOf<OneRingHapticEvent>()
        val sizeDp = 300

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = {},
                modifier = Modifier.size(sizeDp.dp),
                haptics = OneRingHaptics { events += it },
            )
        }

        val point = pointInWedge(index = 0, count = items.size, sizeDp = sizeDp)
        composeRule.onRoot().performTouchInput {
            down(point)
            moveTo(point) // still inside the same wedge -- must not re-fire
            moveTo(point)
            up()
        }
        composeRule.waitForIdle()

        assert(events.count { it == OneRingHapticEvent.HighlightChanged } == 1) {
            "expected exactly one HighlightChanged event for staying in one wedge, got $events"
        }
        assert(events.contains(OneRingHapticEvent.Committed)) { "expected a Committed event, got $events" }
    }
}
