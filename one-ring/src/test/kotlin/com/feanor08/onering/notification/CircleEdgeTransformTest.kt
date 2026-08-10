package com.feanor08.onering.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-4f

class CircleEdgeTransformTest {

    private val dimensions = OneRingNotificationDefaults.dimensions()

    @Test fun `at the viewport center, a card gets full scale and alpha`() {
        val transform = circleEdgeTransform(0f, dimensions)
        assertEquals(1f, transform.scaleX, EPS)
        assertEquals(1f, transform.alpha, EPS)
    }

    @Test fun `at the viewport edge, a card gets the fully reduced scale and alpha`() {
        val transform = circleEdgeTransform(1f, dimensions)
        assertEquals(1f - dimensions.edgeWidthReductionFraction, transform.scaleX, EPS)
        assertEquals(1f - dimensions.edgeAlphaReductionFraction, transform.alpha, EPS)
    }

    @Test fun `values beyond 1 are clamped to the edge reduction, never overshooting`() {
        val atEdge = circleEdgeTransform(1f, dimensions)
        val beyondEdge = circleEdgeTransform(2.5f, dimensions)
        assertEquals(atEdge.scaleX, beyondEdge.scaleX, EPS)
        assertEquals(atEdge.alpha, beyondEdge.alpha, EPS)
    }

    @Test fun `negative distance is clamped to center values`() {
        val transform = circleEdgeTransform(-1f, dimensions)
        assertEquals(1f, transform.scaleX, EPS)
        assertEquals(1f, transform.alpha, EPS)
    }

    @Test fun `scale and alpha decrease monotonically with distance`() {
        val samples = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { circleEdgeTransform(it, dimensions) }
        for (i in 1 until samples.size) {
            assertTrue(samples[i].scaleX <= samples[i - 1].scaleX)
            assertTrue(samples[i].alpha <= samples[i - 1].alpha)
        }
    }

    @Test fun `neither scaleX nor alpha ever goes negative for a reasonable reduction fraction`() {
        val transform = circleEdgeTransform(1f, dimensions)
        assertTrue(transform.scaleX > 0f)
        assertTrue(transform.alpha > 0f)
    }
}
