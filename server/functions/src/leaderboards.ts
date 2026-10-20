/**
 * Leaderboards: the quickest server-checked ETs per class and distance, the most online wins
 * (this week and all time), and the top crews.
 *
 * Every clean run the server replays (online races, tournament passes) is appended to runs/;
 * online wins count up in stats/{uid}. Both are plain writes, so they slot into the race and
 * tournament transactions without extra reads. A board takes the quickest few hundred runs,
 * keeps each racer's best, and looks the names and crew tags up as it's read.
 */
import { FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { handleOf, weekKey } from "./crews";

export const BOARD_SIZE = 25;
/** How many runs a board reads to find BOARD_SIZE distinct racers. */
const SCAN = 400;
export const CLASSES = ["STREET", "SPORT", "PRO", "OUTLAW"];
export type Dist = "eighth" | "quarter";

export interface RunRow { uid: string; et: number; mph: number; car: string; paint: number; at: number }

// --- pure, tested -----------------------------------------------------------------------------

/** Each racer's best run, quickest first (an earlier run wins a tie). */
export function bestPerRacer<T extends RunRow>(rows: T[], limit = BOARD_SIZE): T[] {
  const best = new Map<string, T>();
  for (const r of rows) {
    const b = best.get(r.uid);
    if (!b || r.et < b.et || (r.et === b.et && r.at < b.at)) best.set(r.uid, r);
  }
  return [...best.values()].sort((a, b) => a.et - b.et || a.at - b.at).slice(0, limit);
}

// --- writes, from inside other transactions ---------------------------------------------------

const db = () => getFirestore();

/** A clean, server-replayed run for the ET boards. */
export function recordRun(tx: Transaction, row: RunRow & { cls: string; dist: Dist; track: string; src: "online" | "tournament" }) {
  if (!(row.et > 0) || !CLASSES.includes(row.cls)) return;
  tx.set(db().collection("runs").doc(), row);
}

/** An online win, for the wins boards. */
export function recordWinStat(tx: Transaction, uid: string) {
  tx.set(db().collection("stats").doc(uid), { wins: FieldValue.increment(1), weekWins: { [weekKey()]: FieldValue.increment(1) } }, { merge: true });
}

// --- the board --------------------------------------------------------------------------------

/** Names and crew tags as they are now. */
async function people(uids: string[]) {
  if (!uids.length) return new Map<string, { name: string; tag: string | null }>();
  const players = await db().getAll(...uids.map((u) => db().collection("players").doc(u)));
  const crewIds = [...new Set(players.map((p) => p.data()?.crew as string | undefined).filter((c): c is string => !!c))];
  const crews = crewIds.length ? await db().getAll(...crewIds.map((c) => db().collection("crews").doc(c))) : [];
  const tags = new Map(crews.map((c) => [c.id, (c.data()?.tag as string | undefined) ?? null]));
  return new Map(players.map((p) => [p.id, { name: handleOf(p.id, p.data()), tag: tags.get(p.data()?.crew) ?? null }]));
}

/**
 * board: "et" (with cls and dist), "wins" or "crews" (with period "week" or "all").
 * Returns the rows and, for the racer boards, your own row and place when you're on it.
 */
export const leaderboard = onCall({ maxInstances: 10 }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const uid = req.auth.uid;
  const d = (req.data ?? {}) as Record<string, unknown>;
  const week = weekKey();
  const period = d.period === "all" ? "all" : "week";

  if (d.board === "et") {
    const cls = typeof d.cls === "string" && CLASSES.includes(d.cls) ? d.cls : null;
    const dist: Dist = d.dist === "eighth" ? "eighth" : "quarter";
    if (!cls) throw new HttpsError("invalid-argument", "cls");
    const snap = await db().collection("runs").where("cls", "==", cls).where("dist", "==", dist).orderBy("et").limit(SCAN).get();
    const all = bestPerRacer(snap.docs.map((r) => r.data() as RunRow), SCAN);
    const top = all.slice(0, BOARD_SIZE);
    const who = await people(top.map((r) => r.uid).concat(top.some((r) => r.uid === uid) ? [] : [uid]));
    const place = all.findIndex((r) => r.uid === uid);
    const row = (r: RunRow) => ({ uid: r.uid, ...who.get(r.uid), et: r.et, mph: r.mph, car: r.car, paint: r.paint });
    return { board: "et", cls, dist, rows: top.map(row), mine: place >= 0 ? { place: place + 1, ...row(all[place]) } : null };
  }

  if (d.board === "wins") {
    const field = period === "week" ? `weekWins.${week}` : "wins";
    const snap = await db().collection("stats").where(field, ">", 0).orderBy(field, "desc").limit(BOARD_SIZE).get();
    const rows = snap.docs.map((s) => ({ uid: s.id, wins: (period === "week" ? s.data().weekWins?.[week] : s.data().wins) ?? 0 }));
    const who = await people(rows.map((r) => r.uid));
    const place = rows.findIndex((r) => r.uid === uid);
    return { board: "wins", period, week, rows: rows.map((r) => ({ ...r, ...who.get(r.uid) })), mine: place >= 0 ? { place: place + 1, ...rows[place] } : null };
  }

  if (d.board === "crews") {
    const field = period === "week" ? `war.${week}.total` : "wins";
    const snap = await db().collection("crews").where(field, ">", 0).orderBy(field, "desc").limit(BOARD_SIZE).get();
    const me = (await db().collection("players").doc(uid).get()).data()?.crew as string | undefined;
    const rows = snap.docs.map((c) => {
      const x = c.data();
      return { id: c.id, name: x.name, tag: x.tag, emblem: x.emblem, members: x.memberCount ?? 0, wins: (period === "week" ? x.war?.[week]?.total : x.wins) ?? 0 };
    });
    const place = rows.findIndex((r) => r.id === me);
    return { board: "crews", period, week, rows, mine: place >= 0 ? { place: place + 1, ...rows[place] } : null };
  }

  throw new HttpsError("invalid-argument", "board: et, wins or crews");
});
