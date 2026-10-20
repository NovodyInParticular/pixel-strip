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
  assert.ok(Math.abs(runA.body.result.run.et - clean.expect.splits[4]) < 0.01, "the server's replay matches the game's ET");
  assert.equal(runA.body.result.result, null, "not settled until both are in");
  assert.equal((await call("submitRun", { raceId, events: clean.events }, a.token)).status, 409, "one run each");
  assert.equal((await call("submitRun", { raceId, events: clean.events }, stranger.token)).status, 403, "strangers can't submit");
  const runB = await call("submitRun", { raceId, events: red.events }, b.token);
  const result = runB.body.result.result;
  assert.ok(runB.body.result.run.red, "the red light is caught by the replay");
  assert.equal(result.winner, a.uid, "a clean run beats a red light");
  assert.equal((await rtGet(`races/${raceId}/result`, b.token)).body.winner, a.uid, "the result is in the room");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
