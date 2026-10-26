/**
 * The physics half of core/.../data/CarDef.kt: a car's stats as the simulation needs them.
 * Built from assets/data/cars/<id>.json, then turned into the effective car by parts.ts.
 */
import * as K from "./consts";

export interface Nitrous { hp: number; charges: number; seconds: number }

export interface CarDef {
  id: string;
  price: number;
  dealer: boolean;
  hp: number;
  peakHpRpm: number;
  torqueNm: number;
  peakTorqueRpm: number;
  idleRpm: number;
  redline: number;
  weightKg: number;
  drivetrain: string;
  rearWeight: number;
  wheelbaseM: number;
  cgHeightM: number;
  gears: number[];
  finalDrive: number;
  shiftTime: number;
  clutchTime: number;
  clutchTorque: number;
  tireRadiusM: number;
  grip: number;
  engineInertia: number;
  wheelInertia: number;
  dragArea: number;
  powerRating: string;
  nitrous: Nitrous;
  stockTurbo: boolean;
  /** For swaps: what sits in the bay ("rotary" counts rotors as cylinders). */
  engineLayout: string;
  cylinders: number;
  // from parts and tunes (parts.ts)
  boostGain: number;
  boostSpoolRpm: number;
  boostLag: number;
  /** Factory turbo: share of the published torque its stock boost makes (CarDef.stockBoostGain). */
  stockBoostGain: number;
  /** An automatic with a torque converter: staged on the brake it loads the engine and builds boost. */
  converter: boolean;
  blowerGain: number;
  tirePressure: number;
  pressureSens: number;
  launchControl: number;
  nitrousAutoGear: number;
  nitrousRamp: number;
  boostPsi: number;
  boostByGear: number[] | null;
  blowOff: boolean;
  antiLag: boolean;
  blowerType: number;
  bandLo: number;
  bandMid: number;
  bandHi: number;
  powerMult: number;
  knockLimit: number;
  fuelHp: number;
  transferMult: number;
  transferRate: number;
  wheelieBars: boolean;
  tireWarm: number;
  timingDeg: number;
  afr: number;
}

export const STOCK_PRESSURE = 32;
export const GROSS_TO_NET = 0.82;

export const ratingFactor = (d: CarDef) => (d.powerRating === "gross" ? GROSS_TO_NET : 1);

/* eslint-disable @typescript-eslint/no-explicit-any */
export function parseCar(j: any): CarDef {
  const num = (k: string, def?: number): number => {
    const v = j[k];
    if (typeof v === "number") return v;
    if (def === undefined) throw new Error(`car ${j.id}: missing ${k}`);
    return def;
  };
  return {
    id: j.id,
    price: num("price", 0),
    dealer: j.dealer !== false,
    hp: num("hp"),
    peakHpRpm: num("peakHpRpm"),
    torqueNm: num("torqueNm"),
    peakTorqueRpm: num("peakTorqueRpm"),
    idleRpm: num("idleRpm", 800),
    redline: num("redline"),
    weightKg: num("weightKg"),
    drivetrain: j.drivetrain,
    rearWeight: num("rearWeight", 0.5),
    wheelbaseM: num("wheelbaseM", 2.6),
    cgHeightM: num("cgHeightM", 0.5),
    gears: j.gears,
    finalDrive: num("finalDrive"),
    shiftTime: num("shiftTime", 0.25),
    clutchTime: num("clutchTime", 0.3),
    clutchTorque: num("clutchTorque", 1.5),
    tireRadiusM: num("tireRadiusM"),
    grip: num("grip"),
    engineInertia: num("engineInertia", 0.25),
    wheelInertia: num("wheelInertia", 2.5),
    dragArea: num("dragArea"),
    powerRating: j.powerRating ?? "net",
    nitrous: j.nitrous ? { hp: j.nitrous.hp, charges: j.nitrous.charges, seconds: j.nitrous.seconds } : { hp: 0, charges: 0, seconds: 0 },
    stockTurbo: j.engine?.stockTurbo === true,
    engineLayout: typeof j.engine?.layout === "string" ? j.engine.layout : "v8",
    cylinders: typeof j.engine?.cylinders === "number" ? j.engine.cylinders : 8,
    boostGain: 0,
    // a factory turbo is sized to be on full boost by the torque peak
    boostSpoolRpm: j.engine?.stockTurbo === true ? (typeof j.engine.spoolRpm === "number" ? j.engine.spoolRpm : Math.max(K.STOCK_SPOOL_MIN, num("peakTorqueRpm") - K.BOOST_RANGE_RPM)) : 0,
    boostLag: j.engine?.stockTurbo === true ? (typeof j.engine.lag === "number" ? j.engine.lag : K.STOCK_LAG) : 0.8,
    stockBoostGain: j.engine?.stockTurbo === true ? K.STOCK_BOOST_GAIN : 0,
    converter: j.converter === true,
    blowerGain: 0,
    tirePressure: STOCK_PRESSURE,
    pressureSens: 1,
    launchControl: 0,
    nitrousAutoGear: 0,
    nitrousRamp: 0,
    boostPsi: 0,
    boostByGear: null,
    blowOff: false,
    antiLag: false,
    blowerType: 0,
    bandLo: 0,
    bandMid: 0,
    bandHi: 0,
    powerMult: 1,
    knockLimit: Number.MAX_VALUE,
    fuelHp: Number.MAX_VALUE,
    transferMult: 1,
    transferRate: 1,
    wheelieBars: false,
    tireWarm: 0.78,
    timingDeg: 0,
    afr: 12.8,
  };
}
