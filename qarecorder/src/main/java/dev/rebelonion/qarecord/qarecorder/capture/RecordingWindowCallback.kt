package dev.rebelonion.qarecord.qarecorder.capture

import android.app.Activity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Window
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder

class RecordingWindowCallback(
    private val activity: Activity,
    val delegate: Window.Callback,
    private val recorder: QaStepRecorder,
) : Window.Callback by delegate {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        recorder.observeTouch(activity, event)
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        recorder.observeKey(activity, event)
        return delegate.dispatchKeyEvent(event)
    }
}
