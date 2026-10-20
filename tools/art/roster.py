"""The car roster as sprite profiles.

Everything is drawn at one scale (~18.2 px per metre, from the Vandal's 96 px for 5.28 m), so
real lengths, wheelbases, overhangs and roof heights carry over. Shapes are authored as
(x, h): x from the rear bumper, h = height above the ground in pixels. Real inspirations and
stock specs are in car_references.md; nothing here uses real names.
"""
from car import GROUND


def Y(h):
    return GROUND - h


def _pts(pts):
    return [(x, Y(h)) for x, h in pts]


def _rect(r):
    x0, h0, x1, h1 = r
    return (x0, Y(h1), x1, Y(h0))


def make(id, length, wheel_r, rear_x, front_x, belt, nose, tail, rocker, greenhouse, glass, head, tail_lamp,
         rim, bumpers="chrome", front_bumper=None, rear_bumper=None, grille=None, popup=None, extras=(),
         rear_strip=False, crease=0.22, flare=0.0, seams=None, exhaust=None, gas_cap=None, tail_style=None,
         tail_count=2, dark_quarters=False, mirror=True, arch=None, chrome_surround=None, wing_belt=None):
    beltline = _pts(belt)
    body = beltline + _pts(nose)
    body += [(front_x + wheel_r * 1.25, Y(rocker)), (rear_x - wheel_r * 1.25, Y(rocker))]
    body += _pts(tail)
    gh = _pts(greenhouse)
    glass_polys = [_pts(g) for g in glass]
    (xr, yr), (xb, yb) = gh[-2], gh[-1]
    windshield = [(xr - 1.6, yr + 0.3), (xr, yr + 0.1), (xb, yb), (xb - 2.4, yb)]
    strip = None
    if rear_strip:
        (x0, y0), (x1, y1) = gh[0], gh[1]
        strip = [(x0, y0), (x0 + 2.2, y0), (x1 + 1.2, y1 + 0.3), (x1, y1 + 0.2)]
    main = max(glass_polys, key=lambda g: max(x for x, _ in g) - min(x for x, _ in g)) if glass_polys else gh
    gx0 = min(x for x, _ in main)
    belt_h_at = lambda x: next((h for (xa, h), (xb2, _) in zip(belt, belt[1:]) if xa <= x <= xb2), belt[0][1])
    tips = exhaust or [(2.0, rocker + 0.9), (6.0, rocker + 0.9)]
    p = {
        "id": id,
        "length": length,
        "wheel_r": wheel_r,
        "rear_wheel": (rear_x, Y(wheel_r)),
        "front_wheel": (front_x, Y(wheel_r)),
        "arch_r": arch or wheel_r * 1.19,
        "body": body,
        "beltline": beltline,
        "rocker_y": Y(rocker),
        "crease_t": crease,
        "flare": flare,
        "greenhouse": gh,
        "glass": glass_polys,
        "windshield": windshield,
        "rear_strip": strip,
        "dark_quarters": dark_quarters,
        "door_seams": seams if seams is not None else [gx0 - 1.5, xb - 2.0],
        "handle": (gx0 + 2, Y(belt_h_at(gx0 + 2) - 1.8)),
        "mirror": (xb - 3.5, yb - 1.6) if mirror else None,
        "bumpers": bumpers,
        "chrome_surround": chrome_surround if chrome_surround is not None else bumpers == "chrome",
        "front_bumper": [(x, Y(h)) for x, h in _box(front_bumper)] if front_bumper else None,
        "rear_bumper": [(x, Y(h)) for x, h in _box(rear_bumper)] if rear_bumper else None,
        "grille": _rect(grille) if grille else None,
        "tail_lamp": _rect(tail_lamp),
        "tail_style": tail_style,
        "tail_count": tail_count,
        "head_lamp": _rect(head),
        "popup": (popup[0], popup[1], Y(popup[2]), Y(popup[3])) if popup else None,
        "markers": [((front_x + wheel_r * 1.45, Y(rocker + 5.5)), "orange"), ((rear_x - wheel_r * 1.5, Y(rocker + 5.5)), "red")],
        "gas_cap": (gas_cap[0], Y(gas_cap[1])) if gas_cap else None,
        "exhaust_tips": [(x, Y(h)) for x, h in tips],
        "exhaust": (tips[0][0], Y(tips[0][1])),
        "hotspots": [(front_x + 2, front_x + 9), (rear_x - 6, rear_x), ((rear_x + front_x) / 2 - 2, (rear_x + front_x) / 2 + 1)],
        "rim": rim,
        "extras": [_extra(e) for e in extras],
    }
    return p


def _box(b):
    x0, x1, h0, h1 = b
    return [(x0, h1), (x1, h1), (x1, h0), (x0, h0)]


def _extra(e):
    e = dict(e)
    if "rect" in e:
        e["rect"] = _rect(e["rect"])
    if "polys" in e:
        e["polys"] = [_pts(poly) for poly in e["polys"]]
    if "pts" in e:
        e["pts"] = _pts(e["pts"])
    return e


def wing(x0, x1, h_post, h_blade, thick=2.2, posts=None):
    """Painted rear wing: a blade on posts rising from the deck."""
    posts = posts or [x0 + 2, x1 - 2.5]
    polys = [[(x0, h_blade + thick), (x1 - 0.5, h_blade + thick * 0.85), (x1, h_blade + 0.3), (x0 + 0.3, h_blade - 0.2)]]
    for px in posts:
        polys.append([(px, h_post), (px + 1.2, h_post), (px + 1.0, h_blade), (px - 0.1, h_blade)])
    return {"kind": "wing", "polys": polys}


