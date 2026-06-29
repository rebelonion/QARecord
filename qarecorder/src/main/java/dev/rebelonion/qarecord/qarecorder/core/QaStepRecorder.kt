package dev.rebelonion.qarecord.qarecorder.core

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.rebelonion.qarecord.qarecorder.capture.RecordingWindowCallback
import dev.rebelonion.qarecord.qarecorder.capture.WindowRootObserver
import dev.rebelonion.qarecord.qarecorder.model.BackStep
import dev.rebelonion.qarecord.qarecorder.model.LongPressStep
import dev.rebelonion.qarecord.qarecorder.model.RecordedStep
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.RecordedText
import dev.rebelonion.qarecord.qarecorder.model.ScrollDirection
import dev.rebelonion.qarecord.qarecorder.model.ScrollStep
import dev.rebelonion.qarecord.qarecorder.model.SelectStep
import dev.rebelonion.qarecord.qarecorder.model.TapStep
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.TextEntryStep
import dev.rebelonion.qarecord.qarecorder.model.ToggleStep
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.model.ValueChangeStep
import dev.rebelonion.qarecord.qarecorder.output.MarkdownStepFormatter
import dev.rebelonion.qarecord.qarecorder.overlay.QaRecorderOverlay
import dev.rebelonion.qarecord.qarecorder.resolve.ClassicViewTargetResolver
import dev.rebelonion.qarecord.qarecorder.resolve.ComposeSemanticsTargetResolver
import dev.rebelonion.qarecord.qarecorder.resolve.UiTargetResolver
import dev.rebelonion.qarecord.qarecorder.resolve.WebViewDomTargetResolver
import dev.rebelonion.qarecord.qarecorder.web.WebViewDomBridge
import java.util.WeakHashMap
import kotlin.math.hypot
import kotlin.math.max

