package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmRepeatDay
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.stargate.sdk.notification.NotificationPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDateTime

class AlarmScheduleTest {
    @Test
    fun `alarm is due within the grace window`() {
        val alarm = AlarmSetting(id = "alarm-1", hour = 7, minute = 30)
        val dueMoment = LocalDateTime.of(2026, 5, 29, 7, 34, 59)
        val beforeWindow = LocalDateTime.of(2026, 5, 29, 7, 29, 59)
        val afterWindow = LocalDateTime.of(2026, 5, 29, 7, 35, 1)

        assertTrue(isAlarmDue(dueMoment, alarm))
        assertFalse(isAlarmDue(beforeWindow, alarm))
        assertFalse(isAlarmDue(afterWindow, alarm))
    }

    @Test
    fun `alarm is due only on matching repeat days`() {
        val fridayOnlyAlarm = AlarmSetting(
            id = "alarm-1",
            hour = 7,
            minute = 30,
            repeatDays = listOf(AlarmRepeatDay.FRIDAY)
        )
        val matchingDay = LocalDateTime.of(2026, 5, 29, 7, 34, 59)
        val nonMatchingDay = LocalDateTime.of(2026, 6, 1, 7, 34, 59)

        assertTrue(isAlarmDue(matchingDay, fridayOnlyAlarm))
        assertFalse(isAlarmDue(nonMatchingDay, fridayOnlyAlarm))
    }

    @Test
    fun `alarm action maps to notification priority`() {
        assertEquals(NotificationPriority.LOW, alarmNotificationPriority(AlarmAction.SILENT))
        assertEquals(NotificationPriority.NORMAL, alarmNotificationPriority(AlarmAction.NORMAL))
        assertEquals(NotificationPriority.HIGH, alarmNotificationPriority(AlarmAction.ATTENTION))
        assertEquals(NotificationPriority.URGENT, alarmNotificationPriority(AlarmAction.URGENT))
    }

    @Test
    fun `silent alarms do not notify`() {
        assertFalse(shouldNotifyAlarm(AlarmAction.SILENT))
        assertTrue(shouldNotifyAlarm(AlarmAction.NORMAL))
    }

    @Test
    fun `alarm time formatting is zero padded`() {
        val alarm = AlarmSetting(id = "alarm-2", hour = 5, minute = 7)
        assertEquals("05:07", formatAlarmTime(alarm))
    }

    @Test
    fun `alarm name falls back to time when blank`() {
        val unnamedAlarm = AlarmSetting(id = "alarm-3", hour = 9, minute = 45)
        val namedAlarm = AlarmSetting(id = "alarm-4", name = "Breakfast", hour = 9, minute = 45)

        assertEquals("Alarm 09:45", formatAlarmName(unnamedAlarm))
        assertEquals("Breakfast", formatAlarmName(namedAlarm))
    }

    @Test
    fun `alarm notification body uses name when present`() {
        val unnamedAlarm = AlarmSetting(id = "alarm-5", hour = 9, minute = 45)
        val namedAlarm = AlarmSetting(id = "alarm-6", name = "Breakfast", hour = 9, minute = 45)

        assertEquals("Alarm 09:45 is due. (Normal)", formatAlarmNotificationBody(unnamedAlarm))
        assertEquals("Breakfast is due at 09:45. (Normal)", formatAlarmNotificationBody(namedAlarm))
    }

    @Test
    fun `alarm notification body includes the scheduled day when provided`() {
        val alarm = AlarmSetting(id = "alarm-8", name = "Breakfast", hour = 9, minute = 45)
        val scheduledAt = LocalDateTime.of(2026, 5, 29, 9, 45)

        assertEquals(
            "Breakfast is due on Fri at 09:45. (Normal)",
            formatAlarmNotificationBody(alarm, scheduledAt)
        )
    }

    @Test
    fun `alarm summary formats repeat days`() {
        val alarm = AlarmSetting(
            id = "alarm-7",
            name = "Gym",
            hour = 18,
            minute = 15,
            repeatDays = listOf(AlarmRepeatDay.MONDAY, AlarmRepeatDay.WEDNESDAY, AlarmRepeatDay.FRIDAY)
        )

        assertEquals("Mon, Wed, Fri • Normal", formatAlarmSummary(alarm))
    }

