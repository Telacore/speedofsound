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
            onClicked { showAlarmEditor() }
        }

        maxAlarmsRow = SpinRow.withRange(
            com.zugaldia.speedofsound.core.desktop.settings.MIN_MAX_ALARMS.toDouble(),
            com.zugaldia.speedofsound.core.desktop.settings.MAX_MAX_ALARMS.toDouble(),
            1.0
        ).apply {
            title = "Maximum Alarms"
            subtitle = "How many alarms you can define in total"
            digits = 0
            value = viewModel.getMaxAlarms().toDouble()
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
            description = "Define repeating daily alarms and optionally name them. The action controls notification urgency; " +
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
            viewModel.setMaxAlarms(maxAlarmsRow.value.toInt())
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
        viewModel.getAlarms()
            .sortedWith(compareBy<AlarmSetting> { it.hour }.thenBy { it.minute }.thenBy { it.id })
            .forEach { alarm -> addAlarmToUI(alarm) }
        updatePlaceholderVisibility()
        maxAlarmsRow.value = viewModel.getMaxAlarms().toDouble()
        updateAddButtonState()
    }

    private fun showAlarmEditor(existingAlarm: AlarmSetting? = null) {
        val dialog = AlarmEditorDialog(existingAlarm) { alarm -> upsertAlarm(alarm) }
        dialog.present(this)
    }

    private fun upsertAlarm(alarm: AlarmSetting) {
        val currentAlarms = viewModel.getAlarms().toMutableList()
        val index = currentAlarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            currentAlarms[index] = alarm
        } else {
            currentAlarms.add(alarm)
        }
        viewModel.setAlarms(currentAlarms)
        refresh()
    }

    private fun deleteAlarm(alarmId: String) {
        val updatedAlarms = viewModel.getAlarms().filterNot { it.id == alarmId }
        viewModel.setAlarms(updatedAlarms)
        refresh()
    }

    private fun addAlarmToUI(alarm: AlarmSetting) {
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
        editButton.onClicked { showAlarmEditor(alarm) }
        deleteButton.onClicked { deleteAlarm(alarm.id) }
    }

    private fun updatePlaceholderVisibility() {
        val hasAlarms = viewModel.getAlarms().isNotEmpty()
        alarmsListBox.visible = hasAlarms
        placeholderBox.visible = !hasAlarms
    }

    private fun updateAddButtonState() {
        addButton.sensitive = viewModel.getAlarms().size < viewModel.getMaxAlarms()
    }
}
