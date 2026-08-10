package com.feanor08.onering.sample.ui.demos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.graphics.Color
import com.feanor08.onering.OneRingIcon
import com.feanor08.onering.notification.OneRingNotificationPost

/**
 * Dummy notification data + one shared color palette for [NotificationDemo] --
 * WhatsApp/vehicle/cloud concepts live only here, in the sample; the
 * `one-ring` library itself never interprets `sourceKey` at all.
 */
internal object NotificationDemoData {
    val WhatsAppColor = Color(0xFF56C985)
    val SoftwareColor = Color(0xFF5B8FF5)
    val TyrePressureColor = Color(0xFFF0B64D)
    val ServiceColor = Color(0xFF58B8C7)
    val CloudColor = Color(0xFF9B7BEA)
    val BatteryColor = Color(0xFFE7685D)

    private val WhatsAppIcon = OneRingIcon.Vector(Icons.Rounded.Chat)
    private val SoftwareIcon = OneRingIcon.Vector(Icons.Rounded.SystemUpdate)
    private val TyrePressureIcon = OneRingIcon.Vector(Icons.Rounded.WarningAmber)
    private val ServiceIcon = OneRingIcon.Vector(Icons.Rounded.Handyman)
    private val CloudIcon = OneRingIcon.Vector(Icons.Rounded.Cloud)
    private val BatteryIcon = OneRingIcon.Vector(Icons.Rounded.BatteryAlert)

    private val adarshFollowUps = listOf(
        "I'm almost there.",
        "Traffic is moving now.",
        "I'm outside.",
        "Call me when you reach.",
        "Parking near the entrance.",
    )

    fun adarshFirst(): OneRingNotificationPost = whatsApp(
        threadKey = "whatsapp:adarsh",
        title = "Adarsh",
        message = "I'll reach in 10 minutes.",
    )

    /** [index] cycles through a fixed follow-up script -- all of it accumulates in the same card. */
    fun adarshFollowUp(index: Int): OneRingNotificationPost = whatsApp(
        threadKey = "whatsapp:adarsh",
        title = "Adarsh",
        message = adarshFollowUps[index % adarshFollowUps.size],
    )

    fun varsha(): OneRingNotificationPost = whatsApp(
        threadKey = "whatsapp:varsha",
        title = "Varsha",
        message = "Are we still on for tonight?",
    )

    fun family(): OneRingNotificationPost = whatsApp(
        threadKey = "whatsapp:family",
        title = "Family",
        message = "Dinner at 8?",
    )

    private fun whatsApp(threadKey: String, title: String, message: String) = OneRingNotificationPost(
        threadKey = threadKey,
        sourceKey = "whatsapp",
        sourceLabel = "WhatsApp",
        sourceIcon = WhatsAppIcon,
        icon = WhatsAppIcon,
        accentColor = WhatsAppColor,
        title = title,
        message = message,
    )

    fun softwareUpdate(): OneRingNotificationPost = OneRingNotificationPost(
        threadKey = "software:update",
        sourceKey = "software",
        sourceLabel = "Software",
        sourceIcon = SoftwareIcon,
        icon = SoftwareIcon,
        accentColor = SoftwareColor,
        title = "Software Update",
        message = "Version 2.4 is ready to install.",
    )

    fun tyrePressure(): OneRingNotificationPost = OneRingNotificationPost(
        threadKey = "vehicle:tyre-pressure",
        sourceKey = "vehicle",
        sourceLabel = "Vehicle",
        sourceIcon = TyrePressureIcon,
        icon = TyrePressureIcon,
        accentColor = TyrePressureColor,
        title = "Tyre Pressure",
        message = "Rear left tyre is low.",
    )

    fun serviceReminder(): OneRingNotificationPost = OneRingNotificationPost(
        threadKey = "service:reminder",
        sourceKey = "service",
        sourceLabel = "Service",
        sourceIcon = ServiceIcon,
        icon = ServiceIcon,
        accentColor = ServiceColor,
        title = "Service Reminder",
        message = "Your next service is due in 500 km.",
    )

    fun cloudNotification(): OneRingNotificationPost = OneRingNotificationPost(
        threadKey = "cloud:sync",
        sourceKey = "cloud",
        sourceLabel = "Cloud",
        sourceIcon = CloudIcon,
        icon = CloudIcon,
        accentColor = CloudColor,
        title = "Cloud Sync",
        message = "Your trip history has been backed up.",
    )

    fun batteryAlert(): OneRingNotificationPost = OneRingNotificationPost(
        threadKey = "battery:alert",
        sourceKey = "battery",
        sourceLabel = "Battery",
        sourceIcon = BatteryIcon,
        icon = BatteryIcon,
        accentColor = BatteryColor,
        title = "Battery Alert",
        message = "12V battery voltage is low.",
    )
}
