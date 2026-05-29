package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.APPLICATION_NAME
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
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
    private var lastCheckAt: LocalDateTime? = null
    private var schedulerStateReady = false

    fun connect() {
        if (schedulerJob != null) {
            return
        }

        schedulerStateReady = false
        reloadAlarms()
        reloadTriggerHistory()
        val now = LocalDateTime.now(clock)
        lastCheckAt = settingsClient.peekAlarmLastCheckAt()?.takeIf { !it.isAfter(now) }
        schedulerStateReady = true
        persistSchedulerState()
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
        val alarms = settingsClient.peekAlarms().sortedWith(
            compareBy<AlarmSetting> { it.hour }
                .thenBy { it.minute }
                .thenBy { it.action.ordinal }
                .thenBy { it.id }
        )
        val activeIds = alarms.map { it.id }.toSet()
        synchronized(stateLock) {
            activeAlarms = alarms
            lastTriggeredDates.keys.retainAll(activeIds)
        }
        if (schedulerStateReady) {
            persistSchedulerState()
        }
        logger.info("Loaded {} alarm(s).", alarms.size)
    }

    private fun reloadTriggerHistory() {
        synchronized(stateLock) {
            lastTriggeredDates.clear()
            lastTriggeredDates.putAll(settingsClient.peekAlarmLastTriggeredDates())
            lastTriggeredDates.keys.retainAll(activeAlarms.map { it.id }.toSet())
        }
    }

    private fun checkAlarms() {
        val now = LocalDateTime.now(clock)
        val previousCheck = synchronized(stateLock) {
            resolveAlarmCheckWindowStart(lastCheckAt, now, checkIntervalSeconds)
        }
        val dueAlarmEvents = synchronized(stateLock) {
            activeAlarms.flatMap { alarm ->
                if (!alarm.enabled) {
                    return@flatMap emptyList()
                }

                findDueOccurrencesSince(previousCheck, now, alarm)
                    .filter { occurrence -> lastTriggeredDates[alarm.id] != occurrence.scheduledAt.toLocalDate() }
                    .map { occurrence ->
                        lastTriggeredDates[alarm.id] = occurrence.scheduledAt.toLocalDate()
                        alarm to occurrence.scheduledAt
                    }
            }
        }.sortedWith(
            compareBy<Pair<AlarmSetting, LocalDateTime>> { it.second }
                .thenBy { it.first.id }
        )

        dueAlarmEvents.forEach { (alarm, dueAt) ->
            fireAlarm(alarm, dueAt)
        }

        synchronized(stateLock) {
            lastCheckAt = now
        }
        persistSchedulerState()
    }

    private fun persistSchedulerState() {
        val snapshot = synchronized(stateLock) {
            AlarmSchedulerState(
                lastCheckAt = lastCheckAt?.toString(),
                lastTriggeredDates = lastTriggeredDates.mapValues { (_, date) -> date.toString() },
            )
        }
        if (!settingsClient.setAlarmSchedulerState(snapshot, emitChange = false)) {
            logger.warn("Failed to persist alarm scheduler state.")
        }
    }

    private fun fireAlarm(alarm: AlarmSetting, dueAt: LocalDateTime) {
        val body = formatAlarmNotificationBody(alarm, dueAt)
        logger.info("Firing alarm {} at {} with action {}", alarm.id, dueAt, alarm.action)
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
