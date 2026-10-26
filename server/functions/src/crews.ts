/**
 * Crews: up to 20 racers under a name, a tag and a pixel emblem; a leader, officers and members.
 * Weekly crew wars: every online win scores for the winner's crew in the district (track) it was
 * raced in; the crew with the most wins in a district this week holds it.
 *
 * Firestore: crews/{id} (public details, members, weekly scores), crewTags/{TAG} (keeps tags
 * unique), players/{uid}.crew. Every change goes through these functions; clients can't write.
 */
import { randomBytes } from "node:crypto";
import { TRACK_IDS } from "./tracks";
import { FieldValue, getFirestore, Transaction } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

export const MAX_MEMBERS = 20;
export type Role = "leader" | "officer" | "member";

/** Emblem: a shape, three colours (primary, edge, icon) from the game's emblem palette, and an icon. */
export interface Emblem { shape: number; colors: number[]; icon: number }
export const SHAPES = 6;
export const COLORS = 12;
export const ICONS = 12;

export interface Member { role: Role; name: string; joined: number; best?: Showcase | null }
/** A member's best online run, with the car that did it (shown in the crew HQ). */
export interface Showcase { car: string; paint: number; parts: Record<string, number>; cosmetics: Record<string, number>; et: number }

/** Districts of the war map: the tracks open to everyone. */
export const DISTRICTS = TRACK_IDS;

/** ISO week, e.g. "2026-W43": the war resets every Monday. */
export function weekKey(t = Date.now()): string {
  const d = new Date(t);
  const day = (d.getUTCDay() + 6) % 7;
  d.setUTCDate(d.getUTCDate() - day + 3);
  const first = new Date(Date.UTC(d.getUTCFullYear(), 0, 4));
  const week = 1 + Math.round(((d.getTime() - first.getTime()) / 86400000 - 3 + ((first.getUTCDay() + 6) % 7)) / 7);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

// --- validation (pure, tested) -----------------------------------------------------------------

export function cleanName(raw: unknown, min: number, max: number): string | null {
  if (typeof raw !== "string") return null;
  const s = raw.trim().toUpperCase().replace(/\s+/g, " ");
  return s.length >= min && s.length <= max && /^[A-Z0-9 '!?.-]+$/.test(s) ? s : null;
}

export const cleanTag = (raw: unknown) => {
  if (typeof raw !== "string") return null;
  const s = raw.trim().toUpperCase();
  return /^[A-Z0-9]{2,4}$/.test(s) ? s : null;
};

export function cleanEmblem(raw: unknown): Emblem | null {
  if (typeof raw !== "object" || raw === null) return null;
  const o = raw as Record<string, unknown>;
  const int = (v: unknown, n: number) => Number.isInteger(v) && (v as number) >= 0 && (v as number) < n;
  if (!int(o.shape, SHAPES) || !int(o.icon, ICONS) || !Array.isArray(o.colors) || o.colors.length !== 3 || !o.colors.every((c) => int(c, COLORS))) return null;
  return { shape: o.shape as number, colors: o.colors as number[], icon: o.icon as number };
}

/** Can [actor] do [action] to [target]? Leaders run everything; officers manage plain members. */
export function allowed(actor: Role, action: "kick" | "promote" | "demote" | "lead" | "edit", target?: Role): boolean {
  switch (action) {
    case "edit": return actor === "leader";
    case "lead": return actor === "leader" && target !== "leader";
    case "promote": return actor === "leader" && target === "member";
    case "demote": return actor === "leader" && target === "officer";
    case "kick": return target === "member" ? actor === "leader" || actor === "officer" : actor === "leader" && target === "officer";
  }
}

/** Who holds each district this week: the crew with the most wins there (ties: whoever got there first). */
export function holders(scores: { crew: string; districts: Record<string, number>; first: Record<string, number> }[]) {
  const out: Record<string, { crew: string; wins: number } | null> = {};
  for (const d of DISTRICTS) {
    let best: { crew: string; wins: number; at: number } | null = null;
    for (const s of scores) {
      const wins = s.districts[d] ?? 0;
      if (wins <= 0) continue;
      const at = s.first[d] ?? Number.MAX_SAFE_INTEGER;
      if (!best || wins > best.wins || (wins === best.wins && at < best.at)) best = { crew: s.crew, wins, at };
    }
    out[d] = best ? { crew: best.crew, wins: best.wins } : null;
  }
  return out;
}

// --- functions ---------------------------------------------------------------------------------

const db = () => getFirestore();
const crewRef = (id: string) => db().collection("crews").doc(id);
const playerRef = (uid: string) => db().collection("players").doc(uid);

function needAuth(req: { auth?: { uid: string } }): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "sign in first");
  return req.auth.uid;
}

/** The player's display name (set with setName, or their RACER handle). */
export const handleOf = (uid: string, doc?: FirebaseFirestore.DocumentData) =>
  (doc?.name as string | undefined) ?? "RACER " + uid.slice(0, 4).toUpperCase();

