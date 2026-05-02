@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.kavishdevar.librepods.services.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import me.kavishdevar.librepods.MainActivity
import me.kavishdevar.librepods.R
import me.kavishdevar.librepods.data.Battery
import me.kavishdevar.librepods.data.BatteryComponent
import me.kavishdevar.librepods.data.BatteryStatus

object LiveUpdateNotification {
    const val CHANNEL_ID = "airpods_live_update"
    const val NOTIF_ID_MAIN = 4
    const val NOTIF_ID_LOW_LEFT = 5
    const val NOTIF_ID_LOW_RIGHT = 6
    const val NOTIF_ID_LOW_CASE = 7
    const val NOTIF_ID_CASE_OPEN = 8

    private val state = LiveUpdateState()

    fun resetState() {
        state.reset()
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.live_update_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.live_update_channel_description)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    fun show(
        context: Context,
        airpodsName: String,
        batteryList: List<Battery>,
        headsUp: Boolean
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val builder = buildMain(context, airpodsName, batteryList, headsUp)
        nm.notify(NOTIF_ID_MAIN, builder.build())
    }

    fun update(
        context: Context,
        airpodsName: String,
        batteryList: List<Battery>
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val builder = buildMain(context, airpodsName, batteryList, headsUp = false)
        nm.notify(NOTIF_ID_MAIN, builder.build())
    }

    fun checkLowBattery(context: Context, batteryList: List<Battery>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pi = mainActivityPendingIntent(context)

        batteryList.forEach { battery ->
            if (battery.status == BatteryStatus.DISCONNECTED) return@forEach
            if (!state.shouldFireLowBattery(battery.component, battery.level)) return@forEach

            val (titleRes, notifId) = when (battery.component) {
                BatteryComponent.LEFT -> R.string.live_update_low_battery_left to NOTIF_ID_LOW_LEFT
                BatteryComponent.RIGHT -> R.string.live_update_low_battery_right to NOTIF_ID_LOW_RIGHT
                BatteryComponent.CASE -> R.string.live_update_low_battery_case to NOTIF_ID_LOW_CASE
                else -> return@forEach
            }

            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.airpods_live_update_icon)
                .setContentTitle(context.getString(titleRes, battery.level))
                .setContentText(context.getString(R.string.live_update_low_battery_body))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build()
            nm.notify(notifId, notif)
        }
    }

    fun showCaseOpenReminder(context: Context, batteryList: List<Battery>) {
        val caseBattery = batteryList.firstOrNull { it.component == BatteryComponent.CASE }
            ?: return
        if (caseBattery.status == BatteryStatus.DISCONNECTED) return
        if (caseBattery.level <= 0) return
        if (!state.shouldFireCaseOpenReminder(caseBattery.level)) return

        val nm = context.getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.airpods_live_update_icon)
            .setContentTitle(context.getString(R.string.live_update_case_open_title))
            .setContentText(context.getString(R.string.live_update_case_open_body, caseBattery.level))
            .setContentIntent(mainActivityPendingIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()
        nm.notify(NOTIF_ID_CASE_OPEN, notif)
    }

    fun resetCaseOpenReminderOnLidClose() {
        state.resetCaseOpenReminder()
    }

    fun headsUpOnListeningModeChange(
        context: Context,
        airpodsName: String,
        batteryList: List<Battery>,
        newMode: Byte
    ) {
        if (!state.shouldFireListeningModeChange(newMode)) return
        show(context, airpodsName, batteryList, headsUp = true)
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_MAIN)
        nm.cancel(NOTIF_ID_LOW_LEFT)
        nm.cancel(NOTIF_ID_LOW_RIGHT)
        nm.cancel(NOTIF_ID_LOW_CASE)
        nm.cancel(NOTIF_ID_CASE_OPEN)
    }

    private fun buildMain(
        context: Context,
        airpodsName: String,
        batteryList: List<Battery>,
        headsUp: Boolean
    ): NotificationCompat.Builder {
        val left = batteryList.firstOrNull { it.component == BatteryComponent.LEFT }
        val right = batteryList.firstOrNull { it.component == BatteryComponent.RIGHT }
        val case = batteryList.firstOrNull { it.component == BatteryComponent.CASE }

        val lowestEar = listOfNotNull(left, right)
            .filter { it.status != BatteryStatus.DISCONNECTED }
            .minByOrNull { it.level }
            ?.level ?: 0

        val expandedBody = buildString {
            left?.takeIf { it.status != BatteryStatus.DISCONNECTED }?.let {
                append("L ")
                if (it.status == BatteryStatus.CHARGING) append("⚡")
                append("${it.level}%")
            }
            right?.takeIf { it.status != BatteryStatus.DISCONNECTED }?.let {
                if (isNotEmpty()) append(" · ")
                append("R ")
                if (it.status == BatteryStatus.CHARGING) append("⚡")
                append("${it.level}%")
            }
            case?.takeIf { it.status != BatteryStatus.DISCONNECTED }?.let {
                if (isNotEmpty()) append(" · ")
                append("Case ")
                if (it.status == BatteryStatus.CHARGING) append("⚡")
                append("${it.level}%")
            }
        }

        val progressStyle = NotificationCompat.ProgressStyle()
            .setProgress(lowestEar)

        val samsungLiveExtras = Bundle().apply {
            putInt("android.ongoingActivityNoti.style", 1)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.airpods_live_update_icon)
            .setContentTitle(airpodsName)
            .setContentText(if (expandedBody.isNotEmpty()) expandedBody else "$lowestEar%")
            .setShortCriticalText("$lowestEar%")
            .setStyle(progressStyle)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(mainActivityPendingIntent(context))
            .setPriority(if (headsUp) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(!headsUp)
            .setRequestPromotedOngoing(true)
            .addExtras(samsungLiveExtras)
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
