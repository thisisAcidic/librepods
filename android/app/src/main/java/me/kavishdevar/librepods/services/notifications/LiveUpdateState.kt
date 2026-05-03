package me.kavishdevar.librepods.services.notifications

import me.kavishdevar.librepods.data.BatteryComponent

class LiveUpdateState {
    private val lowBatteryFired = mutableMapOf(
        BatteryComponent.LEFT to false,
        BatteryComponent.RIGHT to false,
        BatteryComponent.CASE to false
    )

    var lastListeningMode: Byte? = null
    var caseOpenReminderFired = false

    fun shouldFireLowBattery(component: Int, level: Int): Boolean {
        val previouslyFired = lowBatteryFired[component] ?: false
        return when {
            level <= 15 && !previouslyFired -> {
                lowBatteryFired[component] = true
                true
            }
            level > 30 && previouslyFired -> {
                lowBatteryFired[component] = false
                false
            }
            else -> false
        }
    }

    fun resetCaseOpenReminder() {
        caseOpenReminderFired = false
    }

    fun shouldFireCaseOpenReminder(caseLevel: Int): Boolean {
        if (caseLevel < 30 && !caseOpenReminderFired) {
            caseOpenReminderFired = true
            return true
        }
        return false
    }

    fun shouldFireListeningModeChange(newMode: Byte): Boolean {
        if (lastListeningMode != newMode) {
            lastListeningMode = newMode
            return true
        }
        return false
    }

    fun reset() {
        lowBatteryFired.keys.toList().forEach { lowBatteryFired[it] = false }
        lastListeningMode = null
        caseOpenReminderFired = false
    }
}
