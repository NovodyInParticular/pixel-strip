import assert from "node:assert/strict";
import { test } from "node:test";
import { emptyState } from "../ledger";
import { classFor, dayEnd, dayKey, givePrizeCar, parseId, prizeCarFor, PRIZE_CARS, prizes, standings, trackFor } from "../tournaments";

test("days are UTC and end at midnight", () => {
  const t = Date.parse("2026-10-20T23:59:59Z");
  assert.equal(dayKey(t), "2026-10-20");
  assert.equal(dayKey(t + 1000), "2026-10-21");
  assert.equal(dayEnd("2026-10-20"), Date.parse("2026-10-21T00:00:00Z"));
});

test("ids are checked", () => {
  assert.deepEqual(parseId("2026-10-20-SPORT"), { day: "2026-10-20", cls: "SPORT" });
  for (const bad of ["2026-10-20-ROCKET", "20-10-2026-SPORT", "2026-10-20", 7, null]) assert.equal(parseId(bad), null, String(bad));
});

test("everyone gets the same track all day, and the classes differ", () => {
  const tracks = ["a", "b", "c", "d", "e", "f"];
  assert.equal(trackFor("2026-10-20", "PRO", tracks), trackFor("2026-10-20", "PRO", tracks));
  const week = Array.from({ length: 7 }, (_, i) => trackFor(`2026-10-${20 + i}`, "PRO", tracks));
  assert.ok(new Set(week).size > 2, week.join(","));
});

test("classes follow economy.json", () => {
  assert.equal(classFor(12.0), "OUTLAW");
  assert.equal(classFor(12.5), "OUTLAW");
  assert.equal(classFor(13.2), "PRO");
  assert.equal(classFor(14.0), "SPORT");
  assert.equal(classFor(16.0), "STREET");
});

test("prizes never pay out more than the pool", () => {
  for (const pool of [10_000, 123_457, 1_000_000]) {
    for (let n = 1; n <= 14; n++) {
      const p = prizes(pool, n);
      assert.equal(p.length, Math.min(n, 10));
      assert.ok(p.reduce((a, b) => a + b, 0) <= pool);
      assert.ok(p.reduce((a, b) => a + b, 0) >= pool - 10, "rounding only");
      for (let i = 1; i < p.length; i++) assert.ok(p[i] <= p[i - 1]);
    }
  }
  assert.deepEqual(prizes(10_000, 1), [10_000], "a lone finisher takes it all");
  assert.deepEqual(prizes(10_000, 0), []);
});

test("quickest ET wins, earlier run breaks a tie, no clean run doesn't place", () => {
  const e = (et: number | null, at: number) => ({ id: `${et}@${at}`, best: et === null ? null : { et, mph: 100, rt: 0.1, at } });
  const order = standings([e(11.2, 5), e(null, 1), e(11.1, 9), e(11.2, 3)]).map((x) => x.id);
  assert.deepEqual(order, ["11.1@9", "11.2@3", "11.2@5"]);
});

test("the day's winner drives a rare car home, once", () => {
  assert.equal(prizeCarFor("2026-10-25", "STREET"), null, "street pays cash only");
  assert.equal(prizeCarFor("2026-10-25", "PRO"), "kirin_r");
  const week = Array.from({ length: 14 }, (_, i) => prizeCarFor(`2026-10-${10 + i}`, "OUTLAW"));
  assert.deepEqual([...new Set(week)].sort(), [...PRIZE_CARS.OUTLAW].sort(), "the outlaw prize rotates by day");
  const s = emptyState();
  assert.equal(givePrizeCar(s, "kirin_r", 6), true);
  assert.equal(givePrizeCar(s, "kirin_r", 6), false, "not a second one");
  assert.deepEqual(s.cars.map((c) => [c.uid, c.car, c.paint]), [["c1", "kirin_r", 6]]);
});
