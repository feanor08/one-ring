package com.feanor08.onering.internal

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Size
import com.feanor08.onering.OneRingDefaults
import com.feanor08.onering.OneRingHaptics
import com.feanor08.onering.OneRingItem
import com.feanor08.onering.OneRingState
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
private const val REVEAL_MS = 400
private const val SELECT_MS = 400

/**
 * Regression coverage for a real, shipped bug: the submenu's reveal was
 * animated correctly, but `state.path` -- the thing the renderer gates submenu
 * arcs on -- was only published *after* the parent's select animation
 * finished. Every intermediate reveal value was therefore computed into a
 * submenu that wasn't being drawn, and the first frame that actually drew it
 * was already at reveal ~1: the submenu popped in at full size.
 *
 * These tests drive the real [RingInteractionController] transition (not the
 * pure interpolation), so they fail again if anyone re-orders the state
 * mutations back into "commit -> final submenu in one step".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubmenuOpenSequencingTest {

    private val density = FakeDensity(2f)
    private val dimensions = OneRingDefaults.dimensions()
    private val resolved = resolveRing(Size(600f, 600f), density, dimensions, itemCount = 3)

    private val items = oneRingItems {
        menu(id = "media", label = "Media") {
            action(id = "play", label = "Play")
            action(id = "next", label = "Next")
        }
        action(id = "climate", label = "Climate")
        action(id = "nav", label = "Navigation")
    }

    private val motion = OneRingDefaults.motion(
        hover = tween(100, easing = LinearEasing),
        select = tween(SELECT_MS, easing = LinearEasing),
        close = tween(SELECT_MS, easing = LinearEasing),
        submenuReveal = tween(REVEAL_MS, easing = LinearEasing),
        siblingExit = tween(100, easing = LinearEasing),
    )

    /** The inner radius the renderer would actually draw this frame. */
    private fun renderedInnerPx(state: OneRingState): Float =
        lerp(resolved.submenuOuterPx, resolved.submenuInnerPx, state.submenuReveal.value.coerceIn(0f, 1f))

    /** True when the renderer would draw any submenu at all -- it gates on `path`. */
    private fun submenuIsRendered(state: OneRingState): Boolean = currentSubmenu(items, state.path) != null

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

    @Test
    fun `the submenu becomes renderable at zero thickness, not at full size`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("media")

            // Step frame by frame until the renderer would first draw the submenu,
            // and capture the reveal at exactly that moment.
            var revealWhenFirstRendered = -1f
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                if (revealWhenFirstRendered < 0f && submenuIsRendered(state)) {
                    revealWhenFirstRendered = state.submenuReveal.value
                }
            }

            assertTrue("the submenu was never rendered at all", revealWhenFirstRendered >= 0f)
            assertTrue(
                "submenu first became renderable at reveal=$revealWhenFirstRendered -- it popped in " +
                    "at (near) full size instead of growing from zero thickness",
                revealWhenFirstRendered < 0.05f,
            )
        }
    }

    @Test
    fun `intermediate radii are rendered across the whole open transition`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("media")

            val renderedInners = mutableListOf<Float>()
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                if (submenuIsRendered(state)) renderedInners += renderedInnerPx(state)
            }

            val strictlyIntermediate = renderedInners.count {
                it < resolved.submenuOuterPx - TOL && it > resolved.submenuInnerPx + TOL
            }
            assertTrue(
                "only $strictlyIntermediate rendered frames had a partially-grown submenu; " +
                    "the growth is not visible",
                strictlyIntermediate >= 10,
            )

            // And it must be monotonic inward -- no jump back outward.
            renderedInners.zipWithNext { a, b ->
                assertTrue("inner radius grew back outward: $a -> $b", b <= a + TOL)
            }
        }
    }

    @Test
    fun `the transition reaches exactly the configured submenu band and settles`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("media")
            advanceUntilIdle()

            assertEquals(listOf("media"), state.path)
            assertEquals(OneRingState.Phase.Settled, state.phase)
            assertEquals(1f, state.submenuReveal.value, 0.001f)
            assertEquals(resolved.submenuInnerPx, renderedInnerPx(state), TOL)
        }
    }

    @Test
    fun `at roughly a quarter and a half of the reveal the radius is genuinely partway`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("media")

            // Past the stagger, a quarter into the reveal.
            advanceTimeBy(45L + REVEAL_MS / 4)
            runCurrent()
            assertTrue("submenu not renderable a quarter in", submenuIsRendered(state))
            val quarter = renderedInnerPx(state)
            assertTrue(
                "quarter-way inner=$quarter is not between ${resolved.submenuInnerPx} and ${resolved.submenuOuterPx}",
                quarter < resolved.submenuOuterPx - TOL && quarter > resolved.submenuInnerPx + TOL,
            )

            advanceTimeBy((REVEAL_MS / 4).toLong())
            runCurrent()
            val half = renderedInnerPx(state)
            assertTrue("half-way inner=$half did not move further inward than quarter=$quarter", half < quarter - TOL)
            assertTrue("half-way inner=$half already reached the final radius", half > resolved.submenuInnerPx + TOL)
        }
    }

    @Test
    fun `the parent keeps expanding while the submenu grows, so they overlap`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("media")
            advanceTimeBy(45L + REVEAL_MS / 2)
            runCurrent()

            val parentExpansion = state.animations.of("media").expansion.value
            assertTrue("parent already finished before the submenu grew", parentExpansion < 2f - 0.05f)
            assertTrue("parent had not started expanding", parentExpansion > 1f)
            assertTrue("submenu had not started growing", state.submenuReveal.value > 0.05f)
            assertTrue("submenu already finished", state.submenuReveal.value < 0.95f)
        }
    }

    @Test
    fun `committing mid-hover continues the parent from its current radius`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            // Start a hover and interrupt it partway, exactly like an early release.
            controller.setHighlight("media")
            advanceTimeBy(40)
            runCurrent()
            val midHover = state.animations.of("media").expansion.value
            assertTrue("hover never got underway: $midHover", midHover > 0.05f && midHover < 0.95f)
            val radiusBefore = resolved.mainOuterFor(midHover)

            controller.commitDirectly("media")
            advanceTimeBy(16)
            runCurrent()
            val radiusAfter = resolved.mainOuterFor(state.animations.of("media").expansion.value)

            assertTrue("parent radius snapped inward on commit", radiusAfter >= radiusBefore - 1f)
            assertTrue(
                "parent radius jumped toward the selected radius in one frame",
                radiusAfter - radiusBefore < (resolved.mainSelectedOuterPx - resolved.mainOuterPx) * 0.3f,
            )
        }
    }

    @Test
    fun `closing shrinks through intermediate radii before the submenu disappears`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            controller.commitDirectly("media")
            advanceUntilIdle()
            assertEquals(listOf("media"), state.path)

            controller.closeDirectly()

            val renderedInners = mutableListOf<Float>()
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                if (submenuIsRendered(state)) renderedInners += renderedInnerPx(state)
            }

            val strictlyIntermediate = renderedInners.count {
                it < resolved.submenuOuterPx - TOL && it > resolved.submenuInnerPx + TOL
            }
            assertTrue(
                "the submenu vanished instead of shrinking ($strictlyIntermediate partial frames)",
                strictlyIntermediate >= 10,
            )
            assertTrue("still open after closing", state.path.isEmpty())
        }
    }

    @Test
    fun `rapid open-close-open leaves the ring settled and open, not stuck mid-transition`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val state = OneRingState(emptyList())
            val controller = controllerFor(state, this)

            // Hammer the programmatic entry points mid-transition. They are
            // gated on Phase.Settled exactly like the pointer path, so the
            // extra calls are dropped rather than interleaving two transitions
            // that would race over `phase`/`path`.
            controller.commitDirectly("media")
            advanceTimeBy(60)
            runCurrent()
            controller.closeDirectly()
            advanceTimeBy(60)
            runCurrent()
            controller.commitDirectly("media")
            advanceUntilIdle()

            assertEquals(OneRingState.Phase.Settled, state.phase)
            assertEquals(listOf("media"), state.path)
            assertEquals(resolved.submenuInnerPx, renderedInnerPx(state), TOL)

            // And the ring is still responsive afterwards: a close now works.
            controller.closeDirectly()
            advanceUntilIdle()
            assertEquals(OneRingState.Phase.Settled, state.phase)
            assertTrue("ring did not respond to input after rapid toggling", state.path.isEmpty())
        }
    }

    @Test
    fun `weighted children keep their angular spans fixed for the whole growth`() = runTest {
        withContext(VirtualFrameClock(scheduler = testScheduler)) {
            val weightedItems = oneRingItems {
                menu(id = "media", label = "Media", weight = 2f) {
                    action(id = "play", label = "Play", weight = 2f)
                    action(id = "prev", label = "Previous")
                    action(id = "next", label = "Next")
                }
                action(id = "climate", label = "Climate")
                action(id = "nav", label = "Navigation")
            }
            val weightedResolved = resolveRing(
                Size(600f, 600f),
                density,
                dimensions,
                weightedItems.map { it.weight },
            )
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

            controller.commitDirectly("media")

            val parent = weightedItems.first() as OneRingItem.Menu
            var reference: List<Pair<Float, Float>>? = null
            var samples = 0
            repeat(200) {
                advanceTimeBy(16)
                runCurrent()
                if (currentSubmenu(weightedItems, state.path) != null) {
                    val parentSpan = weightedResolved.mainSpans.first()
                    val spans = submenuChildSpans(weightedResolved, dimensions, parentSpan, parent.children)
                        .map { it.startDeg to it.sweepDeg }
                    if (reference == null) reference = spans else assertEquals(reference, spans)
                    samples++
                }
            }
            assertTrue("never sampled the growing submenu", samples > 10)
        }
    }
}
