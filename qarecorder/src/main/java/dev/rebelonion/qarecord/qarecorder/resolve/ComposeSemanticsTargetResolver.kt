package dev.rebelonion.qarecord.qarecorder.resolve

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.Role as ComposeRole
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.state.ToggleableState
import dev.rebelonion.qarecord.qarecorder.model.RecordedValue
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.TextInputSnapshot
import dev.rebelonion.qarecord.qarecorder.model.UiRole

class ComposeSemanticsTargetResolver : UiTargetResolver {
    override fun resolveTap(
        activity: Activity,
        rawX: Float,
        rawY: Float,
    ): RecordedTarget? {
        val root = activity.window.decorView ?: return null
        return resolveTapInRoot(root, rawX, rawY)
    }

    override fun resolveTapInRoot(
        root: View,
        rawX: Float,
        rawY: Float,
    ): RecordedTarget? {
        val composeHosts = mutableListOf<View>()
        root.findAndroidComposeViews(composeHosts)

        return composeHosts.asSequence().flatMap { host ->
            val hostBounds = host.globalBounds()
            if (!hostBounds.contains(rawX.toInt(), rawY.toInt())) return@flatMap emptySequence()

            val semanticsOwner = host.tryGetSemanticsOwner() ?: return@flatMap emptySequence()
            val hostLocation = IntArray(2)
            host.getLocationOnScreen(hostLocation)

            semanticsOwner.rootSemanticsNode
                .flatten()
                .asSequence()
                .mapNotNull { node -> node.toCandidate(hostLocation, rawX, rawY) }
                .filter { it.score > 0 }
        }.maxWithOrNull(
            compareBy<SemanticsCandidate> { it.score }
                .thenBy { -it.area }
        )?.target
    }

    override fun resolveFocusedInput(activity: Activity): TextInputSnapshot? {
        val root = activity.window.decorView ?: return null
        val composeHosts = mutableListOf<View>()
        root.findAndroidComposeViews(composeHosts)

        return composeHosts.firstNotNullOfOrNull { host ->
            val semanticsOwner = host.tryGetSemanticsOwner() ?: return@firstNotNullOfOrNull null
            val hostLocation = IntArray(2)
            host.getLocationOnScreen(hostLocation)

            semanticsOwner.rootSemanticsNode
                .flatten()
                .firstOrNull { node ->
                    node.config.getOrNull(SemanticsProperties.Focused) == true &&
                        node.config.contains(SemanticsActions.SetText)
                }
                ?.let { node ->
                    TextInputSnapshot(
                        target = RecordedTarget(
                            label = node.label(),
                            role = UiRole.TextField,
                            source = TargetSource.ComposeSemantics,
                            bounds = node.screenBounds(hostLocation),
                            rawFallback = "SemanticsNode(${node.id})",
                            value = node.config.recordedValue(),
                        ),
                        text = node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty(),
                    )
                }
        }
    }

    override fun resolveStatefulTargetsInRoot(root: View): List<RecordedTarget> {
        val composeHosts = mutableListOf<View>()
        root.findAndroidComposeViews(composeHosts)

        return composeHosts.flatMap { host ->
            val semanticsOwner = host.tryGetSemanticsOwner() ?: return@flatMap emptyList()
            val hostLocation = IntArray(2)
            host.getLocationOnScreen(hostLocation)

            semanticsOwner.rootSemanticsNode
                .flatten()
                .mapNotNull { node ->
                    val checked = node.config.checkedState()
                    val value = node.config.recordedValue()
                    if (checked == null && value == null) return@mapNotNull null

                    RecordedTarget(
                        label = node.label(),
                        role = node.config.role(),
                        source = TargetSource.ComposeSemantics,
                        bounds = node.screenBounds(hostLocation),
                        rawFallback = "SemanticsNode(${node.id})",
                        checked = checked,
                        value = value,
                    )
                }
        }
    }

