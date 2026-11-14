package com.salsaldrive.app

import kotlin.math.abs
import kotlin.math.max

/**
 * 측정 엔진: 차량 속도 값만으로 흔들림을 판정하고 "수위"를 계산합니다.
 *
 * 판정 기준(공통, m/s²)
 *  - 주의 1.5 이상: 승차감 연구에서 불편이 시작되는 수준 (표시만, 감점 없음)
 *  - 흔들림 2.0 이상: 착석 승객 기준. 한국교통안전공단 운행기록 기준의 급감속(초당 7.5km/h)과 비슷
 *  - 강한 흔들림 3.0 이상: 같은 기준의 급가속(초당 11km/h)에 해당
 *
 * 동승자별 차이는 기준값이 아니라 "무엇을 더 무겁게 보느냐"(가중치)로 둡니다.
 * 가중치는 설계 초기값이며 실제 사용 데이터로 조정할 값입니다.
 */
enum class Passenger(
    val label: String,
    val wBrake: Double,
    val wLaunch: Double,
    val wAccel: Double,
    val wDose: Double,
) {
    CHILD("아이", 1.0, 1.0, 1.0, 2.5),
    PREGNANT("임산부", 1.5, 1.5, 1.0, 1.0),
    SENIOR("어르신", 1.5, 1.5, 1.0, 1.0),
    PET("반려동물", 1.25, 1.25, 1.25, 1.0),
}

enum class ShakeType(val label: String) { BRAKE("급제동"), ACCEL("급가속"), LAUNCH("급출발") }

data class ShakeEvent(val secondsFromStart: Long, val type: ShakeType, val magnitude: Double, val strong: Boolean)

class DriveEngine(
    val passenger: Passenger,
    /** 가족별 보정값. 1.0이 기본이고, "힘들어했어요"가 쌓이면 커져서 더 엄격하게 봅니다. */
    private val sensitivity: Double = 1.0,
) {
    companion object {
        const val CAUTION = 1.5
        const val SHAKE = 2.0
        const val STRONG = 3.0
        /** 속도 값의 잡음을 줄이는 지수 평활 계수 */
        const val SMOOTHING = 0.4
        /** 저속 출발로 볼 속도 (10km/h) */
        const val LAUNCH_SPEED_MPS = 10 / 3.6
    }

    var level = 100.0; private set          // 수위(%), 100에서 시작해 흔들릴 때마다 줄어듦
    var dose = 0.0; private set             // 멀미 누적 지수 (가속도 제곱의 누적)
    var drops = 0; private set              // 흘린 물방울 수
    var accel = 0.0; private set            // 평활한 가감속 (m/s², 음수 = 감속)
    var speedMps = 0.0; private set
    var caution = false; private set
    var shaking = false; private set
    val events = mutableListOf<ShakeEvent>()

    private var lastSpeed: Double? = null
    private var lastTime = 0L
    private var startTime = -1L
    private var penalty = 0.0

    private fun weightOf(type: ShakeType) = when (type) {
        ShakeType.BRAKE -> passenger.wBrake
        ShakeType.LAUNCH -> passenger.wLaunch
        ShakeType.ACCEL -> passenger.wAccel
    }

    val elapsedSeconds: Long get() = if (startTime < 0) 0 else max(0L, (lastTime - startTime) / 1000)

    /**
     * 새 속도 값이 들어올 때마다 호출합니다.
     * @param mps 차량 속도 (m/s)
     * @param timeMillis 값이 측정된 시각 (ms)
     * @return 이번 값으로 새 흔들림이 판정되면 그 이벤트, 아니면 null
     */
    fun onSpeed(mps: Double, timeMillis: Long): ShakeEvent? {
        speedMps = mps
        if (startTime < 0) startTime = timeMillis
        val prev = lastSpeed
        if (prev == null) {
            lastSpeed = mps; lastTime = timeMillis
            return null
        }
        val dt = (timeMillis - lastTime) / 1000.0
        if (dt < 0.05) return null           // 너무 촘촘한 값은 건너뜀
        val raw = (mps - prev) / dt
        accel = accel * (1 - SMOOTHING) + raw * SMOOTHING
        lastSpeed = mps; lastTime = timeMillis

        val mag = abs(accel)
        if (mag > 0.3) dose += mag * mag * dt

        val shakeLimit = SHAKE / sensitivity
        val over = mag >= shakeLimit
        caution = mag >= CAUTION / sensitivity && !over
        var event: ShakeEvent? = null
        if (over && !shaking) {
            val type = when {
                accel < 0 -> ShakeType.BRAKE
                prev < LAUNCH_SPEED_MPS -> ShakeType.LAUNCH
                else -> ShakeType.ACCEL
            }
            val strong = mag >= STRONG / sensitivity
            penalty += (if (strong) 8.0 else 4.0) * weightOf(type)
            drops += if (strong) 2 else 1
            event = ShakeEvent(elapsedSeconds, type, mag, strong)
            events.add(event)
        } else if (over && shaking) {
            // 같은 흔들림이 이어지는 동안 더 세지면 기록을 갱신하고, 강한 흔들림이 되면 한 번 더 감점
            val last = events.last()
            if (mag > last.magnitude) {
                val nowStrong = mag >= STRONG / sensitivity
                if (nowStrong && !last.strong) {
                    penalty += 4.0 * weightOf(last.type)
                    drops += 1
                }
                events[events.size - 1] = last.copy(magnitude = mag, strong = last.strong || nowStrong)
            }
        }
        shaking = over
        level = max(0.0, 100.0 - penalty - dose * 0.05 * passenger.wDose)
        return event
    }
}
