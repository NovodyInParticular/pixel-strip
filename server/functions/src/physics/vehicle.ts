/**
 * Port of core/.../race/Vehicle.kt (the physics only: no ratings list or UI events) and a replay
 * of a RunRecorder event list. Kotlin uses 32-bit floats and this uses doubles, so results agree
 * to well under the 0.01 s the shared physics cases allow, not bit for bit.
 */
import { CarDef, STOCK_PRESSURE } from "./car";
import * as K from "./consts";
import { blower, launchRpm, limit, shiftPoints, torqueAt } from "./dragmath";

export type Mode = "HELD" | "BURNOUT" | "CREEP" | "LAUNCH" | "DRIVE" | "SHIFT";

const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);

export class Vehicle {
  mode: Mode = "HELD";
  x = 0;
  v = 0;
  accel = 0;
  rpm: number;
  gear = 0;
  driveW = 0;
  spinning = false;
  slipSpeed = 0;
  tireTemp = 0.15;
  wheelie = 0;
  limiter = false;
  throttle = 0;
  brake = 0;
  trackGrip = K.DEFAULT_PREP;
  nitrousCharges: number;
  nitrousTime = 0;
  readonly shiftPoints: number[];
  hasLeft = false;
  leaveTime = 0;
  readonly splits = K.SPLITS_M.map(() => -1);
  readonly splitSpeed = K.SPLITS_M.map(() => 0);
  /** Launch and shift ratings, as in the game (PERFECT shifts earn the bonus). */
  readonly ratings: string[] = [];
  steps = 0;
  boost = 0;

  private clutch = 0;
  private launchRpm = 0;
  private feather = 1;
  private shiftTimer = 0;
  private bonusTimer = 0;
  private limiterTimer = 0;
  private creepTarget = 0;
  private surface = K.DEFAULT_PREP;
  private clock = 0;
  private nitrousOn = 0;
  private transferAccel = 0;
  private readonly pressureGrip: number;
  private readonly tireR: number;
  private readonly rolling: number;

  constructor(readonly def: CarDef, private readonly powerScale = 1) {
    this.rpm = def.idleRpm;
    this.nitrousCharges = def.nitrous.charges;
    this.shiftPoints = shiftPoints(def);
    const pressureDelta = STOCK_PRESSURE - def.tirePressure;
    this.pressureGrip = 1 + pressureDelta * K.PRESSURE_GRIP * def.pressureSens;
    this.tireR = def.tireRadiusM * (1 - pressureDelta * K.PRESSURE_RADIUS);
    this.rolling = K.ROLLING * (1 + pressureDelta * K.PRESSURE_ROLL);
  }

  get nitrousActive() { return this.nitrousTime > 0; }
  private get mass() { return this.def.weightKg; }
  private get ratio() { return this.def.gears[this.gear] * this.def.finalDrive; }
  private get rollW() { return this.v / this.tireR; }
  private get peakGrip() { return this.def.grip * this.tempGrip() * this.surface * this.pressureGrip; }

  // --- commands --------------------------------------------------------------------------

  startBurnout() {
    this.mode = "BURNOUT";
    this.surface = K.BURNOUT_SURFACE;
    this.gear = 0;
  }

  creepTo(targetX: number) {
    this.mode = "CREEP";
    this.surface = this.trackGrip;
    this.creepTarget = targetX;
  }

  launch(raceClock: number) {
    if (this.mode !== "HELD") return;
    this.clock = raceClock;
    this.mode = "LAUNCH";
    this.clutch = 0;
    this.gear = 0;
    this.launchRpm = this.rpm;
    this.feather = 1;
    const sweet = launchRpm(this.def, this.trackGrip);
    if (this.rpm > sweet) {
      const extraW = (this.rpm - sweet) * K.RPM_TO_RAD;
      const inertia = this.def.wheelInertia + this.def.engineInertia * this.ratio * this.ratio;
      this.driveW = this.def.engineInertia * extraW * this.ratio / inertia * K.SPIN_SHOCK;
      this.spinning = this.driveW * this.tireR > 0.4;
    }
    const d = (this.rpm - sweet) / sweet;
    this.ratings.push(Math.abs(d) <= 0.08 ? "PERFECT" : Math.abs(d) <= 0.18 ? "GOOD" : d < 0 ? "BOG" : "SPIN");
  }

