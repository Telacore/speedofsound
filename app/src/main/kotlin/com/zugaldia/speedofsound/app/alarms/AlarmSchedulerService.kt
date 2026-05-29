package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.APPLICATION_NAME
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.seconds

class AlarmSchedulerService(
    private val settingsClient: SettingsClient,
    private val portalsClient: PortalsClient,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val checkIntervalSeconds: Long = 15,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(AlarmSchedulerService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateLock = Any()

    private var settingsJob: Job? = null
    private var schedulerJob: Job? = null
    private var activeAlarms: List<AlarmSetting> = emptyList()
    private val lastTriggeredDates = mutableMapOf<String, LocalDate>()

    fun connect() {
        if (schedulerJob != null) {
            return
        }

        reloadAlarms()
        settingsJob = scope.launch {
            settingsClient.settingsChanged.collect { key ->
                if (key == KEY_ALARMS) {
                    reloadAlarms()
                }
            }
        }
        schedulerJob = scope.launch {
            while (true) {
                checkAlarms()
                delay(checkIntervalSeconds.seconds)
            }
        }
    }

    private fun reloadAlarms() {
        val alarms = settingsClient.getAlarms().sortedWith(
            compareBy<AlarmSetting> { it.hour }
                .thenBy { it.minute }
                .thenBy { it.action.ordinal }
                .thenBy { it.id }
        )
        synchronized(stateLock) {
            activeAlarms = alarms
            lastTriggeredDates.keys.retainAll(alarms.map { it.id }.toSet())
        }
        logger.info("Loaded {} alarm(s).", alarms.size)
    }

    private fun checkAlarms() {
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        val dueAlarms = synchronized(stateLock) {
            activeAlarms.filter { alarm ->
                alarm.enabled &&
                    lastTriggeredDates[alarm.id] != today &&
                    isAlarmDue(now, alarm)
            }.also { alarms ->
                alarms.forEach { alarm ->
                    lastTriggeredDates[alarm.id] = today
                }
            }
        }

        dueAlarms.forEach { fireAlarm(it) }
    }

    private fun fireAlarm(alarm: AlarmSetting) {
        val body = formatAlarmNotificationBody(alarm)
        logger.info("Firing alarm {} with action {}", alarm.id, alarm.action)
        if (!shouldNotifyAlarm(alarm.action)) {
            logger.info("Alarm {} is silent; skipping desktop notification.", alarm.id)
            return
        }
        portalsClient.showNotification(
            title = APPLICATION_NAME,
            body = body,
            priority = alarmNotificationPriority(alarm.action)
        )
    }

    override fun close() {
        settingsJob?.cancel()
        schedulerJob?.cancel()
        settingsJob = null
        schedulerJob = null
        scope.cancel()
    }
}
