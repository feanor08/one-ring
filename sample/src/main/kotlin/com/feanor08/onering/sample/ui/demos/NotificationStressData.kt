package com.feanor08.onering.sample.ui.demos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.notification.OneRingNotificationPost

/**
 * Pre-built large-stack payloads for [NotificationStackStressDemo] -- a
 * dedicated screen for visually checking the notification stack's
 * (`NotificationGroupSlot`) collapse/expand behavior at 8 and 20 real
 * threads, with deliberately unequal message lengths so the collapsed height
 * cap (`OneRingNotificationDimensions.collapsedCardMaxHeightFraction`) and
 * unequal-height rear-card geometry both get exercised, not just the common
 * short-message case.
 *
 * Deliberately vehicle-alert/system-update flavored, not chat-message
 * flavored -- distinct system components each firing once reads more
 * naturally as "8/20 unrelated things happened" than 8/20 people all texting
 * at once would, and keeps this screen visually distinct from
 * [NotificationDemo]'s WhatsApp-style stack.
 */
internal object NotificationStressData {
    private val VehicleColor = Color(0xFFF0B64D)
    private val SystemColor = Color(0xFF5B8FF5)

    private val VehicleIcon = OneRingIcon.Vector(Icons.Rounded.WarningAmber)
    private val SystemIcon = OneRingIcon.Vector(Icons.Rounded.SystemUpdate)

    private val vehicleAlerts = listOf(
        "Tyre Pressure" to "Rear left tyre is low.",
        "Battery Alert" to "12V battery voltage is low.",
        "Oil Change Due" to "Oil life at 10%. Schedule a change soon.",
        "Brake Wear" to "Front brake pads are wearing thin -- inspection recommended within 1,000 km.",
        "Coolant Low" to "Coolant level below minimum.",
        "Washer Fluid Low" to "Windshield washer fluid is running low.",
        "Door Ajar" to "Rear passenger door is not fully closed.",
        "Engine Temperature" to "Engine running hotter than normal -- pull over when safe and let it cool before continuing.",
    )

    private val systemUpdates = listOf(
        "Maps" to "New map data available for your region.",
        "Music" to "App update ready to install.",
        "Bluetooth Stack" to "Firmware update improves pairing reliability with newer phones.",
        "Camera Firmware" to "Update available for the reversing camera module.",
        "Climate Control" to "Minor update available.",
        "Infotainment" to "System update ready -- includes performance improvements and bug fixes.",
        "Navigation" to "Voice guidance package updated.",
        "Voice Assistant" to "New wake-word model available for download.",
        "Security Module" to "Security patch available -- recommended to install as soon as possible.",
        "Telematics" to "Connectivity module firmware update ready.",
        "Dashboard UI" to "Cosmetic update available.",
        "Charging Controller" to "Update improves fast-charging compatibility with more public chargers.",
        "Battery Management" to "Update available.",
        "Driver Profile" to "Sync update ready.",
        "Parking Assist" to "Sensor calibration update available -- improves distance accuracy in low light.",
        "Cruise Control" to "Minor update available.",
        "Lane Assist" to "Camera calibration update ready to install.",
        "Backup Camera" to "Firmware update available.",
        "Wireless CarPlay" to "Connectivity update improves reconnection time after standby.",
        "OTA Updater" to "The update service itself has a new version available.",
    )

    private fun stack(
        sourceKey: String,
        sourceLabel: String,
        icon: OneRingIcon,
        accentColor: Color,
        entries: List<Pair<String, String>>,
    ): List<OneRingNotificationPost> = entries.mapIndexed { i, (title, message) ->
        OneRingNotificationPost(
            threadKey = "$sourceKey:$i",
            sourceKey = sourceKey,
            sourceLabel = sourceLabel,
            sourceIcon = icon,
            icon = icon,
            accentColor = accentColor,
            title = title,
            message = message,
        )
    }

    /** Eight distinct vehicle alerts under one source -- well past the two-deep visible rear cap, exercising "child 3+" placement. */
    fun eightThreadStack(): List<OneRingNotificationPost> =
        stack("stress-vehicle", "Vehicle", VehicleIcon, VehicleColor, vehicleAlerts)

    /** Twenty distinct system updates under one source -- the largest case called out in the plan's performance checks. */
    fun twentyThreadStack(): List<OneRingNotificationPost> =
        stack("stress-system", "System", SystemIcon, SystemColor, systemUpdates)
}
