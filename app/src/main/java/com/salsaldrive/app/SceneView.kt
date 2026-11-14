package com.salsaldrive.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * 주행 화면: 뒤에서 내려다본 내 차가 지붕 위 물컵을 싣고 달리는 모습.
 * 모든 좌표는 900 x 1000 "장면 좌표"로 그리고, 화면 크기에 맞게 확대/축소합니다.
 */
class SceneView(context: Context) : View(context) {

    // 액티비티가 넣어주는 값
    var speedKmh = 0.0
    var level = 100.0
    var accel = 0.0
    var drops = 0
    var passengerLabel = "아이"
    var warning = false

    private val roads: List<Bitmap> = listOf(
        R.drawable.road0, R.drawable.road1, R.drawable.road2, R.drawable.road3,
        R.drawable.road4, R.drawable.road5, R.drawable.road6, R.drawable.road7,
    ).map { BitmapFactory.decodeResource(resources, it) }
    private val car = BitmapFactory.decodeResource(resources, R.drawable.car)
    private val glass = BitmapFactory.decodeResource(resources, R.drawable.glass)
    private val sceneRect = RectF(0f, 0f, 900f, 1000f)

    // 유리컵 안쪽 모양 (장면 좌표)
    private val cupPath = Path().apply {
        moveTo(336f, 217f); lineTo(564f, 217f); lineTo(538f, 509f); lineTo(528f, 521f)
        lineTo(450f, 528f); lineTo(372f, 521f); lineTo(362f, 509f); close()
    }
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 217f, 0f, 532f, Color.rgb(143, 208, 250), Color.rgb(31, 111, 176), Shader.TileMode.CLAMP)
    }
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(169, 218, 251) }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255) }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(90, 174, 232) }
    private val roadDropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(92, 166, 222) }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(184, 5, 7, 10) }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 애니메이션 상태
    private var roadOffset = 0.0
    private var slosh = 0.0
    private var sloshVelocity = 0.0
    private var lastFrame = 0L
    private var lastDrops = 0
    private data class AirDrop(val start: Long, val x: Float)
    private data class RoadDrop(val x: Float, var y: Float)
    private val airDrops = mutableListOf<AirDrop>()
    private val roadDrops = mutableListOf<RoadDrop>()
    private var dropSeq = 0

    fun reset() {
        airDrops.clear(); roadDrops.clear(); lastDrops = 0; slosh = 0.0; sloshVelocity = 0.0
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastFrame == 0L) 0.016 else min(0.1, (now - lastFrame) / 1000.0)
        lastFrame = now
        update(now, dt)

        val scale = min(width / 900f, height / 1000f)
        canvas.save()
        canvas.translate((width - 900f * scale) / 2f, (height - 1000f * scale) / 2f)
        canvas.scale(scale, scale)

        canvas.drawBitmap(roads[((-roadOffset.toInt()) % 8 + 8) % 8], null, sceneRect, null)
        for (d in roadDrops) {
            val w = 26f + (d.y - 830f) * 0.24f
            val h = w * 0.4f
            val cx = 450f + (d.x - 450f) * (1f + (d.y - 830f) / 300f)
            canvas.drawOval(cx - w / 2, d.y - h / 2, cx + w / 2, d.y + h / 2, roadDropPaint)
        }
        canvas.drawBitmap(car, null, sceneRect, null)
        drawWater(canvas)
        canvas.drawBitmap(glass, null, sceneRect, null)
        drawAirDrops(canvas, now)
        drawHud(canvas)
        canvas.restore()

        postInvalidateOnAnimation()
    }

    private fun update(now: Long, dt: Double) {
        // 새로 흘린 물방울만큼 컵에서 떨어뜨림
        while (lastDrops < drops) {
            dropSeq++
            airDrops.add(AirDrop(now + (lastDrops % 2) * 250L, 446f + ((dropSeq * 37) % 50) - 25))
            lastDrops++
        }
        // 물 출렁임: 감속하면 앞(화면 위)으로, 가속하면 뒤로
        val target = (accel * 7).coerceIn(-18.0, 18.0)
        sloshVelocity += ((target - slosh) * 40 - sloshVelocity * 7) * dt
        slosh += sloshVelocity * dt
        // 차로선은 속도에 맞춰 다가옴
        roadOffset += speedKmh * 0.8 * dt
        // 떨어진 물방울은 도로 위에서 뒤로 멀어짐
        val landed = airDrops.filter { now - it.start >= 1000 }
        airDrops.removeAll(landed)
        landed.forEach { roadDrops.add(RoadDrop(it.x, 842f)) }
        val dy = ((3 + speedKmh * 0.35) * 2 * dt).toFloat()
        roadDrops.forEach { it.y += dy * (1f + (it.y - 830f) / 120f) }
        roadDrops.removeAll { it.y > 1040f }
    }

    private fun drawWater(canvas: Canvas) {
        val lvl = level.coerceIn(0.0, 100.0)
        val rTop = 315 - 262.7 * lvl / 100 + slosh
        val top = (217 + rTop).toFloat()
        val inset = (maxOf(0.0, rTop) * 0.0976).toFloat()
        val sh = (40 + min(16.0, abs(accel) * 5)).toFloat()
        canvas.save()
        canvas.clipPath(cupPath)
        canvas.drawRect(332f, top, 568f, 532f, waterPaint)
        val surf = RectF(332f + inset, top - sh / 2, 568f - inset, top + sh / 2)
        canvas.drawOval(surf, surfacePaint)
        val sw = surf.width()
        canvas.drawOval(surf.left + sw * 0.18f, surf.top + sh * 0.28f, surf.left + sw * 0.64f, surf.top + sh * 0.54f, shinePaint)
        canvas.restore()
    }

    private val dropPath = listOf(456f to 540f, 452f to 625f, 447f to 715f, 446f to 800f, 446f to 842f)

    private fun drawAirDrops(canvas: Canvas, now: Long) {
        for (d in airDrops) {
            if (now < d.start) continue
            val u = ((now - d.start) / 1000f).coerceIn(0f, 0.999f)
            val f = u * 4; val i = f.toInt(); val fr = (f - i) * (f - i)
            val (x0, y0) = dropPath[i]; val (x1, y1) = dropPath[i + 1]
            val x = x0 + (x1 - x0) * fr + (d.x - 446f)
            val y = y0 + (y1 - y0) * fr
            val r = 10f + u * 5f
            val p = Path().apply {
                addCircle(x, y, r, Path.Direction.CW)
                moveTo(x - r * 0.92f, y - r * 0.35f); lineTo(x + r * 0.92f, y - r * 0.35f); lineTo(x, y - r * 2.2f); close()
            }
            canvas.drawPath(p, dropPaint)
        }
    }

    private fun drawHud(canvas: Canvas) {
        val ink = Color.rgb(238, 242, 245); val muted = Color.rgb(163, 177, 190)
        val water = Color.rgb(140, 203, 247); val amber = Color.rgb(242, 178, 76)
        fun t(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean, align: Paint.Align = Paint.Align.LEFT): Float {
            textPaint.textSize = size; textPaint.color = color; textPaint.textAlign = align
            textPaint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(s, x, y, textPaint)
            return textPaint.measureText(s)
        }
        val sp = speedKmh.toInt().toString()
        val w = t(sp, 40f, 122f, 90f, ink, true)
        t("km/h", 40f + w + 10f, 122f, 38f, muted, false)
        t("D", 450f, 118f, 88f, ink, true, Paint.Align.CENTER)
        t("수위", 860f, 62f, 38f, muted, false, Paint.Align.RIGHT)
        t("${level.toInt()}%", 860f, 128f, 64f, if (warning) amber else water, true, Paint.Align.RIGHT)
        canvas.drawRoundRect(24f, 842f, 380f, 986f, 18f, 18f, boxPaint)
        t("$passengerLabel 기준", 44f, 896f, 40f, water, false)
        t("흘린 물 ${drops}방울", 44f, 962f, 52f, Color.rgb(220, 227, 234), false)
    }
}
