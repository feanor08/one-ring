package com.feanor08.onering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Invalid weights must fail at construction, where the mistake is, rather than
 * propagating NaN or zero-sweep spans down into Canvas drawing.
 */
class OneRingItemWeightTest {

    @Test
    fun `weight defaults to 1 on every item type`() {
        assertEquals(1f, OneRingItem.Action(id = "a", label = "A").weight, 0f)
        assertEquals(1f, OneRingItem.Menu(id = "m", label = "M", children = emptyList()).weight, 0f)
        assertEquals(1f, OneRingItem.Seek(id = "s", label = "S", value = 0f, onValueChange = {}).weight, 0f)
    }

    @Test
    fun `a zero weight is rejected`() {
        assertRejects { OneRingItem.Action(id = "zero", label = "Z", weight = 0f) }
    }

    @Test
    fun `a negative weight is rejected`() {
        assertRejects { OneRingItem.Action(id = "neg", label = "N", weight = -2f) }
    }

    @Test
    fun `NaN and infinite weights are rejected`() {
        assertRejects { OneRingItem.Action(id = "nan", label = "N", weight = Float.NaN) }
        assertRejects { OneRingItem.Action(id = "inf", label = "I", weight = Float.POSITIVE_INFINITY) }
        assertRejects { OneRingItem.Action(id = "ninf", label = "I", weight = Float.NEGATIVE_INFINITY) }
    }

    @Test
    fun `invalid weights are rejected on menus and seeks too`() {
        assertRejects { OneRingItem.Menu(id = "m", label = "M", children = emptyList(), weight = 0f) }
        assertRejects { OneRingItem.Seek(id = "s", label = "S", value = 0f, onValueChange = {}, weight = Float.NaN) }
    }

    @Test
    fun `the error names the offending item so it can be found`() {
        try {
            OneRingItem.Action(id = "climate", label = "Climate", weight = 0f)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue("message should name the item: ${e.message}", e.message!!.contains("climate"))
        }
    }

    @Test
    fun `the DSL carries weights through to the built items`() {
        val items = oneRingItems {
            action(id = "big", label = "Big", weight = 2f)
            menu(id = "mid", label = "Mid", weight = 1.5f) {
                action(id = "child", label = "Child", weight = 3f)
            }
            seek(id = "small", label = "Small", value = 0f, onValueChange = {}, weight = 0.5f)
        }

        assertEquals(2f, items[0].weight, 0f)
        assertEquals(1.5f, items[1].weight, 0f)
        assertEquals(3f, (items[1] as OneRingItem.Menu).children.single().weight, 0f)
        assertEquals(0.5f, items[2].weight, 0f)
    }

    @Test
    fun `the DSL rejects an invalid weight as well`() {
        assertRejects { oneRingItems { action(id = "bad", label = "Bad", weight = -1f) } }
    }

    private inline fun assertRejects(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException for an invalid weight")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