/** Pick a display name (3-12 letters, digits, spaces). Shown on the VS card, timeslips and the crew roster. */
export const setName = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const name = cleanName((req.data ?? {}).name, 3, 12);
  if (!name) throw new HttpsError("invalid-argument", "3 to 12 letters, digits or spaces");
  await db().runTransaction(async (tx) => {
    const p = await tx.get(playerRef(uid));
    tx.set(playerRef(uid), { name }, { merge: true });
    const crew = p.data()?.crew as string | undefined;
    if (crew) tx.update(crewRef(crew), { [`members.${uid}.name`]: name });
  });
  return { name };
});

async function myCrew(tx: Transaction, uid: string) {
  const p = await tx.get(playerRef(uid));
  const crewId = p.data()?.crew as string | undefined;
  if (!crewId) return { p, crewId: null, crew: null };
  const c = await tx.get(crewRef(crewId));
  return { p, crewId, crew: c.exists ? c.data()! : null };
}

/** Found a crew: you lead it. The tag must be free. */
export const createCrew = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const name = cleanName(d.name, 3, 16);
  const tag = cleanTag(d.tag);
  const emblem = cleanEmblem(d.emblem);
  if (!name || !tag || !emblem) throw new HttpsError("invalid-argument", "name 3-16 characters, tag 2-4 letters or digits, and an emblem");
  const id = randomBytes(8).toString("base64url");
  await db().runTransaction(async (tx) => {
    const { p, crewId } = await myCrew(tx, uid);
    if (crewId) throw new HttpsError("failed-precondition", "leave your crew first");
    const tagRef = db().collection("crewTags").doc(tag);
    if ((await tx.get(tagRef)).exists) throw new HttpsError("already-exists", `the tag ${tag} is taken`);
    tx.set(tagRef, { crew: id });
    tx.set(crewRef(id), {
      name, tag, emblem, open: true, created: Date.now(), memberCount: 1, wins: 0,
      members: { [uid]: { role: "leader", name: handleOf(uid, p.data()), joined: Date.now(), best: null } },
    });
    tx.set(playerRef(uid), { crew: id }, { merge: true });
  });
  return { crewId: id };
});

/** Join an open crew with room. */
export const joinCrew = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const id = (req.data ?? {}).crewId;
  if (typeof id !== "string") throw new HttpsError("invalid-argument", "crewId");
  await db().runTransaction(async (tx) => {
    const { p, crewId } = await myCrew(tx, uid);
    if (crewId) throw new HttpsError("failed-precondition", "leave your crew first");
    const c = await tx.get(crewRef(id));
    if (!c.exists) throw new HttpsError("not-found", "no such crew");
    const crew = c.data()!;
    if (!crew.open) throw new HttpsError("permission-denied", "this crew is invite-only");
    if (crew.memberCount >= MAX_MEMBERS) throw new HttpsError("resource-exhausted", "this crew is full");
    tx.update(crewRef(id), { [`members.${uid}`]: { role: "member", name: handleOf(uid, p.data()), joined: Date.now(), best: null }, memberCount: FieldValue.increment(1) });
    tx.set(playerRef(uid), { crew: id }, { merge: true });
  });
  return { crewId: id };
});

/** Leave. A leader hands over to the longest-serving officer (or member); the last one out closes the crew. */
export const leaveCrew = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  await db().runTransaction(async (tx) => {
    const { crewId, crew } = await myCrew(tx, uid);
    if (!crewId || !crew) throw new HttpsError("failed-precondition", "you're not in a crew");
    const members = crew.members as Record<string, Member>;
    const me = members[uid];
    tx.update(playerRef(uid), { crew: FieldValue.delete() });
    if (crew.memberCount <= 1) {
      tx.delete(crewRef(crewId));
      tx.delete(db().collection("crewTags").doc(crew.tag));
      return;
    }
    const update: Record<string, unknown> = { [`members.${uid}`]: FieldValue.delete(), memberCount: FieldValue.increment(-1) };
    if (me?.role === "leader") {
      const next = Object.entries(members).filter(([u]) => u !== uid)
        .sort(([, a], [, b]) => (a.role === "officer" ? 0 : 1) - (b.role === "officer" ? 0 : 1) || a.joined - b.joined)[0];
      if (next) update[`members.${next[0]}.role`] = "leader";
    }
    tx.update(crewRef(crewId), update);
  });
  return { ok: true };
});

/** Leader and officer actions on a member: kick, promote, demote, hand over the lead. */
export const manageMember = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const action = d.action;
  const target = d.uid;
  if (typeof target !== "string" || !["kick", "promote", "demote", "lead"].includes(action as string)) throw new HttpsError("invalid-argument", "action, uid");
  await db().runTransaction(async (tx) => {
    const { crewId, crew } = await myCrew(tx, uid);
    if (!crewId || !crew) throw new HttpsError("failed-precondition", "you're not in a crew");
    const members = crew.members as Record<string, Member>;
    const them = members[target];
    if (!them || target === uid) throw new HttpsError("not-found", "not in your crew");
    if (!allowed(members[uid].role, action as "kick", them.role)) throw new HttpsError("permission-denied", "your role can't do that");
    const ref = crewRef(crewId);
    switch (action) {
      case "kick":
        tx.update(ref, { [`members.${target}`]: FieldValue.delete(), memberCount: FieldValue.increment(-1) });
        tx.update(playerRef(target), { crew: FieldValue.delete() });
        break;
      case "promote": tx.update(ref, { [`members.${target}.role`]: "officer" }); break;
      case "demote": tx.update(ref, { [`members.${target}.role`]: "member" }); break;
      case "lead": tx.update(ref, { [`members.${target}.role`]: "leader", [`members.${uid}.role`]: "officer" }); break;
    }
  });
  return { ok: true };
});

