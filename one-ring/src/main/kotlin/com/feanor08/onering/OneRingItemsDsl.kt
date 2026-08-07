package com.feanor08.onering

/** Scopes [oneRingItems]'s builder lambda so it can't accidentally nest into an unrelated DSL. */
@DslMarker
public annotation class OneRingDsl

/**
 * A builder for nested [OneRingItem] trees, cheaper to read than nested
 * `listOf(...)` calls and able to catch duplicate sibling ids at construction
 * time instead of silently breaking hit-testing later.
 */
@OneRingDsl
public class OneRingItemsScope internal constructor() {
    private val items = mutableListOf<OneRingItem>()

    public fun action(
        id: String,
        label: String,
        icon: OneRingIcon = OneRingIcon.None,
        enabled: Boolean = true,
        contentDescription: String? = null,
        payload: Any? = null,
    ) {
        items += OneRingItem.Action(id, label, icon, enabled, contentDescription, payload)
    }

    public fun menu(
        id: String,
        label: String,
        icon: OneRingIcon = OneRingIcon.None,
        enabled: Boolean = true,
        contentDescription: String? = null,
        closeIcon: OneRingIcon? = null,
        content: OneRingItemsScope.() -> Unit,
    ) {
        val children = OneRingItemsScope().apply(content).build()
        items += OneRingItem.Menu(id, label, icon, enabled, contentDescription, children, closeIcon)
    }

    public fun seek(
        id: String,
        label: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        icon: OneRingIcon = OneRingIcon.None,
        enabled: Boolean = true,
        contentDescription: String? = null,
        valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
        steps: Int = 0,
        onValueChangeFinished: (() -> Unit)? = null,
        valueLabel: ((Float) -> String)? = null,
        closeIcon: OneRingIcon? = null,
    ) {
        items += OneRingItem.Seek(
            id = id,
            label = label,
            icon = icon,
            enabled = enabled,
            contentDescription = contentDescription,
            value = value,
            valueRange = valueRange,
            steps = steps,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueLabel = valueLabel,
            closeIcon = closeIcon,
        )
    }

    internal fun build(): List<OneRingItem> {
        val duplicates = items.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate item ids among siblings: $duplicates" }
        return items.toList()
    }
}

/**
 * Builds a top-level [OneRingItem] list, e.g.:
 * ```
 * val items = oneRingItems {
 *     menu("music", "Music") {
 *         action("prev", "Previous")
 *         seek("vol", "Volume", value = musicVolume, onValueChange = { musicVolume = it })
 *     }
 *     seek("volume", "Volume", value = volume, onValueChange = { volume = it })
 * }
 * ```
 */
public fun oneRingItems(content: OneRingItemsScope.() -> Unit): List<OneRingItem> =
    OneRingItemsScope().apply(content).build()
