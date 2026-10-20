/**
 * Daily rewards: one claim per UTC day. Claiming on consecutive days walks a seven-day cycle of
 * growing rewards; missing a day starts it over. Cash grows with your level, and day seven also
 * gives XP (which can level you up, paying that level's cash too). The server's clock decides.
 *
 * Firestore: players/{uid}.daily = { last: "2026-10-20", streak: n }.
 */
import { getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { addXp, levelOf, State } from "./ledger";
import { rules } from "./race";
import { dayEnd, dayKey } from "./tournaments";

/** Cash for days 1..7 of the cycle, before the level bonus. */
export const CASH = [1_000, 1_500, 2_000, 3_000, 4_000, 6_000, 10_000];
/** Extra cash per level, every day. */
export const PER_LEVEL = 200;
/** XP on day seven. */
export const DAY7_XP = 250;
const DAY_MS = 86_400_000;

// --- pure, tested -----------------------------------------------------------------------------

/** The streak a claim today would make: one more after yesterday's claim, else a fresh start. */
export function nextStreak(last: string | null, streak: number, today: string): number {
  if (last === today) return streak;
  const yesterday = dayKey(Date.parse(`${today}T00:00:00Z`) - DAY_MS);
  return last === yesterday ? streak + 1 : 1;
}

/** Day 1..7 of the cycle for a streak. */
export const cycleDay = (streak: number) => ((Math.max(1, streak) - 1) % 7) + 1;

export function reward(day: number, level: number): { cash: number; xp: number } {
  return { cash: CASH[day - 1] + PER_LEVEL * level, xp: day === 7 ? DAY7_XP : 0 };
}

// --- function ---------------------------------------------------------------------------------

/** { claim: false } just looks; { claim: true } takes today's reward if it's waiting. */
export const daily = onCall({ maxInstances: 10 }, async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  const uid = req.auth.uid;
  const claim = (req.data ?? {}).claim === true;
  const now = Date.now();
  const today = dayKey(now);
  const ref = getFirestore().collection("players").doc(uid);
  return getFirestore().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const state = snap.data()?.state as State | undefined;
    if (!state) throw new HttpsError("failed-precondition", "sync your garage first");
    const d = (snap.data()?.daily ?? {}) as { last?: string; streak?: number };
    const ready = d.last !== today;
    const streak = ready ? nextStreak(d.last ?? null, d.streak ?? 0, today) : d.streak ?? 1;
    const day = cycleDay(streak);
    const level = levelOf(rules.xp, state.xp ?? 0);
    const view = {
      ready, streak, day, nextAt: dayEnd(today), now,
      week: Array.from({ length: 7 }, (_, i) => reward(i + 1, level)),
    };
    if (!claim) return { ...view, claimed: null };
    if (!ready) throw new HttpsError("already-exists", "already claimed today");
    const r = reward(day, level);
    state.cash += r.cash;
    if (r.xp) addXp(rules, state, r.xp);
    tx.update(ref, { state, daily: { last: today, streak } });
    return { ...view, ready: false, claimed: r };
  });
});
