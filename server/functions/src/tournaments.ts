/**
 * Tournaments: every day (UTC) each class has a time-trial tournament on a track picked by the
 * date. Pay the entry fee with a car of that class (the server rates it), get MAX_RUNS timed
 * quarter-mile passes (each one replayed by the server's physics), and your best ET counts.
 * When the day is over the top ten split the pool: every entry fee plus the house's seed.
 *
 * Firestore: tournaments/{id} (the event, its pool and, once settled, the results) and
 * tournaments/{id}/entries/{uid} (the car as entered, runs used, best run).
 */
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { SPLITS_M } from "./physics/consts";
import { perfectPass, replay } from "./physics/vehicle";
import { addXp, Car, State } from "./ledger";
import { effective, rules, sanitizeEvents, START_X, TRACKS } from "./race";
import { handleOf } from "./crews";
import { classFor } from "./classes";
import { recordRun } from "./leaderboards";

export { classFor };

export const CLASSES = ["STREET", "SPORT", "PRO", "OUTLAW"] as const;
export type Cls = (typeof CLASSES)[number];
/** Entry fee by class; the house seeds every pool with SEED_FEES entry fees. */
export const FEES: Record<Cls, number> = { STREET: 1_000, SPORT: 4_000, PRO: 15_000, OUTLAW: 50_000 };
export const SEED_FEES = 10;
export const MAX_RUNS = 5;
/** Percent of the pool for 1st..10th; with fewer finishers the places there are split it all. */
export const SHARES = [40, 20, 12, 8, 6, 5, 3, 2, 2, 2];
const DAY_MS = 86_400_000;

// --- pure, tested -----------------------------------------------------------------------------

/** "2026-10-20" for the UTC day containing t. */
export const dayKey = (t: number) => new Date(Math.floor(t / DAY_MS) * DAY_MS).toISOString().slice(0, 10);
export const dayEnd = (day: string) => Date.parse(`${day}T00:00:00Z`) + DAY_MS;

/** The same track for everyone all day: a small hash of the day and class. */
export function trackFor(day: string, cls: string, tracks: string[]): string {
  let h = 2166136261;
  for (const ch of `${day}/${cls}`) h = Math.imul(h ^ ch.charCodeAt(0), 16777619) >>> 0;
  return tracks[h % tracks.length];
}

export const tournamentId = (day: string, cls: string) => `${day}-${cls}`;

export function parseId(id: unknown): { day: string; cls: Cls } | null {
  if (typeof id !== "string") return null;
  const m = /^(\d{4}-\d{2}-\d{2})-([A-Z]+)$/.exec(id);
  if (!m || !(CLASSES as readonly string[]).includes(m[2]) || Number.isNaN(Date.parse(m[1]))) return null;
  return { day: m[1], cls: m[2] as Cls };
}

export const seedFor = (cls: Cls) => FEES[cls] * SEED_FEES;

export interface Best { et: number; mph: number; rt: number; at: number }

/** Finishing order: quickest best ET, and the earlier run on a tie. Entries without a clean run don't place. */
export function standings<T extends { best: Best | null }>(entries: T[]): T[] {
  return entries.filter((e) => e.best).sort((a, b) => a.best!.et - b.best!.et || a.best!.at - b.best!.at);
}

/** Whole-dollar prizes for [places] finishers; any rounding stays with the house. */
export function prizes(pool: number, places: number): number[] {
  const n = Math.min(places, SHARES.length);
  if (n <= 0) return [];
  const total = SHARES.slice(0, n).reduce((a, b) => a + b, 0);
  return SHARES.slice(0, n).map((s) => Math.floor((pool * s) / total));   // whole numbers: exact up to 2^53
}

// --- functions --------------------------------------------------------------------------------

const db = () => getFirestore();
const tRef = (id: string) => db().collection("tournaments").doc(id);
const playerRef = (uid: string) => db().collection("players").doc(uid);

