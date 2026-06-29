package dev.rebelonion.qarecord.qarecorder.resolve

import android.app.Activity
import android.view.View
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TextInputSnapshot
import dev.rebelonion.qarecord.qarecorder.web.WebViewDomBridge

class WebViewDomTargetResolver : UiTargetResolver {
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
    ): RecordedTarget? = WebViewDomBridge.targetAt(root, rawX.toInt(), rawY.toInt())

    override fun resolveFocusedInput(activity: Activity): TextInputSnapshot? {
        val root = activity.window.decorView ?: return null
        return WebViewDomBridge.focusedInput(root)
    }
}
