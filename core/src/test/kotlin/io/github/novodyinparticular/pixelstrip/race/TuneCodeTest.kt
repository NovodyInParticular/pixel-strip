package io.github.novodyinparticular.pixelstrip.race

import io.github.novodyinparticular.pixelstrip.data.Tune
import io.github.novodyinparticular.pixelstrip.data.TuneCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TuneCodeTest {
    @Test
    fun roundTripsAndRejectsBadCodes() {
        val tune = Tune("MINE", floatArrayOf(2.81f, 1.95f, 1.37f, 1.02f), 3.73f, 3400f, 2, 22.5f,
            boostPsi = 18f, boostByGear = floatArrayOf(0.6f, 0.8f, 1f, 1f), timing = 2.5f, afr = 11.8f, nitrousShot = 150f, rideHeight = -0.6f)
        val code = TuneCode.encode("vandal", tune, 4, 3.55f)
        println("share code: $code")
        val back = assertNotNull(TuneCode.decode(code, "vandal", 4))
        assertEquals(tune.gears!!.toList(), back.gears!!.toList())
        assertEquals(3.73f, back.finalDrive)
        assertEquals(3400f, back.launchRpm)
        assertEquals(2, back.nitrousGear)
        assertEquals(22.5f, back.tirePressure)
        assertEquals(18f, back.boostPsi!!, 0.4f)
        assertEquals(0.6f, back.boostByGear!![0], 0.07f)
        assertEquals(2.5f, back.timing)
        assertEquals(11.8f, back.afr!!, 0.1f)
        assertEquals(150f, back.nitrousShot!!, 9f)
        assertEquals(-0.6f, back.rideHeight, 0.14f)
        assertNull(back.scPsi, "unset values stay unset")
        assertNull(TuneCode.decode(code, "kite", 4), "another car's code must be refused")
        assertNull(TuneCode.decode(code.dropLast(1) + (if (code.last() == 'A') 'B' else 'A'), "vandal", 4), "a typo must fail the checksum")
        assertNull(TuneCode.decode(code, "vandal", 6), "a different gear count must be refused")
    }
}
