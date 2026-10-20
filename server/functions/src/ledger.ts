/**
 * The ledger rules, mirroring core/.../save/Ledger.kt exactly. Both sides run
 * shared/ledger_cases.json in their tests; change one, change the other.
 */

export interface CarRule { price: number; sell: number; dealer: boolean; paint: number; parts: Record<string, number[]> }
export interface RivalRule {
  id: string; payout: number; boss: boolean;
  car: string; paint: number; parts: Record<string, number>; cosmetics: Record<string, number>;
}
export interface Rules {
  startingCash: number;
  starterCars: string[];
  resalePercent: number;
  cars: Record<string, CarRule>;
  slots: Record<string, { tiers: number; excludes: string[] }>;
  cosmetics: Record<string, { price: number; styles: number }>;
  colors: number;
  colorPrice: number;
  finishes: number[];
  decals: number[];
  decalColors: number;
  decalColorPrice: number;
  rivals: RivalRule[];
  minRaceGapMs: number;
}

export interface Car { uid: string; car: string; paint: number; parts: Record<string, number>; cosmetics: Record<string, number> }
export interface State { cash: number; nextUid: number; lastRaceT: number; beaten: string[]; cars: Car[] }

export interface Entry {
  type: string;
  t: number;
  car?: string;
  uid?: string;
  slot?: string;
  rival?: string;
  won?: boolean;
  pink?: boolean;
  paint?: number[];
}

export function emptyState(): State {
  return { cash: 0, nextUid: 1, lastRaceT: 0, beaten: [], cars: [] };
}

const has = (o: object, k: string | undefined): k is string => k !== undefined && Object.prototype.hasOwnProperty.call(o, k);

function addCar(s: State, car: string, paint: number): Car {
  const c: Car = { uid: `c${s.nextUid++}`, car, paint, parts: {}, cosmetics: {} };
  s.cars.push(c);
  return c;
}

function removeCar(s: State, uid: string) {
  s.cars = s.cars.filter((c) => c.uid !== uid);
}

export function saleValue(r: Rules, car: Car): number {
  const rule = has(r.cars, car.car) ? r.cars[car.car] : undefined;
  if (!rule) return 0;
  let parts = 0;
  for (const [slot, tier] of Object.entries(car.parts)) {
    const prices = has(rule.parts, slot) ? rule.parts[slot] : undefined;
    if (!prices) continue;
    for (let i = 0; i < Math.min(tier, prices.length); i++) parts += prices[i];
  }
  return rule.sell + Math.floor((parts * r.resalePercent) / 100);
}

export function paintCost(r: Rules, car: Car, p: number[]): number {
  let c = 0;
  if (p[0] !== car.paint) c += r.colorPrice;
  if (p[1] !== (car.cosmetics.finish ?? 0)) c += r.finishes[p[1]];
  if (p[2] !== (car.cosmetics.decal ?? 0)) c += r.decals[p[2]];
  if (p[3] !== (car.cosmetics.decalColor ?? 0) && p[2] > 0) c += r.decalColorPrice;
  return c;
}

export function unlocked(r: Rules, s: State, rivalId: string): boolean {
  const i = r.rivals.findIndex((x) => x.id === rivalId);
  return i === 0 || (i > 0 && s.beaten.includes(r.rivals[i - 1].id));
}

const inRange = (v: number, n: number) => Number.isInteger(v) && v >= 0 && v < n;

/** Applies [e] to [s] if the rules allow it. Returns false, and changes nothing, if they don't. */
export function apply(r: Rules, s: State, e: Entry): boolean {
  const car = e.uid !== undefined ? s.cars.find((c) => c.uid === e.uid) : undefined;
  switch (e.type) {
    case "start": {
      if (!has(r.cars, e.car) || s.cars.length > 0 || !r.starterCars.includes(e.car)) return false;
      s.cash = r.startingCash;
      addCar(s, e.car, r.cars[e.car].paint);
      return true;
    }
    case "buy": {
      if (!has(r.cars, e.car)) return false;
      const rule = r.cars[e.car];
      if (!rule.dealer || s.cars.some((c) => c.car === e.car) || s.cash < rule.price) return false;
      s.cash -= rule.price;
      addCar(s, e.car, rule.paint);
      return true;
    }
    case "part": {
      if (!car || !has(r.slots, e.slot)) return false;
      const slot = r.slots[e.slot];
      const tier = (car.parts[e.slot] ?? 0) + 1;
      if (tier > slot.tiers) return false;
      const prices = has(r.cars, car.car) && has(r.cars[car.car].parts, e.slot) ? r.cars[car.car].parts[e.slot] : undefined;
      const price = prices?.[tier - 1];
      if (price === undefined || s.cash < price) return false;
      s.cash -= price;
      car.parts[e.slot] = tier;
      for (const x of slot.excludes) delete car.parts[x];
      return true;
    }
    case "look": {
      if (!car || !has(r.cosmetics, e.slot)) return false;
      const cos = r.cosmetics[e.slot];
      if (s.cash < cos.price) return false;
      s.cash -= cos.price;
      car.cosmetics[e.slot] = ((car.cosmetics[e.slot] ?? 0) + 1) % cos.styles;
      return true;
    }
    case "paint": {
      const p = e.paint;
      if (!car || !Array.isArray(p) || p.length !== 4) return false;
      if (!inRange(p[0], r.colors) || !inRange(p[1], r.finishes.length) || !inRange(p[2], r.decals.length) || !inRange(p[3], r.decalColors)) return false;
      const cost = paintCost(r, car, p);
      if (cost === 0 || s.cash < cost) return false;
      s.cash -= cost;
      car.paint = p[0];
      car.cosmetics.finish = p[1];
      car.cosmetics.decal = p[2];
      car.cosmetics.decalColor = p[3];
      return true;
    }
    case "sell": {
      if (!car || s.cars.length < 2) return false;
      s.cash += saleValue(r, car);
      removeCar(s, car.uid);
      return true;
    }
    case "race": {
      if (!car) return false;
      const rival = r.rivals.find((x) => x.id === e.rival);
      if (!rival || !unlocked(r, s, rival.id)) return false;
      if (e.pink && !s.beaten.includes(rival.id)) return false;
      if (s.lastRaceT !== 0 && e.t - s.lastRaceT < r.minRaceGapMs) return false;
      s.lastRaceT = e.t;
      if (!e.won) {
        if (e.pink && s.cars.length > 1) removeCar(s, car.uid);
        return true;
      }
      const first = !s.beaten.includes(rival.id);
      s.cash += e.pink ? 0 : first ? rival.payout : Math.floor(rival.payout / 2);
      if (first) s.beaten.push(rival.id);
      if ((rival.boss && first) || e.pink) {
        const prize = addCar(s, rival.car, rival.paint);
        Object.assign(prize.parts, rival.parts);
        Object.assign(prize.cosmetics, rival.cosmetics);
      }
      return true;
    }
    default:
      return false;
  }
}

