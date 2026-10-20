package io.github.novodyinparticular.pixelstrip.save

import com.badlogic.gdx.utils.JsonValue

/**
 * One thing that changes cash or the garage. The game applies it at once and queues it; on sync
 * the server replays the queue with the same rules (server/functions/src/ledger.ts) and its
 * result wins. [t] is the client clock in ms.
 *
 * Types: start(car), buy(car), part(uid, slot), look(uid, slot = cosmetic id),
 * paint(uid, paint = [colour, finish, decal, decal colour]), sell(uid), race(uid, rival, won, pink).
 */
class Entry(
    val type: String,
    val t: Long,
    val car: String? = null,
    val uid: String? = null,
    val slot: String? = null,
    val rival: String? = null,
    val won: Boolean = false,
    val pink: Boolean = false,
    val paint: List<Int>? = null,
) {
    fun toJson(): JsonValue {
        val o = JsonValue(JsonValue.ValueType.`object`)
        o.addChild("type", JsonValue(type))
        o.addChild("t", JsonValue(t))
        car?.let { o.addChild("car", JsonValue(it)) }
        uid?.let { o.addChild("uid", JsonValue(it)) }
        slot?.let { o.addChild("slot", JsonValue(it)) }
        rival?.let { o.addChild("rival", JsonValue(it)) }
        if (type == "race") {
            o.addChild("won", JsonValue(won))
            o.addChild("pink", JsonValue(pink))
        }
        paint?.let { p -> o.addChild("paint", JsonValue(JsonValue.ValueType.array).also { a -> p.forEach { a.addChild(JsonValue(it.toLong())) } }) }
        return o
    }

    companion object {
        fun from(o: JsonValue) = Entry(
            o.getString("type"), o.getLong("t", 0),
            o.getString("car", null), o.getString("uid", null), o.getString("slot", null), o.getString("rival", null),
            o.getBoolean("won", false), o.getBoolean("pink", false), o.get("paint")?.asIntArray()?.toList(),
        )
    }
}

/** The rules every [Entry] is checked against. Mirrors server/functions/src/ledger.ts exactly. */
object Ledger {
    /** What the dealer pays for [car]: its resale price plus a share of every part tier fitted. */
    fun saleValue(r: Rules, car: OwnedCar): Long {
        val rule = r.cars[car.carId] ?: return 0
        var parts = 0L
        for ((slot, tier) in car.parts) {
            val prices = rule.parts[slot] ?: continue
            for (i in 0 until tier.coerceAtMost(prices.size)) parts += prices[i]
        }
        return rule.sell + parts * r.resalePercent / 100
    }

    fun paintCost(r: Rules, car: OwnedCar, p: List<Int>): Long {
        var c = 0L
        if (p[0] != car.paint) c += r.colorPrice
        if (p[1] != (car.cosmetics["finish"] ?: 0)) c += r.finishes[p[1]]
        if (p[2] != (car.cosmetics["decal"] ?: 0)) c += r.decals[p[2]]
        if (p[3] != (car.cosmetics["decalColor"] ?: 0) && p[2] > 0) c += r.decalColorPrice
        return c
    }

    fun unlocked(r: Rules, s: SaveGame, rivalId: String): Boolean {
        val i = r.rivalIndex(rivalId)
        return i == 0 || (i > 0 && r.rivals[i - 1].id in s.beaten)
    }

    /** Applies [e] to [s] if the rules allow it. Returns false, and changes nothing, if they don't. */
    fun apply(r: Rules, s: SaveGame, e: Entry): Boolean {
        val car = e.uid?.let { u -> s.cars.firstOrNull { it.uid == u } }
        when (e.type) {
            "start" -> {
                val c = e.car ?: return false
                if (s.cars.isNotEmpty() || c !in r.starterCars) return false
                val rule = r.cars[c] ?: return false
                s.cash = r.startingCash
                s.addCar(c, rule.paint)
            }
            "buy" -> {
                val c = e.car ?: return false
                val rule = r.cars[c] ?: return false
                if (!rule.dealer || s.owns(c) || s.cash < rule.price) return false
                s.cash -= rule.price
                s.addCar(c, rule.paint)
            }
            "part" -> {
                car ?: return false
                val slot = r.slots[e.slot] ?: return false
                val tier = (car.parts[e.slot] ?: 0) + 1
                if (tier > slot.tiers) return false
                val price = r.cars[car.carId]?.parts?.get(e.slot)?.getOrNull(tier - 1) ?: return false
                if (s.cash < price) return false
                s.cash -= price
                car.parts[e.slot!!] = tier
                slot.excludes.forEach { car.parts.remove(it) }
            }
            "look" -> {
                car ?: return false
                val cos = r.cosmetics[e.slot] ?: return false
                if (s.cash < cos.price) return false
                s.cash -= cos.price
                car.cosmetics[e.slot!!] = ((car.cosmetics[e.slot] ?: 0) + 1) % cos.styles
            }
            "paint" -> {
                car ?: return false
                val p = e.paint ?: return false
                if (p.size != 4 || p[0] !in 0 until r.colors || p[1] !in r.finishes.indices ||
                    p[2] !in r.decals.indices || p[3] !in 0 until r.decalColors) return false
                val cost = paintCost(r, car, p)
                if (cost == 0L || s.cash < cost) return false
                s.cash -= cost
                car.paint = p[0]
                car.cosmetics["finish"] = p[1]
                car.cosmetics["decal"] = p[2]
                car.cosmetics["decalColor"] = p[3]
            }
            "sell" -> {
                car ?: return false
                if (s.cars.size < 2) return false
                s.cash += saleValue(r, car)
                s.removeCar(car.uid)
            }
            "race" -> {
                car ?: return false
                val rival = r.rivals.getOrNull(r.rivalIndex(e.rival ?: return false)) ?: return false
                if (!unlocked(r, s, rival.id)) return false
                if (e.pink && rival.id !in s.beaten) return false
                if (s.lastRaceT != 0L && e.t - s.lastRaceT < r.minRaceGapMs) return false
                s.lastRaceT = e.t
                if (!e.won) {
                    if (e.pink && s.cars.size > 1) s.removeCar(car.uid)
                    return true
                }
                val first = rival.id !in s.beaten
                s.cash += if (e.pink) 0L else if (first) rival.payout else rival.payout / 2
                s.beaten.add(rival.id)
                if (rival.boss && first || e.pink) {
                    val prize = s.addCar(rival.car, rival.paint)
                    prize.parts.putAll(rival.parts)
                    prize.cosmetics.putAll(rival.cosmetics)
                }
            }
            else -> return false
        }
        return true
    }
}
