package com.example.gamebooster

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

class AppUse(val pkg: String, val label: String, val ms: Long, val icon: Drawable?)

class UsageResult(val screenMs: Long, val apps: List<AppUse>)

fun fmt(ms: Long): String {
    val m = ms / 60000L
    val h = m / 60L
    val mm = m % 60L
    return when {
        h > 0L && mm > 0L -> "$h soat $mm daq"
        h > 0L -> "$h soat"
        m > 0L -> "$m daq"
        else -> "1 daqiqadan kam"
    }
}

object UsageReport {

    fun hasAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return cal.timeInMillis
    }

    private fun screenFromEvents(usm: UsageStatsManager, start: Long, end: Long): Long {
        if (Build.VERSION.SDK_INT < 28) return 0L
        val ev = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        var on = -1L
        var total = 0L
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val type = e.eventType
            if (type == 15) {
                if (on < 0L) on = e.timeStamp
            } else if (type == 16) {
                if (on >= 0L) {
                    total += e.timeStamp - on
                    on = -1L
                }
            }
        }
        if (on >= 0L) total += end - on
        return total
    }

    fun load(c: Context, days: Int): UsageResult {
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = c.packageManager
        val end = System.currentTimeMillis()
        val start = startOfDay(days - 1)

        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end) ?: emptyList()
        val sums = HashMap<String, Long>()
        for (s in stats) {
            val t = s.totalTimeInForeground
            if (t > 0L) sums[s.packageName] = (sums[s.packageName] ?: 0L) + t
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePkg = pm.resolveActivity(homeIntent, 0)?.activityInfo?.packageName

        var appSum = 0L
        val list = ArrayList<AppUse>()
        for ((pkg, ms) in sums) {
            if (pkg == homePkg) continue
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            appSum += ms
            if (ms < 30000L) continue
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg
            }
            val icon = try {
                pm.getApplicationIcon(pkg)
            } catch (_: Exception) {
                null
            }
            list.add(AppUse(pkg, label, ms, icon))
        }
        list.sortByDescending { it.ms }
        val screen = maxOf(screenFromEvents(usm, start, end), appSum)
        return UsageResult(screen, list)
    }
}

class ReportView(ctx: Context, private val onBack: () -> Unit) : FrameLayout(ctx) {

    private val green = Color.rgb(140, 255, 160)
    private val neon = Color.rgb(40, 255, 100)
    private var days = 1

    private val summaryTitle = TextView(ctx)
    private val summaryTime = TextView(ctx)
    private val summaryAvg = TextView(ctx)
    private val list = LinearLayout(ctx)
    private val periodBtns = ArrayList<TextView>()
    private val periodDays = intArrayOf(1, 7, 30)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun pill(filled: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(22).toFloat()
        g.setColor(if (filled) Color.argb(110, 40, 255, 100) else Color.argb(60, 0, 20, 5))
        g.setStroke(dp(1) + 1, neon)
        return g
    }

    init {
        isClickable = true
        setBackgroundColor(Color.BLACK)
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT

        val bgId = resources.getIdentifier("game_bg", "drawable", ctx.packageName)
        if (bgId != 0) {
            val iv = ImageView(ctx)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.alpha = 0.7f
            try {
                iv.setImageResource(bgId)
            } catch (_: Throwable) {
            }
            addView(iv, FrameLayout.LayoutParams(mp, mp))
        }

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(44), dp(16), dp(16))

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = TextView(ctx)
        back.text = "←"
        back.textSize = 28f
        back.setTextColor(green)
        back.setPadding(0, 0, dp(16), 0)
        back.setOnClickListener { onBack() }
        val title = TextView(ctx)
        title.text = "Hisobot"
        title.textSize = 24f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(green)
        head.addView(back)
        head.addView(title)
        col.addView(head)

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        val names = arrayOf("1 kunlik", "1 haftalik", "1 oylik")
        for (i in 0 until 3) {
            val b = TextView(ctx)
            b.text = names[i]
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setTextColor(green)
            b.setPadding(0, dp(10), 0, dp(10))
            val lp = LinearLayout.LayoutParams(0, wc, 1f)
            lp.setMargins(dp(4), dp(14), dp(4), dp(10))
            b.setOnClickListener { setPeriod(periodDays[i]) }
            periodBtns.add(b)
            row.addView(b, lp)
        }
        col.addView(row)

