/**
 * Port of core/.../data/Parts.kt (PartCatalog's lookups, Tune, Upgrades): installed parts and a
 * tune turn the stock car into the car that races. Must match the Kotlin; the shared physics
 * cases check it.
 */
import { CarDef, STOCK_PRESSURE, ratingFactor } from "./car";
import * as K from "./consts";

/** engine: a swap kit's engine; engineType "piston"/"rotary": does nothing on the other kind; boostOnly: needs a turbo. */
export interface Option { name: string; price: number; effects: Record<string, number>; engine?: string; engineType?: string; boostOnly: boolean }
export interface Slot { id: string; options: Option[]; excludes: string[] }
/** PartCatalog.EngineSpec */
export interface EngineSpec {
  hp: number; peakHpRpm: number; torqueNm: number; peakTorqueRpm: number; redline: number; idleRpm: number;
  layout: string; cylinders: number; turbo: boolean; vtecRpm?: number; lag?: number; spoolRpm?: number; mass: number;
}
export interface Catalog {
  slots: Slot[]; byId: Map<string, Slot>; cosmeticDrag: Record<string, number[]>;
  engines: Record<string, EngineSpec>; layoutMass: Record<string, number>;
}

/* eslint-disable @typescript-eslint/no-explicit-any */
export function parseCatalog(j: any): Catalog {
  const slots: Slot[] = j.slots.map((s: any) => ({
    id: s.id,
    excludes: s.excludes ?? [],
    options: s.options.map((o: any) => {
      const effects: Record<string, number> = {};
      for (const [k, v] of Object.entries(o)) if (typeof v === "number" && k !== "price") effects[k] = v;
      return { name: o.name, price: o.price, effects, engine: o.engine, engineType: o.engineType, boostOnly: o.boostOnly === true };
    }),
  }));
  const cosmeticDrag: Record<string, number[]> = {};
  for (const c of j.cosmetic) if (Array.isArray(c.dragArea)) cosmeticDrag[c.id] = c.dragArea;
  return { slots, byId: new Map(slots.map((s) => [s.id, s])), cosmeticDrag, engines: j.engines ?? {}, layoutMass: j.layoutMass ?? {} };
}

const worksOn = (o: Option, layout: string) => o.engineType === undefined || (o.engineType === "rotary") === (layout === "rotary");
const engineMass = (c: Catalog, layout: string, cylinders: number) => c.layoutMass[layout === "rotary" ? `rotary${cylinders}` : layout] ?? 180;

/** Upgrades.swapped: a swap kit's engine in place of the car's own (power, rpm, layout, factory turbo, weight). */
export function swapped(def: CarDef, c: Catalog, parts: Record<string, number>): CarDef {
  const id = fitted(c, parts, "swap").engine;
  const e = id !== undefined ? c.engines[id] : undefined;
  if (!e) return def;
  const big = ["v8", "v10", "v12", "flat12"].includes(e.layout);
  return {
    ...def,
    hp: e.hp, peakHpRpm: e.peakHpRpm, torqueNm: e.torqueNm, peakTorqueRpm: e.peakTorqueRpm, redline: e.redline, idleRpm: e.idleRpm,
    powerRating: "net", engineLayout: e.layout, cylinders: e.cylinders,
    stockTurbo: e.turbo, stockBoostGain: e.turbo ? K.STOCK_BOOST_GAIN : 0,
    boostSpoolRpm: e.turbo ? e.spoolRpm ?? Math.max(K.STOCK_SPOOL_MIN, e.peakTorqueRpm - K.BOOST_RANGE_RPM) : 0,
    boostLag: e.turbo ? e.lag ?? K.STOCK_LAG : 0.8,
    engineInertia: Math.round((0.06 + 0.035 * e.cylinders + (big ? 0.06 : 0)) * 100) / 100,
    weightKg: def.weightKg + e.mass - engineMass(c, def.engineLayout, def.cylinders),
  };
}

export const turboed = (def: CarDef, c: Catalog, parts: Record<string, number>) => def.stockTurbo || fx(c, parts, "turbo", "psi") > 0;

/** Upgrades.working: the fitted option if it does anything on this (already swapped) car. */
export function working(c: Catalog, parts: Record<string, number>, slot: string, def: CarDef): Option | null {
  const o = fitted(c, parts, slot);
  return worksOn(o, def.engineLayout) && (!o.boostOnly || turboed(def, c, parts)) ? o : null;
}
const wfx = (c: Catalog, parts: Record<string, number>, slot: string, key: string, def: CarDef) => working(c, parts, slot, def)?.effects[key] ?? 0;

