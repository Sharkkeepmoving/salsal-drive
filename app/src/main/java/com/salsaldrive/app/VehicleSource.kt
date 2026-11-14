package com.salsaldrive.app

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs

/** 차량 데이터를 앱에 전달하는 통로. 실제 차량(에뮬레이터)과 시연 모드가 같은 형태로 값을 줍니다. */
interface VehicleSource {
    interface Listener {
        fun onSpeed(mps: Double, timeMillis: Long)
        fun onGear(drive: Boolean, park: Boolean)
    }
    fun start(listener: Listener): Boolean
    fun stop()
}

/**
 * AAOS 표준 차량 속성으로 속도와 기어를 읽습니다.
 * Pleos 개발 문서의 "AAOS Vehicle Property Availability"에서
 * PERF_VEHICLE_SPEED, GEAR_SELECTION 은 사용 가능(O)으로 안내되어 있고, 외부 앱은 읽기만 할 수 있습니다.
 */
class CarPropertySource(private val context: Context) : VehicleSource {
    companion object {
        private const val TAG = "CarPropertySource"
        // android.car.VehicleGear 값
        private const val GEAR_NEUTRAL = 0x0001
        private const val GEAR_REVERSE = 0x0002
        private const val GEAR_PARK = 0x0004
    }

    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var listener: VehicleSource.Listener? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val l = listener ?: return
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val v = value.value as? Float ?: return
                    // timestamp 는 elapsedRealtimeNanos 기준
                    l.onSpeed(abs(v).toDouble(), value.timestamp / 1_000_000)
                }
                VehiclePropertyIds.GEAR_SELECTION -> {
                    val g = value.value as? Int ?: return
                    val park = g == GEAR_PARK
                    val drive = !park && g != GEAR_NEUTRAL && g != GEAR_REVERSE
                    l.onGear(drive, park)
                }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "property error $propertyId")
        }
    }

    override fun start(listener: VehicleSource.Listener): Boolean {
        this.listener = listener
        return try {
            val c = Car.createCar(context) ?: return false
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            val speedOk = m.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
            m.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            speedOk
        } catch (e: Exception) {
            // 차량이 아닌 기기, 퍼미션 누락, 지원하지 않는 속성 등
            Log.w(TAG, "car property unavailable", e)
            false
        }
    }

    override fun stop() {
        try { manager?.unregisterCallback(callback) } catch (_: Exception) {}
        try { car?.disconnect() } catch (_: Exception) {}
        listener = null; manager = null; car = null
    }
}

/**
 * 시연 모드: 실제 차량 값을 바꿀 수 없을 때 쓰는 가상 주행.
 * 화면에 "시연용 가상 주행 데이터"라고 표시합니다.
 */
class DemoSource : VehicleSource {
    private val handler = Handler(Looper.getMainLooper())
    private var listener: VehicleSource.Listener? = null
    private var t0 = 0L
    private var manualUntil = 0L
    private var manualAccel = 0.0
    private var speedKmh = 0.0

    // (초, km/h) 부드러운 가상 주행 경로
    private val keys = listOf(0.0 to 0.0, 8.0 to 30.0, 12.0 to 42.0, 16.0 to 45.0, 20.0 to 38.0, 26.0 to 48.0, 32.0 to 40.0, 38.0 to 46.0, 44.0 to 42.0)

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val t = (now - t0) / 1000.0
            speedKmh = if (now < manualUntil) {
                (speedKmh + manualAccel * 3.6 * 0.1).coerceIn(0.0, 110.0)
            } else {
                val target = profile(if (t < 44.0) t else 8.0 + (t - 8.0) % 36.0)
                // 평소 주행은 1.2 m/s² 이내로만 속도를 바꿔 흔들림으로 판정되지 않게 함
                val step = (target - speedKmh).coerceIn(-1.2 * 3.6 * 0.1, 1.2 * 3.6 * 0.1)
                speedKmh + step
            }
            listener?.onSpeed(speedKmh / 3.6, now)
            handler.postDelayed(this, 100)
        }
    }

    private fun profile(t: Double): Double {
        for (i in 0 until keys.size - 1) {
            val (ta, va) = keys[i]; val (tb, vb) = keys[i + 1]
            if (t <= tb) {
                var u = (t - ta) / (tb - ta)
                u = u * u * (3 - 2 * u)
                return va + (vb - va) * u
            }
        }
        return keys.last().second
    }

    /** 시연용: 1.4초 동안 강하게 감속 */
    fun hardBrake() { manualAccel = -3.6; manualUntil = SystemClock.elapsedRealtime() + 1400 }

    /** 시연용: 1.4초 동안 강하게 가속 */
    fun hardAccel() { manualAccel = 2.6; manualUntil = SystemClock.elapsedRealtime() + 1400 }

    override fun start(listener: VehicleSource.Listener): Boolean {
        this.listener = listener
        t0 = SystemClock.elapsedRealtime(); speedKmh = 0.0
        listener.onGear(drive = true, park = false)
        handler.post(tick)
        return true
    }

    override fun stop() {
        handler.removeCallbacks(tick)
        listener = null
    }
}
