/**
 * End to end against the emulator suite (run by `npm run e2e` in server/):
 * anonymous sign-in, sync, refused cheats, persistence across calls, and Firestore closed to clients.
 */
import assert from "node:assert/strict";

const project = process.env.GCLOUD_PROJECT ?? "demo-pixel-strip";
const auth = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST ?? "127.0.0.1:9099"}`;
const fn = `http://127.0.0.1:5001/${project}/us-central1/sync`;
const firestore = `http://${process.env.FIRESTORE_EMULATOR_HOST ?? "127.0.0.1:8080"}`;

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

  console.log("e2e: all checks passed");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