/** Leader: change the emblem, or open/close the crew to new members. */
export const editCrew = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  await db().runTransaction(async (tx) => {
    const { crewId, crew } = await myCrew(tx, uid);
    if (!crewId || !crew) throw new HttpsError("failed-precondition", "you're not in a crew");
    if (!allowed((crew.members as Record<string, Member>)[uid].role, "edit")) throw new HttpsError("permission-denied", "only the leader can");
    const update: Record<string, unknown> = {};
    if (d.emblem !== undefined) {
      const e = cleanEmblem(d.emblem);
      if (!e) throw new HttpsError("invalid-argument", "emblem");
      update.emblem = e;
    }
    if (typeof d.open === "boolean") update.open = d.open;
    tx.update(crewRef(crewId), update);
  });
  return { ok: true };
});

/** Public view of a crew (name, tag, emblem, roster with roles and showcase cars, this week's war). */
function publicCrew(id: string, c: FirebaseFirestore.DocumentData, week: string) {
  return {
    id, name: c.name, tag: c.tag, emblem: c.emblem, open: c.open, memberCount: c.memberCount, wins: c.wins ?? 0,
    members: c.members, week, score: c.war?.[week]?.total ?? 0, districts: c.war?.[week]?.districts ?? {},
  };
}

/** Your crew (or null), crews to join (open ones, this week's strongest first, or by tag), and the war map. */
export const crewHome = onCall({ maxInstances: 10 }, async (req) => {
  const uid = needAuth(req);
  const d = (req.data ?? {}) as Record<string, unknown>;
  const week = weekKey();
  const p = await playerRef(uid).get();
  const mine = p.data()?.crew as string | undefined;
  const crews = await db().collection("crews").get();
  const all = crews.docs.map((doc) => publicCrew(doc.id, doc.data(), week));
  const search = typeof d.tag === "string" ? cleanTag(d.tag) : null;
  const list = all.filter((c) => c.id !== mine && (search ? c.tag.startsWith(search) : c.open))
    .sort((a, b) => b.score - a.score || b.wins - a.wins).slice(0, 30);
  const scores = crews.docs.map((doc) => ({ crew: doc.id, districts: doc.data().war?.[week]?.districts ?? {}, first: doc.data().war?.[week]?.first ?? {} }));
  const held = holders(scores);
  const byId = new Map(all.map((c) => [c.id, c]));
  const map = Object.fromEntries(DISTRICTS.map((dist) => {
    const h = held[dist];
    const c = h ? byId.get(h.crew) : null;
    return [dist, h && c ? { crew: h.crew, tag: c.tag, emblem: c.emblem, wins: h.wins } : null];
  }));
  return { name: handleOf(uid, p.data()), crew: mine ? byId.get(mine) ?? null : null, list, map, week };
});

/**
 * An online win: +1 for the winner's crew this week, in the race's district; and the winner's
 * showcase car if this run beat their best. Called from race.ts inside the settle transaction.
 */
export async function recordWin(tx: Transaction, winner: string, district: string, showcase: Showcase | null,
  playerDoc: FirebaseFirestore.DocumentData | undefined, crewDoc: FirebaseFirestore.DocumentData | null) {
  const crewId = playerDoc?.crew as string | undefined;
  if (!crewId || !crewDoc) return;
  const week = weekKey();
  const update: Record<string, unknown> = {
    wins: FieldValue.increment(1),
    [`war.${week}.total`]: FieldValue.increment(1),
  };
  if (DISTRICTS.includes(district)) {
    update[`war.${week}.districts.${district}`] = FieldValue.increment(1);
    if (!crewDoc.war?.[week]?.first?.[district]) update[`war.${week}.first.${district}`] = Date.now();
  }
  const member = (crewDoc.members as Record<string, Member>)[winner];
  if (member && showcase && (!member.best || showcase.et < member.best.et)) update[`members.${winner}.best`] = showcase;
  tx.update(crewRef(crewId), update);
}

/** For race rooms: a player's name and crew badge. */
export async function badgeOf(uid: string) {
  const p = await playerRef(uid).get();
  const crewId = p.data()?.crew as string | undefined;
  const c = crewId ? await crewRef(crewId).get() : null;
  return { name: handleOf(uid, p.data()), crew: c?.exists ? { tag: c.data()!.tag, emblem: c.data()!.emblem } : null };
}
