package io.github.novodyinparticular.pixelstrip.data

/**
 * Short share codes for tunes: "VANDAL-3F9K-QW2M-..." The prefix is the car model; the rest packs
 * every value into base-32 with a checksum, so a mistyped or wrong-car code is refused.
 */
object TuneCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"   // no I, L, O, U

    private class Bits {
        val bits = ArrayList<Boolean>()
        fun put(value: Int, n: Int) {
            for (i in n - 1 downTo 0) bits.add((value shr i) and 1 == 1)
        }
    }

    fun encode(carId: String, tune: Tune, gearCount: Int, stockFinal: Float): String {
        val b = Bits()
        b.put(gearCount, 3)
        val gears = tune.gears ?: FloatArray(gearCount)
        for (i in 0 until gearCount) b.put((gears.getOrElse(i) { 0f } * 100f).toInt().coerceIn(0, 1023), 10)
        b.put(((tune.finalDrive ?: stockFinal) * 100f).toInt().coerceIn(0, 1023), 10)
        b.put((tune.launchRpm / 50f).toInt().coerceIn(0, 255), 8)
        b.put(tune.nitrousGear.coerceIn(0, 7), 3)
        b.put((tune.tirePressure * 2f).toInt().coerceIn(0, 127), 7)
        b.put(if (tune.gears == null) 1 else 0, 1)
        while (b.bits.size % 5 != 0) b.bits.add(false)
        val body = StringBuilder()
        for (i in b.bits.indices step 5) {
            var v = 0
            for (k in 0 until 5) v = (v shl 1) or (if (b.bits[i + k]) 1 else 0)
            body.append(ALPHABET[v])
        }
        val sum = checksum(carId, body.toString())
        val chunks = (body.toString() + sum).chunked(4).joinToString("-")
        return "${prefix(carId)}-$chunks"
    }

    /** The tune, or null when the code is malformed, for another car, or fails its checksum. */
    fun decode(code: String, carId: String, gearCount: Int): Tune? {
        val clean = code.trim().uppercase().replace(" ", "")
        val pre = prefix(carId) + "-"
        if (!clean.startsWith(pre)) return null
        val packed = clean.removePrefix(pre).replace("-", "")
        if (packed.length < 3) return null
        val body = packed.dropLast(2)
        if (checksum(carId, body) != packed.takeLast(2)) return null
        val bits = ArrayList<Boolean>()
        for (c in body) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            for (i in 4 downTo 0) bits.add((v shr i) and 1 == 1)
        }
        var pos = 0
        fun take(n: Int): Int {
            var v = 0
            repeat(n) { v = (v shl 1) or (if (bits.getOrElse(pos++) { false }) 1 else 0) }
            return v
        }
        if (take(3) != gearCount) return null
        val gears = FloatArray(gearCount) { take(10) / 100f }
        val final = take(10) / 100f
        val launch = take(8) * 50f
        val nos = take(3)
        val psi = take(7) / 2f
        val stockGears = take(1) == 1
        return Tune("SHARED", if (stockGears) null else gears, final, launch, nos, psi)
    }

    private fun prefix(carId: String) = carId.uppercase().replace("_", "").take(6)

    private fun checksum(carId: String, body: String): String {
        var h = 7
        for (c in carId + body) h = (h * 31 + c.code) and 0x3FF
        return "${ALPHABET[h shr 5]}${ALPHABET[h and 31]}"
    }
}