        summaryTitle.text = "Bugun ekran yoniq vaqti"
        summaryTitle.textSize = 14f
        summaryTitle.setTextColor(Color.argb(200, 140, 255, 160))
        summaryTime.textSize = 30f
        summaryTime.typeface = Typeface.DEFAULT_BOLD
        summaryTime.setTextColor(green)
        summaryAvg.textSize = 13f
        summaryAvg.setTextColor(Color.argb(200, 140, 255, 160))
        col.addView(summaryTitle)
        col.addView(summaryTime)
        col.addView(summaryAvg)

        val sv = ScrollView(ctx)
        list.orientation = LinearLayout.VERTICAL
        sv.addView(list)
        val slp = LinearLayout.LayoutParams(mp, 0, 1f)
        slp.topMargin = dp(12)
        col.addView(sv, slp)

        addView(col, FrameLayout.LayoutParams(mp, mp))
        updatePeriodButtons()
    }

    private fun setPeriod(d: Int) {
        days = d
        updatePeriodButtons()
        refresh()
    }

    private fun updatePeriodButtons() {
        for (i in 0 until periodBtns.size) {
            val sel = periodDays[i] == days
            periodBtns[i].background = pill(sel)
            periodBtns[i].typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    fun refresh() {
        list.removeAllViews()
        if (!UsageReport.hasAccess(context)) {
            summaryTime.text = "—"
            summaryAvg.text = ""
            showPermission()
            return
        }
        summaryTime.text = "..."
        summaryAvg.text = ""
        val d = days
        val appCtx = context.applicationContext
        Thread {
            val res = try {
                UsageReport.load(appCtx, d)
            } catch (_: Throwable) {
                null
            }
            post {
                if (d == days) render(res, d)
            }
        }.start()
    }

    private fun showPermission() {
        val t = TextView(context)
        t.text = "Hisobot uchun \"Foydalanish ma'lumotlariga kirish\" ruxsati kerak. Ruxsat berish ni bosing, ro'yxatdan CLENOX ni toping va yoqing."
        t.textSize = 15f
        t.setTextColor(green)
        t.setPadding(0, dp(24), 0, dp(16))
        val b = TextView(context)
        b.text = "Ruxsat berish"
        b.textSize = 16f
        b.gravity = Gravity.CENTER
        b.setTextColor(green)
        b.setPadding(dp(24), dp(12), dp(24), dp(12))
        b.background = pill(true)
        b.setOnClickListener {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:" + context.packageName))
                )
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
        list.addView(t)
        list.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun render(res: UsageResult?, d: Int) {
        list.removeAllViews()
        if (res == null) {
            summaryTime.text = "Xatolik"
            return
        }
        summaryTitle.text = when (d) {
            1 -> "Bugun ekran yoniq vaqti"
            7 -> "Oxirgi 7 kun ekran yoniq vaqti"
            else -> "Oxirgi 30 kun ekran yoniq vaqti"
        }
        summaryTime.text = fmt(res.screenMs)
        summaryAvg.text = if (d > 1) "Kuniga o'rtacha: " + fmt(res.screenMs / d) else ""

        if (res.apps.isEmpty()) {
            val t = TextView(context)
            t.text = "Ma'lumot topilmadi"
            t.textSize = 15f
            t.setTextColor(green)
            t.setPadding(0, dp(24), 0, 0)
            list.addView(t)
            return
        }
        val top = res.apps[0].ms.toFloat()
        for (a in res.apps) list.addView(row(a, top))
    }

    private fun row(a: AppUse, top: Float): View {
        val r = LinearLayout(context)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(8), 0, dp(8))

        val icon = ImageView(context)
        val ic = a.icon
        if (ic != null) icon.setImageDrawable(ic)
        r.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))

        val mid = LinearLayout(context)
        mid.orientation = LinearLayout.VERTICAL
        mid.setPadding(dp(12), 0, dp(12), 0)
        val name = TextView(context)
        name.text = a.label
        name.textSize = 15f
        name.setTextColor(Color.WHITE)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        mid.addView(name)

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.weightSum = 1f
        val frac = (a.ms / top).coerceIn(0.03f, 1f)
        val fillV = View(context)
        val g = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(15, 150, 35), Color.rgb(110, 255, 130))
        )
        g.cornerRadius = dp(3).toFloat()
        fillV.background = g
        bar.addView(fillV, LinearLayout.LayoutParams(0, dp(6), frac))
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
        blp.topMargin = dp(6)
        mid.addView(bar, blp)

        r.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val time = TextView(context)
        time.text = fmt(a.ms)
        time.textSize = 14f
        time.setTextColor(green)
        r.addView(time)
        return r
    }
}
