package com.zugaldia.speedofsound.app.screens.preferences.personalization

import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.ICON_EDIT
import com.zugaldia.speedofsound.app.ICON_TRASH
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.app.DEFAULT_TEXT_VIEW_HEIGHT
import com.zugaldia.speedofsound.app.DEFAULT_TEXT_VIEW_PADDING
import com.zugaldia.speedofsound.app.SETTINGS_SAVE_DEBOUNCE_MS
import com.zugaldia.speedofsound.app.STYLE_CLASS_BOXED_LIST
import com.zugaldia.speedofsound.app.STYLE_CLASS_FLAT
import com.zugaldia.speedofsound.app.STYLE_CLASS_LINKED
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.core.desktop.settings.MAX_CUSTOM_CONTEXT_CHARS
import com.zugaldia.speedofsound.core.desktop.settings.MAX_VOCABULARY_WORDS
import org.gnome.adw.ActionRow
import org.gnome.adw.PreferencesGroup
import org.gnome.adw.PreferencesPage
import org.gnome.glib.GLib
import org.gnome.gtk.Align
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Entry
import org.gnome.gtk.ListBox
import org.gnome.gtk.Orientation
import org.gnome.gtk.ScrolledWindow
import org.gnome.gtk.SelectionMode
import org.gnome.gtk.TextIter
import org.gnome.gtk.TextView
import org.gnome.gtk.WrapMode
import org.slf4j.LoggerFactory

/*
 * We will want to switch to Adw.WrapBox once we target a higher Adw version.
 * https://gnome.pages.gitlab.gnome.org/libadwaita/doc/1-latest/class.WrapBox.html
 */
class PersonalizationPage(private val viewModel: PreferencesViewModel) : PreferencesPage() {
    private val logger = LoggerFactory.getLogger(PersonalizationPage::class.java)

    private val vocabularyListBox: ListBox
    private val instructionsTextView: TextView
    private var saveInstructionsCounter: Int = 0
    private var isRefreshing = false

    init {
        title = "Personalization"
        iconName = ICON_EDIT

        instructionsTextView = TextView().apply {
            wrapMode = WrapMode.WORD_CHAR
            topMargin = DEFAULT_TEXT_VIEW_PADDING
            bottomMargin = DEFAULT_TEXT_VIEW_PADDING
            leftMargin = DEFAULT_TEXT_VIEW_PADDING
            rightMargin = DEFAULT_TEXT_VIEW_PADDING
        }

        val instructionsScrolledWindow = ScrolledWindow().apply {
            child = instructionsTextView
            minContentHeight = DEFAULT_TEXT_VIEW_HEIGHT
        }

        val instructionsGroup = PreferencesGroup().apply {
            title = "Custom Context"
            description = "Optionally share details like your location or writing style to help improve " +
                "transcriptions (max $MAX_CUSTOM_CONTEXT_CHARS characters)"
            add(instructionsScrolledWindow)
        }

        vocabularyListBox = ListBox().apply {
            addCssClass(STYLE_CLASS_BOXED_LIST)
            marginTop = DEFAULT_BOX_SPACING
            selectionMode = SelectionMode.NONE
        }

        val vocabularyEntry = Entry().apply {
            hexpand = true
            placeholderText = "Add a word or short phrase"
        }

        val vocabularyAddButton = Button.withLabel("Add").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
        }

        val vocabularyEntryBox = Box(Orientation.HORIZONTAL, 0).apply {
            addCssClass(STYLE_CLASS_LINKED)
            append(vocabularyEntry)
            append(vocabularyAddButton)
        }

        val vocabularyGroup = PreferencesGroup().apply {
            title = "Custom Vocabulary"
            description = "Optionally add entries the model should recognize, such as names, " +
                "technical terms, or acronyms (max $MAX_VOCABULARY_WORDS words)"
            add(vocabularyEntryBox)
            add(vocabularyListBox)
        }

        vocabularyAddButton.onClicked { addVocabularyWord(vocabularyEntry) }
        vocabularyEntry.onActivate { addVocabularyWord(vocabularyEntry) }

        add(instructionsGroup)
        add(vocabularyGroup)

