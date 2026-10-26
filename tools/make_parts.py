"""Writes assets/data/parts.json: every part slot, its options and their effects, the engines the
swap kits put in, and the parts section of car_references.md (fictional brands -> real makers).

Option 0 of every slot is what the car left the factory with (free, always owned). Options are
only ever appended to a slot (saves store owned parts by index). Prices are for a STREET-class car
and scale with the car's stock class (classMultiplier). Effect keys:

  lo / mid / hi      torque added near idle / at the torque peak / at peak power and up (shares)
  tq                 torque added across the whole curve
  peakRpm            moves the torque and power peaks (rpm)
  rev                raises the rev limit the valvetrain allows (rpm)
  idle               idle rpm change (big cams, porting)
  strength           knock limit from the bottom end (or apex seals): total cylinder-pressure multiplier it takes
  octane             knock limit multiplier from the fuel; fuelFlow = how much more fuel it needs
  fuelHp             crank horsepower the pump and injectors can feed
  ecu                0 stock, 1 piggyback, 2 reflash, 3 standalone (unlocks tuning)
  launch             1 two-step launch control, 2 two-step plus anti-lag (needs a turbo)
  psi, spool, lag    turbo: most boost, rpm it starts making boost, seconds to spool
  psiAdd, lagMult    an external wastegate holds more boost; a downpipe spools the turbo sooner
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
Sound only (the physics ignores them; audio/Synth.kt reads them):
  loud, rasp         exhaust volume and crackle; lope = choppy idle (cams, porting); growl = induction noise
  whistle            turbo whistle size; screamer = wastegate scream; bovType 1 vent-to-atmosphere, 2 recirculating
Rules:
  requires           [[slot, minOption], ...] any one of them; requiresAll = every one of them
  rwdOnly; carOnly = car ids it fits; engine = the engine a swap kit puts in (ENGINES)
  engineType "piston" or "rotary": the part does nothing on the other kind (follows a swapped engine)
  boostOnly          does nothing without a turbo (downpipes)
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent


def o(name, price, info, brand=None, **fx):
    d = {"name": name, "price": price, "info": info}
    if brand:
        d["brand"] = brand
    d.update(fx)
    return d


STOCK = lambda info="FACTORY PART": o("STOCK", 0, info)

WIDE = [["tubs", 1], ["fenders", 2]]
PISTON = {"engineType": "piston"}
ROTARY_FX = {"engineType": "rotary"}


def _roster():
    cars = {}
    for f in (ROOT / "assets/data/cars").glob("*.json"):
        d = json.loads(f.read_text())
        cars[d["id"]] = d
    return cars


CARS = _roster()
ROTARIES = sorted(c for c, d in CARS.items() if d["engine"]["layout"] == "rotary")
# factory twin-turbo and sequential setups (the twin-to-single conversion)
TWIN_TURBO = ["kaminari_mk1", "kaminari_mk2", "kaminari_mk3", "tsunami", "spiral_r", "typhoon", "zephyr_tt", "wal_awd", "quaranta"]

# Swaps enthusiasts actually do, per platform
SWAPS = {
    "k24": ["kite", "kite_ef", "kite_rx", "dart", "arrow"],
    "b18c": ["kite", "kite_ef", "kite_rx", "dart"],
    "h22": ["kite", "kite_ef", "dart"],
    "rb26": ["sylph_13", "sylph_14", "sylph_15", "sylph_180", "zephyr_240", "kaminari_c10"],
    "2jz": ["tsunami_mk3", "pursuit", "kestrel"],
    "ls": ["spiral_sa", "spiral_turbo", "spiral_r", "zephyr_240", "zephyr_tt", "kokoro", "sylph_13", "sylph_14", "isar_s3",
           "tsunami_mk3", "phantom"],
    "bb572": ["mammoth", "condor"],
    "coyote": ["ranger_500", "mamba"],
    "13brew": ["spiral_sa", "spiral_turbo", "prairie"],
    "20b": ROTARIES,
    "26b": ROTARIES,
}

# What a swap puts in: replaces the car's engine numbers (net ratings). mass = the engine's kg; the
# car gains or loses the difference from its own engine (LAYOUT_MASS).
ENGINES = {
    "k24": dict(name="K-SERIES (K24/K20 HYBRID)", hp=245, peakHpRpm=7800, torqueNm=255, peakTorqueRpm=6200, redline=8400, idleRpm=850,
                layout="i4", cylinders=4, turbo=False, vtecRpm=5500, mass=125),
    "b18c": dict(name="B18C TYPE R", hp=195, peakHpRpm=8000, torqueNm=178, peakTorqueRpm=7500, redline=8400, idleRpm=850,
                 layout="i4", cylinders=4, turbo=False, vtecRpm=5700, mass=125),
    "h22": dict(name="H22A", hp=217, peakHpRpm=7200, torqueNm=221, peakTorqueRpm=6700, redline=7800, idleRpm=800,
                layout="i4", cylinders=4, turbo=False, vtecRpm=5500, mass=145),
    "rb26": dict(name="RB26DETT TWIN TURBO", hp=276, peakHpRpm=6800, torqueNm=353, peakTorqueRpm=4400, redline=8000, idleRpm=900,
                 layout="i6", cylinders=6, turbo=True, lag=0.45, mass=230),
    "2jz": dict(name="2JZ-GTE TWIN TURBO", hp=320, peakHpRpm=5600, torqueNm=427, peakTorqueRpm=4000, redline=6800, idleRpm=750,
                layout="i6", cylinders=6, turbo=True, lag=0.4, spoolRpm=2400, mass=230),
    "ls": dict(name="LS1 5.7 V8", hp=345, peakHpRpm=5600, torqueNm=475, peakTorqueRpm=4400, redline=6400, idleRpm=750,
               layout="v8", cylinders=8, turbo=False, mass=200),
    "bb572": dict(name="572 BIG-BLOCK CRATE", hp=620, peakHpRpm=5500, torqueNm=881, peakTorqueRpm=4400, redline=6200, idleRpm=850,
                  layout="v8", cylinders=8, turbo=False, mass=310),
    "coyote": dict(name="COYOTE 5.0 V8", hp=435, peakHpRpm=6500, torqueNm=542, peakTorqueRpm=4250, redline=7000, idleRpm=700,
                   layout="v8", cylinders=8, turbo=False, mass=205),
    "13brew": dict(name="13B-REW SEQUENTIAL TWIN TURBO", hp=276, peakHpRpm=6500, torqueNm=314, peakTorqueRpm=5000, redline=8000,
                   idleRpm=850, layout="rotary", cylinders=2, turbo=True, lag=0.45, spoolRpm=3000, mass=125),
    "20b": dict(name="20B-REW 3-ROTOR TWIN TURBO", hp=276, peakHpRpm=6500, torqueNm=402, peakTorqueRpm=3000, redline=8000,
                idleRpm=850, layout="rotary", cylinders=3, turbo=True, lag=0.5, spoolRpm=2400, mass=150),
    "26b": dict(name="26B 4-ROTOR, BRIDGE PORTED", hp=520, peakHpRpm=8500, torqueNm=480, peakTorqueRpm=7000, redline=9500,
                idleRpm=1100, layout="rotary", cylinders=4, turbo=False, mass=185),
}

# Rough kg of a stock engine by layout (rotaries by rotor count)
LAYOUT_MASS = {"i4": 140, "i6": 220, "v6": 175, "v8": 230, "v10": 280, "v12": 270, "flat4": 135, "flat6": 190, "flat12": 260,
               "rotary2": 115, "rotary3": 150, "rotary4": 185}


def swap(engine, price, info, brand, strength=0.0, **rules):
    e = ENGINES[engine]
    fx = dict(engine=engine, carOnly=SWAPS[engine], **rules)
    if strength:
        fx["strength"] = strength
    return o(SHORT[engine] + " SWAP", price, info, brand, **fx)


# Swap kit names in the shop list (the engine's full name is in ENGINES)
SHORT = {"k24": "K-SERIES", "b18c": "B18C", "h22": "H22", "rb26": "RB26", "2jz": "2JZ", "ls": "LS V8", "bb572": "572 BIG-BLOCK",
         "coyote": "COYOTE V8", "13brew": "13B-REW", "20b": "20B 3-ROTOR", "26b": "26B 4-ROTOR"}


SLOTS = [
    # --- BREATHING: in and out ------------------------------------------------------------------
    ("intake", "AIR INTAKE", "BREATHING", [
        STOCK("FACTORY AIRBOX"),
        o("COLD AIR INTAKE", 900, "COOLER AIR, MORE EVERYWHERE. YOU HEAR IT", "AIRHAMMER", lo=0.01, mid=0.02, hi=0.03, growl=0.5),
        o("RAM AIR", 2200, "HOOD SCOOP FEEDS IT. BEST UP TOP", "AIRHAMMER", mid=0.02, hi=0.05, growl=0.6, visual="scoop"),
        o("SHORT RAM", 500, "CONE FILTER IN THE BAY: LOUD, SMALL GAIN", "AIRHAMMER", mid=0.01, hi=0.02, growl=0.7),
    ]),
    ("throttle", "THROTTLE BODY", "BREATHING", [
        STOCK(),
        o("BIG BORE", 700, "MORE AIR AT HIGH RPM", "STACKWELL", mid=0.01, hi=0.03),
        o("ITBS + STACKS", 4500, "A TRUMPET PER CYLINDER. PEAKY, AND IT HOWLS", "STACKWELL",
          lo=-0.02, mid=0.03, hi=0.07, peakRpm=150, growl=1.0, requiresAll=[["ecu", 1]]),
    ]),
    ("manifold", "INTAKE MANIFOLD", "BREATHING", [
        STOCK(),
        o("PORTED", 1800, "SMOOTHER RUNNERS", "KOBUN", mid=0.02, hi=0.03),
        o("LONG RUNNER", 3000, "FAT MIDRANGE, FADES UP TOP", "KOBUN", lo=0.05, mid=0.05, hi=-0.02, peakRpm=-200),
        o("SHORT RUNNER", 3500, "SOFT DOWN LOW, SCREAMS UP TOP", "KOBUN", lo=-0.04, mid=0.01, hi=0.08, peakRpm=250),
    ]),
    ("headers", "HEADERS", "BREATHING", [
        STOCK("CAST MANIFOLD"),
        o("SHORTY HEADERS", 900, "EASY FIT, SMALL GAIN, A LITTLE RASP", "HOOKLINE", mid=0.02, hi=0.02, rasp=0.4),
        o("LONG TUBE HEADERS", 2200, "BEST SCAVENGING. RASPY", "HOOKLINE", lo=0.01, mid=0.04, hi=0.05, rasp=0.7),
    ]),
    ("downpipe", "DOWNPIPE", "BREATHING", [
        STOCK("FACTORY DOWNPIPE AND CAT"),
        o("HIGH-FLOW DOWNPIPE", 900, "TURBO CARS: SPOOLS SOONER, MORE UP TOP", "KOBUN", hi=0.03, lagMult=0.92, loud=0.1, boostOnly=True),
        o("TEST PIPE", 600, "TURBO CARS: NO CAT AT ALL. LOUD, QUICK SPOOL", "KOBUN", hi=0.05, lagMult=0.86, loud=0.25, rasp=0.2, boostOnly=True),
    ]),
    ("exhaust", "EXHAUST", "BREATHING", [
        STOCK(),
        o("CAT-BACK", 1200, "FREER AND LOUDER", "THUNDERBORE", hi=0.02, loud=0.3),
        o("STRAIGHT PIPE", 1800, "NO CATS, NO MUFFLERS. ROUGH AND LOUD", "THUNDERBORE", mid=0.01, hi=0.04, loud=0.75, rasp=0.5),
        o("SIDE PIPES", 3000, "OUT THE ROCKERS. LIGHT AND LOUD", "THUNDERBORE", mid=0.01, hi=0.04, weight=-6, loud=0.8, rasp=0.4, visual="sidepipe"),
        o("SIDE EXIT", 1600, "EXITS BEFORE THE REAR WHEEL. SHORT, LOUD", "THUNDERBORE", mid=0.01, hi=0.03, weight=-4,
          loud=0.6, rasp=0.3, visual="sideexit"),
        o("DUAL STRAIGHT PIPES", 2600, "TWO BIG PIPES OUT THE BACK. THE LOUDEST", "THUNDERBORE", mid=0.01, hi=0.05, weight=3, loud=1.0,
          rasp=0.6, visual="dual"),
    ]),
    # --- ENGINE: the rotating assembly, and swaps ---------------------------------------------------
    ("heads", "CYLINDER HEADS", "ENGINE", [
        STOCK(),
        o("PORTED HEADS", 4000, "BIGGER PORTS, BETTER FLOW", "CROWLEY", mid=0.03, hi=0.05, rev=200, **PISTON),
        o("CNC RACE HEADS", 9000, "MACHINED PORTS, STIFF SPRINGS", "CROWLEY", mid=0.05, hi=0.09, rev=500, **PISTON),
    ]),
    ("cams", "CAMSHAFTS", "ENGINE", [
        STOCK(),
        o("STREET CAM", 1500, "MILD. KEEPS THE IDLE SMOOTH", "CROWLEY", lo=-0.01, mid=0.03, hi=0.05, peakRpm=150, lope=0.25, **PISTON),
        o("HOT CAM", 3200, "LUMPY IDLE, PULLS HARD UP TOP", "CROWLEY", lo=-0.05, mid=0.03, hi=0.10, peakRpm=350, rev=200, idle=100,
          lope=0.6, **PISTON),
        o("RACE CAM", 6500, "ALL TOP END, CHOPPY IDLE. NEEDS SPRINGS", "CROWLEY", lo=-0.12, mid=0.02, hi=0.16,
          peakRpm=650, rev=400, idle=250, lope=1.0, requiresAll=[["valvesprings", 1]], **PISTON),
    ]),
    ("valvesprings", "VALVE SPRINGS", "ENGINE", [
        STOCK("FACTORY SPRINGS: FLOAT PAST REDLINE"),
        o("UPGRADED SPRINGS", 600, "STIFFER SPRINGS, STEEL RETAINERS. +400 RPM", "CROWLEY", rev=400, **PISTON),
        o("DUAL SPRINGS + TI", 1400, "FOR BIG CAMS AND BIG REVS. +800 RPM", "CROWLEY", rev=800, **PISTON),
    ]),
    ("internals", "BOTTOM END", "ENGINE", [
        o("STOCK", 0, "CAST PISTONS. KNOCKS PAST ~7 PSI ON PUMP GAS", strength=1.35, **PISTON),
        o("FORGED PISTONS+RODS", 5000, "TAKES REAL BOOST", "WISEMAN", strength=1.7, rev=200, **PISTON),
        o("FORGED ASSEMBLY", 11000, "FORGED CRANK, RODS, PISTONS", "KARRO", strength=2.2, rev=500, inertia=0.95, **PISTON),
        o("BILLET RACE BLOCK", 26000, "BUILT FOR BIG BOOST AND BIG SHOTS", "KARRO", strength=3.0, rev=800, inertia=0.92, **PISTON),
    ]),
    ("swap", "ENGINE SWAP", "ENGINE", [
        STOCK("ORIGINAL ENGINE"),
        o("STROKER KIT", 8000, "MORE DISPLACEMENT, MORE TORQUE", "CROWLEY", tq=0.16, weight=5, **PISTON),
        o("BIG-BORE LONG BLOCK", 22000, "THE SAME FAMILY, BORED AND STROKED. HEAVIER", "KARRO", tq=0.38, hi=0.04, weight=40, rear=-0.01, rev=200,
          **PISTON),
        o("RACE ENGINE", 55000, "PURPOSE-BUILT DRAG VERSION OF YOUR ENGINE", "KARRO", tq=0.62, hi=0.08, weight=30, rev=600, strength=1.9,
          **PISTON),
        swap("k24", 15000, "THE HONDA SWAP: HIGH REVS, VTEC AT 5,500", "HASLER", strength=1.8),
        swap("rb26", 32000, "THE GODZILLA SIX: TWIN TURBOS, LOVES BOOST", "COLLIN", strength=1.9),
        swap("2jz", 34000, "THE STRONGEST STOCK BOTTOM END THERE IS", "COLLIN", strength=2.3),
        swap("ls", 18000, "FITS EVERYTHING. A LIGHT ALLOY V8", "SIKKA", strength=1.6),
        swap("bb572", 28000, "9.4 LITRES. HEAVY, ENORMOUS TORQUE", "WEYLAND", strength=1.7),
        swap("coyote", 24000, "MODERN FOUR-CAM V8 IN A CLASSIC", "HARLAN RACING", strength=1.6),
        swap("b18c", 9000, "THE TYPE R HEART: SCREAMS TO 8,400", "HASLER", strength=1.4),
        swap("h22", 8000, "BIG HONDA TORQUE, VTEC AT 5,500", "HASLER", strength=1.4),
        swap("13brew", 16000, "THE FD'S SEQUENTIAL TWIN-TURBO ROTARY", "ROTOHEAD"),
        swap("20b", 38000, "THREE ROTORS: DEEPER, SMOOTHER, MUCH MORE", "ROTOHEAD", requiresAll=[["fuel", 1], ["ecu", 2]]),
        swap("26b", 75000, "THE ULTIMATE: FOUR ROTORS TO 9,500 RPM", "ROTOHEAD",
             requiresAll=[["fuel", 2], ["ecu", 3], ["oilinject", 1]]),
    ]),
    # --- ROTARY: only the Wankels take these ---------------------------------------------------------
    ("porting", "PORTING", "ROTARY", [
        STOCK("FACTORY PORTS"),
        o("STREET PORT", 2500, "BIGGER PORTS: MORE TOP END, LUMPIER IDLE", "ROTOHEAD", lo=-0.02, mid=0.03, hi=0.07, peakRpm=400,
          rev=300, idle=50, lope=0.35, carOnly=ROTARIES, **ROTARY_FX),
        o("BRIDGE PORT", 5500, "HITS HARD UP TOP. IDLES BRAP BRAP BRAP", "ROTOHEAD", lo=-0.08, mid=0.02, hi=0.15,
          peakRpm=900, rev=800, idle=200, lope=0.7, carOnly=ROTARIES, **ROTARY_FX),
        o("PERIPHERAL PORT", 11000, "RACE PORTS: NOTHING LOW, EVERYTHING HIGH", "ROTOHEAD", lo=-0.16, hi=0.26,
          peakRpm=1500, rev=1500, idle=400, lope=1.0, carOnly=ROTARIES, requiresAll=[["apexseals", 1], ["oilinject", 1]], **ROTARY_FX),
    ]),
    ("apexseals", "APEX SEALS", "ROTARY", [
        o("STOCK", 0, "FACTORY 3-PIECE SEALS. EASY ON THE BOOST", strength=1.25, **ROTARY_FX),
        o("2MM STEEL SEALS", 2200, "TAKES REAL BOOST", "IANNO", strength=1.7, carOnly=ROTARIES, **ROTARY_FX),
        o("CERAMIC SEALS", 5000, "FOR BIG BOOST AND BIG REVS. +500 RPM", "IANNO", strength=2.4, rev=500, carOnly=ROTARIES, **ROTARY_FX),
    ]),
    ("oilinject", "OIL INJECTION", "ROTARY", [
        STOCK("FACTORY METERING PUMP"),
        o("OMP + PREMIX", 700, "MORE OIL TO THE SEALS. +300 RPM", "ROTOHEAD", rev=300, carOnly=ROTARIES, **ROTARY_FX),
        o("BILLET OIL PUMP", 1800, "FEEDS A HIGH-REVVING BUILD. +600 RPM", "ROTOHEAD", rev=600, carOnly=ROTARIES, **ROTARY_FX),
    ]),
    # --- FUEL AND ECU ------------------------------------------------------------------------------
    ("fuel", "FUEL SYSTEM", "FUEL & ECU", [
        o("STOCK", 0, "FEEDS ABOUT STOCK POWER PLUS A BIT"),
        o("UPGRADED PUMP", 800, "SUPPORTS ~450 HP", "WALDEN", fuelHp=450),
        o("BIG INJECTORS + PUMP", 2500, "SUPPORTS ~750 HP", "INJEX", fuelHp=750),
        o("RACE FUEL SYSTEM", 6000, "TWIN PUMPS, HUGE INJECTORS. ~1400 HP", "INJEX", fuelHp=1400),
        o("STAGED INJECTION", 3500, "SECONDARY INJECTORS PER ROTOR. ~1000 HP", "ROTOHEAD", fuelHp=1000, carOnly=ROTARIES, **ROTARY_FX),
    ]),
    ("fueltype", "FUEL", "FUEL & ECU", [
        o("PUMP 91", 0, "WHAT THE GAS STATION SELLS", octane=1.0),
        o("PUMP 93", 300, "A LITTLE MORE KNOCK MARGIN", octane=1.05),
        o("E85 CONVERSION", 1500, "FIGHTS KNOCK. NEEDS BIG INJECTORS", "INJEX", octane=1.25, fuelFlow=1.3, tq=0.02,
          requiresAll=[["fuel", 2]]),
        o("RACE GAS 110", 3000, "THE MOST KNOCK MARGIN THERE IS", octane=1.35),
    ]),
    ("ecu", "ENGINE COMPUTER", "FUEL & ECU", [
        o("STOCK", 0, "LOCKED. NO TUNING"),
        o("PIGGYBACK", 1200, "TIMING +/-2 DEG", "SPLICE", ecu=1, tq=0.02),
        o("REFLASH", 2500, "TIMING, AIR/FUEL, REV LIMIT", "REFLEXA", ecu=2, tq=0.04),
        o("STANDALONE", 6000, "EVERYTHING. BOOST BY GEAR, ANTI-LAG READY", "HALTRON", ecu=3, tq=0.05),
    ]),
    ("launch", "LAUNCH CONTROL", "FUEL & ECU", [
        STOCK("YOUR RIGHT FOOT"),
        o("2-STEP LAUNCH", 900, "HOLDS THE REVS ON THE LINE. POPS AND BANGS", "HALTRON", launch=1, requiresAll=[["ecu", 2]]),
        o("2-STEP + ANTI-LAG", 3500, "LEAVE ON BOOST. FLAMES WHILE STAGED", "MOTEX", launch=2, requiresAll=[["ecu", 3]]),
    ]),
    # --- FORCED INDUCTION -------------------------------------------------------------------------
    ("turbo", "TURBO", "BOOST", [
        STOCK("NONE ADDED"),
        o("SMALL TURBO", 3500, "SPOOLS EARLY, RUNS OUT UP TOP. MAX 15 PSI", "GARNET", psi=15, spool=2600, lag=0.35, whistle=0.3),
        o("MEDIUM TURBO", 6000, "THE ALL-ROUNDER. MAX 22 PSI", "GARNET", psi=22, spool=3300, lag=0.6, whistle=0.5, visual="intercooler"),
        o("TWIN TURBOS", 11000, "SINGLE TO TWIN: EARLY SPOOL. MAX 28 PSI", "KOBUN", psi=28, spool=3000, lag=0.55,
          whistle=0.6, visual="intercooler", requiresAll=[["fuel", 2], ["ecu", 2]]),
        o("LARGE TURBO", 9000, "LAZY TO SPOOL, HUGE UP TOP. MAX 30 PSI", "PRECISE", psi=30, spool=4100, lag=0.95, whistle=0.8,
          visual="intercooler", requiresAll=[["fuel", 2], ["ecu", 2]]),
        o("88MM PRO MOD", 18000, "ALL OR NOTHING. MAX 40 PSI", "PRECISE", psi=40, spool=5000, lag=1.4, whistle=1.0, visual="intercooler",
          requiresAll=[["fuel", 3], ["ecu", 3]]),
        o("BALL-BEARING TURBO", 8500, "SPOOLS SMALL, PULLS BIG. MAX 24 PSI", "GARNET", psi=24, spool=3000, lag=0.38,
          whistle=0.7, visual="intercooler", requiresAll=[["fuel", 1], ["ecu", 2]]),
        o("TWIN-TO-SINGLE", 12000, "ONE BIG TURBO FOR THE TWINS. MAX 32 PSI", "PRECISE", psi=32, spool=4000, lag=0.8,
          whistle=0.9, visual="intercooler", carOnly=TWIN_TURBO, requiresAll=[["fuel", 2], ["ecu", 2]]),
    ], ["supercharger"]),
    ("intercooler", "INTERCOOLER", "BOOST", [
        o("NONE", 0, "HOT AIR: BOOST MAKES LESS POWER", icEff=0.78),
        o("SIDE MOUNT", 900, "SMALL, TUCKED AWAY", "KOBUN", icEff=0.86),
        o("FRONT MOUNT", 1800, "BIG CORE IN THE BUMPER", "KOBUN", icEff=0.93, drag=0.004),
        o("AIR-TO-WATER", 3500, "ICE TANK. COLDEST CHARGE", "TURBOZEN", icEff=0.97, weight=12),
    ]),
    ("boostctl", "BOOST CONTROL", "BOOST", [
        o("WASTEGATE SPRING", 0, "BOOST FIXED AT 8 PSI", boostCtl=0),
        o("MANUAL CONTROLLER", 400, "SET THE BOOST YOURSELF", "TURBOZEN", boostCtl=1),
        o("ELECTRONIC CONTROL", 1500, "BOOST BY GEAR (NEEDS STANDALONE ECU)", "TURBOZEN", boostCtl=2),
    ]),
    ("wastegate", "WASTEGATE", "BOOST", [
        STOCK("INTERNAL WASTEGATE"),
        o("EXTERNAL WASTEGATE", 1100, "HOLDS BOOST FLAT: 3 PSI MORE ON TAP", "TIDEGATE", psiAdd=3),
        o("GATE + SCREAMER", 1300, "THE SAME, VENTED TO THE AIR. IT SCREAMS", "TIDEGATE", psiAdd=3, screamer=1.0, loud=0.15),
    ]),
    ("bov", "BLOW-OFF VALVE", "BOOST", [
        o("NONE", 0, "BOOST DIES ON EVERY SHIFT. FLUTTERS"),
        o("ATMOSPHERIC BOV", 350, "KEEPS IT SPOOLED THROUGH SHIFTS. PSSSH", "TURBOZEN", bov=1, bovType=1),
        o("RECIRCULATING BOV", 300, "KEEPS THE TURBO SPINNING, QUIETLY", "TURBOZEN", bov=1, bovType=2),
    ]),
    ("supercharger", "SUPERCHARGER", "BOOST", [
        STOCK("NONE ADDED"),
        o("ROOTS BLOWER", 5000, "INSTANT TORQUE, FADES UP TOP. 12 PSI. WHINES", "WEYLAND", scPsi=12, scType=1, visual="blower"),
        o("TWIN-SCREW", 7500, "INSTANT AND EFFICIENT. 16 PSI", "WHIPSAW", scPsi=16, scType=2, visual="blower"),
        o("CENTRIFUGAL", 6500, "BUILDS WITH RPM TO 20 PSI. A RISING SIREN", "VORTRAX", scPsi=20, scType=3),
        o("14-71 HIGH HELIX", 16000, "TALL BLOWER THROUGH THE HOOD. 24 PSI", "WEYLAND", scPsi=24, scType=1, weight=25, drag=0.02, visual="blower",
          requiresAll=[["fuel", 2]]),
    ], ["turbo"]),
    # --- NITROUS ---------------------------------------------------------------------------------
    ("nitrous", "NITROUS KIT", "NITROUS", [
        STOCK("NONE ADDED"),
        o("DRY 75 SHOT", 900, "UP TO 75 HP. THE ECU ADDS THE FUEL", "NOVUS", nosHp=75),
        o("WET 150 SHOT", 1800, "UP TO 150 HP. ITS OWN FUEL", "NOVUS", nosHp=150),
        o("DIRECT PORT 300", 5500, "A NOZZLE PER CYLINDER. UP TO 300 HP", "NOVUS", nosHp=300, requiresAll=[["fuel", 1]]),
        o("DUAL STAGE 500", 12000, "UP TO 500 HP. BRING A BUILT MOTOR", "NOVUS", nosHp=500, requires=[["internals", 1], ["apexseals", 1]],
          requiresAll=[["fuel", 2]]),
    ]),
    ("bottle", "BOTTLE", "NITROUS", [
        o("10 LB", 0, "ONE SPRAY", charges=1),
        o("15 LB", 500, "TWO SPRAYS", "NOVUS", charges=2),
        o("TWIN 15 LB", 1200, "THREE SPRAYS", "NOVUS", charges=3, weight=10),
    ]),
    ("nosctl", "NITROUS CONTROLLER", "NITROUS", [
        o("ON/OFF", 0, "THE WHOLE SHOT AT ONCE"),
        o("PROGRESSIVE", 1500, "RAMPS THE SHOT IN. SAVES THE TYRES", "NOVUS", progressive=1),
    ]),
    # --- DRIVETRAIN ------------------------------------------------------------------------------
    ("clutch", "CLUTCH", "DRIVETRAIN", [
        STOCK(),
        o("STAGE 2 ORGANIC", 900, "HOLDS MORE, STILL SMOOTH", "EXODI", clutch=0.4, clutchTime=0.9),
        o("STAGE 3 PUCK", 1600, "GRABBY. HITS THE TYRES HARD", "EXODI", clutch=0.9, clutchTime=0.7),
        o("TWIN DISC", 3200, "HOLDS BIG POWER", "OSAKI", clutch=1.5, clutchTime=0.8),
        o("TRIPLE DISC SLIPPER", 6500, "BUILT TO SLIP ON PURPOSE", "OSAKI", clutch=2.4, clutchTime=1.0),
    ]),
    ("flywheel", "FLYWHEEL", "DRIVETRAIN", [
        STOCK(),
        o("LIGHT CHROMOLY", 600, "REVS QUICKER", "EXODI", inertia=0.82),
        o("ALUMINUM", 1100, "REVS FAST, BOGS EASY", "EXODI", inertia=0.65),
    ]),
    ("gearbox", "GEARBOX", "DRIVETRAIN", [
        STOCK(),
        o("CLOSE-RATIO", 2500, "TIGHTER GEARS, TUNABLE", "QUAYLE", shift=0.9, tuneRange=0.1),
        o("5-SPEED", 4500, "FIVE GEARS", "QUAYLE", gears=5, shift=0.85, tuneRange=0.2),
        o("6-SPEED", 6500, "SIX GEARS", "QUAYLE", gears=6, shift=0.8, tuneRange=0.3),
        o("DOG BOX", 12000, "STRAIGHT-CUT. NO SYNCHROS, FAST SHIFTS", "QUAYLE", gears=6, shift=0.5, tuneRange=0.45),
        o("SEQUENTIAL", 18000, "PULL BACK, PULL BACK", "QUAYLE", gears=6, shift=0.35, tuneRange=0.5),
    ]),
    ("shifter", "SHIFTER", "DRIVETRAIN", [
        STOCK(),
        o("SHORT THROW", 350, "SHORTER THROWS", "SHAFTWORKS", shift=0.88),
    ]),
    ("driveshaft", "DRIVESHAFT", "DRIVETRAIN", [
        STOCK(),
        o("ALUMINUM", 700, "LIGHTER TO SPIN UP", "SHAFTWORKS", wheelInertia=0.92, weight=-4),
        o("CARBON FIBRE", 1600, "LIGHTEST", "SHAFTWORKS", wheelInertia=0.85, weight=-7),
    ]),
    ("axles", "AXLES", "DRIVETRAIN", [
        STOCK(),
        o("CHROMOLY AXLES", 900, "STRONGER, A LITTLE LIGHTER", "SHAFTWORKS", wheelInertia=0.96, weight=-2),
    ]),
    ("diff", "DIFFERENTIAL", "DRIVETRAIN", [
        STOCK("OPEN DIFF: ONE-WHEEL PEEL"),
        o("CLUTCH LSD", 1200, "BOTH WHEELS PUSH", "OSAKI", grip=0.04),
        o("TORSEN", 1800, "SMOOTH AND STRONG", "QUAYLE", grip=0.05),
        o("SPOOL", 900, "LOCKED SOLID. PURE DRAG", "SHAFTWORKS", grip=0.07),
    ]),
    ("final", "RING AND PINION", "DRIVETRAIN", [
        STOCK(),
        o("SHORTER (+8%)", 1000, "QUICKER OFF THE LINE", "QUAYLE", finalMult=1.08, finalRange=0.08),
        o("SHORTEST (+16%)", 1400, "ALL LAUNCH, LOW TOP SPEED", "QUAYLE", finalMult=1.16, finalRange=0.08),
        o("TALLER (-8%)", 1000, "MORE TOP END", "QUAYLE", finalMult=0.92, finalRange=0.08),
        o("QUICK-CHANGE", 3000, "ANY RATIO YOU LIKE", "QUAYLE", finalRange=0.35),
    ]),
    # --- SUSPENSION ------------------------------------------------------------------------------
    ("springs", "SPRINGS", "SUSPENSION", [
        STOCK(),
        o("DRAG SPRINGS", 700, "SOFT REAR, MORE SQUAT", "SANDHOFF", transfer=1.12),
        o("COILOVERS", 2800, "ADJUSTABLE RIDE HEIGHT", "TENNO", transfer=1.08, adjustable=1),
        o("LOCKED REAR", 900, "NO SQUAT, NOSE STAYS DOWN. FOR FRONT-DRIVERS", "SANDHOFF", transfer=0.8),
    ]),
    ("shocks", "SHOCKS", "SUSPENSION", [
        STOCK(),
        o("90/10 DRAG SHOCKS", 1100, "FRONT LIFTS FAST, WEIGHT HITS THE REAR", "SANDHOFF", transferRate=1.8),
        o("DOUBLE ADJUSTABLE", 2600, "TUNE HOW FAST IT TRANSFERS", "SANDHOFF", transferRate=1.6, adjustable=2),
        o("SLOW-RISE SHOCKS", 1300, "THE NOSE RISES LATE: WEIGHT STAYS UP FRONT", "SANDHOFF", transferRate=0.55),
    ]),
    ("bars", "TRACTION BARS", "SUSPENSION", [
        STOCK(),
        o("TRACTION BARS", 500, "STOPS AXLE WRAP", "CALDER", grip=0.02, rwdOnly=True),
        o("LADDER BARS", 1500, "PLANTS THE REAR AXLE", "CALDER", grip=0.045, rwdOnly=True),
    ]),
    ("wheeliebars", "WHEELIE BARS", "SUSPENSION", [
        o("NONE", 0, "TOO MUCH WHEELIE AND YOU LIFT"),
        o("WHEELIE BARS", 900, "FRONT STAYS DOWN, NO LIFTING", "CALDER", wheelieBars=1, weight=8, rwdOnly=True, visual="wheeliebar"),
    ]),
    ("tubs", "WHEEL TUBS", "SUSPENSION", [
        STOCK(),
        o("MINI-TUBS", 2500, "ROOM FOR WIDE TYRES", "CALDER", tubs=1, weight=6),
    ]),
    ("cage", "ROLL CAGE", "SUSPENSION", [
        o("NONE", 0, "FACTORY SHELL"),
        o("6-POINT BAR", 1500, "STIFFER CHASSIS", "CALDER", grip=0.01, weight=18, visual="cage"),
        o("10-POINT CAGE", 3500, "STIFFEST. A RACE CAR", "CALDER", grip=0.025, weight=30, visual="cage"),
    ]),
    # --- TYRES AND WHEELS -------------------------------------------------------------------------
    ("tires", "TYRES", "TYRES", [
        o("STREET", 0, "ALL-SEASON", warm=0.7, sens=1.0),
        o("SPORT", 1200, "STICKIER STREET TYRE", "NITTAN", grip=0.06, warm=0.72, sens=1.0),
        o("275 DRAG RADIAL", 2400, "STREET-LEGAL DRAG TYRE", "HOOZER", grip=0.16, warm=0.8, sens=1.3),
        o("315 DRAG RADIAL", 3600, "WIDER. NEEDS TUBS OR WIDEBODY", "HOOZER", grip=0.22, warm=0.82, sens=1.4, requires=WIDE),
        o("SLICKS", 4800, "HUGE GRIP WHEN HOT. DO A BURNOUT", "HOOZER", grip=0.32, warm=0.9, sens=1.8, requires=WIDE),
    ]),
    ("wheels", "WHEELS", "TYRES", [
        STOCK(),
        o("FORGED LIGHTWEIGHT", 2400, "LESS TO SPIN UP", "VOLKER", wheelInertia=0.85, weight=-8),
        o("BEADLOCKS", 3200, "RUN THE PRESSURE LOW", "VOLKER", wheelInertia=0.82, weight=-6, sens=1.25),
    ]),
    # --- WEIGHT ---------------------------------------------------------------------------------
    ("interior", "INTERIOR", "WEIGHT", [
        STOCK(),
        o("STRIP THE REAR", 500, "NO REAR SEATS, NO CARPET", weight=-35),
        o("FULL GUT", 1800, "ONE RACE SEAT. NOTHING ELSE", "BRYDE", weight=-80),
    ]),
    ("seats", "SEATS", "WEIGHT", [
        STOCK(),
        o("PASSENGER DELETE", 150, "NOBODY RIDES ALONG", weight=-16),
    ]),
    ("ac", "AIR CONDITIONING", "WEIGHT", [
        STOCK(),
        o("AC DELETE", 250, "NO COMPRESSOR TO TURN. A LITTLE FREE POWER", weight=-24, tq=0.01),
    ]),
    ("panels", "BODY PANELS", "WEIGHT", [
        STOCK(),
        o("FIBERGLASS HOOD", 900, "LIGHT HOOD", "SEIBAN", weight=-12),
        o("CARBON HOOD + TRUNK", 3500, "BARE CARBON", "SEIBAN", weight=-28, visual="carbon"),
        o("FULL CARBON PANELS", 9500, "EVERY PANEL", "SEIBAN", weight=-60, visual="carbon"),
    ]),
    ("glass", "GLASS", "WEIGHT", [
        STOCK(),
        o("LEXAN REAR + SIDES", 800, "PLASTIC WINDOWS", "CLEARLITE", weight=-14),
        o("FULL LEXAN", 1500, "EVEN THE WINDSHIELD", "CLEARLITE", weight=-22),
    ]),
    ("battery", "BATTERY", "WEIGHT", [
        STOCK("UNDER THE HOOD"),
        o("TRUNK RELOCATION", 300, "MOVES WEIGHT TO THE REAR", rear=0.012),
        o("LIGHT, IN THE TRUNK", 800, "LIGHTER AND REARWARD", "VOLTCELL", rear=0.012, weight=-11),
        o("LIGHT, UP FRONT", 700, "LIGHTER, AND THE WEIGHT STAYS ON THE NOSE", "VOLTCELL", weight=-11),
    ]),
    # --- BODY: drawn on the car, and they change the aero ---------------------------------------
    ("bumper", "FRONT BUMPER", "BODY", [
        STOCK(),
        o("LIP SPOILER", 600, "A LITTLE LOWER, A LITTLE SLIPPERIER", "SEIBAN", drag=-0.005, visual="bumper1"),
        o("STREET BUMPER", 1500, "AGGRESSIVE INTAKES", "SEIBAN", drag=-0.008, weight=-2, visual="bumper2"),
        o("RACE SPLITTER", 2800, "FLAT SPLITTER, CANARDS", "SEIBAN", drag=-0.012, weight=-3, visual="bumper3"),
    ]),
    ("skirts", "SIDE SKIRTS", "BODY", [
        STOCK(),
        o("SIDE SKIRTS", 900, "CLEANS UP THE SIDES", "SEIBAN", drag=-0.006, visual="skirts1"),
        o("RACE SKIRTS", 1800, "LOW AND FLAT", "SEIBAN", drag=-0.01, visual="skirts2"),
    ]),
    ("rear", "REAR BUMPER", "BODY", [
        STOCK(),
        o("REAR DIFFUSER", 1200, "SMOOTHS THE WAKE", "SEIBAN", drag=-0.006, visual="rear1"),
        o("RACE DIFFUSER", 2400, "BIG FINS", "SEIBAN", drag=-0.01, visual="rear2"),
    ]),
    ("hood", "HOOD", "BODY", [
        STOCK(),
        o("VENTED HOOD", 1200, "LETS THE HEAT OUT. YOU CAN SEE THE TURBO", "SEIBAN", visual="hood1"),
        o("COWL INDUCTION", 1600, "THE CLASSIC BULGE", "SEIBAN", hi=0.01, visual="hood2"),
    ]),
    ("fenders", "FENDERS", "BODY", [
        STOCK(),
        o("FENDER FLARES", 1500, "A LITTLE WIDER", "SEIBAN", drag=0.004, visual="flares"),
        o("WIDEBODY KIT", 6000, "ROOM FOR WIDE TYRES", "SEIBAN", drag=0.012, weight=8, visual="widebody"),
    ]),
    ("chute", "PARACHUTE", "BODY", [
        o("NONE", 0, "BRAKES ONLY"),
        o("DRAG CHUTE", 1200, "POPS AFTER THE FINISH LINE", "CALDER", weight=4, visual="chute"),
    ]),
]

COSMETIC = [
    {"id": "rims", "name": "RIMS", "group": "LOOKS",
     "styles": ["stock", "magnum", "rally", "fivestar", "mesh", "turbine", "phone", "three", "sixspoke", "mesh_black", "steelie_black", "split_black"], "price": 2500},
    {"id": "wing", "name": "SPOILER", "group": "LOOKS", "styles": ["stock", "ducktail", "gt", "drag"], "price": 3000, "dragArea": [0, 0.004, 0.012, 0.02]},
    {"id": "tint", "name": "WINDOW TINT", "group": "LOOKS", "styles": ["none", "light", "limo"], "price": 400},
    {"id": "underglow", "name": "UNDERGLOW", "group": "LOOKS", "styles": ["off", "cyan", "magenta", "green", "orange", "purple"], "price": 1500},
]

# Fictional parts makers and the real companies they stand in for (car_references.md)
BRANDS = {
    "AIRHAMMER": "K&N, AEM (intakes)", "STACKWELL": "Jenvey, TWM (individual throttle bodies, velocity stacks)",
    "KOBUN": "HKS, Tomei, GReddy (manifolds, downpipes, intercoolers, twin-turbo kits)", "HOOKLINE": "Hooker, Kooks (headers)",
    "THUNDERBORE": "Borla, MagnaFlow (exhausts)", "CROWLEY": "Brian Crower, Crower (cams, heads, valve springs, strokers)",
    "WISEMAN": "Wiseco (forged pistons)", "KARRO": "Carrillo, Dart (rods, rotating assemblies, blocks)",
    "HASLER": "Hasport (Honda swap mounts and kits)", "COLLIN": "Collins Adapters (RB/JZ swaps)", "SIKKA": "Sikky (LS swap kits)",
    "WEYLAND": "Weiand, BDS (roots blowers, big-block crate engines)", "HARLAN RACING": "Ford Performance (Coyote crate engine)",
    "ROTOHEAD": "Pineapple Racing, Mazdatrix (porting, oil injection, 3/4-rotor swaps)", "IANNO": "Iannetti (apex seals)",
    "WALDEN": "Walbro, Aeromotive (fuel pumps)", "INJEX": "Injector Dynamics, FIC (injectors, fuel systems)",
    "SPLICE": "Unichip, GReddy e-Manage (piggyback ECUs)", "REFLEXA": "Hondata, COBB (reflash)", "HALTRON": "Haltech (standalone ECUs, 2-step)",
    "MOTEX": "MoTeC (anti-lag)", "GARNET": "Garrett (turbos, ball-bearing turbos)", "PRECISE": "Precision Turbo (big singles)",
    "TIDEGATE": "TiAL (external wastegates)", "TURBOZEN": "Turbosmart (boost controllers, BOVs, intercoolers)",
    "WHIPSAW": "Whipple (twin-screw superchargers)", "VORTRAX": "Vortech, ProCharger (centrifugal superchargers)",
    "NOVUS": "NOS, Nitrous Express (nitrous kits)", "EXODI": "Exedy, ACT (clutches, flywheels)", "OSAKI": "OS Giken (twin-disc clutches, LSDs)",
    "QUAYLE": "Quaife (gearboxes, dog boxes, sequentials, diffs)", "SHAFTWORKS": "The Driveshaft Shop (driveshafts, axles, short shifters)",
    "SANDHOFF": "Santhuff (drag springs and shocks)", "TENNO": "Tein, KW (coilovers)", "CALDER": "Calvert Racing (bars, tubs, cages)",
    "NITTAN": "Nitto (street tyres)", "HOOZER": "Hoosier, Mickey Thompson (drag radials, slicks)", "VOLKER": "Volk, Weld (forged wheels, beadlocks)",
    "BRYDE": "Bride (race seats)", "SEIBAN": "Seibon (carbon panels and body parts)", "CLEARLITE": "Lexan polycarbonate windows",
    "VOLTCELL": "Odyssey, Braille (light batteries)",
}

ENGINE_REFS = {
    "k24": "Honda K24/K20 'frankenstein' hybrid", "b18c": "Honda B18C (Integra Type R)", "h22": "Honda H22A (Prelude)",
    "rb26": "Nissan RB26DETT (Skyline GT-R)", "2jz": "Toyota 2JZ-GTE (Supra MK4)", "ls": "GM LS1 5.7", "bb572": "Chevrolet 572 big-block crate",
    "coyote": "Ford Coyote 5.0", "13brew": "Mazda 13B-REW (FD RX-7)", "20b": "Mazda 20B-REW (Eunos Cosmo)", "26b": "Mazda 26B 4-rotor (787B-style)",
}


def paint(p):
    """Paint options; the CREW decal puts your crew's emblem on the door."""
    if not any(d["name"] == "CREW" for d in p["decals"]):
        p["decals"].append({"name": "CREW", "price": 2000})
    return p


