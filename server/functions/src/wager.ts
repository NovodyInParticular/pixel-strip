/**
 * Bets and pink slips on online races: what can be staked, and the money and car moves, as pure
 * functions on the two players' ledger states (race.ts runs them inside Firestore transactions).
 *
 * Protections: both players confirm before anything moves; the server takes the stakes (escrow)
 * at confirmation and pays out at the verdict; nobody bets cash they don't have (a balance driven
 * negative by a refund blocks betting) or a car that's their last, or one already on the line.
 */
import { Car, State } from "./ledger";

export type Wager = { kind: "none" } | { kind: "cash"; amount: number } | { kind: "pink" };

export const MIN_BET = 100;
/** Not a cap on betting: just the largest number the server will hold exactly. */
export const MAX_BET = 1_000_000_000_000;

/** Untrusted client input -> a wager, or null if it isn't one. */
export function parseWager(raw: unknown): Wager | null {
  if (raw === undefined || raw === null) return { kind: "none" };
  if (typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  if (o.kind === "none") return { kind: "none" };
  if (o.kind === "pink") return { kind: "pink" };
  if (o.kind === "cash" && Number.isInteger(o.amount) && (o.amount as number) >= MIN_BET && (o.amount as number) <= MAX_BET) {
    return { kind: "cash", amount: o.amount as number };
  }
  return null;
}

/** Players only meet others staking exactly the same. */
export const wagerKey = (w: Wager) => (w.kind === "cash" ? `cash:${w.amount}` : w.kind);

/** Why this player can't stake [w] with car [carUid], or null if they can. */
export function cannotStake(s: State, w: Wager, carUid: string): string | null {
  if (w.kind === "none") return null;
  if (s.cash < 0) return "your balance is negative: no betting until it's back above zero";
  if ((s.staked ?? []).length > 0) return "you already have a car on the line";
  if (w.kind === "cash" && s.cash < w.amount) return "not enough cash for that bet";
  if (w.kind === "pink") {
    if (!s.cars.some((c) => c.uid === carUid)) return "that car isn't in your garage";
    if (s.cars.length < 2) return "you can't race your last car for pink slips";
  }
  return null;
}

/** Both confirmed: take the stakes. The caller checks cannotStake first for both. */
export function escrow(s: State, w: Wager, carUid: string) {
  if (w.kind === "cash") s.cash -= w.amount;
  if (w.kind === "pink") s.staked = [...(s.staked ?? []), carUid];
}

/** The verdict: the winner gets both stakes (pink: the loser's car joins their garage). */
export function payout(winner: State, loser: State, w: Wager, winnerCar: string, loserCar: string): Car | null {
  if (w.kind === "cash") {
    winner.cash += w.amount * 2;
    return null;
  }
  if (w.kind !== "pink") return null;
  winner.staked = (winner.staked ?? []).filter((u) => u !== winnerCar);
  loser.staked = (loser.staked ?? []).filter((u) => u !== loserCar);
  const car = loser.cars.find((c) => c.uid === loserCar);
  if (!car) return null;
  loser.cars = loser.cars.filter((c) => c.uid !== loserCar);
  const won: Car = { ...car, uid: `c${winner.nextUid++}`, parts: { ...car.parts }, cosmetics: { ...car.cosmetics }, owned: { ...(car.owned ?? {}) } };
  winner.cars.push(won);
  return won;
}

/** No race happened (declined, timed out, nobody finished): everyone gets their stake back. */
export function refund(s: State, w: Wager, carUid: string) {
  if (w.kind === "cash") s.cash += w.amount;
  if (w.kind === "pink") s.staked = (s.staked ?? []).filter((u) => u !== carUid);
}
