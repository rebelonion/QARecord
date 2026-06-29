package dev.rebelonion.qarecord.qarecorder

import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.web.WebViewDomBridge
import dev.rebelonion.qarecord.qarecorder.web.WebViewDomEventMapper
import org.junit.Assert.assertEquals
import org.junit.Test

internal class WebViewDomEventMapperIntegrationTest {
    @Test
    fun mapsButtonClick() {
        val event = map(
            """
                {
                  "tag": "button",
                  "label": "Apply Web Coupon",
                  "clientX": 40,
                  "clientY": 25,
                  "left": 10,
                  "top": 20,
                  "right": 210,
                  "bottom": 64
                }
            """.trimIndent(),
        )

        assertEquals("Apply Web Coupon", event.target.label)
        assertEquals(UiRole.Button, event.target.role)
        assertEquals(TargetSource.WebViewDom, event.target.source)
        assertEquals(140, event.rawX)
        assertEquals(225, event.rawY)
        assertEquals(110, event.target.bounds?.left)
        assertEquals(264, event.target.bounds?.bottom)
    }

    @Test
    fun mapsCheckboxState() {
        val event = map(
            """
                {
                  "tag": "input",
                  "type": "checkbox",
                  "label": "Send SMS updates",
                  "checked": true
                }
            """.trimIndent(),
        )

        assertEquals("Send SMS updates", event.target.label)
        assertEquals(UiRole.Checkbox, event.target.role)
        assertEquals(true, event.target.checked)
    }

    @Test
    fun mapsSelectAsMenuItemWithValueText() {
        val event = map(
            """
                {
                  "tag": "select",
                  "label": "Tomorrow, 9-10 AM",
                  "value": "Tomorrow, 9-10 AM"
                }
            """.trimIndent(),
        )

        assertEquals("Tomorrow, 9-10 AM", event.target.label)
        assertEquals(UiRole.MenuItem, event.target.role)
        assertEquals("Tomorrow, 9-10 AM", event.text)
    }

    @Test
    fun mapsTextInputValue() {
        val event = map(
            """
                {
                  "tag": "input",
                  "type": "email",
                  "label": "Email",
                  "value": "qa@example.com"
                }
            """.trimIndent(),
        )

        assertEquals("Email", event.target.label)
        assertEquals(UiRole.TextField, event.target.role)
        assertEquals("qa@example.com", event.text)
    }

    private fun map(json: String): WebViewDomBridge.DomEvent {
        return WebViewDomEventMapper.fromJson(
            json = json,
            webViewLeft = 100,
            webViewTop = 200,
            scale = 1f,
            timestampMs = 123L,
        )
    }
}
