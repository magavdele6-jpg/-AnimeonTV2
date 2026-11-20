package co.animeon.v2.tv

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cursorView: CursorOverlayView

    private var cursorX = 640f
    private var cursorY = 360f
    private val cursorStep = 28f

    private val blockedDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "yandex.ru/ads",
        "an.yandex.ru",
        "mc.yandex.ru",
        "top-fwz1.mail.ru",
        "vk.com/rtrg",
        "adfox.ru",
        "mgid.com",
        "propellerads.com",
        "popads.net",
        "revcontent.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "pubmatic.com",
        "rubiconproject.com"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var heldKeyCode: Int? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val code = heldKeyCode ?: return
            moveCursorByKey(code)
            handler.postDelayed(this, 35L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        val root = findViewById<FrameLayout>(android.R.id.content)
        cursorView = CursorOverlayView(this)
        root.addView(
            cursorView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        webView.post {
            cursorX = (webView.width / 2f).takeIf { it > 0 } ?: 640f
            cursorY = (webView.height / 2f).takeIf { it > 0 } ?: 360f
            cursorView.setPosition(cursorX, cursorY)
        }

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.userAgentString =
            "Mozilla/5.0 (Linux; Android 13; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase() ?: return null
                val host = request.url?.host?.lowercase() ?: ""
                val isAd = blockedDomains.any { domain ->
                    host == domain || host.endsWith(".$domain") || url.contains(domain)
                }
                return if (isAd) {
                    WebResourceResponse(
                        "text/plain",
                        "utf-8",
                        ByteArrayInputStream(ByteArray(0))
                    )
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val adCleanupJs = """
                    (function() {
                        const selectors = [
                            '[id*="ad" i]', '[class*="ad-" i]', '[class*="ads" i]',
                            '[class*="banner" i]', '[class*="popup" i]', '[class*="overlay" i]',
                            'iframe[src*="ads"]', 'iframe[src*="doubleclick"]'
                        ];
                        for (const sel of selectors) {
                            document.querySelectorAll(sel).forEach(el => {
                                const rect = el.getBoundingClientRect();
                                if (rect.width > 0 && rect.height > 0 && rect.height < 450) {
                                    el.style.display = 'none';
                                }
                            });
                        }
                    })();
                """.trimIndent()
                view?.evaluateJavascript(adCleanupJs, null)
            }
        }

        webView.loadUrl("https://v2.animeon.co")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val isNavKey = code == KeyEvent.KEYCODE_DPAD_UP ||
            code == KeyEvent.KEYCODE_DPAD_DOWN ||
            code == KeyEvent.KEYCODE_DPAD_LEFT ||
            code == KeyEvent.KEYCODE_DPAD_RIGHT

        if (isNavKey) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        moveCursorByKey(code)
                        heldKeyCode = code
                        handler.removeCallbacks(repeatRunnable)
                        handler.postDelayed(repeatRunnable, 220L)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    if (heldKeyCode == code) {
                        heldKeyCode = null
                        handler.removeCallbacks(repeatRunnable)
                    }
                    return true
                }
            }
        }

        if (code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER ||
            code == KeyEvent.KEYCODE_NUMPAD_ENTER
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                performCursorClick()
            }
            return true
        }

        if (code == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun moveCursorByKey(keyCode: Int) {
        val maxX = (webView.width - 10f).coerceAtLeast(100f)
        val maxY = (webView.height - 10f).coerceAtLeast(100f)

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> cursorX = (cursorX - cursorStep).coerceAtLeast(10f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> cursorX = (cursorX + cursorStep).coerceAtMost(maxX)
            KeyEvent.KEYCODE_DPAD_UP -> cursorY = (cursorY - cursorStep).coerceAtLeast(10f)
            KeyEvent.KEYCODE_DPAD_DOWN -> cursorY = (cursorY + cursorStep).coerceAtMost(maxY)
        }
        cursorView.setPosition(cursorX, cursorY)
    }

    private fun performCursorClick() {
        val now = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0)
        val upEvent = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, cursorX, cursorY, 0)
        webView.dispatchTouchEvent(downEvent)
        webView.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        upEvent.recycle()
    }

    override fun onDestroy() {
        handler.removeCallbacks(repeatRunnable)
        webView.destroy()
        super.onDestroy()
    }

    private class CursorOverlayView(context: MainActivity) : View(context) {
        private var xPos = 640f
        private var yPos = 360f

        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF3B30")
            style = Paint.Style.FILL
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        fun setPosition(x: Float, y: Float) {
            xPos = x
            yPos = y
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawCircle(xPos, yPos, 15f, outerPaint)
            canvas.drawCircle(xPos, yPos, 15f, borderPaint)
            canvas.drawCircle(xPos, yPos, 7f, innerPaint)
        }
    }
}
