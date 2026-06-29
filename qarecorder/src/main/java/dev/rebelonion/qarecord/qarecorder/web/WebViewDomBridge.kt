package dev.rebelonion.qarecord.qarecorder.web

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.JavascriptInterface
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.rebelonion.qarecord.qarecorder.model.RecordedTarget
import dev.rebelonion.qarecord.qarecorder.model.TextInputSnapshot
import dev.rebelonion.qarecord.qarecorder.model.UiRole
import java.util.WeakHashMap
import kotlin.math.abs

object WebViewDomBridge {
    private const val BRIDGE_NAME = "QaStepRecorderBridge"
    private const val RECENT_EVENT_WINDOW_MS = 1_500L
    private const val TOUCH_PROXIMITY_PX = 96
    private val mainHandler = Handler(Looper.getMainLooper())
    private val events = WeakHashMap<WebView, DomEvent>()
    private val focusedInputs = WeakHashMap<WebView, DomEvent>()
    private val instrumented = WeakHashMap<WebView, Bridge>()

    fun instrument(
        webView: WebView,
        webViewClient: WebViewClient? = null,
    ): WebViewClient {
        webView.settings.javaScriptEnabled = true
        if (!instrumented.containsKey(webView)) {
            Bridge(webView).also { bridge ->
                instrumented[webView] = bridge
                webView.addJavascriptInterface(bridge, BRIDGE_NAME)
            }
        }
        if (webViewClient == null && webView.webViewClient is RecordingWebViewClient) {
            webView.post { inject(webView) }
            return webView.webViewClient
        }
        val recordingClient = RecordingWebViewClient(webViewClient ?: webView.webViewClient)
        webView.webViewClient = recordingClient
        webView.post { inject(webView) }
        return recordingClient
    }

    fun targetAt(root: android.view.View, rawX: Int, rawY: Int): RecordedTarget? {
        val webView = root.findWebViewAt(rawX, rawY) ?: return null
        val now = SystemClock.elapsedRealtime()
        val event = events[webView]
            ?.takeIf { now - it.timestampMs <= RECENT_EVENT_WINDOW_MS }
            ?.takeIf { it.isNear(rawX, rawY) }
            ?: return null
        return event.target
    }

    fun focusedInput(root: android.view.View): TextInputSnapshot? {
        val webViews = mutableListOf<WebView>()
        root.collectWebViews(webViews)

        val now = SystemClock.elapsedRealtime()
        return webViews.firstNotNullOfOrNull { webView ->
            focusedInputs[webView]
                ?.takeIf { now - it.timestampMs <= RECENT_EVENT_WINDOW_MS * 4 }
                ?.takeIf { it.target.role == UiRole.TextField }
                ?.let { event ->
                    TextInputSnapshot(
                        target = event.target,
                        text = event.text.orEmpty(),
                    )
                }
        }
    }

    private fun inject(webView: WebView) {
        webView.evaluateJavascript(injectedScript, null)
    }

    private fun record(webView: WebView, json: String) {
        val webLocation = IntArray(2)
        webView.getLocationOnScreen(webLocation)
        @Suppress("DEPRECATION")
        val webViewScale = webView.scale
        val event = runCatching {
            WebViewDomEventMapper.fromJson(
                json = json,
                webViewLeft = webLocation[0],
                webViewTop = webLocation[1],
                scale = webViewScale,
                timestampMs = SystemClock.elapsedRealtime(),
            )
        }.getOrNull() ?: return
        events[webView] = event
        if (event.target.role == UiRole.TextField) {
            focusedInputs[webView] = event
        }
    }

    private fun android.view.View.findWebViewAt(rawX: Int, rawY: Int): WebView? {
        if (!isShown) return null
        val bounds = globalBounds()
        if (!bounds.contains(rawX, rawY)) return null
        if (this is android.view.ViewGroup) {
            for (index in childCount - 1 downTo 0) {
                val found = getChildAt(index).findWebViewAt(rawX, rawY)
                if (found != null) return found
            }
        }
        return this as? WebView
    }