export function fitted(c: Catalog, parts: Record<string, number>, slot: string): Option {
  const s = c.byId.get(slot);
  if (!s) throw new Error(`no slot ${slot}`);
  const i = Math.min(Math.max(parts[slot] ?? 0, 0), s.options.length - 1);
  return s.options[i];
}

export const fx = (c: Catalog, parts: Record<string, number>, slot: string, key: string) => fitted(c, parts, slot).effects[key] ?? 0;

export interface Tune {
  gears?: number[] | null;
  finalDrive?: number | null;
  launchRpm?: number;
  nitrousGear?: number;
  tirePressure?: number;
  boostPsi?: number | null;
  boostByGear?: number[] | null;
  scPsi?: number | null;
  timing?: number;
  afr?: number | null;
  limiter?: number | null;
  nitrousShot?: number | null;
  nitrousRamp?: number;
  rideHeight?: number;
  shockRate?: number;
}

export const BASE_RANGE = 0.05;
export const TIMING_RANGE = [0, 2, 4, 6];
export const TIMING_POWER = 0.012;
export const TIMING_KNOCK = 0.03;
export const AFR_MIN = 10.5;
export const AFR_MAX = 13.5;
export const AFR_POWER = 0.015;
export const AFR_KNOCK = 0.05;
export const PSI_GAIN = 0.05;
export const SPRING_PSI = 8;
export const DEFAULT_PSI = 14;
export const STOCK_TURBO_PSI = 10;
export const STOCK_IC = 0.86;
export const SC_EFF = [0, 0.72, 0.85, 0.9];
export const STOCK_FUEL_MARGIN = 1.3;
export const NITROUS_SECONDS = 2.5;
export const RIDE_CG = 0.06;
export const RIDE_DRAG = 0.03;

const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);

export interface Limits {
  gearRange: number;
  finalRange: number;
  timingRange: number;
  afr: boolean;
  limiter: boolean;
  limiterMax: number;
  launchControl: boolean;
  boostMax: number;
  boostAdjustable: boolean;
  boostByGear: boolean;
  scMax: number;
  nitrousMax: number;
  progressive: boolean;
  rideHeight: boolean;
  shocks: boolean;
  antiLag: boolean;
}

export function limits(stock: CarDef, c: Catalog, parts: Record<string, number>): Limits {
  const def = swapped(stock, c, parts);
  const ecu = Math.trunc(fx(c, parts, "ecu", "ecu"));
  const turbo = fx(c, parts, "turbo", "psi");
  const ctl = Math.trunc(fx(c, parts, "boostctl", "boostCtl"));
  const launch = Math.trunc(fx(c, parts, "launch", "launch"));
  let rev = 0;
  for (const s of c.slots) rev += wfx(c, parts, s.id, "rev", def);
  return {
    gearRange: BASE_RANGE + fx(c, parts, "gearbox", "tuneRange"),
    finalRange: BASE_RANGE + fx(c, parts, "final", "finalRange"),
    timingRange: TIMING_RANGE[ecu],
    afr: ecu >= 2,
    limiter: ecu >= 2,
    limiterMax: def.redline + rev,
    launchControl: launch >= 1,
    boostMax: turbo > 0 ? turbo + fx(c, parts, "wastegate", "psiAdd") : 0,
    boostAdjustable: turbo > 0 && ctl >= 1,
    boostByGear: turbo > 0 && ctl >= 2 && ecu >= 3,
    scMax: fx(c, parts, "supercharger", "scPsi"),
    nitrousMax: fx(c, parts, "nitrous", "nosHp"),
    progressive: fx(c, parts, "nosctl", "progressive") > 0,
    rideHeight: fx(c, parts, "springs", "adjustable") > 0,
    shocks: fx(c, parts, "shocks", "adjustable") > 0,
    antiLag: turboed(def, c, parts) && launch >= 2,
  };
}

export function baseGears(def: CarDef, c: Catalog, parts: Record<string, number>): number[] {
  const count = Math.trunc(fx(c, parts, "gearbox", "gears"));
  if (count <= def.gears.length) return def.gears;
  const first = def.gears[0];
  const top = Math.min(def.gears[def.gears.length - 1], 0.8);
  return Array.from({ length: count }, (_, i) => first * Math.pow(top / first, i / (count - 1)));
}

export const defaultAfr = (forced: boolean) => (forced ? 11.5 : 12.8);

