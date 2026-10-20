/**
 * End to end against the emulator suite (run by `npm run e2e` in server/):
 * anonymous sign-in, sync, refused cheats, persistence across calls, and Firestore closed to clients.
 */
import assert from "node:assert/strict";

const project = process.env.GCLOUD_PROJECT ?? "demo-pixel-strip";
const auth = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9099"}`;
const fn = `http://127.0.0.1:5001/${project}/us-central1/sync`;
const firestore = `http://${process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080"}`;
const rtdb = `http://${process.env.FIREBASE_DATABASE_EMULATOR_HOST ?? "127.0.0.1:9000"}`;
const ns = `?ns=${project}`;   // the emulator names a demo project's default database after the project
const call = (name: string, data: unknown, token: string) => json(`http://127.0.0.1:5001/${project}/us-central1/${name}`, { data }, token);
const rtGet = async (path: string, token: string) => {
  const r = await fetch(`${rtdb}/${path}.json${ns}&auth=${token}`);
  return { status: r.status, body: await r.json().catch(() => null) as any };
};
const rtPut = async (path: string, body: unknown, token: string) =>
  (await fetch(`${rtdb}/${path}.json${ns}&auth=${token}`, { method: "PUT", body: JSON.stringify(body) })).status;

async function json(url: string, body: unknown, token?: string) {
  const r = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  return { status: r.status, body: (await r.json().catch(() => null)) as any };
}

