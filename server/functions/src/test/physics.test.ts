import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { parseCar } from "../physics/car";
import { apply, parseCatalog } from "../physics/parts";
import { tuneFrom } from "../physics/tune";
import { replay } from "../physics/vehicle";

const root = join(__dirname, "../../../..");
const catalog = parseCatalog(JSON.parse(readFileSync(join(root, "assets/data/parts.json"), "utf8")));
const cases = JSON.parse(readFileSync(join(root, "shared/physics_cases.json"), "utf8"));
const car = (id: string) => parseCar(JSON.parse(readFileSync(join(root, `assets/data/cars/${id}.json`), "utf8")));

// The game simulates in 32-bit floats, the port in doubles: a split can land a physics step (1/240 s)
// either side, and a hard-revving build can drift a few. Three steps, about 12.5 ms.
const TOLERANCE = 3 / 240 + 5e-4;

test("the port replays the game's races within three physics steps (shared/physics_cases.json)", () => {
  assert.ok(cases.length >= 8);
  let worst = 0;
  for (const c of cases) {
    const def = apply(car(c.car), catalog, c.parts, {}, tuneFrom(c.tune));
    const r = replay(def, c.events, { trackGrip: c.trackGrip, startX: c.startX, finishM: c.finishM });
    const want = c.expect;
    assert.ok(r.leaveTime !== null, `${c.name}: never left`);
    assert.ok(Math.abs(r.leaveTime! - want.leaveTime) < TOLERANCE, `${c.name}: reaction ${r.leaveTime} vs ${want.leaveTime}`);
    want.splits.forEach((s: number, i: number) => {
      if (s < 0) return assert.ok(r.splits[i] < 0, `${c.name}: split ${i} should not be reached`);
      const d = Math.abs(r.splits[i] - s);
      worst = Math.max(worst, d);
      assert.ok(d < TOLERANCE, `${c.name}: split ${i} ${r.splits[i].toFixed(4)} vs ${s.toFixed(4)}`);
      assert.ok(Math.abs(r.splitSpeed[i] - want.splitSpeed[i]) < 0.3, `${c.name}: speed at split ${i}`);
    });
    assert.deepEqual(r.ratings, want.ratings, `${c.name}: ratings`);
  }
  console.log(`worst split difference: ${(worst * 1000).toFixed(3)} ms`);
});
