package com.example.gamebooster

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

class NeonView(ctx: Context) : View(ctx) {

    private val bg: Bitmap? = load("game_bg", 1)

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

    private fun line(color: Int): Paint {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = color
        return p
    }

    // Halqa
    private val glow = line(Color.argb(60, 0, 255, 70))
    private val mid = line(Color.argb(150, 40, 255, 100))
    private val core = line(Color.rgb(190, 255, 205))
    private val arc = line(Color.rgb(220, 255, 230))

    // Supurgi
    private val bGlow = line(Color.argb(60, 0, 255, 70))
    private val bMid = line(Color.argb(150, 40, 255, 100))
    private val bCore = line(Color.rgb(190, 255, 205))
    private val bFill = Paint(Paint.ANTI_ALIAS_FLAG)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val oval = RectF()
    private val dst = RectF()

    private val handle = Path()
    private val hole = Path()
    private val ferrule = Path()
    private val bristles = ArrayList<Path>()
    private var bristleShader: Shader? = null
    private var handleShader: Shader? = null
    private var unit = 1f

    private var t = 0f
    private var startNs = 0L
    private var lastNs = 0L
    private var phi = 0f
    private var phiVel = 0f

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            t = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val u = w * 0.36f * 1.15f
        unit = u

        handle.reset()
        handle.addRoundRect(
            RectF(-0.035f * u, -0.70f * u, 0.035f * u, -0.14f * u),
            0.035f * u, 0.035f * u, Path.Direction.CW
        )
        hole.reset()
        hole.addCircle(0f, -0.62f * u, 0.012f * u, Path.Direction.CW)

        ferrule.reset()
        ferrule.moveTo(-0.085f * u, 0f)
        ferrule.lineTo(0.085f * u, 0f)
        ferrule.lineTo(0.07f * u, -0.15f * u)
        ferrule.lineTo(-0.07f * u, -0.15f * u)
        ferrule.close()

        bristles.clear()
        for (i in 0 until 5) {
            val tl = -0.085f * u + i * 0.034f * u + 0.003f * u
            val tr = tl + 0.028f * u
            val bl = -0.20f * u + i * 0.08f * u + 0.006f * u
            val br = bl + 0.068f * u
            val by = (0.42f - abs(i - 2) * 0.035f) * u
            val p = Path()
            p.moveTo(tl, 0f)
            p.lineTo(tr, 0f)
            p.lineTo(br, by)
            p.lineTo(bl, by)
            p.close()
            bristles.add(p)
        }

        bristleShader = LinearGradient(
            0f, 0f, 0f, 0.42f * u,
            Color.rgb(15, 150, 35), Color.rgb(110, 255, 130), Shader.TileMode.CLAMP
        )
        handleShader = LinearGradient(
            -0.035f * u, 0f, 0.035f * u, 0f,
            Color.rgb(20, 160, 40), Color.rgb(120, 255, 140), Shader.TileMode.CLAMP
        )
        bGlow.strokeWidth = u * 0.05f
        bMid.strokeWidth = u * 0.02f
        bCore.strokeWidth = u * 0.008f
    }

    private fun drawShape(c: Canvas, p: Path, sh: Shader?) {
        c.drawPath(p, bGlow)
        c.drawPath(p, bMid)
        bFill.shader = sh
        c.drawPath(p, bFill)
        c.drawPath(p, bCore)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val two = (2.0 * PI).toFloat()
        val deg = (PI / 180.0).toFloat()

        val now = System.nanoTime()
        if (startNs == 0L) startNs = now
        var dt = if (lastNs == 0L) 0.016f else (now - lastNs) / 1000000000f
        lastNs = now
        if (dt > 0.05f) dt = 0.05f
        val sec = (now - startNs) / 1000000000f

        // ---------- Fon ----------
        c.drawColor(Color.BLACK)
        val b = bg
        if (b != null) {
            val sc = maxOf(w / b.width, h / b.height)
            val dw = b.width * sc
            val dh = b.height * sc
            dst.set((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
            c.drawBitmap(b, null, dst, bgPaint)
        }

        // ---------- Halqa ----------
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

        // ---------- Supurgi fizikasi ----------
        val u = unit
        val omega = two / 1.8f
        val bp = omega * sec
        val thetaMax = 24f
        val theta = thetaMax * sin(bp)
        val vn = -cos(theta * deg) * cos(bp)
        val target = 32f * vn
        val wn = 12f
        val zeta = 0.5f
        val hh = dt / 4f
        for (k in 0 until 4) {
            val acc = wn * wn * (target - phi) - 2f * zeta * wn * phiVel
            phiVel += acc * hh
            phi += phiVel * hh
        }

        val px = cx
        val py = cy - 0.16f * u

        c.save()
        c.translate(px, py)
        c.rotate(theta)
        c.translate(0f, 0.30f * u)

        c.save()
        c.rotate(phi)
        for (p in bristles) drawShape(c, p, bristleShader)
        c.restore()

        drawShape(c, handle, handleShader)
        c.drawPath(hole, bCore)
        drawShape(c, ferrule, handleShader)
        c.restore()

        // ---------- Chang zarrachalari ----------
        val life = 0.7f
        val n = 16
        for (i in 0 until n) {
            val u2 = sec / life + i / n.toFloat()
            val age = u2 - floor(u2)
            val sp = sec - age * life
            val a = omega * sp
            val th = thetaMax * deg * sin(a)
            val tipX = px - 0.72f * u * sin(th)
            val tipY = py + 0.72f * u * cos(th)
            val dir = if (-cos(a) >= 0f) 1f else -1f
            val x = tipX + dir * age * u * 0.55f + sin(i * 12.9898f) * u * 0.05f
            val y = tipY - age * u * 0.18f + sin(i * 78.233f) * u * 0.04f
            fill.color = Color.argb(((1f - age) * 210f).toInt(), 120, 255, 150)
            c.drawCircle(x, y, (1f - age) * w * 0.011f + 2f, fill)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startNs = 0L
        lastNs = 0L
        phi = 0f
        phiVel = 0f
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
