package com.example.gamebooster

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.sin

class NeonView(ctx: Context) : View(ctx) {

    private val bg: Bitmap? = load("game_bg", 1)
    private val broom: Bitmap? = load("game_logo", 2)

    private fun load(name: String, sample: Int): Bitmap? {
        return try {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id == 0) {
                null
            } else {
                val o = BitmapFactory.Options()
                o.inSampleSize = sample
                BitmapFactory.decodeResource(context.resources, id, o)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ring(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.color = color
        return p
    }

    private val glow = ring(Color.argb(60, 0, 255, 70))
    private val mid = ring(Color.argb(150, 40, 255, 100))
    private val core = ring(Color.rgb(190, 255, 205))
    private val arc = ring(Color.rgb(220, 255, 230))
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val broomPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val oval = RectF()
    private val dst = RectF()
    private var t = 0f

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        broomPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val two = (2.0 * PI).toFloat()

        c.drawColor(Color.BLACK)
        val b = bg
        if (b != null) {
            val sc = maxOf(w / b.width, h / b.height)
            val dw = b.width * sc
            val dh = b.height * sc
            dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(b, null, dst, bgPaint)
        }

        val cx = w / 2f
        val cy = h * 0.40f
        val r = w * 0.36f * (1f + 0.018f * sin(two * t * 2f))

        glow.strokeWidth = w * 0.05f
        mid.strokeWidth = w * 0.02f
        core.strokeWidth = w * 0.006f
        c.drawCircle(cx, cy, r, glow)
        c.drawCircle(cx, cy, r, mid)
        c.drawCircle(cx, cy, r, core)

        oval.set(cx - r, cy - r, cx + r, cy + r)
        arc.strokeWidth = w * 0.014f
        c.drawArc(oval, t * 360f, 70f, false, arc)
        c.drawArc(oval, t * 360f + 180f, 70f, false, arc)

        val br = broom
        if (br != null) {
            val s = r * 1.6f
            val left = cx - s / 2f
            val top = cy - s / 2f
            dst.set(left, top, left + s, top + s)
            val angle = 22f * sin(two * t)
            c.save()
            c.rotate(angle, left + s * 0.78f, top + s * 0.16f)
            c.drawBitmap(br, null, dst, broomPaint)
            c.restore()
        }

        for (i in 0 until 14) {
            val ph = (t * 2f + i / 14f) % 1f
            val side = if (i % 2 == 0) -1f else 1f
            val x = cx + side * (r * 0.15f + ph * r * 0.75f)
            val y = cy + r * 0.50f - ph * r * 0.12f + sin(i * 1.7f) * r * 0.05f
            fill.color = Color.argb(((1f - ph) * 220f).toInt(), 120, 255, 150)
            c.drawCircle(x, y, (1f - ph) * w * 0.012f + 2f, fill)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }
}

class CacheAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CacheAccessibilityService? = null
        fun running(): Boolean = instance != null

        private val STORAGE = setOf(
            "storage", "storage & cache", "storage and cache",
            "хранилище", "память", "xotira"
        )
        private val CLEAR_CACHE = setOf(
            "clear cache", "очистить кеш", "очистить кэш",
            "keshni tozalash", "keshni o'chirish"
        )
    }

    private val queue = ArrayDeque<String>()
    private var busy = false
    private var gen = 0
    private var total = 0
    private var done = 0
    private val h = Handler(Looper.getMainLooper())

    private var overlay: View? = null
    private var statusText: TextView? = null

    override fun onServiceConnected() { instance = this }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // ---------- Oyna ----------
    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val root = FrameLayout(this)
        root.addView(
            NeonView(this),
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL

        val tv = TextView(this)
        tv.text = "0%"
        tv.textSize = 34f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(Color.rgb(140, 255, 160))
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, 0, 0, 32)

        val btn = Button(this)
        btn.text = "Bekor qilish"
        btn.isAllCaps = false
        btn.setTextColor(Color.rgb(160, 255, 180))
        val g = GradientDrawable()
        g.setColor(Color.argb(120, 0, 30, 10))
        g.setStroke(3, Color.rgb(40, 255, 100))
        g.cornerRadius = 40f
        btn.background = g
        btn.setOnClickListener { cancelCleaning() }

        col.addView(tv)
        col.addView(btn)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        lp.bottomMargin = 160
        root.addView(col, lp)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        )
        wm.addView(root, params)
        overlay = root
        statusText = tv
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {}
        overlay = null
        statusText = null
    }

    private fun updateText() {
        val p = if (total > 0) done * 100 / total else 0
        statusText?.text = "$p%"
    }

    private fun cancelCleaning() {
        gen++
        queue.clear()
        busy = false
        hideOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    // ---------- Tozalash ----------
    fun enqueue(packages: List<String>) {
        gen++
        queue.clear()
        queue.addAll(packages)
        total = packages.size
        done = 0
        busy = false
        showOverlay()
        updateText()
        next()
    }

    private fun next() {
        val p = queue.removeFirstOrNull()
        if (p == null) {
            busy = false
            hideOverlay()
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        busy = true
        val g = gen
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        h.postDelayed({ tick(g, 0) }, 1200L)
    }

    private fun tick(g: Int, attempt: Int) {
        if (g != gen) return
        if (attempt > 12) {
            done++
            updateText()
            next()
            return
        }
        val root = rootInActiveWindow
        if (root != null) {
            if (clickLabel(root, CLEAR_CACHE)) {
                h.postDelayed({
                    if (g == gen) {
                        done++
                        updateText()
                        next()
                    }
                }, 900L)
                return
            }
            clickLabel(root, STORAGE)
        }
        h.postDelayed({ tick(g, attempt + 1) }, 700L)
    }

    private fun clickLabel(root: AccessibilityNodeInfo, labels: Set<String>): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            val t = n.text?.toString()?.trim()?.lowercase()
            if (t != null && t in labels) {
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                if (c != null && c.isEnabled) {
                    c.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
            for (k in 0 until n.childCount) {
                val child = n.getChild(k)
                if (child != null) stack.add(child)
            }
        }
        return false
    }
}
