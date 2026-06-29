package dev.rebelonion.qarecord.qarecorder.output

import dev.rebelonion.qarecord.qarecorder.model.BackStep
import dev.rebelonion.qarecord.qarecorder.model.LongPressStep
import dev.rebelonion.qarecord.qarecorder.model.RecordedStep
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.RecordedText
import dev.rebelonion.qarecord.qarecorder.model.RecordedValue
import dev.rebelonion.qarecord.qarecorder.model.ScrollStep
import dev.rebelonion.qarecord.qarecorder.model.SelectStep
import dev.rebelonion.qarecord.qarecorder.model.TapStep
import dev.rebelonion.qarecord.qarecorder.model.TextEntryStep
import dev.rebelonion.qarecord.qarecorder.model.ToggleStep
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.model.ValueChangeStep
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object MarkdownStepFormatter {
    fun format(steps: List<RecordedStep>): String {
        return buildString {
            appendLine("## Steps")
            appendLine()

            if (steps.isEmpty()) {
                appendLine("_No steps recorded._")
                return@buildString
            }

            steps.forEachIndexed { index, step ->
                appendLine(formatLine(index + 1, step))
            }
        }.trimEnd()
    }

    fun formatLine(stepNumber: Int, step: RecordedStep): String {
        return "$stepNumber. ${step.toHumanText()}"
    }

    fun humanText(step: RecordedStep): String = step.toHumanText()

    private fun RecordedStep.toHumanText(): String {
        return when (this) {
            is TapStep -> "Tap ${target.toHumanReference()}"
            is LongPressStep -> "Long press ${target.toHumanReference()}"
            is ToggleStep -> checked.toToggleVerb() + " ${target.toHumanReference()}"
            is ValueChangeStep -> "Set ${target.toHumanReference()} to ${value.toHumanReference(target)}"
            is SelectStep -> "Select ${target.toHumanReference()}"
            is TextEntryStep -> "Enter ${text.toHumanReference()} into ${target.toHumanReference()}"
            is ScrollStep -> "Scroll ${direction.name.lowercase()}"
            is BackStep -> "Press Back"
        }
    }

    private fun RecordedText.toHumanReference(): String {
        return when (this) {
            is RecordedText.Literal -> "\"${value.escapeForStep()}\""
            is RecordedText.Redacted -> replacement
        }
    }

    private fun Boolean?.toToggleVerb(): String {
        return when (this) {
            true -> "Turn on"
            false -> "Turn off"
            null -> "Toggle"
        }
    }

    private fun RecordedValue.toHumanReference(target: RecordedTarget): String {
        val minValue = min
        val maxValue = max
        if (target.label.isPercentLabel()) return "${current.formatNumber()}%"

        if (minValue != null && maxValue != null && minValue == 0f && maxValue == 1f) {
            val percent = ((current - minValue) / (maxValue - minValue) * 100f).roundToInt()
            if (percent in 0..100) return "$percent%"
        }

        return current.formatNumber()
    }

    private fun Float.formatNumber(): String {
        return if (abs(this - roundToInt()) < 0.001f) {
            roundToInt().toString()
        } else {
            String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')
        }
    }

    private fun String?.isPercentLabel(): Boolean {
        return this?.contains("percent", ignoreCase = true) == true || this?.contains("%") == true
    }

    private fun String.escapeForStep(): String {
        return replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun RecordedTarget.toHumanReference(): String {
        val readableLabel = label?.takeIf { it.isNotBlank() }
        if (readableLabel != null) return "\"$readableLabel\""

        return when (role) {
            UiRole.ComposeContent -> "\"Compose content\""
            UiRole.TextField -> "text field"
            UiRole.Button -> "button"
            UiRole.Checkbox -> "checkbox"
            UiRole.Switch -> "switch"
            UiRole.RadioButton -> "radio button"
            UiRole.Slider -> "slider"
            UiRole.MenuItem -> "menu item"
            UiRole.Image -> "image"
            UiRole.Unknown -> "unknown element"
        }
    }
}