MAGNUM = {"style": "magnum", "spokes": 5, "color": "chrome", "letters": True}

# --- Classics ----------------------------------------------------------------------------------

VANDAL = {
    # '69 Charger R/T: long hood, tunnel-roof sail panels, coke-bottle hips, hidden lamps.
    "id": "vandal",
    "length": 96,
    "wheel_r": 7.3,
    "rear_wheel": (20.0, 24.2),
    "front_wheel": (76.5, 24.2),
    "arch_r": 8.7,
    "body": [(0.4, 14.6), (1.6, 13.1), (6, 12.5), (13, 12.0), (19, 11.5), (25, 11.9), (33, 12.5),
             (46, 12.7), (60, 12.8), (74, 13.1), (86, 13.5), (92.5, 14.0), (95.4, 14.6), (96.4, 15.6),
             (95.9, 21.8), (95.2, 24.6), (93.2, 26.3), (88, 26.8), (64, 26.9), (30, 26.9), (8, 26.8),
             (2.6, 26.2), (0.9, 24.8), (0.3, 21.0)],
    "beltline": [(0, 13.6), (6, 12.5), (13, 12.0), (19, 11.5), (25, 11.9), (33, 12.5), (46, 12.7),
                 (60, 12.8), (74, 13.1), (86, 13.5), (92.5, 14.0), (96, 15.0)],
    "rocker_y": 26.8,
    "crease_t": 0.2,
    "flare": 0.0,
    "greenhouse": [(9.5, 12.3), (15, 10.6), (21, 7.8), (27, 5.3), (32, 4.1), (45, 4.0),
                   (49, 4.5), (52.5, 6.3), (62.6, 12.9)],
    "glass": [[(29.8, 11.7), (32.0, 6.6), (35.0, 5.4), (45.2, 5.4), (48.8, 6.9), (54.4, 11.7)]],
    "windshield": [(48.6, 4.6), (50.4, 4.5), (62.0, 12.6), (59.8, 12.6)],
    "rear_strip": None,
    "door_seams": [31.5, 57.0],
    "handle": (50, 15),
    "mirror": (58, 10),
    "bumpers": "chrome",
    "front_bumper": [(91.5, 21.6), (96.4, 21.6), (96.4, 23.4), (91.5, 23.4)],
    "rear_bumper": [(-0.4, 21.2), (5.0, 21.2), (5.0, 22.9), (-0.4, 22.9)],
    "grille": (94.6, 15.2, 96.2, 21.2),
    "tail_lamp": (-0.2, 14.6, 0.9, 18.6),
    "head_lamp": (95.0, 16.4, 96.4, 18.6),
    "markers": [((89, 17), "orange"), ((4, 17), "red")],
    "gas_cap": (9, 13.5),
    "exhaust_tips": [(2.0, 25.6), (6.5, 25.6)],
    "exhaust": (2.0, 25.6),
    "hotspots": [(78, 86), (12, 18), (40, 43)],
    "rim": MAGNUM,
    "extras": [],
}

RANGER = make(  # '67 Mustang fastback GT: long hood, straight fastback slope, side scoops, tri-bar tails
    "ranger", 84, 6.6, 17, 67,
    belt=[(0, 14.5), (6, 15.5), (20, 15.6), (34, 15.6), (56, 15.4), (70, 15.2), (80, 14.9), (84, 14.5)],
    nose=[(84.6, 13.2), (84.4, 9), (83.4, 6.2)],
    tail=[(0.6, 6.2), (0, 10), (0, 13.6)],
    rocker=4.8,
    greenhouse=[(8, 16.0), (20, 19.4), (32, 22.6), (40, 24.0), (46, 24.1), (48.5, 23.2), (56.5, 15.4)],
    glass=[[(35, 16.4), (38.5, 22.6), (46, 23.0), (48, 22.0), (53, 16.4)]],
    head=(82.6, 11, 84.4, 13), tail_lamp=(-0.2, 10.2, 0.8, 13.6),
    rim={"style": "rally", "spokes": 6, "color": "silver", "letters": True},
    front_bumper=(80, 85, 7.4, 9.0), rear_bumper=(-0.5, 5, 7.4, 9.2), grille=(83.4, 9.4, 84.6, 12.8),
    extras=[{"kind": "vent", "rect": (25, 10.4, 30, 13.2), "slots": 3},     # side scoop ahead of the rear wheel
            {"kind": "vent", "rect": (14, 17.4, 19, 18.6), "slots": 3}],    # C-pillar louvres
    gas_cap=(3, 14.2),
)

