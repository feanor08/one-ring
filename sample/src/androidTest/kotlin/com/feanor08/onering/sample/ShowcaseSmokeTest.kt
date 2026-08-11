package com.feanor08.onering.sample

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

    /**
     * The showcase home list is a `LazyColumn` (see `ShowcaseApp.kt`), which
     * `performScrollTo()` -- a `Modifier.verticalScroll`-oriented action that
     * incrementally nudges the nearest scrollable ancestor -- cannot reach
     * reliably once there are many off-screen, unmeasured items below the
     * viewport (this was a real, previously-undiagnosed defect: on a real
     * device, `performScrollTo()` on the home list reliably failed to reach
     * far-down entries like "Accessibility" or "Motion"). `performScrollToNode`
     * uses the lazy list's own semantics-exposed scroll-to-index action to
     * jump directly to the target item regardless of list length or how far
     * down it is -- the correct, deterministic tool for a `LazyColumn`.
     */
    private fun openDemo(demo: Demo) {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(demo.title))
        rule.onNodeWithText(demo.title).performClick()
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
        // The preset chips live in their own LazyRow (see StylingDemo.kt),
        // nested inside DemoScaffold's own outer vertical scroll -- so
        // hasScrollAction() alone matches two nodes here, unlike openDemo's
        // single home list. This matcher picks the *horizontally* scrollable
        // one specifically, then uses the same performScrollToNode reasoning:
        // a plain performClick() can't find a chip Compose hasn't measured yet.
        val chipRow = SemanticsMatcher("is a horizontally scrollable row") {
            it.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null
        }
        listOf("Light", "Automotive", "Cyberpunk", "Minimal Monochrome", "High Contrast", "Default Dark")
            .forEach { preset ->
                rule.onNode(chipRow).performScrollToNode(hasText(preset))
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
    fun notificationDemoPostingEveryButtonDoesNotCrashAndAdarshMessagesAccumulate() {
        launch()
        openDemo(Demo.Notifications)
        // The initial delayed post has time to land.
        rule.waitForIdle()

        rule.onNodeWithText("WhatsApp · Adarsh").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Adarsh +1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("WhatsApp · Varsha").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("WhatsApp · Family").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Software Update").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Tyre Pressure").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Service Reminder").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Cloud Notification").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Battery Alert").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Clear All").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Reset Demo").performScrollTo().performClick()
        rule.waitForIdle()
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
