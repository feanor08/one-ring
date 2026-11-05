package com.feanor08.onering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRingItemsDslTest {

    @Test fun `builds a flat list of actions in declaration order`() {
        val items = oneRingItems {
            action(id = "a", label = "A")
            action(id = "b", label = "B")
        }
        assertEquals(listOf("a", "b"), items.map { it.id })
        assertTrue(items.all { it is OneRingItem.Action })
    }

    @Test fun `menu nests children via the DSL`() {
        val items = oneRingItems {
            menu(id = "music", label = "Music") {
                action(id = "prev", label = "Previous")
                action(id = "next", label = "Next")
            }
        }
        val menu = items.single() as OneRingItem.Menu
        assertEquals("music", menu.id)
        assertEquals(listOf("prev", "next"), menu.children.map { it.id })
    }

    @Test fun `seek captures a fully controlled value`() {
        var value = 0.5f
        val items = oneRingItems {
            seek(id = "volume", label = "Volume", value = value, onValueChange = { value = it })
        }
        val seek = items.single() as OneRingItem.Seek
        assertEquals(0.5f, seek.value, 0f)
        seek.onValueChange(0.75f)
        assertEquals(0.75f, value, 0f)
    }

    @Test fun `toggle captures a fully controlled checked value`() {
        var checked = false
        val items = oneRingItems {
            toggle(id = "bt", label = "Bluetooth", checked = checked, onCheckedChange = { checked = it })
        }
        val toggle = items.single() as OneRingItem.Toggle
        assertEquals(false, toggle.checked)
        toggle.onCheckedChange(true)
        assertEquals(true, checked)
    }

    @Test fun `duplicate sibling ids are rejected at construction time`() {
        assertThrows(IllegalArgumentException::class.java) {
            oneRingItems {
                action(id = "a", label = "A")
                action(id = "a", label = "A again")
            }
        }
    }

    @Test fun `duplicate ids across different nesting levels are allowed (only siblings must be unique)`() {
        val items = oneRingItems {
            action(id = "shared", label = "Top")
            menu(id = "menu", label = "Menu") {
                action(id = "shared", label = "Nested")
            }
        }
        assertEquals(2, items.size)
    }

    @Test fun `empty builder produces an empty list`() {
        assertTrue(oneRingItems { }.isEmpty())
    }
}
