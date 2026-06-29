package dev.rebelonion.qarecord.qarecorder.capture

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder

class RootRecordingWindowCallback(
    private val activity: Activity,
    private val root: View,
    val delegate: Window.Callback,
    private val recorder: QaStepRecorder,
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        recorder.observeWindowRootTouch(activity, root, event)
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        recorder.observeWindowRootKey(activity, event)
        return delegate.dispatchKeyEvent(event)
    }
}
