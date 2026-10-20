package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.utils.JsonValue

/**
 * Everything a [Vehicle] was told, by physics step: the throttle and brake it actually saw, and
 * each command. The server replays it with its own physics (server/functions/src/physics) to get
 * the run's real reaction time and ET, so a client can't just claim a time.
 *
 * Event: s = step index (applied before that step), a = thr | brk | burnout | stage | launch |
 * shift | nos, v = value (thr, brk), c = race clock (launch).
 */
class RunRecorder {
    class Event(val s: Int, val a: String, val v: Float = 0f, val c: Float = 0f)

    val events = ArrayList<Event>()
    private var throttle = -1f
    private var brake = -1f

    fun observe(step: Int, t: Float, b: Float) {
        if (t != throttle) events.add(Event(step, "thr", t)).also { throttle = t }
        if (b != brake) events.add(Event(step, "brk", b)).also { brake = b }
    }

    fun command(step: Int, a: String, c: Float = 0f) {
        events.add(Event(step, a, c = c))
    }

    fun toJson(): JsonValue = JsonValue(JsonValue.ValueType.array).also { arr ->
        for (e in events) arr.addChild(JsonValue(JsonValue.ValueType.`object`).also { o ->
            o.addChild("s", JsonValue(e.s.toLong()))
            o.addChild("a", JsonValue(e.a))
            if (e.a == "thr" || e.a == "brk") o.addChild("v", JsonValue(e.v.toDouble()))
            if (e.a == "launch") o.addChild("c", JsonValue(e.c.toDouble()))
        })
    }
}
