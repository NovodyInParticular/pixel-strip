/**
 * Online races. matchmake pairs two players with similar builds and opens a race room in the
 * Realtime Database; during the race the clients stream their positions to the room (rules in
 * database.rules.json); when a client finishes it submits its RunRecorder events and the server
 * replays them with its own physics, so the result can't be faked. raceResult settles the race
 * (or a forfeit when the other side never finishes).
 */
import { randomBytes } from "node:crypto";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { CarDef, parseCar } from "./physics/car";
import { SPLITS_M } from "./physics/consts";
import { apply, Catalog, parseCatalog } from "./physics/parts";
import { tuneFrom } from "./physics/tune";
import { MAX_STEPS, perfectPass, replay, RunEvent } from "./physics/vehicle";
import { Car, State } from "./ledger";

const data = join(__dirname, "data");
const catalog: Catalog = parseCatalog(JSON.parse(readFileSync(join(data, "parts.json"), "utf8")));
const cars = new Map<string, CarDef>(
  readdirSync(join(data, "cars")).filter((f) => f.endsWith(".json")).map((f) => {
    const c = parseCar(JSON.parse(readFileSync(join(data, "cars", f), "utf8")));
    return [c.id, c];
  }),
);
/** Tracks anyone can race on (Joe's San Antonio is his alone), with their surface grip. */
const TRACKS = ["neon_city", "desert", "port", "coast", "airfield", "speedway"].map((id) => {
  const t = JSON.parse(readFileSync(join(data, "tracks", `${id}.json`), "utf8"));
  return { id, grip: typeof t.trackGrip === "number" ? t.trackGrip : 1.25 };
});

/** Matchmaking rating as the game shows it (Economy.rating): 13.0 s -> 500, a tenth is 10 points. */
export const rating = (quarter: number) => Math.max(0, Math.trunc((18 - quarter) * 100));
/** How far apart two ratings may be at first, and how much wider the window gets each second waited. */
const RATING_WINDOW = 40;
const WINDOW_GROWTH = 4;
const QUEUE_TTL_MS = 90_000;
/** A race with only one finished run is settled as a forfeit after this long. */
export const FORFEIT_MS = 150_000;
/** Where every run starts: the burnout box (RaceScreen.BURNOUT_X). */
const START_X = -9;
const MAX_EVENTS = 6000;

interface Entry {
  uid: string;
  carUid: string;
  car: Car;
  tune: unknown;
  rating: number;
  quarter: boolean;
  created: number;
}

/** The player's car as the server knows it (from the ledger state) and how it races with [tune]. */
async function carOf(uid: string, carUid: string): Promise<Car> {
  const snap = await getFirestore().collection("players").doc(uid).get();
  const state = snap.exists ? (snap.data()!.state as State) : null;
  const car = state?.cars.find((c) => c.uid === carUid);
  if (!car) throw new HttpsError("failed-precondition", "sync your garage first: the server doesn't know that car");
  return car;
}

export function effective(car: Car, tune: unknown): CarDef {
  const base = cars.get(car.car);
  if (!base) throw new HttpsError("failed-precondition", `unknown car ${car.car}`);
  return apply(base, catalog, car.parts, car.cosmetics, tuneFrom(tune));
}

const handle = (uid: string) => "RACER " + uid.slice(0, 4).toUpperCase();

/**
 * Join the queue with a car (and its tune), or get paired with someone already waiting whose
 * build rates close to yours. Returns { raceId } when paired, or { waiting: true }: the waiting
 * client then watches /matches/{uid} in the Realtime Database.
 */
export const matchmake = onCall({ maxInstances: 10 }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const uid = req.auth.uid;
  const d = (req.data ?? {}) as Record<string, unknown>;
  if (typeof d.car !== "string") throw new HttpsError("invalid-argument", "car");
  const quarter = d.quarter !== false;
  const car = await carOf(uid, d.car);
  const def = effective(car, d.tune);
  const me: Entry = { uid, carUid: d.car, car, tune: d.tune ?? null, rating: rating(perfectPass(def).quarter), quarter, created: Date.now() };

  const fs = getFirestore();
  const paired = await fs.runTransaction(async (tx) => {
    const now = Date.now();
    // one range filter only, so no composite index is needed; the distance is checked here
    const waiting = await tx.get(fs.collection("queue").where("created", ">", now - QUEUE_TTL_MS));
    let best: Entry | null = null;
    for (const doc of waiting.docs) {
      const e = doc.data() as Entry;
      if (e.uid === uid || e.quarter !== quarter) continue;
      const window = RATING_WINDOW + WINDOW_GROWTH * (now - e.created) / 1000;
      if (Math.abs(e.rating - me.rating) > window) continue;
      if (!best || e.created < best.created) best = e;
    }
    if (!best) {
      tx.set(fs.collection("queue").doc(uid), me);
      return null;
    }
    tx.delete(fs.collection("queue").doc(best.uid));
    tx.delete(fs.collection("queue").doc(uid));
    return best;
  });
  if (!paired) return { waiting: true, rating: me.rating };

  const raceId = randomBytes(9).toString("base64url");
  const track = TRACKS[Math.floor(Math.random() * TRACKS.length)];
  const player = (e: Entry) => ({
    name: handle(e.uid), car: e.car.car, paint: e.car.paint, parts: e.car.parts, cosmetics: e.car.cosmetics, rating: e.rating,
  });
  const room = {
    players: { [paired.uid]: player(paired), [uid]: player(me) },
    lanes: { [paired.uid]: 0, [uid]: 1 },
    track: track.id,
    quarter,
    /** The starter's wait between both staged and the first amber (Tree.start). */
    treeDelay: 0.7 + Math.random() * 0.9,
    created: Date.now(),
  };
  // the room is public to its two racers; the builds and tunes stay on the server for the replay
  await fs.collection("races").doc(raceId).set({
    ...room, uids: [paired.uid, uid], trackGrip: track.grip,
    entries: { [paired.uid]: { carUid: paired.carUid, car: paired.car, tune: paired.tune }, [uid]: { carUid: me.carUid, car: me.car, tune: me.tune } },
  });
  const db = getDatabase();
  await db.ref(`races/${raceId}`).set(room);
  await db.ref(`matches/${paired.uid}`).set(raceId);
  return { raceId, room };
});

