"""One-off: converts career.json rival builds from the old four-tier parts to the new options,
through assets/data/parts_migration.json (the same table the game uses for old saves)."""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
A = ROOT / "assets/data"
mig = {k: v for k, v in json.loads((A / "parts_migration.json").read_text()).items() if not k.startswith("_")}
cat = {s["id"]: s for s in json.loads((A / "parts.json").read_text())["slots"]}


def ok(slot, opt, parts, drivetrain):
    o = cat[slot]["options"][opt]
    if o.get("rwdOnly") and drivetrain != "RWD":
        return False
    req = o.get("requires")
    return not req or any(parts.get(s, 0) >= m for s, m in req)


career = json.loads((A / "career.json").read_text())
for tier in career["tiers"]:
    for r in tier["rivals"]:
        dt = json.loads((A / f"cars/{r['car']}.json").read_text())["drivetrain"]
        out = {}
        for slot, t in r["parts"].items():
            for k, v in (mig.get(slot, [{}] * 4)[t - 1] if t > 0 else {}).items():
                out[k] = max(out.get(k, 0), v)
        kit = r.get("cosmetics", {}).pop("kit", 0)
        if kit:
            for k, v in mig["kit"][kit - 1].items():
                out[k] = max(out.get(k, 0), v)
        if out.get("turbo") and out.get("supercharger"):
            out.pop("supercharger")
        r["parts"] = {k: v for k, v in sorted(out.items()) if k in cat and ok(k, v, out, dt)}
        print(f"{r['id']:9s} {r['car']:13s} {r['parts']}")
(A / "career.json").write_text(json.dumps(career, indent=1) + "\n")
