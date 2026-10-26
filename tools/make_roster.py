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
    car("kaminari_c10", "Kaminari 2000 GT-X", "Raijin", 1969, "classic", 150000, 158, 7000, 177, 5600, 8000, 1100, "RWD",
        0.47, 2.57, 0.52, [2.957, 1.857, 1.311, 1.0, 0.852], 4.444, 0.318, 0.72, "i6", 6, 6, 16.0,
        "1969 Nissan Skyline 2000GT-R (KPGC10 'Hakosuka')", "160 PS gross @ 7,000 · 18 kgm @ 5,600 · ~1,100 kg · RWD · 5-speed, 4.44 final",
        sprite="warrior", rating="gross", idle=950, avail="barn"),
    keep("kaminari_mk1", 13.3, "1989 Nissan Skyline GT-R (R32)", "276 hp @ 6,800 · 260 lb-ft @ 4,400 · ~3,150 lb · AWD (ATTESA E-TS) · 5-speed, 4.11 final"),
    car("kaminari_mk2", "Kaminari GT-X Mk2", "Raijin", 1995, "90s", 120000, 276, 6800, 368, 4400, 8000, 1530, "AWD",
        0.41, 2.72, 0.5, [3.214, 1.925, 1.302, 1.0, 0.752], 4.111, 0.326, 0.72, "i6", 6, 11, 13.2,
        "1995 Nissan Skyline GT-R (R33)", "276 hp @ 6,800 · 368 Nm @ 4,400 · ~1,530 kg · AWD (ATTESA E-TS Pro) · 5-speed, 4.11 final",
        sprite="kaminari_mk1", turbo=True, grip=0.98),
    keep("kaminari_mk3", 13.0, "1999 Nissan Skyline GT-R V-Spec (R34)", "276 hp rated (~330 real) @ 7,000 · 289 lb-ft @ 4,400 · ~3,440 lb · AWD · 6-speed Getrag, 3.545 final"),
    car("zephyr_240", "Zephyr 240", "Raijin", 1970, "classic", 30000, 151, 5600, 198, 4400, 6500, 1068, "RWD",
        0.48, 2.305, 0.5, [3.549, 2.197, 1.42, 1.0], 3.364, 0.318, 0.7, "i6", 6, 3, 16.1,
        "1970 Datsun 240Z (HS30)", "151 hp gross @ 5,600 · 146 lb-ft @ 4,400 · ~2,355 lb · RWD · 4-speed, 3.36 rear",
        sprite="ranger", rating="gross"),
    car("zephyr_tt", "Zephyr 300 TT", "Raijin", 1990, "90s", 48000, 300, 6400, 384, 3600, 7000, 1565, "RWD",
        0.47, 2.45, 0.48, [3.321, 1.902, 1.308, 1.0, 0.759], 3.692, 0.313, 0.58, "v6", 6, 0, 13.8,
        "1990 Nissan 300ZX Twin Turbo (Z32)", "300 hp @ 6,400 · 283 lb-ft @ 3,600 · ~3,450 lb · RWD · 5-speed, 3.69 rear",
        sprite="zephyr", turbo=True),
    car("sylph_13", "Sylph K13", "Raijin", 1991, "90s", 16000, 202, 6000, 274, 4000, 7500, 1220, "RWD",
        0.46, 2.475, 0.49, [3.321, 1.902, 1.308, 1.0, 0.759], 4.111, 0.308, 0.6, "i4", 4, 11, 14.8,
        "1991 Nissan Silvia K's (S13, SR20DET)", "205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,220 kg · RWD · 5-speed, 4.11 final",
        sprite="ranger50", turbo=True, grip=0.9),
    car("sylph_14", "Sylph K14", "Raijin", 1997, "90s", 22000, 217, 6000, 275, 4800, 7500, 1240, "RWD",
        0.46, 2.525, 0.49, [3.321, 1.902, 1.308, 1.0, 0.759], 4.111, 0.316, 0.6, "i4", 4, 5, 14.5,
        "1997 Nissan Silvia K's (S14 kouki, SR20DET)", "220 PS @ 6,000 · 28 kgm @ 4,800 · ~1,240 kg · RWD · 5-speed, 4.11 final",
        sprite="ranger50", turbo=True, grip=0.92),
    car("sylph_15", "Sylph R15", "Raijin", 1999, "90s", 38000, 247, 6400, 275, 4800, 7500, 1250, "RWD",
        0.46, 2.525, 0.48, [3.626, 2.2, 1.541, 1.213, 1.0, 0.767], 4.083, 0.313, 0.58, "i4", 4, 4, 14.1,
        "1999 Nissan Silvia Spec-R (S15, SR20DET)", "250 PS @ 6,400 · 28 kgm @ 4,800 · ~1,250 kg · RWD · 6-speed, 4.08 final",
        sprite="kaminari_mk1", turbo=True),
    car("sylph_180", "Sylph 180 Fastback", "Raijin", 1996, "90s", 17000, 202, 6000, 274, 4000, 7500, 1240, "RWD",
        0.46, 2.475, 0.49, [3.321, 1.902, 1.308, 1.0, 0.759], 4.111, 0.308, 0.6, "i4", 4, 6, 14.9,
        "1996 Nissan 180SX Type X (SR20DET)", "205 PS @ 6,000 · 28 kgm @ 4,000 · ~1,240 kg · RWD · 5-speed, 4.11 final",
        sprite="corsair_iroc", turbo=True, grip=0.9),
    # --- Hayate (Toyota) ------------------------------------------------------------------------
    car("tsunami_mk3", "Tsunami Turbo Mk3", "Hayate", 1987, "80s", 15000, 230, 5600, 334, 3200, 6500, 1603, "RWD",
        0.47, 2.595, 0.5, [3.25, 1.955, 1.31, 1.0, 0.753], 3.727, 0.316, 0.66, "i6", 6, 6, 15.1,
        "1987 Toyota Supra Turbo (MA70, 7M-GTE)", "230 hp @ 5,600 · 246 lb-ft @ 3,200 · ~3,535 lb · RWD · 5-speed R154, 3.73 rear",
        sprite="zephyr", turbo=True, grip=0.9),
    keep("tsunami", 13.3, "1994 Toyota Supra Turbo (MK4)", "320 hp @ 5,600 · 315 lb-ft @ 4,000 · ~3,450 lb · RWD · 6-speed Getrag V160, 3.13 rear"),
    keep("kestrel", 16.5, "1985 Toyota Corolla GT-S (AE86)", "112 hp @ 6,600 · 97 lb-ft @ 5,800 · ~2,400 lb · RWD · 5-speed T50, 4.30 rear"),
    car("ronin", "Ronin MR Turbo", "Hayate", 1991, "90s", 24000, 200, 6000, 271, 3200, 7200, 1310, "RWD",
        0.57, 2.4, 0.46, [3.23, 1.913, 1.258, 0.918, 0.731], 4.285, 0.303, 0.56, "i4", 4, 0, 14.3,
        "1991 Toyota MR2 Turbo (SW20, 3S-GTE)", "200 hp @ 6,000 · 200 lb-ft @ 3,200 · ~2,888 lb · mid-engine RWD · 5-speed E153, 4.29 final",
        sprite="kirin", turbo=True, grip=0.93),
    car("seiran", "Seiran GT-4", "Hayate", 1991, "90s", 26000, 200, 6000, 271, 3200, 7000, 1479, "AWD",
        0.4, 2.525, 0.5, [3.23, 1.913, 1.258, 0.918, 0.731], 4.285, 0.298, 0.64, "i4", 4, 0, 15.0,
        "1991 Toyota Celica All-Trac / GT-Four (ST185)", "200 hp @ 6,000 · 200 lb-ft @ 3,200 · ~3,260 lb · AWD · 5-speed E150F, 4.29 final",
        sprite="squall", turbo=True, grip=0.93),
    car("pursuit", "Pursuit Tourer TV", "Hayate", 1998, "90s", 32000, 276, 6200, 378, 2400, 7000, 1510, "RWD",
        0.47, 2.73, 0.52, [3.25, 1.955, 1.31, 1.0, 0.753], 3.909, 0.321, 0.7, "i6", 6, 11, 14.4,
        "1998 Toyota Chaser Tourer V (JZX100, 1JZ-GTE)", "280 PS @ 6,200 · 38.5 kgm @ 2,400 · ~1,510 kg · RWD · 5-speed R154, 3.91 final",
        sprite="kaminari_mk3", turbo=True, grip=0.92),
    car("shirasagi", "Shirasagi 2000", "Hayate", 1967, "classic", 300000, 148, 6600, 177, 5000, 7000, 1120, "RWD",
        0.49, 2.33, 0.46, [3.14, 1.94, 1.41, 1.0, 0.85], 4.375, 0.323, 0.55, "i6", 6, 6, 16.8,
        "1967 Toyota 2000GT", "150 PS gross @ 6,600 · 18 kgm @ 5,000 · ~1,120 kg · RWD · 5-speed, 4.375 final",
        sprite="ranger", rating="gross", idle=900, avail="barn"),
    # --- Kitsune (Mazda) ------------------------------------------------------------------------
    car("spiral_sa", "Spiral SA", "Kitsune", 1984, "80s", 10000, 135, 6000, 180, 2750, 7000, 1175, "RWD",
        0.49, 2.42, 0.47, [3.674, 2.217, 1.432, 1.0, 0.825], 4.1, 0.301, 0.6, "rotary", 2, 0, 16.1,
        "1984 Mazda RX-7 GSL-SE (FB, 13B)", "135 hp @ 6,000 · 133 lb-ft @ 2,750 · ~2,590 lb · RWD · 5-speed, 4.10 rear",
        sprite="spiral_turbo", grip=0.87, idle=850),
    keep("spiral_turbo", 15.0, "1987 Mazda RX-7 Turbo II (FC3S)", "182 hp @ 6,500 · 183 lb-ft @ 3,500 · ~2,850 lb · RWD · 5-speed, 4.10 rear"),
    car("spiral_r", "Spiral R Final", "Kitsune", 2002, "90s", 95000, 276, 6500, 314, 5000, 8000, 1270, "RWD",
        0.5, 2.425, 0.46, [3.483, 2.015, 1.391, 1.0, 0.762], 4.3, 0.318, 0.56, "rotary", 2, 4, 13.4,
        "2002 Mazda RX-7 Spirit R Type A (FD3S)", "280 PS @ 6,500 · 32 kgm @ 5,000 · ~1,270 kg · RWD · 5-speed, 4.30 final",
        sprite="spiral_r", turbo=True, grip=1.0, idle=850),
    car("kokoro", "Kokoro 1.6", "Kitsune", 1990, "90s", 8000, 116, 6500, 136, 5500, 7200, 990, "RWD",
        0.48, 2.265, 0.45, [3.136, 1.888, 1.33, 1.0, 0.814], 4.3, 0.289, 0.62, "i4", 4, 0, 16.8,
        "1990 Mazda MX-5 Miata (NA, 1.6)", "116 hp @ 6,500 · 100 lb-ft @ 5,500 · ~2,182 lb · RWD · 5-speed, 4.30 rear",
        sprite="asp", grip=0.88),
    car("prairie", "Prairie R3", "Kitsune", 1972, "classic", 45000, 118, 6500, 157, 4000, 7000, 905, "RWD",
        0.47, 2.31, 0.5, [3.683, 2.263, 1.397, 1.0, 0.862], 4.111, 0.297, 0.68, "rotary", 2, 3, 16.4,
        "1972 Mazda RX-3 / Savanna GT (S124A, 12A)", "120 PS gross @ 6,500 · 16 kgm @ 4,000 · ~905 kg · RWD · 5-speed, 4.11 final",
        sprite="warrior", rating="gross", grip=0.8, idle=900, avail="barn"),
    # --- Hikari (Honda) -------------------------------------------------------------------------
    keep("kirin", 13.6, "1991 Acura NSX", "270 hp @ 7,100 · 210 lb-ft @ 5,300 · ~3,010 lb · mid-engine RWD · 5-speed, 4.06 final",
         engine_vtec=5800),
    car("kirin_r", "Kirin R", "Hikari", 1992, "90s", 260000, 276, 7300, 294, 5400, 8000, 1230, "RWD",
        0.58, 2.53, 0.44, [3.066, 2.047, 1.481, 1.166, 0.976], 4.235, 0.314, 0.52, "v6", 6, 6, 13.2,
        "1992 Honda NSX Type R (NA1)", "280 PS @ 7,300 · 30 kgm @ 5,400 · ~1,230 kg · mid-engine RWD · 5-speed close-ratio, 4.24 final",
        sprite="kirin", vtec=5800, grip=1.0, avail="prize"),
    keep("kite", 16.0, "1994 Honda Civic Si hatchback (EG)", "125 hp @ 6,600 · 106 lb-ft @ 5,200 · ~2,350 lb · FWD · 5-speed, 4.25 final",
         engine_vtec=4800),
    keep("kite_ef", 16.3, "1990 Honda Civic Si hatchback (EF); boss car of Joe \"Ground Control\", built to the user's friend's real car",
         "108 hp @ 6,000 · 100 lb-ft @ 5,000 · ~2,250 lb · FWD · 5-speed, 4.25 final", avail="boss"),
    car("kite_rx", "Kite RX", "Hikari", 1997, "90s", 30000, 182, 8200, 160, 7500, 8900, 1050, "FWD",
        0.38, 2.62, 0.49, [3.23, 2.105, 1.458, 1.107, 0.848], 4.785, 0.298, 0.6, "i4", 4, 6, 15.1,
        "1997 Honda Civic Type R (EK9, B16B)", "185 PS @ 8,200 · 16.3 kgm @ 7,500 · ~1,050 kg · FWD · 5-speed close-ratio, 4.79 final",
        sprite="kite", vtec=5800, grip=0.93, e_inertia=0.11),
    keep("arrow", 15.1, "1997 Acura Integra Type R (DC2)", "195 hp @ 8,000 · 130 lb-ft @ 7,500 · ~2,640 lb · FWD · 5-speed close-ratio, 4.40 final",
         engine_vtec=5700),
    car("dart", "Dart Si", "Hikari", 1988, "80s", 7000, 105, 6000, 133, 5000, 6800, 958, "FWD",
        0.38, 2.3, 0.48, [3.25, 1.894, 1.259, 0.937, 0.771], 4.25, 0.289, 0.55, "i4", 4, 0, 16.3,
        "1988 Honda CRX Si (EF8)", "105 hp @ 6,000 · 98 lb-ft @ 5,000 · ~2,112 lb · FWD · 5-speed, 4.25 final",
        sprite="kite_ef", grip=0.88, e_inertia=0.1),
    car("overture", "Overture VTX", "Hikari", 1993, "90s", 14000, 190, 6800, 214, 5500, 7500, 1300, "FWD",
        0.38, 2.55, 0.5, [3.307, 1.809, 1.23, 0.933, 0.757], 4.266, 0.303, 0.62, "i4", 4, 7, 15.4,
        "1993 Honda Prelude VTEC (BB1, H22A)", "190 hp @ 6,800 · 158 lb-ft @ 5,500 · ~2,866 lb · FWD · 5-speed, 4.27 final",
        sprite="arrow", vtec=5200, grip=0.92),
    car("tenshi", "Tenshi 2000", "Hikari", 2000, "90s", 42000, 240, 8300, 208, 7500, 9000, 1274, "RWD",
        0.5, 2.4, 0.45, [3.133, 2.045, 1.481, 1.161, 0.971, 0.811], 4.1, 0.316, 0.62, "i4", 4, 11, 14.2,
        "2000 Honda S2000 (AP1, F20C)", "240 hp @ 8,300 · 153 lb-ft @ 7,500 · ~2,809 lb · RWD · 6-speed, 4.10 rear",
        sprite="asp", vtec=6000, grip=0.98, e_inertia=0.11),
    # --- Tsubasa (Mitsubishi) -------------------------------------------------------------------
    car("halberd_4", "Halberd IV", "Tsubasa", 1996, "90s", 40000, 276, 6500, 352, 3000, 7500, 1350, "AWD",
        0.4, 2.51, 0.52, [2.785, 1.95, 1.444, 1.096, 0.761], 4.529, 0.314, 0.68, "i4", 4, 6, 13.8,
        "1996 Mitsubishi Lancer Evolution IV GSR (CN9A)", "280 PS @ 6,500 · 36 kgm @ 3,000 · ~1,350 kg · AWD (AYC) · 5-speed, 4.53 final",
        sprite="kaminari_mk1", turbo=True, grip=0.93),
    car("halberd_5", "Halberd V", "Tsubasa", 1998, "90s", 52000, 276, 6500, 373, 3000, 7500, 1360, "AWD",
        0.4, 2.51, 0.52, [2.785, 1.95, 1.444, 1.096, 0.761], 4.529, 0.317, 0.68, "i4", 4, 9, 13.7,
        "1998 Mitsubishi Lancer Evolution V GSR (CP9A)", "280 PS @ 6,500 · 38 kgm @ 3,000 · ~1,360 kg · AWD (AYC) · 5-speed, 4.53 final",
        sprite="kaminari_mk1", turbo=True, grip=0.97),
    car("halberd_6", "Halberd VI", "Tsubasa", 1999, "90s", 58000, 276, 6500, 373, 3000, 7500, 1360, "AWD",
        0.4, 2.51, 0.52, [2.785, 1.95, 1.444, 1.096, 0.761], 4.529, 0.317, 0.66, "i4", 4, 0, 13.6,
        "1999 Mitsubishi Lancer Evolution VI GSR (CP9A)", "280 PS @ 6,500 · 38 kgm @ 3,000 · ~1,360 kg · AWD (AYC) · 5-speed, 4.53 final",
        sprite="kaminari_mk1", turbo=True, grip=0.98),
    keep("typhoon", 13.8, "1991 Mitsubishi 3000GT VR-4", "300 hp @ 6,000 · 307 lb-ft @ 2,500 · ~3,740 lb · AWD · 5-speed, 4.15 final"),
    keep("squall", 14.5, "1995 Mitsubishi Eclipse GSX (2G)", "210 hp @ 6,000 · 214 lb-ft @ 3,000 · ~3,200 lb · AWD · 5-speed, 4.15 final"),
    car("stellar", "Stellar ESR", "Tsubasa", 1988, "80s", 13000, 188, 5000, 317, 2500, 6000, 1334, "RWD",
        0.47, 2.435, 0.5, [3.369, 2.035, 1.36, 1.0, 0.856], 3.545, 0.316, 0.68, "i4", 4, 0, 15.8,
        "1988 Mitsubishi Starion ESI-R (4G54 turbo)", "188 hp @ 5,000 · 234 lb-ft @ 2,500 · ~2,940 lb · RWD · 5-speed, 3.55 rear",
        sprite="zephyr", turbo=True, grip=0.9),
    # --- Seiza (Subaru) -------------------------------------------------------------------------
    car("comet", "Comet STX", "Seiza", 1998, "90s", 45000, 276, 6500, 352, 4000, 8000, 1270, "AWD",
        0.4, 2.52, 0.52, [3.083, 2.062, 1.545, 1.151, 0.825], 4.444, 0.306, 0.68, "flat4", 4, 9, 13.7,
        "1998 Subaru Impreza WRX STI Version V (GC8)", "280 PS @ 6,500 · 36 kgm @ 4,000 · ~1,270 kg · AWD (DCCD) · 5-speed, 4.44 final",
        sprite="kaminari_mk1", turbo=True, grip=0.95),
    car("comet_22x", "Comet 22X", "Seiza", 1998, "90s", 280000, 276, 6000, 363, 3200, 7900, 1270, "AWD",
        0.4, 2.52, 0.52, [3.083, 2.062, 1.545, 1.151, 0.825], 3.9, 0.31, 0.7, "flat4", 4, 9, 13.4,
        "1998 Subaru Impreza 22B STi (GC8, EJ22G)", "280 PS @ 6,000 · 37 kgm @ 3,200 · ~1,270 kg · AWD · 5-speed, 3.90 final",
        sprite="kaminari_mk1", turbo=True, grip=0.98, avail="prize"),
    # --- American legends -----------------------------------------------------------------------
    car("ranger_500", "Ranger 500", "Carver", 1967, "classic", 160000, 355, 5400, 569, 3200, 5800, 1588, "RWD",
        0.43, 2.743, 0.54, [2.32, 1.69, 1.29, 1.0], 3.5, 0.33, 0.88, "v8", 8, 6, 14.7,
        "1967 Shelby GT500 (Mustang fastback, 428 Police Interceptor)", "355 hp gross @ 5,400 · 420 lb-ft @ 3,200 · ~3,500 lb · RWD · 4-speed Top Loader, 3.50 rear",
        sprite="ranger", rating="gross", grip=0.84),
    car("mamba", "Mamba 427", "Carver", 1965, "classic", 300000, 425, 6000, 651, 3700, 6500, 1147, "RWD",
        0.48, 2.286, 0.46, [2.36, 1.61, 1.2, 1.0], 3.54, 0.33, 0.85, "v8", 8, 9, 12.4,
        "1965 Shelby Cobra 427 (street)", "425 hp gross @ 6,000 · 480 lb-ft @ 3,700 · ~2,529 lb · RWD · 4-speed Top Loader, 3.54 rear",
        sprite="asp", rating="gross", grip=0.84, avail="barn"),
    car("lm40", "LM40 Road", "Harlan", 1967, "classic", 300000, 306, 6000, 446, 4200, 6500, 1030, "RWD",
        0.58, 2.42, 0.42, [2.42, 1.47, 1.09, 0.958, 0.846], 3.77, 0.33, 0.55, "v8", 8, 10, 13.1,
        "1967 Ford GT40 Mk III road car (289 HiPo)", "306 hp gross @ 6,000 · 329 lb-ft @ 4,200 · ~1,030 kg · mid-engine RWD · 5-speed ZF, 3.77 final",
        sprite="kirin", rating="gross", grip=0.88, avail="prize"),
    keep("phantom", 13.4, "1987 Buick GNX", "276 hp rated (~300 real) @ 4,400 · 360 lb-ft @ 3,000 · ~3,545 lb · RWD · 4-speed auto 200-4R, 3.42 rear",
         converter=True),
    keep("vortex", 13.0, "1990 Chevrolet Corvette ZR-1 (C4)", "375 hp @ 5,800 · 370 lb-ft @ 4,800 · ~3,465 lb · RWD · 6-speed ZF, 3.45 rear"),
    car("asp", "Asp GTS", "Dominion", 1996, "90s", 190000, 450, 5200, 664, 3700, 6000, 1535, "RWD",
        0.5, 2.444, 0.45, [2.66, 1.78, 1.3, 1.0, 0.74, 0.5], 3.07, 0.329, 0.82, "v10", 10, 9, 12.4,
        "1996 Dodge Viper GTS", "450 hp @ 5,200 · 490 lb-ft @ 3,700 · ~3,383 lb · RWD · 6-speed T56, 3.07 rear",
        sprite="asp", grip=1.0, e_inertia=0.32, w_inertia=3.0),
    keep("mammoth", 13.8, "1970 Chevrolet Chevelle SS 454 LS6", "450 hp @ 5,600 · 500 lb-ft @ 3,600 · ~3,800 lb · RWD · 4-speed Muncie M22, 3.73 rear"),
    car("condor", "Condor 440", "Dominion", 1970, "classic", 140000, 375, 4600, 651, 3200, 5200, 1742, "RWD",
        0.43, 2.95, 0.56, [2.66, 1.91, 1.39, 1.0], 3.54, 0.32, 0.68, "v8", 8, 0, 14.4,
        "1970 Plymouth Road Runner Superbird (440 Super Commando)", "375 hp gross @ 4,600 · 480 lb-ft @ 3,200 · ~3,840 lb · RWD · 4-speed A833, 3.54 rear",
        sprite="vandal", rating="gross", grip=0.83, avail="boss"),
    # --- European legends -----------------------------------------------------------------------
    car("wal", "Wal Turbo", "Kessler", 1979, "classic", 80000, 296, 5500, 412, 4000, 6800, 1300, "RWD",
        0.61, 2.272, 0.48, [2.25, 1.304, 0.893, 0.656], 4.222, 0.316, 0.72, "flat6", 6, 13, 13.6,
        "1979 Porsche 911 Turbo 3.3 (930)", "300 PS @ 5,500 · 42 kgm @ 4,000 · ~1,300 kg · rear-engine RWD · 4-speed, 4.22 final",
        sprite="kessler", turbo=True, grip=0.95),
    car("wal_awd", "Wal Turbo AWD", "Kessler", 1996, "90s", 170000, 402, 5750, 540, 4500, 6800, 1500, "AWD",
        0.6, 2.272, 0.47, [3.82, 2.15, 1.56, 1.21, 0.97, 0.75], 3.444, 0.314, 0.66, "flat6", 6, 11, 12.3,
        "1996 Porsche 911 Turbo (993)", "408 PS @ 5,750 · 540 Nm @ 4,500 · ~1,500 kg · rear-engine AWD · 6-speed, 3.44 final",
        sprite="kessler", turbo=True, grip=1.02),
    car("isar_s3", "Isar S3 Sport", "Isar", 1988, "80s", 36000, 192, 6750, 230, 4750, 7250, 1300, "RWD",
        0.47, 2.565, 0.5, [3.83, 2.2, 1.4, 1.0, 0.81], 4.1, 0.303, 0.64, "i4", 4, 6, 15.3,
        "1988 BMW M3 (E30, S14)", "192 hp @ 6,750 · 170 lb-ft @ 4,750 · ~2,865 lb · RWD · 5-speed dogleg Getrag 265, 4.10 rear",
        sprite="ranger50", grip=0.92),
    car("quaranta", "Quaranta", "Fenice", 1990, "90s", 320000, 478, 7000, 577, 4000, 7750, 1254, "RWD",
        0.58, 2.45, 0.42, [2.769, 1.722, 1.227, 0.962, 0.766], 2.727, 0.333, 0.65, "v8", 8, 0, 11.9,
        "1990 Ferrari F40", "478 hp @ 7,000 · 426 lb-ft @ 4,000 · ~1,254 kg · mid-engine RWD · 5-speed, 2.73 final",
        sprite="kirin", turbo=True, grip=1.02, avail="prize", exotic=True),
    car("scarlatta", "Scarlatta", "Fenice", 1986, "80s", 150000, 380, 5750, 490, 4500, 6800, 1660, "RWD",
        0.59, 2.55, 0.46, [3.139, 2.104, 1.522, 1.156, 0.917], 3.21, 0.331, 0.68, "flat12", 12, 0, 13.5,
        "1986 Ferrari Testarossa (US)", "380 hp @ 5,750 · 361 lb-ft @ 4,500 · ~3,660 lb · mid-engine RWD · 5-speed, 3.21 final",
        sprite="kirin", grip=0.96, exotic=True),
    car("zanna", "Zanna 5000", "Furia", 1985, "80s", 230000, 420, 7000, 462, 5000, 7500, 1520, "RWD",
        0.58, 2.45, 0.44, [2.256, 1.769, 1.31, 1.04, 0.857], 4.091, 0.311, 0.86, "v12", 12, 4, 13.0,
        "1985 Lamborghini Countach 5000 Quattrovalvole (US)", "420 hp @ 7,000 · 341 lb-ft @ 5,000 · ~3,350 lb · mid-engine RWD · 5-speed, 4.09 final; tall wing",
        sprite="kirin", grip=1.0, exotic=True),
    car("wraith", "Wraith Turbo SE", "Arden", 1989, "80s", 60000, 264, 6500, 354, 4250, 7000, 1270, "RWD",
        0.57, 2.44, 0.44, [3.364, 2.059, 1.381, 1.057, 0.821], 3.889, 0.33, 0.6, "i4", 4, 6, 13.8,
        "1989 Lotus Esprit Turbo SE", "264 hp @ 6,500 · 261 lb-ft @ 4,250 · ~2,800 lb · mid-engine RWD · 5-speed, 3.89 final",
        sprite="kirin", turbo=True, grip=0.93),
]