    @Test
    fun `alarm repeat day formatter collapses common groups`() {
        assertEquals(
            "Weekdays",
            formatRepeatDays(
                listOf(
                    AlarmRepeatDay.MONDAY,
                    AlarmRepeatDay.TUESDAY,
                    AlarmRepeatDay.WEDNESDAY,
                    AlarmRepeatDay.THURSDAY,
                    AlarmRepeatDay.FRIDAY,
                )
            )
        )
        assertEquals(
            "Weekends",
            formatRepeatDays(
                listOf(
                    AlarmRepeatDay.SATURDAY,
                    AlarmRepeatDay.SUNDAY,
                )
            )
        )
    }

    @Test
    fun `alarm overview highlights the next active alarm`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0)
        val alarms = listOf(
            AlarmSetting(id = "alarm-1", name = "Morning", hour = 7, minute = 30),
            AlarmSetting(id = "alarm-2", name = "Lunch", hour = 12, minute = 0),
            AlarmSetting(id = "alarm-3", name = "Disabled", hour = 18, minute = 0, enabled = false),
        )

        assertEquals(
            "2 active alarms · next Lunch at 12:00",
            formatAlarmOverview(now, alarms)
        )
    }

    @Test
    fun `alarm overview respects repeat days`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0)
        val alarms = listOf(
            AlarmSetting(
                id = "alarm-1",
                name = "Weekly",
                hour = 9,
                minute = 0,
                repeatDays = listOf(AlarmRepeatDay.MONDAY),
            ),
        )

        assertEquals(
            "1 active alarm · next Weekly on Mon at 09:00",
            formatAlarmOverview(now, alarms)
        )
    }

    @Test
    fun `alarm overview falls back when all alarms are disabled`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0)
        val alarms = listOf(
            AlarmSetting(id = "alarm-1", name = "Morning", hour = 7, minute = 30, enabled = false),
        )

        assertEquals("All alarms disabled", formatAlarmOverview(now, alarms))
    }

    @Test
    fun `alarm overview marks a recently missed alarm as due now`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 3, 15)
        val alarms = listOf(
            AlarmSetting(id = "alarm-1", name = "Morning", hour = 9, minute = 0),
        )

        assertEquals(
            "1 active alarm · next Morning due now",
            formatAlarmOverview(now, alarms)
        )
    }

    @Test
    fun `catch up helper finds the most recent missed occurrence in a range`() {
        val start = LocalDateTime.of(2026, 5, 29, 8, 0)
        val end = LocalDateTime.of(2026, 5, 29, 10, 0)
        val alarm = AlarmSetting(id = "alarm-1", name = "Morning", hour = 9, minute = 0)

        assertEquals(
            LocalDateTime.of(2026, 5, 29, 9, 0),
            findMostRecentDueOccurrenceSince(start, end, alarm)
        )
    }

    @Test
    fun `catch up helper returns every missed occurrence in order`() {
        val start = LocalDateTime.of(2026, 5, 29, 8, 0)
        val end = LocalDateTime.of(2026, 5, 31, 10, 0)
        val alarm = AlarmSetting(id = "alarm-3", name = "Daily", hour = 9, minute = 0)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 5, 29, 9, 0),
                LocalDateTime.of(2026, 5, 30, 9, 0),
                LocalDateTime.of(2026, 5, 31, 9, 0),
            ),
            findDueOccurrencesSince(start, end, alarm).map { it.scheduledAt }
        )
    }

    @Test
    fun `catch up helper spans midnight`() {
        val start = LocalDateTime.of(2026, 5, 29, 23, 59)
        val end = LocalDateTime.of(2026, 5, 30, 0, 2)
        val alarm = AlarmSetting(id = "alarm-2", name = "Night", hour = 23, minute = 58)

        assertEquals(
            LocalDateTime.of(2026, 5, 29, 23, 58),
            findMostRecentDueOccurrenceSince(start, end, alarm)
        )
    }

    @Test
    fun `alarm summary refresh delay rolls to the next minute`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0, 30)

        assertEquals(30000L, millisUntilNextMinuteBoundary(now))
        assertEquals(30000L, millisUntilNextAlarmSummaryRefresh(now))
    }

    @Test
    fun `alarm check window start clamps future last check times`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0, 30)
        val futureLastCheckAt = LocalDateTime.of(2026, 5, 29, 10, 0, 0)

        assertEquals(
            LocalDateTime.of(2026, 5, 29, 9, 0, 15),
            resolveAlarmCheckWindowStart(futureLastCheckAt, now, 15)
        )
    }
}
