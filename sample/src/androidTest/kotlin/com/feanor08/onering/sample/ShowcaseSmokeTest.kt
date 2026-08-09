package com.feanor08.onering.sample

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.feanor08.onering.sample.theme.OneRingSampleTheme
import com.feanor08.onering.sample.ui.Demo
import com.feanor08.onering.sample.ui.ShowcaseApp
import org.junit.Rule
import org.junit.Test

/**
 * Smoke coverage only: that every showcase screen composes, and that the
 * interactive controls which rebuild the item list (preset chips, item-count
 * chips) don't crash when toggled. Real geometry/interaction correctness is
 * tested in the :one-ring module, not here.
 */
class ShowcaseSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    private fun launch() {
        rule.setContent { OneRingSampleTheme { ShowcaseApp() } }
    }

    private fun openDemo(demo: Demo) {
        rule.onNodeWithText(demo.title).performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun everyDemoInTheCatalogOpensAndRenders() {
        launch()
        Demo.entries.forEach { demo ->
            openDemo(demo)
            rule.onNodeWithContentDescription("Back").assertExists()
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun basicDemoRendersItsStatusLine() {
        launch()
        openDemo(Demo.Basic)
        rule.onNodeWithText("Last selected: (none yet)").assertExists()
    }

    @Test
    fun nestedDemoRendersAtTheRootPath() {
        launch()
        openDemo(Demo.Nested)
        rule.onNodeWithText("Last action: (none yet)  ·  path: [(root)]").assertExists()
    }

    @Test
    fun seekDemoRendersAllThreeSections() {
        launch()
        openDemo(Demo.Seek)
        rule.onNodeWithText("Main-ring seek").assertExists()
        rule.onNodeWithText("Submenu seek").performScrollTo().assertExists()
        rule.onNodeWithText("Seek steps").performScrollTo().assertExists()
    }

    @Test
    fun switchingEveryItemCountDoesNotCrash() {
        launch()
        openDemo(Demo.ItemCounts)
        listOf("2", "3", "4", "5", "6", "8", "10", "12").forEach { count ->
            rule.onNodeWithText(count).performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText("Last selected: (none yet)").assertExists()
    }

    @Test
    fun switchingEveryColorPresetDoesNotCrash() {
        launch()
        openDemo(Demo.Styling)
        listOf("Light", "Automotive", "Cyberpunk", "Minimal Monochrome", "High Contrast", "Default Dark")
            .forEach { preset ->
                rule.onNodeWithText(preset).performClick()
                rule.waitForIdle()
            }
    }

    @Test
    fun switchingEveryMotionPresetDoesNotCrash() {
        launch()
        openDemo(Demo.Motion)
        listOf("Instant", "Fast", "Slow", "Default").forEach { preset ->
            rule.onNodeWithText(preset).performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun dynamicDemoAddRemoveShuffleAndDisableDoNotCrash() {
        launch()
        openDemo(Demo.Dynamic)
        repeat(3) { rule.onNodeWithText("Add").performScrollTo().performClick() }
        rule.onNodeWithText("Shuffle").performScrollTo().performClick()
        repeat(2) { rule.onNodeWithText("Remove").performScrollTo().performClick() }
        rule.onNodeWithText("Toggle random disabled").performScrollTo().performClick()
        rule.onNodeWithText("Reset").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Last selected: (none yet)  ·  4 items").assertExists()
    }

    @Test
    fun accessibilityDemoExposesDisabledItemsToSemantics() {
        launch()
        openDemo(Demo.Accessibility)
        // The library publishes one semantics node per wedge, using
        // contentDescription in preference to the label.
        rule.onNodeWithContentDescription("Bluetooth, unavailable").assertExists()
        rule.onNodeWithContentDescription("Camera, unavailable").assertExists()
    }
}
