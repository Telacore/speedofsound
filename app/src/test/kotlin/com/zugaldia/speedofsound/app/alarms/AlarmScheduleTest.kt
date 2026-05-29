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
    fun `alarm time formatting is zero padded`() {
        val alarm = AlarmSetting(id = "alarm-2", hour = 5, minute = 7)
        assertEquals("05:07", formatAlarmTime(alarm))
    }
}
