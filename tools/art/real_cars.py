"""Car profiles authored in metres from the real cars' dimensions, then converted to roster.make().

Each car is drawn from its published length, wheelbase, overhangs, height and tyre size, with the
points where its shape turns (tail, deck, rear glass, roof, windshield, cowl, hood, nose) placed
where they sit on the real car. Lines marked smooth are curved through their points
(Catmull-Rom), so a body flows instead of looking faceted; corners are kept where a car has them.

Coordinates: x in metres from the rear bumper, h in metres above the ground.
"""
from roster import make, wing

M = 18.2          # profile units per metre (roster.py's scale)


def _cr(pts, n=4):
    """Catmull-Rom through the points (ends kept): a smooth curve with n steps per span."""
    if len(pts) < 3:
        return list(pts)
    out = []
    ext = [pts[0]] + list(pts) + [pts[-1]]
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1], ext[i], ext[i + 1], ext[i + 2]
        for k in range(n):
            t = k / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in range(2)))
    out.append(pts[-1])
    return out


def _u(pts, smooth=False):
    pts = _cr(pts) if smooth else pts
    return [(x * M, h * M) for x, h in pts]


def _box(b):
    return tuple(v * M for v in b)


def _extra(e):
    e = dict(e)
    for k in ("polys",):
        if k in e:
            e[k] = [[(x * M, h * M) for x, h in poly] for poly in e[k]]
    if "pts" in e:
        e["pts"] = [(x * M, h * M) for x, h in e["pts"]]
    if "rect" in e:
        e["rect"] = _box(e["rect"])
    return e


def car_m(id, length, wheelbase, rear_axle, tyre_d, belt, nose, tail, rocker, roof, glass, head, tail_lamp, rim,
          smooth_belt=True, smooth_roof=True, wing_m=None, extras=(), front_bumper=None, rear_bumper=None, seams=None,
          exhaust=None, popup=None, grille=None, **kw):
    """A profile from metres. roof runs rear glass base -> roof -> windshield top, windshield base (the last two
    stay sharp). wing_m: (x0, x1, deck_h, blade_h) for a rear wing on posts."""
    rr = tyre_d / 2
    gh = _cr(roof[:-1]) + [roof[-1]] if smooth_roof else roof
    ex = [_extra(e) for e in extras]
    if wing_m:
        x0, x1, dh, bh = wing_m
        ex.append(wing(x0 * M, x1 * M, dh * M, bh * M, thick=0.06 * M, posts=[(x0 + 0.1) * M, (x1 - 0.18) * M]))
    return make(
        id, length * M, rr * M, rear_axle * M, (rear_axle + wheelbase) * M,
        belt=_u(belt, smooth_belt), nose=_u(nose, True), tail=_u(tail, True), rocker=rocker * M,
        greenhouse=[(x * M, h * M) for x, h in gh], glass=[_u(g) for g in glass],
        head=_box(head), tail_lamp=_box(tail_lamp), rim=rim, extras=ex,
        front_bumper=_box(front_bumper) if front_bumper else None, rear_bumper=_box(rear_bumper) if rear_bumper else None,
        seams=[s * M for s in seams] if seams else None, exhaust=[(x * M, h * M) for x, h in exhaust] if exhaust else None,
        popup=tuple(v * M for v in popup) if popup else None, grille=_box(grille) if grille else None,
        arch=rr * M * 1.12, **kw)


SILVER5 = {"style": "fivestar", "spokes": 5, "color": "silver"}

