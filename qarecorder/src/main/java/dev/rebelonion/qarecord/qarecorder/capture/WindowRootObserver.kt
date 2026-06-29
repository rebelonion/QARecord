package dev.rebelonion.qarecord.qarecorder.capture

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import dev.rebelonion.qarecord.qarecorder.core.QaRecorderConfig
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import java.util.IdentityHashMap
import java.util.WeakHashMap

class WindowRootObserver(
    private val recorder: QaStepRecorder,
    private val config: QaRecorderConfig,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val observedRoots = WeakHashMap<View, Activity>()
    private val wrappedWindows = WeakHashMap<View, Window>()
    private val describedRoots = WeakHashMap<View, Unit>()
    private val rootMetadata = IdentityHashMap<View, RootMetadata>()
    private var activity: Activity? = null
    private var polling = false

    private val pollRoots = object : Runnable {
        override fun run() {
            val currentActivity = activity
            if (currentActivity == null) {
                polling = false
                return
            }

            attachCurrentRoots(currentActivity)
            mainHandler.postDelayed(this, ROOT_POLL_MS)
        }
    }

    fun start(activity: Activity) {
        this.activity = activity
        attachCurrentRoots(activity)
        if (!polling) {
            polling = true
            mainHandler.postDelayed(pollRoots, ROOT_POLL_MS)
        }
    }

    fun stop(activity: Activity) {
        if (this.activity == activity) {
            this.activity = null
            polling = false
            mainHandler.removeCallbacks(pollRoots)
        }
    }

    fun markRootAction(root: View) {
        rootMetadata[root]?.lastTouchAtMs = SystemClock.elapsedRealtime()
    }

    private fun attachCurrentRoots(activity: Activity) {
        val activityRoot = activity.window.decorView
        val rootEntries = WindowRootRegistry.entries()
        val currentRoots = rootEntries.map { it.view }

        observedRoots.keys
            .asSequence()
            .filter { it != activityRoot }
            .filter { root -> !currentRoots.contains(root) || !root.isShown }
            .toList()
            .forEach { root -> handleRemovedRoot(activity, root) }

        rootEntries
            .filter { it.view != activityRoot }
            .filter { it.view.isShown }
            .forEach { entry -> observeRoot(activity, entry) }
    }

    private fun handleRemovedRoot(activity: Activity, root: View) {
        logWindowRoot("Root removed: ${root.shortDescription()}")
        val metadata = rootMetadata.remove(root)
        val inferredPopupTarget = if (config.inferPopupSelections && root.isComposePopupLayout()) {
            inferPopupSelection(root, metadata)
        } else {
            null
        }
        val hadWindow = restoreWindowCallback(root)
        observedRoots.remove(root)

        when {
            inferredPopupTarget != null -> recorder.observeInferredWindowRootTap(activity, inferredPopupTarget)
            (metadata?.hasWindow == true || hadWindow) &&
                SystemClock.elapsedRealtime() - (metadata?.lastTouchAtMs ?: 0L) > RECENT_TOUCH_MS -> {
                recorder.observeWindowRootDismissed(activity)
            }
        }
    }

    private fun observeRoot(activity: Activity, entry: WindowRootEntry) {
        val root = entry.view
        if (!observedRoots.containsKey(root)) {
            observedRoots[root] = activity
            rootMetadata[root] = RootMetadata(
                hasWindow = root.findReflectedWindow() != null,
                viewRootImpl = entry.viewRootImpl,
            )
            logWindowRoot("Root appeared: ${root.diagnosticDescription()}")
            if (root.isComposePopupLayout()) {
                logWindowRoot(
                    "Popup ViewRootImpl: ${entry.viewRootImpl?.viewRootDiagnosticDescription() ?: "none"}",
                )
            }
        }

        if (!describedRoots.containsKey(root)) {
            describedRoots[root] = Unit
            logWindowRoot("Root candidates: ${root.clickableCandidatesDescription()}")
        }

        cachePopupSemantics(root)
        wrapWindowCallback(activity, root)
        attachTouchListeners(activity, root, root)
    }

    private fun cachePopupSemantics(root: View) {
        if (!config.inferPopupSelections || !root.isComposePopupLayout()) return

        val metadata = rootMetadata[root] ?: return
        if (metadata.semanticsDumpCount < popupSemanticsDumpLimit()) {
            metadata.semanticsDumpCount += 1
            metadata.popupCandidates = PopupSemanticsReader.candidates(root)
            logWindowRoot(
                "Popup semantics ${metadata.semanticsDumpCount}: ${PopupSemanticsReader.describe(metadata.popupCandidates)}",
            )
        } else if (metadata.popupCandidates.isEmpty()) {
            metadata.popupCandidates = PopupSemanticsReader.candidates(root)
        }
    }

    private fun inferPopupSelection(root: View, metadata: RootMetadata?): RecordedTarget? {
        val inference = PopupSemanticsReader.inferSelection(
            root = root,
            viewRootImpl = metadata?.viewRootImpl,
            cachedCandidates = metadata?.popupCandidates,
        ) ?: return null

        logWindowRoot(
            "Popup infer touch=${inference.touchPoint.x},${inference.touchPoint.y} " +
                "adjusted=${inference.adjustedPoint.x},${inference.adjustedPoint.y} " +
                "candidates=${inference.candidateCount} target=${inference.target?.label ?: "none"}",
        )
        return inference.target
    }

    private fun wrapWindowCallback(activity: Activity, root: View) {
        if (wrappedWindows.containsKey(root)) return

        val window = root.findReflectedWindow()
        if (window == null) {
            if (rootMetadata[root]?.loggedMissingWindow != true) {
                logWindowRoot("Root has no reflected Window: ${root.shortDescription()}")
                rootMetadata[root]?.loggedMissingWindow = true
            }
            return
        }
        val current = window.callback
        if (current is RootRecordingWindowCallback) return

        window.callback = RootRecordingWindowCallback(
            activity = activity,
            root = root,
            delegate = current,
            recorder = recorder,
        )
        wrappedWindows[root] = window
        logWindowRoot("Wrapped root Window callback: ${root.shortDescription()}")
    }

    private fun restoreWindowCallback(root: View): Boolean {
        val window = wrappedWindows.remove(root) ?: return false
        val current = window.callback
        if (current is RootRecordingWindowCallback) {
            window.callback = current.delegate
        }
        return true
    }

    private fun attachTouchListeners(
        activity: Activity,
        root: View,
        view: View,
    ) {
        view.setOnTouchListener { _, event ->
            if (config.debugWindowRoots && root.isComposePopupLayout() && event.actionMasked == MotionEvent.ACTION_UP) {
                logWindowRoot(
                    "Popup touch ACTION_UP root=${root.shortDescription()} view=${view.shortDescription()} raw=${event.rawX.toInt()},${event.rawY.toInt()}",
                )
            }
            recorder.observeWindowRootTouch(activity, root, event)
            false
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                attachTouchListeners(activity, root, view.getChildAt(index))
            }
        }
    }

    private fun logWindowRoot(message: String) {
        if (config.debugWindowRoots) {
            Log.d(TAG, message)
        }
    }

    private fun popupSemanticsDumpLimit(): Int {
        return if (config.debugWindowRoots) POPUP_SEMANTICS_DUMP_COUNT else 0
    }

    private data class RootMetadata(
        val hasWindow: Boolean,
        val viewRootImpl: Any?,
        var loggedMissingWindow: Boolean = false,
        var lastTouchAtMs: Long = 0L,
        var semanticsDumpCount: Int = 0,
        var popupCandidates: List<PopupSemanticsCandidate> = emptyList(),
    )

    private companion object {
        private const val TAG = "QaWindowRoots"
        private const val ROOT_POLL_MS = 100L
        private const val RECENT_TOUCH_MS = 2_000L
        private const val POPUP_SEMANTICS_DUMP_COUNT = 5
    }
}
