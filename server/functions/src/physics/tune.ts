/** Tune as the game saves it (SaveGame.tuneJson keys) -> parts.ts Tune. */
import { Tune } from "./parts";

/* eslint-disable @typescript-eslint/no-explicit-any */
export function tuneFrom(o: any): Tune | null {
  if (!o || typeof o !== "object") return null;
  const n = (v: any) => (typeof v === "number" && Number.isFinite(v) ? v : undefined);
  const arr = (v: any) => (Array.isArray(v) && v.length <= 8 && v.every((x) => typeof x === "number" && Number.isFinite(x)) ? (v as number[]) : null);
  return {
    gears: arr(o.gears),
    finalDrive: n(o.final) ?? null,
    launchRpm: n(o.launch) ?? 0,
    nitrousGear: Math.trunc(n(o.nosGear) ?? 0),
    tirePressure: n(o.psi) ?? 32,
    boostPsi: n(o.boost) ?? null,
    boostByGear: arr(o.boostByGear),
    scPsi: n(o.sc) ?? null,
    timing: n(o.timing) ?? 0,
    afr: n(o.afr) ?? null,
    limiter: n(o.limiter) ?? null,
    nitrousShot: n(o.shot) ?? null,
    nitrousRamp: n(o.ramp) ?? 0.6,
    rideHeight: n(o.ride) ?? 0,
    shockRate: n(o.shock) ?? 0.5,
  };
}