# Factory turbo character from each real setup (engine.lag in s, engine.spoolRpm): without an entry a
# turbo is on full boost by the torque peak after 0.6 s. Twin and sequential turbos snap on sooner;
# big early singles make you wait (the 930 most of all).
TURBO = {
    "kaminari_mk1": dict(lag=0.45), "kaminari_mk2": dict(lag=0.45), "kaminari_mk3": dict(lag=0.45),
    "tsunami": dict(lag=0.4, spoolRpm=2400), "spiral_r": dict(lag=0.45, spoolRpm=3000),
    "typhoon": dict(lag=0.45), "zephyr_tt": dict(lag=0.45), "wal_awd": dict(lag=0.45),
    "phantom": dict(lag=0.5), "pursuit": dict(lag=0.55),
    "tsunami_mk3": dict(lag=0.75), "ronin": dict(lag=0.7), "seiran": dict(lag=0.7),
    "stellar": dict(lag=0.85), "wraith": dict(lag=0.8), "quaranta": dict(lag=0.7, spoolRpm=3400),
    "wal": dict(lag=1.0, spoolRpm=3200),
}

# Gated shifters: the exotics change gear slower than the era's default. An automatic's shift is
# quick and never takes the clutch out: the GNX's power gap is short.
SHIFT = {"zanna": 0.38, "scarlatta": 0.36, "quaranta": 0.33, "phantom": 0.15}