function needAuth(req: { auth?: { uid: string } }): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  return req.auth.uid;
}

function eventOf(day: string, cls: Cls) {
  const track = trackFor(day, cls, TRACKS.map((t) => t.id));
  return { id: tournamentId(day, cls), day, cls, track, fee: FEES[cls], seed: seedFor(cls), ends: dayEnd(day) };
}

/** Pays out a finished tournament once: ranks the entries and credits the top ten's garages. */
async function settle(id: string, now = Date.now()) {
  const p = parseId(id);
  if (!p || now < dayEnd(p.day)) return;
  await db().runTransaction(async (tx) => {
    const doc = await tx.get(tRef(id));
    if (!doc.exists || doc.data()!.settled) return;
    const t = doc.data()!;
    const entries = (await tx.get(tRef(id).collection("entries"))).docs.map((d) => ({ uid: d.id, ...(d.data() as { name: string; tag: string | null; car: string; best: Best | null }) }));
    const order = standings(entries);
    const pay = prizes(t.pool, order.length);
    const players = await Promise.all(pay.map((_, i) => tx.get(playerRef(order[i].uid))));
    const results = order.slice(0, 20).map((e, i) => ({ uid: e.uid, name: e.name ?? "?", tag: e.tag ?? null, car: e.car ?? null, et: e.best!.et, mph: e.best!.mph, prize: pay[i] ?? 0 }));
    pay.forEach((amount, i) => {
      const snap = players[i];
      if (!snap.exists || amount <= 0) return;
      const state = snap.data()!.state as State;
      state.cash += amount;
      tx.update(snap.ref, { state });
    });
    tx.update(tRef(id), { settled: now, results });
  });
}

function publicEntry(uid: string, e: FirebaseFirestore.DocumentData) {
  return { uid, name: e.name, tag: e.tag ?? null, car: e.car, paint: e.paint ?? 0, runs: e.runs ?? 0, best: e.best ?? null };
}

/** Today's four tournaments (with the leaderboard and your entry) and yesterday's results. */
export const tournaments = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const now = Date.now();
  const today = dayKey(now);
  const yesterday = dayKey(now - DAY_MS);
  await Promise.all(CLASSES.map((c) => settle(tournamentId(yesterday, c), now)));
  const view = async (day: string, cls: Cls) => {
    const ev = eventOf(day, cls);
    const doc = (await tRef(ev.id).get()).data();
    const top = (await tRef(ev.id).collection("entries").where("best", "!=", null).get()).docs
      .map((d) => publicEntry(d.id, d.data()));
    const board = standings(top).slice(0, 10);
    const mine = await tRef(ev.id).collection("entries").doc(uid).get();
    const order = standings(top);
    return {
      ...ev, pool: doc?.pool ?? ev.seed, entrants: doc?.entrants ?? 0, settled: doc?.settled ?? null, results: doc?.results ?? null,
      board, prizes: prizes(doc?.pool ?? ev.seed, Math.max(1, order.length)),
      mine: mine.exists ? { ...publicEntry(uid, mine.data()!), rank: order.findIndex((e) => e.uid === uid) + 1 } : null,
    };
  };
  return {
    now,
    today: await Promise.all(CLASSES.map((c) => view(today, c))),
    yesterday: await Promise.all(CLASSES.map((c) => view(yesterday, c))),
  };
});