  shift() {
    if (this.gear >= this.def.gears.length - 1 || (this.mode !== "DRIVE" && this.mode !== "LAUNCH")) return;
    const opt = this.shiftPoints[this.gear];
    const d = (this.rpm - opt) / opt;
    const r = Math.abs(d) <= 0.035 ? "PERFECT" : Math.abs(d) <= 0.09 ? "GOOD" : d < 0 ? "EARLY" : "LATE";
    this.shiftTimer = this.def.shiftTime * (r === "PERFECT" ? 0.55 : r === "GOOD" ? 0.85 : 1);
    if (r === "PERFECT") this.bonusTimer = K.PERFECT_BONUS_S;
    this.mode = "SHIFT";
    this.ratings.push(r);
  }

  nitrous(): boolean {
    if (this.nitrousCharges <= 0 || this.nitrousActive || this.def.nitrous.hp <= 0) return false;
    if (this.mode !== "DRIVE" && this.mode !== "SHIFT" && this.mode !== "LAUNCH") return false;
    this.nitrousCharges--;
    this.nitrousTime = this.def.nitrous.seconds;
    return true;
  }

  /** Test and rating hook: set the staged rpm and tyre temperature directly (Vehicle.prime). */
  prime(stagedRpm: number, temp: number) {
    this.rpm = clamp(stagedRpm, this.def.idleRpm, this.def.redline);
    this.tireTemp = temp;
  }

  // --- model -----------------------------------------------------------------------------

  private torqueAt(r: number) { return torqueAt(this.def, r) * this.powerScale; }

  private tempGrip() {
    const t = this.tireTemp;
    const bell = 1 - ((t - this.def.tireWarm) / 0.4) ** 2;
    const hot = t > 1.1 ? (t - 1.1) * 0.6 : 0;
    return 1 + 0.12 * Math.max(0, bell) - hot;
  }

  private friction(r: number) { return 15 + r * 0.008; }

  private bogFactor() {
    const lo = this.def.idleRpm * 0.8;
    const hi = this.def.idleRpm * 2.0;
    const t = clamp((this.rpm - lo) / (hi - lo), 0, 1);
    return 0.22 + 0.78 * t * t * (3 - 2 * t);
  }

  private engineTorque(dt: number, bog = true) {
    const d = this.def;
    if (this.rpm >= d.redline) this.limiterTimer = K.LIMITER_CUT_S;
    this.limiter = this.limiterTimer > 0;
    if (this.limiter) {
      this.limiterTimer -= dt;
      return -this.friction(this.rpm);
    }
    const na = this.torqueAt(this.rpm) * (bog ? this.bogFactor() : 1);
    let t = na;
    if (d.blowerGain > 0) t *= 1 + blower(d, this.rpm);
    if (d.boostGain > 0) t *= 1 + d.boostGain * this.boost;
    if (this.nitrousActive) {
      this.nitrousOn = d.nitrousRamp > 0 ? Math.min(1, this.nitrousOn + dt / d.nitrousRamp) : 1;
      t += d.nitrous.hp * this.nitrousOn * K.HP_TO_W / (Math.max(this.rpm, K.NITROUS_MIN_RPM) * K.RPM_TO_RAD);
    } else this.nitrousOn = 0;
    t = limit(d, this.rpm, na, t) * this.throttle;
    if (this.bonusTimer > 0) t *= 1 + K.PERFECT_BONUS;
    return t - this.friction(this.rpm) * (1 - this.throttle);
  }

  private driveLoad() {
    const d = this.def;
    const g = this.mass * K.GRAVITY;
    const transfer = this.mass * this.transferAccel * d.cgHeightM / d.wheelbaseM * d.transferMult;
    const front = g * (1 - d.rearWeight) - transfer;
    this.wheelie = front < 0 ? Math.min(d.wheelieBars ? K.WHEELIE_BAR_LIFT : 1, -front / g * 3) : 0;
    if (d.drivetrain === "AWD") return g;
    if (d.drivetrain === "FWD") return Math.max(g * 0.15, front);
    return Math.min(g, g * d.rearWeight + transfer);
  }

