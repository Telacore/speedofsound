package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.APPLICATION_NAME
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_LAST_CHECK_AT
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_LAST_TRIGGERED_DATES
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_SCHEDULER_STATE
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
import kotlin.time.Duration.Companion.milliseconds

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
        val schedulerStateChanged = reloadSchedulerState()
        schedulerStateReady = true
        if (schedulerStateChanged) {
            persistSchedulerState()
        }
        settingsJob = scope.launch {
            settingsClient.settingsChanged.collect { key ->
                onSettingsChanged(key)
            }
        }
        schedulerJob = scope.launch {
            while (true) {
                checkAlarms()
                delay(millisUntilNextMinuteBoundary(LocalDateTime.now(clock)).milliseconds)
            }
        }
    }

    internal fun reloadAlarms() {
        val alarms = settingsClient.peekAlarms().sortedWith(
            compareBy<AlarmSetting> { it.hour }
                .thenBy { it.minute }
                .thenBy { it.action.ordinal }
                .thenBy { it.id }
        )
        val activeIds = alarms.map { it.id }.toSet()
        val historyChanged = synchronized(stateLock) {
            val previousHistoryKeys = lastTriggeredDates.keys.toSet()
            activeAlarms = alarms
            lastTriggeredDates.keys.retainAll(activeIds)
            previousHistoryKeys != lastTriggeredDates.keys
        }
        if (schedulerStateReady && historyChanged) {
            persistSchedulerState()
        }
        logger.info("Loaded {} alarm(s).", alarms.size)
    }

    internal fun reloadSchedulerState(): Boolean {
        val schedulerState = settingsClient.peekAlarmSchedulerState()
        val now = LocalDateTime.now(clock)
        val changed = synchronized(stateLock) {
            val activeIds = activeAlarms.map { it.id }.toSet()
            val nextTriggeredDates = schedulerState.lastTriggeredDates.mapNotNull { (alarmId, dateValue) ->
                runCatching { LocalDate.parse(dateValue) }
                    .getOrNull()
                    ?.let { parsedDate -> alarmId to parsedDate }
            }.toMap().filterKeys { it in activeIds }
            val nextLastCheckAt = schedulerState.lastCheckAt?.let { rawValue ->
                runCatching { LocalDateTime.parse(rawValue) }.getOrNull()
            }?.takeIf { !it.isAfter(now) }

            val changed = lastTriggeredDates != nextTriggeredDates || lastCheckAt != nextLastCheckAt
            lastTriggeredDates.clear()
            lastTriggeredDates.putAll(nextTriggeredDates)
            lastCheckAt = nextLastCheckAt
            changed
        }
        logger.info("Loaded alarm scheduler state.")
        return changed
    }

    internal fun onSettingsChanged(key: String) {
        when (key) {
            KEY_ALARMS -> reloadAlarms()
            KEY_ALARM_SCHEDULER_STATE, KEY_ALARM_LAST_TRIGGERED_DATES, KEY_ALARM_LAST_CHECK_AT -> {
                val schedulerStateChanged = reloadSchedulerState()
                if (schedulerStateReady && schedulerStateChanged) {
                    persistSchedulerState()
                }
            }
        }
    }

    internal fun snapshotSchedulerState(): AlarmSchedulerState = synchronized(stateLock) {
        AlarmSchedulerState(
            lastCheckAt = lastCheckAt?.toString(),
            lastTriggeredDates = lastTriggeredDates.mapValues { (_, date) -> date.toString() },
        )
    }

    internal fun checkAlarms() {
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
                lastCheckAt = lastCheckAt
                    ?.withSecond(0)
                    ?.withNano(0)
                    ?.toString(),
                lastTriggeredDates = lastTriggeredDates.mapValues { (_, date) -> date.toString() },
            )
        }
        if (snapshot == settingsClient.peekAlarmSchedulerState()) {
            return
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
