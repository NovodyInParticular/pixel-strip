import assert from "node:assert/strict";
import { test } from "node:test";
import { CASH, cycleDay, nextStreak, PER_LEVEL, reward } from "../daily";

test("streaks: one more after yesterday, a fresh start after a gap, unchanged on the same day", () => {
  assert.equal(nextStreak(null, 0, "2026-10-20"), 1);
  assert.equal(nextStreak("2026-10-19", 4, "2026-10-20"), 5);
  assert.equal(nextStreak("2026-10-18", 4, "2026-10-20"), 1);
  assert.equal(nextStreak("2026-10-20", 4, "2026-10-20"), 4);
  assert.equal(nextStreak("2026-12-31", 2, "2027-01-01"), 3, "across the year");
  assert.equal(nextStreak("2026-02-28", 2, "2026-03-01"), 3, "across a short month");
});

test("the seven-day cycle repeats", () => {
  assert.deepEqual([1, 2, 7, 8, 14, 15].map(cycleDay), [1, 2, 7, 1, 7, 1]);
});

test("rewards grow through the week and with level; day seven adds XP", () => {
  assert.deepEqual(reward(1, 1), { cash: CASH[0] + PER_LEVEL, xp: 0 });
  assert.equal(reward(7, 10).xp, 250);
  for (let d = 2; d <= 7; d++) assert.ok(reward(d, 1).cash > reward(d - 1, 1).cash);
  assert.ok(reward(3, 20).cash > reward(3, 2).cash);
});