  private slipRatio(slip: number) { return slip / Math.max(this.v, 3); }

  private gripShare(s: number) {
    if (s < K.PEAK_SLIP) return (s / K.PEAK_SLIP) * (2 - s / K.PEAK_SLIP);
    if (s < K.PLATEAU_SLIP) return 1;
    return 1 - K.SPIN_FALLOFF * Math.min(1, (s - K.PLATEAU_SLIP) / 0.8);
  }

  private spool(dt: number) {
    const d = this.def;
    if (d.boostGain <= 0) return;
    let target = clamp((this.rpm - d.boostSpoolRpm) / K.BOOST_RANGE_RPM, 0, 1) * this.throttle;
    const share = d.boostByGear?.[this.gear];
    if (share !== undefined) target *= clamp(share, 0.2, 1);
    if (this.mode === "HELD" && d.antiLag && this.throttle > 0) target = Math.max(target, K.ANTI_LAG_BOOST);
    if (this.mode === "SHIFT") {
      this.boost *= d.blowOff ? 1 - dt * 0.4 : 1 - Math.min(1, dt * 6);
      return;
    }
    const rate = target > this.boost ? dt / d.boostLag : dt * 3;
    this.boost += (target - this.boost) * Math.min(1, rate * 3);
  }

  step(dt: number) {
    const d = this.def;
    this.steps++;
    this.clock += dt;
    this.transferAccel += (this.accel - this.transferAccel) * Math.min(1, dt * d.transferRate / K.TRANSFER_TAU);
    this.spool(dt);
    if (d.nitrousAutoGear > 0 && this.hasLeft && this.mode === "DRIVE" && this.gear + 1 >= d.nitrousAutoGear) this.nitrous();
    if (this.nitrousActive) this.nitrousTime -= dt;
    if (this.bonusTimer > 0) this.bonusTimer -= dt;
    switch (this.mode) {
      case "HELD":
        this.rpm = this.throttle > 0 ? Math.min(d.redline, this.rpm + K.REV_UP * this.throttle * dt) : Math.max(d.idleRpm, this.rpm - K.REV_DOWN * dt);
        if (d.launchControl > 0 && this.throttle > 0) this.rpm = Math.min(this.rpm, d.launchControl);
        this.limiter = this.rpm >= d.redline - 1;
        this.v = 0;
        this.driveW = 0;
        this.spinning = false;
        this.slipSpeed = 0;
        this.tireTemp = Math.max(0.1, this.tireTemp - dt * 0.015);
        break;
      case "CREEP":
        this.rpm += (d.idleRpm * 1.15 - this.rpm) * Math.min(1, dt * 4);
        this.v = this.creepTarget - this.x > 0.7 ? K.CREEP_SPEED : K.STAGE_SPEED;
        this.x = Math.min(this.creepTarget, this.x + this.v * dt);
        if (this.x >= this.creepTarget) {
          this.v = 0;
          this.mode = "HELD";
        }
        this.driveW = this.rollW;
        this.spinning = false;
        this.slipSpeed = 0;
        break;
      case "BURNOUT": this.burnout(dt); break;
      case "LAUNCH":
      case "DRIVE": this.drive(dt); break;
      case "SHIFT": this.shifting(dt); break;
    }
    if (this.mode === "LAUNCH" || this.mode === "DRIVE" || this.mode === "SHIFT") this.timing();
  }