CORSAIR = make(  # '69 Camaro SS: long hood, short deck with ducktail, notchback, sculpted flanks
    "corsair", 86, 6.6, 19, 69,
    belt=[(0, 14.2), (4, 15.0), (16, 15.3), (22, 15.5), (40, 15.4), (55, 15.0), (70, 14.8), (82, 14.4), (86, 13.8)],
    nose=[(86.4, 12.5), (86.2, 8), (85, 6)],
    tail=[(1, 6), (0, 9.5), (0, 13.6)],
    rocker=4.6,
    greenhouse=[(22, 15.5), (32, 22.0), (36, 23.6), (46, 23.7), (50, 22.8), (58.4, 15.2)],
    glass=[[(34, 16.4), (36.5, 22.4), (46, 22.6), (49, 21.8), (55, 16.4)]],
    head=(84.6, 10.4, 86.2, 12.0), tail_lamp=(-0.2, 10.6, 0.8, 13.0),
    rim={"style": "rally", "spokes": 5, "color": "silver", "letters": True},
    front_bumper=(82, 86.8, 7.2, 8.6), rear_bumper=(-0.5, 5, 7.2, 8.8), grille=(85, 8.8, 86.4, 12.6),
    extras=[{"kind": "ducktail", "polys": [[(0, 14.2), (5, 15.2), (5, 16.0), (0.4, 15.0)]]},
            {"kind": "vent", "rect": (28, 10.0, 31, 12.4), "slots": 2}],
    crease=0.3,
)

MAMMOTH = make(  # '70 Chevelle SS 454: long flat sides, formal roof, cowl-induction hood, bumper tail lamps
    "mammoth", 92, 6.8, 22, 74,
    belt=[(0, 15.2), (5, 15.6), (18, 15.8), (24, 16.0), (60, 16.0), (62, 16.6), (70, 16.8), (78, 16.0), (88, 15.6), (92, 15.3)],
    nose=[(92.4, 14), (92, 8), (91, 6)],
    tail=[(1, 6), (0, 9), (0, 14.6)],
    rocker=4.9,
    greenhouse=[(24, 16.0), (29, 21.5), (34, 24.4), (50, 24.6), (54, 24.0), (62, 16.6)],
    glass=[[(35, 16.8), (37, 23.6), (49.5, 23.8), (53, 23.0), (59, 16.8)]],
    head=(90.8, 11, 92.4, 13.4), tail_lamp=(-0.3, 7.6, 0.7, 9.6),
    rim={"style": "magnum", "spokes": 5, "color": "chrome", "letters": True},
    front_bumper=(87, 92.8, 7.0, 9.0), rear_bumper=(-0.6, 6, 7.0, 10.0), grille=(91.6, 9.2, 92.6, 13.6),
    extras=[{"kind": "scoop", "rect": (63, 16.6, 69, 17.6)}],   # cowl-induction flap
)

PIRANHA = make(  # '70 'Cuda Hemi: low E-body, shaker scoop through the hood, high rear hips
    "piranha", 86, 6.8, 20, 70,
    belt=[(0, 14.0), (4, 14.8), (12, 15.4), (21, 15.0), (40, 14.8), (54, 14.6), (70, 14.4), (84, 14.0), (86, 13.6)],
    nose=[(86.4, 12.5), (86.2, 8.5), (85, 6)],
    tail=[(0.8, 6), (0, 9), (0, 13.4)],
    rocker=4.6,
    greenhouse=[(21, 15.0), (30, 21.4), (34, 23.4), (44, 23.5), (48, 22.6), (56, 14.6)],
    glass=[[(32, 16.0), (34.5, 22.4), (44, 22.6), (47, 21.6), (53, 16.0)]],
    head=(84.8, 10.6, 86.2, 12.4), tail_lamp=(-0.2, 10.0, 0.8, 12.6),
    rim={"style": "magnum", "spokes": 5, "color": "silver", "letters": True},
    front_bumper=(82, 86.8, 7.0, 8.4), rear_bumper=(-0.5, 5, 7.0, 8.6), grille=(85.4, 8.6, 86.6, 12.6),
    extras=[{"kind": "shaker", "rect": (63, 14.4, 70, 16.9)}],
)

WARRIOR = make(  # '64 GTO hardtop: long, boxy, flat roof, twin hood scoops, crisp side crease
    "warrior", 94, 6.6, 23, 76,
    belt=[(0, 15.0), (6, 15.4), (20, 15.6), (25, 15.8), (64, 15.7), (80, 15.4), (92, 15.2), (94, 15.0)],
    nose=[(94.4, 13.6), (94, 8), (93, 6.2)],
    tail=[(1, 6), (0, 9.5), (0, 14.4)],
    rocker=5.0,
    greenhouse=[(25, 15.8), (31, 22.6), (36, 24.4), (53, 24.5), (56, 23.8), (64, 15.7)],
    glass=[[(35, 16.6), (37.5, 23.6), (52, 23.8), (55, 23.0), (61, 16.6)]],
    head=(92.8, 11.4, 94.4, 13.4), tail_lamp=(-0.2, 10.0, 0.8, 12.6),
    rim={"style": "hubcap", "spokes": 10, "color": "chrome", "letters": True},
    front_bumper=(89, 94.8, 7.2, 9.0), rear_bumper=(-0.6, 6, 7.2, 9.2), grille=(93.4, 9.2, 94.6, 13.6),
    extras=[{"kind": "scoop", "rect": (67, 15.6, 72, 16.5)}],
    crease=0.18,
)

# --- 80s -----------------------------------------------------------------------------------------