# Prices (2026-10-25 rebalance): a band per stock class (STREET 6-30k, SPORT 28-75k, PRO 70-180k,
# OUTLAW 180-340k), placed in it by the stock quarter mile, x1.05-1.2 for icons. Cars nobody sells
# (boss, barn, prize) get the same book value: it only sets what the dealer pays for them.
PRICES = {
    "shirasagi": 8500,
    "kite_ef": 9500,
    "zephyr_240": 10500,
    "kestrel": 10500,
    "kaminari_c10": 11000,
    "prairie": 11500,
    "dart": 11500,
    "kite": 13500,
    "kokoro": 14000,
    "spiral_sa": 16000,
    "seiran": 22000,
    "tsunami_mk3": 23000,
    "spiral_turbo": 24000,
    "squall": 24000,
    "stellar": 24000,
    "overture": 26000,
    "arrow": 27000,
    "ronin": 28000,
    "isar_s3": 32000,
    "tenshi": 32000,
    "sylph_14": 34000,
    "sylph_180": 34000,
    "kite_rx": 36000,
    "sylph_13": 37000,
    "phantom": 43000,
    "typhoon": 51000,
    "condor": 52000,
    "pursuit": 55000,
    "kaminari_mk2": 56000,
    "ranger_500": 57000,
    "wal": 57000,
    "sylph_15": 59000,
    "zephyr_tt": 61000,
    "kaminari_mk1": 68000,
    "kirin": 80000,
    "wraith": 80000,
    "mammoth": 81000,
    "halberd_4": 82000,
    "halberd_5": 90000,
    "tsunami": 95000,
    "halberd_6": 95000,
    "kaminari_mk3": 100000,
    "comet_22x": 120000,
    "spiral_r": 130000,
    "comet": 130000,
    "scarlatta": 135000,
    "kirin_r": 150000,
    "vortex": 165000,
    "lm40": 180000,
    "quaranta": 205000,
    "zanna": 205000,
    "mamba": 235000,
    "wal_awd": 265000,
    "asp": 280000,
}