# 1999 Nissan Skyline GT-R V-Spec (R34): 4,600 long, 2,665 wheelbase, 1,360 tall, 245/40R18.
# Boxy and upright: tall flat sides, a short square trunk, a high tail with quad round lamps and a
# big adjustable wing; deep front bumper, a long flat hood, vents behind the front wheels.
R34 = car_m(
    "kaminari_mk3", 4.60, 2.665, 1.06, 0.653,
    belt=[(0.0, 0.98), (0.10, 1.01), (0.70, 1.02), (0.85, 1.01), (1.40, 0.95), (2.30, 0.92), (3.00, 0.90), (3.70, 0.86),
          (4.20, 0.81), (4.50, 0.74)],
    nose=[(4.56, 0.71), (4.60, 0.58), (4.60, 0.36), (4.55, 0.20)],
    tail=[(0.10, 0.20), (0.02, 0.32), (0.00, 0.60), (0.00, 0.85), (0.0, 0.98)],
    rocker=0.21,
    roof=[(0.86, 1.01), (1.05, 1.18), (1.30, 1.33), (1.60, 1.36), (2.20, 1.36), (2.36, 1.34), (3.02, 0.91)],
    glass=[[(1.32, 0.97), (1.42, 1.27), (1.66, 1.31), (1.66, 0.96)],
           [(1.76, 0.95), (1.76, 1.31), (2.22, 1.32), (2.34, 1.29), (2.86, 0.94)]],
    head=(4.36, 0.60, 4.58, 0.70), tail_lamp=(-0.01, 0.76, 0.04, 0.92),
    rim=SILVER5, bumpers="body", front_bumper=(4.20, 4.61, 0.20, 0.50), rear_bumper=(-0.01, 0.40, 0.21, 0.46),
    wing_m=(0.04, 0.66, 1.02, 1.18), seams=[1.72, 2.96],
    extras=[{"kind": "vent", "rect": (3.98, 0.55, 4.16, 0.66), "slots": 3},
            {"kind": "intake", "rect": (4.34, 0.27, 4.58, 0.36)}],
    exhaust=[(0.10, 0.22)], tail_style="round", tail_count=2, crease=0.3,
)

# 1994 Toyota Supra Turbo (MK4): 4,515 long, 2,550 wheelbase, 1,275 tall, 255/40R17.
# Round and low-nosed: a long falling hood, a bubble cabin, the rear glass curving down into a tall
# tail, and the hoop wing standing over the deck.
SUPRA = car_m(
    "tsunami", 4.515, 2.55, 0.96, 0.636,
    belt=[(0.0, 0.94), (0.15, 0.99), (0.55, 1.00), (1.10, 0.93), (1.90, 0.88), (2.70, 0.86), (3.30, 0.80), (3.90, 0.70),
          (4.30, 0.62), (4.47, 0.56)],
    nose=[(4.51, 0.50), (4.515, 0.40), (4.48, 0.26), (4.40, 0.18)],
    tail=[(0.12, 0.20), (0.03, 0.34), (0.00, 0.60), (0.0, 0.94)],
    rocker=0.20,
    roof=[(0.58, 0.99), (0.95, 1.16), (1.35, 1.26), (1.75, 1.275), (2.15, 1.26), (2.30, 1.23), (2.95, 0.85)],
    glass=[[(1.45, 0.92), (1.52, 1.18), (1.85, 1.23), (2.20, 1.22), (2.85, 0.88), (1.90, 0.89)]],
    head=(4.24, 0.55, 4.47, 0.63), tail_lamp=(-0.01, 0.74, 0.04, 0.88),
    rim=SILVER5, bumpers="body", front_bumper=(4.20, 4.52, 0.18, 0.44), rear_bumper=(-0.01, 0.42, 0.20, 0.46),
    wing_m=(0.02, 0.92, 0.99, 1.20), seams=[1.50, 2.86],
    extras=[{"kind": "intake", "rect": (4.30, 0.24, 4.50, 0.33)}],
    exhaust=[(0.14, 0.24)], crease=0.26, dark_quarters=True,
)