/** Leave the queue. */
export const cancelMatch = onCall({ maxInstances: 10 }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  await getFirestore().collection("queue").doc(req.auth.uid).delete();
  await getDatabase().ref(`matches/${req.auth.uid}`).remove();
  return { ok: true };
});

interface Run { rt: number | null; et: number | null; mph: number; red: boolean; at: number }

function sanitizeEvents(raw: unknown): RunEvent[] {
  if (!Array.isArray(raw) || raw.length > MAX_EVENTS) throw new HttpsError("invalid-argument", "events");
  const out: RunEvent[] = [];
  for (const e of raw) {
    if (typeof e !== "object" || e === null) throw new HttpsError("invalid-argument", "event");
    const o = e as Record<string, unknown>;
    if (!Number.isInteger(o.s) || (o.s as number) < 0 || (o.s as number) > MAX_STEPS || typeof o.a !== "string") throw new HttpsError("invalid-argument", "event");
    out.push({ s: o.s as number, a: o.a, v: typeof o.v === "number" ? o.v : undefined, c: typeof o.c === "number" ? o.c : undefined });
  }
  return out;
}

/** Who won: a red light loses to a clean run; otherwise reaction plus ET; a dead heat goes to the lower ET. */
export function decide(a: Run, b: Run): 0 | 1 {
  const clean = (r: Run) => !r.red && r.et !== null;
  if (clean(a) !== clean(b)) return clean(a) ? 0 : 1;
  if (!clean(a)) return (a.rt ?? -99) >= (b.rt ?? -99) ? 0 : 1;      // both fouled: the later foul wins
  const ta = a.rt! + a.et!;
  const tb = b.rt! + b.et!;
  if (ta !== tb) return ta < tb ? 0 : 1;
  return a.et! <= b.et! ? 0 : 1;
}

async function settle(raceId: string): Promise<unknown | null> {
  const fs = getFirestore();
  return fs.runTransaction(async (tx) => {
    const ref = fs.collection("races").doc(raceId);
    const race = (await tx.get(ref)).data();
    if (!race) return null;
    if (race.result) return race.result;
    const runs = (race.runs ?? {}) as Record<string, Run>;
    const [ua, ub] = race.uids as string[];
    let result;
    if (runs[ua] && runs[ub]) {
      const w = decide(runs[ua], runs[ub]);
      result = { winner: w === 0 ? ua : ub, runs, forfeit: false };
    } else if ((runs[ua] || runs[ub]) && Date.now() - race.created > FORFEIT_MS) {
      result = { winner: runs[ua] ? ua : ub, runs, forfeit: true };
    } else return null;
    tx.update(ref, { result, settled: Date.now() });
    await getDatabase().ref(`races/${raceId}/result`).set(result);
    return result;
  });
}

/**
 * A finished run: the client's RunRecorder events. Replayed here with the car and tune the server
 * holds for this race; the client's own idea of its time is never used.
 */
export const submitRun = onCall({ maxInstances: 10 }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const uid = req.auth.uid;
  const d = (req.data ?? {}) as Record<string, unknown>;
  if (typeof d.raceId !== "string") throw new HttpsError("invalid-argument", "raceId");
  const events = sanitizeEvents(d.events);
  const fs = getFirestore();
  const ref = fs.collection("races").doc(d.raceId);
  const race = (await ref.get()).data();
  if (!race || !(race.uids as string[]).includes(uid)) throw new HttpsError("permission-denied", "not your race");
  if (race.runs?.[uid]) throw new HttpsError("already-exists", "run already in");
  const entry = race.entries[uid];
  const def = effective(entry.car, entry.tune);
  const finishM = SPLITS_M[race.quarter ? 4 : 2];
  const r = replay(def, events, { trackGrip: race.trackGrip, startX: START_X, finishM });
  const split = race.quarter ? 4 : 2;
  const run: Run = {
    rt: r.leaveTime,
    et: r.splits[split] >= 0 ? r.splits[split] : null,
    mph: r.splitSpeed[split] * 2.23694,
    red: r.leaveTime !== null && r.leaveTime < 0,
    at: Date.now(),
  };
  await ref.update({ [`runs.${uid}`]: run, [`events.${uid}`]: events.length });
  await getDatabase().ref(`races/${d.raceId}/runs/${uid}`).set(run);
  const result = await settle(d.raceId);
  return { run, result };
});

/** The result once both runs are in (or the forfeit, once the other side has had long enough). */
export const raceResult = onCall({ maxInstances: 10 }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const d = (req.data ?? {}) as Record<string, unknown>;
  if (typeof d.raceId !== "string") throw new HttpsError("invalid-argument", "raceId");
  const race = (await getFirestore().collection("races").doc(d.raceId).get()).data();
  if (!race || !(race.uids as string[]).includes(req.auth.uid)) throw new HttpsError("permission-denied", "not your race");
  return { result: await settle(d.raceId) };
});