PHANTOM = make(  # '87 GNX: boxy G-body, near-vertical rear glass, flares, fender vents, all black
    "phantom", 92, 6.5, 23, 73,
    belt=[(0, 15.2), (5, 15.6), (20, 15.8), (25, 16.0), (60, 16.0), (66, 16.4), (76, 16.2), (90, 15.4), (92, 15.0)],
    nose=[(92.3, 13.8), (92, 7), (91, 5.5)],
    tail=[(0.8, 5.5), (0, 9), (0, 14.6)],
    rocker=4.8,
    greenhouse=[(25, 16.0), (28, 22.8), (31, 24.7), (48, 24.8), (51, 24.2), (60, 16.0)],
    glass=[[(32, 16.8), (34, 24.0), (47.5, 24.1), (50, 23.4), (57, 16.8)]],
    head=(90.6, 11, 92.2, 13.4), tail_lamp=(-0.2, 9.6, 0.8, 13.2),
    rim={"style": "mesh", "spokes": 10, "color": "black"},
    bumpers="body", front_bumper=(88, 92.4, 5.6, 9.6), rear_bumper=(-0.4, 4, 5.6, 9.4), grille=(91.6, 9.8, 92.6, 13.6),
    extras=[{"kind": "gills", "rect": (62, 9.2, 66.5, 12.2), "slots": 3},
            {"kind": "lip", "polys": [[(0, 15.2), (4, 15.6), (4, 16.2), (0.3, 15.8)]]}],
    flare=1.2, arch=7.9,
)

RANGER50 = make(  # '88 Fox-body 5.0 LX notch: upright square cabin with B-pillar, aero nose, light
    "ranger50", 83, 6.0, 20, 66,
    belt=[(0, 15.0), (4, 15.5), (16, 15.6), (21, 15.7), (55, 15.5), (66, 15.0), (76, 14.0), (82, 12.6)],
    nose=[(83, 11.0), (83, 7.5), (82, 5.6)],
    tail=[(0.6, 5.8), (0, 9), (0, 14.4)],
    rocker=4.6,
    greenhouse=[(21, 15.7), (24, 22.6), (27, 24.0), (43, 24.0), (46, 23.2), (55, 15.5)],
    glass=[[(33, 16.4), (33, 22.9), (42.5, 22.9), (45, 22.0), (52, 16.4)], [(24.8, 16.4), (27, 22.8), (31, 22.9), (31, 16.4)]],
    head=(80.2, 10.6, 82.6, 12.4), tail_lamp=(-0.2, 10.0, 0.8, 13.6),
    rim={"style": "turbine", "spokes": 10, "color": "silver"},
    bumpers="body", front_bumper=(78, 83.4, 5.6, 9.0), rear_bumper=(-0.4, 4.5, 5.6, 9.4),
    extras=[{"kind": "skirt", "rect": (26, 4.6, 60, 6.0)}],
    seams=[31.8, 53],
)

CORSAIR_IROC = make(  # '88 IROC-Z: long low wedge, huge hatch glass, ground effects, hatch spoiler
    "corsair_iroc", 89, 6.4, 23, 70,
    belt=[(0, 14.0), (5, 15.0), (8, 15.2), (40, 15.0), (58, 14.8), (72, 14.0), (84, 12.6), (89, 11.6)],
    nose=[(89.4, 10.6), (89.2, 7), (88, 4.6)],
    tail=[(0.6, 5.0), (0, 8), (0, 13.6)],
    rocker=4.0,
    greenhouse=[(8, 15.2), (30, 22.4), (34, 23.0), (44, 23.0), (48, 22.2), (59, 14.8)],
    glass=[[(32, 15.9), (35, 22.1), (44, 22.2), (47, 21.4), (55, 15.9)], [(20, 16.0), (30, 21.6), (31.5, 21.8), (31.5, 16.0)]],
    head=(86.8, 9.6, 89.2, 11.0), tail_lamp=(-0.2, 10.0, 0.8, 13.0),
    rim={"style": "fivestar", "spokes": 5, "color": "silver"},
    bumpers="body", front_bumper=(84, 89.6, 4.6, 8.0), rear_bumper=(-0.4, 4, 5.0, 8.6),
    extras=[{"kind": "lip", "polys": [[(0, 14.0), (6, 15.2), (6, 16.0), (0.4, 15.0)]]},
            {"kind": "skirt", "rect": (28, 4.0, 62, 5.6)}],
    rear_strip=True, dark_quarters=True,
)

KESTREL = make(  # '85 AE86 liftback: tall boxy cabin on a small body, flat panels, pop-ups, big black bumpers
    "kestrel", 76, 5.6, 17, 61,
    belt=[(0, 14.5), (4, 15.0), (12, 15.2), (50, 15.0), (62, 14.6), (72, 14.0), (76, 13.0)],
    nose=[(76.6, 12.0), (76.6, 7.5), (75.8, 5.8)],
    tail=[(0.5, 6.0), (0, 8.5), (0, 14.0)],
    rocker=5.0,
    greenhouse=[(12, 15.2), (22, 22.5), (26, 24.2), (40, 24.2), (42, 23.4), (51, 15.0)],
    glass=[[(33, 16.0), (33, 23.2), (39.5, 23.2), (41.5, 22.4), (48, 16.0)], [(19, 16.0), (24, 21.5), (28, 23.2), (31, 23.2), (31, 16.0)]],
    head=(74.8, 8.2, 76.6, 9.4), tail_lamp=(-0.2, 10.0, 0.8, 13.0),
    rim={"style": "phone", "spokes": 6, "color": "silver"},
    bumpers="black", front_bumper=(72.5, 77.2, 6.4, 9.6), rear_bumper=(-0.6, 3.5, 6.4, 9.6),
    popup=(67, 72, 14.4, 16.6),
    rear_strip=True, seams=[31.8, 49],
)