# 1991 Acura NSX: 4,405 long, 2,530 wheelbase, 1,170 tall, 225/50R16 rear.
# Mid-engine: the canopy sits far forward, a long flat engine deck runs back to a full-width tail
# with its integrated spoiler; low pointed nose with pop-up lamps; the big intake behind the door.
NSX = car_m(
    "kirin", 4.405, 2.53, 1.00, 0.63,
    belt=[(0.0, 0.92), (0.05, 0.98), (0.55, 0.97), (1.20, 0.93), (1.70, 0.86), (2.40, 0.80), (3.00, 0.74), (3.60, 0.66),
          (4.10, 0.58), (4.36, 0.52)],
    nose=[(4.40, 0.47), (4.405, 0.36), (4.37, 0.24), (4.30, 0.17)],
    tail=[(0.10, 0.20), (0.02, 0.34), (0.00, 0.62), (0.0, 0.92)],
    rocker=0.19,
    roof=[(1.20, 0.94), (1.45, 1.10), (1.75, 1.17), (2.20, 1.17), (2.38, 1.14), (3.00, 0.76)],
    glass=[[(1.62, 0.88), (1.70, 1.10), (2.18, 1.13), (2.30, 1.10), (2.86, 0.79), (1.80, 0.86)]],
    head=(4.15, 0.53, 4.38, 0.58), tail_lamp=(-0.01, 0.78, 0.03, 0.90),
    rim={"style": "sixspoke", "spokes": 7, "color": "silver"}, bumpers="body",
    front_bumper=(4.10, 4.41, 0.17, 0.40), rear_bumper=(-0.01, 0.40, 0.19, 0.44), seams=[1.62, 2.82],
    extras=[{"kind": "intake", "rect": (1.30, 0.48, 1.58, 0.68)},
            {"kind": "fill", "rect": (1.15, 0.86, 1.62, 0.93), "color": ("night", 0)}],     # the black sail behind the glass
    popup=(3.75, 4.15, 0.60, 0.68), exhaust=[(0.08, 0.24)], crease=0.24,
)

# 2002 Mazda RX-7 Spirit R (FD3S): 4,295 long, 2,425 wheelbase, 1,230 tall, 255/40R17.
# Low and curvy: the hood dips between humped front fenders, a double-bubble roof flows into the glass
# hatch, the hips swell over the rear wheels, round tails under a wing.
FD = car_m(
    "spiral_r", 4.295, 2.425, 0.92, 0.636,
    belt=[(0.0, 0.90), (0.10, 0.96), (0.50, 0.98), (0.90, 0.95), (1.30, 0.86), (2.00, 0.80), (2.70, 0.78), (3.25, 0.76),
          (3.55, 0.72), (3.95, 0.62), (4.24, 0.52)],
    nose=[(4.285, 0.47), (4.295, 0.36), (4.26, 0.24), (4.18, 0.17)],
    tail=[(0.10, 0.20), (0.02, 0.34), (0.00, 0.62), (0.0, 0.90)],
    rocker=0.19,
    roof=[(0.52, 0.98), (0.90, 1.12), (1.40, 1.22), (1.85, 1.23), (2.20, 1.21), (2.32, 1.18), (2.92, 0.79)],
    glass=[[(1.50, 0.86), (1.58, 1.13), (1.90, 1.18), (2.22, 1.16), (2.80, 0.81), (1.70, 0.84)]],
    head=(3.95, 0.55, 4.20, 0.60), tail_lamp=(-0.01, 0.72, 0.03, 0.86),
    rim=SILVER5, bumpers="body", front_bumper=(3.95, 4.30, 0.17, 0.40), rear_bumper=(-0.01, 0.40, 0.19, 0.44),
    wing_m=(0.04, 0.70, 0.98, 1.10), seams=[1.48, 2.78],
    extras=[{"kind": "intake", "rect": (4.05, 0.22, 4.28, 0.31)}],
    popup=(3.60, 3.95, 0.66, 0.74), exhaust=[(0.10, 0.24)], tail_style="round", tail_count=2, crease=0.22, flare=0.5,
)

