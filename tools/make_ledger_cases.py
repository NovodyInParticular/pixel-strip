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
X = R["xp"]
WIN, LOSS, BOSS = X["careerWin"], X["careerLoss"], X["bossBonus"]


def level_cash(xp):
    """Cash paid for every level crossed on the way to [xp]: level L needs step*(L-1)*L/2, pays cash*(L-1)."""
    paid, level = 0, 1
    while level < X["maxLevel"] and xp >= X["levelStep"] * level * (level + 1) // 2:
        level += 1
        paid += X["levelCash"] * (level - 1)
    return paid


def start(car="kite", t=1000):
    return {"type": "start", "t": t, "car": car}


def race(rival, t, won=True, pink=False, uid="c1"):
    return {"type": "race", "t": t, "uid": uid, "rival": rival, "won": won, "pink": pink}


def part(slot, opt, uid="c1", t=2000):
    return {"type": "part", "t": t, "uid": uid, "slot": slot, "opt": opt}


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
case("parts cost the per-car price, any option can be bought directly",
     [start(), part("intake", 2)], cash=START - k["intake"][1], cars=[kite(parts={"intake": 2})])
case("swapping back to an owned part is free; refitting the fitted one is refused",
     [start(), part("intake", 2), part("intake", 0), part("intake", 2), part("intake", 2)], refused=[4],
     cash=START - k["intake"][1], cars=[kite(parts={"intake": 2})])
case("a part needs the cash", [start(), part("swap", 3)], refused=[1], cash=START, cars=[kite()])
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
boss_xp = 4 * WIN + BOSS
boss_cash = START + sum(RIV[r]["payout"] for r in ["dash", "kat", "tiny", "joe"]) + level_cash(boss_xp)
joe = RIV["joe"]
case("first boss win gives the boss car with its parts", boss, cash=boss_cash,
     beaten=["dash", "kat", "tiny", "joe"], cars=[kite(), kite("c2", joe["parts"], joe["car"], joe["paint"])], xp=boss_xp)
case("boss car only the first time", boss + [race("joe", 50000)],
     cash=boss_cash + joe["payout"] // 2 + level_cash(boss_xp + WIN) - level_cash(boss_xp), xp=boss_xp + WIN,
     cars=[kite(), kite("c2", joe["parts"], joe["car"])])


def sale(car, parts):
    """Resale plus 30% of every option bought (a prize car owns just what it came with)."""
    c = CARS[car]
    spent = sum(c["parts"][s][o - 1] for s, o in parts.items() if o > 0)
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
case("cosmetics: next style, or pick one; the current style is refused",
     [start(), race("dash", 1500), {"type": "look", "t": 2000, "uid": "c1", "slot": "wing"},
      {"type": "look", "t": 2000, "uid": "c1", "slot": "wing", "opt": 3}, {"type": "look", "t": 2000, "uid": "c1", "slot": "wing", "opt": 3}],
     refused=[4], cash=START + RIV["dash"]["payout"] - 2 * R["cosmetics"]["wing"]["price"])
case("turbo and supercharger exclude each other",
     [start(), race("dash", 10000), race("kat", 20000), part("turbo", 1), part("supercharger", 1)],
     cash=START + dash["payout"] + RIV["kat"]["payout"] - k["turbo"][0] - k["supercharger"][0], cars=[kite(parts={"supercharger": 1})])
two = START + dash["payout"] + RIV["kat"]["payout"]
case("wide tyres need tubs or a widebody, and the widebody can't come off under them",
     [start(), race("dash", 10000), race("kat", 20000), part("tires", 4), part("fenders", 2), part("tires", 4), part("fenders", 0)],
     refused=[3, 6], cash=two - k["fenders"][1] - k["tires"][3], cars=[kite(parts={"fenders": 2, "tires": 4})])
case("RWD-only parts on a front-driver are refused", [start(), part("wheeliebars", 1)], refused=[1], cash=START)
case("Joe's AWD conversion only fits his EF", boss + [part("drive", 1), part("drive", 1, uid="c2", t=41000)], refused=[5],
     cash=boss_cash - CARS[joe["car"]]["parts"]["drive"][0])
case("unknown things are refused",
     [start(), {"type": "gift", "t": 2000}, part("rocket", 1), {"type": "part", "t": 2000, "uid": "c9", "slot": "intake", "opt": 1},
      race("nobody", 10000)], refused=[1, 2, 3, 4], cash=START)

rematches = [start()] + [race("dash", 10000 + i * GAP) for i in range(8)]
case("XP: a win, a loss, and levels paying cash as each threshold is crossed",
     rematches + [race("dash", 10000 + 8 * GAP, won=False)],
     cash=START + dash["payout"] + 7 * (dash["payout"] // 2) + level_cash(8 * WIN + LOSS), xp=8 * WIN + LOSS)
case("XP: a refused race earns nothing", [start(), race("kat", 10000)], refused=[1], cash=START, xp=0)
assert level_cash(8 * WIN + LOSS) == 1000 + 2000, "825 XP is level 3: 1,000 for level 2 and 2,000 for level 3"

(ROOT / "shared/ledger_cases.json").write_text(json.dumps(cases, indent=1) + "\n")
print(len(cases), "cases")