SPIRAL_TURBO = make(  # '87 FC rotary turbo: smooth wedge, bubble hatch, offset hood scoop, pop-ups
    "spiral_turbo", 78, 6.0, 18, 62,
    belt=[(0, 14.3), (5, 15.0), (10, 15.2), (47, 14.6), (60, 14.2), (70, 13.2), (78, 11.2)],
    nose=[(78.5, 10.0), (78.4, 7.0), (77.5, 5.0)],
    tail=[(0.5, 5.5), (0, 8.5), (0, 13.8)],
    rocker=4.4,
    greenhouse=[(10, 15.2), (17, 20.5), (24, 23.0), (36, 23.0), (39, 22.2), (48, 14.6)],
    glass=[[(27, 15.8), (29, 22.3), (36, 22.4), (38.5, 21.6), (45, 15.8)], [(17, 15.6), (20, 19.5), (25.5, 22.3), (26, 15.6)]],
    head=(76.4, 8.4, 78.4, 9.4), tail_lamp=(-0.2, 10.4, 0.8, 13.0),
    rim={"style": "fivestar", "spokes": 5, "color": "silver"},
    bumpers="body", front_bumper=(74, 78.8, 5.0, 8.4), rear_bumper=(-0.4, 4, 5.4, 8.8),
    popup=(66, 71, 13.6, 15.8),
    extras=[{"kind": "scoop", "rect": (54, 14.4, 60, 15.4)}],
    rear_strip=True, dark_quarters=True,
)

KESSLER = make(  # '86 944 Turbo: flared arches, glass bubble hatch with rubber spoiler, frog-eye pop-ups
    "kessler", 76, 6.2, 16, 60,
    belt=[(0, 14.4), (4, 15.2), (11, 15.4), (46, 14.6), (58, 14.2), (68, 13.2), (76, 11.4)],
    nose=[(76.6, 10.2), (76.6, 6.8), (75.6, 5.0)],
    tail=[(0.5, 5.6), (0, 8.5), (0, 14.0)],
    rocker=4.6,
    greenhouse=[(11, 15.4), (16, 19.8), (22, 22.8), (34, 23.0), (37, 22.2), (47, 14.6)],
    glass=[[(25, 15.8), (27, 22.3), (34, 22.4), (36.5, 21.6), (44, 15.8)], [(17, 15.8), (19.5, 19.4), (23.5, 21.9), (23.5, 15.8)]],
    head=(74.6, 8.4, 76.6, 9.6), tail_lamp=(-0.2, 10.6, 0.8, 13.2),
    rim={"style": "phone", "spokes": 7, "color": "silver"},
    bumpers="body", front_bumper=(72, 77, 5.0, 8.6), rear_bumper=(-0.4, 4, 5.6, 9.0),
    popup=(65, 70, 13.4, 15.6),
    extras=[{"kind": "skirt", "rect": (4, 15.0, 12, 15.6)}],   # black rubber spoiler under the glass
    rear_strip=True, flare=1.4, arch=7.6,
)

ZEPHYR = make(  # '86 300ZX turbo: long wedge nose, semi-hidden lamps, black sail glass, hood scoop
    "zephyr", 83, 6.2, 23, 65,
    belt=[(0, 14.4), (4, 15.0), (14, 15.2), (48, 14.8), (62, 14.4), (74, 13.2), (83, 11.6)],
    nose=[(83.5, 10.5), (83.4, 7.0), (82.4, 5.0)],
    tail=[(0.5, 5.5), (0, 8.5), (0, 13.8)],
    rocker=4.5,
    greenhouse=[(14, 15.2), (24, 21.4), (29, 23.4), (38, 23.6), (41, 22.8), (50, 14.8)],
    glass=[[(30, 15.8), (31.5, 22.8), (38, 22.9), (40.5, 22.1), (47, 15.8)], [(22, 15.8), (26.5, 20.3), (29, 22.2), (29, 15.8)]],
    head=(76, 11.6, 80, 12.6), tail_lamp=(-0.2, 10.2, 0.8, 13.0),
    rim={"style": "turbine", "spokes": 8, "color": "silver"},
    bumpers="body", front_bumper=(78, 83.8, 5.0, 8.6), rear_bumper=(-0.4, 4, 5.4, 8.8),
    extras=[{"kind": "scoop", "rect": (58, 14.4, 64, 15.4)}],
    rear_strip=True, dark_quarters=True,
)

# --- 90s -----------------------------------------------------------------------------------------

TSUNAMI = make(  # '94 Supra MK4 turbo: round bubble body, hoop wing, oval lamps, rear-quarter intake
    "tsunami", 82, 6.4, 18, 64,
    belt=[(0, 14.6), (3, 15.6), (14, 16.0), (17, 16.0), (34, 15.6), (48, 15.2), (62, 14.6), (74, 13.2), (82, 11.0)],
    nose=[(82.6, 9.6), (82.4, 6.5), (81.4, 4.6)],
    tail=[(0.5, 5.5), (0, 9), (0, 14.0)],
    rocker=4.4,
    greenhouse=[(17, 16.0), (24, 21.5), (30, 23.2), (38, 23.2), (41, 22.4), (50, 15.1)],
    glass=[[(27, 16.4), (30.5, 22.4), (38, 22.5), (40.5, 21.6), (47, 16.2)]],
    head=(78, 10.4, 81.4, 12.0), tail_lamp=(-0.2, 11.0, 0.8, 13.6), tail_style="round",
    rim={"style": "fivestar", "spokes": 5, "color": "silver"},
    bumpers="body", front_bumper=(77, 82.8, 4.6, 8.4), rear_bumper=(-0.4, 4, 5.4, 9.0),
    extras=[wing(0, 11, 15.8, 17.8, posts=[3, 9]), {"kind": "intake", "rect": (22, 10.0, 26, 12.0)}],
    rear_strip=True,
)