BRANDS = ("Raijin (Nissan), Hayate (Toyota), Kitsune (Mazda), Hikari (Honda/Acura), Tsubasa (Mitsubishi), Seiza (Subaru), "
          "Carver (Shelby), Harlan (Ford), Crestline (Chevrolet), Regent (Buick), Dominion (Dodge/Plymouth), Kessler (Porsche), "
          "Isar (BMW), Fenice (Ferrari), Furia (Lamborghini), Arden (Lotus).")

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
        if c["id"] in SHIFT:
            d["shiftTime"] = SHIFT[c["id"]]
        path.write_text(json.dumps(d, indent=2) + "\n")
        rows.append(f"| {d['brand']} {d['name']} | `{d['id']}` | {c['_inspired']} | {d['drivetrain']} | {c['_specs']} | {c['_et']:.1f} | {AVAIL[c['avail']]} |")
    (ROOT / "assets" / "data" / "roster.json").write_text(json.dumps(ids, indent=2) + "\n")

    ref = ROOT / "car_references.md"
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
        "('88 IROC-Z), Kessler 940 Turbo ('86 944 Turbo), Raijin Zephyr 300 Turbo ('86 Z31 300ZX). Their ids are never reused.\n")
    print(f"{len(ROSTER)} cars written")


if __name__ == "__main__":
    main()
