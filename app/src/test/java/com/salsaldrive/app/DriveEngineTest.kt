package com.salsaldrive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveEngineTest {

    /** 일정한 가감속으로 seconds 동안 달린 결과 */
    private fun run(engine: DriveEngine, startKmh: Double, accel: Double, seconds: Double, t0: Long = 0L): Long {
        var t = t0
        var v = startKmh / 3.6
        val steps = (seconds * 10).toInt()
        engine.onSpeed(v, t)
        repeat(steps) {
            t += 100
            v = (v + accel * 0.1).coerceAtLeast(0.0)
            engine.onSpeed(v, t)
        }
        return t
    }

    @Test
    fun smoothDrivingKeepsFullLevel() {
        val e = DriveEngine(Passenger.CHILD)
        run(e, 30.0, 0.5, 10.0)
        assertEquals(0, e.events.size)
        assertTrue(e.level > 95)
    }

    @Test
    fun hardBrakeIsOneStrongEvent() {
        val e = DriveEngine(Passenger.CHILD)
        val t = run(e, 45.0, 0.0, 2.0)
        run(e, 45.0, -3.6, 1.4, t)
        assertEquals(1, e.events.size)
        assertEquals(ShakeType.BRAKE, e.events[0].type)
        assertTrue(e.events[0].strong)
        assertEquals(2, e.drops)
        assertTrue(e.level <= 92.0)
    }

    @Test
    fun seniorWeighsBrakingMore() {
        val child = DriveEngine(Passenger.CHILD)
        val senior = DriveEngine(Passenger.SENIOR)
        listOf(child, senior).forEach { run(it, 45.0, -2.4, 1.5) }
        assertTrue(senior.level < child.level)
    }
}
