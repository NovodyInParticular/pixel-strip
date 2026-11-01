package io.github.novodyinparticular.pixelstrip.race

/** The race lengths: each finishes at one of [Vehicle.SPLITS_M]. Saved by ordinal, so append only. */
enum class Distance(val label: String, val short: String, val split: Int, val key: String) {
    EIGHTH("1/8 MILE", "1/8", 2, "eighth"),
    QUARTER("1/4 MILE", "1/4", 4, "quarter"),
    HALF("1/2 MILE", "1/2", 5, "half");

    val metres get() = Vehicle.SPLITS_M[split]

    /** The next one round, for the test-n-tune distance button. */
    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: EIGHTH
        fun of(ordinal: Int) = entries.getOrElse(ordinal) { EIGHTH }
    }
}
