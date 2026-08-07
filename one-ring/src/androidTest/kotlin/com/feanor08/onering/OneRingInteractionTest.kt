package com.feanor08.onering

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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
}
