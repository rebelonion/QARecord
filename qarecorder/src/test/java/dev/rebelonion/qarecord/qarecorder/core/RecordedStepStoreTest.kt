package dev.rebelonion.qarecord.qarecorder.core

import dev.rebelonion.qarecord.qarecorder.model.BackStep
import dev.rebelonion.qarecord.qarecorder.model.ScrollDirection
import dev.rebelonion.qarecord.qarecorder.model.ScrollStep
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordedStepStoreTest {
    @Test
    fun consecutiveScrollsInSameDirectionAreCoalesced() {
        val store = RecordedStepStore(
            duplicateStepWindowMs = 0L,
            scrollCoalesceWindowMs = 2_000L,
        )

        store.append(scroll(timestampMs = 1_000L, direction = ScrollDirection.Down))
        val result = store.append(scroll(timestampMs = 2_500L, direction = ScrollDirection.Down))

        assertEquals(RecordedStepStore.AppendResult.Coalesced, result)
        assertEquals(
            listOf(scroll(timestampMs = 2_500L, direction = ScrollDirection.Down)),
            store.snapshot(),
        )
    }

    @Test
    fun scrollCoalescingStopsAfterDifferentStep() {
        val store = RecordedStepStore(
            duplicateStepWindowMs = 0L,
            scrollCoalesceWindowMs = 2_000L,
        )

        store.append(scroll(timestampMs = 1_000L, direction = ScrollDirection.Down))
        store.append(BackStep(timestampMs = 1_500L, screenName = "MainActivity"))
        val result = store.append(scroll(timestampMs = 2_000L, direction = ScrollDirection.Down))

        assertEquals(RecordedStepStore.AppendResult.Accepted, result)
        assertEquals(3, store.size)
    }

    @Test
    fun scrollsInDifferentDirectionsAreNotCoalesced() {
        val store = RecordedStepStore(
            duplicateStepWindowMs = 0L,
            scrollCoalesceWindowMs = 2_000L,
        )

        store.append(scroll(timestampMs = 1_000L, direction = ScrollDirection.Down))
        val result = store.append(scroll(timestampMs = 1_500L, direction = ScrollDirection.Up))

        assertEquals(RecordedStepStore.AppendResult.Accepted, result)
        assertEquals(2, store.size)
    }

    private fun scroll(timestampMs: Long, direction: ScrollDirection): ScrollStep {
        return ScrollStep(
            timestampMs = timestampMs,
            screenName = "MainActivity",
            direction = direction,
        )
    }
}