  private burnout(dt: number) {
    const d = this.def;
    this.v = 0;
    this.accel = 0;
    const inertia = d.wheelInertia + d.engineInertia * this.ratio * this.ratio;
    const load = this.driveLoad();
    const wheelT = this.engineTorque(dt, false) * this.ratio * K.DRIVELINE_EFF;
    const staticLimit = this.peakGrip * load * this.tireR;
    if (this.driveW * this.tireR < 0.3 && wheelT <= staticLimit) {
      this.driveW = 0;
    } else {
      const f = this.peakGrip * (1 - K.SPIN_FALLOFF) * load;
      this.driveW = Math.max(0, this.driveW + (wheelT - f * this.tireR) / inertia * dt);
    }
    this.rpm = Math.max(d.idleRpm * (this.throttle > 0 ? 1.8 : 1), this.driveW * this.ratio / K.RPM_TO_RAD);
    if (this.rpm >= d.redline) this.rpm = d.redline;
    this.slipSpeed = this.driveW * this.tireR;
    this.spinning = this.slipSpeed > 0.5;
    this.tireTemp = Math.min(1.4, this.tireTemp + this.slipSpeed * K.BURNOUT_HEAT * dt);
  }

  private drive(dt: number) {
    const d = this.def;
    const load = this.driveLoad();
    const grip = this.peakGrip * load;
    const coupledRpm = this.driveW * this.ratio / K.RPM_TO_RAD;
    if (this.mode === "LAUNCH") {
      this.clutch = Math.min(1, this.clutch + dt / d.clutchTime);
      const sagged = this.launchRpm * (1 - K.LAUNCH_SAG * this.clutch);
      this.rpm = Math.max(coupledRpm, sagged);
      if (coupledRpm >= sagged) this.mode = "DRIVE";
    } else {
      this.rpm = coupledRpm;
    }
    this.rpm = clamp(this.rpm, d.idleRpm * K.BOG_FLOOR, d.redline + 50);

    const fullT = this.engineTorque(dt) * this.ratio * K.DRIVELINE_EFF;
    const ideal = fullT > 0 ? clamp(grip * this.tireR / fullT, K.FEATHER_MIN, 1) : 1;
    const share = this.spinning ? this.gripShare(this.slipRatio(Math.max(0, this.driveW * this.tireR - this.v))) : 1;
    this.feather = this.spinning ? Math.max(ideal * share * 0.85, this.feather - dt * K.FEATHER_DROP)
      : Math.min(Math.min(1, ideal * 1.04), this.feather + dt * K.FEATHER_RISE);
    if (!d.wheelieBars && this.wheelie > K.WHEELIE_LIFT) this.feather = Math.min(this.feather, 1 - (this.wheelie - K.WHEELIE_LIFT) * 1.2);
    const axleT = fullT * this.feather;
    const driveF = axleT / this.tireR;
    const inertia = d.wheelInertia + d.engineInertia * this.ratio * this.ratio;
    let force: number;
    if (!this.spinning && driveF <= grip) {
      force = driveF;
    } else {
      this.spinning = true;
      const slip = Math.max(0, this.driveW * this.tireR - this.v);
      const s = this.slipRatio(slip);
      force = this.peakGrip * this.gripShare(Math.max(s, K.PEAK_SLIP)) * load;
      this.driveW += (axleT - force * this.tireR) / inertia * dt;
      if (s < K.PEAK_SLIP && driveF <= grip) this.spinning = false;
    }
    this.integrate(force, dt);
    if (!this.spinning) this.driveW = this.rollW;
    this.driveW = Math.max(this.driveW, this.rollW);
    this.slipSpeed = Math.max(0, this.driveW * this.tireR - this.v);
    this.tireTemp = Math.min(1.4, this.tireTemp + this.slipSpeed * 0.004 * dt);
  }

  private shifting(dt: number) {
    const d = this.def;
    const nextRatio = d.gears[this.gear + 1] * d.finalDrive;
    const target = Math.max(d.idleRpm, this.rollW * nextRatio / K.RPM_TO_RAD);
    this.rpm += (target - this.rpm) * Math.min(1, dt * 14);
    this.driveLoad();
    this.integrate(0, dt);
    this.driveW += (this.rollW - this.driveW) * Math.min(1, dt * 10);
    this.slipSpeed = Math.max(0, this.driveW * this.tireR - this.v);
    this.shiftTimer -= dt;
    if (this.shiftTimer <= 0) {
      this.gear++;
      this.mode = "DRIVE";
      this.spinning = false;
      this.rpm = Math.max(d.idleRpm, this.driveW * this.ratio / K.RPM_TO_RAD);
    }
  }

