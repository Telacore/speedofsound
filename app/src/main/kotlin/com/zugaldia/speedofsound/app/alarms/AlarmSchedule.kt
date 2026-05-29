package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.AlarmRepeatDay
import com.zugaldia.speedofsound.core.desktop.settings.allAlarmRepeatDays
import com.zugaldia.speedofsound.core.desktop.settings.isScheduledOn
import com.zugaldia.speedofsound.core.desktop.settings.normalizedRepeatDays
import com.zugaldia.speedofsound.core.desktop.settings.weekdayAlarmRepeatDays
import com.zugaldia.speedofsound.core.desktop.settings.weekendAlarmRepeatDays
import com.zugaldia.speedofsound.core.desktop.settings.shortLabel
import com.zugaldia.stargate.sdk.notification.NotificationPriority
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale

internal const val ALARM_TRIGGER_GRACE_MINUTES = 5L

fun formatAlarmTime(alarm: AlarmSetting): String =
    String.format(Locale.ROOT, "%02d:%02d", alarm.hour, alarm.minute)

fun formatAlarmName(alarm: AlarmSetting): String =
    alarm.name.trim().ifBlank { "Alarm ${formatAlarmTime(alarm)}" }

fun formatAlarmNotificationBody(alarm: AlarmSetting): String =
    formatAlarmNotificationBody(alarm, null)

