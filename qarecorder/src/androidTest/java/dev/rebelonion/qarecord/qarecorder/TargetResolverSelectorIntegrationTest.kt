package dev.rebelonion.qarecord.qarecorder

import android.graphics.Rect
import dev.rebelonion.qarecord.qarecorder.core.TargetResolverSelector
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import org.junit.Assert.assertEquals
import org.junit.Test

internal class TargetResolverSelectorIntegrationTest {
    @Test
    fun choosePrefersSmallerMeaningfulComposeTargetOverLargerNativeContainer() {
        val target = TargetResolverSelector.choose(
            listOf(
                RecordedTarget(
                    label = "Misleading wrapper",
                    role = UiRole.Button,
                    source = TargetSource.AndroidView,
                    bounds = Rect(0, 0, 400, 400),
                ),
                RecordedTarget(
                    label = "Deep Compose Action",
                    role = UiRole.Button,
                    source = TargetSource.ComposeSemantics,
                    bounds = Rect(100, 100, 250, 180),
                ),
            ),
        )

        assertEquals("Deep Compose Action", target?.label)
        assertEquals(TargetSource.ComposeSemantics, target?.source)
    }

    @Test
    fun choosePrefersSmallerNativeTargetOverLargerComposeHostFallback() {
        val target = TargetResolverSelector.choose(
            listOf(
                RecordedTarget(
                    label = "Deep Native Action",
                    role = UiRole.Button,
                    source = TargetSource.AndroidView,
                    bounds = Rect(100, 100, 250, 180),
                ),
                RecordedTarget(
                    label = "Compose content",
                    role = UiRole.ComposeContent,
                    source = TargetSource.Fallback,
                    bounds = Rect(0, 0, 400, 400),
                ),
            ),
        )

        assertEquals("Deep Native Action", target?.label)
        assertEquals(TargetSource.AndroidView, target?.source)
    }
}