/** Upgrades.spoolScale: big engines spool a given turbo sooner (rated for ~300 Nm, sqrt of stock torque, 0.6..1.4). */
export const SPOOL_REF_NM = 300;
export const spoolScale = (def: CarDef) => clamp(Math.sqrt(SPOOL_REF_NM / (def.torqueNm * ratingFactor(def))), 0.6, 1.4);

export function apply(stock: CarDef, c: Catalog, parts: Record<string, number>, cosmetics: Record<string, number>, tune: Tune | null): CarDef {
  const lim = limits(stock, c, parts);
  const def = swapped(stock, c, parts);
  let lagMult = 1;
  let lo = 0, mid = 0, hi = 0, peakRpm = 0, idle = 0, weight = 0, rear = 0, grip = 0, clutch = 0, drag = 0;
  let clutchTime = 1, inertia = 1, wheelInertia = 1, shift = 1, sens = 1, transfer = 1, transferRate = 1;
  for (const slot of c.slots) {
    const w = working(c, parts, slot.id, def);       // piston parts on a rotary, a downpipe with no turbo
    if (!w) continue;
    const e = w.effects;
    const all = e.tq ?? 0;
    lo += all + (e.lo ?? 0);
    mid += all + (e.mid ?? 0);
    hi += all + (e.hi ?? 0);
    peakRpm += e.peakRpm ?? 0;
    idle += e.idle ?? 0;
    weight += e.weight ?? 0;
    rear += e.rear ?? 0;
    grip += e.grip ?? 0;
    clutch += e.clutch ?? 0;
    drag += e.drag ?? 0;
    if (e.clutchTime !== undefined) clutchTime *= e.clutchTime;
    if (e.inertia !== undefined) inertia *= e.inertia;
    if (e.wheelInertia !== undefined) wheelInertia *= e.wheelInertia;
    if (e.shift !== undefined) shift *= e.shift;
    if (e.sens !== undefined) sens *= e.sens;
    if (e.transfer !== undefined) transfer *= e.transfer;
    if (e.transferRate !== undefined) transferRate *= e.transferRate;
    if (e.lagMult !== undefined) lagMult *= e.lagMult;
  }
  for (const [id, drags] of Object.entries(c.cosmeticDrag)) {
    const style = cosmetics[id] ?? 0;
    if (style >= 0 && style < drags.length) drag += drags[style];
  }

  const timing = clamp(tune?.timing ?? 0, -lim.timingRange, lim.timingRange);
  const forced = lim.boostMax > 0 || lim.scMax > 0 || lim.nitrousMax > 0 || def.stockTurbo;
  const afr = lim.afr ? clamp(tune?.afr ?? defaultAfr(forced), AFR_MIN, AFR_MAX) : defaultAfr(forced);
  const afrDelta = afr - defaultAfr(forced);
  const powerMult = (1 + timing * TIMING_POWER) * (1 + afrDelta * AFR_POWER);
  const knockLimit = Math.max(wfx(c, parts, "internals", "strength", def), wfx(c, parts, "swap", "strength", def),
    wfx(c, parts, "apexseals", "strength", def)) *
    fx(c, parts, "fueltype", "octane") * (1 - afrDelta * AFR_KNOCK) / (1 + timing * TIMING_KNOCK);
  const fuelFlow = fitted(c, parts, "fueltype").effects.fuelFlow ?? 1;
  const stockFuel = def.hp * ratingFactor(def) * STOCK_FUEL_MARGIN;
  const fuelHp = Math.max(stockFuel, wfx(c, parts, "fuel", "fuelHp", def)) / fuelFlow;

  const icRaw = fitted(c, parts, "intercooler").effects.icEff;
  const icEff = icRaw !== undefined ? ((parts.intercooler ?? 0) === 0 && def.stockTurbo ? STOCK_IC : icRaw) : 0.78;
  let psi: number;
  if (lim.boostMax <= 0) psi = 0;
  else if (!lim.boostAdjustable) psi = Math.min(SPRING_PSI, lim.boostMax);
  else psi = clamp(tune?.boostPsi ?? Math.min(lim.boostMax, DEFAULT_PSI), Math.min(SPRING_PSI, lim.boostMax), lim.boostMax);
  const extraPsi = def.stockTurbo ? Math.max(0, psi - STOCK_TURBO_PSI) : psi;
  const turbo = fitted(c, parts, "turbo").effects;
  const sc = fitted(c, parts, "supercharger").effects;
  const scPsi = lim.scMax > 0 ? clamp(tune?.scPsi ?? lim.scMax * 0.8, lim.scMax * 0.5, lim.scMax) : 0;
  const byGear = lim.boostByGear ? (tune?.boostByGear ?? null) : null;

  const nosMax = lim.nitrousMax;
  const nitrous = nosMax > 0
    ? {
      hp: clamp(tune?.nitrousShot ?? nosMax, nosMax * 0.25, nosMax),
      charges: Math.max(1, Math.trunc(fx(c, parts, "bottle", "charges"))),
      seconds: def.nitrous.seconds > 0 ? def.nitrous.seconds : NITROUS_SECONDS,
    }
    : def.nitrous;

  const base = baseGears(def, c, parts);
  const gears = tune?.gears && tune.gears.length === base.length
    ? tune.gears.map((g, i) => clamp(g, base[i] * (1 - lim.gearRange), base[i] * (1 + lim.gearRange)))
    : base;
  const stockFinal = def.finalDrive * (fitted(c, parts, "final").effects.finalMult ?? 1);
  const finalDrive = clamp(tune?.finalDrive ?? stockFinal, stockFinal * (1 - lim.finalRange), stockFinal * (1 + lim.finalRange));

  const peakHp = def.peakHpRpm + peakRpm;
  const ride = lim.rideHeight ? clamp(tune?.rideHeight ?? 0, -1, 1) : 0;
  const shockRate = lim.shocks ? 0.6 + 0.8 * clamp(tune?.shockRate ?? 0.5, 0, 1) : 1;
  const maxRedline = Math.max(lim.limiterMax, peakHp + 300);
  const redline = lim.limiter ? clamp(tune?.limiter ?? maxRedline, peakHp + 200, maxRedline) : maxRedline;
  const sens0 = fx(c, parts, "tires", "sens");
  const warm = fx(c, parts, "tires", "warm");
  return {
    ...def,
    peakHpRpm: peakHp,
    peakTorqueRpm: Math.min(def.peakTorqueRpm + peakRpm * 0.6, peakHp - 300),
    idleRpm: def.idleRpm + idle,
    redline,
    weightKg: def.weightKg + weight,
    rearWeight: clamp(def.rearWeight + rear, 0.3, 0.7),
    cgHeightM: def.cgHeightM * (1 + ride * RIDE_CG),
    grip: def.grip * (1 + grip),
    clutchTorque: def.clutchTorque + clutch,
    clutchTime: def.clutchTime * clutchTime,
    engineInertia: def.engineInertia * inertia,
    wheelInertia: def.wheelInertia * wheelInertia,
    shiftTime: Math.max(def.shiftTime * shift, 0.06),
    dragArea: Math.max((def.dragArea + drag) * (1 + ride * RIDE_DRAG), 0.3),
    gears,
    finalDrive,
    nitrous,
    boostGain: extraPsi * PSI_GAIN * icEff,
    // an aftermarket turbo replaces a factory one, so the stock boost spools with it
    boostSpoolRpm: turbo.spool !== undefined ? turbo.spool * spoolScale(def) : def.boostSpoolRpm,
    boostLag: (turbo.lag ?? def.boostLag) * lagMult,
    boostPsi: psi,
    boostByGear: byGear,
    blowOff: fx(c, parts, "bov", "bov") > 0,
    antiLag: lim.antiLag && lim.launchControl && (tune?.launchRpm ?? 0) > 0,
    blowerGain: scPsi * PSI_GAIN * SC_EFF[Math.trunc(sc.scType ?? 0)],
    blowerType: Math.trunc(sc.scType ?? 0),
    tirePressure: tune?.tirePressure ?? STOCK_PRESSURE,
    pressureSens: (sens0 > 0 ? sens0 : 1) * (fitted(c, parts, "wheels").effects.sens ?? 1),
    tireWarm: warm > 0 ? warm : 0.78,
    launchControl: lim.launchControl ? tune?.launchRpm ?? 0 : 0,
    nitrousAutoGear: tune?.nitrousGear ?? 0,
    nitrousRamp: lim.progressive ? clamp(tune?.nitrousRamp ?? 0.6, 0.1, 2) : 0,
    bandLo: lo,
    bandMid: mid,
    bandHi: hi,
    powerMult,
    knockLimit,
    fuelHp,
    transferMult: transfer,
    transferRate: transferRate * shockRate,
    wheelieBars: fx(c, parts, "wheeliebars", "wheelieBars") > 0,
    timingDeg: timing,
    afr,
  };
}
