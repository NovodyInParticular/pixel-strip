/** Port of core/.../race/DragMath.kt: torque curve, power adders, knock and fuel limits, shift points, launch rpm. */
import { CarDef, ratingFactor } from "./car";
import { BOOST_RANGE_RPM, DRIVELINE_EFF, HP_TO_W, LAUNCH_SAG, RPM_TO_RAD } from "./consts";

export function band(d: CarDef, r: number): number {
  let add: number;
  if (r <= d.idleRpm) add = d.bandLo;
  else if (r <= d.peakTorqueRpm) add = d.bandLo + (d.bandMid - d.bandLo) * (r - d.idleRpm) / (d.peakTorqueRpm - d.idleRpm);
  else if (r <= d.peakHpRpm) add = d.bandMid + (d.bandHi - d.bandMid) * (r - d.peakTorqueRpm) / (d.peakHpRpm - d.peakTorqueRpm);
  else add = d.bandHi;
  return Math.max(1 + add, 0.3);
}

function rawTorque(d: CarDef, r: number): number {
  const tPeak = d.torqueNm;
  const tAtPeakHp = d.hp * 745.7 / (d.peakHpRpm * 2 * Math.PI / 60);
  if (r <= d.peakTorqueRpm) {
    const t = Math.min(Math.max((r - d.idleRpm) / (d.peakTorqueRpm - d.idleRpm), 0), 1);
    return tPeak * (0.62 + 0.38 * (1 - (1 - t) * (1 - t)));
  }
  if (r <= d.peakHpRpm) return tPeak + (tAtPeakHp - tPeak) * (r - d.peakTorqueRpm) / (d.peakHpRpm - d.peakTorqueRpm);
  return tAtPeakHp * (1 - 0.55 * Math.min(Math.max((r - d.peakHpRpm) / (d.redline - d.peakHpRpm + 1), 0), 1));
}

export const torqueAt = (d: CarDef, r: number) => rawTorque(d, r) * ratingFactor(d) * band(d, r) * d.powerMult;

export function blower(d: CarDef, r: number): number {
  const t = Math.min(Math.max(r / d.redline, 0), 1);
  switch (d.blowerType) {
    case 1: return d.blowerGain * (1.05 - 0.25 * t);
    case 2: return d.blowerGain * (0.95 + 0.05 * t);
    case 3: return d.blowerGain * t * t;
    default: return d.blowerGain * (0.75 + 0.25 * t);
  }
}

export const KNOCK_RETARD = 0.97;
export const LEAN_CUT = 0.97;

export function limit(d: CarDef, r: number, na: number, total: number): number {
  let t = total;
  if (na > 0 && t > na * d.knockLimit) t = na * d.knockLimit * KNOCK_RETARD;
  const fuelT = d.fuelHp * HP_TO_W / (Math.max(r, 1000) * RPM_TO_RAD);
  if (t > fuelT) t = fuelT * LEAN_CUT;
  return t;
}

export function crankTorque(d: CarDef, r: number): number {
  const na = torqueAt(d, r);
  let t = na;
  const spooled = Math.min(Math.max((r - d.boostSpoolRpm) / BOOST_RANGE_RPM, 0), 1);
  if (d.stockBoostGain > 0) t *= stockBoost(d, spooled);
  if (d.blowerGain > 0) t *= 1 + blower(d, r);
  if (d.boostGain > 0) t *= 1 + d.boostGain * spooled;
  return limit(d, r, na, t);
}

/** Share of the published (full stock boost) torque a factory turbo car makes at boost 0..1. */
export function stockBoost(d: CarDef, boost: number) {
  return (1 + d.stockBoostGain * boost) / (1 + d.stockBoostGain);
}

export function shiftPoints(d: CarDef): number[] {
  return d.gears.map((_, g) => {
    if (g === d.gears.length - 1) return d.redline;
    const r1 = d.gears[g];
    const r2 = d.gears[g + 1];
    let rpm = d.peakTorqueRpm;
    while (rpm < d.redline * 0.985) {
      if (torqueAt(d, rpm * r2 / r1) * r2 >= torqueAt(d, rpm) * r1) return rpm;
      rpm += 25;
    }
    return d.redline * 0.985;
  });
}

export function launchRpm(d: CarDef, trackGrip: number): number {
  const g = 9.81 * d.weightKg;
  const load = d.drivetrain === "AWD" ? g : d.drivetrain === "FWD" ? g * (1 - d.rearWeight) * 0.9 : g * (d.rearWeight + 0.08);
  const grip = d.grip * trackGrip * load;
  const ratio = d.gears[0] * d.finalDrive;
  const bogEdge = d.idleRpm * 2.0 / (1 - LAUNCH_SAG);
  let rpm = d.idleRpm * 1.5;
  while (rpm < d.peakTorqueRpm && torqueAt(d, rpm) * ratio * DRIVELINE_EFF / d.tireRadiusM < grip * 1.6) rpm += 50;
  return Math.min(Math.max(rpm, bogEdge), d.peakHpRpm * 0.85);
}
