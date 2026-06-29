package dev.rebelonion.qarecord.qarecorder.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.Collections
import java.util.WeakHashMap

class QaRecorderLifecycleCallbacks(
    private val recorder: QaStepRecorder,
) : Application.ActivityLifecycleCallbacks {
    private val resumedActivities = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    override fun onActivityResumed(activity: Activity) {
        resumedActivities += activity
        recorder.attachTo(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivities -= activity
        recorder.detachFrom(activity)
    }

    fun detachAll() {
        resumedActivities.toList().forEach { activity ->
            recorder.detachFrom(activity)
        }
        resumedActivities.clear()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
