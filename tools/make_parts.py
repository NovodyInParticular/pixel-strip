"""Writes assets/data/parts.json: every part slot, its options and their effects.

Option 0 of every slot is what the car left the factory with (free, always owned). Prices are
for a STREET-class car and scale with the car's stock class (classMultiplier). Effect keys:

  lo / mid / hi      torque added near idle / at the torque peak / at peak power and up (shares)
  tq                 torque added across the whole curve
  peakRpm            moves the torque and power peaks (rpm)
  rev                raises the rev limit the valvetrain allows (rpm)
  idle               idle rpm change (big cams)
  strength           knock limit from the bottom end: total cylinder-pressure multiplier it takes
  octane             knock limit multiplier from the fuel; fuelFlow = how much more fuel it needs
  fuelHp             crank horsepower the pump and injectors can feed
  ecu                0 stock, 1 piggyback, 2 reflash, 3 standalone (unlocks tuning)
  psi, spool, lag    turbo: most boost, rpm it starts making boost, seconds to spool
  icEff              intercooler: how much of the boost turns into power
  boostCtl           0 spring only (8 psi), 1 manual controller, 2 electronic (boost by gear)
  bov                blow-off valve: keeps the turbo spinning through shifts
  scPsi, scType      supercharger: boost and type (1 roots, 2 twin-screw, 3 centrifugal)
  nosHp, charges     nitrous: biggest shot, bottle sprays; progressive = ramped controller
  clutch, clutchTime clutch capacity (x peak torque) and engagement time (multiplier)
  inertia, wheelInertia  rotating mass multipliers (engine side, axle side)
  gears, shift, tuneRange  gearbox: gear count, shift time multiplier, ratio tuning range
  finalMult, finalRange    ring and pinion
  grip               tyre/axle grip share; warm = tyre temperature it likes; sens = pressure sensitivity
  transfer, transferRate, adjustable   weight transfer amount, speed, and whether it's tunable
  wheelieBars, tubs  wheelie bars; room for wide tyres
  weight, rear       kg, and weight moved rearward (share)
  drag               drag area (m^2)
  requires           [[slot, minOption], ...] any one of them; rwdOnly; carOnly = car ids it fits
  awd                converts the drivetrain to all-wheel drive
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent


def o(name, price, info, **fx):
    d = {"name": name, "price": price, "info": info}
    d.update(fx)
    return d


STOCK = lambda info="FACTORY PART": o("STOCK", 0, info)

WIDE = [["tubs", 1], ["fenders", 2]]

SLOTS = [
    # --- ENGINE: breathing and the rotating assembly ----------------------------------------------
    ("intake", "AIR INTAKE", "ENGINE", [
        STOCK("FACTORY AIRBOX"),
        o("COLD AIR INTAKE", 900, "COOLER AIR, A LITTLE MORE EVERYWHERE", lo=0.01, mid=0.02, hi=0.03),
        o("RAM AIR", 2200, "HOOD SCOOP FEEDS IT. BEST UP TOP", mid=0.02, hi=0.05, visual="scoop"),
    ]),
    ("throttle", "THROTTLE BODY", "ENGINE", [
        STOCK(),
        o("BIG BORE", 700, "MORE AIR AT HIGH RPM", mid=0.01, hi=0.03),
        o("INDIVIDUAL THROTTLES", 4500, "ONE PER CYLINDER. SHARP, PEAKY", lo=-0.02, mid=0.03, hi=0.07, peakRpm=150),
    ]),
    ("manifold", "INTAKE MANIFOLD", "ENGINE", [
        STOCK(),
        o("PORTED", 1800, "SMOOTHER RUNNERS", mid=0.02, hi=0.03),
        o("LONG RUNNER", 3000, "FAT MIDRANGE, FADES UP TOP", lo=0.05, mid=0.05, hi=-0.02, peakRpm=-200),
        o("SHORT RUNNER", 3500, "SOFT DOWN LOW, SCREAMS UP TOP", lo=-0.04, mid=0.01, hi=0.08, peakRpm=250),
    ]),
    ("heads", "CYLINDER HEADS", "ENGINE", [
        STOCK(),
        o("PORTED HEADS", 4000, "BIGGER PORTS, BETTER FLOW", mid=0.03, hi=0.05, rev=200),
        o("CNC RACE HEADS", 9000, "MACHINED PORTS, STIFF SPRINGS", mid=0.05, hi=0.09, rev=500),
    ]),
    ("cams", "CAMSHAFTS", "ENGINE", [
        STOCK(),
        o("STREET CAM", 1500, "MILD. KEEPS THE IDLE SMOOTH", lo=-0.01, mid=0.03, hi=0.05, peakRpm=150),
        o("HOT CAM", 3200, "LUMPY IDLE, PULLS HARD UP TOP", lo=-0.05, mid=0.03, hi=0.10, peakRpm=350, rev=200, idle=100),
        o("RACE CAM", 6500, "NO LOW END. ALL TOP END", lo=-0.12, mid=0.02, hi=0.16, peakRpm=650, rev=400, idle=250),
    ]),
    ("internals", "BOTTOM END", "ENGINE", [
        o("STOCK", 0, "CAST PISTONS. KNOCKS PAST ~7 PSI ON PUMP GAS", strength=1.35),
        o("FORGED PISTONS", 5000, "TAKES REAL BOOST", strength=1.7, rev=200),
        o("FORGED ROTATING ASSEMBLY", 11000, "FORGED CRANK, RODS, PISTONS", strength=2.2, rev=500, inertia=0.95),
        o("BILLET RACE BLOCK", 26000, "BUILT FOR BIG BOOST AND BIG SHOTS", strength=3.0, rev=800, inertia=0.92),
    ]),
    ("headers", "HEADERS", "ENGINE", [
        STOCK("CAST MANIFOLD"),
        o("SHORTY HEADERS", 900, "EASY FIT, SMALL GAIN", mid=0.02, hi=0.02),
        o("LONG TUBE HEADERS", 2200, "BEST SCAVENGING", lo=0.01, mid=0.04, hi=0.05),
    ]),
    ("exhaust", "EXHAUST", "ENGINE", [
        STOCK(),
        o("CAT-BACK", 1200, "FREER AND LOUDER", hi=0.02),
        o("STRAIGHT PIPE", 1800, "NO CATS, NO MUFFLERS", mid=0.01, hi=0.04),
        o("SIDE PIPES", 3000, "OUT THE ROCKERS. LIGHT AND LOUD", mid=0.01, hi=0.04, weight=-6, visual="sidepipe"),
    ]),
    ("swap", "ENGINE SWAP", "ENGINE", [
        STOCK("ORIGINAL ENGINE"),
        o("STROKER KIT", 8000, "MORE DISPLACEMENT, MORE TORQUE", tq=0.16, weight=5),
        o("BIG CRATE ENGINE", 22000, "BIGGER MOTOR. HEAVY ON THE NOSE", tq=0.38, hi=0.04, weight=40, rear=-0.01, rev=200),
        o("RACE ENGINE", 55000, "PURPOSE-BUILT DRAG MOTOR", tq=0.62, hi=0.08, weight=30, rev=600, strength=1.9),
        o("K-SERIES SWAP", 30000, "JOE'S SPECIAL: A HIGH-REVVING HONDA HEART. ONLY FITS HIS EF", tq=0.85, hi=0.2, rev=1500, peakRpm=600,
          strength=2.0, weight=8, carOnly=["kite_ef"]),
    ]),
    # --- FUEL AND ECU ------------------------------------------------------------------------------
    ("fuel", "FUEL SYSTEM", "FUEL & ECU", [
        o("STOCK", 0, "FEEDS ABOUT STOCK POWER PLUS A BIT"),
        o("UPGRADED PUMP", 800, "SUPPORTS ~450 HP", fuelHp=450),
        o("BIG INJECTORS + PUMP", 2500, "SUPPORTS ~750 HP", fuelHp=750),
        o("RACE FUEL SYSTEM", 6000, "TWIN PUMPS, HUGE INJECTORS. ~1400 HP", fuelHp=1400),
    ]),
    ("fueltype", "FUEL", "FUEL & ECU", [
        o("PUMP 91", 0, "WHAT THE GAS STATION SELLS", octane=1.0),
        o("PUMP 93", 300, "A LITTLE MORE KNOCK MARGIN", octane=1.05),
        o("E85", 1500, "COOLS THE CHARGE, FIGHTS KNOCK. NEEDS 30% MORE FUEL", octane=1.25, fuelFlow=1.3, tq=0.02),
        o("RACE GAS 110", 3000, "THE MOST KNOCK MARGIN THERE IS", octane=1.35),
    ]),
    ("ecu", "ENGINE COMPUTER", "FUEL & ECU", [
        o("STOCK", 0, "LOCKED. NO TUNING"),
        o("PIGGYBACK", 1200, "TIMING +/-2 DEG", ecu=1, tq=0.02),
        o("REFLASH", 2500, "TIMING, AIR/FUEL, REV LIMIT, LAUNCH CONTROL", ecu=2, tq=0.04),
        o("STANDALONE", 6000, "EVERYTHING. ANTI-LAG ON THE TWO-STEP", ecu=3, tq=0.05),
    ]),
    # --- FORCED INDUCTION -------------------------------------------------------------------------
    ("turbo", "TURBO", "BOOST", [
        STOCK("NONE ADDED"),
        o("SMALL TURBO", 3500, "SPOOLS EARLY, RUNS OUT UP TOP. MAX 15 PSI", psi=15, spool=2600, lag=0.35),
        o("MEDIUM TURBO", 6000, "THE ALL-ROUNDER. MAX 22 PSI", psi=22, spool=3300, lag=0.6, visual="intercooler"),
        o("TWIN TURBOS", 11000, "EARLY SPOOL AND BIG POWER. MAX 28 PSI", psi=28, spool=3000, lag=0.55, visual="intercooler"),
        o("LARGE TURBO", 9000, "LAZY TO SPOOL, HUGE UP TOP. MAX 30 PSI", psi=30, spool=4100, lag=0.95, visual="intercooler"),
        o("88MM PRO MOD", 18000, "ALL OR NOTHING. MAX 40 PSI", psi=40, spool=5000, lag=1.4, visual="intercooler"),
    ], ["supercharger"]),
    ("intercooler", "INTERCOOLER", "BOOST", [
        o("NONE", 0, "HOT AIR: BOOST MAKES LESS POWER", icEff=0.78),
        o("SIDE MOUNT", 900, "SMALL, TUCKED AWAY", icEff=0.86),
        o("FRONT MOUNT", 1800, "BIG CORE IN THE BUMPER", icEff=0.93, drag=0.004),
        o("AIR-TO-WATER", 3500, "ICE TANK. COLDEST CHARGE", icEff=0.97, weight=12),
    ]),
    ("boostctl", "BOOST CONTROL", "BOOST", [
        o("WASTEGATE SPRING", 0, "BOOST FIXED AT 8 PSI", boostCtl=0),
        o("MANUAL CONTROLLER", 400, "SET THE BOOST YOURSELF", boostCtl=1),
        o("ELECTRONIC CONTROL", 1500, "BOOST BY GEAR (NEEDS STANDALONE ECU)", boostCtl=2),
    ]),
    ("bov", "BLOW-OFF VALVE", "BOOST", [
        o("NONE", 0, "BOOST DIES ON EVERY SHIFT"),
        o("BLOW-OFF VALVE", 350, "KEEPS THE TURBO SPINNING THROUGH SHIFTS", bov=1),
    ]),
    ("supercharger", "SUPERCHARGER", "BOOST", [
        STOCK("NONE ADDED"),
        o("ROOTS BLOWER", 5000, "INSTANT TORQUE, FADES UP TOP. 12 PSI", scPsi=12, scType=1, visual="blower"),
        o("TWIN-SCREW", 7500, "INSTANT AND EFFICIENT. 16 PSI", scPsi=16, scType=2, visual="blower"),
        o("CENTRIFUGAL", 6500, "BUILDS WITH RPM. 20 PSI AT REDLINE", scPsi=20, scType=3),
        o("14-71 HIGH HELIX", 16000, "TALL BLOWER THROUGH THE HOOD. 24 PSI", scPsi=24, scType=1, weight=25, drag=0.02, visual="blower"),
    ], ["turbo"]),
    # --- NITROUS ---------------------------------------------------------------------------------
    ("nitrous", "NITROUS KIT", "NITROUS", [
        STOCK("NONE ADDED"),
        o("DRY 75 SHOT", 900, "UP TO 75 HP", nosHp=75),
        o("WET 150 SHOT", 1800, "UP TO 150 HP", nosHp=150),
        o("DIRECT PORT 300", 5500, "A NOZZLE PER CYLINDER. UP TO 300 HP", nosHp=300),
        o("DUAL STAGE 500", 12000, "UP TO 500 HP. BRING A BUILT MOTOR", nosHp=500),
    ]),
    ("bottle", "BOTTLE", "NITROUS", [
        o("10 LB", 0, "ONE SPRAY", charges=1),
        o("15 LB", 500, "TWO SPRAYS", charges=2),
        o("TWIN 15 LB", 1200, "THREE SPRAYS", charges=3, weight=10),
    ]),
    ("nosctl", "NITROUS CONTROLLER", "NITROUS", [
        o("ON/OFF", 0, "THE WHOLE SHOT AT ONCE"),
        o("PROGRESSIVE", 1500, "RAMPS THE SHOT IN. SAVES THE TYRES", progressive=1),
    ]),
    # --- DRIVETRAIN ------------------------------------------------------------------------------
    ("clutch", "CLUTCH", "DRIVETRAIN", [
        STOCK(),
        o("STAGE 2 ORGANIC", 900, "HOLDS MORE, STILL SMOOTH", clutch=0.4, clutchTime=0.9),
        o("STAGE 3 PUCK", 1600, "GRABBY. HITS THE TYRES HARD", clutch=0.9, clutchTime=0.7),
        o("TWIN DISC", 3200, "HOLDS BIG POWER", clutch=1.5, clutchTime=0.8),
        o("TRIPLE DISC SLIPPER", 6500, "BUILT TO SLIP ON PURPOSE", clutch=2.4, clutchTime=1.0),
    ]),
    ("flywheel", "FLYWHEEL", "DRIVETRAIN", [
        STOCK(),
        o("LIGHT CHROMOLY", 600, "REVS QUICKER", inertia=0.82),
        o("ALUMINUM", 1100, "REVS FAST, BOGS EASY", inertia=0.65),
    ]),
    ("gearbox", "GEARBOX", "DRIVETRAIN", [
        STOCK(),
        o("CLOSE-RATIO", 2500, "TIGHTER GEARS, TUNABLE", shift=0.9, tuneRange=0.1),
        o("5-SPEED", 4500, "FIVE GEARS", gears=5, shift=0.85, tuneRange=0.2),
        o("6-SPEED", 6500, "SIX GEARS", gears=6, shift=0.8, tuneRange=0.3),
        o("DOG BOX", 12000, "STRAIGHT-CUT. NO SYNCHROS, FAST SHIFTS", gears=6, shift=0.5, tuneRange=0.45),
        o("SEQUENTIAL", 18000, "PULL BACK, PULL BACK", gears=6, shift=0.35, tuneRange=0.5),
    ]),
    ("shifter", "SHIFTER", "DRIVETRAIN", [
        STOCK(),
        o("SHORT THROW", 350, "SHORTER THROWS", shift=0.88),
    ]),
    ("driveshaft", "DRIVESHAFT / AXLES", "DRIVETRAIN", [
        STOCK(),
        o("ALUMINUM", 700, "LIGHTER TO SPIN UP", wheelInertia=0.92, weight=-4),
        o("CARBON FIBRE", 1600, "LIGHTEST", wheelInertia=0.85, weight=-7),
    ]),
    ("diff", "DIFFERENTIAL", "DRIVETRAIN", [
        STOCK("OPEN DIFF: ONE-WHEEL PEEL"),
        o("CLUTCH LSD", 1200, "BOTH WHEELS PUSH", grip=0.04),
        o("TORSEN", 1800, "SMOOTH AND STRONG", grip=0.05),
        o("SPOOL", 900, "LOCKED SOLID. PURE DRAG", grip=0.07),
    ]),
    ("drive", "DRIVE LAYOUT", "DRIVETRAIN", [
        STOCK("AS BUILT"),
        o("AWD CONVERSION", 15000, "JOE'S SPECIAL: ALL FOUR WHEELS PULL. ONLY FITS HIS EF", awd=1, weight=55, carOnly=["kite_ef"]),
    ]),
    ("final", "RING AND PINION", "DRIVETRAIN", [
        STOCK(),
        o("SHORTER (+8%)", 1000, "QUICKER OFF THE LINE", finalMult=1.08, finalRange=0.08),
        o("SHORTEST (+16%)", 1400, "ALL LAUNCH, LOW TOP SPEED", finalMult=1.16, finalRange=0.08),
        o("TALLER (-8%)", 1000, "MORE TOP END", finalMult=0.92, finalRange=0.08),
        o("QUICK-CHANGE", 3000, "ANY RATIO YOU LIKE", finalRange=0.35),
    ]),
    # --- SUSPENSION ------------------------------------------------------------------------------
    ("springs", "SPRINGS", "SUSPENSION", [
        STOCK(),
        o("DRAG SPRINGS", 700, "SOFT REAR, MORE SQUAT", transfer=1.12),
        o("COILOVERS", 2800, "ADJUSTABLE RIDE HEIGHT", transfer=1.08, adjustable=1),
    ]),
    ("shocks", "SHOCKS", "SUSPENSION", [
        STOCK(),
        o("90/10 DRAG SHOCKS", 1100, "FRONT LIFTS FAST, WEIGHT HITS THE REAR", transferRate=1.8),
        o("DOUBLE ADJUSTABLE", 2600, "TUNE HOW FAST IT TRANSFERS", transferRate=1.6, adjustable=2),
    ]),
    ("bars", "TRACTION BARS", "SUSPENSION", [
        STOCK(),
        o("TRACTION BARS", 500, "STOPS AXLE WRAP", grip=0.02, rwdOnly=True),
        o("LADDER BARS", 1500, "PLANTS THE REAR AXLE", grip=0.045, rwdOnly=True),
    ]),
    ("wheeliebars", "WHEELIE BARS", "SUSPENSION", [
        o("NONE", 0, "TOO MUCH WHEELIE AND YOU LIFT"),
        o("WHEELIE BARS", 900, "FRONT STAYS DOWN, NO LIFTING", wheelieBars=1, weight=8, rwdOnly=True, visual="wheeliebar"),
    ]),
    ("tubs", "WHEEL TUBS", "SUSPENSION", [
        STOCK(),
        o("MINI-TUBS", 2500, "ROOM FOR WIDE TYRES", tubs=1, weight=6),
    ]),
    ("cage", "ROLL CAGE", "SUSPENSION", [
        o("NONE", 0, "FACTORY SHELL"),
        o("6-POINT BAR", 1500, "STIFFER CHASSIS", grip=0.01, weight=18, visual="cage"),
        o("10-POINT CAGE", 3500, "STIFFEST. A RACE CAR", grip=0.025, weight=30, visual="cage"),
    ]),
    # --- TYRES AND WHEELS -------------------------------------------------------------------------
    ("tires", "TYRES", "TYRES", [
        o("STREET", 0, "ALL-SEASON", warm=0.7, sens=1.0),
        o("SPORT", 1200, "STICKIER STREET TYRE", grip=0.06, warm=0.72, sens=1.0),
        o("275 DRAG RADIAL", 2400, "STREET-LEGAL DRAG TYRE", grip=0.16, warm=0.8, sens=1.3),
        o("315 DRAG RADIAL", 3600, "WIDER. NEEDS TUBS OR WIDEBODY", grip=0.22, warm=0.82, sens=1.4, requires=WIDE),
        o("SLICKS", 4800, "HUGE GRIP WHEN HOT. DO A BURNOUT", grip=0.32, warm=0.9, sens=1.8, requires=WIDE),
    ]),
    ("wheels", "WHEELS", "TYRES", [
        STOCK(),
        o("FORGED LIGHTWEIGHT", 2400, "LESS TO SPIN UP", wheelInertia=0.85, weight=-8),
        o("BEADLOCKS", 3200, "RUN THE PRESSURE LOW", wheelInertia=0.82, weight=-6, sens=1.25),
    ]),
    # --- WEIGHT ---------------------------------------------------------------------------------
    ("interior", "INTERIOR", "WEIGHT", [
        STOCK(),
        o("STRIP THE REAR", 500, "NO REAR SEATS, NO CARPET", weight=-35),
        o("FULL GUT", 1800, "ONE RACE SEAT. NOTHING ELSE", weight=-80),
    ]),
    ("panels", "BODY PANELS", "WEIGHT", [
        STOCK(),
        o("FIBERGLASS HOOD", 900, "LIGHT HOOD", weight=-12),
        o("CARBON HOOD + TRUNK", 3500, "BARE CARBON", weight=-28, visual="carbon"),
        o("FULL CARBON PANELS", 9500, "EVERY PANEL", weight=-60, visual="carbon"),
    ]),
    ("glass", "GLASS", "WEIGHT", [
        STOCK(),
        o("LEXAN REAR + SIDES", 800, "PLASTIC WINDOWS", weight=-14),
        o("FULL LEXAN", 1500, "EVEN THE WINDSHIELD", weight=-22),
    ]),
    ("battery", "BATTERY", "WEIGHT", [
        STOCK("UNDER THE HOOD"),
        o("TRUNK RELOCATION", 300, "MOVES WEIGHT TO THE REAR", rear=0.012),
        o("LIGHT BATTERY IN TRUNK", 800, "LIGHTER AND REARWARD", rear=0.012, weight=-11),
    ]),
    # --- BODY: drawn on the car, and they change the aero ---------------------------------------
    ("bumper", "FRONT BUMPER", "BODY", [
        STOCK(),
        o("LIP SPOILER", 600, "A LITTLE LOWER, A LITTLE SLIPPERIER", drag=-0.005, visual="bumper1"),
        o("STREET BUMPER", 1500, "AGGRESSIVE INTAKES", drag=-0.008, weight=-2, visual="bumper2"),
        o("RACE SPLITTER", 2800, "FLAT SPLITTER, CANARDS", drag=-0.012, weight=-3, visual="bumper3"),
    ]),
    ("skirts", "SIDE SKIRTS", "BODY", [
        STOCK(),
        o("SIDE SKIRTS", 900, "CLEANS UP THE SIDES", drag=-0.006, visual="skirts1"),
        o("RACE SKIRTS", 1800, "LOW AND FLAT", drag=-0.01, visual="skirts2"),
    ]),
    ("rear", "REAR BUMPER", "BODY", [
        STOCK(),
        o("REAR DIFFUSER", 1200, "SMOOTHS THE WAKE", drag=-0.006, visual="rear1"),
        o("RACE DIFFUSER", 2400, "BIG FINS", drag=-0.01, visual="rear2"),
    ]),
    ("hood", "HOOD", "BODY", [
        STOCK(),
        o("VENTED HOOD", 1200, "LETS THE HEAT OUT", visual="hood1"),
        o("COWL INDUCTION", 1600, "THE CLASSIC BULGE", hi=0.01, visual="hood2"),
    ]),
    ("fenders", "FENDERS", "BODY", [
        STOCK(),
        o("FENDER FLARES", 1500, "A LITTLE WIDER", drag=0.004, visual="flares"),
        o("WIDEBODY KIT", 6000, "ROOM FOR WIDE TYRES", drag=0.012, weight=8, visual="widebody"),
    ]),
    ("chute", "PARACHUTE", "BODY", [
        o("NONE", 0, "BRAKES ONLY"),
        o("DRAG CHUTE", 1200, "POPS AFTER THE FINISH LINE", weight=4, visual="chute"),
    ]),
]

COSMETIC = [
    {"id": "rims", "name": "RIMS", "group": "LOOKS",
     "styles": ["stock", "magnum", "rally", "fivestar", "mesh", "turbine", "phone", "three", "sixspoke", "mesh_black", "steelie_black", "split_black"], "price": 2500},
    {"id": "wing", "name": "SPOILER", "group": "LOOKS", "styles": ["stock", "ducktail", "gt", "drag"], "price": 3000, "dragArea": [0, 0.004, 0.012, 0.02]},
    {"id": "tint", "name": "WINDOW TINT", "group": "LOOKS", "styles": ["none", "light", "limo"], "price": 400},
    {"id": "underglow", "name": "UNDERGLOW", "group": "LOOKS", "styles": ["off", "cyan", "magenta", "green", "orange", "purple"], "price": 1500},
]


def paint(p):
    """Paint options; the CREW decal puts your crew's emblem on the door."""
    if not any(d["name"] == "CREW" for d in p["decals"]):
        p["decals"].append({"name": "CREW", "price": 2000})
    return p


def main():
    path = ROOT / "assets/data/parts.json"
    old = json.loads(path.read_text())
    slots = []
    for row in SLOTS:
        sid, name, group, options = row[:4]
        excludes = row[4] if len(row) > 4 else []
        slots.append({"id": sid, "name": name, "group": group, "options": options, "excludes": excludes})
    out = {
        "version": 2,
        "classMultiplier": old["classMultiplier"],
        "slots": slots,
        "cosmetic": COSMETIC,
        "paint": paint(old["paint"]),
    }
    path.write_text(json.dumps(out, indent=1) + "\n")
    print(len(slots), "slots,", sum(len(s["options"]) for s in slots) - len(slots), "parts")


if __name__ == "__main__":
    main()
