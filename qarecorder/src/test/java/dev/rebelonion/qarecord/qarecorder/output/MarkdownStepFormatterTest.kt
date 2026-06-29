package dev.rebelonion.qarecord.qarecorder.output

import dev.rebelonion.qarecord.qarecorder.model.LongPressStep
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.RecordedValue
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.ToggleStep
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.model.ValueChangeStep
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownStepFormatterTest {
    @Test
    fun formatterIncludesToggleStateWhenAvailable() {
        val step = ToggleStep(
            timestampMs = 1L,
            screenName = "MainActivity",
            target = RecordedTarget(
                label = "Leave at door",
                role = UiRole.Switch,
                source = TargetSource.ComposeSemantics,
                bounds = null,
                checked = true,
            ),
            checked = true,
        )

        assertEquals(
            "1. Turn on \"Leave at door\"",
            MarkdownStepFormatter.formatLine(1, step),
        )
    }

    @Test
    fun formatterIncludesSliderPercentWhenRangeIsAvailable() {
        val step = ValueChangeStep(
            timestampMs = 1L,
            screenName = "MainActivity",
            target = RecordedTarget(
                label = "Tip percentage",
                role = UiRole.Slider,
                source = TargetSource.ComposeSemantics,
                bounds = null,
            ),
            value = RecordedValue(
                current = 18f,
                min = 0f,
                max = 30f,
            ),
        )

        assertEquals(
            "1. Set \"Tip percentage\" to 18%",
            MarkdownStepFormatter.formatLine(1, step),
        )
    }

    @Test
    fun formatterIncludesLongPressAction() {
        val step = LongPressStep(
            timestampMs = 1L,
            screenName = "MainActivity",
            target = RecordedTarget(
                label = "Order options",
                role = UiRole.Button,
                source = TargetSource.ComposeSemantics,
                bounds = null,
            ),
        )

        assertEquals(
            "1. Long press \"Order options\"",
            MarkdownStepFormatter.formatLine(1, step),
        )
    }
}
