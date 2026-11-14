package com.salsaldrive.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * 화면 3개: 출발(동승자 선택) → 주행(물컵 화면) → 도착 리포트.
 * 기어가 D가 되면 주행이 시작되고, P가 되면 리포트로 넘어갑니다.
 */
class MainActivity : Activity(), VehicleSource.Listener {

    private val bg = Color.rgb(16, 21, 27)
    private val card = Color.rgb(26, 34, 43)
    private val ink = Color.rgb(238, 242, 245)
    private val muted = Color.rgb(163, 177, 190)
    private val blue = Color.rgb(127, 196, 245)
    private val amber = Color.rgb(242, 178, 76)

    private lateinit var root: FrameLayout
    private lateinit var scene: SceneView
    private lateinit var coachText: TextView
    private lateinit var timeText: TextView
    private lateinit var doseText: TextView

    private var passenger = Passenger.CHILD
    private var engine: DriveEngine? = null
    private var source: VehicleSource? = null
    private var demo: DemoSource? = null
    private var screen = "start"
    private var tts: TextToSpeech? = null
    private var lastSpoken = 0L
    private var lastShake = 0L
    private val prefs by lazy { getSharedPreferences("salsal", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        setContentView(root)
        tts = TextToSpeech(this) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN }
        // 실제 차량 값을 먼저 시도합니다. 안 되면 출발 화면의 "시연 모드"로 진행합니다.
        showStart()
    }

    override fun onDestroy() {
        source?.stop(); demo?.stop(); tts?.shutdown()
        super.onDestroy()
    }

    // ---------------- 차량 데이터 ----------------

    override fun onSpeed(mps: Double, timeMillis: Long) {
        runOnUiThread {
            val e = engine ?: return@runOnUiThread
            if (screen != "drive") return@runOnUiThread
            val event = e.onSpeed(mps, timeMillis)
            scene.speedKmh = mps * 3.6
            scene.accel = e.accel
            scene.level = e.level
            scene.drops = e.drops
            scene.warning = e.shaking
            if (event != null) {
                lastShake = SystemClock.elapsedRealtime()
                setCoach(ReportText.coach(event.type), warn = true)
            } else if (SystemClock.elapsedRealtime() - lastShake > 12_000 && coachText.tag == "warn") {
                setCoach("좋아요, 지금처럼만 가면 돼요", warn = false)
            }
            timeText.text = ReportText.clock(e.elapsedSeconds)
            doseText.text = e.dose.toInt().toString()
        }
    }

    override fun onGear(drive: Boolean, park: Boolean) {
        runOnUiThread {
            if (drive && screen == "start") startDrive(useDemo = false)
            if (park && screen == "drive") showReport()
        }
    }

    // ---------------- 화면 ----------------

