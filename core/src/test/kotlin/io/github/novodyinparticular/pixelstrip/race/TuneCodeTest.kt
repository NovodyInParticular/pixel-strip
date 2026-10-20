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
        val tune = Tune("MINE", floatArrayOf(2.81f, 1.95f, 1.37f, 1.02f), 3.73f, 3400f, 2, 22.5f)
        val code = TuneCode.encode("vandal", tune, 4, 3.55f)
        println("share code: $code")
        val back = assertNotNull(TuneCode.decode(code, "vandal", 4))
        assertEquals(tune.gears!!.toList(), back.gears!!.toList())
        assertEquals(3.73f, back.finalDrive)
        assertEquals(3400f, back.launchRpm)
        assertEquals(2, back.nitrousGear)
        assertEquals(22.5f, back.tirePressure)
        assertNull(TuneCode.decode(code, "kite", 4), "another car's code must be refused")
        assertNull(TuneCode.decode(code.dropLast(1) + (if (code.last() == 'A') 'B' else 'A'), "vandal", 4), "a typo must fail the checksum")
        assertNull(TuneCode.decode(code, "vandal", 6), "a different gear count must be refused")
    }
}
