package dev.rebelonion.qarecord.qarecorder

import android.graphics.Rect
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.rebelonion.qarecord.qarecorder.core.QaStepRecorder
import dev.rebelonion.qarecord.qarecorder.model.TargetSource
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import dev.rebelonion.qarecord.qarecorder.resolve.WebViewDomTargetResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class WebViewDomTargetResolverIntegrationTest : ResolverIntegrationTestBase() {
    @Test
    fun instrumentedWebViewResolvesDomButtonClick() {
        val fixture = loadInstrumentedWebView()

        fixture.dispatchDomClick("apply")
        val target = fixture.resolveTargetAtElement("apply")

        assertEquals("Apply Web Coupon", target?.label)
        assertEquals(UiRole.Button, target?.role)
        assertEquals(TargetSource.WebViewDom, target?.source)
    }

    @Test
    fun instrumentedWebViewResolvesDomCheckboxState() {
        val fixture = loadInstrumentedWebView()

        fixture.dispatchDomClick("sms")
        val target = fixture.resolveTargetAtElement("sms")

        assertEquals("Send SMS updates", target?.label)
        assertEquals(UiRole.Checkbox, target?.role)
        assertEquals(true, target?.checked)
        assertEquals(TargetSource.WebViewDom, target?.source)
    }

    @Test
    fun instrumentedWebViewResolvesFocusedTextInputSnapshot() {
        val fixture = loadInstrumentedWebView()

        fixture.evaluate(
            """
                (function () {
                  var input = document.getElementById('email');
                  input.focus();
                  input.value = 'qa@example.com';
                  input.dispatchEvent(new Event('input', { bubbles: true }));
                  return true;
                })();
            """.trimIndent(),
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnUiThread {
                WebViewDomTargetResolver()
                    .resolveFocusedInput(composeRule.activity)
                    ?.text == "qa@example.com"
            }
        }
        val snapshot = composeRule.runOnUiThread {
            WebViewDomTargetResolver().resolveFocusedInput(composeRule.activity)
        }

        assertEquals("Email", snapshot?.target?.label)
        assertEquals(UiRole.TextField, snapshot?.target?.role)
        assertEquals("qa@example.com", snapshot?.text)
    }

    @Test
    fun instrumentedWebViewDelegatesPageFinished() {
        val delegateFinished = AtomicBoolean(false)

        loadInstrumentedWebView(
            delegate = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    delegateFinished.set(true)
                }
            },
        )

        assertTrue(delegateFinished.get())
    }

    private fun loadInstrumentedWebView(
        delegate: WebViewClient = WebViewClient(),
    ): WebViewFixture {
        val pageFinished = AtomicBoolean(false)
        val webViewRef = AtomicReference<WebView>()

        composeRule.runOnUiThread {
            val webView = WebView(composeRule.activity).apply {
                settings.javaScriptEnabled = true
                QaStepRecorder.instrument(
                    this,
                    object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            delegate.onPageFinished(view, url)
                            pageFinished.set(true)
                        }
                    },
                )
                loadDataWithBaseURL(
                    "https://qa-record.test/",
                    webViewTestHtml,
                    "text/html",
                    "UTF-8",
                    null,
                )
            }
            composeRule.activity.setContentView(webView)
            webViewRef.set(webView)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { pageFinished.get() }
        val fixture = WebViewFixture(webViewRef.get())
        fixture.waitForBridge()
        return fixture
    }

    private inner class WebViewFixture(
        private val webView: WebView,
    ) {
        fun waitForBridge() {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                evaluate("Boolean(window.__qaStepRecorderInstalled);") == "true"
            }
        }

        fun dispatchDomClick(id: String) {
            evaluate(
                """
                    (function () {
                      var el = document.getElementById('$id');
                      var rect = el.getBoundingClientRect();
                      var event = new MouseEvent('click', {
                        bubbles: true,
                        cancelable: true,
                        clientX: rect.left + rect.width / 2,
                        clientY: rect.top + rect.height / 2
                      });
                      el.dispatchEvent(event);
                      return true;
                    })();
                """.trimIndent(),
            )
        }

        fun resolveTargetAtElement(id: String) =
            elementBounds(id).let { bounds ->
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.runOnUiThread {
                        WebViewDomTargetResolver().resolveTap(
                            composeRule.activity,
                            bounds.exactCenterX(),
                            bounds.exactCenterY(),
                        ) != null
                    }
                }
                composeRule.runOnUiThread {
                    WebViewDomTargetResolver().resolveTap(
                        composeRule.activity,
                        bounds.exactCenterX(),
                        bounds.exactCenterY(),
                    )
                }
            }

        fun elementBounds(id: String): Rect {
            val result = evaluate(
                """
                    (function () {
                      var rect = document.getElementById('$id').getBoundingClientRect();
                      return [rect.left, rect.top, rect.right, rect.bottom].join(',');
                    })();
                """.trimIndent(),
            ).trim('"')
            val parts = result.split(',').map { it.toFloat() }
            val webLocation = IntArray(2)
            composeRule.runOnUiThread {
                webView.getLocationOnScreen(webLocation)
            }
            @Suppress("DEPRECATION")
            val scale = composeRule.runOnUiThread { webView.scale }
            return Rect(
                webLocation[0] + (parts[0] * scale).toInt(),
                webLocation[1] + (parts[1] * scale).toInt(),
                webLocation[0] + (parts[2] * scale).toInt(),
                webLocation[1] + (parts[3] * scale).toInt(),
            )
        }

        fun evaluate(script: String): String {
            val result = AtomicReference<String>()
            composeRule.runOnUiThread {
                webView.evaluateJavascript(script) { value ->
                    result.set(value)
                }
            }
            composeRule.waitUntil(timeoutMillis = 5_000) { result.get() != null }
            return result.get()
        }
    }
}

private val webViewTestHtml = """
    <!doctype html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1" />
      <style>
        body { margin: 0; padding: 16px; font: 16px sans-serif; }
        label { display: block; margin: 12px 0 6px; }
        input, button { box-sizing: border-box; width: 100%; min-height: 44px; }
        .row { display: flex; gap: 8px; align-items: center; }
        .row input { width: auto; min-height: auto; }
      </style>
    </head>
    <body>
      <label for="email">Email</label>
      <input id="email" type="email" placeholder="name@example.com" />
      <label class="row">
        <input id="sms" type="checkbox" aria-label="Send SMS updates" />
        <span>Send SMS updates</span>
      </label>
      <button id="apply" type="button">Apply Web Coupon</button>
    </body>
    </html>
""".trimIndent()
