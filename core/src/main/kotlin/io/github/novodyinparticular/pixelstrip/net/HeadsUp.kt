package io.github.novodyinparticular.pixelstrip.net

import io.github.novodyinparticular.pixelstrip.race.Distance
import io.github.novodyinparticular.pixelstrip.race.Vehicle

/**
 * A race against another player's phone: an online race through the server ([OnlineRace]) or a
 * head-to-head over the local Wi-Fi ([LanRace]). The race screen only needs this much of either.
 */
interface HeadsUp {
    val me: OnlineRace.Player
    val them: OnlineRace.Player
    val track: String
    val distance: Distance
    /** The starter's wait, shared so both trees count down alike. */
    val treeDelay: Float
    val wager: OnlineRace.Wager
    /** The other phone's latest report. */
    val live: OnlineRace.Live?
    /** Seconds since the other phone last reported. */
    val silence: Float
    val result: OnlineRace.Result?
    val error: String?
    /** True over the local Wi-Fi: no server, no XP, no stakes. */
    val local: Boolean

    /** Streams our car and pulls theirs; called every frame of the race. */
    fun update(dt: Float, car: Vehicle, clock: Float)

    /** Our run is over ([red]: we left before the green); called every frame once finished. */
    fun finish(car: Vehicle, red: Boolean, split: Int)

    /** Leaving the race before it ends. */
    fun quit() {}
}
