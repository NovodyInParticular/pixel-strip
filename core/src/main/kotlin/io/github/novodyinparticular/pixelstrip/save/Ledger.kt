package io.github.novodyinparticular.pixelstrip.save

import com.badlogic.gdx.utils.JsonValue

/**
 * One thing that changes cash or the garage. The game applies it at once and queues it; on sync
 * the server replays the queue with the same rules (server/functions/src/ledger.ts) and its
 * result wins. [t] is the client clock in ms.
 *
 * Types: start(car), buy(car), part(uid, slot, opt), look(uid, slot = cosmetic id, opt = style or -1 for the next),
 * paint(uid, paint = [colour, finish, decal, decal colour]), sell(uid), race(uid, rival, won, pink),
 * retire(uid, car = a starter in case it was the last car), find(car = a barn find).
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
    /** part: the option to fit (bought first if this car doesn't own it). */
    val opt: Int = -1,
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
        if (opt >= 0) o.addChild("opt", JsonValue(opt.toLong()))
        paint?.let { p -> o.addChild("paint", JsonValue(JsonValue.ValueType.array).also { a -> p.forEach { a.addChild(JsonValue(it.toLong())) } }) }
        return o
    }

    companion object {
        fun from(o: JsonValue) = Entry(
            o.getString("type"), o.getLong("t", 0),
            o.getString("car", null), o.getString("uid", null), o.getString("slot", null), o.getString("rival", null),
            o.getBoolean("won", false), o.getBoolean("pink", false), o.get("paint")?.asIntArray()?.toList(), o.getInt("opt", -1),
        )
    }
}

/** The rules every [Entry] is checked against. Mirrors server/functions/src/ledger.ts exactly. */
object Ledger {
    /** What the dealer pays for [car]: its resale price plus a share of every part bought for it. */
    fun saleValue(r: Rules, car: OwnedCar): Long {
        val rule = r.cars[car.carId] ?: return 0
        var parts = 0L
        for ((slot, bits) in car.owned) {
            val prices = rule.parts[slot] ?: continue
            for (i in prices.indices) if (bits and (1 shl (i + 1)) != 0) parts += prices[i]
        }
        return rule.sell + parts * r.resalePercent / 100
    }

    /** Every fitted option's requirements are met and it suits the drivetrain. */
    fun buildValid(r: Rules, carId: String, parts: Map<String, Int>): Boolean {
        val drivetrain = r.cars[carId]?.drivetrain ?: return false
        for ((slot, opt) in parts) {
            if (opt == 0) continue
            val s = r.slots[slot] ?: return false
            if (opt !in 1 until s.options) return false
            if (opt in s.rwdOnly && drivetrain != "RWD") return false
            s.carOnly[opt]?.let { if (carId !in it) return false }
            s.requiresAll[opt]?.let { all -> if (all.any { (ns, min) -> (parts[ns] ?: 0) < min }) return false }
            val need = s.requires[opt] ?: continue
            if (need.none { (ns, min) -> (parts[ns] ?: 0) >= min }) return false
        }
        return true
    }

    fun paintCost(r: Rules, car: OwnedCar, p: List<Int>): Long {
        var c = 0L
        if (p[0] != car.paint) c += r.colorPrice
        if (p[1] != (car.cosmetics["finish"] ?: 0)) c += r.finishes[p[1]]
        if (p[2] != (car.cosmetics["decal"] ?: 0)) c += r.decals[p[2]]
        if (p[3] != (car.cosmetics["decalColor"] ?: 0) && p[2] > 0) c += r.decalColorPrice
        return c
    }

    /**
     * What a car or part taken out of the game pays back: a retired car its full price, every part
     * bought for it and its paint and cosmetics; otherwise each retired part this car owns. 0 = nothing.
     */
    fun refund(r: Rules, car: OwnedCar): Long {
        r.retired.cars[car.carId]?.let { rc ->
            var total = rc.price
            for ((slot, bits) in car.owned) {
                val prices = rc.parts[slot] ?: continue
                for (i in prices.indices) if (bits and (1 shl (i + 1)) != 0) total += prices[i]
            }
            for ((k, v) in car.cosmetics) {
                if (v <= 0) continue
                total += when (k) {
                    "finish" -> r.finishes.getOrElse(v) { 0L }
                    "decal" -> r.decals.getOrElse(v) { 0L }
                    "decalColor" -> if ((car.cosmetics["decal"] ?: 0) > 0) r.decalColorPrice else 0L
                    else -> r.cosmetics[k]?.price ?: 0L
                }
            }
            if (car.paint != rc.paint) total += r.colorPrice
            return total
        }
        var total = 0L
        for ((slot, opts) in r.retired.parts) {
            val bits = car.owned[slot] ?: continue
            for ((opt, byCar) in opts) if (bits and (1 shl opt) != 0) total += byCar[car.carId] ?: 0L
        }
        return total
    }

