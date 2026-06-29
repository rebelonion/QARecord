package dev.rebelonion.qarecord.qarecorder.overlay

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder
import java.util.WeakHashMap
import androidx.core.view.isVisible

class QaRecorderOverlay(
    private val recorder: QaStepRecorder,
) {
    private val overlays = WeakHashMap<Activity, OverlayViews>()

    fun attach(activity: Activity) {
        if (overlays.containsKey(activity)) return

        val decor = activity.window.decorView as? ViewGroup ?: return
        val overlay = buildOverlay(activity)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply {
            topMargin = INITIAL_TOP_MARGIN_PX
            leftMargin = INITIAL_SIDE_MARGIN_PX
        }

        decor.addView(overlay.root, params)
        applyStatusBarAwareTopMargin(overlay.root)
        overlays[activity] = overlay
    }

    fun detach(activity: Activity) {
        val overlay = overlays.remove(activity) ?: return
        (overlay.root.parent as? ViewGroup)?.removeView(overlay.root)
    }

    fun refresh() {
        overlays.values.forEach { overlay ->
            overlay.status.text = recorder.statusText()
        }
    }

    fun contains(activity: Activity, rawX: Float, rawY: Float): Boolean {
        val overlay = overlays[activity]?.root ?: return false
        val bounds = Rect()
        if (!overlay.getGlobalVisibleRect(bounds)) return false
        return bounds.contains(rawX.toInt(), rawY.toInt())
    }

    private fun buildOverlay(activity: Activity): OverlayViews {
        val status = TextView(activity).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, 0, 12, 0)
        }

        val start = Button(activity).apply {
            text = "Start"
            setOnClickListener {
                recorder.start()
                status.text = recorder.statusText()
            }
        }

        val stop = Button(activity).apply {
            text = "Stop"
            setOnClickListener {
                recorder.stop()
                status.text = recorder.statusText()
            }
        }

        val copy = Button(activity).apply {
            text = "Copy"
            setOnClickListener {
                val output = recorder.markdownOutput()
                val clipboard =
                    activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("QA recorded steps", output))
                Toast.makeText(activity, "QA steps copied", Toast.LENGTH_SHORT).show()
            }
        }

        val clear = Button(activity).apply {
            text = "Clear"
            setOnClickListener {
                recorder.clear()
                status.text = recorder.statusText()
            }
        }

        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(start)
            addView(stop)
            addView(copy)
            addView(clear)
            visibility = View.GONE
        }

        val collapse = Button(activity).apply {
            text = "-"
            minWidth = 0
            minimumWidth = 0
            setOnClickListener {
                val collapsed = controls.isVisible
                controls.visibility = if (collapsed) View.GONE else View.VISIBLE
                text = if (collapsed) "+" else "-"
            }
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(status)
            addView(collapse)
            installDragHandler(this)
            installDragHandler(status)
        }

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.argb(220, 32, 32, 32))
            elevation = 16f
            addView(header)
            addView(controls)
            status.text = recorder.statusText()
        }

        return OverlayViews(
            root = root,
            status = status,
        )
    }

    private fun installDragHandler(handle: View) {
        val touchSlop = ViewConfiguration.get(handle.context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startLeft = 0
        var startTop = 0
        var dragging = false

        handle.isClickable = true
        handle.setOnTouchListener { view, event ->
            val root = findOverlayRoot(view) ?: return@setOnTouchListener false
            val parent = root.parent as? ViewGroup ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startLeft = root.left
                    startTop = root.top
                    dragging = false
                    parent.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                        dragging = true
                    }
                    if (dragging) {
                        moveOverlay(root, parent, startLeft + dx.toInt(), startTop + dy.toInt())
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    parent.requestDisallowInterceptTouchEvent(false)
                    if (!dragging) view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun findOverlayRoot(view: View): View? {
        var current: View? = view
        while (current != null && current.layoutParams !is FrameLayout.LayoutParams) {
            current = current.parent as? View
        }
        return current
    }

    private fun moveOverlay(root: View, parent: ViewGroup, left: Int, top: Int) {
        val params = root.layoutParams as? FrameLayout.LayoutParams ?: return
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = left.coerceIn(0, (parent.width - root.width).coerceAtLeast(0))
        params.topMargin = top.coerceIn(0, (parent.height - root.height).coerceAtLeast(0))
        params.rightMargin = 0
        root.layoutParams = params
    }

    private fun applyStatusBarAwareTopMargin(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val params = view.layoutParams as? FrameLayout.LayoutParams
            if (params != null && params.topMargin < statusBarTop + INITIAL_TOP_MARGIN_PX) {
                params.topMargin = statusBarTop + INITIAL_TOP_MARGIN_PX
                view.layoutParams = params
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private data class OverlayViews(
        val root: View,
        val status: TextView,
    )

    private companion object {
        const val INITIAL_TOP_MARGIN_PX = 40
        const val INITIAL_SIDE_MARGIN_PX = 20
    }
}
