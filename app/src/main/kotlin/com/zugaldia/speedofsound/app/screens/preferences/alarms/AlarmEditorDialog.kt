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
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.MAX_ALARM_NAME_LENGTH
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
    private val onAlarmSaved: (AlarmSetting) -> Unit,
) : Dialog() {
    private val nameRow: EntryRow
    private val hourRow: SpinRow
    private val minuteRow: SpinRow
    private val actionRow: ComboRow
    private val enabledRow: SwitchRow

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

        val group = PreferencesGroup().apply {
            title = if (existingAlarm == null) "New Alarm" else "Alarm"
            description = "Alarms repeat every day at the chosen time. Add an optional name to " +
                "make multiple alarms easier to tell apart. " +
                "Silent skips the desktop notification entirely. Attention and Urgent " +
                "increase notification urgency; true hardware vibration is not guaranteed on desktop Linux."
            add(nameRow)
            add(hourRow)
            add(minuteRow)
            add(actionRow)
            add(enabledRow)
        }

        val cancelButton = Button.withLabel("Cancel").apply {
            onClicked { close() }
        }

        val saveButton = Button.withLabel("Save").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            onClicked {
                onAlarmSaved(
                    AlarmSetting(
                        id = existingAlarm?.id ?: generateUniqueId(),
                        name = nameRow.text.trim(),
                        hour = hourRow.value.toInt(),
                        minute = minuteRow.value.toInt(),
                        action = selectedAction(),
                        enabled = enabledRow.active,
                    )
                )
                close()
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
            append(buttonBox)
        }
    }

    private fun selectedAction(): AlarmAction {
        val selectedIndex = actionRow.selected
        return ACTIONS.getOrNull(selectedIndex) ?: AlarmAction.NORMAL
    }

    companion object {
        private val ACTIONS = AlarmAction.values().toList()
        private val ACTION_LABELS = ACTIONS.map { formatAlarmAction(it) }
    }
}