class QaStepRecorder private constructor(
    private val config: QaRecorderConfig,
    private val resolvers: List<UiTargetResolver>,
) {
    private val originalCallbacks = WeakHashMap<Activity, RecordingWindowCallback>()
    private val stepStore = RecordedStepStore()
    private val overlay = QaRecorderOverlay(this)
    private val windowRootObserver = WindowRootObserver(this, config)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recording = false
    private var activeGesture: TouchGesture? = null
    private var textSession: TextInputSession? = null
    private val pollTextInput = object : Runnable {
        override fun run() {
            pollTextInputSession()
        }
    }

    fun attachTo(activity: Activity) {
        overlay.attach(activity)
        if (config.captureTransientWindowRoots) {
            windowRootObserver.start(activity)
        }

        val current = activity.window.callback
        if (current is RecordingWindowCallback) return

        val wrapper = RecordingWindowCallback(
            activity = activity,
            delegate = current,
            recorder = this,
        )
        originalCallbacks[activity] = wrapper
        activity.window.callback = wrapper
    }

    fun detachFrom(activity: Activity) {
        finishTextSession()
        val current = activity.window.callback
        if (current is RecordingWindowCallback && current == originalCallbacks[activity]) {
            activity.window.callback = current.delegate
        }
        originalCallbacks.remove(activity)
        overlay.detach(activity)
        if (config.captureTransientWindowRoots) {
            windowRootObserver.stop(activity)
        }
    }

    fun observeTouch(activity: Activity, event: MotionEvent) {
        if (!recording) return
        observeTouch(
            activity = activity,
            root = activity.window.decorView,
            event = event,
            ignoreOverlay = true,
        )
    }

    fun observeWindowRootTouch(activity: Activity, root: View, event: MotionEvent) {
        if (!recording) return
        observeTouch(
            activity = activity,
            root = root,
            event = event,
            ignoreOverlay = false,
            onRecordedRootAction = { windowRootObserver.markRootAction(root) },
        )
    }

    fun observeWindowRootKey(activity: Activity, event: KeyEvent) {
        if (!recording || event.action != KeyEvent.ACTION_UP) return
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            recordBack(activity)
        }
    }

    fun observeWindowRootDismissed(activity: Activity) {
        if (!recording) return
        recordBack(activity)
    }

    fun observeInferredWindowRootTap(activity: Activity, target: RecordedTarget) {
        if (!recording) return
        finishTextSession()
        appendInteractionStep(activity, target)
    }

    private fun observeTouch(
        activity: Activity,
        root: View,
        event: MotionEvent,
        ignoreOverlay: Boolean,
        onRecordedRootAction: (() -> Unit)? = null,
    ) {

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeGesture = TouchGesture(
                    downRawX = event.rawX,
                    downRawY = event.rawY,
                    downTimeMs = event.eventTime,
                    touchSlop = ViewConfiguration.get(activity).scaledTouchSlop,
                    statefulTargetsAtDown = resolveStatefulTargets(root),
                )
            }

            MotionEvent.ACTION_MOVE -> {
                activeGesture?.observeMove(event.rawX, event.rawY)
            }

            MotionEvent.ACTION_CANCEL -> {
                activeGesture = null
            }

            MotionEvent.ACTION_UP -> {
                val gesture = activeGesture
                activeGesture = null

                if (gesture == null) return
                if (ignoreOverlay && overlay.contains(activity, event.rawX, event.rawY)) return

                if (gesture.isTap(event)) {
                    val preDispatchTarget = resolveTapTarget(root, event.rawX, event.rawY)
                    onRecordedRootAction?.invoke()
                    recordAfterUiHandlesTouch {
                        recordTap(
                            activity,
                            root,
                            gesture,
                            event.rawX,
                            event.rawY,
                            preDispatchTarget,
                        )
                    }
                } else if (gesture.isLongPress(event)) {
                    val preDispatchTarget = resolveTapTarget(root, event.rawX, event.rawY)
                    onRecordedRootAction?.invoke()
                    recordAfterUiHandlesTouch {
                        recordLongPress(activity, root, event.rawX, event.rawY, preDispatchTarget)
                    }
                } else if (gesture.isScroll(event)) {
                    if (recordScroll(activity, gesture.scrollDirection(event))) {
                        onRecordedRootAction?.invoke()
                    }
                } else if (gesture.isDrag()) {
                    onRecordedRootAction?.invoke()
                    recordAfterUiHandlesTouch {
                        recordValueGesture(activity, root, event.rawX, event.rawY)
                    }
                }
            }
        }
    }

    private fun recordAfterUiHandlesTouch(block: () -> Unit) {
        mainHandler.postDelayed(block, POST_TOUCH_CAPTURE_DELAY_MS)
    }

    private fun recordTap(
        activity: Activity,
        root: View,
        gesture: TouchGesture,
        rawX: Float,
        rawY: Float,
        preDispatchTarget: RecordedTarget,
    ): Boolean {
        val target = resolveChangedStatefulTarget(root, gesture, rawX, rawY)
            ?: resolveTapTarget(root, rawX, rawY).takeUnless { it.isUnknownFallback() }
            ?: preDispatchTarget

        if (target.isUnknownFallback()) return false

        if (target.role == UiRole.TextField) {
            beginTextSession(activity, target)
            return true
        }

        finishTextSession()
        appendInteractionStep(activity, target)
        return true
    }

    private fun recordLongPress(
        activity: Activity,
        root: View,
        rawX: Float,
        rawY: Float,
        preDispatchTarget: RecordedTarget,
    ): Boolean {
        val target = resolveTapTarget(root, rawX, rawY).takeUnless { it.isUnknownFallback() }
            ?: preDispatchTarget

        finishTextSession()
        appendStep(
            LongPressStep(
                timestampMs = SystemClock.elapsedRealtime(),
                screenName = activity.javaClass.simpleName,
                target = target,
            )
        )
        return true
    }

    private fun recordValueGesture(activity: Activity, root: View, rawX: Float, rawY: Float): Boolean {
        val target = resolveTapTarget(root, rawX, rawY)
        if (target.role != UiRole.Slider || target.value == null) return false

        finishTextSession()
        appendInteractionStep(activity, target)
        return true
    }

    private fun resolveChangedStatefulTarget(
        root: View,
        gesture: TouchGesture,
        rawX: Float,
        rawY: Float,
    ): RecordedTarget? {
        val previousTargets = gesture.statefulTargetsAtDown
            .associateBy { it.stableStatefulKey() }
        if (previousTargets.isEmpty()) return null

        return resolveStatefulTargets(root)
            .filter { current ->
                val previous = previousTargets[current.stableStatefulKey()] ?: return@filter false
                current.hasStateChangeFrom(previous) &&
                    current.isNearTouch(gesture.downRawX, gesture.downRawY, rawX, rawY, gesture.touchSlop)
            }
            .minByOrNull { current ->
                current.bounds?.distanceSquaredTo(rawX, rawY) ?: Float.MAX_VALUE
            }
    }

    private fun resolveStatefulTargets(root: View): List<RecordedTarget> {
        return resolvers.flatMap { resolver ->
            resolver.resolveStatefulTargetsInRoot(root)
        }
    }

    private fun resolveTapTarget(root: View, rawX: Float, rawY: Float): RecordedTarget {
        val candidates = resolvers.mapNotNull { resolver ->
            resolver.resolveTapInRoot(root, rawX, rawY)
        }

        return TargetResolverSelector.choose(candidates) ?: RecordedTarget(
            label = null,
            role = UiRole.Unknown,
            source = TargetSource.Fallback,
            bounds = null,
        )
    }

    private fun RecordedTarget.isUnknownFallback(): Boolean {
        return role == UiRole.Unknown && label.isNullOrBlank()
    }

    private fun RecordedTarget.stableStatefulKey(): String {
        return listOfNotNull(
            source.name,
            rawFallback,
            role.name,
            label,
        ).joinToString("|")
    }

    private fun RecordedTarget.hasStateChangeFrom(previous: RecordedTarget): Boolean {
        return when (role) {
            UiRole.Checkbox,
            UiRole.Switch -> checked != null && previous.checked != null && checked != previous.checked
            UiRole.Slider -> value != null && previous.value != null && value.current != previous.value.current
            else -> false
        }
    }

    private fun RecordedTarget.isNearTouch(
        downRawX: Float,
        downRawY: Float,
        upRawX: Float,
        upRawY: Float,
        touchSlop: Int,
    ): Boolean {
        val bounds = bounds ?: return false
        val expansion = max(touchSlop * 4, MIN_STATEFUL_TOUCH_EXPANSION_PX)
        return bounds.expandedBy(expansion).contains(downRawX.toInt(), downRawY.toInt()) ||
            bounds.expandedBy(expansion).contains(upRawX.toInt(), upRawY.toInt())
    }

    private fun Rect.expandedBy(amount: Int): Rect {
        return Rect(left - amount, top - amount, right + amount, bottom + amount)
    }

    private fun Rect.distanceSquaredTo(rawX: Float, rawY: Float): Float {
        val clampedX = rawX.coerceIn(left.toFloat(), right.toFloat())
        val clampedY = rawY.coerceIn(top.toFloat(), bottom.toFloat())
        val dx = rawX - clampedX
        val dy = rawY - clampedY
        return dx * dx + dy * dy
    }

    private fun appendInteractionStep(activity: Activity, target: RecordedTarget) {
        val timestampMs = SystemClock.elapsedRealtime()
        val screenName = activity.javaClass.simpleName
        val new = when (target.role) {
            UiRole.Checkbox,
            UiRole.Switch -> ToggleStep(
                timestampMs = timestampMs,
                screenName = screenName,
                target = target,
                checked = target.checked,
            )
            UiRole.Slider -> target.value?.let { value ->
                ValueChangeStep(
                    timestampMs = timestampMs,
                    screenName = screenName,
                    target = target,
                    value = value,
                )
            } ?: TapStep(
                timestampMs = timestampMs,
                screenName = screenName,
                target = target,
            )
            UiRole.RadioButton,
            UiRole.MenuItem -> SelectStep(
                timestampMs = timestampMs,
                screenName = screenName,
                target = target,
            )
            else -> TapStep(
                timestampMs = timestampMs,
                screenName = screenName,
                target = target,
            )
        }
        appendStep(new)
    }

    private fun recordedText(value: String): RecordedText {
        return if (config.recordLiteralTextEntry) {
            RecordedText.Literal(value)
        } else {
            RecordedText.Redacted("[text]")
        }
    }

    private fun recordScroll(activity: Activity, direction: ScrollDirection): Boolean {
        finishTextSession()
        val new = ScrollStep(
            timestampMs = SystemClock.elapsedRealtime(),
            screenName = activity.javaClass.simpleName,
            direction = direction,
        )
        appendStep(new)
        return true
    }

    fun observeKey(activity: Activity, event: KeyEvent) {
        if (!recording || event.action != KeyEvent.ACTION_UP) return
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            recordBack(activity)
        }
    }

    private fun recordBack(activity: Activity) {
        finishTextSession()
        val new = BackStep(
            timestampMs = SystemClock.elapsedRealtime(),
            screenName = activity.javaClass.simpleName,
        )
        appendStep(new)
    }

    fun start() {
        recording = true
        stepStore.clear()
        activeGesture = null
        cancelTextSession()
        overlay.refresh()
    }

    fun stop() {
        finishTextSession()
        recording = false
        activeGesture = null
        overlay.refresh()
    }

    fun clear() {
        stepStore.clear()
        cancelTextSession()
        overlay.refresh()
    }

    fun markdownOutput(): String = MarkdownStepFormatter.format(stepStore.snapshot())

    fun statusText(): String {
        val state = if (recording) "Recording" else "Stopped"
        return "QA Recorder: $state (${stepStore.size})"
    }

    private fun logRecordedStep(step: RecordedStep) {
        val displayLine = MarkdownStepFormatter.formatLine(stepStore.size, step)
        Log.d("QaStepRecorder", displayLine)
    }

    private fun beginTextSession(activity: Activity, target: RecordedTarget) {
        val current = textSession
        if (current != null && current.target == target) return

        finishTextSession()
        textSession = TextInputSession(
            activity = activity,
            target = target,
            baselineText = null,
            latestText = "",
            lastChangedAtMs = SystemClock.elapsedRealtime(),
        )
        mainHandler.postDelayed(pollTextInput, TEXT_POLL_DELAY_MS)
    }

    private fun pollTextInputSession() {
        val session = textSession ?: return
        val snapshot = resolvers.firstNotNullOfOrNull { it.resolveFocusedInput(session.activity) }

        if (snapshot == null || snapshot.target.role != UiRole.TextField) {
            finishTextSession()
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (session.baselineText == null) {
            session.baselineText = snapshot.text
            session.latestText = snapshot.text
            session.lastChangedAtMs = now
        } else if (session.latestText != snapshot.text) {
            session.latestText = snapshot.text
            session.lastChangedAtMs = now
        }

        if (session.hasTextChange && now - session.lastChangedAtMs >= TEXT_IDLE_EMIT_MS) {
            emitTextEntry(session)
            session.baselineText = session.latestText
        }

        mainHandler.postDelayed(pollTextInput, TEXT_POLL_DELAY_MS)
    }

    private fun finishTextSession() {
        val session = textSession ?: return
        if (session.hasTextChange) {
            emitTextEntry(session)
        }
        cancelTextSession()
    }

    private fun cancelTextSession() {
        textSession = null
        mainHandler.removeCallbacks(pollTextInput)
    }

    private fun emitTextEntry(session: TextInputSession) {
        val new = TextEntryStep(
            timestampMs = SystemClock.elapsedRealtime(),
            screenName = session.activity.javaClass.simpleName,
            target = session.target,
            text = recordedText(session.latestText),
        )
        appendStep(new)
    }

    private fun appendStep(step: RecordedStep) {
        when (stepStore.append(step)) {
            RecordedStepStore.AppendResult.Accepted -> {
                logRecordedStep(step)
                overlay.refresh()
            }
            RecordedStepStore.AppendResult.Coalesced -> overlay.refresh()
            RecordedStepStore.AppendResult.Ignored -> Unit
        }
    }

    companion object {
        private const val TEXT_POLL_DELAY_MS = 250L
        private const val TEXT_IDLE_EMIT_MS = 1_200L
        private const val MAX_TAP_DURATION_MS = 700L
        private const val POST_TOUCH_CAPTURE_DELAY_MS = 100L
        private const val MIN_STATEFUL_TOUCH_EXPANSION_PX = 48
        private var installation: Installation? = null

        fun install(
            app: Application,
            config: QaRecorderConfig = QaRecorderConfig(),
        ): QaStepRecorder {
            installation?.let { return it.recorder }

            val recorder = QaStepRecorder(
                config = config,
                resolvers = listOf(
                    ComposeSemanticsTargetResolver(),
                    WebViewDomTargetResolver(),
                    ClassicViewTargetResolver(),
                ),
            )
            val lifecycleCallbacks = QaRecorderLifecycleCallbacks(recorder)
            app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            installation = Installation(
                app = app,
                recorder = recorder,
                lifecycleCallbacks = lifecycleCallbacks,
            )
            return recorder
        }

        fun uninstall(): Boolean {
            val current = installation ?: return false
            current.app.unregisterActivityLifecycleCallbacks(current.lifecycleCallbacks)
            current.lifecycleCallbacks.detachAll()
            current.recorder.stop()
            installation = null
            return true
        }

        private fun uninstall(recorder: QaStepRecorder) {
            if (installation?.recorder == recorder) {
                uninstall()
            }
        }

        /**
         * Enables DOM-level recording for a WebView in QA/debug builds.
         *
         * If the WebView already uses a WebViewClient, pass it as [webViewClient] before loading
         * content so recorder injection can delegate to it. Setting another WebViewClient after this
         * call will bypass recorder injection for later navigations.
         */
        fun instrument(
            webView: WebView,
            webViewClient: WebViewClient? = null,
        ): WebViewClient {
            return WebViewDomBridge.instrument(webView, webViewClient)
        }

        private data class Installation(
            val app: Application,
            val recorder: QaStepRecorder,
            val lifecycleCallbacks: QaRecorderLifecycleCallbacks,
        )
    }

    private data class TouchGesture(
        val downRawX: Float,
        val downRawY: Float,
        val downTimeMs: Long,
        val touchSlop: Int,
        val statefulTargetsAtDown: List<RecordedTarget>,
        var movedPastSlop: Boolean = false,
    ) {
        fun observeMove(rawX: Float, rawY: Float) {
            if (distanceFromDown(rawX, rawY) > touchSlop) {
                movedPastSlop = true
            }
        }

        fun isTap(event: MotionEvent): Boolean {
            return !movedPastSlop &&
                distanceFromDown(event.rawX, event.rawY) <= touchSlop &&
                event.eventTime - downTimeMs <= MAX_TAP_DURATION_MS
        }

        fun isLongPress(event: MotionEvent): Boolean {
            return !movedPastSlop &&
                distanceFromDown(event.rawX, event.rawY) <= touchSlop &&
                event.eventTime - downTimeMs >= ViewConfiguration.getLongPressTimeout()
        }

        fun isScroll(event: MotionEvent): Boolean {
            return movedPastSlop && kotlin.math.abs(event.rawY - downRawY) >= touchSlop * 2
        }

        fun isDrag(): Boolean {
            return movedPastSlop
        }

        fun scrollDirection(event: MotionEvent): ScrollDirection {
            return if (event.rawY < downRawY) ScrollDirection.Down else ScrollDirection.Up
        }

        private fun distanceFromDown(rawX: Float, rawY: Float): Float {
            return hypot(rawX - downRawX, rawY - downRawY)
        }
    }

    private data class TextInputSession(
        val activity: Activity,
        val target: RecordedTarget,
        var baselineText: String?,
        var latestText: String,
        var lastChangedAtMs: Long,
    ) {
        val hasTextChange: Boolean
            get() = baselineText != null && latestText != baselineText
    }
}