/** Enter today's tournament for the car's class: pays the fee from your garage cash. */
export const enterTournament = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const p = parseId(d.id);
  if (!p || typeof d.car !== "string") throw new HttpsError("invalid-argument", "id, car");
  const now = Date.now();
  if (p.day !== dayKey(now)) throw new HttpsError("failed-precondition", "that tournament isn't running");
  const ev = eventOf(p.day, p.cls);
  return db().runTransaction(async (tx) => {
    const player = await tx.get(playerRef(uid));
    const state = player.data()?.state as State | undefined;
    const car = state?.cars.find((c) => c.uid === d.car) as Car | undefined;
    if (!state || !car) throw new HttpsError("failed-precondition", "sync your garage first: the server doesn't know that car");
    if (state.staked?.includes(car.uid)) throw new HttpsError("failed-precondition", "that car is on the line in a race");
    const entryRef = tRef(ev.id).collection("entries").doc(uid);
    if ((await tx.get(entryRef)).exists) throw new HttpsError("already-exists", "you're already in this one");
    const tDoc = await tx.get(tRef(ev.id));
    const quarter = perfectPass(effective(car, d.tune)).quarter;
    const cls = classFor(quarter);
    if (cls !== p.cls) throw new HttpsError("failed-precondition", `that car rates ${cls}, not ${p.cls}`);
    if (state.cash < ev.fee) throw new HttpsError("failed-precondition", "not enough cash for the entry fee");
    const badge = player.data()?.crew ? (await tx.get(db().collection("crews").doc(player.data()!.crew))).data() : null;
    // every read is done: now the writes
    state.cash -= ev.fee;
    tx.update(player.ref, { state });
    tx.set(entryRef, {
      name: handleOf(uid, player.data()), tag: badge?.tag ?? null, carUid: car.uid, car: car.car, paint: car.paint,
      build: car, tune: d.tune ?? null, runs: 0, best: null, entered: now,
    });
    if (tDoc.exists) tx.update(tRef(ev.id), { pool: FieldValue.increment(ev.fee), entrants: FieldValue.increment(1) });
    else tx.set(tRef(ev.id), { ...ev, pool: ev.seed + ev.fee, entrants: 1, settled: null, results: null });
    return { ok: true, cash: state.cash, fee: ev.fee };
  });
});

/** One timed pass: the server replays it on the tournament's track with the car as entered. */
export const tournamentRun = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const p = parseId(d.id);
  if (!p) throw new HttpsError("invalid-argument", "id");
  const events = sanitizeEvents(d.events);
  const now = Date.now();
  if (now >= dayEnd(p.day)) throw new HttpsError("failed-precondition", "this tournament is over");
  const ev = eventOf(p.day, p.cls);
  const grip = TRACKS.find((t) => t.id === ev.track)!.grip;
  const entryRef = tRef(ev.id).collection("entries").doc(uid);
  return db().runTransaction(async (tx) => {
    const entry = await tx.get(entryRef);
    const player = await tx.get(playerRef(uid));
    if (!entry.exists) throw new HttpsError("failed-precondition", "enter first");
    const e = entry.data()!;
    if (e.runs >= MAX_RUNS) throw new HttpsError("resource-exhausted", "no runs left");
    const r = replay(effective(e.build as Car, e.tune), events, { trackGrip: grip, startX: START_X, finishM: SPLITS_M[4] });
    const et = r.splits[4] >= 0 ? r.splits[4] : null;
    const red = r.leaveTime !== null && r.leaveTime < 0;
    const run = { et: red ? null : et, mph: r.splitSpeed[4] * 2.23694, rt: r.leaveTime, red };
    const best: Best | null = e.best ?? null;
    const better = run.et !== null && run.rt !== null && (!best || run.et < best.et);
    const next = better ? { et: run.et!, mph: run.mph, rt: run.rt!, at: now } : best;
    tx.update(entryRef, { runs: e.runs + 1, best: next, last: { ...run, at: now } });
    if (run.et !== null && run.rt !== null && player.exists) {
      const state = player.data()!.state as State;
      addXp(rules, state, rules.xp.tournamentRun);
      tx.update(player.ref, { state });
    }
    if (run.et !== null && run.rt !== null) {
      recordRun(tx, { uid, et: run.et, mph: run.mph, car: e.car, paint: e.paint ?? 0, at: now, cls: p.cls, dist: "quarter", track: ev.track, src: "tournament" });
    }
    return { run, best: next, runsLeft: MAX_RUNS - e.runs - 1, newBest: better };
  });
});
