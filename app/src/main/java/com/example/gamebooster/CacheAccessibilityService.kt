package com.example.gamebooster

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RectF
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

class NeonView(ctx: Context) : View(ctx) {

    private val bg: Bitmap? = try {
        val id = ctx.resources.getIdentifier("game_bg", "drawable", ctx.packageName)
        if (id != 0) BitmapFactory.decodeResource(ctx.resources, id) else null
    } catch (_: Throwable) {
        null
    }

    private fun neon(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = color
        return p
    }

    private val glow = neon(Color.argb(70, 0, 160, 255))
    private val mid = neon(Color.argb(170, 0, 200, 255))
    private val core = neon(Color.rgb(200, 245, 255))

    private val paths = ArrayList<Path>()
    private val measures = ArrayList<PathMeasure>()
    private val seg = Path()
    private var progress = 0f

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    private fun base(): List<Path> {
        val list = ArrayList<Path>()

        val body = Path()
        body.moveTo(45f, 25f)
        body.cubicTo(20f, 25f, 8f, 60f, 4f, 95f)
        body.cubicTo(1f, 118f, 14f, 130f, 30f, 128f)
        body.cubicTo(48f, 126f, 62f, 100f, 78f, 90f)
        body.lineTo(122f, 90f)
        body.cubicTo(138f, 100f, 152f, 126f, 170f, 128f)
        body.cubicTo(186f, 130f, 199f, 118f, 196f, 95f)
        body.cubicTo(192f, 60f, 180f, 25f, 155f, 25f)
        body.cubicTo(140f, 25f, 135f, 32f, 125f, 32f)
        body.lineTo(75f, 32f)
        body.cubicTo(65f, 32f, 60f, 25f, 45f, 25f)
        body.close()
        list.add(body)

        val dpad = Path()
        dpad.moveTo(44f, 43f)
        dpad.lineTo(56f, 43f)
        dpad.lineTo(56f, 49f)
        dpad.lineTo(62f, 49f)
        dpad.lineTo(62f, 61f)
        dpad.lineTo(56f, 61f)
        dpad.lineTo(56f, 67f)
        dpad.lineTo(44f, 67f)
        dpad.lineTo(44f, 61f)
        dpad.lineTo(38f, 61f)
        dpad.lineTo(38f, 49f)
        dpad.lineTo(44f, 49f)
        dpad.close()
        list.add(dpad)

        val bx = floatArrayOf(150f, 150f, 139f, 161f)
        val by = floatArrayOf(44f, 66f, 55f, 55f)
        for (i in bx.indices) {
            val c = Path()
            c.addCircle(bx[i], by[i], 5f, Path.Direction.CW)
            list.add(c)
        }

        val sx = floatArrayOf(75f, 125f)
        for (x in sx) {
            val c = Path()
            c.addCircle(x, 70f, 11f, Path.Direction.CW)
            list.add(c)
        }
        return list
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        paths.clear()
        measures.clear()
        val s = w * 0.75f / 200f
        val m = Matrix()
        m.setScale(s, s)
        m.postTranslate(w / 2f - 100f * s, h * 0.38f - 65f * s)
        for (b in base()) {
            val p = Path()
            b.transform(m, p)
            paths.add(p)
            measures.add(PathMeasure(p, false))
        }
        glow.strokeWidth = 5f * s
        mid.strokeWidth = 2.2f * s
        core.strokeWidth = 0.9f * s
    }

    override fun onDraw(c: Canvas) {
        val b = bg
        if (b != null) {
            val sc = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val dw = b.width * sc
            val dh = b.height * sc
            val dst = RectF((width - dw) / 2f, (height - dh) / 2f, (width + dw) / 2f, (height + dh) / 2f)
            c.drawBitmap(b, null, dst, null)
        } else {
            c.drawColor(Color.BLACK)
        }
        c.drawColor(Color.argb(90, 0, 0, 20))

        val p = minOf(1f, progress / 0.8f)
        for (i in paths.indices) {
            seg.reset()
            val pm = measures[i]
            pm.getSegment(0f, pm.length * p, seg, true)
            c.drawPath(seg, glow)
            c.drawPath(seg, mid)
            c.drawPath(seg, core)
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
        tv.text = "Tozalanmoqda..."
        tv.textSize = 20f
        tv.setTextColor(Color.rgb(160, 235, 255))
        tv.gravity = Gravity.CENTER
        tv.setPadding(0, 0, 0, 32)

        val btn = Button(this)
        btn.text = "Bekor qilish"
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
        statusText?.text = "Tozalanmoqda... $done / $total"
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