  private integrate(force: number, dt: number) {
    const resist = 0.5 * K.AIR_DENSITY * this.def.dragArea * this.v * this.v + this.rolling * this.mass * K.GRAVITY;
    this.accel = (force - resist) / this.mass - this.brake * K.BRAKE_DECEL;
    this.v = Math.max(0, this.v + this.accel * dt);
    this.x += this.v * dt;
  }

  private timing() {
    if (!this.hasLeft && this.x > K.LEAVE_M) {
      this.hasLeft = true;
      this.leaveTime = this.clock;
    }
    if (!this.hasLeft) return;
    for (let i = 0; i < K.SPLITS_M.length; i++) {
      if (this.splits[i] < 0 && this.x >= K.SPLITS_M[i]) {
        this.splits[i] = this.clock - this.leaveTime;
        this.splitSpeed[i] = this.v;
      }
    }
  }
}

/** One RunRecorder event (core/.../race/RunRecorder.kt). */
export interface RunEvent { s: number; a: string; v?: number; c?: number }

export interface Replay {
  /** Race clock when the stage beam cleared (negative = red light), or null if it never left. */
  leaveTime: number | null;
  splits: number[];
  splitSpeed: number[];
  ratings: string[];
  steps: number;
}

/** Most steps a replay may run (two minutes of racing at 240 Hz). */
export const MAX_STEPS = 240 * 120;

/**
 * Replays [events] from the start: each event applies just before its step, exactly as the game
 * applied it. [startX] is where the car starts (the burnout box). Runs until [finishM] plus a
 * little, or the events end, or MAX_STEPS.
 */
export function replay(def: CarDef, events: RunEvent[], opts: { trackGrip: number; startX: number; finishM: number }): Replay {
  const car = new Vehicle(def);
  car.trackGrip = opts.trackGrip;
  car.x = opts.startX;
  const sorted = [...events].sort((a, b) => a.s - b.s);
  const lastStep = sorted.length ? sorted[sorted.length - 1].s : 0;
  let i = 0;
  for (let step = 0; step < MAX_STEPS; step++) {
    while (i < sorted.length && sorted[i].s === step) {
      const e = sorted[i++];
      switch (e.a) {
        case "thr": car.throttle = clamp(e.v ?? 0, 0, 1); break;
        case "brk": car.brake = clamp(e.v ?? 0, 0, 1); break;
        case "burnout": car.startBurnout(); break;
        case "stage": car.creepTo(K.STAGE_M); break;
        case "launch": car.launch(e.c ?? 0); break;
        case "shift": car.shift(); break;
        case "nos": car.nitrous(); break;
      }
    }
    car.step(K.STEP);
    if (step > lastStep && (car.splits[car.splits.length - 1] >= 0 || car.x > opts.finishM + 10)) break;
  }
  return {
    leaveTime: car.hasLeft ? car.leaveTime : null,
    splits: [...car.splits],
    splitSpeed: [...car.splitSpeed],
    ratings: car.ratings,
    steps: car.steps,
  };
}

/** A perfect driver's quarter mile on warm tyres (DragMath.perfectPass): matchmaking ratings. */
export function perfectPass(def: CarDef): { sixty: number; eighth: number; quarter: number; trapMph: number } {
  const car = new Vehicle(def);
  car.prime(launchRpm(def, K.DEFAULT_PREP), 0.78);
  car.launch(0);
  car.throttle = 1;
  let t = 0;
  while (car.x < K.SPLITS_M[4] && t < 40) {
    if (car.mode === "DRIVE" && car.gear < def.gears.length - 1 && car.rpm >= car.shiftPoints[car.gear]) car.shift();
    car.step(K.STEP);
    t += K.STEP;
  }
  return { sixty: car.splits[0], eighth: car.splits[2], quarter: car.splits[4], trapMph: car.splitSpeed[4] * 2.23694 };
}