    fun unlocked(r: Rules, s: SaveGame, rivalId: String): Boolean {
        val i = r.rivalIndex(rivalId)
        return i == 0 || (i > 0 && r.rivals[i - 1].id in s.beaten)
    }

    /** Adds XP; each level crossed pays its cash. */
    fun addXp(r: Rules, s: SaveGame, n: Long) {
        val before = r.xp.levelOf(s.xp)
        s.xp += n
        for (l in before + 1..r.xp.levelOf(s.xp)) s.cash += r.xp.levelCash * (l - 1)
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
                val id = e.slot ?: return false
                val slot = r.slots[id] ?: return false
                if (e.opt !in 0 until slot.options || e.opt == (car.parts[id] ?: 0)) return false
                val next = HashMap(car.parts)
                if (e.opt == 0) next.remove(id) else next[id] = e.opt
                if (e.opt > 0) slot.excludes.forEach { next.remove(it) }
                if (!buildValid(r, car.carId, next)) return false
                val price = if (car.owns(id, e.opt)) 0L else r.cars[car.carId]?.parts?.get(id)?.getOrNull(e.opt - 1) ?: return false
                if (s.cash < price) return false
                s.cash -= price
                if (e.opt > 0) car.owned[id] = (car.owned[id] ?: 0) or (1 shl e.opt)
                car.parts.clear()
                car.parts.putAll(next)
            }
            "look" -> {
                car ?: return false
                val cos = r.cosmetics[e.slot] ?: return false
                val style = if (e.opt >= 0) e.opt else ((car.cosmetics[e.slot] ?: 0) + 1) % cos.styles
                if (style >= cos.styles || style == (car.cosmetics[e.slot] ?: 0)) return false
                if (s.cash < cos.price) return false
                s.cash -= cos.price
                car.cosmetics[e.slot!!] = style
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
                    addXp(r, s, r.xp.careerLoss)
                    return true
                }
                val first = rival.id !in s.beaten
                addXp(r, s, r.xp.careerWin + if (rival.boss && first) r.xp.bossBonus else 0)
                s.cash += if (e.pink) 0L else if (first) rival.payout else rival.payout / 2
                s.beaten.add(rival.id)
                if (rival.boss && first || e.pink) {
                    val prize = s.addCar(rival.car, rival.paint)
                    prize.parts.putAll(rival.parts)
                    for ((k, v) in rival.parts) if (v > 0) prize.owned[k] = 1 shl v
                    prize.cosmetics.putAll(rival.cosmetics)
                }
            }
            "retire" -> {
                car ?: return false
                val pay = refund(r, car)
                if (pay <= 0L) return false
                if (car.carId in r.retired.cars) {
                    val starter = e.car
                    if (s.cars.size == 1 && (starter == null || starter !in r.starterCars || r.cars[starter] == null)) return false
                    s.cash += pay
                    s.removeCar(car.uid)
                    if (s.cars.isEmpty()) s.addCar(starter!!, r.cars.getValue(starter).paint)
                } else {
                    s.cash += pay
                    for ((slot, opts) in r.retired.parts) {
                        val bits = car.owned[slot] ?: continue
                        var left = bits
                        for (opt in opts.keys) left = left and (1 shl opt).inv()
                        if (left == 0) car.owned.remove(slot) else car.owned[slot] = left
                        if (car.parts[slot]?.let { it in opts } == true) car.parts.remove(slot)
                    }
                }
            }
            "find" -> {
                val barn = r.barns.firstOrNull { it.car == e.car } ?: return false
                if (barn.after !in s.beaten || barn.car in s.found || r.cars[barn.car] == null) return false
                s.found.add(barn.car)
                s.addCar(barn.car, barn.paint)
            }
            else -> return false
        }
        return true
    }
}
