package com.feanor08.onering.internal

import com.feanor08.onering.OneRingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentSubmenuTest {

    private val child = OneRingItem.Action(id = "child", label = "Child")
    private val menu = OneRingItem.Menu(id = "menu", label = "Menu", children = listOf(child))
    private val leaf = OneRingItem.Action(id = "leaf", label = "Leaf")
    private val items = listOf(menu, leaf)

    @Test fun `empty path has no current submenu`() {
        assertNull(currentSubmenu(items, emptyList()))
    }

    @Test fun `path pointing at a Menu resolves to it and its children`() {
        val result = currentSubmenu(items, listOf("menu"))
        assertEquals(menu, result?.first)
        assertEquals(listOf(child), result?.second)
    }

    @Test fun `path pointing at a non-Menu item is null`() {
        assertNull(currentSubmenu(items, listOf("leaf")))
    }

    @Test fun `path pointing at an unknown id is null`() {
        assertNull(currentSubmenu(items, listOf("nope")))
    }

    @Test fun `paths deeper than one level are not yet supported and resolve to null`() {
        assertNull(currentSubmenu(items, listOf("menu", "child")))
    }
}
