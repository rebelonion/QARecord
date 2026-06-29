package dev.rebelonion.qarecord.qarecorder.core

import dev.rebelonion.qarecord.qarecorder.model.RecordedStep
import dev.rebelonion.qarecord.qarecorder.model.ScrollStep
import dev.rebelonion.qarecord.qarecorder.output.MarkdownStepFormatter

internal class RecordedStepStore(
    private val duplicateStepWindowMs: Long = DEFAULT_DUPLICATE_STEP_WINDOW_MS,
    private val scrollCoalesceWindowMs: Long = DEFAULT_SCROLL_COALESCE_WINDOW_MS,
) {
    private val steps = mutableListOf<RecordedStep>()

    val size: Int
        get() = steps.size

    fun snapshot(): List<RecordedStep> = steps.toList()

    fun clear() {
        steps.clear()
    }

    fun append(step: RecordedStep): AppendResult {
        val previous = steps.lastOrNull()
        if (previous != null && isDuplicate(previous, step)) {
            return AppendResult.Ignored
        }

        if (previous is ScrollStep && step is ScrollStep && shouldCoalesceScroll(previous, step)) {
            steps[steps.lastIndex] = step
            return AppendResult.Coalesced
        }

        steps += step
        return AppendResult.Accepted
    }

    private fun isDuplicate(previous: RecordedStep, step: RecordedStep): Boolean {
        val ageMs = step.timestampMs - previous.timestampMs
        return ageMs in 0..duplicateStepWindowMs &&
            MarkdownStepFormatter.humanText(previous) == MarkdownStepFormatter.humanText(step)
    }

    private fun shouldCoalesceScroll(previous: ScrollStep, step: ScrollStep): Boolean {
        val ageMs = step.timestampMs - previous.timestampMs
        return ageMs in 0..scrollCoalesceWindowMs &&
            previous.screenName == step.screenName &&
            previous.direction == step.direction
    }

    enum class AppendResult {
        Accepted,
        Coalesced,
        Ignored,
    }

    companion object {
        private const val DEFAULT_DUPLICATE_STEP_WINDOW_MS = 500L
        private const val DEFAULT_SCROLL_COALESCE_WINDOW_MS = 2_000L
    }
}
