import assert from "node:assert/strict";
import { test } from "node:test";
import { allowed, cleanEmblem, cleanName, cleanTag, holders, weekKey } from "../crews";

test("names, tags and emblems are checked", () => {
  assert.equal(cleanName("  night   riders ", 3, 16), "NIGHT RIDERS");
  assert.equal(cleanName("ab", 3, 16), null);
  assert.equal(cleanName("<script>", 3, 16), null);
  assert.equal(cleanTag("nr"), "NR");
  for (const bad of ["N", "NIGHT", "N R", "N!", 5]) assert.equal(cleanTag(bad), null, String(bad));
  assert.deepEqual(cleanEmblem({ shape: 2, colors: [1, 2, 3], icon: 4 }), { shape: 2, colors: [1, 2, 3], icon: 4 });
  for (const bad of [{ shape: 9, colors: [1, 2, 3], icon: 4 }, { shape: 1, colors: [1, 2], icon: 4 }, { shape: 1, colors: [1, 2, 99], icon: 4 }, null]) {
    assert.equal(cleanEmblem(bad), null, JSON.stringify(bad));
  }
});

test("roles: leaders run it, officers manage members, members manage nobody", () => {
  assert.ok(allowed("officer", "kick", "member"));
  assert.ok(!allowed("officer", "kick", "officer"));
  assert.ok(!allowed("officer", "kick", "leader"));
  assert.ok(allowed("leader", "kick", "officer"));
  assert.ok(!allowed("member", "kick", "member"));
  assert.ok(allowed("leader", "promote", "member") && !allowed("officer", "promote", "member"));
  assert.ok(allowed("leader", "lead", "officer") && !allowed("officer", "lead", "member"));
  assert.ok(allowed("leader", "edit") && !allowed("officer", "edit"));
});

test("districts go to the crew with the most wins there, first to it on a tie", () => {
  const h = holders([
    { crew: "a", districts: { desert: 3, port: 2 }, first: { desert: 100, port: 50 } },
    { crew: "b", districts: { desert: 5, port: 2 }, first: { desert: 200, port: 40 } },
  ]);
  assert.deepEqual(h.desert, { crew: "b", wins: 5 });
  assert.deepEqual(h.port, { crew: "b", wins: 2 }, "tie: b got there first");
  assert.equal(h.coast, null, "nobody raced there");
});

test("the war week turns over on Monday", () => {
  assert.equal(weekKey(Date.UTC(2026, 9, 18)), "2026-W42");   // Sunday 18 Oct 2026
  assert.equal(weekKey(Date.UTC(2026, 9, 19)), "2026-W43");   // Monday
  assert.equal(weekKey(Date.UTC(2027, 0, 1)), "2026-W53");    // ISO: 1 Jan 2027 is in 2026's last week
});
