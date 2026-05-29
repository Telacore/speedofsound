package com.zugaldia.speedofsound.app.screens.preferences.alarms

import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.ICON_ALARM
import com.zugaldia.speedofsound.app.ICON_EDIT
import com.zugaldia.speedofsound.app.ICON_TRASH
import com.zugaldia.speedofsound.app.STYLE_CLASS_BOXED_LIST
import com.zugaldia.speedofsound.app.STYLE_CLASS_DIM_LABEL
import com.zugaldia.speedofsound.app.STYLE_CLASS_FLAT
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.app.alarms.formatAlarmName
import com.zugaldia.speedofsound.app.alarms.formatAlarmSummary
import com.zugaldia.speedofsound.app.alarms.formatAlarmTime
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import org.gnome.adw.ActionRow
import org.gnome.adw.PreferencesGroup
import org.gnome.adw.PreferencesPage
import org.gnome.adw.SpinRow
import org.gnome.gtk.Align
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Label
import org.gnome.gtk.ListBox
import org.gnome.gtk.Orientation
import org.gnome.gtk.SelectionMode
import org.gnome.gtk.Switch
import org.slf4j.LoggerFactory

class AlarmsPage(private val viewModel: PreferencesViewModel) : PreferencesPage() {
    private val logger = LoggerFactory.getLogger(AlarmsPage::class.java)

    private val alarmsListBox: ListBox
    private val placeholderBox: Box
    private val addButton: Button
    private val maxAlarmsRow: SpinRow
    private var isRefreshing = false

    init {
        title = "Alarms"
        iconName = ICON_ALARM

        addButton = Button.withLabel("Add Alarm").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            onClicked {
                val dialog = AlarmEditorDialog(null) { alarm -> upsertAlarm(alarm) }
                dialog.present(this@AlarmsPage)
            }
        }

        maxAlarmsRow = SpinRow.withRange(
            com.zugaldia.speedofsound.core.desktop.settings.MIN_MAX_ALARMS.toDouble(),
            com.zugaldia.speedofsound.core.desktop.settings.MAX_MAX_ALARMS.toDouble(),
            1.0
        ).apply {
            title = "Maximum Alarms"
            subtitle = "How many alarms you can define in total"
            digits = 0
            value = viewModel.peekMaxAlarms().toDouble()
        }

        alarmsListBox = ListBox().apply {
            addCssClass(STYLE_CLASS_BOXED_LIST)
            marginTop = DEFAULT_BOX_SPACING
            selectionMode = SelectionMode.NONE
        }

        val placeholderLabel = Label("No alarms configured").apply {
            addCssClass(STYLE_CLASS_DIM_LABEL)
            halign = Align.CENTER
        }

        placeholderBox = Box(Orientation.VERTICAL, 0).apply {
            vexpand = true
            halign = Align.FILL
            valign = Align.FILL
            append(Box(Orientation.VERTICAL, 0).apply { vexpand = true })
            append(placeholderLabel)
            append(Box(Orientation.VERTICAL, 0).apply { vexpand = true })
        }

        val alarmsGroup = PreferencesGroup().apply {
            title = "Alarms"
            description = "Define repeating alarms, choose the weekdays they run on, and optionally name them. The action controls notification urgency; " +
                "Silent alarms do not show a desktop notification. Hardware vibration is not guaranteed, " +
                "so attention-level notifications are the best desktop approximation."
            add(maxAlarmsRow)
            add(addButton)
            add(alarmsListBox)
            add(placeholderBox)
        }

        add(alarmsGroup)
        loadAlarms()

        maxAlarmsRow.onNotify("value") {
            if (isRefreshing) return@onNotify
            if (!viewModel.setMaxAlarms(maxAlarmsRow.value.toInt())) {
                logger.warn("Failed to persist max alarms change")
            }
            refresh()
        }
    }

    fun refresh() {
        logger.info("Refreshing alarms")
        isRefreshing = true
        try {
            alarmsListBox.removeAll()
            loadAlarms()
        } finally {
            isRefreshing = false
        }
    }

    private fun loadAlarms() {
        val currentAlarms = viewModel.peekAlarms()
        currentAlarms
            .sortedWith(compareBy<AlarmSetting> { it.hour }.thenBy { it.minute }.thenBy { it.id })
            .forEach { alarm ->
                val row = ActionRow().apply {
                    title = formatAlarmName(alarm)
                    subtitle = formatAlarmSummary(alarm)
                }

                val enabledSwitch = Switch().apply {
                    active = alarm.enabled
                    valign = Align.CENTER
                }

                val editButton = Button.fromIconName(ICON_EDIT).apply {
                    addCssClass(STYLE_CLASS_FLAT)
                    valign = Align.CENTER
                }

                val deleteButton = Button.fromIconName(ICON_TRASH).apply {
                    addCssClass(STYLE_CLASS_FLAT)
                    valign = Align.CENTER
                }

                row.addSuffix(enabledSwitch)
                row.addSuffix(editButton)
                row.addSuffix(deleteButton)
                alarmsListBox.append(row)

                enabledSwitch.onNotify("active") {
                    upsertAlarm(alarm.copy(enabled = enabledSwitch.active))
                }
                editButton.onClicked {
                    val dialog = AlarmEditorDialog(alarm) { updatedAlarm -> upsertAlarm(updatedAlarm) }
                    dialog.present(this@AlarmsPage)
                }
                deleteButton.onClicked {
                    val updatedAlarms = viewModel.peekAlarms().filterNot { it.id == alarm.id }
                    if (!viewModel.setAlarms(updatedAlarms)) {
                        logger.warn("Failed to persist alarm deletion '${alarm.id}'")
                    }
                    refresh()
                }
            }
        alarmsListBox.visible = currentAlarms.isNotEmpty()
        placeholderBox.visible = currentAlarms.isEmpty()
        maxAlarmsRow.value = viewModel.peekMaxAlarms().toDouble()
        addButton.sensitive = currentAlarms.size < viewModel.peekMaxAlarms()
    }

    private fun upsertAlarm(alarm: AlarmSetting): Boolean {
        val currentAlarms = viewModel.peekAlarms().toMutableList()
        val index = currentAlarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            currentAlarms[index] = alarm
        } else {
            currentAlarms.add(alarm)
        }
        if (!viewModel.setAlarms(currentAlarms)) {
            logger.warn("Failed to persist alarm '${alarm.id}'")
            refresh()
            return false
        }
        refresh()
        return true
    }

}
