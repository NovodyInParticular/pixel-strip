package io.github.novodyinparticular.pixelstrip.race

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.MathUtils
import io.github.novodyinparticular.pixelstrip.PixelStrip
import io.github.novodyinparticular.pixelstrip.audio.Sfx
import io.github.novodyinparticular.pixelstrip.data.Career
import io.github.novodyinparticular.pixelstrip.save.OwnedCar
import io.github.novodyinparticular.pixelstrip.save.RunRecord
import io.github.novodyinparticular.pixelstrip.ui.GarageScreen
import io.github.novodyinparticular.pixelstrip.data.TrackDef
import io.github.novodyinparticular.pixelstrip.gfx.Lights
import io.github.novodyinparticular.pixelstrip.world.TrackRenderer
import io.github.novodyinparticular.pixelstrip.world.TrackRenderer.Companion.PX_PER_M
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * One drag race, player in the near lane against a hologram ghost in the far lane:
 * burnout box -> creep into the beams -> tree countdown (rev to set launch RPM) -> launch on
 * green -> shift and spray -> finish (photo finish when it's close) -> timeslip.
 */
class RaceScreen(
    private val game: PixelStrip,
    trackId: String,
    private val owned: OwnedCar,
    private val test: Boolean = false,
    /** A career rival in the far lane (a real car), instead of the hologram ghost. */
    private val rival: Career.Rival? = null,
    /** Cars on the line: win theirs, lose yours (never offered with your last car). */
    private val pinkSlip: Boolean = false,
) : ScreenAdapter() {
    private enum class Phase { BURNOUT, STAGING, COUNTDOWN, RACING, PHOTO, FINISHED }

    private val art = game.art
    private val post = game.post
    private val batch = game.batch
    private val font = game.font
    private val cam = OrthographicCamera()

    private val track = TrackDef.load(trackId)
    private val trackView = TrackRenderer(track, art)
    private val carDef = game.effective(owned)
    private val ramps = art.tex("paint/ramps.png")
    private val paint = art.shader("batch.vert", "paint.frag")
    private val tint = art.shader("batch.vert", "tint.frag")
    private val white = art.tex("ui/white.png")
    private val flameTex = art.tex("fx/flame.png")
    private val lut = art.tex(track.lut)
    private val tree = Tree(art)
    private val hud = Hud(font, white)
    private val particles = Particles(art.tex("fx/smoke.png"), white, art.tex("fx/haze.png"))
    private val speedLines = SpeedLines(white)
    private val controls = Controls(post, font, white)
    private val lights = Lights()
    private val weather = io.github.novodyinparticular.pixelstrip.world.Weather(track.weather, white)
    private var lastCamX = 0f
    private val gantry = track.props.firstOrNull { it.atFinish && it.image.endsWith("finish_gantry.png") }
    private val lamps = ArrayList<Pair<Float, Float>>()

    private val gasButton = controls.add(Controls.Button(intArrayOf(Input.Keys.UP, Input.Keys.W), "GAS", Color(0.82f, 0.18f, 0.32f, 1f), big = true))
    private val actionButton = controls.add(Controls.Button(intArrayOf(Input.Keys.SPACE, Input.Keys.ENTER), "STAGE", Color(0.13f, 0.6f, 0.62f, 1f), big = true))
    private val nosButton = controls.add(Controls.Button(intArrayOf(Input.Keys.N), "NOS", Color(0.25f, 0.4f, 0.9f, 1f)))
    private val distButton = controls.add(Controls.Button(intArrayOf(Input.Keys.T), "1/8 MILE", Color(0.45f, 0.2f, 0.55f, 1f)))
    private val garageButton = controls.add(Controls.Button(intArrayOf(Input.Keys.G, Input.Keys.ESCAPE, Input.Keys.BACK), "GARAGE", Color(0.3f, 0.3f, 0.42f, 1f)))

    private lateinit var player: Vehicle
    private lateinit var ghost: Vehicle
    private lateinit var ghostDriver: AutoDriver
    private var autoPlayer: AutoDriver? = null
    private val playerView = CarRenderer(carDef, art, paint, tint, ramps).apply {
        paintRow = owned.paint
        looks = CarLooks.of(owned, game.parts)
    }
    private val rivalCar = rival?.asCar()
    private val opponentDef = rivalCar?.let { game.effective(it) } ?: carDef
    private val ghostView = CarRenderer(opponentDef, art, paint, tint, ramps).apply {
        paintRow = rivalCar?.paint ?: 2
        rivalCar?.let { looks = CarLooks.of(it, game.parts) }
        if (rival?.driver == "joe") driver = 1
    }
    private var payout = 0L
    private var wonPrize = false
    private var settled = false
    private var lostCar = false

    private fun playerWon(): Boolean {
        if (playerFoul || player.splits[finishSplit] < 0f) return false
        val mine = player.leaveTime + player.splits[finishSplit]
        val theirs = if (ghost.splits[finishSplit] >= 0f) ghost.leaveTime + ghost.splits[finishSplit] else Float.MAX_VALUE
        return mine <= theirs
    }

    /** Career result: cash for a win (half for a rematch), and the boss's car the first time. */
    private fun settleCareer() {
        val r = rival ?: return
        settled = true
        val save = game.save
        if (!playerWon()) {
            if (pinkSlip && save.cars.size > 1) {
                save.removeCar(owned.uid)
                lostCar = true
            }
            save.write()
            return
        }
        val firstWin = r.id !in save.beaten
        payout = if (pinkSlip) 0L else if (firstWin) r.payout else r.payout / 2
        save.cash += payout
        save.beaten.add(r.id)
        if (r.boss && firstWin || pinkSlip) {
            val prize = r.asCar()
            val car = save.addCar(prize.carId, prize.paint)
            car.parts.putAll(prize.parts)
            car.cosmetics.putAll(prize.cosmetics)
            wonPrize = true
        }
        save.write()
    }
    private val playerMotion = CarMotion()
    private val ghostMotion = CarMotion()

    private var quarter = rival?.let { game.career.tierOf(it).quarter } ?: false
    private val distanceM get() = if (quarter) Vehicle.SPLITS_M[4] else Vehicle.SPLITS_M[2]
    private val finishSplit get() = if (quarter) 4 else 2

    private var phase = Phase.BURNOUT
    private var phaseTime = 0f
    private var camX = 0f
    private var shake = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    private var flash = 0f
    private val flashColor = Color()
    private var zoom = 1f
    private var accumulator = 0f
    private var screenTime = 0f
    private var playerFoul = false
    private var greenSeen = false
    private var stagedBeep = false
    private var cheered = false
    private var bestBefore: RunRecord? = null
    private var smokeTimer = 0f
    private var timeScale = 1f
    private var slowMo = false
    private var print = 0f
    private val flame = FloatArray(2)       // backfire timers: player, ghost

    // Desktop screenshot autoplay
    private val shots = game.shots
    private var shotIndex = 0

    init {
        newRace()
    }

    private fun newRace() {
        player = Vehicle(carDef).apply { trackGrip = track.trackGrip }
        val close = shots?.close == true
        ghost = Vehicle(opponentDef, powerScale = if (close || rival != null) 1f else 0.98f).apply { trackGrip = track.trackGrip }
        ghostDriver = AutoDriver(ghost, skill = rival?.skill ?: if (close) 0.9f else 0.6f, trackGrip = ghost.trackGrip)
        payout = 0L
        wonPrize = false
        cheered = false
        autoPlayer = if (shots != null) AutoDriver(player, skill = 0.9f, trackGrip = player.trackGrip).apply { burnoutTime = 2.2f } else null
        player.x = BURNOUT_X
        ghost.x = BURNOUT_X
        player.startBurnout()
        ghost.startBurnout()
        tree.reset()
        particles.clear()
        playerFoul = false
        greenSeen = false
        slowMo = false
        timeScale = 1f
        trackView.finishPx = distanceM * PX_PER_M
        setPhase(Phase.BURNOUT)
        camX = player.x * PX_PER_M - post.width * CAM_LEAD
    }

    private fun setPhase(p: Phase) {
        phase = p
        phaseTime = 0f
        if (shots != null) Gdx.app.log("phase", "$p at t=%.2f".format(screenTime))
    }

    private fun buzz(ms: Int) {
        if (game.settings.vibration) game.platform.vibrate(ms)
    }

    override fun render(delta: Float) {
        val dt = min(delta, 1f / 20f)
        screenTime += dt
        phaseTime += dt
        layoutControls()
        autoplay(dt)
        controls.update(dt)
        update(dt)
        draw(dt)
        captureShots()
    }

    private fun layoutControls() {
        val w = post.width.toFloat()
        val h = post.height.toFloat()
        gasButton.apply { x = w - 96f; y = 10f; this.w = 86f; this.h = 58f }
        actionButton.apply { x = 10f; y = 10f; this.w = 84f; this.h = 46f }
        nosButton.apply { x = 10f; y = 62f; this.w = 64f; this.h = 24f }
        distButton.apply { x = w / 2f - 42f; y = h - 46f; this.w = 84f; this.h = 18f }
        actionButton.label = when (phase) {
            Phase.BURNOUT -> "STAGE"
            Phase.STAGING, Phase.COUNTDOWN -> "LAUNCH"
            Phase.RACING, Phase.PHOTO -> "SHIFT"
            Phase.FINISHED -> if (rival == null) "AGAIN" else if (wonPrize) "CLAIM" else "MAP"
        }
        actionButton.visible = phase != Phase.STAGING && phase != Phase.PHOTO
        nosButton.visible = phase == Phase.RACING && player.def.nitrous.charges > 0 && player.hasLeft
        nosButton.label = "NOS x${player.nitrousCharges}"
        distButton.visible = phase == Phase.BURNOUT && rival == null
        distButton.label = if (quarter) "1/4 MILE" else "1/8 MILE"
        gasButton.visible = phase != Phase.FINISHED && phase != Phase.PHOTO
        garageButton.apply { x = w - 78f; y = 10f; this.w = 68f; this.h = 22f; visible = phase == Phase.FINISHED || phase == Phase.BURNOUT && !gasButton.down }
        if (phase == Phase.BURNOUT) garageButton.apply { y = h - 72f }
    }

    /** Desktop screenshots: an AutoDriver presses the player's buttons. */
    private fun autoplay(dt: Float) {
        val d = autoPlayer ?: return
        val throttle = d.update(dt, tree.staged[1], tree.raceClock, tree.running)
        gasButton.forced = throttle > 0.4f
        actionButton.forced = d.wantsStage || (d.wantsLaunch && tree.green) || d.wantsShift
        nosButton.forced = d.wantsNitrous
    }

    private fun update(realDt: Float) {
        val dt = realDt * timeScale
        tree.update(dt)
        if (garageButton.justPressed && !game.transitioning && shots == null) {
            if (rival != null) game.go(0) { io.github.novodyinparticular.pixelstrip.ui.CareerScreen(game) } else game.go(2) { GarageScreen(game) }
            return
        }
        if (distButton.justPressed) {
            quarter = !quarter
            trackView.finishPx = distanceM * PX_PER_M
        }

        // Player inputs per phase
        player.throttle = if (gasButton.down) 1f else 0f
        when (phase) {
            Phase.BURNOUT -> if (actionButton.justPressed) {
                player.creepTo(Tree.STAGE_M)
                setPhase(Phase.STAGING)
            }
            Phase.STAGING, Phase.COUNTDOWN -> if (actionButton.justPressed && player.mode == Vehicle.Mode.HELD && tree.staged[1]) {
                player.launch(if (tree.running) tree.raceClock else -1f)
                zoom += 0.06f                    // punch in on the launch
                setPhase(Phase.RACING)
            }
            Phase.RACING -> {
                if (actionButton.justPressed) player.shift()
                if (nosButton.justPressed && player.nitrous()) buzz(60)
            }
            Phase.PHOTO -> if (phaseTime > PHOTO_HOLD_S) setPhase(Phase.FINISHED)
            Phase.FINISHED -> if (phaseTime > 1.5f && actionButton.justPressed) {
                val r = rival
                if (r == null) newRace() else if (!game.transitioning) {
                    if (wonPrize) game.go(2) { io.github.novodyinparticular.pixelstrip.ui.PrizeScreen(game, r) }
                    else game.go(0) { io.github.novodyinparticular.pixelstrip.ui.CareerScreen(game) }
                }
                return
            }
        }
        if (player.hasLeft && player.x > distanceM + 0.5f) player.throttle = 0f
        if (player.mode == Vehicle.Mode.CREEP) player.throttle = 0f

        // Ghost (sits out test-n-tune: its lane just reads as staged)
        if (test) ghost.x = Tree.STAGE_M
        if (!test) ghost.throttle = ghostDriver.update(dt, tree.staged[0], tree.raceClock, tree.running)
        if (test && ghost.mode != Vehicle.Mode.HELD) ghost.hold()
        if (!test && ghostDriver.wantsStage && ghost.mode == Vehicle.Mode.BURNOUT) ghost.creepTo(Tree.STAGE_M)
        if (ghostDriver.wantsLaunch && ghost.mode == Vehicle.Mode.HELD && tree.green) ghost.launch(tree.raceClock)
        if (ghostDriver.wantsShift) ghost.shift()
        if (ghostDriver.wantsNitrous) ghost.nitrous()
        if (ghost.hasLeft && ghost.x > distanceM + 0.5f) ghost.throttle = 0f

        // Physics (frozen during the photo-finish print)
        if (phase != Phase.PHOTO) {
            accumulator += dt
            while (accumulator >= STEP) {
                accumulator -= STEP
                player.step(STEP)
                if (!test) ghost.step(STEP)
            }
        }
        for (car in listOf(player, ghost)) car.brake = if (car.hasLeft && car.x > distanceM + 6f) 1f else 0f
        tree.beams(1, player.x)
        tree.beams(0, ghost.x)
        if (!playerFoul && player.hasLeft && player.leaveTime < 0f) {
            // Cleared the stage beam before the green: red light
            playerFoul = true
            tree.foul(1)
            hud.pop("RED LIGHT!", Hud.RED)
            buzz(120)
        }
        if (tree.staged[0] && tree.staged[1] && player.mode == Vehicle.Mode.HELD && ghost.mode == Vehicle.Mode.HELD && !tree.running) {
            tree.start()
            setPhase(Phase.COUNTDOWN)
        }
        if (tree.green && !greenSeen) {
            greenSeen = true
            buzz(25)
        }

        if (phase == Phase.FINISHED && rival != null && !settled) settleCareer()
        handleEvents(player, playerMotion, 0, true)
        handleEvents(ghost, ghostMotion, 1, false)
        updateFinish()

        // Sound: both engines from the live physics, the crowd with the action, music under it all
        val audio = game.audio
        audio.engine(0, player, owned.parts["exhaust"] ?: 0, 0.9f)
        if (!test) audio.engine(1, ghost, rivalCar?.parts?.get("exhaust") ?: 0, 0.5f) else audio.synth.engines[1].active = false
        audio.synth.crowd = if (phase == Phase.RACING) 0.25f + 0.6f * min(1f, player.v / 40f) else 0.15f
        audio.music(if (rival?.boss == true) "boss" else track.song, if (phase == Phase.RACING) 0.12f else 0.3f)
        audio.synth.rain = if (track.weather == "rain") 0.5f else 0f
        weather.update(dt, camX - lastCamX, post.width)
        lastCamX = camX
        if (weather.struck) audio.play(Sfx.Kind.THUNDER)
        if (tree.staged[1] && !stagedBeep) {
            stagedBeep = true
            audio.play(Sfx.Kind.BEEP)
        }
        if (!tree.staged[1]) stagedBeep = false

        // Body motion, wheels, flames, smoke, haze, purge
        if (phase != Phase.PHOTO) {
            playerMotion.update(dt, player, phase == Phase.STAGING || phase == Phase.COUNTDOWN)
            ghostMotion.update(dt, ghost, false)
            playerView.update(dt, player.rollW, player.driveW)
            ghostView.update(dt, ghost.rollW, ghost.driveW)
            for (i in 0..1) flame[i] = max(0f, flame[i] - dt)
            if (phase == Phase.COUNTDOWN && player.limiter && MathUtils.random() < 0.04f) flame[0] = 0.06f   // pops on the limiter
            smokeTimer += dt
            if (smokeTimer > 1f / 60f) {
                smokeTimer = 0f
                emitCarEffects(player, playerView, TrackRenderer.SCREEN_H - track.nearLaneTop)
                emitCarEffects(ghost, ghostView, TrackRenderer.SCREEN_H - track.farLaneTop)
            }
            particles.update(dt)
            trackView.update(dt, if (phase == Phase.RACING) min(1f, player.v / 40f) else if (player.spinning) 0.6f else 0.15f)
        }
        speedLines.update(dt, if (phase == Phase.RACING) player.v else 0f, post.width)

        updateCamera(realDt)
    }

    /** Close at the line: slow motion into a frozen photo-finish print. */
    private fun updateFinish() {
        if (phase != Phase.RACING) return
        val playerDone = player.splits[finishSplit] >= 0f
        val ghostDone = ghost.splits[finishSplit] >= 0f
        val lead = max(player.x, ghost.x)
        if (!test && !slowMo && !playerFoul && player.hasLeft && ghost.hasLeft && distanceM - lead in 0f..7f && abs(player.x - ghost.x) < PHOTO_GAP_M) {
            slowMo = true
        }
        timeScale += ((if (slowMo) 0.22f else 1f) - timeScale) * 0.25f
        if (playerDone && !cheered) {
            cheered = true
            game.audio.play(Sfx.Kind.CHEER, 0.8f)
        }
        if (playerDone && ghostDone && slowMo) {
            game.audio.play(Sfx.Kind.SLAM)
            setPhase(Phase.PHOTO)
            timeScale = 1f
            popFlash(Color.WHITE, 0.9f)                      // the camera fires
            buzz(30)
        } else if (playerDone && (ghostDone || test || player.x > distanceM + 40f)) {
            timeScale = 1f
            setPhase(Phase.FINISHED)
            if (test && !playerFoul) {
                bestBefore = owned.bestRun(owned.tuneName, quarter)
                owned.logRun(RunRecord(owned.tuneName, quarter, player.splits[finishSplit], player.splits[0], player.splitSpeed[finishSplit] * 2.23694f, player.leaveTime))
                game.save.write()
            }
        }
    }

    private fun updateCamera(dt: Float) {
        val speedK = (player.v / 42f).coerceIn(0f, 1f)
        val lead = when (phase) {
            Phase.BURNOUT, Phase.STAGING -> CAM_LEAD + 0.08f
            else -> CAM_LEAD - 0.06f * speedK          // pull back as speed builds
        }
        val target = player.x * PX_PER_M - post.width * lead
        camX += (target - camX) * min(1f, dt * (if (phase == Phase.RACING) 30f else 4f))
        val zoomTarget = when (phase) {
            Phase.BURNOUT -> 1.05f
            Phase.STAGING -> 1.09f
            Phase.COUNTDOWN -> 1.13f
            Phase.RACING -> 1f + 0.1f * (1f - speedK)
            Phase.PHOTO -> 1.12f
            Phase.FINISHED -> 1f
        }
        zoom += (zoomTarget - zoom) * min(1f, dt * 2.5f)
        print = if (phase == Phase.PHOTO) min(1f, print + dt * 4f) else max(0f, print - dt * 1.5f)
        shake = max(0f, shake - dt * 8f)
        flash = max(0f, flash - dt * 2.2f)
        if (player.spinning && player.slipSpeed > 6f) shake = max(shake, 0.6f)
        if (player.limiter && phase == Phase.COUNTDOWN) shake = max(shake, 0.4f)
        if (phase == Phase.PHOTO) shake = 0f
    }

    private fun handleEvents(car: Vehicle, motion: CarMotion, lane: Int, isPlayer: Boolean) {
        for (e in car.events) {
            when (e) {
                is Vehicle.Event.Launched -> {
                    motion.kick(10f)
                    if (!isPlayer) continue
                    shake = 3f
                    buzz(45)
                    val (text, color) = when (e.rating) {
                        Vehicle.Rating.PERFECT -> "PERFECT LAUNCH" to Hud.PERFECT_C
                        Vehicle.Rating.GOOD -> "GOOD LAUNCH" to Hud.GOOD_C
                        Vehicle.Rating.BOG -> "BOGGED!" to Hud.YELLOW
                        else -> "WHEELSPIN!" to ORANGE
                    }
                    if (!playerFoul) hud.pop(text, color)
                    if (e.rating == Vehicle.Rating.PERFECT) popFlash(Hud.PERFECT_C, 0.35f)
                }
                is Vehicle.Event.Shifted -> {
                    motion.kick()
                    flame[lane] = 0.14f                       // backfire through the pipes
                    game.audio.play(Sfx.Kind.CLUNK, if (isPlayer) 0.8f else 0.35f)
                    game.audio.play(Sfx.Kind.POP, if (isPlayer) 0.6f else 0.3f)
                    if (!isPlayer) continue
                    shake = max(shake, 1.2f)
                    buzz(20)
                    val (text, color) = when (e.rating) {
                        Vehicle.Rating.PERFECT -> "PERFECT SHIFT" to Hud.PERFECT_C
                        Vehicle.Rating.GOOD -> "GOOD SHIFT" to Hud.GOOD_C
                        Vehicle.Rating.EARLY -> "EARLY SHIFT" to Hud.YELLOW
                        else -> "LATE SHIFT" to ORANGE
                    }
                    hud.pop(text, color)
                    if (e.rating == Vehicle.Rating.PERFECT) popFlash(Hud.PERFECT_C, 0.25f)
                }
                Vehicle.Event.NitrousOn -> if (isPlayer) {
                    hud.pop("NITROUS!", NOS_BLUE)
                    popFlash(NOS_BLUE, 0.3f)
                    shake = max(shake, 2f)
                }
                Vehicle.Event.NitrousOff -> {}
            }
        }
        car.events.clear()
    }

    private fun popFlash(c: Color, amount: Float) {
        flash = amount
        flashColor.set(c)
    }

    private fun flameOn(car: Vehicle, lane: Int) = flame[lane] > 0f || (car.nitrousActive && car.throttle > 0f)

    /** Smoke and rubber from spinning tyres, heat haze off the pipes, nitrous purges while staged. */
    private fun emitCarEffects(car: Vehicle, view: CarRenderer, ground: Float) {
        val s = carDef.sprite
        val canvasX = car.x * PX_PER_M - s.frontX
        if (car.slipSpeed >= 1.5f) {
            val wheelX = canvasX + s.rearWheel.first
            val heat = (car.slipSpeed / 20f).coerceIn(0.2f, 1f)
            val n = if (car.slipSpeed > 12f) 3 else 1
            val linger = if (car.mode == Vehicle.Mode.BURNOUT) 1.6f else 1f
            repeat(n) { particles.smoke(wheelX - 4f, ground + 2f, heat, SMOKE_LIT, SMOKE_AMBIENT, linger) }
            if (MathUtils.random() < 0.35f) particles.rubber(wheelX, ground)
            if (car.mode == Vehicle.Mode.BURNOUT) particles.haze(wheelX, ground + 6f, 0.8f)
        }
        val (ex, ey) = view.exhaustAt(canvasX, ground)
        if (car.hasLeft && car.throttle > 0f || car.mode == Vehicle.Mode.BURNOUT && car.throttle > 0f) {
            if (MathUtils.random() < 0.5f) particles.haze(ex - 6f, ey, 0.9f)
        }
        if (car.wheelie > 0.3f && MathUtils.random() < 0.6f) particles.spark(canvasX, ground + 1f)    // wheelie bars scraping
        val purgeWindow = phase == Phase.COUNTDOWN && car.def.nitrous.charges > 0 &&
            (phaseTime in 0.25f..0.55f || phaseTime in 0.95f..1.15f)
        if (purgeWindow) repeat(2) { particles.purge(canvasX + s.frontX - 14f, ground + 22f) }
    }

    private fun carCanvasX(v: Vehicle) = v.x * PX_PER_M - carDef.sprite.frontX

    private fun collectLights(camPx: Float, w: Int, nearGround: Float, farGround: Float, treeX: Float, treeY: Float) {
        lights.clear()
        val playerX = carCanvasX(player) - camPx
        // Flames first: they matter most when they're on
        if (flameOn(player, 0)) {
            val (ex, ey) = playerView.exhaustAt(playerX, nearGround)
            lights.add(ex - 6f, ey, 14f, 1f, 0.5f, 0.18f, 80f)
        }
        // Streetlamps near the car
        lamps.clear()
        trackView.lampPositions(camPx, w, lamps)
        for ((lx, ly) in lamps) if (lx > -160f && lx < w + 160f) lights.add(lx, ly, 70f, 1f, 0.8f, 0.52f, 230f)
        // The tree's lit bulbs throw colour on the cars beside it
        if (tree.running && tree.clock > 0.6f) {
            val c = if (tree.green) Hud.GOOD_C else Hud.AMBER
            lights.add(treeX + tree.width / 2f, treeY + 50f, 26f, c.r * 0.8f, c.g * 0.8f, c.b * 0.8f, 110f)
        }
        // Ghost headlamps (cyan) and the city's purple glow from above
        val (gx, gy) = ghostView.headlampAt(carCanvasX(ghost) - camPx, farGround)
        lights.add(gx + 10f, gy, 18f, 0.2f, 0.45f, 0.6f, 90f)
        lights.add(playerX + 60f, post.height + 30f, 160f, 0.3f, 0.12f, 0.36f, 420f)
    }

    private fun drawFlame(car: Vehicle, view: CarRenderer, x: Float, ground: Float, lane: Int) {
        if (!flameOn(car, lane)) return
        val (ex, ey) = view.exhaustAt(x, ground)
        val f = ((trackView.time * 25f).toInt() + lane) % 3
        val nitro = car.nitrousActive
        batch.setColor(if (nitro) 0.75f else 1f, if (nitro) 0.85f else 1f, 1f, 1f)
        batch.draw(flameTex, ex - 18f, ey - 4f, f * 18, 0, 18, 9)
        batch.color = Color.WHITE
    }

    private fun draw(dt: Float) {
        val w = post.width
        val h = post.height
        cam.setToOrtho(false, w.toFloat(), h.toFloat())
        shakeX = if (shake > 0f) floor(MathUtils.random(-shake, shake)) else 0f
        shakeY = if (shake > 0f) floor(MathUtils.random(-shake, shake) * 0.5f) else 0f
        cam.position.add(shakeX, shakeY, 0f)
        cam.update()
        batch.projectionMatrix = cam.combined
        val camPx = floor(camX)
        val farGround = TrackRenderer.SCREEN_H - track.farLaneTop
        val nearGround = TrackRenderer.SCREEN_H - track.nearLaneTop
        val t = trackView.time
        val treeX = TREE_DX - camPx
        val treeY = TrackRenderer.SCREEN_H - TREE_BASE_TOP
        val playerX = carCanvasX(player) - camPx
        val ghostX = carCanvasX(ghost) - camPx
        collectLights(camPx, w, nearGround, farGround, treeX, treeY)
        post.clearUiAndDistort()

        // World
        post.scene.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.begin()
        trackView.drawSky(batch, w)
        trackView.drawWorld(batch, camPx, w)
        trackView.drawLights(batch, camPx, w)
        if (track.wet) drawReflections(playerX, ghostX, nearGround, farGround, t)
        particles.draw(batch, camPx, front = false)
        drawFlame(ghost, ghostView, ghostX, farGround, 1)
        if (rival != null) {
            ghostView.draw(batch, ghostX, farGround, t, pitch = ghostMotion.pitch, ambient = track.ambient, lights = lights, shakeX = shakeX, shakeY = shakeY)
        } else if (!test) ghostView.draw(batch, ghostX, farGround, t, ghost = 0.62f, pitch = ghostMotion.pitch)
        tree.draw(batch, treeX, treeY)
        drawFlame(player, playerView, playerX, nearGround, 0)
        playerView.draw(batch, playerX, nearGround, t, pitch = playerMotion.pitch, lift = playerMotion.lift,
            ambient = track.ambient, lights = lights, shakeX = shakeX, shakeY = shakeY)
        particles.draw(batch, camPx, front = true)
        if (track.headlights) {
            playerView.drawBeam(batch, playerX, nearGround, BEAM, 0.32f)
            ghostView.drawBeam(batch, ghostX, farGround, GHOST_BEAM, 0.12f)
        }
        drawEtBoard(camPx, false)
        trackView.drawForeground(batch, camPx, w)
        weather.draw(batch)
        speedLines.draw(batch, player.v)
        batch.end()
        post.scene.end()

        // Emissive
        post.glow.begin()
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        batch.begin()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        trackView.drawGlow(batch, camPx, w)
        playerView.drawGlow(batch, playerX, nearGround, playerMotion.pitch, playerMotion.lift)
        if (rival != null) ghostView.drawGlow(batch, ghostX, farGround, ghostMotion.pitch)
        if (track.headlights) playerView.drawBeam(batch, playerX, nearGround, BEAM, 0.12f)
        tree.draw(batch, treeX, treeY, bulbsOnly = true)
        particles.drawGlow(batch, camPx)
        drawEtBoard(camPx, true)
        weather.drawGlow(batch)
        drawFlame(player, playerView, playerX, nearGround, 0)
        drawFlame(ghost, ghostView, ghostX, farGround, 1)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.end()
        post.glow.end()

        // Heat haze offsets
        post.distort.begin()
        batch.begin()
        particles.drawHaze(batch, camPx)
        batch.end()
        post.distort.end()

        // HUD (premultiplied alpha, composited after zoom and grade)
        cam.position.sub(shakeX, shakeY, 0f)
        cam.update()
        batch.projectionMatrix = cam.combined
        post.ui.begin()
        batch.begin()
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA)
        drawHud(w, h, dt)
        batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_ONE)
        if (phase != Phase.FINISHED && phase != Phase.PHOTO) hud.drawTachGlow(batch, TACH_X, TACH_Y, player.rpm, carDef.redline, tachZone())
        controls.drawGlow(batch)
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.end()
        post.ui.end()

        post.bloomGain = track.bloomGain
        post.lutMix = track.lutMix
        post.crt = game.settings.crt
        post.flash = max(flash * 0.45f, weather.lightning * 0.55f)
        post.flashColor.set(if (weather.lightning > flash) Color(0.85f, 0.9f, 1f, 1f) else flashColor)
        post.chromatic = if (player.nitrousActive) 0.9f else 0f
        post.radialBlur = if (player.nitrousActive) 0.22f else 0f
        post.zoom = zoom
        val focusPx = if (phase == Phase.PHOTO) trackView.finishPx - camPx else playerX + carDef.sprite.width / 2f
        post.focusX = (focusPx / w).coerceIn(0.2f, 0.8f)
        post.focusY = ((nearGround + 16f) / h).coerceIn(0.1f, 0.9f)
        post.hazeY0 = (TrackRenderer.SCREEN_H - track.roadBottom) / h
        post.hazeY1 = (TrackRenderer.SCREEN_H - track.roadTop) / h
        post.hazeAmp = track.asphaltHaze
        post.time = t
        post.print = print
        post.present(lut)
    }

    /** The gantry's ET board shows the player's time once they're through the traps. */
    private fun drawEtBoard(camPx: Float, glow: Boolean) {
        val et = player.splits[finishSplit]
        if (et < 0f) return
        val g = gantry ?: return
        val text = if (et < 10f) "%.2f".format(et) else "%.1f".format(et)
        val boardX = floor((trackView.finishPx + g.dx - camPx) * g.parallax) + ET_BOARD_X
        val boardY = TrackRenderer.SCREEN_H - g.top - ET_BOARD_Y
        font.draw(batch, text, boardX, boardY, if (glow) Hud.RED else ET_RED, 1, if (glow) null else Color(0.1f, 0.02f, 0.04f, 1f))
    }

    /** Wet asphalt: each car mirrored under itself, darker and cut into rippling strips. */
    private fun drawReflections(playerX: Float, ghostX: Float, nearGround: Float, farGround: Float, t: Float) {
        batch.flush()
        // the hologram ghost (and the empty test lane) cast no reflection
        for ((view, x, ground, skip) in listOf(
            Quad(ghostView, ghostX, farGround, rival == null || test), Quad(playerView, playerX, nearGround, false))) {
            if (skip) continue
            val m = batch.transformMatrix.cpy()
            batch.transformMatrix = m.cpy().translate(0f, ground * 2f - 2f, 0f).scale(1f, -1f, 1f)
            batch.setColor(0.45f, 0.5f, 0.65f, 0.38f)
            view.draw(batch, x + MathUtils.sin(t * 6f) * 0.6f, ground, t, lit = true, ambient = Color(0.45f, 0.5f, 0.65f, 1f))
            batch.transformMatrix = m
            batch.color = Color.WHITE
        }
    }

    private data class Quad(val view: CarRenderer, val x: Float, val ground: Float, val skip: Boolean)

    private fun tachZone(): Hud.Zone? = when {
        phase == Phase.STAGING || phase == Phase.COUNTDOWN -> Hud.Zone(DragMath.launchRpm(carDef, player.trackGrip), 0.08f, 0.18f)
        phase == Phase.RACING && player.gear < carDef.gears.size - 1 -> Hud.Zone(player.shiftPoints[player.gear], 0.035f, 0.09f)
        else -> null
    }

    // --- HUD -----------------------------------------------------------------------------

    private fun drawHud(w: Int, h: Int, dt: Float) {
        if (phase == Phase.PHOTO) {
            if ((phaseTime * 3f).toInt() % 2 == 0 || phaseTime > 0.6f) {
                font.drawCentered(batch, "PHOTO FINISH", w / 2f, h - 64f, Hud.RED, 3)
            }
            return
        }
        font.draw(batch, track.name.uppercase(), 8f, h - 14f, CYAN)
        font.draw(batch, if (quarter) "1/4 MILE" else "1/8 MILE", 8f, h - 24f, PINK)
        val speedText = if (game.settings.metric) "${(player.v * 3.6f).toInt()} KM/H" else "${(player.v * 2.23694f).toInt()} MPH"
        font.draw(batch, speedText, w - 8f - font.width(speedText, 2), h - 20f, Color.WHITE, 2)
        val clock = if (player.hasLeft) {
            val finish = player.splits[finishSplit]
            if (finish >= 0f) finish else max(0f, tree.raceClock - player.leaveTime)
        } else 0f
        val timeText = "%.3f".format(clock)
        font.draw(batch, timeText, w - 8f - font.width(timeText), h - 32f, Hud.YELLOW)
        drawDistanceBar(w, h)

        if (phase != Phase.FINISHED) {
            val gearText = if (player.mode == Vehicle.Mode.SHIFT) "-" else (player.gear + 1).toString()
            hud.drawTach(batch, TACH_X, TACH_Y, player.rpm, carDef.redline, tachZone(), gearText, "${player.rpm.toInt()} RPM", player.limiter, trackView.time)
        }
        val blink = (screenTime * 2.5f).toInt() % 2 == 0
        if (phase == Phase.BURNOUT || phase == Phase.STAGING || phase == Phase.COUNTDOWN) {
            batch.setColor(0.03f, 0.02f, 0.08f, 0.6f)
            batch.draw(white, 0f, h - (if (phase == Phase.BURNOUT) 112f else 92f), w.toFloat(), if (phase == Phase.BURNOUT) 56f else 36f)
            batch.color = Color.WHITE
        }
        when (phase) {
            Phase.BURNOUT -> {
                font.drawCentered(batch, if (player.spinning) "SMOKE 'EM!" else "HOLD GAS: BURNOUT", w / 2f, h - 70f, if (player.spinning) ORANGE else Color.WHITE, 2)
                drawTireTemp(w, h)
                if (blink) font.drawCentered(batch, "TAP STAGE WHEN READY", w / 2f, h - 86f, CYAN)
            }
            Phase.STAGING -> font.drawCentered(batch, "STAGING...", w / 2f, h - 70f, Color.WHITE, 2)
            Phase.COUNTDOWN -> {
                font.drawCentered(batch, "REV INTO THE ZONE", w / 2f, h - 70f, Color.WHITE, 2)
                if (blink) font.drawCentered(batch, "LAUNCH ON GREEN", w / 2f, h - 86f, Hud.GOOD_C)
            }
            Phase.RACING -> if (player.nitrousCharges > 0 && player.gear == 0 && player.hasLeft) {
                font.drawCentered(batch, "SPRAY ONCE IT HOOKS", w / 2f, h - 54f, NOS_BLUE)
            }
            Phase.FINISHED -> drawSlip(w, h)
            Phase.PHOTO -> {}
        }
        hud.drawPops(batch, w, dt)
        controls.draw(batch)
    }

    private fun drawTireTemp(w: Int, h: Int) {
        val x = w / 2f - 40f
        val y = h - 104f
        font.draw(batch, "TIRES", x - 36f, y - 1f, Hud.YELLOW)
        batch.setColor(0.1f, 0.08f, 0.2f, 0.85f)
        batch.draw(white, x, y, 80f, 5f)
        val t = (player.tireTemp / 1.1f).coerceIn(0f, 1f)
        batch.color = if (player.tireTemp in 0.6f..1.0f) Hud.GOOD_C else if (player.tireTemp > 1.0f) Hud.RED else ORANGE
        batch.draw(white, x, y, floor(80f * t), 5f)
        batch.color = Color.WHITE
        batch.draw(white, x + floor(80f * 0.78f / 1.1f), y - 1f, 1f, 7f)     // best temperature mark
    }

    private fun drawDistanceBar(w: Int, h: Int) {
        val x0 = w / 2f - 80f
        val y = h - 12f
        batch.setColor(0.1f, 0.08f, 0.2f, 0.85f)
        batch.draw(white, x0, y, 160f, 3f)
        val pp = (player.x / distanceM).coerceIn(0f, 1f)
        val gp = (ghost.x / distanceM).coerceIn(0f, 1f)
        batch.color = GHOST_MARK
        batch.draw(white, floor(x0 + gp * 158f), y - 1f, 2f, 5f)
        batch.color = PINK
        batch.draw(white, floor(x0 + pp * 158f), y - 1f, 2f, 5f)
        batch.color = Color.WHITE
    }

    private fun fmt(v: Float) = if (v < 0f) "-" else "%.3f".format(v)
    private fun speedOf(v: Float) = if (game.settings.metric) "%.1f".format(v * 3.6f) else "%.2f".format(v * 2.23694f)

    private fun drawSlip(w: Int, h: Int) {
        if (test) return drawTestSlip(w, h)
        val unit = if (game.settings.metric) "KMH" else "MPH"
        val lines = ArrayList<Hud.SlipLine>()
        lines.add(Hud.SlipLine("R/T", if (playerFoul) "%.3f".format(player.leaveTime) else fmt(player.leaveTime), fmt(ghost.leaveTime)))
        lines.add(Hud.SlipLine("60'", fmt(player.splits[0]), fmt(ghost.splits[0])))
        lines.add(Hud.SlipLine("330'", fmt(player.splits[1]), fmt(ghost.splits[1])))
        lines.add(Hud.SlipLine("1/8 ET", fmt(player.splits[2]), fmt(ghost.splits[2])))
        lines.add(Hud.SlipLine("1/8 $unit", speedOf(player.splitSpeed[2]), speedOf(ghost.splitSpeed[2])))
        if (quarter) {
            lines.add(Hud.SlipLine("1000'", fmt(player.splits[3]), fmt(ghost.splits[3])))
            lines.add(Hud.SlipLine("1/4 ET", fmt(player.splits[4]), fmt(ghost.splits[4])))
            lines.add(Hud.SlipLine("1/4 $unit", speedOf(player.splitSpeed[4]), speedOf(ghost.splitSpeed[4])))
        }
        val notes = ArrayList<String>()
        player.ratings.firstOrNull()?.let { notes.add("LAUNCH  ${it.name}") }
        player.ratings.drop(1).forEachIndexed { i, r -> notes.add("SHIFT ${i + 1}-${i + 2} ${r.name}") }
        val playerFinish = player.leaveTime + player.splits[finishSplit]
        val ghostFinish = if (ghost.splits[finishSplit] >= 0f) ghost.leaveTime + ghost.splits[finishSplit] else Float.MAX_VALUE
        val (stamp, color) = when {
            playerFoul -> "RED LIGHT" to Hud.RED
            playerFinish <= ghostFinish -> "WIN" to Color(0.2f, 0.55f, 0.3f, 1f)
            else -> "LOSS" to Hud.RED
        }
        if (slowMo && !playerFoul) notes.add("MARGIN %.4f S".format(abs(playerFinish - ghostFinish)))
        if (payout > 0) notes.add("PURSE  $" + "%,d".format(payout))
        if (wonPrize) notes.add("PINK SLIP: ${game.car(rival!!.carId).name.uppercase()}")
        if (lostCar) notes.add("LOST: ${carDef.name.uppercase()}")
        hud.drawTimeslip(batch, w, h, "${track.name.uppercase()} DRAGWAY", lines, notes, stamp, color, phaseTime / 1.2f,
            them = rival?.name?.take(6) ?: "GHOST")
    }

    /** Test-n-tune slip: this run against the best earlier run on the same tune. */
    private fun drawTestSlip(w: Int, h: Int) {
        val unit = if (game.settings.metric) "KMH" else "MPH"
        val best = bestBefore
        fun bestOr(v: Float?) = v?.let { fmt(it) } ?: "-"
        val lines = arrayListOf(
            Hud.SlipLine("R/T", if (playerFoul) "%.3f".format(player.leaveTime) else fmt(player.leaveTime), bestOr(best?.rt)),
            Hud.SlipLine("60'", fmt(player.splits[0]), bestOr(best?.sixty)),
            Hud.SlipLine(if (quarter) "1/4 ET" else "1/8 ET", fmt(player.splits[finishSplit]), bestOr(best?.et)),
            Hud.SlipLine(unit, speedOf(player.splitSpeed[finishSplit]), best?.let { if (game.settings.metric) "%.1f".format(it.trapMph * 1.60934f) else "%.2f".format(it.trapMph) } ?: "-"),
        )
        val notes = arrayListOf("TUNE  ${owned.tuneName}")
        player.ratings.firstOrNull()?.let { notes.add("LAUNCH  ${it.name}") }
        player.ratings.drop(1).forEachIndexed { i, r -> notes.add("SHIFT ${i + 1}-${i + 2} ${r.name}") }
        val newBest = !playerFoul && (best == null || player.splits[finishSplit] < best.et)
        val (stamp, color) = when {
            playerFoul -> "RED LIGHT" to Hud.RED
            newBest -> "NEW BEST" to Color(0.2f, 0.55f, 0.3f, 1f)
            else -> "TEST" to Hud.INK_FAINT
        }
        hud.drawTimeslip(batch, w, h, "TEST N TUNE", lines, notes, stamp, color, phaseTime / 1.2f, them = "BEST")
    }

    // --- Desktop screenshots ---------------------------------------------------------------

    private val phaseShots = listOf(Phase.RACING to -1f, Phase.PHOTO to 0.08f, Phase.PHOTO to 0.9f, Phase.FINISHED to 1.6f)
    private val phaseShotDone = BooleanArray(4)

    /** Close-race mode also grabs the slow-motion approach, the photo print and the slip. */
    private fun capturePhaseShots(plan: io.github.novodyinparticular.pixelstrip.ShotPlan) {
        if (!plan.close) return
        for ((i, pair) in phaseShots.withIndex()) {
            val (p, at) = pair
            val due = if (at < 0f) phase == p && slowMo && timeScale < 0.5f else phase == p && phaseTime >= at
            if (!phaseShotDone[i] && due) {
                phaseShotDone[i] = true
                saveFrame("${plan.dir}/photo_$i.png")
            }
        }
    }

    private fun saveFrame(path: String) {
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        PixmapIO.writePNG(Gdx.files.absolute(path), pixmap, Deflater.DEFAULT_COMPRESSION, true)
        pixmap.dispose()
        Gdx.app.log("shots", "saved $path phase=$phase")
    }

    private fun captureShots() {
        val plan = shots ?: return
        capturePhaseShots(plan)
        if (shotIndex >= plan.times.size) return
        if (screenTime < plan.times[shotIndex]) return
        val bw = Gdx.graphics.backBufferWidth
        val bh = Gdx.graphics.backBufferHeight
        val pixmap = Pixmap.createFromFrameBuffer(0, 0, bw, bh)
        val file = Gdx.files.absolute("${plan.dir}/shot_%02d.png".format(shotIndex))
        PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true)
        pixmap.dispose()
        Gdx.app.log("shots", "saved ${file.name()} t=%.2f phase=$phase x=%.1f v=%.1f rpm=%.0f gear=%d temp=%.2f fps=%d ratings=${player.ratings}".format(
            screenTime, player.x, player.v, player.rpm, player.gear + 1, player.tireTemp, Gdx.graphics.framesPerSecond))
        shotIndex++
        if (shotIndex >= plan.times.size) Gdx.app.exit()
    }

    companion object {
        const val STEP = 1f / 240f
        const val BURNOUT_X = -9f
        const val CAM_LEAD = 0.42f
        const val TREE_DX = 16f
        const val TREE_BASE_TOP = 240f
        const val TACH_Y = 120f
        const val TACH_X = 52f
        const val PHOTO_HOLD_S = 1.8f
        const val PHOTO_GAP_M = 2.5f
        // ET digits inside the finish_gantry sprite (tools/art/track_neon.py)
        const val ET_BOARD_X = 16f
        const val ET_BOARD_Y = 56f
        private val ET_RED = Color(1f, 0.42f, 0.32f, 1f)
        private val CYAN = Color(0.49f, 0.95f, 0.86f, 1f)
        private val PINK = Color(0.94f, 0.37f, 0.68f, 1f)
        private val ORANGE = Color(1f, 0.6f, 0.24f, 1f)
        private val NOS_BLUE = Color(0.45f, 0.75f, 1f, 1f)
        private val GHOST_MARK = Color(0.3f, 0.85f, 0.95f, 1f)
        private val BEAM = Color(1f, 0.9f, 0.7f, 1f)
        private val GHOST_BEAM = Color(0.5f, 0.9f, 1f, 1f)
        private val SMOKE_LIT = Color(1f, 0.55f, 0.62f, 1f)
        private val SMOKE_AMBIENT = Color(0.78f, 0.74f, 0.95f, 1f)
    }
}
