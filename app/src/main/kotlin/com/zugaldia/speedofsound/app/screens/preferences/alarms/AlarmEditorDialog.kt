package com.zugaldia.speedofsound.app.screens.preferences.alarms

import com.zugaldia.speedofsound.app.DEFAULT_ADD_ALARM_DIALOG_HEIGHT
import com.zugaldia.speedofsound.app.DEFAULT_ADD_ALARM_DIALOG_WIDTH
import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.DEFAULT_MARGIN
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.app.ADW_MAX_LENGTH_MIN_MAJOR_VERSION
import com.zugaldia.speedofsound.app.ADW_MAX_LENGTH_MIN_MINOR_VERSION
import com.zugaldia.speedofsound.app.alarms.formatAlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmRepeatDay
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.MAX_ALARM_NAME_LENGTH
import com.zugaldia.speedofsound.core.desktop.settings.longLabel
import com.zugaldia.speedofsound.core.desktop.settings.allAlarmRepeatDays
import com.zugaldia.speedofsound.core.desktop.settings.weekdayAlarmRepeatDays
import com.zugaldia.speedofsound.core.desktop.settings.weekendAlarmRepeatDays
import com.zugaldia.speedofsound.core.generateUniqueId
import org.gnome.adw.ComboRow
import org.gnome.adw.Dialog
import org.gnome.adw.EntryRow
import org.gnome.adw.PreferencesGroup
import org.gnome.adw.SpinRow
import org.gnome.adw.SwitchRow
import org.gnome.gtk.Align
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Orientation
import org.gnome.gtk.StringList

class AlarmEditorDialog(
    private val existingAlarm: AlarmSetting? = null,
    private val onAlarmSaved: (AlarmSetting) -> Boolean,
) : Dialog() {
    private val nameRow: EntryRow
    private val hourRow: SpinRow
    private val minuteRow: SpinRow
    private val actionRow: ComboRow
    private val enabledRow: SwitchRow
    private val repeatDayRows: List<Pair<AlarmRepeatDay, SwitchRow>>

    init {
        title = if (existingAlarm == null) "Add Alarm" else "Edit Alarm"
        contentWidth = DEFAULT_ADD_ALARM_DIALOG_WIDTH
        contentHeight = DEFAULT_ADD_ALARM_DIALOG_HEIGHT

        val supportsMaxLength = com.zugaldia.speedofsound.app.isAdwVersionAtLeast(
            ADW_MAX_LENGTH_MIN_MAJOR_VERSION,
            ADW_MAX_LENGTH_MIN_MINOR_VERSION,
        )

        nameRow = EntryRow().apply {
            title = "Name"
            subtitle = "Optional label shown in the list and notifications"
            text = existingAlarm?.name ?: ""
            if (supportsMaxLength) {
                maxLength = MAX_ALARM_NAME_LENGTH
            }
        }
        hourRow = SpinRow.withRange(0.0, 23.0, 1.0).apply {
            title = "Hour"
            subtitle = "24-hour clock"
            digits = 0
            value = existingAlarm?.hour?.toDouble() ?: 7.0
        }

        minuteRow = SpinRow.withRange(0.0, 59.0, 1.0).apply {
            title = "Minute"
            subtitle = "0-59"
            digits = 0
            value = existingAlarm?.minute?.toDouble() ?: 0.0
        }

        actionRow = ComboRow().apply {
            title = "Action"
            subtitle = "How strongly the alarm should notify"
            useSubtitle = false
            model = StringList(ACTION_LABELS.toTypedArray())
            selected = existingAlarm?.action?.ordinal ?: AlarmAction.NORMAL.ordinal
        }

        enabledRow = SwitchRow().apply {
            title = "Enabled"
            subtitle = "Disabled alarms stay saved but do not fire"
            active = existingAlarm?.enabled ?: true
        }

        repeatDayRows = allAlarmRepeatDays().map { repeatDay ->
            repeatDay to SwitchRow().apply {
                title = repeatDay.longLabel()
                subtitle = "Repeat on ${repeatDay.longLabel()}"
                active = existingAlarm?.repeatDays?.contains(repeatDay) ?: true
            }
        }

        val repeatPresetGroup = PreferencesGroup().apply {
            title = "Quick Picks"
            description = "Apply a common repeat pattern in one click."
            add(Box(Orientation.HORIZONTAL, DEFAULT_BOX_SPACING).apply {
                append(Button.withLabel("Daily").apply {
                    onClicked { selectRepeatDays(allAlarmRepeatDays()) }
                })
                append(Button.withLabel("Weekdays").apply {
                    onClicked { selectRepeatDays(weekdayAlarmRepeatDays()) }
                })
                append(Button.withLabel("Weekends").apply {
                    onClicked { selectRepeatDays(weekendAlarmRepeatDays()) }
                })
            })
        }

        val group = PreferencesGroup().apply {
            title = if (existingAlarm == null) "New Alarm" else "Alarm"
            description = "Alarms repeat on the selected days at the chosen time. Add an optional name to " +
                "make multiple alarms easier to tell apart. " +
                "Silent skips the desktop notification entirely. Attention and Urgent " +
                "increase notification urgency; true hardware vibration is not guaranteed on desktop Linux."
            add(nameRow)
            add(hourRow)
            add(minuteRow)
            add(actionRow)
            add(enabledRow)
        }

        val repeatDaysGroup = PreferencesGroup().apply {
            title = "Repeat Days"
            description = "Choose which days this alarm repeats on. Leaving all switches off falls back to every day."
            repeatDayRows.forEach { (_, row) -> add(row) }
        }

        val cancelButton = Button.withLabel("Cancel").apply {
            onClicked { close() }
        }

        val saveButton = Button.withLabel("Save").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            onClicked {
                if (onAlarmSaved(
                    AlarmSetting(
                        id = existingAlarm?.id ?: generateUniqueId(),
                        name = nameRow.text.trim(),
                        hour = hourRow.value.toInt(),
                        minute = minuteRow.value.toInt(),
                        action = selectedAction(),
                        enabled = enabledRow.active,
                        repeatDays = selectedRepeatDays(),
                    )
                )) {
                    close()
                }
            }
        }

        val buttonBox = Box(Orientation.HORIZONTAL, DEFAULT_BOX_SPACING).apply {
            halign = Align.END
            valign = Align.END
            append(cancelButton)
            append(saveButton)
        }

        child = Box(Orientation.VERTICAL, DEFAULT_BOX_SPACING).apply {
            marginTop = DEFAULT_MARGIN
            marginBottom = DEFAULT_MARGIN
            marginStart = DEFAULT_MARGIN
            marginEnd = DEFAULT_MARGIN
            vexpand = true
            append(group)
            append(repeatPresetGroup)
            append(repeatDaysGroup)
            append(buttonBox)
        }
    }

    private fun selectedAction(): AlarmAction {
        val selectedIndex = actionRow.selected
        return ACTIONS.getOrNull(selectedIndex) ?: AlarmAction.NORMAL
    }

    private fun selectedRepeatDays(): List<AlarmRepeatDay> =
        repeatDayRows
            .filter { (_, row) -> row.active }
            .map { (day, _) -> day }
            .ifEmpty { allAlarmRepeatDays() }

    private fun selectRepeatDays(repeatDays: List<AlarmRepeatDay>) {
        val selectedDays = repeatDays.toSet()
        repeatDayRows.forEach { (day, row) ->
            row.active = day in selectedDays
        }
    }

    companion object {
        private val ACTIONS = AlarmAction.values().toList()
        private val ACTION_LABELS = ACTIONS.map { formatAlarmAction(it) }
    }
}
