import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";
import { apply, emptyState, importLegacy, LEGACY_CASH_CAP, Rules, sanitize } from "../ledger";

const root = join(__dirname, "../../../..");
const rules: Rules = JSON.parse(readFileSync(join(root, "assets/data/rules.json"), "utf8"));
const cases = JSON.parse(readFileSync(join(root, "shared/ledger_cases.json"), "utf8"));

interface Want { uid: string; car: string; parts?: Record<string, number>; paint?: number }

test("shared ledger cases (same file as core LedgerTest)", () => {
  assert.ok(cases.length >= 10);
  for (const c of cases) {
    const s = emptyState();
    const refused: number[] = [];
    c.entries.forEach((raw: unknown, i: number) => {
      const e = sanitize(raw);
      if (!e || !apply(rules, s, e)) refused.push(i);
    });
    const want = c.expect;
    assert.deepEqual(refused, want.refused, `${c.name}: refused`);
    if (want.cash !== undefined) assert.equal(s.cash, want.cash, `${c.name}: cash`);
    if (want.beaten !== undefined) assert.deepEqual(s.beaten, want.beaten, `${c.name}: beaten`);
    if (want.cars !== undefined) {
      assert.equal(s.cars.length, want.cars.length, `${c.name}: car count`);
      want.cars.forEach((w: Want, i: number) => {
        assert.equal(s.cars[i].uid, w.uid, `${c.name}: car ${i} uid`);
        assert.equal(s.cars[i].car, w.car, `${c.name}: car ${i} id`);
        if (w.parts) assert.deepEqual(s.cars[i].parts, w.parts, `${c.name}: car ${i} parts`);
        if (w.paint !== undefined) assert.equal(s.cars[i].paint, w.paint, `${c.name}: car ${i} paint`);
      });
    }
  }
});

test("garbage entries are refused, not crashed on", () => {
  for (const raw of [null, 5, "x", {}, { type: "race" }, { type: "race", t: "1" }, { type: "paint", t: 1, uid: "c1", paint: [1, 2] },
    { type: "buy", t: 1, car: "__proto__" }, { type: "part", t: 1, uid: "c1", slot: "constructor", opt: 1 }, { type: "part", t: 1, uid: "c1", slot: "intake", opt: 1.5 },
    { type: "part", t: 1, uid: "c1", slot: "intake", opt: 99 }, { type: "part", t: 1, uid: "c1", slot: "intake" }]) {
    const s = emptyState();
    apply(rules, s, { type: "start", t: 0, car: "kite" });
    const e = sanitize(raw);
    assert.ok(!e || !apply(rules, s, e), JSON.stringify(raw));
    assert.equal(s.cash, rules.startingCash);
  }
});

test("legacy import keeps known things and caps cash", () => {
  const s = importLegacy(rules, {
    cash: 9_999_999,
    beaten: ["dash", "nobody"],
    cars: [
      { uid: "c3", car: "vandal", paint: 1, parts: { intake: 2, rocket: 9, exhaust: 99 }, cosmetics: { wing: 1, finish: 2, nope: 1 }, owned: { intake: 0b111, exhaust: 1 << 20 } },
      { uid: "c4", car: "spaceship" },
      { uid: "../x", car: "kite" },
    ],
  });
  assert.equal(s.cash, LEGACY_CASH_CAP);
  assert.deepEqual(s.beaten, ["dash"]);
  assert.equal(s.cars.length, 1);
  assert.deepEqual(s.cars[0].parts, { intake: 2 });
  assert.deepEqual(s.cars[0].owned, { intake: 0b110 }, "owned bits stay inside the slot's options, and the factory bit never counts");
  assert.deepEqual(s.cars[0].cosmetics, { wing: 1, finish: 2 });
  assert.equal(s.nextUid, 4);
});
