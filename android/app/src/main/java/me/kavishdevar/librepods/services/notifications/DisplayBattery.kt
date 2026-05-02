package me.kavishdevar.librepods.services.notifications

import me.kavishdevar.librepods.data.Battery

data class DisplayBattery(
    val battery: Battery,
    val isRemembered: Boolean,
    val rememberedAtMillis: Long?
) {
    val component: Int get() = battery.component
    val level: Int get() = battery.level
    val status: Int get() = battery.status
}
