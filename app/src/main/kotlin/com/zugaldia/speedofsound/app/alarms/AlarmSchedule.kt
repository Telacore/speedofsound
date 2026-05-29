package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.stargate.sdk.notification.NotificationPriority
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

private const val ALARM_TRIGGER_GRACE_MINUTES = 5L

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

data class AlarmOccurrence(
    val alarm: AlarmSetting,
    val nextRun: LocalDateTime,
    val dueNow: Boolean,
)

fun nextAlarmOccurrence(now: LocalDateTime, alarms: List<AlarmSetting>): AlarmOccurrence? {
    val reference = now.withSecond(0).withNano(0)
    return alarms.asSequence()
        .filter { it.enabled }
        .map { alarm ->
            val candidate = reference.toLocalDate().atTime(alarm.hour, alarm.minute)
            val dueNow = isWithinAlarmGraceWindow(now, candidate)
            val nextRun = if (candidate.isBefore(reference) && !dueNow) candidate.plusDays(1) else candidate
            AlarmOccurrence(alarm = alarm, nextRun = nextRun, dueNow = dueNow)
        }
        .minByOrNull { it.nextRun }
}

fun formatAlarmOverview(now: LocalDateTime, alarms: List<AlarmSetting>): String {
    if (alarms.isEmpty()) {
        return ""
    }

    val activeAlarms = alarms.filter { it.enabled }
    if (activeAlarms.isEmpty()) {
        return "All alarms disabled"
    }

    val occurrence = nextAlarmOccurrence(now, activeAlarms) ?: return ""
    val activeCount = activeAlarms.size
    val activeLabel = if (activeCount == 1) "1 active alarm" else "$activeCount active alarms"
    val label = occurrence.alarm.name.trim().ifBlank { "alarm" }
    val daySuffix = if (occurrence.nextRun.toLocalDate().isEqual(now.toLocalDate())) "" else " tomorrow"
    val timing = if (occurrence.dueNow) {
        "due now"
    } else {
        "$daySuffix at ${formatAlarmTime(occurrence.alarm)}"
    }
    return "$activeLabel · next $label $timing".replace("  ", " ").trim()
}

fun millisUntilNextAlarmSummaryRefresh(now: LocalDateTime): Long {
    val reference = now.withSecond(0).withNano(0)
    val nextMinute = reference.plusMinutes(1)
    return Duration.between(now, nextMinute).toMillis().coerceAtLeast(1)
}

fun isAlarmDue(now: LocalDateTime, alarm: AlarmSetting): Boolean {
    val alarmStart = now.toLocalDate().atTime(alarm.hour, alarm.minute)
    val alarmEnd = alarmStart.plusMinutes(ALARM_TRIGGER_GRACE_MINUTES)
    return !now.isBefore(alarmStart) && !now.isAfter(alarmEnd)
}

private fun isWithinAlarmGraceWindow(now: LocalDateTime, candidate: LocalDateTime): Boolean =
    !now.isBefore(candidate) && !now.isAfter(candidate.plusMinutes(ALARM_TRIGGER_GRACE_MINUTES))