KAMINARI_MK1 = make(  # '89 R32 GT-R: crisp boxy coupe, squared flares, trunk blade, quad round tails
    "kaminari_mk1", 83, 6.2, 18, 66,
    belt=[(0, 15.0), (4, 15.8), (13, 16.0), (16, 16.0), (50, 15.8), (66, 15.4), (78, 14.8), (83, 14.0)],
    nose=[(83.4, 12.8), (83.4, 7.5), (82.4, 5.0)],
    tail=[(0.5, 5.5), (0, 9), (0, 14.4)],
    rocker=4.6,
    greenhouse=[(16, 16.0), (24, 22.6), (28, 24.3), (41, 24.4), (44, 23.5), (52, 15.8)],
    glass=[[(29, 16.6), (30, 23.6), (40.5, 23.6), (43, 22.8), (49, 16.6)], [(24.6, 16.6), (26, 21.0), (28, 23.4), (28, 16.6)]],
    head=(81.4, 11.0, 83.2, 13.0), tail_lamp=(-0.2, 11.0, 0.8, 13.8), tail_style="round",
    rim={"style": "sixspoke", "spokes": 6, "color": "silver"},
    bumpers="body", front_bumper=(78, 83.8, 5.0, 9.0), rear_bumper=(-0.4, 4, 5.5, 9.4),
    extras=[wing(1, 9, 15.8, 16.8, thick=1.6, posts=[2.5, 7])],
    flare=1.2,
)

KAMINARI_MK3 = make(  # '99 R34 GT-R: taller and boxier, big adjustable wing, deep intakes, fender vents
    "kaminari_mk3", 84, 6.5, 19, 67,
    belt=[(0, 15.4), (4, 16.3), (12, 16.5), (15, 16.5), (50, 16.0), (67, 15.6), (80, 15.0), (84, 14.6)],
    nose=[(84.5, 13.0), (84.5, 7.0), (83.4, 4.6)],
    tail=[(0.5, 5.6), (0, 9), (0, 14.8)],
    rocker=4.6,
    greenhouse=[(15, 16.5), (21, 22.6), (25, 24.7), (41, 24.8), (44, 24.0), (52, 16.0)],
    glass=[[(28, 17.0), (29, 24.0), (40.5, 24.0), (43, 23.2), (49, 17.0)], [(22, 17.0), (24, 22.0), (27, 23.8), (27, 17.0)]],
    head=(82, 11.6, 84.2, 13.6), tail_lamp=(-0.2, 11.2, 0.8, 14.2), tail_style="round",
    rim={"style": "fivestar", "spokes": 5, "color": "black"},
    bumpers="body", front_bumper=(78, 84.8, 4.6, 9.4), rear_bumper=(-0.4, 4, 5.6, 9.6),
    extras=[wing(0, 11, 16.2, 18.4, thick=2.4, posts=[3, 8.5]), {"kind": "vent", "rect": (56.5, 10.0, 59.5, 12.4), "slots": 2},
            {"kind": "intake", "rect": (81, 5.4, 84.4, 7.4)}],
    flare=1.0,
)

SPIRAL_R = make(  # '93 FD rotary: low, curvy hips and fenders, double-bubble roof, round tails, wing
    "spiral_r", 78, 6.2, 17, 61,
    belt=[(0, 14.2), (3, 15.4), (9, 16.0), (16, 16.2), (22, 15.6), (30, 15.0), (46, 14.8), (56, 15.2), (62, 14.8), (70, 13.2), (78, 10.8)],
    nose=[(78.4, 9.8), (78.2, 6.4), (77, 4.6)],
    tail=[(0.5, 5.4), (0, 9), (0, 13.8)],
    rocker=4.3,
    greenhouse=[(14, 16.1), (19, 20.0), (25, 22.2), (34, 22.4), (37, 21.5), (47, 14.8)],
    glass=[[(25.5, 15.6), (27, 21.6), (34, 21.7), (36.5, 20.8), (44, 15.4)], [(19, 16.0), (21.5, 19.4), (24.5, 21.4), (24.5, 16.0)]],
    head=(76, 8.0, 78.2, 9.0), tail_lamp=(-0.2, 10.6, 0.8, 12.8), tail_style="round",
    rim={"style": "fivestar", "spokes": 5, "color": "silver"},
    bumpers="body", front_bumper=(74, 78.6, 4.6, 8.2), rear_bumper=(-0.4, 4, 5.4, 8.8),
    popup=(64, 69, 13.0, 15.0),
    extras=[wing(0, 8, 15.8, 16.8, thick=1.6, posts=[2, 6])],
    rear_strip=True, dark_quarters=True,
)

