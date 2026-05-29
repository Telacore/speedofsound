package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.stargate.sdk.notification.NotificationPriority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDateTime

class AlarmScheduleTest {
    @Test
    fun `alarm is due only within the matching minute`() {
        val alarm = AlarmSetting(id = "alarm-1", hour = 7, minute = 30)
        val dueMoment = LocalDateTime.of(2026, 5, 29, 7, 30, 15)
        val beforeWindow = LocalDateTime.of(2026, 5, 29, 7, 29, 59)
        val afterWindow = LocalDateTime.of(2026, 5, 29, 7, 31, 0)

        assertTrue(isAlarmDue(dueMoment, alarm))
        assertFalse(isAlarmDue(beforeWindow, alarm))
        assertFalse(isAlarmDue(afterWindow, alarm))
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
    fun `alarm overview falls back when all alarms are disabled`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0)
        val alarms = listOf(
            AlarmSetting(id = "alarm-1", name = "Morning", hour = 7, minute = 30, enabled = false),
        )

        assertEquals("All alarms disabled", formatAlarmOverview(now, alarms))
    }

    @Test
    fun `alarm summary refresh delay rolls to the next minute`() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0, 30)

        assertEquals(30000L, millisUntilNextAlarmSummaryRefresh(now))
    }
}
