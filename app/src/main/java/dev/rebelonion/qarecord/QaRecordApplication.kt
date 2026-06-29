package dev.rebelonion.qarecord

import android.app.Application
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder

class QaRecordApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.QA_RECORDER_ENABLED) {
            QaStepRecorder.install(this)
        }
    }
}