fun formatAlarmNotificationBody(alarm: AlarmSetting, scheduledAt: LocalDateTime?): String {
    val formattedTime = formatAlarmTime(alarm)
    val trimmedName = alarm.name.trim()
    val whenPart = scheduledAt?.let { " on ${it.dayOfWeek.shortLabel()}" }.orEmpty()
    return if (trimmedName.isBlank()) {
        "Alarm is due$whenPart at $formattedTime. (${formatAlarmAction(alarm.action)})"
    } else {
        "$trimmedName is due$whenPart at $formattedTime. (${formatAlarmAction(alarm.action)})"
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
    append(formatRepeatDays(alarm.repeatDays))
    append(" • ")
    append(formatAlarmAction(alarm.action))
}

fun formatRepeatDays(repeatDays: List<AlarmRepeatDay>): String {
    val normalized = repeatDays.normalizedRepeatDays()
    return when {
        normalized == allAlarmRepeatDays() -> "Daily"
        normalized == weekdayAlarmRepeatDays() -> "Weekdays"
        normalized == weekendAlarmRepeatDays() -> "Weekends"
        else -> normalized.joinToString(", ") { it.shortLabel() }
    }
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

data class AlarmDueOccurrence(
    val alarm: AlarmSetting,
    val scheduledAt: LocalDateTime,
)

fun nextAlarmOccurrence(now: LocalDateTime, alarms: List<AlarmSetting>): AlarmOccurrence? {
    val reference = now.withSecond(0).withNano(0)
    return alarms.asSequence()
        .filter { it.enabled }
        .mapNotNull { alarm -> nextAlarmOccurrenceForAlarm(reference, now, alarm) }
        .minByOrNull { it.nextRun }
}

fun formatAlarmOverview(now: LocalDateTime, alarms: List<AlarmSetting>): String {
    if (alarms.isEmpty()) {
        return "No alarms configured"
    }

    val activeAlarms = alarms.filter { it.enabled }
    if (activeAlarms.isEmpty()) {
        return "All alarms disabled"
    }

    val occurrence = nextAlarmOccurrence(now, activeAlarms) ?: return ""
    val activeCount = activeAlarms.size
    val activeLabel = if (activeCount == 1) "1 active alarm" else "$activeCount active alarms"
    val label = occurrence.alarm.name.trim().ifBlank { "alarm" }
    val timing = formatOccurrenceTiming(now, occurrence)
    return "$activeLabel · next $label $timing".trim()
}

fun millisUntilNextMinuteBoundary(now: LocalDateTime): Long {
    val reference = now.withSecond(0).withNano(0)
    val nextMinute = reference.plusMinutes(1)
    return Duration.between(now, nextMinute).toMillis().coerceAtLeast(1)
}

fun millisUntilNextAlarmSummaryRefresh(now: LocalDateTime): Long =
    millisUntilNextMinuteBoundary(now)

fun resolveAlarmCheckWindowStart(
    lastCheckAt: LocalDateTime?,
    now: LocalDateTime,
    fallbackIntervalSeconds: Long,
): LocalDateTime =
    lastCheckAt?.takeIf { !it.isAfter(now) } ?: now.minusSeconds(fallbackIntervalSeconds)

fun isAlarmDue(now: LocalDateTime, alarm: AlarmSetting): Boolean {
    if (!alarm.isScheduledOn(now.toLocalDate())) {
        return false
    }
    val alarmStart = now.toLocalDate().atTime(alarm.hour, alarm.minute)
    val alarmEnd = alarmStart.plusMinutes(ALARM_TRIGGER_GRACE_MINUTES)
    return !now.isBefore(alarmStart) && !now.isAfter(alarmEnd)
}

fun findMostRecentDueOccurrenceSince(
    start: LocalDateTime,
    end: LocalDateTime,
    alarm: AlarmSetting,
): LocalDateTime? {
    return findDueOccurrencesSince(start, end, alarm).lastOrNull()?.scheduledAt
}

private fun nextAlarmOccurrenceForAlarm(
    reference: LocalDateTime,
    now: LocalDateTime,
    alarm: AlarmSetting,
): AlarmOccurrence? {
    val today = reference.toLocalDate()
    for (offset in 0..7) {
        val date = today.plusDays(offset.toLong())
        if (!alarm.isScheduledOn(date)) {
            continue
        }

        val candidate = date.atTime(alarm.hour, alarm.minute)
        if (offset == 0) {
            if (isWithinAlarmGraceWindow(now, candidate)) {
                return AlarmOccurrence(alarm = alarm, nextRun = candidate, dueNow = true)
            }
            if (!candidate.isBefore(reference)) {
                return AlarmOccurrence(alarm = alarm, nextRun = candidate, dueNow = false)
            }
            continue
        }

        return AlarmOccurrence(alarm = alarm, nextRun = candidate, dueNow = false)
    }

    return null
}

fun findDueOccurrencesSince(
    start: LocalDateTime,
    end: LocalDateTime,
    alarm: AlarmSetting,
): List<AlarmDueOccurrence> {
    val normalizedStart = start.withSecond(0).withNano(0)
    val normalizedEnd = end.withSecond(0).withNano(0)
    if (normalizedEnd.isBefore(normalizedStart)) {
        return emptyList()
    }

    val occurrences = mutableListOf<AlarmDueOccurrence>()
    var date = normalizedStart.toLocalDate().minusDays(1)
    val lastDate = normalizedEnd.toLocalDate()
    while (!date.isAfter(lastDate)) {
        if (alarm.isScheduledOn(date)) {
            val candidate = date.atTime(alarm.hour, alarm.minute)
            val windowEnd = candidate.plusMinutes(ALARM_TRIGGER_GRACE_MINUTES)
            if (!candidate.isAfter(normalizedEnd) && !windowEnd.isBefore(normalizedStart)) {
                occurrences.add(AlarmDueOccurrence(alarm = alarm, scheduledAt = candidate))
            }
        }
        date = date.plusDays(1)
    }

    return occurrences
}

private fun formatOccurrenceTiming(now: LocalDateTime, occurrence: AlarmOccurrence): String {
    if (occurrence.dueNow) {
        return "due now"
    }

    val alarmTime = formatAlarmTime(occurrence.alarm)
    val occurrenceDate = occurrence.nextRun.toLocalDate()
    return when {
        occurrenceDate.isEqual(now.toLocalDate()) -> "at $alarmTime"
        occurrenceDate.isEqual(now.toLocalDate().plusDays(1)) -> "tomorrow at $alarmTime"
        else -> "on ${occurrence.nextRun.dayOfWeek.shortLabel()} at $alarmTime"
    }
}

private fun isWithinAlarmGraceWindow(now: LocalDateTime, candidate: LocalDateTime): Boolean =
    !now.isBefore(candidate) && !now.isAfter(candidate.plusMinutes(ALARM_TRIGGER_GRACE_MINUTES))