    private fun android.view.View.collectWebViews(out: MutableList<WebView>) {
        if (this is WebView) out += this
        if (this is android.view.ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).collectWebViews(out)
            }
        }
    }

    private fun android.view.View.globalBounds(): Rect {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + width, location[1] + height)
    }

    private fun DomEvent.isNear(rawX: Int, rawY: Int): Boolean {
        val bounds = target.bounds
        if (bounds != null && bounds.expandedBy(TOUCH_PROXIMITY_PX).contains(rawX, rawY)) return true
        return abs(this.rawX - rawX) <= TOUCH_PROXIMITY_PX && abs(this.rawY - rawY) <= TOUCH_PROXIMITY_PX
    }

    private fun Rect.expandedBy(amount: Int): Rect =
        Rect(left - amount, top - amount, right + amount, bottom + amount)

    private class Bridge(webView: WebView) {
        private val webViewRef = java.lang.ref.WeakReference(webView)

        @JavascriptInterface
        fun record(json: String) {
            val webView = webViewRef.get() ?: return
            mainHandler.post { record(webView, json) }
        }
    }

    internal data class DomEvent(
        val target: RecordedTarget,
        val rawX: Int,
        val rawY: Int,
        val text: String?,
        val timestampMs: Long,
    )

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private class RecordingWebViewClient(
        private val delegate: WebViewClient,
    ) : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            delegate.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            delegate.onPageFinished(view, url)
            inject(view)
        }

        override fun onPageCommitVisible(view: WebView, url: String?) {
            delegate.onPageCommitVisible(view, url)
            inject(view)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            delegate.shouldOverrideUrlLoading(view, request)

        @Suppress("DEPRECATION")
        override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
            delegate.shouldOverrideUrlLoading(view, url)

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            delegate.shouldInterceptRequest(view, request)

        @Suppress("DEPRECATION")
        override fun shouldInterceptRequest(view: WebView, url: String?): WebResourceResponse? =
            delegate.shouldInterceptRequest(view, url)

        override fun onLoadResource(view: WebView, url: String?) {
            delegate.onLoadResource(view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            delegate.onReceivedError(view, request, error)
        }

        @Suppress("DEPRECATION")
        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String?,
            failingUrl: String?,
        ) {
            delegate.onReceivedError(view, errorCode, description, failingUrl)
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            delegate.onReceivedHttpError(view, request, errorResponse)
        }

        override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
            delegate.onFormResubmission(view, dontResend, resend)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            delegate.doUpdateVisitedHistory(view, url, isReload)
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            delegate.onReceivedSslError(view, handler, error)
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            delegate.onReceivedClientCertRequest(view, request)
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView,
            handler: HttpAuthHandler,
            host: String?,
            realm: String?,
        ) {
            delegate.onReceivedHttpAuthRequest(view, handler, host, realm)
        }

        override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean =
            delegate.shouldOverrideKeyEvent(view, event)

        override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
            delegate.onUnhandledKeyEvent(view, event)
        }

        override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
            delegate.onScaleChanged(view, oldScale, newScale)
        }

        override fun onReceivedLoginRequest(
            view: WebView,
            realm: String?,
            account: String?,
            args: String?,
        ) {
            delegate.onReceivedLoginRequest(view, realm, account, args)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean =
            delegate.onRenderProcessGone(view, detail)

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse,
        ) {
            delegate.onSafeBrowsingHit(view, request, threatType, callback)
        }
    }

    private val injectedScript = """
        (function () {
          if (window.__qaStepRecorderInstalled) return;
          window.__qaStepRecorderInstalled = true;

          function textOf(value) {
            return value == null ? '' : String(value).trim();
          }

          function labelFor(el) {
            var labelledBy = el.getAttribute('aria-labelledby');
            if (labelledBy) {
              var fromIds = labelledBy.split(/\s+/).map(function (id) {
                var node = document.getElementById(id);
                return node ? textOf(node.innerText || node.textContent) : '';
              }).filter(Boolean).join(' ');
              if (fromIds) return fromIds;
            }

            var aria = textOf(el.getAttribute('aria-label'));
            if (aria) return aria;

            if (el.id) {
              var labels = document.getElementsByTagName('label');
              for (var i = 0; i < labels.length; i++) {
                if (labels[i].getAttribute('for') === el.id) {
                  var explicitText = textOf(labels[i].innerText || labels[i].textContent);
                  if (explicitText) return explicitText;
                }
              }
            }

            var wrappingLabel = el.closest('label');
            if (wrappingLabel) {
              var wrappingText = textOf(wrappingLabel.innerText || wrappingLabel.textContent);
              if (wrappingText) return wrappingText;
            }

            if (el.tagName === 'SELECT' && el.selectedOptions && el.selectedOptions.length) {
              return textOf(el.selectedOptions[0].text);
            }

            var ownText = textOf(el.innerText || el.textContent);
            if (ownText) return ownText;

            var placeholder = textOf(el.getAttribute('placeholder'));
            if (placeholder) return placeholder;

            var value = textOf(el.value);
            if (value && el.tagName !== 'INPUT') return value;

            return '';
          }

          function payloadFor(el, event) {
            if (!el || !el.getBoundingClientRect) return null;
            var rect = el.getBoundingClientRect();
            var tag = el.tagName ? el.tagName.toLowerCase() : '';
            var value = tag === 'select' && el.selectedOptions && el.selectedOptions.length
              ? textOf(el.selectedOptions[0].text)
              : textOf(el.value);
            return {
              event: event.type,
              tag: tag,
              type: textOf(el.getAttribute('type')).toLowerCase(),
              label: labelFor(el),
              value: value,
              checked: typeof el.checked === 'boolean' ? el.checked : undefined,
              clickable: !!el.onclick || tag === 'a' || tag === 'button',
              clientX: typeof event.clientX === 'number' ? event.clientX : rect.left + rect.width / 2,
              clientY: typeof event.clientY === 'number' ? event.clientY : rect.top + rect.height / 2,
              left: rect.left,
              top: rect.top,
              right: rect.right,
              bottom: rect.bottom
            };
          }

          function report(event) {
            var el = event.target;
            if (!el) return;
            var target = el.closest('button, a, input, select, textarea, [role], [aria-label], [onclick]');
            var payload = payloadFor(target || el, event);
            if (!payload || !window.QaStepRecorderBridge) return;
            window.QaStepRecorderBridge.record(JSON.stringify(payload));
          }

          document.addEventListener('click', report, true);
          document.addEventListener('change', report, true);
          document.addEventListener('input', report, true);
          document.addEventListener('focusin', report, true);
        })();
    """.trimIndent()
}
