package me.kavishdevar.librepods.services.notifications

import android.content.Context
import androidx.core.content.edit
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus

class RememberedBatteryCache(context: Context) {
    private val prefs = context.getSharedPreferences("remembered_battery", Context.MODE_PRIVATE)

    private data class Entry(val level: Int, val status: Int, val ts: Long)

    fun record(battery: List<Battery>) {
        prefs.edit {
            battery.forEach { b ->
                if (b.status == BatteryStatus.DISCONNECTED) return@forEach
                val key = keyFor(b.component) ?: return@forEach
                putInt("${key}_level", b.level)
                putInt("${key}_status", b.status)
                putLong("${key}_ts", System.currentTimeMillis())
            }
        }
    }

    fun getMerged(
        currentLive: List<Battery>,
        ttlMillis: Long,
        now: Long = System.currentTimeMillis()
    ): List<DisplayBattery> {
        return listOf(BatteryComponent.LEFT, BatteryComponent.RIGHT, BatteryComponent.CASE).map { component ->
            val live = currentLive.firstOrNull { it.component == component }
            if (live != null && live.status != BatteryStatus.DISCONNECTED) {
                DisplayBattery(live, isRemembered = false, rememberedAtMillis = null)
            } else {
                val cached = readEntry(component)
                if (cached != null && now - cached.ts <= ttlMillis) {
                    DisplayBattery(
                        Battery(component, cached.level, BatteryStatus.NOT_CHARGING),
                        isRemembered = true,
                        rememberedAtMillis = cached.ts
                    )
                } else {
                    DisplayBattery(
                        Battery(component, 0, BatteryStatus.DISCONNECTED),
                        isRemembered = false,
                        rememberedAtMillis = null
                    )
                }
            }
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private fun keyFor(component: Int): String? = when (component) {
        BatteryComponent.LEFT -> "left"
        BatteryComponent.RIGHT -> "right"
        BatteryComponent.CASE -> "case"
        else -> null
    }

    private fun readEntry(component: Int): Entry? {
        val key = keyFor(component) ?: return null
        if (!prefs.contains("${key}_ts")) return null
        return Entry(
            level = prefs.getInt("${key}_level", 0),
            status = prefs.getInt("${key}_status", BatteryStatus.NOT_CHARGING),
            ts = prefs.getLong("${key}_ts", 0L)
        )
    }
}