    private fun SemanticsNode.toCandidate(
        hostLocation: IntArray,
        rawX: Float,
        rawY: Float,
    ): SemanticsCandidate? {
        val bounds = screenBounds(hostLocation)
        if (!bounds.contains(rawX.toInt(), rawY.toInt())) return null

        val config = config
        val label = label()
        val role = config.role()
        val usefulAction =
            config.contains(SemanticsActions.OnClick) ||
                config.contains(SemanticsActions.SetText) ||
                config.contains(SemanticsActions.SetProgress)
        if (!usefulAction && role == UiRole.Unknown) return null

        var score = 0
        if (label != null) score += 6
        if (usefulAction) score += 4
        if (role != UiRole.Unknown) score += 3
        if (config.getOrNull(SemanticsProperties.StateDescription) != null) score += 1
        if (config.getOrNull(SemanticsProperties.EditableText) != null) score += 2
        if (config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null) score += 2

        return SemanticsCandidate(
            score = score,
            area = bounds.width() * bounds.height(),
            target = RecordedTarget(
                label = label,
                role = role,
                source = TargetSource.ComposeSemantics,
                bounds = bounds,
                rawFallback = "SemanticsNode(${id})",
                checked = config.checkedState(),
                value = config.recordedValue(),
            ),
        )
    }

    private fun SemanticsNode.screenBounds(hostLocation: IntArray): Rect {
        val localBounds = boundsInRoot
        return Rect(
            hostLocation[0] + localBounds.left.toInt(),
            hostLocation[1] + localBounds.top.toInt(),
            hostLocation[0] + localBounds.right.toInt(),
            hostLocation[1] + localBounds.bottom.toInt(),
        )
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

        return children
            .mapNotNull { child -> child.config.directLabel() }
            .distinct()
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    private fun SemanticsConfiguration.directLabel(): String? {
        val text =
            getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }
                ?.takeIf { it.isNotBlank() }
        if (text != null) return text

        val contentDescription =
            getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(" ")
                ?.takeIf { it.isNotBlank() }
        if (contentDescription != null) return contentDescription

        val editableText =
            getOrNull(SemanticsProperties.EditableText)
                ?.text
                ?.takeIf { it.isNotBlank() }
        if (editableText != null) return editableText

        return getOrNull(SemanticsProperties.TestTag)
            ?.takeIf { it.isNotBlank() }
    }

    private fun SemanticsConfiguration.role(): UiRole {
        val role = getOrNull(SemanticsProperties.Role)
        return when (role) {
            ComposeRole.Button -> UiRole.Button
            ComposeRole.Checkbox -> UiRole.Checkbox
            ComposeRole.RadioButton -> UiRole.RadioButton
            ComposeRole.Switch -> UiRole.Switch
            ComposeRole.Image -> UiRole.Image
            else -> when {
                contains(SemanticsActions.SetText) -> UiRole.TextField
                contains(SemanticsActions.SetProgress) ||
                    getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null -> UiRole.Slider
                contains(SemanticsActions.OnClick) -> UiRole.Button
                else -> UiRole.Unknown
            }
        }
    }

    private fun SemanticsConfiguration.checkedState(): Boolean? {
        val toggleState = when (getOrNull(SemanticsProperties.ToggleableState)) {
            ToggleableState.On -> true
            ToggleableState.Off -> false
            else -> null
        }
        if (toggleState != null) return toggleState

        return getOrNull(SemanticsProperties.Selected)
    }

    private fun SemanticsConfiguration.recordedValue(): RecordedValue? {
        val rangeInfo = getOrNull(SemanticsProperties.ProgressBarRangeInfo) ?: return null
        if (rangeInfo == ProgressBarRangeInfo.Indeterminate) return null

        return RecordedValue(
            current = rangeInfo.current,
            min = rangeInfo.range.start,
            max = rangeInfo.range.endInclusive,
        )
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

    private fun View.globalBounds(): Rect {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Rect(
            location[0],
            location[1],
            location[0] + width,
            location[1] + height,
        )
    }

    private fun <T> SemanticsConfiguration.getOrNull(
        key: SemanticsPropertyKey<T>,
    ): T? = if (contains(key)) this[key] else null

    private data class SemanticsCandidate(
        val score: Int,
        val area: Int,
        val target: RecordedTarget,
    )
}