# 1996 Dodge Viper GTS: 4,488 long, 2,443 wheelbase, 1,194 tall, 335/30ZR18 rear.
# An enormous hood with gills behind the front wheels, the cabin pushed right back, a double-bubble
# roof falling into a short tail.
VIPER = car_m(
    "asp", 4.488, 2.443, 0.96, 0.66,
    belt=[(0.0, 0.86), (0.10, 0.92), (0.45, 0.94), (0.90, 0.90), (1.50, 0.84), (2.00, 0.83), (2.60, 0.82), (3.30, 0.78),
          (3.90, 0.68), (4.30, 0.56), (4.45, 0.50)],
    nose=[(4.48, 0.45), (4.488, 0.34), (4.45, 0.22), (4.36, 0.16)],
    tail=[(0.10, 0.19), (0.02, 0.32), (0.00, 0.58), (0.0, 0.86)],
    rocker=0.18,
    roof=[(0.55, 0.93), (0.95, 1.10), (1.30, 1.19), (1.70, 1.19), (1.82, 1.16), (2.50, 0.84)],
    glass=[[(1.15, 0.86), (1.22, 1.10), (1.66, 1.13), (1.78, 1.10), (2.38, 0.86)]],
    head=(4.20, 0.52, 4.46, 0.60), tail_lamp=(-0.01, 0.70, 0.03, 0.82),
    rim={"style": "sixspoke", "spokes": 6, "color": "silver"}, bumpers="body",
    front_bumper=(4.10, 4.49, 0.16, 0.40), rear_bumper=(-0.01, 0.38, 0.18, 0.42), seams=[1.12, 2.40],
    extras=[{"kind": "gills", "rect": (3.25, 0.48, 3.62, 0.66), "slots": 4}],
    exhaust=[(0.10, 0.22)], crease=0.24,
)

# 1970 Chevrolet Chevelle SS 454: 5,170 long, 2,845 wheelbase, 1,350 tall, F70-14 (~0.67 m).
# Long and formal: a flat hood with the cowl-induction bulge, a squared roof with a steep rear window,
# a long flat trunk, chrome bumpers at both ends, a straight beltline the full length.
CHEVELLE = car_m(
    "mammoth", 5.17, 2.845, 1.23, 0.67,
    belt=[(0.0, 0.92), (0.06, 0.97), (1.05, 0.98), (1.30, 0.97), (2.40, 0.95), (3.20, 0.93), (4.20, 0.92), (4.85, 0.89),
          (5.10, 0.84)],
    nose=[(5.15, 0.80), (5.17, 0.62), (5.17, 0.40), (5.12, 0.26)],
    tail=[(0.06, 0.26), (0.00, 0.38), (0.00, 0.92)],
    rocker=0.26,
    roof=[(1.35, 0.97), (1.75, 1.30), (1.95, 1.35), (2.60, 1.35), (2.75, 1.32), (3.30, 0.94)],
    glass=[[(1.78, 0.98), (1.86, 1.27), (2.08, 1.30), (2.08, 0.97)],
           [(2.16, 0.97), (2.16, 1.30), (2.58, 1.31), (2.70, 1.28), (3.18, 0.96)]],
    head=(5.06, 0.62, 5.17, 0.76), tail_lamp=(-0.01, 0.40, 0.03, 0.50),
    rim={"style": "rally", "spokes": 6, "color": "silver", "letters": True}, bumpers="chrome",
    front_bumper=(4.95, 5.20, 0.30, 0.42), rear_bumper=(-0.03, 0.30, 0.30, 0.42), seams=[2.12, 3.25],
    extras=[{"kind": "scoop", "rect": (3.60, 0.93, 4.20, 0.98)}],
    smooth_belt=False, exhaust=[(0.10, 0.28), (0.35, 0.28)], crease=0.18,
    grille=(5.13, 0.48, 5.18, 0.76),
)

REAL = {p["id"]: p for p in (R34, SUPRA, NSX, FD, VIPER, CHEVELLE)}
