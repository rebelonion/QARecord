package dev.rebelonion.qarecord.qarecorder.core

import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole

internal object TargetResolverSelector {
    fun choose(candidates: List<RecordedTarget>): RecordedTarget? {
        return candidates
            .filter { it.isMeaningfulSpecificTarget() }
            .minWithOrNull(
                compareBy<RecordedTarget> { target ->
                    target.bounds?.let { bounds -> bounds.width() * bounds.height() } ?: Int.MAX_VALUE
                }
                    .thenBy { it.source.priority },
            )
            ?: candidates.firstOrNull { target ->
                target.role != UiRole.ComposeContent
            }
            ?: candidates.firstOrNull()
    }

    private fun RecordedTarget.isMeaningfulSpecificTarget(): Boolean {
        return role != UiRole.ComposeContent &&
            !isUnknownFallback() &&
            (label?.isNotBlank() == true || role != UiRole.Unknown)
    }

    private fun RecordedTarget.isUnknownFallback(): Boolean {
        return role == UiRole.Unknown && label.isNullOrBlank()
    }

    private val TargetSource.priority: Int
        get() = when (this) {
            TargetSource.AndroidView -> 0
            TargetSource.ComposeSemantics -> 1
            TargetSource.WebViewDom -> 1
            TargetSource.Fallback -> 2
        }
}
