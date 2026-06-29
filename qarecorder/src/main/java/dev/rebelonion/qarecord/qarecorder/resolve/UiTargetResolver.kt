package dev.rebelonion.qarecord.qarecorder.resolve

import android.app.Activity
import android.view.View
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TextInputSnapshot

interface UiTargetResolver {
    fun resolveTap(
        activity: Activity,
        rawX: Float,
        rawY: Float,
    ): RecordedTarget?

    fun resolveTapInRoot(
        root: View,
        rawX: Float,
        rawY: Float,
    ): RecordedTarget?

    fun resolveStatefulTargetsInRoot(root: View): List<RecordedTarget> = emptyList()

    fun resolveFocusedInput(activity: Activity): TextInputSnapshot? = null
}