/** Untrusted JSON -> Entry, or null if it isn't shaped like one. */
export function sanitize(raw: unknown): Entry | null {
  if (typeof raw !== "object" || raw === null) return null;
  const o = raw as Record<string, unknown>;
  const str = (k: string) => (o[k] === undefined ? undefined : typeof o[k] === "string" && (o[k] as string).length <= 64 ? (o[k] as string) : null);
  if (typeof o.type !== "string" || typeof o.t !== "number" || !Number.isFinite(o.t)) return null;
  const e: Entry = { type: o.type, t: o.t };
  for (const k of ["car", "uid", "slot", "rival"] as const) {
    const v = str(k);
    if (v === null) return null;
    if (v !== undefined) e[k] = v;
  }
  if (o.won !== undefined) e.won = o.won === true;
  if (o.pink !== undefined) e.pink = o.pink === true;
  if (o.paint !== undefined) {
    if (!Array.isArray(o.paint) || o.paint.length !== 4 || !o.paint.every((v) => Number.isInteger(v))) return null;
    e.paint = o.paint as number[];
  }
  return e;
}

/** Cash a pre-ledger save may bring in on its first sync. */
export const LEGACY_CASH_CAP = 250_000;

/**
 * A pre-ledger save, imported once when an account is created: only known cars, parts, styles
 * and rivals survive, and cash is capped.
 */
export function importLegacy(r: Rules, raw: unknown): State {
  const s = emptyState();
  if (typeof raw !== "object" || raw === null) return s;
  const o = raw as Record<string, unknown>;
  const cash = typeof o.cash === "number" && Number.isFinite(o.cash) ? Math.floor(o.cash) : 0;
  s.cash = Math.max(0, Math.min(LEGACY_CASH_CAP, cash));
  if (Array.isArray(o.beaten)) s.beaten = r.rivals.map((x) => x.id).filter((id) => (o.beaten as unknown[]).includes(id));
  let maxUid = 0;
  if (Array.isArray(o.cars)) {
    for (const c of o.cars.slice(0, 60)) {
      if (typeof c !== "object" || c === null) continue;
      const cc = c as Record<string, unknown>;
      if (typeof cc.uid !== "string" || !/^c\d{1,6}$/.test(cc.uid) || typeof cc.car !== "string" || !has(r.cars, cc.car)) continue;
      if (s.cars.some((x) => x.uid === cc.uid)) continue;
      const car: Car = { uid: cc.uid, car: cc.car, paint: inRange(cc.paint as number, r.colors) ? (cc.paint as number) : r.cars[cc.car].paint, parts: {}, cosmetics: {} };
      for (const [k, v] of Object.entries((cc.parts ?? {}) as Record<string, unknown>)) {
        if (has(r.slots, k) && Number.isInteger(v) && (v as number) >= 1 && (v as number) <= r.slots[k].tiers) car.parts[k] = v as number;
      }
      for (const [k, v] of Object.entries((cc.cosmetics ?? {}) as Record<string, unknown>)) {
        const n = has(r.cosmetics, k) ? r.cosmetics[k].styles : k === "finish" ? r.finishes.length : k === "decal" ? r.decals.length : k === "decalColor" ? r.decalColors : 0;
        if (inRange(v as number, n)) car.cosmetics[k] = v as number;
      }
      s.cars.push(car);
      maxUid = Math.max(maxUid, parseInt(cc.uid.slice(1), 10));
    }
  }
  s.nextUid = maxUid + 1;
  return s;
}
