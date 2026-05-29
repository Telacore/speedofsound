package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.stargate.sdk.notification.NotificationPriority
import java.time.LocalDateTime
import java.util.Locale

fun formatAlarmTime(alarm: AlarmSetting): String =
    String.format(Locale.ROOT, "%02d:%02d", alarm.hour, alarm.minute)

fun formatAlarmName(alarm: AlarmSetting): String =
    alarm.name.trim().ifBlank { "Alarm ${formatAlarmTime(alarm)}" }

fun formatAlarmNotificationBody(alarm: AlarmSetting): String {
    val formattedTime = formatAlarmTime(alarm)
    val trimmedName = alarm.name.trim()
    return if (trimmedName.isBlank()) {
        "Alarm $formattedTime is due. (${formatAlarmAction(alarm.action)})"
    } else {
        "$trimmedName is due at $formattedTime. (${formatAlarmAction(alarm.action)})"
    }
}

fun formatAlarmAction(action: AlarmAction): String = when (action) {
    AlarmAction.SILENT -> "Silent"
    AlarmAction.NORMAL -> "Normal"
    AlarmAction.ATTENTION -> "Attention"
    AlarmAction.URGENT -> "Urgent"
}

fun formatAlarmSummary(alarm: AlarmSetting): String = buildString {
    if (!alarm.enabled) {
        append("Disabled • ")
    }
    append("Daily • ")
    append(formatAlarmAction(alarm.action))
}

fun alarmNotificationPriority(action: AlarmAction): NotificationPriority = when (action) {
    AlarmAction.SILENT -> NotificationPriority.LOW
    AlarmAction.NORMAL -> NotificationPriority.NORMAL
    AlarmAction.ATTENTION -> NotificationPriority.HIGH
    AlarmAction.URGENT -> NotificationPriority.URGENT
}

fun shouldNotifyAlarm(action: AlarmAction): Boolean =
    action != AlarmAction.SILENT

fun isAlarmDue(now: LocalDateTime, alarm: AlarmSetting): Boolean {
    val alarmStart = now.toLocalDate().atTime(alarm.hour, alarm.minute)
    val alarmEnd = alarmStart.plusMinutes(1)
    return !now.isBefore(alarmStart) && now.isBefore(alarmEnd)
}