        loadValues()
        instructionsTextView.buffer.onChanged {
            if (isRefreshing) return@onChanged
            val buffer = instructionsTextView.buffer
            val charCount = buffer.charCount
            if (charCount > MAX_CUSTOM_CONTEXT_CHARS) {
                val start = TextIter()
                val end = TextIter()
                buffer.getBounds(start, end)
                val text = buffer.getText(start, end, false)
                val truncated = text.substring(0, MAX_CUSTOM_CONTEXT_CHARS)
                buffer.setText(truncated, -1)
                buffer.getEndIter(end)
                buffer.placeCursor(end)
                logger.warn("Text truncated to $MAX_CUSTOM_CONTEXT_CHARS characters")
            }
            saveInstructionsCounter++
            val currentCounter = saveInstructionsCounter
            GLib.timeoutAdd(GLib.PRIORITY_DEFAULT, SETTINGS_SAVE_DEBOUNCE_MS) {
                if (saveInstructionsCounter == currentCounter) {
                    saveInstructions()
                }
                false // Don't repeat
            }
        }
    }

    private fun loadValues() {
        val instructions = viewModel.peekCustomContext()
        instructionsTextView.buffer.setText(instructions, -1)

        val vocabulary = viewModel.peekCustomVocabulary()
        vocabulary.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { word ->
            val row = ActionRow().apply { title = word }
            val deleteButton = Button.fromIconName(ICON_TRASH).apply {
                addCssClass(STYLE_CLASS_FLAT)
                valign = Align.CENTER
                onClicked {
                    val updatedVocabulary = currentVocabulary().filterNot { it == row.title }
                    if (saveVocabulary(updatedVocabulary)) {
                        vocabularyListBox.remove(row)
                    }
                }
            }

            row.addSuffix(deleteButton)
            vocabularyListBox.append(row)
        }
    }

    fun refresh() {
        logger.info("Refreshing personalization settings")
        isRefreshing = true
        try {
            vocabularyListBox.removeAll()
            loadValues()
        } finally {
            isRefreshing = false
        }
    }

    /**
     * Force immediate save of instructions.
     * Should be called when the dialog is closed to ensure no data is lost.
     */
    fun forceSaveInstructions() {
        saveInstructionsCounter++ // Invalidate any pending saves
        saveInstructions()
    }

    private fun saveInstructions() {
        val buffer = instructionsTextView.buffer
        val start = TextIter()
        val end = TextIter()
        buffer.getBounds(start, end)
        val text = buffer.getText(start, end, false)
        logger.info("Saving instructions: ${text.length} chars.")
        if (!viewModel.setCustomContext(text)) {
            logger.warn("Failed to persist custom context")
            refresh()
        }
    }

    private fun saveVocabulary(vocabulary: List<String>): Boolean {
        logger.info("Saving vocabulary: ${vocabulary.size} words.")
        if (!viewModel.setCustomVocabulary(vocabulary)) {
            logger.warn("Failed to persist custom vocabulary")
            refresh()
            return false
        }
        return true
    }

    private fun addVocabularyWord(entry: Entry) {
        val word = entry.text.trim()
        if (word.isEmpty()) return

        var wordCount = 0
        var child = vocabularyListBox.firstChild
        while (child != null) {
            if (child is ActionRow) wordCount++
            child = child.nextSibling
        }

        if (wordCount >= MAX_VOCABULARY_WORDS) {
            logger.warn("Cannot add word: vocabulary limit of $MAX_VOCABULARY_WORDS reached")
            return
        }

        val updatedVocabulary = currentVocabulary() + word
        if (saveVocabulary(updatedVocabulary)) {
            val row = ActionRow().apply { title = word }
            val deleteButton = Button.fromIconName(ICON_TRASH).apply {
                addCssClass(STYLE_CLASS_FLAT)
                valign = Align.CENTER
                onClicked {
                    val updatedVocabulary = currentVocabulary().filterNot { it == row.title }
                    if (saveVocabulary(updatedVocabulary)) {
                        vocabularyListBox.remove(row)
                    }
                }
            }

            row.addSuffix(deleteButton)
            vocabularyListBox.append(row)
            entry.text = ""
        }
    }

    private fun currentVocabulary(): List<String> {
        val vocabulary = mutableListOf<String>()
        var child = vocabularyListBox.firstChild
        while (child != null) {
            if (child is ActionRow) { vocabulary.add(child.title) }
            child = child.nextSibling
        }
        return vocabulary
    }
}