KIRIN = make(  # '91 NSX: cab-forward mid-engine, long rear deck, big side intake, tail-spoiler arc, pop-ups
    "kirin", 80, 6.2, 15, 61,
    belt=[(0, 14.6), (2, 15.8), (6, 16.2), (14, 15.8), (24, 15.4), (36, 15.0), (50, 14.6), (60, 14.2), (70, 12.6), (80, 10.0)],
    nose=[(80.4, 9.0), (80.2, 6.0), (79, 4.4)],
    tail=[(0.5, 5.4), (0, 8.5), (0, 14.0)],
    rocker=4.3,
    greenhouse=[(20, 15.6), (28, 19.6), (33, 21.3), (42, 21.3), (46, 20.4), (60, 14.2)],
    glass=[[(32, 15.4), (34, 20.6), (42, 20.7), (44.5, 19.8), (52, 14.8)], [(24, 15.6), (27.5, 18.8), (31, 20.4), (31, 15.4)]],
    head=(77.6, 7.6, 80.2, 8.6), tail_lamp=(-0.2, 11.0, 0.8, 13.6),
    rim={"style": "sevenspoke", "spokes": 7, "color": "silver"},
    bumpers="body", front_bumper=(75, 80.6, 4.4, 7.8), rear_bumper=(-0.4, 4, 5.4, 9.0),
    popup=(65, 70, 13.2, 15.0),
    extras=[{"kind": "intake", "rect": (23, 9.6, 30, 13.0)},
            {"kind": "lip", "polys": [[(0, 14.6), (7, 16.2), (7, 17.0), (0.4, 15.6)]]}],
    rear_strip=True, dark_quarters=True,
)

TYPHOON = make(  # '91 3000GT VR-4: wide heavy wedge, door-side intakes, active spoiler, pop-ups
    "typhoon", 83, 6.4, 19, 64,
    belt=[(0, 14.4), (4, 15.4), (10, 15.6), (12, 15.6), (48, 15.0), (62, 14.6), (72, 13.6), (83, 11.4)],
    nose=[(83.5, 10.2), (83.4, 6.4), (82.2, 4.4)],
    tail=[(0.5, 5.4), (0, 9), (0, 13.8)],
    rocker=4.3,
    greenhouse=[(12, 15.6), (21, 21.2), (27, 23.4), (37, 23.5), (40, 22.6), (49, 15.0)],
    glass=[[(28, 16.0), (30, 22.7), (37, 22.8), (39.5, 21.9), (46, 15.8)], [(20, 16.0), (23.5, 19.8), (27, 22.4), (27, 16.0)]],
    head=(80.8, 8.2, 83.2, 9.2), tail_lamp=(-0.2, 10.4, 0.8, 13.2),
    rim={"style": "sixspoke", "spokes": 6, "color": "silver"},
    bumpers="body", front_bumper=(78, 83.8, 4.4, 8.2), rear_bumper=(-0.4, 4, 5.4, 8.8),
    popup=(68, 73, 13.6, 15.6),
    extras=[wing(1, 11, 15.4, 16.4, thick=1.8, posts=[3, 8]), {"kind": "intake", "rect": (30, 9.6, 35, 12.4)}],
    rear_strip=True, dark_quarters=True,
)

SQUALL = make(  # '95 2G Eclipse AWD turbo: curvy bio body, oval lamps, tall hoop wing
    "squall", 80, 6.2, 16, 62,
    belt=[(0, 14.6), (3, 15.6), (10, 15.8), (12, 15.8), (48, 15.2), (62, 14.8), (72, 13.4), (80, 11.2)],
    nose=[(80.4, 10.0), (80.3, 6.6), (79.2, 4.8)],
    tail=[(0.5, 5.5), (0, 9), (0, 14.0)],
    rocker=4.5,
    greenhouse=[(12, 15.8), (20, 21.0), (26, 23.6), (35, 23.7), (38, 22.8), (49, 15.2)],
    glass=[[(27, 16.2), (29, 22.9), (35, 23.0), (37.5, 22.0), (46, 16.0)], [(19.5, 16.2), (23, 20.2), (26.5, 22.8), (26.5, 16.2)]],
    head=(76, 10.6, 79.6, 12.2), tail_lamp=(-0.2, 10.4, 0.8, 13.2),
    rim={"style": "fivestar", "spokes": 5, "color": "silver"},
    bumpers="body", front_bumper=(75, 80.8, 4.8, 8.6), rear_bumper=(-0.4, 4, 5.5, 9.0),
    extras=[wing(0, 10, 15.6, 17.6, posts=[2, 8.5])],
    rear_strip=True, dark_quarters=True,
)

KITE = make(  # '94 EG hatch: chopped Kamm tail, long roof and wheelbase, roof spoiler, low nose
    "kite", 74, 5.6, 12, 59,
    belt=[(0, 14.8), (6, 15.2), (40, 14.8), (52, 14.4), (64, 13.4), (74, 11.6)],
    nose=[(74.4, 10.6), (74.3, 6.8), (73.4, 5.0)],
    tail=[(0.2, 5.4), (-0.2, 9), (0, 14.4)],
    rocker=4.6,
    greenhouse=[(1.5, 15.0), (7, 22.2), (10.5, 24.3), (30, 24.4), (34, 23.6), (44, 14.8)],
    glass=[[(17, 15.6), (17, 23.6), (29.5, 23.6), (32.5, 22.8), (41, 15.4)], [(9, 15.8), (10.5, 22.6), (12, 23.6), (15, 23.6), (15, 15.6)]],
    head=(71, 10.4, 74.2, 11.8), tail_lamp=(-0.3, 10.4, 0.7, 14.2),
    rim={"style": "hubcap", "spokes": 8, "color": "silver"},
    bumpers="body", front_bumper=(70, 74.8, 5.0, 8.4), rear_bumper=(-0.4, 3.5, 5.4, 9.0),
    extras=[{"kind": "roof_spoiler", "polys": [[(8, 24.0), (14, 24.4), (14, 25.0), (8.5, 25.4)]]}],
    rear_strip=True, seams=[15.8, 42],
)

