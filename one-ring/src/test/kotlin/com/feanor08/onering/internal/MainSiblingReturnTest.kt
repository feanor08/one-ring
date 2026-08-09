package com.feanor08.onering.internal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingHaptics
import com.feanor08.onering.OneRingState
import com.feanor08.onering.geometry.AngularSpan
import com.feanor08.onering.oneRingItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOL = 0.5f
private const val SELECT_MS = 300
private const val CLOSE_MS = 300
private const val REVEAL_MS = 300

/**
 * Regression coverage for a real bug: closing a submenu animated the selected
 * parent correctly, but every *other* main-ring wedge simply popped back into
 * existence once the transition finished, instead of growing back outward from
 * the inner edge the way they collapsed inward when the submenu opened.
 *
 * These tests drive the real [RingInteractionController] and read the exact
 * radius [RingFrameBuilder] would render each frame, so they fail again if a
 * future change makes the fix a no-op (e.g. reverting to instant sibling
 * geometry, or forgetting to exclude the active item from the shared reveal).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainSiblingReturnTest {

    private val density = FakeDensity(2f)
    private val dimensions = OneRingDefaults.dimensions()
    private val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 4)

    private val items = oneRingItems {
        menu(id = "media", label = "Media") {
            action(id = "play", label = "Play")
            action(id = "next", label = "Next")
        }
        action(id = "climate", label = "Climate")
        action(id = "nav", label = "Navigation")
        action(id = "settings", label = "Settings")
    }

    private val motion = OneRingDefaults.motion(
        hover = tween(80, easing = LinearEasing),
        select = tween(SELECT_MS, easing = LinearEasing),
        close = tween(CLOSE_MS, easing = LinearEasing),
        submenuReveal = tween(REVEAL_MS, easing = LinearEasing),
        iconMorph = tween(60, easing = LinearEasing),
        siblingExit = tween(SELECT_MS, easing = LinearEasing),
    )

    private fun controllerFor(state: OneRingState, scope: CoroutineScope): RingInteractionController {
        state.scope = scope
        return RingInteractionController(state).apply {
            update(
                items = items,
                resolved = resolved,
                dimensions = dimensions,
                motion = motion,
                behavior = OneRingDefaults.behavior(hapticsEnabled = false),
                haptics = OneRingHaptics.None,
                onAction = {},
                onEvent = null,
            )
        }
    }

    /** The outer radius RingFrameBuilder would actually draw for [id] this frame. */
    private fun renderedOuterPx(state: OneRingState, id: String): Float {
        val activeId = state.path.firstOrNull() ?: state.seekSession?.itemId
        val frame = RingFrameBuilder.build(
            items = items,
            resolved = resolved,
            colors = OneRingDefaults.colors(),
            expansionOf = { itemId -> state.animations.of(itemId).expansion.value },
            mainSiblingRevealProgress = state.mainSiblingReveal.value,
            activeItemId = activeId,
        )
        return frame.arcs.first { items[it.itemIndex].id == id }.band.outerPx
    }

    private fun renderedSpan(state: OneRingState, id: String): AngularSpan {
        val activeId = state.path.firstOrNull() ?: state.seekSession?.itemId
        val frame = RingFrameBuilder.build(
            items = items,
            resolved = resolved,
            colors = OneRingDefaults.colors(),
            expansionOf = { itemId -> state.animations.of(itemId).expansion.value },
            mainSiblingRevealProgress = state.mainSiblingReveal.value,
            activeItemId = activeId,
        )
        return frame.arcs.first { items[it.itemIndex].id == id }.span
    }

    // ---- The core bug: closing a submenu ---------------------------------

    @Test
    fun `at the start of close a sibling has already collapsed to the inner radius`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            assertEquals(resolved.mainInnerPx, renderedOuterPx(state, "climate"), TOL)
        }
    }

    @Test
    fun `a quarter into close a sibling is genuinely between the inner and outer radius`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            // Past the iconMorph X-out stage (60ms), a quarter into the 300ms close.
            advanceTimeBy(60L + CLOSE_MS / 4)
            runCurrent()

            val outer = renderedOuterPx(state, "climate")
            assertTrue(
                "a sibling should be partially grown at 25% close, but outer=$outer is not strictly " +
                    "between mainInner=${resolved.mainInnerPx} and mainOuter=${resolved.mainOuterPx}",
                outer > resolved.mainInnerPx + TOL && outer < resolved.mainOuterPx - TOL,
            )
        }
    }

    @Test
    fun `halfway into close a sibling has grown further than it had at the quarter mark`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            advanceTimeBy(60L + CLOSE_MS / 4)
            runCurrent()
            val quarter = renderedOuterPx(state, "climate")

            advanceTimeBy((CLOSE_MS / 4).toLong())
            runCurrent()
            val half = renderedOuterPx(state, "climate")

            assertTrue("outer radius did not grow between 25% and 50% close ($quarter -> $half)", half > quarter + TOL)
            assertTrue("already reached full size at 50% close", half < resolved.mainOuterPx - TOL)
        }
    }

    @Test
    fun `growth is monotonic outward across the whole close, never jumping back in`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            var previous = -1f
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                val outer = renderedOuterPx(state, "climate")
                if (previous >= 0f) {
                    assertTrue("sibling outer radius shrank mid-return: $previous -> $outer", outer >= previous - TOL)
                }
                previous = outer
            }
        }
    }

    @Test
    fun `once settled every sibling is back at the full main outer radius`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            advanceUntilIdle()

            listOf("climate", "nav", "settings").forEach { id ->
                assertEquals("$id did not return to full size", resolved.mainOuterPx, renderedOuterPx(state, id), TOL)
            }
            assertEquals(1f, state.mainSiblingReveal.value, 0.001f)
        }
    }

    @Test
    fun `sibling alpha is also intermediate during the return, not start-then-jump`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()
            assertEquals(0f, state.animations.of("climate").contentAlpha.value, 0.01f)

            controller.closeDirectly()
            advanceTimeBy(60L + CLOSE_MS / 2)
            runCurrent()
            val mid = state.animations.of("climate").contentAlpha.value
            assertTrue("alpha should be partway back at the midpoint, was $mid", mid > 0.05f && mid < 0.95f)

            advanceUntilIdle()
            assertEquals(1f, state.animations.of("climate").contentAlpha.value, 0.01f)
        }
    }

    // ---- Submenu collapses at the same time as siblings regrow -----------

    @Test
    fun `the submenu is simultaneously mid-collapse while siblings are mid-regrow`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            advanceTimeBy(60L + CLOSE_MS / 3)
            runCurrent()

            val siblingOuter = renderedOuterPx(state, "climate")
            assertTrue(
                "siblings hadn't started regrowing while submenu was mid-collapse",
                siblingOuter > resolved.mainInnerPx + TOL,
            )
            assertTrue(
                "submenu reveal should be mid-collapse at the same time, was ${state.submenuReveal.value}",
                state.submenuReveal.value in 0.05f..0.95f,
            )
        }
    }

    // ---- The selected parent continuously transforms, never pops ---------

    @Test
    fun `the selected parent's own radius shrinks continuously, with no jump`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()
            assertEquals(2f, state.animations.of("media").expansion.value, 0.01f)

            controller.closeDirectly()
            var previous = Float.MAX_VALUE
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                val expansion = state.animations.of("media").expansion.value
                assertTrue(
                    "parent expansion jumped back outward: $previous -> $expansion",
                    expansion <= previous + 0.05f,
                )
                previous = expansion
            }
            assertEquals(0f, state.animations.of("media").expansion.value, 0.01f)
        }
    }

    @Test
    fun `the parent is excluded from the sibling reveal throughout the whole close`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                // The parent's rendered radius must always come from its own
                // expansion, never from mainSiblingReveal -- e.g. it must never
                // sit at mainInnerPx (the sibling-collapsed radius) while its own
                // expansion is still comfortably above rest.
                val parentExpansion = state.animations.of("media").expansion.value
                if (parentExpansion > 0.5f) {
                    val parentOuter = renderedOuterPx(state, "media")
                    assertTrue(
                        "parent outer=$parentOuter looks sibling-collapsed despite expansion=$parentExpansion",
                        parentOuter > resolved.mainInnerPx + TOL,
                    )
                }
            }
        }
    }

    // ---- Weighted rings keep their angular geometry fixed -----------------

    @Test
    fun `weighted siblings keep their exact start and end angles throughout the return`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val weightedItems = oneRingItems {
                menu(id = "media", label = "Media", weight = 2f) {
                    action(id = "play", label = "Play")
                }
                action(id = "climate", label = "Climate", weight = 1f)
                action(id = "nav", label = "Navigation", weight = 1f)
            }
            val weightedResolved = resolveRing(Size(600f, 600f), density, dimensions, weightedItems.map { it.weight })
            val state = OneRingState(emptyList())
            state.scope = this
            val controller = RingInteractionController(state).apply {
                update(
                    items = weightedItems,
                    resolved = weightedResolved,
                    dimensions = dimensions,
                    motion = motion,
                    behavior = OneRingDefaults.behavior(hapticsEnabled = false),
                    haptics = OneRingHaptics.None,
                    onAction = {},
                    onEvent = null,
                )
            }

            fun spanFor(id: String): AngularSpan {
                val activeId = state.path.firstOrNull()
                val frame = RingFrameBuilder.build(
                    items = weightedItems,
                    resolved = weightedResolved,
                    colors = OneRingDefaults.colors(),
                    expansionOf = { itemId -> state.animations.of(itemId).expansion.value },
                    mainSiblingRevealProgress = state.mainSiblingReveal.value,
                    activeItemId = activeId,
                )
                return frame.arcs.first { weightedItems[it.itemIndex].id == id }.span
            }

            controller.commitDirectly("media")
            advanceUntilIdle()
            val referenceClimate = spanFor("climate")
            val referenceNav = spanFor("nav")

            controller.closeDirectly()
            repeat(30) {
                advanceTimeBy(16)
                runCurrent()
                val climate = spanFor("climate")
                val nav = spanFor("nav")
                assertEquals(referenceClimate.startDeg, climate.startDeg, 0.001f)
                assertEquals(referenceClimate.sweepDeg, climate.sweepDeg, 0.001f)
                assertEquals(referenceNav.startDeg, nav.startDeg, 0.001f)
                assertEquals(referenceNav.sweepDeg, nav.sweepDeg, 0.001f)
            }
        }
    }

    // ---- Not accidentally highlighted while returning ----------------------

    @Test
    fun `returning siblings use normal styling, never the pointer-highlight accent`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            controller.closeDirectly()
            advanceTimeBy(60L + CLOSE_MS / 2)
            runCurrent()

            val colors = OneRingDefaults.colors()
            val frame = RingFrameBuilder.build(
                items = items,
                resolved = resolved,
                colors = colors,
                expansionOf = { itemId -> state.animations.of(itemId).expansion.value },
                highlightedId = state.highlightedId,
                mainSiblingRevealProgress = state.mainSiblingReveal.value,
                activeItemId = state.path.firstOrNull(),
            )
            val climateArc = frame.arcs.first { items[it.itemIndex].id == "climate" }
            assertTrue(
                "a mid-return sibling must not use the highlighted fill",
                climateArc.fill == colors.sector || climateArc.fill == colors.sectorAlternate,
            )
            assertEquals(colors.stroke, climateArc.stroke)
        }
    }

    // ---- Main-ring seek exit uses the same return language ----------------

    @Test
    fun `closing a main-ring seek also regrows its siblings, not an instant pop`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val seekItems = oneRingItems {
                seek(id = "volume", label = "Volume", value = 0.5f, onValueChange = {})
                action(id = "climate", label = "Climate")
                action(id = "nav", label = "Navigation")
                action(id = "settings", label = "Settings")
            }
            val seekResolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 4)
            val state = OneRingState(emptyList())
            state.scope = this
            val controller = RingInteractionController(state).apply {
                update(
                    items = seekItems,
                    resolved = seekResolved,
                    dimensions = dimensions,
                    motion = motion,
                    behavior = OneRingDefaults.behavior(hapticsEnabled = false),
                    haptics = OneRingHaptics.None,
                    onAction = {},
                    onEvent = null,
                )
            }

            fun outerFor(id: String): Float {
                val activeId = state.seekSession?.itemId
                val frame = RingFrameBuilder.build(
                    items = seekItems,
                    resolved = seekResolved,
                    colors = OneRingDefaults.colors(),
                    expansionOf = { itemId -> state.animations.of(itemId).expansion.value },
                    mainSiblingRevealProgress = state.mainSiblingReveal.value,
                    activeItemId = activeId,
                )
                return frame.arcs.first { seekItems[it.itemIndex].id == id }.band.outerPx
            }

            controller.commitDirectly("volume")
            advanceUntilIdle()
            assertEquals(resolved.mainInnerPx, outerFor("climate"), TOL)

            controller.closeDirectly()
            var sawIntermediate = false
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                val outer = outerFor("climate")
                if (outer > resolved.mainInnerPx + TOL && outer < resolved.mainOuterPx - TOL) sawIntermediate = true
            }
            assertTrue("sibling never rendered at an intermediate radius while a main seek closed", sawIntermediate)
            assertEquals(resolved.mainOuterPx, outerFor("climate"), TOL)
        }
    }

    // ---- Submenu-child seek: dimmed siblings are restored on the way out ---

    @Test
    fun `a submenu child dimmed by a sibling seek is restored to full alpha when the submenu closes`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)
            controller.commitDirectly("media")
            advanceUntilIdle()

            // "play" is a submenu child; entering "next" as if it were a seek
            // would dim "play" -- reuse the real seek entry path via a menu
            // with a seek child to exercise the exact code path.
            val seekChildItems = oneRingItems {
                menu(id = "media", label = "Media") {
                    action(id = "play", label = "Play")
                    seek(id = "vol", label = "Volume", value = 0.5f, onValueChange = {})
                }
                action(id = "climate", label = "Climate")
                action(id = "nav", label = "Navigation")
                action(id = "settings", label = "Settings")
            }
            val state2 = OneRingState(emptyList())
            state2.scope = this
            val controller2 = RingInteractionController(state2).apply {
                update(
                    items = seekChildItems,
                    resolved = resolved,
                    dimensions = dimensions,
                    motion = motion,
                    behavior = OneRingDefaults.behavior(hapticsEnabled = false),
                    haptics = OneRingHaptics.None,
                    onAction = {},
                    onEvent = null,
                )
            }

            controller2.commitDirectly("media")
            advanceUntilIdle()
            controller2.commitDirectly("vol") // enters the submenu-level seek, dimming "play"
            advanceUntilIdle()
            assertEquals(0f, state2.animations.of("play").contentAlpha.value, 0.05f)

            // The only exit from a submenu-level seek is the parent's own X,
            // which closes all the way back to Main.
            controller2.closeDirectly()
            advanceUntilIdle()

            assertEquals(
                "the dimmed sibling's alpha was never restored -- it would stay invisible if this " +
                    "submenu were reopened",
                1f,
                state2.animations.of("play").contentAlpha.value,
                0.01f,
            )
        }
    }
}