    private fun showStart() {
        screen = "start"
        if (source == null) {
            val car = CarPropertySource(this)
            source = if (car.start(this)) car else null
        }
        val col = column(56)
        col.addView(text("오늘은 누구와 함께 타나요?", 44f, ink, true))
        col.addView(text("흔들림 판정 기준은 같고, 동승자에 따라 더 무겁게 보는 항목이 달라집니다.", 20f, muted, false), lp(top = 8))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val buttons = mutableListOf<Button>()
        Passenger.values().forEach { p ->
            val b = button(p.label, filled = false)
            b.setOnClickListener {
                passenger = p
                buttons.forEach { it.background = rounded(card, if (it == b) blue else Color.rgb(42, 53, 64), 3) }
            }
            buttons.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, dp(200), 1f).apply { marginEnd = dp(16) })
        }
        buttons[passenger.ordinal].performClick()
        col.addView(row, lp(top = 32))
        col.addView(text("기어를 D에 놓으면 측정이 자동으로 시작됩니다.", 20f, muted, false), lp(top = 32))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button("시연 모드로 시작", filled = false).apply { setOnClickListener { startDrive(useDemo = true) } }, LinearLayout.LayoutParams(0, dp(72), 1f).apply { marginEnd = dp(16) })
        actions.addView(button("주행 시작", filled = true).apply { setOnClickListener { startDrive(useDemo = source == null) } }, LinearLayout.LayoutParams(0, dp(72), 1f))
        col.addView(actions, lp(top = 16))
        setScreen(col)
    }

    private fun startDrive(useDemo: Boolean) {
        val sens = prefs.getFloat("sens_${passenger.name}", 1f).toDouble()
        engine = DriveEngine(passenger, sens)
        screen = "drive"
        // 시연 모드에서는 실제 차량 값이 섞이지 않도록 차량 연결을 잠시 끊음
        if (useDemo) { source?.stop(); source = null }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(32), dp(24), dp(32), dp(24)) }
        scene = SceneView(this).apply { passengerLabel = passenger.label }
        row.addView(scene, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.1f))

        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        coachText = text("${passenger.label} 기준으로 살살 가볼게요", 30f, ink, true).apply {
            background = rounded(card); setPadding(dp(24), dp(24), dp(24), dp(24)); gravity = Gravity.CENTER_VERTICAL
        }
        side.addView(coachText, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        timeText = text("0:00", 36f, ink, true)
        doseText = text("0", 36f, ink, true)
        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(stat("주행 시간", timeText), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(12) })
        stats.addView(stat("멀미 누적 지수", doseText), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        side.addView(stats, lp(top = 12))

        if (useDemo) {
            demo = DemoSource().also { d ->
                val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                tools.addView(button("급제동 재현", filled = false).apply { setOnClickListener { d.hardBrake() } }, LinearLayout.LayoutParams(0, dp(60), 1f).apply { marginEnd = dp(12) })
                tools.addView(button("급가속 재현", filled = false).apply { setOnClickListener { d.hardAccel() } }, LinearLayout.LayoutParams(0, dp(60), 1f))
                side.addView(text("시연용 가상 주행 데이터", 16f, muted, false), lp(top = 12))
                side.addView(tools, lp(top = 8))
                side.addView(button("기어 P · 도착", filled = true).apply { setOnClickListener { showReport() } }, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)).apply { topMargin = dp(12) })
                d.start(this)
            }
        }
        row.addView(side, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f).apply { marginStart = dp(24) })
        setScreen(row)
        speak(coachText.text.toString())
    }

    private fun showReport() {
        val e = engine ?: return
        demo?.stop(); demo = null
        screen = "report"
        val level = e.level.toInt()
        val last = if (prefs.contains("last_${passenger.name}")) prefs.getInt("last_${passenger.name}", 0) else null
        prefs.edit().putInt("last_${passenger.name}", level).apply()

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(32), dp(24), dp(32), dp(24)) }
        val left = column(28).apply { background = rounded(card) }
        left.addView(text("오늘의 도착 수위 · ${passenger.label} 기준", 20f, muted, false))
        left.addView(text("$level%", 96f, blue, true))
        left.addView(text(ReportText.diff(level, last), 24f, ink, true))
        left.addView(text("뒷자리 손님의 한 줄 후기", 16f, muted, false), lp(top = 24))
        left.addView(text(ReportText.review(level, e.events), 22f, ink, true), lp(top = 4))
        left.addView(text("주행 ${ReportText.clock(e.elapsedSeconds)} · 흘린 물 ${e.drops}방울", 18f, muted, false), lp(top = 24))
        row.addView(left, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val counts = ShakeType.values().joinToString("   ") { t -> "${t.label} ${e.events.count { it.type == t }}회" }
        right.addView(text(counts, 24f, ink, true).apply { background = rounded(card); setPadding(dp(24), dp(20), dp(24), dp(20)) })
        val list = e.events.takeLast(5).joinToString("\n") { "${ReportText.clock(it.secondsFromStart)}   ${it.type.label}" + if (it.strong) " (강)" else "" }
        right.addView(text(if (list.isEmpty()) "흔들림 없이 도착했어요." else list, 22f, amber, false).apply {
            background = rounded(card); setPadding(dp(24), dp(20), dp(24), dp(20))
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })

        val ask = text("오늘 함께 탄 ${passenger.label}, 어땠나요?", 18f, muted, false)
        right.addView(ask, lp(top = 16))
        val fb = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("편안했어요" to 0.97f, "보통이에요" to 1f, "힘들어했어요" to 1.08f).forEach { (label, factor) ->
            fb.addView(button(label, filled = false).apply {
                setOnClickListener {
                    // 가족별 보정: 반응에 맞춰 다음 주행의 판정을 조금 엄격하게/느슨하게
                    val key = "sens_${passenger.name}"
                    val v = (prefs.getFloat(key, 1f) * factor).coerceIn(0.85f, 1.3f)
                    prefs.edit().putFloat(key, v).apply()
                    ask.text = if (factor > 1f) "다음 주행부터 기준을 조금 더 엄격하게 볼게요." else "알려주셔서 고마워요. 기준에 반영할게요."
                }
            }, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        }
        right.addView(fb, lp(top = 8))
        right.addView(text(ReportText.tip(e.events), 22f, ink, true), lp(top = 16))
        right.addView(button("새 주행", filled = true).apply { setOnClickListener { showStart() } }, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)).apply { topMargin = dp(12) })
        row.addView(right, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.2f).apply { marginStart = dp(24) })
        setScreen(row)
        speak("도착했어요. 오늘 수위는 ${level}퍼센트예요.")
    }

    // ---------------- 도우미 ----------------

    private fun setCoach(s: String, warn: Boolean) {
        coachText.text = s
        coachText.tag = if (warn) "warn" else "good"
        coachText.setTextColor(if (warn) Color.rgb(247, 206, 138) else ink)
        coachText.background = rounded(if (warn) Color.rgb(58, 44, 20) else card)
        speak(s)
    }

    /** 잔소리가 되지 않도록 음성은 8초에 한 번까지만 */
    private fun speak(s: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSpoken < 8_000) return
        lastSpoken = now
        tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "coach")
    }

    private fun setScreen(v: View) { root.removeAllViews(); root.addView(v, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun column(pad: Int) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(pad), dp(pad), dp(pad), dp(pad)) }

    private fun text(s: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: Int, stroke: Int = Color.TRANSPARENT, strokeDp: Int = 0) = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat(); setColor(fill)
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun button(label: String, filled: Boolean) = Button(this).apply {
        text = label; textSize = 20f; isAllCaps = false
        setTextColor(if (filled) Color.rgb(11, 22, 32) else ink)
        background = if (filled) rounded(blue) else rounded(card, Color.rgb(42, 53, 64), 2)
    }

    private fun stat(label: String, value: TextView) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = rounded(card); setPadding(dp(20), dp(16), dp(20), dp(16))
        addView(text(label, 16f, muted, false)); addView(value)
    }
}
