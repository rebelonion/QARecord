package dev.rebelonion.qarecord.qarecorder.model

import android.graphics.Rect

sealed interface RecordedStep {
    val timestampMs: Long
    val screenName: String?
}

data class TapStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val target: RecordedTarget,
) : RecordedStep

data class LongPressStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val target: RecordedTarget,
) : RecordedStep

data class ToggleStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val target: RecordedTarget,
    val checked: Boolean?,
) : RecordedStep

data class ValueChangeStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val target: RecordedTarget,
    val value: RecordedValue,
) : RecordedStep

data class SelectStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val target: RecordedTarget,
) : RecordedStep

data class TextEntryStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val target: RecordedTarget,
    val text: RecordedText,
) : RecordedStep

data class ScrollStep(
    override val timestampMs: Long,
    override val screenName: String?,
    val direction: ScrollDirection,
) : RecordedStep

data class BackStep(
    override val timestampMs: Long,
    override val screenName: String?,
) : RecordedStep

enum class ScrollDirection {
    Up,
    Down,
}

sealed interface RecordedText {
    data class Literal(val value: String) : RecordedText
    data class Redacted(val replacement: String) : RecordedText
}

data class RecordedValue(
    val current: Float,
    val min: Float? = null,
    val max: Float? = null,
)

data class RecordedTarget(
    val label: String?,
    val role: UiRole,
    val source: TargetSource,
    val bounds: Rect?,
    val rawFallback: String? = null,
    val checked: Boolean? = null,
    val value: RecordedValue? = null,
)

enum class TargetSource {
    AndroidView,
    ComposeSemantics,
    WebViewDom,
    Fallback,
}

enum class UiRole {
    Button,
    TextField,
    Checkbox,
    Switch,
    RadioButton,
    Slider,
    MenuItem,
    Image,
    ComposeContent,
    Unknown,
}

data class TextInputSnapshot(
    val target: RecordedTarget,
    val text: String,
)
