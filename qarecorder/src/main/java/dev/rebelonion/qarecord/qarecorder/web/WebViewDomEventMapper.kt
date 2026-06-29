package dev.rebelonion.qarecord.qarecorder.web

import android.graphics.Rect
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import org.json.JSONObject

internal object WebViewDomEventMapper {
    fun fromJson(
        json: String,
        webViewLeft: Int,
        webViewTop: Int,
        scale: Float,
        timestampMs: Long,
    ): WebViewDomBridge.DomEvent {
        return JSONObject(json).toDomEvent(
            webViewLeft = webViewLeft,
            webViewTop = webViewTop,
            scale = scale,
            timestampMs = timestampMs,
        )
    }

    private fun JSONObject.toDomEvent(
        webViewLeft: Int,
        webViewTop: Int,
        scale: Float,
        timestampMs: Long,
    ): WebViewDomBridge.DomEvent {
        val clientX = optDouble("clientX", optDouble("left", 0.0)).toFloat()
        val clientY = optDouble("clientY", optDouble("top", 0.0)).toFloat()
        val rawX = webViewLeft + (clientX * scale).toInt()
        val rawY = webViewTop + (clientY * scale).toInt()
        val bounds = Rect(
            webViewLeft + (optDouble("left", clientX.toDouble()).toFloat() * scale).toInt(),
            webViewTop + (optDouble("top", clientY.toDouble()).toFloat() * scale).toInt(),
            webViewLeft + (optDouble("right", clientX.toDouble()).toFloat() * scale).toInt(),
            webViewTop + (optDouble("bottom", clientY.toDouble()).toFloat() * scale).toInt(),
        ).takeUnless { it.isEmpty }

        val role = role()
        val label = optString("label").takeIf { it.isNotBlank() }
        val checked = if (has("checked")) optBoolean("checked") else null

        return WebViewDomBridge.DomEvent(
            target = RecordedTarget(
                label = label,
                role = role,
                source = TargetSource.WebViewDom,
                bounds = bounds,
                rawFallback = optString("tag").takeIf { it.isNotBlank() },
                checked = checked,
            ),
            rawX = rawX,
            rawY = rawY,
            text = optString("value").takeIf { it.isNotBlank() },
            timestampMs = timestampMs,
        )
    }

    private fun JSONObject.role(): UiRole {
        val tag = optString("tag").lowercase()
        val type = optString("type").lowercase()
        return when {
            tag == "select" -> UiRole.MenuItem
            tag == "textarea" -> UiRole.TextField
            tag == "a" || tag == "button" -> UiRole.Button
            tag == "input" && type == "checkbox" -> UiRole.Checkbox
            tag == "input" && type == "radio" -> UiRole.RadioButton
            tag == "input" && type == "range" -> UiRole.Slider
            tag == "input" -> UiRole.TextField
            optBoolean("clickable", false) -> UiRole.Button
            else -> UiRole.Unknown
        }
    }
}
