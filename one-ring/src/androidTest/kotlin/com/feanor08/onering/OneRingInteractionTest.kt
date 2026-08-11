package com.feanor08.onering

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.SubmenuLayout
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

    @Test fun releaseOverAnActionItemCommitsItAndClearsHighlight() {
        var lastAction: String? = null

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(300.dp),
            )
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 1, count = items.size))
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == "b") { "expected commit on item b, got $lastAction" }
    }

    @Test fun releaseOutsideTheRingCancelsWithoutFiringAnAction() {
        var lastAction: String? = null

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(300.dp),
            )
        }

        composeRule.onRoot().performTouchInput {
            down(center) // dead center, inside the empty core
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == null) { "expected no action from a center press, got $lastAction" }
    }

    @Test fun scrubbingBetweenWedgesUpdatesTheHighlightedState() {
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = items, onAction = {}, state = state, modifier = Modifier.size(300.dp))
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = items.size))
        }
        composeRule.waitForIdle()
        assert(state.highlightedId == "a") { "expected highlight on a, was ${state.highlightedId}" }

        composeRule.onRoot().performTouchInput {
            moveTo(pointInWedge(index = 2, count = items.size))
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
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = menuItems, onAction = {}, state = state, modifier = Modifier.size(300.dp))
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = menuItems.size))
            up()
        }
        composeRule.waitForIdle()

        assert(state.path == listOf("menu")) { "expected path=[menu], was ${state.path}" }
        assert(state.mode == OneRingMode.Submenu) { "expected Submenu mode, was ${state.mode}" }

        // The parent wedge has grown to the selected radius and is now the
        // close control; pressing the same point again closes it.
        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = menuItems.size))
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
        lateinit var state: OneRingState
        var lastAction: String? = null

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(
                items = menuItems,
                onAction = { lastAction = it.id },
                state = state,
                modifier = Modifier.size(300.dp),
            )
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = menuItems.size))
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

        composeRule.onRoot().performTouchInput {
            down(pointAtPolar(submenuMidRadiusFraction, childMidDeg))
            up()
        }
        composeRule.waitForIdle()

        assert(lastAction == "child0") { "expected commit on child0, got $lastAction" }
        assert(state.path.isEmpty()) { "expected the submenu to close after a child commit, was ${state.path}" }
        assert(state.mode == OneRingMode.Idle) { "expected Idle after child commit, was ${state.mode}" }
    }

    @Test fun talkBackSemanticClickCommitsAnActionWithoutAnyPointerGesture() {
        var lastAction: String? = null

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = { lastAction = it.id },
                modifier = Modifier.size(300.dp),
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

        composeRule.setContent {
            OneRing(
                items = items,
                onAction = {},
                modifier = Modifier.size(300.dp),
                haptics = OneRingHaptics { events += it },
            )
        }

        composeRule.onRoot().performTouchInput {
            val point = pointInWedge(index = 0, count = items.size)
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
