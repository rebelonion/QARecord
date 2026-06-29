package dev.rebelonion.qarecord.qarecorder.capture

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole

object PopupSemanticsReader {
    fun candidates(root: View): List<PopupSemanticsCandidate> {
        val composeHosts = mutableListOf<View>()
        root.findAndroidComposeViews(composeHosts)

        return composeHosts.flatMap { host ->
            val hostLocation = IntArray(2)
            host.getLocationOnScreen(hostLocation)
            val owner = host.tryGetSemanticsOwner() ?: return@flatMap emptyList()
            owner.rootSemanticsNode
                .flatten()
                .mapNotNull { node ->
                    val label = node.label()
                    val hasClick = node.config.contains(SemanticsActions.OnClick)
                    val focused = node.config.getOrNull(SemanticsProperties.Focused)
                    if (label == null && !hasClick && focused != true) return@mapNotNull null

                    val localBounds = node.boundsInRoot
                    val bounds = Rect(
                        (hostLocation[0] + localBounds.left).toInt(),
                        (hostLocation[1] + localBounds.top).toInt(),
                        (hostLocation[0] + localBounds.right).toInt(),
                        (hostLocation[1] + localBounds.bottom).toInt(),
                    )
                    PopupSemanticsCandidate(
                        nodeId = node.id,
                        hasClick = hasClick,
                        focused = focused == true,
                        bounds = bounds,
                        target = RecordedTarget(
                            label = label,
                            role = UiRole.MenuItem,
                            source = TargetSource.ComposeSemantics,
                            bounds = bounds,
                            rawFallback = "PopupSemanticsNode(${node.id})",
                        )
                    )
                }
        }
    }

    fun inferSelection(
        root: View,
        viewRootImpl: Any?,
        cachedCandidates: List<PopupSemanticsCandidate>?,
    ): PopupSelectionInference? {
        val touchPoint = viewRootImpl?.lastTouchPoint() ?: return null
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        val candidates = cachedCandidates.orEmpty()
            .filter { it.hasClick }

        val screenPoint = Point(touchPoint.x, touchPoint.y)
        val adjustedPoint = Point(
            rootLocation[0] + touchPoint.x,
            rootLocation[1] + touchPoint.y,
        )

        val candidate = candidates.firstOrNull { it.bounds.contains(screenPoint.x, screenPoint.y) }
            ?: candidates.firstOrNull { it.bounds.contains(adjustedPoint.x, adjustedPoint.y) }

        return PopupSelectionInference(
            touchPoint = touchPoint,
            adjustedPoint = adjustedPoint,
            candidateCount = candidates.size,
            target = candidate?.target,
        )
    }

    fun describe(candidates: List<PopupSemanticsCandidate>): String {
        return candidates.take(30).joinToString(" | ") { candidate ->
            buildString {
                append("node=${candidate.nodeId}")
                append(" label=\"${candidate.target.label}\"")
                if (candidate.hasClick) append(" onClick")
                if (candidate.focused) append(" focused")
                append(" bounds=${candidate.bounds.left},${candidate.bounds.top}-${candidate.bounds.right},${candidate.bounds.bottom}")
            }
        }.ifBlank { "none" }
    }

    private fun View.findAndroidComposeViews(out: MutableList<View>) {
        if (javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
            out += this
        }

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findAndroidComposeViews(out)
            }
        }
    }

    private fun View.tryGetSemanticsOwner(): SemanticsOwner? {
        if (javaClass.name != "androidx.compose.ui.platform.AndroidComposeView") return null

        return runCatching {
            val field = javaClass.getDeclaredField("semanticsOwner")
            field.isAccessible = true
            field.get(this) as? SemanticsOwner
        }.getOrNull()
    }

    private fun SemanticsNode.flatten(): List<SemanticsNode> {
        val nodes = mutableListOf<SemanticsNode>()

        fun visit(node: SemanticsNode) {
            nodes += node
            node.children.forEach(::visit)
        }

        visit(this)
        return nodes
    }

    private fun SemanticsNode.label(): String? {
        config.directLabel()?.let { return it }
        return children.firstNotNullOfOrNull { child -> child.config.directLabel() }
    }

    private fun SemanticsConfiguration.directLabel(): String? {
        getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(" ")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return getOrNull(SemanticsProperties.TestTag)
            ?.takeIf { it.isNotBlank() }
    }

    private fun <T> SemanticsConfiguration.getOrNull(
        key: SemanticsPropertyKey<T>,
    ): T? = if (contains(key)) this[key] else null
}

data class PopupSemanticsCandidate(
    val nodeId: Int,
    val hasClick: Boolean,
    val focused: Boolean,
    val bounds: Rect,
    val target: RecordedTarget,
)

data class PopupSelectionInference(
    val touchPoint: Point,
    val adjustedPoint: Point,
    val candidateCount: Int,
    val target: RecordedTarget?,
)
