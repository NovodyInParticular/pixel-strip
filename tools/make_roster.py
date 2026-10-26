"""Writes the car roster: assets/data/cars/<id>.json, assets/data/roster.json and the table in
car_references.md, all from the list below.

Every car is fictional in the game (invented names and brands) with base stats from its real
inspiration's stock specs: curb weight without a driver (kg), net power unless "gross" (period SAE
gross, derated by the physics), torque in Nm, factory gear ratios and final drive, stock tyre
radius. `et` is a typical period road-test quarter mile for the stock car; RosterCalibrationTest
holds each car's perfect simulated pass close to it. `drive` is the real car's drivetrain.

Cars marked keep=True already had calibrated stats: their physics are read back from their JSON
and only the fields given here are changed. `sprite` names the existing profile a new car borrows
until it gets its own art (tools/art/roster.py).

Availability: dealer cars are bought; boss cars are won from a career boss; barn finds turn up on
the career map (career.json "barns"); tournament prizes go to a daily winner (server tournaments.ts).

Run: python3 tools/make_roster.py
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
CARS = ROOT / "assets" / "data" / "cars"

# paint ids (tools/art/paint.py): 0 red, 1 purple, 2 teal, 3 orange, 4 yellow, 5 midnight, 6 white,
# 7 gunmetal, 8 lime, 9 blue, 10 green, 11 silver, 12 pacific blue, 13 black

# drivetrain defaults by era: tyre grip and shift speed
ERA = {
    "classic": dict(grip=0.83, shiftTime=0.3),
    "80s": dict(grip=0.89, shiftTime=0.27),
    "90s": dict(grip=0.95, shiftTime=0.25),
}


# converter=True: an automatic whose torque converter loads the engine on the brake, so it builds boost staged
def car(id, name, brand, year, era, price, hp, hp_rpm, tq, tq_rpm, redline, kg, drive, rear, wb, cg, gears, final,
        tyre_r, drag, layout, cyl, paint, et, inspired, specs, sprite=None, turbo=False, rating="net", idle=800,
        vtec=0, grip=None, shift=None, e_inertia=None, w_inertia=None, avail="dealer", exotic=False):
    d = ERA[era]
    return {
        "id": id, "name": name, "brand": brand, "year": year, "era": era, "price": price,
        "dealer": avail == "dealer", "avail": avail, "hp": hp, "powerRating": rating, "peakHpRpm": hp_rpm,
        "torqueNm": tq, "peakTorqueRpm": tq_rpm, "idleRpm": idle, "redline": redline, "weightKg": kg,
        "drivetrain": drive, "rearWeight": rear, "wheelbaseM": wb, "cgHeightM": cg, "gears": gears,
        "finalDrive": final, "shiftTime": shift or d["shiftTime"], "clutchTime": 0.32, "clutchTorque": 1.5,
        "tireRadiusM": tyre_r, "grip": grip or d["grip"],
        # rotating mass grows with displacement and wheel size
        "engineInertia": e_inertia or round(0.06 + 0.035 * cyl + (0.06 if layout in ("v8", "v10", "v12", "flat12") else 0), 2),
        "wheelInertia": w_inertia or round(1.6 + (tyre_r - 0.29) * 20, 1),
        "dragArea": drag, "nitrous": {"hp": 0, "charges": 0, "seconds": 0}, "defaultPaint": paint,
        "engine": {"layout": layout, "cylinders": cyl, "stockTurbo": turbo, **({"vtecRpm": vtec} if vtec else {})},
        **({"exotic": True} if exotic else {}),
        "_et": et, "_inspired": inspired, "_specs": specs, "_sprite": sprite,
    }


def keep(id, et, inspired, specs, avail="dealer", **changes):
    return {"id": id, "keep": True, "_et": et, "_inspired": inspired, "_specs": specs, "avail": avail, **changes}


ROSTER = [
    # --- Raijin (Nissan) ------------------------------------------------------------------------
    keep("kaminari_mk1", 13.3, "1989 Nissan Skyline GT-R (R32)", "276 hp @ 6,800 · 260 lb-ft @ 4,400 · ~3,150 lb · AWD (ATTESA E-TS) · 5-speed, 4.11 final"),
    car("kaminari_mk2", "Kaminari GT-X Mk2", "Raijin", 1995, "90s", 120000, 276, 6800, 368, 4400, 8000, 1530, "AWD",
        0.41, 2.72, 0.5, [3.214, 1.925, 1.302, 1.0, 0.752], 4.111, 0.326, 0.72, "i6", 6, 11, 13.2,
        "1995 Nissan Skyline GT-R (R33)", "276 hp @ 6,800 · 368 Nm @ 4,400 · ~1,530 kg · AWD (ATTESA E-TS Pro) · 5-speed, 4.11 final",
        sprite="kaminari_mk1", turbo=True, grip=0.98),
    keep("kaminari_mk3", 13.0, "1999 Nissan Skyline GT-R V-Spec (R34)", "276 hp rated (~330 real) @ 7,000 · 289 lb-ft @ 4,400 · ~3,440 lb · AWD · 6-speed Getrag, 3.545 final"),
    car("sylph_13", "Sylph K13", "Raijin", 1991, "90s", 16000, 202, 6000, 274, 4000, 7500, 1220, "RWD",
        0.46, 2.475, 0.49, [3.321, 1.902, 1.308, 1.0, 0.759], 4.111, 0.308, 0.6, "i4", 4, 11, 14.8,
        "1991 Nissan Silvia K's (S13, SR20DET)", "205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,220 kg · RWD · 5-speed, 4.11 final",
        sprite="ranger50", turbo=True, grip=0.9),
    car("sylph_15", "Sylph R15", "Raijin", 1999, "90s", 38000, 247, 6400, 275, 4800, 7500, 1250, "RWD",
        0.46, 2.525, 0.48, [3.626, 2.2, 1.541, 1.213, 1.0, 0.767], 4.083, 0.313, 0.58, "i4", 4, 4, 14.1,
        "1999 Nissan Silvia Spec-R (S15, SR20DET)", "250 PS @ 6,400 · 28 kgm @ 4,800 · ~1,250 kg · RWD · 6-speed, 4.08 final",
        sprite="kaminari_mk1", turbo=True),
    car("sylph_180", "Sylph 180 Fastback", "Raijin", 1996, "90s", 17000, 202, 6000, 274, 4000, 7500, 1240, "RWD",
        0.46, 2.475, 0.49, [3.321, 1.902, 1.308, 1.0, 0.759], 4.111, 0.308, 0.6, "i4", 4, 6, 14.9,
        "1996 Nissan 180SX Type X (SR20DET)", "205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,240 kg · RWD · 5-speed, 4.11 final",
        sprite="corsair_iroc", turbo=True, grip=0.9),
    # --- Hayate (Toyota) ------------------------------------------------------------------------
    keep("tsunami", 13.3, "1994 Toyota Supra Turbo (MK4)", "320 hp @ 5,600 · 315 lb-ft @ 4,000 · ~3,450 lb · RWD · 6-speed Getrag V160, 3.13 rear"),
    car("ronin", "Ronin MR Turbo", "Hayate", 1991, "90s", 24000, 200, 6000, 271, 3200, 7200, 1310, "RWD",
        0.57, 2.4, 0.46, [3.23, 1.913, 1.258, 0.918, 0.731], 4.285, 0.303, 0.56, "i4", 4, 0, 14.3,
        "1991 Toyota MR2 Turbo (SW20, 3S-GTE)", "200 hp @ 6,000 · 200 lb-ft @ 3,200 · ~2,888 lb · mid-engine RWD · 5-speed E153, 4.29 final",
        sprite="kirin", turbo=True, grip=0.93),
    # --- Kitsune (Mazda) ------------------------------------------------------------------------
    keep("spiral_turbo", 15.0, "1987 Mazda RX-7 Turbo II (FC3S)", "182 hp @ 6,500 · 183 lb-ft @ 3,500 · ~2,850 lb · RWD · 5-speed, 4.10 rear"),
    car("spiral_r", "Spiral R Final", "Kitsune", 2002, "90s", 95000, 276, 6500, 314, 5000, 8000, 1270, "RWD",
        0.5, 2.425, 0.46, [3.483, 2.015, 1.391, 1.0, 0.762], 4.3, 0.318, 0.56, "rotary", 2, 4, 13.4,
        "2002 Mazda RX-7 Spirit R Type A (FD3S)", "280 PS @ 6,500 · 32 kgm @ 5,000 · ~1,270 kg · RWD · 5-speed, 4.30 final",
        sprite="spiral_r", turbo=True, grip=1.0, idle=850),
    car("prairie", "Prairie R3", "Kitsune", 1972, "classic", 45000, 118, 6500, 157, 4000, 7000, 905, "RWD",
        0.47, 2.31, 0.5, [3.683, 2.263, 1.397, 1.0, 0.862], 4.111, 0.297, 0.68, "rotary", 2, 3, 16.4,
        "1972 Mazda RX-3 / Savanna GT (S124A, 12A)", "120 PS gross @ 6,500 · 16 kgm @ 4,000 · ~905 kg · RWD · 5-speed, 4.11 final",
        sprite="warrior", rating="gross", grip=0.8, idle=900, avail="barn"),
    # --- Hikari (Honda) -------------------------------------------------------------------------
    keep("kirin", 13.6, "1991 Acura NSX", "270 hp @ 7,100 · 210 lb-ft @ 5,300 · ~3,010 lb · mid-engine RWD · 5-speed, 4.06 final",
         engine_vtec=5800),
    keep("kite", 16.0, "1994 Honda Civic Si hatchback (EG)", "125 hp @ 6,600 · 106 lb-ft @ 5,200 · ~2,350 lb · FWD · 5-speed, 4.25 final",
         engine_vtec=4800),
    keep("kite_ef", 16.3, "1990 Honda Civic Si hatchback (EF); boss car of Joe \"Ground Control\", built to the user's friend's real car",
         "108 hp @ 6,000 · 100 lb-ft @ 5,000 · ~2,250 lb · FWD · 5-speed, 4.25 final", avail="boss"),
    car("kite_rx", "Kite RX", "Hikari", 1997, "90s", 30000, 182, 8200, 160, 7500, 8900, 1050, "FWD",
        0.38, 2.62, 0.49, [3.23, 2.105, 1.458, 1.107, 0.848], 4.785, 0.298, 0.6, "i4", 4, 0, 15.1,
        "1997 Honda Civic Type R (EK9, B16B)", "185 PS @ 8,200 · 16.3 kgm @ 7,500 · ~1,050 kg · FWD · 5-speed close-ratio, 4.79 final",
        sprite="kite", vtec=5800, grip=0.93, e_inertia=0.11),
    keep("arrow", 15.1, "1997 Acura Integra Type R (DC2)", "195 hp @ 8,000 · 130 lb-ft @ 7,500 · ~2,640 lb · FWD · 5-speed close-ratio, 4.40 final",
         engine_vtec=5700),
    car("overture", "Overture SH", "Hikari", 1999, "90s", 26000, 200, 7000, 212, 5250, 7400, 1380, "FWD",
        0.37, 2.585, 0.5, [3.285, 1.956, 1.344, 1.033, 0.788], 4.266, 0.306, 0.62, "i4", 4, 10, 15.2,
        "1999 Honda Prelude Type SH (BB6, H22A4)", "200 hp @ 7,000 · 156 lb-ft @ 5,250 · ~3,042 lb · FWD (ATTS) · 5-speed, 4.27 final",
        sprite="arrow", vtec=5200, grip=0.93),
    car("kite_sol", "Kite Sol", "Hikari", 1994, "90s", 15000, 160, 7600, 150, 7000, 8200, 1109, "FWD",
        0.39, 2.37, 0.47, [3.23, 2.105, 1.458, 1.107, 0.848], 4.266, 0.295, 0.6, "i4", 4, 0, 15.6,
        "1994 Honda Civic del Sol VTEC (B16A3)", "160 hp @ 7,600 · 111 lb-ft @ 7,000 · ~2,445 lb · FWD · 5-speed, 4.27 final",
        sprite="kite", vtec=5500, grip=0.9, e_inertia=0.1),
    car("tenshi", "Tenshi 2000", "Hikari", 2000, "90s", 42000, 240, 8300, 208, 7500, 9000, 1274, "RWD",
        0.5, 2.4, 0.45, [3.133, 2.045, 1.481, 1.161, 0.971, 0.811], 4.1, 0.316, 0.62, "i4", 4, 11, 14.2,
        "2000 Honda S2000 (AP1, F20C)", "240 hp @ 8,300 · 153 lb-ft @ 7,500 · ~2,809 lb · RWD · 6-speed, 4.10 rear",
        sprite="asp", vtec=6000, grip=0.98, e_inertia=0.11),
    # --- Tsubasa (Mitsubishi) -------------------------------------------------------------------
    car("halberd_6", "Halberd VI", "Tsubasa", 1999, "90s", 58000, 276, 6500, 373, 3000, 7500, 1360, "AWD",
        0.4, 2.51, 0.52, [2.785, 1.95, 1.444, 1.096, 0.761], 4.529, 0.317, 0.66, "i4", 4, 0, 13.6,
        "1999 Mitsubishi Lancer Evolution VI GSR (CP9A)", "280 PS @ 6,500 · 38 kgm @ 3,000 · ~1,360 kg · AWD (AYC) · 5-speed, 4.53 final",
        sprite="kaminari_mk1", turbo=True, grip=0.98),
    keep("typhoon", 13.8, "1991 Mitsubishi 3000GT VR-4", "300 hp @ 6,000 · 307 lb-ft @ 2,500 · ~3,740 lb · AWD · 5-speed, 4.15 final"),
    # --- Seiza (Subaru) -------------------------------------------------------------------------
    car("comet_gd", "Comet STX", "Seiza", 2004, "90s", 90000, 300, 6000, 407, 4000, 7000, 1480, "AWD",
        0.41, 2.525, 0.52, [3.636, 2.375, 1.761, 1.346, 0.971, 0.756], 3.9, 0.317, 0.66, "flat4", 4, 9, 13.3,
        "2004 Subaru Impreza WRX STI (GD, EJ257)", "300 hp @ 6,000 · 300 lb-ft @ 4,000 · ~3,263 lb · AWD (DCCD) · 6-speed, 3.90 final",
        sprite="kaminari_mk1", turbo=True, grip=0.98),
    # --- Crestline (Chevrolet) -----------------------------------------------------------------
    car("vortex", "Vortex Z6", "Crestline", 2002, "90s", 180000, 405, 6000, 542, 4800, 6500, 1414, "RWD",
        0.49, 2.655, 0.46, [2.97, 2.07, 1.43, 1.0, 0.84, 0.56], 3.42, 0.332, 0.6, "v8", 8, 4, 12.4,
        "2002 Chevrolet Corvette Z06 (C5, LS6)", "405 hp @ 6,000 · 400 lb-ft @ 4,800 · ~3,118 lb · RWD (rear transaxle) · 6-speed M12, 3.42 rear",
        sprite="kirin", grip=0.9, w_inertia=2.4),
]

# Factory turbo character from each real setup (engine.lag in s, engine.spoolRpm): without an entry a
# turbo is on full boost by the torque peak after 0.6 s. Twin and sequential turbos snap on sooner;
# big early singles make you wait (the 930 most of all).
TURBO = {
    "kaminari_mk1": dict(lag=0.45), "kaminari_mk2": dict(lag=0.45), "kaminari_mk3": dict(lag=0.45),
    "tsunami": dict(lag=0.4, spoolRpm=2400), "spiral_r": dict(lag=0.45, spoolRpm=3000),
    "typhoon": dict(lag=0.45), "ronin": dict(lag=0.7),
}

# Japanese cars whose real one is a US car, driven from the left: Joe's EF
LEFT_HAND = {"kite_ef"}
JAPANESE = {"Nissan", "Datsun", "Toyota", "Honda", "Acura", "Mazda", "Eunos", "Mitsubishi", "Subaru", "Lexus", "Infiniti",
            "Suzuki", "Isuzu", "Daihatsu"}

# Cockpit art for the first-person view, when the era's default isn't right (tools/art/cockpit.py)
COCKPIT = {}

# Shift time overrides (s), when a car's gearbox is slower or quicker than its era's default
SHIFT = {}

PRICES = {
    "kite_ef": 9500,
    "prairie": 11500,
    "kite": 13500,
    "kite_sol": 15000,
    "spiral_turbo": 24000,
    "overture": 26000,
    "arrow": 27000,
    "ronin": 28000,
    "tenshi": 32000,
    "sylph_180": 34000,
    "kite_rx": 36000,
    "sylph_13": 37000,
    "typhoon": 51000,
    "kaminari_mk2": 56000,
    "sylph_15": 59000,
    "kaminari_mk1": 68000,
    "kirin": 80000,
    "comet_gd": 90000,
    "halberd_6": 95000,
    "tsunami": 95000,
    "kaminari_mk3": 100000,
    "spiral_r": 130000,
    "vortex": 180000,
}

BRANDS = ("Raijin (Nissan), Hayate (Toyota), Kitsune (Mazda), Hikari (Honda/Acura), Tsubasa (Mitsubishi), Seiza (Subaru), "
          "Crestline (Chevrolet).")

# the second clear-out's ids, for the note in car_references.md
RETIRED_0326 = ("kaminari_c10 zephyr_240 zephyr_tt sylph_14 tsunami_mk3 kestrel seiran pursuit shirasagi spiral_sa kokoro kirin_r "
                "dart halberd_4 halberd_5 squall stellar comet comet_22x ranger_500 mamba lm40 phantom asp mammoth condor wal "
                "wal_awd isar_s3 quaranta scarlatta zanna wraith").split()

AVAIL = {"dealer": "dealer", "boss": "career boss", "barn": "barn find", "prize": "tournament prize"}


def main():
    ids = [c["id"] for c in ROSTER]
    assert len(ids) == len(set(ids)), "duplicate ids"
    retired = json.loads((ROOT / "assets" / "data" / "retired.json").read_text())["cars"]
    assert not set(ids) & set(retired), "a retired id can't come back: its owners were refunded"
    rows = []
    for c in ROSTER:
        path = CARS / f"{c['id']}.json"
        if c.get("keep"):
            d = json.loads(path.read_text())
            d["dealer"] = c["avail"] == "dealer"
            if "engine_vtec" in c:
                d["engine"]["vtecRpm"] = c["engine_vtec"]
            for k in ("price", "converter"):
                if k in c:
                    d[k] = c[k]
        else:
            d = {k: v for k, v in c.items() if not k.startswith("_") and k != "avail"}
        if c["id"] in TURBO:
            assert d["engine"]["stockTurbo"], f"{c['id']} has no factory turbo"
            d["engine"].update(TURBO[c["id"]])
        d["price"] = PRICES[c["id"]]
        if c["id"] in COCKPIT:
            d["cockpit"] = COCKPIT[c["id"]]
        # the driver sits on the right in the Japanese cars (home-market right-hand drive), on the left in the rest
        if c["_inspired"].split()[1] in JAPANESE and c["id"] not in LEFT_HAND:
            d["rhd"] = True
        else:
            d.pop("rhd", None)
        if c["id"] in SHIFT:
            d["shiftTime"] = SHIFT[c["id"]]
        path.write_text(json.dumps(d, indent=2) + "\n")
        rows.append(f"| {d['brand']} {d['name']} | `{d['id']}` | {c['_inspired']} | {d['drivetrain']} | {c['_specs']} | {c['_et']:.1f} | {AVAIL[c['avail']]} |")
    (ROOT / "assets" / "data" / "roster.json").write_text(json.dumps(ids, indent=2) + "\n")

    ref = ROOT / "car_references.md"
    parts = ref.read_text().split("\n## Parts\n", 1)          # tools/make_parts.py owns that section
    tail = "\n## Parts\n" + parts[1] if len(parts) > 1 else ""
    ref.write_text(
        "# Car references (internal)\n\n"
        "Maps each fictional in-game car to its real-world inspiration. Base stats in\n"
        "`assets/data/cars/*.json` come from these stock specs (curb weights rounded to kg, torque to Nm).\n"
        "Never use the real names, brands or logos in the game itself. Written by `tools/make_roster.py`;\n"
        "the Drivetrain and Stock 1/4 columns are read by the tests (each car's real drivetrain, and a typical\n"
        "period road-test quarter mile its stock simulated pass must land near).\n\n"
        "| In-game | Id | Inspired by | Drivetrain | Stock specs | Stock 1/4 (s) | How you get it |\n"
        "|---|---|---|---|---|---|---|\n" + "\n".join(rows) + "\n\n"
        f"Fictional brands: {BRANDS}\n\n"
        "Removed on 2026-10-25 (owners refunded, see `assets/data/retired.json`): Dominion Vandal R/T ('69 Charger R/T),\n"
        "Harlan Ranger GT Fastback ('67 Mustang GT 390), Crestline Corsair SS 396 ('69 Camaro SS), Dominion Piranha Hemi\n"
        "('70 Hemi 'Cuda), Apache Warrior 389 ('64 GTO), Harlan Ranger 5.0 LX ('88 Fox Mustang), Crestline Corsair IROC 5.7\n"
        "('88 IROC-Z), Kessler 940 Turbo ('86 944 Turbo), Raijin Zephyr 300 Turbo ('86 Z31 300ZX).\n\n"
        "Removed on 2026-10-26, when the roster became only the cars in the user's reference photos (owners refunded the\n"
        "same way): " + ", ".join(sorted(RETIRED_0326)) + ". The Crestline Vortex became a C5 Z06 (was a C4 ZR-1) and the\n"
        "Hikari Overture a 5th-generation Prelude (was a 4th). Retired ids are never reused.\n" + tail)
    print(f"{len(ROSTER)} cars written")


if __name__ == "__main__":
    main()