async function main() {
  const signup = await json(`${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, { returnSecureToken: true });
  assert.equal(signup.status, 200, "anonymous sign-up");
  const token: string = signup.body.idToken;
  const uid: string = signup.body.localId;

  const noAuth = await json(fn, { data: { entries: [] } });
  assert.equal(noAuth.status, 401, "sync needs a signed-in user");

  const t = Date.now();
  const first = await json(fn, {
    data: {
      base: 0,
      profile: '{"cars":[]}',
      entries: [
        { type: "start", t, car: "kite" },
        { type: "race", t: t + 10_000, uid: "c1", rival: "dash", won: true, pink: false },
        { type: "race", t: t + 11_000, uid: "c1", rival: "kat", won: true, pink: false },   // too soon after the last
        { type: "buy", t: t + 12_000, car: "vandal" },                                     // can't afford it
        { type: "race", t: t + 9_999_999, uid: "c1", rival: "kat", won: true, pink: false }, // from the future
      ],
    },
  }, token);
  assert.equal(first.status, 200, `sync: ${JSON.stringify(first.body)}`);
  const r1 = first.body.result;
  assert.deepEqual(r1.rejected, [2, 3, 4]);
  assert.equal(r1.seq, 2);
  assert.equal(r1.state.cash, 5000 + 3000);
  assert.deepEqual(r1.state.beaten, ["dash"]);
  assert.equal(r1.profile, null);

  const second = await json(fn, { data: { base: 2, entries: [{ type: "race", t: t + 20_000, uid: "c1", rival: "kat", won: true }] } }, token);
  const r2 = second.body.result;
  assert.equal(r2.seq, 3, "state persisted between calls");
  assert.equal(r2.state.cash, 8000 + 4000);
  assert.equal(r2.profile, '{"cars":[]}', "profile comes back for another device");

  // Clients can't touch Firestore directly, not even their own document
  const read = await fetch(`${firestore}/v1/projects/${project}/databases/(default)/documents/players/${uid}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  assert.equal(read.status, 403, "direct read refused");
  const write = await fetch(`${firestore}/v1/projects/${project}/databases/(default)/documents/players/${uid}?updateMask.fieldPaths=state`, {
    method: "PATCH",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({ fields: { state: { mapValue: { fields: { cash: { integerValue: "999999999" } } } } } }),
  });
  assert.equal(write.status, 403, "direct write refused");

  // A brand-new account may bring a pre-ledger save once, capped
  const other = await json(`${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, { returnSecureToken: true });
  const legacy = await json(fn, { data: { base: 0, entries: [], legacy: { cash: 5_000_000, beaten: [], cars: [{ uid: "c1", car: "vandal", paint: 0, parts: {}, cosmetics: {} }], nextUid: 2 } } }, other.body.idToken);
  assert.equal(legacy.body.result.state.cash, 250_000);
  assert.equal(legacy.body.result.state.cars[0].car, "vandal");
  const again = await json(fn, { data: { base: 0, entries: [], legacy: { cash: 250_000, cars: [{ uid: "c1", car: "aurora" }] } } }, other.body.idToken);
  assert.equal(again.body.result.state.cars[0].car, "vandal", "legacy import only on the first sync");

  await onlineRace();
  await wagers();
  await crews();
  console.log("e2e: all checks passed");
}

/** Two players with a Vandal each: matched, live lanes, replayed runs, a red light losing. */
async function onlineRace() {
  const { readFileSync } = await import("node:fs");
  const { join } = await import("node:path");
  // load database.rules.json into the namespace this test uses (the emulator puts firebase.json's elsewhere)
  const rules = readFileSync(join(__dirname, "../../../database.rules.json"), "utf8");
  const loaded = await fetch(`${rtdb}/.settings/rules.json${ns}`, { method: "PUT", headers: { Authorization: "Bearer owner" }, body: rules });
  assert.equal(loaded.status, 200, "database rules loaded");
  const cases = JSON.parse(readFileSync(join(__dirname, "../../../../shared/physics_cases.json"), "utf8"));
  const clean = cases.find((c: any) => c.name === "vandal stock");
  const red = cases.find((c: any) => c.name === "red light");
  const player = async () => {
    const s = await json(`${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, { returnSecureToken: true });
    const token: string = s.body.idToken;
    const synced = await call("sync", { base: 0, entries: [], legacy: { cash: 1000, beaten: [], cars: [{ uid: "c1", car: "vandal", paint: 0, parts: {}, cosmetics: {} }] } }, token);
    assert.equal(synced.status, 200, "garage synced");
    return { token, uid: s.body.localId as string };
  };
  const a = await player();
  const b = await player();
  const stranger = await player();

  const unknownCar = await call("matchmake", { car: "c9" }, a.token);
  assert.equal(unknownCar.status, 400, "a car the server doesn't know is refused");
  const first = await call("matchmake", { car: "c1", quarter: true }, a.token);
  assert.equal(first.body.result.waiting, true, "first in waits");
  const second = await call("matchmake", { car: "c1", quarter: true }, b.token);
  const raceId: string = second.body.result.raceId;
  assert.ok(raceId, `paired: ${JSON.stringify(second.body)}`);
  assert.equal((await rtGet(`matches/${a.uid}`, a.token)).body, raceId, "the waiting player hears about it");
  assert.equal((await rtGet(`matches/${a.uid}`, b.token)).status, 401, "but nobody else can read their match");

  const room = await rtGet(`races/${raceId}`, a.token);
  assert.equal(room.status, 200);
  assert.ok(room.body.players[a.uid] && room.body.players[b.uid] && room.body.lanes[a.uid] === 0, "both racers in the room");
  assert.equal((await rtGet(`races/${raceId}`, stranger.token)).status, 401, "strangers can't watch");
  assert.equal(await rtPut(`races/${raceId}/live/${a.uid}`, { x: 1.5, v: 3, t: 0.2 }, a.token), 200, "a racer streams their own lane");
  assert.equal(await rtPut(`races/${raceId}/live/${b.uid}`, { x: 400, v: 90, t: 0.2 }, a.token), 401, "but not the other lane");
  assert.equal(await rtPut(`races/${raceId}/result`, { winner: a.uid }, a.token), 401, "and nobody writes the result");

  const runA = await call("submitRun", { raceId, events: clean.events }, a.token);
  assert.equal(runA.status, 200, JSON.stringify(runA.body));
  // the room picks the track, and tracks differ in grip: replay the same events here on that track's surface
  const { parseCar } = await import("../physics/car");
  const { apply, parseCatalog } = await import("../physics/parts");
  const { replay } = await import("../physics/vehicle");
  const root = join(__dirname, "../../../..");
  const grip = JSON.parse(readFileSync(join(root, `assets/data/tracks/${room.body.track}.json`), "utf8")).trackGrip ?? 1.25;
  const def = apply(parseCar(JSON.parse(readFileSync(join(root, "assets/data/cars/vandal.json"), "utf8"))),
    parseCatalog(JSON.parse(readFileSync(join(root, "assets/data/parts.json"), "utf8"))), {}, {}, null);
  const local = replay(def, clean.events, { trackGrip: grip, startX: clean.startX, finishM: clean.finishM }).splits[4];
  assert.ok(Math.abs(runA.body.result.run.et - local) < 1e-9, `the server's replay is the physics port's: ${runA.body.result.run.et} vs ${local}`);
  if (grip === clean.trackGrip) assert.ok(Math.abs(runA.body.result.run.et - clean.expect.splits[4]) < 0.01, "and the game's, on the same surface");
  assert.equal(runA.body.result.result, null, "not settled until both are in");
  assert.equal((await call("submitRun", { raceId, events: clean.events }, a.token)).status, 409, "one run each");
  assert.equal((await call("submitRun", { raceId, events: clean.events }, stranger.token)).status, 403, "strangers can't submit");
  const runB = await call("submitRun", { raceId, events: red.events }, b.token);
  const result = runB.body.result.result;
  assert.ok(runB.body.result.run.red, "the red light is caught by the replay");
  assert.equal(result.winner, a.uid, "a clean run beats a red light");
  assert.equal((await rtGet(`races/${raceId}/result`, b.token)).body.winner, a.uid, "the result is in the room");
}

/** Bets and pink slips: confirmation, escrow, payout, refunds, and the protections. */
async function wagers() {
  const { readFileSync } = await import("node:fs");
  const { join } = await import("node:path");
  const cases = JSON.parse(readFileSync(join(__dirname, "../../../../shared/physics_cases.json"), "utf8"));
  const clean = cases.find((c: any) => c.name === "vandal stock").events;
  const red = cases.find((c: any) => c.name === "red light").events;
  const player = async (cash: number, cars: string[]) => {
    const s = await json(`${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, { returnSecureToken: true });
    const token: string = s.body.idToken;
    const legacy = { cash, beaten: [], cars: cars.map((car, i) => ({ uid: `c${i + 1}`, car, paint: 0, parts: {}, cosmetics: {} })) };
    assert.equal((await call("sync", { base: 0, entries: [], legacy }, token)).status, 200);
    return { token, uid: s.body.localId as string };
  };
  const garage = async (p: { token: string }) => (await call("sync", { entries: [] }, p.token)).body.result.state;
  const match = async (a: any, b: any, wager: unknown, car = "c1") => {
    assert.equal((await call("matchmake", { car, wager }, a.token)).body.result.waiting, true);
    const r = await call("matchmake", { car, wager }, b.token);
    assert.ok(r.body.result?.raceId, `paired: ${JSON.stringify(r.body)}`);
    return r.body.result.raceId as string;
  };

  // cash: matched only on the same stake; nothing moves until both accept; winner takes both
  const a = await player(10000, ["vandal"]);
  const b = await player(10000, ["vandal"]);
  const c = await player(10000, ["vandal"]);
  assert.equal((await call("matchmake", { car: "c1", wager: { kind: "cash", amount: 20000 } }, c.token)).status, 400, "can't bet more than you have");
  assert.equal((await call("matchmake", { car: "c1", wager: { kind: "cash", amount: 1000 } }, c.token)).body.result.waiting, true);
  const id = await match(a, b, { kind: "cash", amount: 5000 });
  assert.equal((await call("cancelMatch", {}, c.token)).status, 200, "a different stake never paired with them");
  assert.equal((await rtGet(`races/${id}`, a.token)).body.state, "confirm");
  assert.equal((await call("submitRun", { raceId: id, events: clean }, a.token)).status, 400, "no racing before both accept");
  assert.equal((await call("confirmWager", { raceId: id, accept: true }, a.token)).body.result.state, "confirm", "waiting on the other");
  assert.equal((await garage(a)).cash, 10000, "nothing taken yet");
  assert.equal((await call("confirmWager", { raceId: id, accept: true }, b.token)).body.result.state, "racing");
  assert.deepEqual([(await garage(a)).cash, (await garage(b)).cash], [5000, 5000], "both stakes held by the server");
  await call("submitRun", { raceId: id, events: clean }, a.token);
  const res = (await call("submitRun", { raceId: id, events: red }, b.token)).body.result.result;
  assert.equal(res.winner, a.uid);
  assert.deepEqual(res.prize, { cash: 10000 });
  assert.deepEqual([(await garage(a)).cash, (await garage(b)).cash], [15000, 5000], "the winner takes both stakes");

  // pink slips: a decline moves nothing; then a real one moves the car
  const p = await player(0, ["vandal", "kite"]);
  const q = await player(0, ["vandal", "kestrel"]);
  const solo = await player(0, ["vandal"]);
  assert.equal((await call("matchmake", { car: "c1", wager: { kind: "pink" } }, solo.token)).status, 400, "never your last car");
  const off = await match(p, q, { kind: "pink" });
  await call("confirmWager", { raceId: off, accept: true }, p.token);
  assert.equal((await call("confirmWager", { raceId: off, accept: false }, q.token)).body.result.state, "off");
  assert.equal((await call("submitRun", { raceId: off, events: clean }, p.token)).status, 400, "an off race can't be run");
  assert.equal((await garage(q)).cars.length, 2, "a decline moves nothing");

  const pink = await match(p, q, { kind: "pink" });
  await call("confirmWager", { raceId: pink, accept: true }, p.token);
  await call("confirmWager", { raceId: pink, accept: true }, q.token);
  const sell = await call("sync", { entries: [{ type: "sell", t: Date.now(), uid: "c1" }] }, q.token);
  assert.deepEqual(sell.body.result.rejected, [0], "a car on the line can't be sold mid-race");
  await call("submitRun", { raceId: pink, events: red }, p.token);
  const pr = (await call("submitRun", { raceId: pink, events: clean }, q.token)).body.result.result;
  assert.equal(pr.winner, q.uid);
  assert.equal(pr.prize.car, "vandal");
  const gp = await garage(p);
  const gq = await garage(q);
  assert.deepEqual(gp.cars.map((x: any) => x.car), ["kite"], "the loser keeps their other car");
  assert.deepEqual(gq.cars.map((x: any) => x.car), ["vandal", "kestrel", "vandal"], "the winner drives off in theirs");
  assert.deepEqual([gp.staked, gq.staked], [[], []]);

  // a negative balance (say, after a refunded purchase) blocks betting but not fun races
  const broke = await player(0, ["vandal"]);
  const fsUrl = `${firestore}/v1/projects/${project}/databases/(default)/documents/players/${broke.uid}?updateMask.fieldPaths=state.cash`;
  await fetch(fsUrl, { method: "PATCH", headers: { Authorization: "Bearer owner", "Content-Type": "application/json" },
    body: JSON.stringify({ fields: { state: { mapValue: { fields: { cash: { integerValue: "-500" } } } } } }) });
  assert.equal((await call("matchmake", { car: "c1", wager: { kind: "cash", amount: 100 } }, broke.token)).status, 400);
  assert.equal((await call("matchmake", { car: "c1" }, broke.token)).status, 200, "fun races still open");
  await call("cancelMatch", {}, broke.token);
}

/** Crews: names, founding, roles, war points from online wins, leadership passing on, the tag freed. */
async function crews() {
  const { readFileSync } = await import("node:fs");
  const { join } = await import("node:path");
  const cases = JSON.parse(readFileSync(join(__dirname, "../../../../shared/physics_cases.json"), "utf8"));
  const clean = cases.find((c: any) => c.name === "vandal stock").events;
  const red = cases.find((c: any) => c.name === "red light").events;
  const player = async () => {
    const s = await json(`${auth}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator`, { returnSecureToken: true });
    const token: string = s.body.idToken;
    await call("sync", { base: 0, entries: [], legacy: { cash: 0, beaten: [], cars: [{ uid: "c1", car: "vandal", paint: 0, parts: {}, cosmetics: {} }] } }, token);
    return { token, uid: s.body.localId as string };
  };
  const emblem = { shape: 1, colors: [2, 5, 0], icon: 3 };
  const home = async (p: { token: string }) => (await call("crewHome", {}, p.token)).body.result;
  const [a, b, c, d, x] = [await player(), await player(), await player(), await player(), await player()];

  assert.equal((await call("setName", { name: "night owl" }, a.token)).body.result.name, "NIGHT OWL");
  assert.equal((await call("setName", { name: "<b>" }, a.token)).status, 400);
  assert.equal((await call("createCrew", { name: "Night Riders", tag: "nr", emblem }, a.token)).status, 200);
  assert.equal((await call("createCrew", { name: "Copycats", tag: "NR", emblem }, b.token)).status, 409, "tags are unique");
  const crewId = (await home(a)).crew.id;
  assert.equal((await home(a)).crew.members[a.uid].role, "leader");
  assert.ok((await home(b)).list.some((cr: any) => cr.id === crewId), "open crews are listed");
  for (const p of [b, c]) assert.equal((await call("joinCrew", { crewId }, p.token)).status, 200);
  assert.equal((await call("joinCrew", { crewId }, b.token)).status, 400, "one crew at a time");
  assert.equal((await call("manageMember", { action: "promote", uid: b.uid }, a.token)).status, 200);
  assert.equal((await call("manageMember", { action: "kick", uid: a.uid }, b.token)).status, 403, "officers can't kick the leader");
  assert.equal((await call("editCrew", { open: false }, b.token)).status, 403, "only the leader edits");
  assert.equal((await call("manageMember", { action: "kick", uid: c.uid }, b.token)).status, 200, "officers kick members");
  assert.equal((await home(c)).crew, null);
  assert.equal((await call("editCrew", { open: false }, a.token)).status, 200);
  assert.equal((await call("joinCrew", { crewId }, d.token)).status, 403, "closed crews take no one");

  // an online win scores for the crew in that track's district
  assert.equal((await call("matchmake", { car: "c1" }, a.token)).body.result.waiting, true);
  const m = (await call("matchmake", { car: "c1" }, x.token)).body.result;
  assert.equal(m.room.players[a.uid].crew.tag, "NR", "the crew tag rides along to the race");
  assert.equal(m.room.players[a.uid].name, "NIGHT OWL");
  await call("submitRun", { raceId: m.raceId, events: clean }, a.token);
  await call("submitRun", { raceId: m.raceId, events: red }, x.token);
  const after = await home(a);
  assert.equal(after.crew.score, 1, "a point for the week");
  assert.equal(after.crew.districts[m.room.track], 1, "in the district raced in");
  assert.equal(after.map[m.room.track].tag, "NR", "they hold it on the war map");
  assert.ok(after.crew.members[a.uid].best?.et > 0, "and the winner's car goes up in the HQ");

  // the leader leaves: the officer leads; the last one out frees the tag
  await call("leaveCrew", {}, a.token);
  assert.equal((await home(b)).crew.members[b.uid].role, "leader");
  await call("leaveCrew", {}, b.token);
  assert.equal((await call("createCrew", { name: "New Riders", tag: "NR", emblem }, c.token)).status, 200, "the tag is free again");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
