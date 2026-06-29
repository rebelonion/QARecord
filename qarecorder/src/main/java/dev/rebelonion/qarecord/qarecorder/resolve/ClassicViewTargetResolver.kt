package dev.rebelonion.qarecord.qarecorder.resolve

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import dev.rebelonion.qarecord.qarecorder.model.RecordedValue
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.TextInputSnapshot
import dev.rebelonion.qarecord.qarecorder.model.UiRole

class ClassicViewTargetResolver : UiTargetResolver {
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
        val hit = root.findDeepestVisibleViewAt(rawX.toInt(), rawY.toInt()) ?: return null
        val target = hit.controlOwner()
        val bounds = target.globalBounds()

        if (target.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
            return RecordedTarget(
                label = "Compose content",
                role = UiRole.ComposeContent,
                source = TargetSource.Fallback,
                bounds = bounds,
                rawFallback = target.javaClass.simpleName,
            )
        }

        return RecordedTarget(
            label = target.readableLabel(),
            role = target.role(),
            source = TargetSource.AndroidView,
            bounds = bounds,
            rawFallback = target.javaClass.simpleName,
            checked = (target as? CompoundButton)?.isChecked,
            value = (target as? SeekBar)?.recordedValue(),
        )
    }

    override fun resolveFocusedInput(activity: Activity): TextInputSnapshot? {
        val focused = activity.currentFocus as? EditText ?: return null
        return TextInputSnapshot(
            target = RecordedTarget(
                label = focused.readableLabel(),
                role = UiRole.TextField,
                source = TargetSource.AndroidView,
                bounds = focused.globalBounds(),
                rawFallback = focused.javaClass.simpleName,
            ),
            text = focused.text?.toString().orEmpty(),
        )
    }

    override fun resolveStatefulTargetsInRoot(root: View): List<RecordedTarget> {
        val out = mutableListOf<RecordedTarget>()
        root.collectStatefulTargets(out)
        return out
    }

    private fun View.collectStatefulTargets(out: MutableList<RecordedTarget>) {
        if (!isShown || alpha <= 0f) return

        if (this is CompoundButton || this is SeekBar) {
            out += RecordedTarget(
                label = readableLabel(),
                role = role(),
                source = TargetSource.AndroidView,
                bounds = globalBounds(),
                rawFallback = javaClass.simpleName,
                checked = (this as? CompoundButton)?.isChecked,
                value = (this as? SeekBar)?.recordedValue(),
            )
        }

        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).collectStatefulTargets(out)
            }
        }
    }

    private fun View.findDeepestVisibleViewAt(rawX: Int, rawY: Int): View? {
        if (!isShown || alpha <= 0f) return null

        val bounds = globalBounds()
        if (!bounds.contains(rawX, rawY)) return null

        if (this is ViewGroup) {
            for (index in childCount - 1 downTo 0) {
                val child = getChildAt(index).findDeepestVisibleViewAt(rawX, rawY)
                if (child != null) return child
            }
        }

        return this
    }

    private fun View.controlOwner(): View {
        return when (val parentView = parent) {
            is Spinner -> parentView
            else -> this
        }
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

    private fun View.readableLabel(): String? {
        if (this is EditText) {
            hint?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        if (this is TextView) {
            text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            hint?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }

        if (this is Spinner) {
            selectedItem?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return resourceEntryName()?.humanizeIdentifier()
    }

    private fun View.resourceEntryName(): String? {
        if (id == View.NO_ID) return null
        return runCatching { resources.getResourceEntryName(id) }.getOrNull()
    }

    private fun String.humanizeIdentifier(): String {
        return replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .lowercase()
            .trim()
    }

    private fun View.role(): UiRole {
        return when (this) {
            is EditText -> UiRole.TextField
            is CheckBox -> UiRole.Checkbox
            is RadioButton -> UiRole.RadioButton
            is Switch -> UiRole.Switch
            is CompoundButton -> UiRole.Switch
            is SeekBar -> UiRole.Slider
            is Spinner -> UiRole.Button
            is ImageButton, is ImageView -> UiRole.Image
            else -> when {
                parent is AdapterView<*> -> UiRole.MenuItem
                this is TextView && isClickable -> UiRole.Button
                isClickable -> UiRole.Button
                else -> UiRole.Unknown
            }
        }
    }

    private fun SeekBar.recordedValue(): RecordedValue {
        return RecordedValue(
            current = progress.toFloat(),
            min = min.toFloat(),
            max = max.toFloat(),
        )
    }
}
