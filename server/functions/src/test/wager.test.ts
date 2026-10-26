import assert from "node:assert/strict";
import { test } from "node:test";
import { State } from "../ledger";
import { cannotStake, escrow, parseWager, payout, refund, wagerKey } from "../wager";

const garage = (cash: number, cars: string[]): State => ({
  cash, nextUid: cars.length + 1, lastRaceT: 0, beaten: [],
  cars: cars.map((car, i) => ({ uid: `c${i + 1}`, car, paint: 0, parts: { intake: 1 }, cosmetics: {}, owned: { intake: 2 } })),
});

test("wagers parse strictly", () => {
  assert.deepEqual(parseWager(undefined), { kind: "none" });
  assert.deepEqual(parseWager({ kind: "cash", amount: 5000 }), { kind: "cash", amount: 5000 });
  for (const bad of [{ kind: "cash", amount: 50 }, { kind: "cash", amount: 1.5 }, { kind: "cash", amount: -5000 }, { kind: "cash" }, { kind: "car" }, "pink", 7]) {
    assert.equal(parseWager(bad), null, JSON.stringify(bad));
  }
  assert.notEqual(wagerKey({ kind: "cash", amount: 1000 }), wagerKey({ kind: "cash", amount: 2000 }), "different stakes never meet");
});

test("you can only stake what you have, and never your last car", () => {
  assert.equal(cannotStake(garage(5000, ["kite"]), { kind: "cash", amount: 5000 }, "c1"), null);
  assert.match(cannotStake(garage(4999, ["kite"]), { kind: "cash", amount: 5000 }, "c1")!, /not enough/);
  assert.match(cannotStake(garage(-10, ["kite", "mammoth"]), { kind: "cash", amount: 100 }, "c1")!, /negative/);
  assert.match(cannotStake(garage(-10, ["kite", "mammoth"]), { kind: "pink" }, "c1")!, /negative/);
  assert.match(cannotStake(garage(0, ["kite"]), { kind: "pink" }, "c1")!, /last car/);
  assert.equal(cannotStake(garage(0, ["kite", "mammoth"]), { kind: "pink" }, "c2"), null);
  const busy = garage(0, ["kite", "mammoth", "tsunami"]);
  busy.staked = ["c1"];
  assert.match(cannotStake(busy, { kind: "pink" }, "c2")!, /already/);
  assert.equal(cannotStake(garage(-5, ["kite"]), { kind: "none" }, "c1"), null, "fun races are always fine");
});

test("cash: escrow, then the winner takes both stakes; a refund puts them back", () => {
  const w = { kind: "cash", amount: 5000 } as const;
  const a = garage(8000, ["kite"]);
  const b = garage(6000, ["kite"]);
  escrow(a, w, "c1");
  escrow(b, w, "c1");
  assert.deepEqual([a.cash, b.cash], [3000, 1000]);
  payout(a, b, w, "c1", "c1");
  assert.deepEqual([a.cash, b.cash], [13000, 1000], "money is conserved: 14000 in, 14000 out");
  const c = garage(5000, ["kite"]);
  escrow(c, w, "c1");
  refund(c, w, "c1");
  assert.equal(c.cash, 5000);
});

test("pink slip: the loser's car (parts and all) moves to the winner under a new uid", () => {
  const w = { kind: "pink" } as const;
  const a = garage(0, ["kite", "tsunami"]);
  const b = garage(0, ["mammoth", "kestrel"]);
  escrow(a, w, "c1");
  escrow(b, w, "c1");
  assert.deepEqual(b.staked, ["c1"]);
  const won = payout(a, b, w, "c1", "c1")!;
  assert.equal(won.car, "mammoth");
  assert.equal(won.uid, "c3");
  assert.deepEqual(won.parts, { intake: 1 });
  assert.deepEqual(a.cars.map((c) => c.car), ["kite", "tsunami", "mammoth"]);
  assert.deepEqual(b.cars.map((c) => c.car), ["kestrel"]);
  assert.deepEqual([a.staked, b.staked], [[], []], "nothing left on the line");
  assert.equal(a.nextUid, 4);
});