def references(slots):
    """The parts section of car_references.md (everything from "## Parts" on is ours)."""
    used = sorted({op["brand"] for _, _, _, ops, *_ in slots for op in ops if "brand" in op})
    missing = [b for b in used if b not in BRANDS]
    assert not missing, f"brands without a real-world reference: {missing}"
    lines = ["## Parts", "",
             "Fictional parts makers in the game and the real companies they stand in for. Never show the real names.", "",
             "| In-game brand | Real-world equivalents |", "|---|---|"]
    lines += [f"| {b} | {BRANDS[b]} |" for b in used]
    lines += ["", "Engine swaps (what each kit stands in for, and which chassis take it):", "",
              "| Swap | Real engine | Fits |", "|---|---|---|"]
    lines += [f"| {ENGINES[e]['name']} | {ENGINE_REFS[e]} | {', '.join(SWAPS[e])} |" for e in ENGINES]
    ref = ROOT / "car_references.md"
    text = ref.read_text()
    head = text.split("\n## Parts\n")[0].rstrip("\n")
    ref.write_text(head + "\n\n" + "\n".join(lines) + "\n")


def main():
    path = ROOT / "assets/data/parts.json"
    old = json.loads(path.read_text())
    slots = []
    for row in SLOTS:
        sid, name, group, options = row[:4]
        excludes = row[4] if len(row) > 4 else []
        slots.append({"id": sid, "name": name, "group": group, "options": options, "excludes": excludes})
    ids = {s["id"] for s in slots}
    for s in slots:
        for op in s["options"]:
            for sl, _ in op.get("requires", []) + op.get("requiresAll", []):
                assert sl in ids, f"{s['id']}/{op['name']} requires unknown slot {sl}"
            for c in op.get("carOnly", []):
                assert c in CARS, f"{s['id']}/{op['name']} fits unknown car {c}"
            # the shop shows a name beside its price and a one-line info under the car
            assert len(op["name"]) <= 20, f"name too long for the shop: {op['name']}"
            assert len(op["info"]) <= 44, f"info too long for the shop: {op['info']}"
    out = {
        "version": 3,
        "classMultiplier": old["classMultiplier"],
        "slots": slots,
        "engines": ENGINES,
        "layoutMass": LAYOUT_MASS,
        "cosmetic": COSMETIC,
        "paint": paint(old["paint"]),
    }
    path.write_text(json.dumps(out, indent=1) + "\n")
    references(SLOTS)
    print(len(slots), "slots,", sum(len(s["options"]) for s in slots) - len(slots), "parts,", len(ENGINES), "swap engines")


if __name__ == "__main__":
    main()