ARROW = make(  # '97 Integra hatch coupe "RS-X": quad round lamps, tall wing, clean curves, white wheels
    "arrow", 80, 6.0, 15, 62,
    belt=[(0, 14.6), (3, 15.6), (10, 15.8), (11, 15.8), (48, 15.1), (62, 14.8), (72, 13.6), (80, 11.8)],
    nose=[(80.4, 10.6), (80.3, 6.8), (79.2, 4.8)],
    tail=[(0.5, 5.5), (0, 9), (0, 14.0)],
    rocker=4.6,
    greenhouse=[(11, 15.8), (19, 20.8), (25, 23.4), (36, 23.5), (39, 22.6), (49, 15.1)],
    glass=[[(27, 16.2), (29, 22.7), (36, 22.8), (38.5, 21.9), (46, 16.0)], [(18.5, 16.2), (22, 20.0), (26, 22.6), (26, 16.2)]],
    head=(77.4, 10.6, 80.2, 12.2), tail_lamp=(-0.2, 10.4, 0.8, 13.2),
    rim={"style": "fivestar", "spokes": 5, "color": "white"},
    bumpers="body", front_bumper=(75, 80.8, 4.8, 8.6), rear_bumper=(-0.4, 4, 5.5, 9.0),
    extras=[wing(0, 10, 15.6, 17.6, posts=[2, 8])],
    rear_strip=True, dark_quarters=True,
)

VORTEX = make(  # '90 C4 ZR-1: very long low nose, fender gills, glass hatch, flat Kamm tail
    "vortex", 82, 6.6, 18, 62,
    belt=[(0, 14.2), (1.5, 15.0), (8, 15.6), (10, 15.6), (42, 15.0), (58, 14.4), (70, 13.0), (82, 10.4)],
    nose=[(82.6, 9.4), (82.4, 6.2), (81.2, 4.4)],
    tail=[(0.5, 5.2), (0, 8.5), (0, 13.6)],
    rocker=4.2,
    greenhouse=[(10, 15.6), (17, 20.2), (24, 21.5), (33, 21.5), (36, 20.7), (46, 14.9)],
    glass=[[(25, 15.8), (26.5, 20.8), (33, 20.9), (35.5, 20.0), (43, 15.6)], [(17, 15.8), (20, 19.4), (24, 20.8), (24, 15.8)]],
    head=(79.8, 7.6, 82.4, 8.6), tail_lamp=(-0.2, 10.4, 0.8, 13.0),
    rim={"style": "turbine", "spokes": 12, "color": "silver"},
    bumpers="body", front_bumper=(77, 82.8, 4.4, 8.0), rear_bumper=(-0.4, 3.5, 5.2, 8.6),
    popup=(66, 71, 12.8, 14.6),
    extras=[{"kind": "gills", "rect": (49.5, 9.4, 54.5, 12.2), "slots": 4},
            {"kind": "skirt", "rect": (26, 4.2, 54, 5.6)}],
    rear_strip=True, dark_quarters=True,
)

ASP = make(  # '92 Viper RT/10 roadster: no roof, long curvy hood, side pipes, huge fender gills, roll bar
    "asp", 81, 6.9, 17, 61,
    belt=[(0, 14.0), (2, 15.0), (8, 15.8), (12, 16.2), (22, 15.2), (28, 14.8), (44, 14.8), (50, 15.4), (56, 15.2), (72, 13.0), (81, 10.8)],
    nose=[(81.4, 9.6), (81.3, 6.6), (80, 4.4)],
    tail=[(0.5, 5.4), (0, 8), (0, 13.4)],
    rocker=4.4,
    greenhouse=[(37.0, 20.2), (39.2, 20.2), (47.0, 14.8), (44.6, 14.8)],    # windshield frame only
    glass=[],
    head=(76, 10.4, 79.4, 11.8), tail_lamp=(-0.2, 10.4, 0.8, 12.6),
    rim={"style": "three", "spokes": 3, "color": "silver"},
    bumpers="body", front_bumper=(76, 81.8, 4.4, 8.0), rear_bumper=(-0.4, 3.5, 5.4, 8.6),
    extras=[{"kind": "sidepipe", "rect": (25, 5.6, 46, 7.2)},
            {"kind": "gills", "rect": (49, 8.6, 55, 13.0), "slots": 4},
            {"kind": "rollbar", "pts": [(24, 15.0), (25, 18.6), (27, 19.4), (30, 19.4), (31.5, 18.6), (32.5, 15.0)]},
            {"kind": "headrest", "rect": (33, 14.8, 36, 17.6)}],
    exhaust=[(25.0, 6.4)], mirror=False, chrome_surround=False,
)
# The windshield on the roadster is the only glass: let the frame's inner edge be the screen
ASP["windshield"] = [(38.0, Y(19.8)), (39.0, Y(19.8)), (46.0, Y(15.0)), (45.0, Y(15.0))]

ROSTER = [VANDAL, RANGER, CORSAIR, MAMMOTH, PIRANHA, WARRIOR,
          PHANTOM, RANGER50, CORSAIR_IROC, KESTREL, SPIRAL_TURBO, KESSLER, ZEPHYR,
          TSUNAMI, KAMINARI_MK1, KAMINARI_MK3, SPIRAL_R, KIRIN, TYPHOON, SQUALL, KITE, ARROW, VORTEX, ASP]
