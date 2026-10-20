"""Writes shared/ledger_cases.json: ledger sequences with hand-reasoned expectations.

Prices are read from assets/data/rules.json so the cases survive price tweaks; the arithmetic
(what each step should cost or pay) is written out here, independently of either applier.
Both core LedgerTest (Kotlin) and server/functions tests (TypeScript) must agree with it.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
R = json.loads((ROOT / "assets/data/rules.json").read_text())
CARS = R["cars"]
RIV = {r["id"]: r for r in R["rivals"]}
START = R["startingCash"]
GAP = R["minRaceGapMs"]


def start(car="kite", t=1000):
    return {"type": "start", "t": t, "car": car}


def race(rival, t, won=True, pink=False, uid="c1"):
    return {"type": "race", "t": t, "uid": uid, "rival": rival, "won": won, "pink": pink}


def part(slot, uid="c1", t=2000):
    return {"type": "part", "t": t, "uid": uid, "slot": slot}


def kite(uid="c1", parts=None, car="kite", paint=None):
    d = {"uid": uid, "car": car, "parts": parts or {}}
    if paint is not None:
        d["paint"] = paint
    return d


cases = []


def case(name, entries, refused=(), **expect):
    expect["refused"] = list(refused)
    cases.append({"name": name, "entries": entries, "expect": expect})


k = CARS["kite"]["parts"]
case("starter car and starting cash", [start()], cash=START, cars=[kite(paint=CARS["kite"]["paint"])])
case("only one starter", [start(), start("kestrel")], refused=[1], cash=START, cars=[kite()])
case("starter must be a starter car", [start("vandal")], refused=[0], cash=0, cars=[])
case("parts cost the per-car price and need the cash",
     [start(), part("intake"), part("intake")], refused=[2] if START - k["intake"][0] < k["intake"][1] else [],
     cash=START - k["intake"][0], cars=[kite(parts={"intake": 1})])
case("locked rival is refused", [start(), race("kat", 10000)], refused=[1], cash=START, beaten=[])
case("races too close together are refused",
     [start(), race("dash", 10000), race("kat", 10000 + GAP - 1)], refused=[2],
     cash=START + RIV["dash"]["payout"], beaten=["dash"])
case("rematch pays half",
     [start(), race("dash", 10000), race("dash", 10000 + GAP)],
     cash=START + RIV["dash"]["payout"] + RIV["dash"]["payout"] // 2, beaten=["dash"])
case("a loss pays nothing but still counts for spacing",
     [start(), race("dash", 10000, won=False), race("dash", 11000)], refused=[2], cash=START, beaten=[])
boss = [start(), race("dash", 10000), race("kat", 20000), race("tiny", 30000), race("joe", 40000)]
boss_cash = START + sum(RIV[r]["payout"] for r in ["dash", "kat", "tiny", "joe"])
joe = RIV["joe"]
case("first boss win gives the boss car with its parts", boss, cash=boss_cash,
     beaten=["dash", "kat", "tiny", "joe"], cars=[kite(), kite("c2", joe["parts"], joe["car"], joe["paint"])])
case("boss car only the first time", boss + [race("joe", 50000)], cash=boss_cash + joe["payout"] // 2,
     cars=[kite(), kite("c2", joe["parts"], joe["car"])])


def sale(car, parts):
    c = CARS[car]
    spent = sum(sum(c["parts"][s][:t]) for s, t in parts.items())
    return c["sell"] + spent * R["resalePercent"] // 100


case("selling pays resale plus 30% of parts", boss + [{"type": "sell", "t": 50000, "uid": "c2"}],
     cash=boss_cash + sale(joe["car"], joe["parts"]), cars=[kite()])
case("never sell the last car", [start(), {"type": "sell", "t": 2000, "uid": "c1"}], refused=[1], cash=START, cars=[kite()])
dash = RIV["dash"]
case("pink slip needs a beaten rival, wins their car, loses yours",
     [start(), race("dash", 10000, pink=True), race("dash", 20000), race("dash", 30000, pink=True),
      race("dash", 40000, won=False, pink=True, uid="c2")],
     refused=[1], cash=START + dash["payout"], cars=[kite()])
case("pink slip loss never takes the last car",
     [start(), race("dash", 10000), race("dash", 20000, won=False, pink=True)],
     cash=START + dash["payout"], cars=[kite()])
paint_cost = R["colorPrice"] + R["finishes"][1] + R["decals"][1] + R["decalColorPrice"]
case("paint charges each change once",
     [start(), race("dash", 10000), {"type": "paint", "t": 11000, "uid": "c1", "paint": [3, 1, 1, 2]},
      {"type": "paint", "t": 12000, "uid": "c1", "paint": [3, 1, 1, 2]}],
     refused=[3], cash=START + dash["payout"] - paint_cost, cars=[kite(paint=3)])
case("paint out of range", [start(), {"type": "paint", "t": 2000, "uid": "c1", "paint": [R["colors"], 0, 0, 0]}],
     refused=[1], cash=START)
case("dealer: not prize cars, not twice, not without cash",
     [start(), {"type": "buy", "t": 2000, "car": joe["car"]}, {"type": "buy", "t": 2000, "car": "kite"},
      {"type": "buy", "t": 2000, "car": "vandal"}], refused=[1, 2, 3], cash=START, cars=[kite()])
case("buying a car", [start(), race("dash", 10000), race("kat", 20000), {"type": "buy", "t": 21000, "car": "kestrel"}],
     cash=START + dash["payout"] + RIV["kat"]["payout"] - CARS["kestrel"]["price"], cars=[kite(), kite("c2", car="kestrel")])
case("cosmetics cycle styles", [start(), {"type": "look", "t": 2000, "uid": "c1", "slot": "wing"}],
     cash=START - R["cosmetics"]["wing"]["price"])
case("turbo and supercharger exclude each other",
     [start(), race("dash", 10000), race("kat", 20000), part("turbo"), part("supercharger")],
     cash=START + dash["payout"] + RIV["kat"]["payout"] - k["turbo"][0] - k["supercharger"][0], cars=[kite(parts={"supercharger": 1})])
case("unknown things are refused",
     [start(), {"type": "gift", "t": 2000}, part("rocket"), {"type": "part", "t": 2000, "uid": "c9", "slot": "intake"},
      race("nobody", 10000)], refused=[1, 2, 3, 4], cash=START)

(ROOT / "shared/ledger_cases.json").write_text(json.dumps(cases, indent=1) + "\n")
print(len(cases), "cases")
