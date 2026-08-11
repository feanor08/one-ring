package com.feanor08.onering

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.geometry.SubmenuLayout
import org.junit.Rule
import org.junit.Test

/** Compose-level coverage of Phase 6's seek controls: entry, drag, exit, both variants. */
class OneRingSeekInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun committingAMainSeekItemEntersSeekModeAndDraggingUpdatesTheValue() {
        var value = 0.1f
        val seekItems = listOf(
            OneRingItem.Seek(id = "vol", label = "Volume", value = value, onValueChange = { value = it }),
            OneRingItem.Action(id = "b", label = "B"),
            OneRingItem.Action(id = "c", label = "C"),
        )
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = seekItems, onAction = {}, state = state, modifier = Modifier.size(300.dp))
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = seekItems.size))
            up()
        }
        composeRule.waitForIdle()
        assert(state.mode == OneRingMode.Seek) { "expected Seek mode after committing a Seek item, was ${state.mode}" }

        // Drag to the far side of the ring, well inside the seek track (the
        // wedge's own excluded region is only 120deg wide, centered on its
        // own midpoint -- 180deg away is always free track).
        composeRule.onRoot().performTouchInput {
            val farSide = pointAtPolar(0.7f, 180f)
            down(farSide)
            moveTo(farSide)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().performTouchInput { up() }
        composeRule.waitForIdle()

        assert(value != 0.1f) { "expected the drag to change the value away from its initial 0.1, stayed at $value" }
    }

    @Test fun pressingTheCloseWedgeExitsMainSeek() {
        val seekItems = listOf(
            OneRingItem.Seek(id = "vol", label = "Volume", value = 0.5f, onValueChange = {}),
            OneRingItem.Action(id = "b", label = "B"),
        )
        lateinit var state: OneRingState

        composeRule.setContent {
            state = rememberOneRingState()
            OneRing(items = seekItems, onAction = {}, state = state, modifier = Modifier.size(300.dp))
        }

        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = seekItems.size))
            up()
        }
        composeRule.waitForIdle()
        assert(state.mode == OneRingMode.Seek) { "expected Seek mode, was ${state.mode}" }

        // The seek item grew to the selected radius and is now the close
        // control, exactly like a Menu -- pressing the same point exits.
        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = seekItems.size))
            up()
        }
        composeRule.waitForIdle()

        assert(state.mode == OneRingMode.Idle) { "expected Idle after closing the seek, was ${state.mode}" }
    }

    @Test fun submenuSeekEntersSeekModeWithoutLeavingTheSubmenuAndClosesViaTheParent() {
        val menuItems = listOf(
            OneRingItem.Menu(
                id = "music",
                label = "Music",
                children = listOf(
                    OneRingItem.Action(id = "prev", label = "Previous"),
                    OneRingItem.Action(id = "next", label = "Next"),
                    OneRingItem.Seek(id = "vol", label = "Volume", value = 0.5f, onValueChange = {}),
                ),
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
        assert(state.path == listOf("music")) { "expected the submenu open, path=${state.path}" }

        // Compute the third child's ("vol") wedge midpoint the same way the
        // library does: the parent occupies one third of the ring (3 top-level
        // items), and SubmenuLayout divides its angular complement.
        val parentSweep = 360f / menuItems.size
        val parentSpan = AngularSpan(-parentSweep / 2f, parentSweep)
        val defaults = OneRingDefaults.dimensions()
        val childSpans = SubmenuLayout.spans(
            parent = parentSpan,
            childCount = 3,
            maxChildSweepDeg = defaults.maxSubmenuItemSweepDeg,
            alignment = defaults.submenuAlignment,
        )
        val childMidDeg = childSpans[2].midDeg // index 2 = "vol"
        val submenuMidRadiusFraction = (0.280f + 0.512f) / 2f

        composeRule.onRoot().performTouchInput {
            down(pointAtPolar(submenuMidRadiusFraction, childMidDeg))
            up()
        }
        composeRule.waitForIdle()

        assert(state.mode == OneRingMode.Seek) { "expected Seek mode, was ${state.mode}" }
        assert(state.path == listOf("music")) { "submenu seek must not pop path, was ${state.path}" }

        // The parent wedge never changed roles -- pressing it exits all the way.
        composeRule.onRoot().performTouchInput {
            down(pointInWedge(index = 0, count = menuItems.size))
            up()
        }
        composeRule.waitForIdle()

        assert(state.path.isEmpty()) { "expected empty path after closing, was ${state.path}" }
        assert(state.mode == OneRingMode.Idle) { "expected Idle after closing, was ${state.mode}" }
    }
}
