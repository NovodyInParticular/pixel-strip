import assert from "node:assert/strict";
import { test } from "node:test";
import { bestPerRacer } from "../leaderboards";

test("one row per racer: their best, quickest first, the earlier run on a tie", () => {
  const r = (uid: string, et: number, at: number) => ({ uid, et, mph: 100, car: "kite", paint: 0, at });
  const rows = [r("a", 12.5, 1), r("b", 12.1, 2), r("a", 12.0, 3), r("c", 12.1, 1), r("b", 12.4, 4)];
  assert.deepEqual(bestPerRacer(rows).map((x) => [x.uid, x.et]), [["a", 12.0], ["c", 12.1], ["b", 12.1]]);
  assert.equal(bestPerRacer(rows, 2).length, 2);
  assert.deepEqual(bestPerRacer([]), []);
});
