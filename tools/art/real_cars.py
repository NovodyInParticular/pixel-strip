"""Car profiles authored in metres from the real cars' dimensions, then converted to roster.make().

Each car is drawn from its published length, wheelbase, overhangs, height and tyre size, with the
points where its shape turns (tail, deck, rear glass, roof, windshield, cowl, hood, nose) placed
where they sit on the real car. Lines marked smooth are curved through their points
(Catmull-Rom), so a body flows instead of looking faceted; corners are kept where a car has them.

Coordinates: x in metres from the rear bumper, h in metres above the ground.
"""
from roster import Y, make, wing

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
# Traced from the side photo. Boxy and upright: tall flat sides, a short square trunk with a high
# tail, the big adjustable wing, a long hood falling to slim lamps, a deep front bumper and splitter.
R34 = car_m(
    "kaminari_mk3", 4.60, 2.665, 1.046, 0.653,
    belt=[(0.13, 0.96), (0.50, 0.98), (0.95, 0.99), (1.35, 0.95), (2.00, 0.93), (2.90, 0.92), (3.30, 0.89),
          (3.70, 0.85), (4.05, 0.79), (4.35, 0.72), (4.52, 0.66)],
    nose=[(4.57, 0.62), (4.60, 0.50), (4.60, 0.30), (4.57, 0.16)],
    tail=[(0.10, 0.20), (0.02, 0.28), (0.00, 0.42), (0.01, 0.58), (0.08, 0.64), (0.09, 0.90), (0.13, 0.96)],
    rocker=0.18,
    roof=[(0.92, 0.99), (1.15, 1.12), (1.40, 1.23), (1.65, 1.28), (2.05, 1.29), (2.38, 1.27), (2.52, 1.24),
          (3.05, 0.92)],
    glass=[[(1.36, 0.94), (1.60, 1.21), (1.85, 1.24), (1.85, 0.93)],
           [(1.93, 0.93), (1.93, 1.24), (2.05, 1.26), (2.42, 1.24), (2.95, 0.93)]],
    head=(4.38, 0.60, 4.55, 0.73), tail_lamp=(-0.01, 0.66, 0.06, 0.88),
    rim={"style": "sixspoke", "spokes": 6, "color": "black"}, bumpers="body",
    front_bumper=(4.25, 4.61, 0.16, 0.56), rear_bumper=(-0.01, 0.45, 0.20, 0.60),
    wing_m=(0.04, 0.58, 0.97, 1.10), seams=[1.78, 3.06],
    extras=[{"kind": "intake", "rect": (4.30, 0.24, 4.52, 0.40)},
            {"kind": "fill", "rect": (1.85, 0.93, 1.93, 1.26), "color": ("night", 0)}],     # the black B-pillar
    exhaust=[(0.10, 0.22)], tail_style="round", tail_count=2, crease=0.3,
)

# 1994 Toyota Supra Turbo (MK4): 4,515 long, 2,550 wheelbase, 1,275 tall, 255/40R17.
# Traced from the side photo. Round and low-nosed: a long falling hood, a bubble cabin with the
# rear glass running down into a tall rounded tail, and the hoop wing standing over the deck.
SUPRA = car_m(
    "tsunami", 4.515, 2.55, 0.981, 0.636,
    belt=[(0.05, 0.88), (0.25, 0.92), (0.60, 0.92), (1.00, 0.90), (1.60, 0.87), (2.40, 0.85), (2.90, 0.83),
          (3.30, 0.80), (3.70, 0.74), (4.10, 0.66), (4.38, 0.58)],
    nose=[(4.48, 0.52), (4.515, 0.42), (4.50, 0.26), (4.45, 0.15)],
    tail=[(0.12, 0.15), (0.02, 0.28), (0.00, 0.50), (0.02, 0.70), (0.05, 0.88)],
    rocker=0.15,
    roof=[(0.62, 0.94), (1.00, 1.04), (1.35, 1.14), (1.70, 1.21), (2.05, 1.22), (2.30, 1.19), (2.42, 1.15),
          (2.95, 0.84)],
    glass=[[(1.30, 0.92), (1.40, 1.07), (1.58, 1.12), (1.58, 0.91)],
           [(1.66, 0.91), (1.66, 1.14), (2.05, 1.17), (2.30, 1.14), (2.82, 0.86)]],
    head=(4.30, 0.57, 4.50, 0.64), tail_lamp=(-0.01, 0.60, 0.04, 0.78),
    rim={"style": "fivestar", "spokes": 5, "color": "silver"}, bumpers="body",
    front_bumper=(4.20, 4.52, 0.15, 0.48), rear_bumper=(-0.01, 0.40, 0.15, 0.46),
    wing_m=(0.08, 0.60, 0.92, 1.10), seams=[1.55, 2.92],
    extras=[{"kind": "intake", "rect": (4.30, 0.20, 4.50, 0.30)},
            {"kind": "fill", "rect": (1.58, 0.91, 1.66, 1.14), "color": ("night", 0)}],
    exhaust=[(0.14, 0.18)], crease=0.26, dark_quarters=True,
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
# Traced from the side photo. Low and curvy: a long low nose, the hood dipping between humped front
# fenders, a bubble canopy that flows into the glass hatch, swollen rear hips, the wing on the tail.
FD = car_m(
    "spiral_r", 4.295, 2.425, 1.107, 0.636,
    belt=[(0.10, 0.80), (0.30, 0.82), (0.70, 0.83), (1.10, 0.82), (1.50, 0.80), (2.20, 0.77), (2.80, 0.74),
          (3.30, 0.71), (3.60, 0.66), (3.90, 0.60), (4.15, 0.53)],
    nose=[(4.24, 0.48), (4.295, 0.40), (4.29, 0.28), (4.24, 0.15)],
    tail=[(0.12, 0.17), (0.02, 0.28), (0.00, 0.44), (0.05, 0.56), (0.08, 0.72), (0.10, 0.80)],
    rocker=0.16,
    roof=[(0.55, 0.83), (0.95, 0.98), (1.30, 1.11), (1.55, 1.18), (1.90, 1.20), (2.20, 1.18), (2.35, 1.13),
          (2.95, 0.78)],
    glass=[[(1.45, 0.86), (1.55, 1.08), (1.85, 1.13), (2.20, 1.11), (2.82, 0.80), (1.70, 0.83)]],
    head=(4.05, 0.33, 4.28, 0.42), tail_lamp=(-0.01, 0.62, 0.06, 0.78),
    rim=SILVER5, bumpers="body", front_bumper=(3.95, 4.30, 0.15, 0.42), rear_bumper=(-0.01, 0.40, 0.17, 0.52),
    wing_m=(0.08, 0.55, 0.78, 0.88), seams=[1.48, 2.78],
    extras=[{"kind": "intake", "rect": (4.05, 0.20, 4.28, 0.30)},
            {"kind": "fill", "rect": (1.30, 0.84, 1.45, 1.10), "color": ("night", 0)}],
    popup=(3.55, 3.90, 0.62, 0.70), exhaust=[(0.10, 0.20)], tail_style="round", tail_count=2, crease=0.22, flare=0.5,
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

# 1996 Nissan 180SX Type X: 4,540 long, 2,475 wheelbase, 1,290 tall, 205/55R15 (here on 16s).
# Traced from the side photo. A long low wedge: pop-up lamps in a sharp nose, a flat beltline, the
# big glass hatch sloping all the way down to a short tail with a small lip spoiler.
S180 = car_m(
    "sylph_180", 4.54, 2.475, 1.073, 0.627,
    belt=[(0.12, 0.84), (0.40, 0.86), (0.90, 0.86), (1.50, 0.85), (2.20, 0.84), (2.80, 0.81), (3.30, 0.75),
          (3.70, 0.69), (4.10, 0.62), (4.40, 0.55)],
    nose=[(4.50, 0.50), (4.54, 0.40), (4.53, 0.25), (4.48, 0.14)],
    tail=[(0.12, 0.17), (0.02, 0.28), (0.00, 0.50), (0.05, 0.70), (0.12, 0.84)],
    rocker=0.17,
    roof=[(0.60, 0.88), (1.20, 1.05), (1.70, 1.18), (1.95, 1.21), (2.35, 1.21), (2.55, 1.17), (3.05, 0.84)],
    glass=[[(0.68, 0.87), (1.25, 1.03), (1.73, 1.16), (1.73, 0.86)],
           [(1.83, 0.86), (1.83, 1.17), (2.35, 1.18), (2.52, 1.14), (2.95, 0.86)]],
    head=(4.30, 0.48, 4.50, 0.53), tail_lamp=(-0.01, 0.60, 0.05, 0.76),
    rim={"style": "fivestar", "spokes": 5, "color": "silver"}, bumpers="body",
    front_bumper=(4.25, 4.55, 0.14, 0.44), rear_bumper=(-0.01, 0.40, 0.17, 0.50),
    seams=[1.73, 2.98], popup=(3.85, 4.30, 0.62, 0.68),
    extras=[{"kind": "lip", "polys": [[(0.10, 0.84), (0.55, 0.87), (0.55, 0.90), (0.15, 0.89)]]},
            {"kind": "fill", "rect": (1.73, 0.86, 1.83, 1.18), "color": ("night", 0)}],
    exhaust=[(0.05, 0.18)], crease=0.24, dark_quarters=True,
)

# 1997 Acura Integra Type R (DC2): 4,380 long, 2,570 wheelbase, 1,335 tall, 195/55R15.
# Traced from the side photo. A low, smooth three-door coupe: a long sloping hood over quad round
# lamps, a high rounded deck behind the hatch glass and the tall Type R wing; white ten-spokes.
DC2 = car_m(
    "arrow", 4.38, 2.57, 0.916, 0.596,
    belt=[(0.10, 0.94), (0.40, 0.96), (0.90, 0.95), (1.50, 0.91), (2.30, 0.88), (2.90, 0.85), (3.40, 0.79),
          (3.80, 0.72), (4.15, 0.62), (4.33, 0.54)],
    nose=[(4.37, 0.48), (4.38, 0.36), (4.36, 0.22), (4.30, 0.14)],
    tail=[(0.12, 0.17), (0.02, 0.28), (0.00, 0.48), (0.04, 0.64), (0.06, 0.82), (0.10, 0.94)],
    rocker=0.18,
    roof=[(0.55, 0.97), (1.00, 1.11), (1.45, 1.22), (1.85, 1.29), (2.30, 1.29), (2.55, 1.25), (3.05, 0.90)],
    glass=[[(1.10, 0.93), (1.62, 1.15), (1.70, 1.18), (1.70, 0.91)],
           [(1.80, 0.91), (1.80, 1.21), (2.30, 1.23), (2.52, 1.19), (2.95, 0.90)]],
    head=(4.15, 0.50, 4.36, 0.60), tail_lamp=(-0.01, 0.64, 0.06, 0.78),
    rim={"style": "sixspoke", "spokes": 10, "color": "white"}, bumpers="body",
    front_bumper=(4.15, 4.39, 0.14, 0.46), rear_bumper=(-0.01, 0.45, 0.17, 0.55),
    wing_m=(0.10, 0.55, 0.96, 1.10), seams=[1.70, 3.02],
    extras=[{"kind": "intake", "rect": (4.20, 0.20, 4.37, 0.27)},
            {"kind": "fill", "rect": (1.70, 0.91, 1.80, 1.22), "color": ("night", 0)}],
    exhaust=[(0.12, 0.18)], crease=0.24,
)

# --- Traced over the user's side photos (tools/art/photo_ref.py); the sprite's colours come from the photo too.
# Each profile's rear axle is where photo_ref puts it ("rear axle ... from the rear bumper").

def photo_car(id, length, wheelbase, rear_axle, tyre_d, **kw):
    kw.setdefault("rim", SILVER5)
    kw.setdefault("bumpers", "body")
    kw.setdefault("crease", 0.24)
    return car_m(id, length, wheelbase, rear_axle, tyre_d, **kw)


# 1990 Honda Civic hatch (EF): 3,965 long, 2,500 wheelbase, 175/70R13. Joe's car is this body.
EF = photo_car(
    "kite_ef", 3.965, 2.50, 0.690, 0.578,
    belt=[(0.17, 0.95), (0.40, 0.88), (1.40, 0.87), (2.60, 0.86), (2.76, 0.82), (3.26, 0.72), (3.65, 0.62), (3.76, 0.55)],
    nose=[(3.82, 0.50), (3.86, 0.42), (3.86, 0.33), (3.80, 0.27)],
    tail=[(0.10, 0.28), (0.00, 0.33), (0.00, 0.54), (0.04, 0.62), (0.09, 0.77), (0.17, 0.95)],
    rocker=0.25,
    roof=[(0.20, 0.97), (0.40, 1.18), (0.55, 1.25), (1.40, 1.25), (2.03, 1.24), (2.12, 1.21), (2.76, 0.82)],
    glass=[[(0.32, 0.87), (0.45, 1.18), (0.55, 1.23), (1.38, 1.23), (1.38, 0.87)],
           [(1.42, 0.87), (1.42, 1.23), (2.00, 1.23), (2.49, 0.87)]],
    head=(3.60, 0.52, 3.80, 0.60), tail_lamp=(0.06, 0.66, 0.24, 0.77),
    rim={"style": "split", "spokes": 8, "color": "black", "size": 0.72}, seams=[1.40, 2.70],
    exhaust=[(0.10, 0.25)],
    extras=[{"kind": "glass_text", "rect": (0.60, 0.98, 0.95, 1.01)}],      # Joe's "GROUND CONTROL" sticker
)

# 1994 Honda Civic hatch (EG): 4,070 long, 2,570 wheelbase, 185/60R14.
EG = photo_car(
    "kite", 4.07, 2.57, 0.711, 0.578,
    belt=[(0.15, 0.93), (0.60, 0.88), (1.50, 0.87), (2.60, 0.86), (2.88, 0.86), (3.30, 0.77), (3.65, 0.68), (3.95, 0.58)],
    nose=[(4.03, 0.54), (4.07, 0.42), (4.06, 0.28), (4.00, 0.21)],
    tail=[(0.10, 0.25), (0.00, 0.31), (0.00, 0.58), (0.05, 0.75), (0.09, 0.87), (0.15, 0.93)],
    rocker=0.23,
    roof=[(0.15, 0.95), (0.30, 1.10), (0.45, 1.27), (1.20, 1.29), (2.11, 1.32), (2.18, 1.25), (2.88, 0.86)],
    glass=[[(0.59, 0.95), (0.66, 1.22), (1.42, 1.25), (1.42, 0.87), (0.65, 0.87)],
           [(1.47, 0.87), (1.47, 1.25), (2.15, 1.24), (2.82, 0.88)],
           [(0.15, 0.95), (0.40, 1.20), (0.48, 1.22), (0.25, 0.95)]],
    head=(3.68, 0.54, 3.93, 0.64), tail_lamp=(0.05, 0.66, 0.28, 0.80), seams=[1.45, 2.85],
    exhaust=[(0.12, 0.24)],
)

# 1997 Honda Civic Type R (EK9): 4,180 long, 2,620 wheelbase, 195/55R15.
EK = photo_car(
    "kite_rx", 4.18, 2.62, 0.691, 0.596,
    belt=[(0.18, 1.01), (0.95, 0.93), (2.49, 0.91), (2.92, 0.93), (3.30, 0.86), (3.65, 0.79), (4.07, 0.66), (4.15, 0.60)],
    nose=[(4.18, 0.56), (4.19, 0.41), (4.15, 0.27), (4.10, 0.22)],
    tail=[(0.10, 0.24), (0.00, 0.33), (0.00, 0.63), (0.09, 0.93), (0.18, 1.01)],
    rocker=0.22,
    roof=[(0.18, 1.02), (0.40, 1.18), (0.68, 1.33), (1.40, 1.35), (2.11, 1.35), (2.26, 1.29), (2.92, 0.93)],
    glass=[[(0.72, 0.98), (0.85, 1.27), (1.49, 1.30), (1.49, 0.93), (0.80, 0.93)],
           [(1.53, 0.92), (1.53, 1.31), (2.15, 1.30), (2.88, 0.93)],
           [(0.18, 1.02), (0.62, 1.30), (0.70, 1.30), (0.30, 1.01)]],
    head=(3.76, 0.58, 4.09, 0.72), tail_lamp=(0.05, 0.68, 0.24, 0.98), seams=[1.52, 2.90],
    exhaust=[(0.12, 0.24)],
)

# 2002 Chevrolet Corvette Z06 (C5): 4,564 long, 2,655 wheelbase, 265/40ZR17 front, 295/35ZR18 rear.
C5 = photo_car(
    "vortex", 4.564, 2.655, 0.995, 0.663,
    belt=[(0.07, 0.95), (0.57, 0.97), (0.99, 0.97), (1.30, 0.94), (1.55, 0.91), (2.50, 0.88), (2.99, 0.87), (3.65, 0.83),
          (4.03, 0.73), (4.34, 0.62), (4.51, 0.47)],
    nose=[(4.55, 0.41), (4.56, 0.33), (4.55, 0.27), (4.51, 0.23)],
    tail=[(0.15, 0.22), (0.03, 0.27), (0.00, 0.54), (0.01, 0.81), (0.07, 0.95)],
    rocker=0.22,
    roof=[(0.99, 0.97), (1.25, 1.12), (1.49, 1.22), (1.92, 1.23), (2.11, 1.20), (2.99, 0.87)],
    glass=[[(1.57, 0.90), (1.60, 1.17), (1.75, 1.18), (2.25, 1.15), (2.90, 0.87)],
           [(0.99, 0.97), (1.45, 1.20), (1.52, 1.19), (1.22, 0.97)]],
    head=(4.30, 0.35, 4.53, 0.43), tail_lamp=(-0.01, 0.62, 0.03, 0.80), seams=[1.48, 2.85],
    rim={"style": "sixspoke", "spokes": 5, "color": "chrome"}, exhaust=[(0.12, 0.22), (0.30, 0.22)],
)

# 1994 Honda Civic del Sol VTEC: 3,995 long, 2,370 wheelbase, 195/60R14; photographed with the targa top off.
DELSOL = photo_car(
    "kite_sol", 3.995, 2.37, 0.742, 0.59,
    belt=[(0.18, 0.89), (0.72, 0.88), (1.33, 0.85), (2.49, 0.82), (3.26, 0.73), (3.65, 0.66), (3.88, 0.56)],
    nose=[(3.97, 0.47), (3.99, 0.35), (3.97, 0.25), (3.93, 0.20)],
    tail=[(0.08, 0.25), (0.00, 0.30), (0.00, 0.58), (0.11, 0.77), (0.18, 0.89)],
    rocker=0.22,
    roof=[(0.72, 0.88), (1.10, 1.10), (1.22, 1.18), (1.33, 1.19), (1.95, 1.17), (2.00, 1.16), (2.49, 0.82)],
    glass=[[(1.98, 1.13), (2.03, 1.14), (2.48, 0.84), (2.30, 0.84)]],
    head=(3.61, 0.50, 3.88, 0.62), tail_lamp=(0.05, 0.60, 0.30, 0.79), seams=[1.30, 2.55],
    wing_m=(0.18, 0.62, 0.88, 0.94), rim={"style": "fivestar", "spokes": 5, "color": "silver"}, exhaust=[(0.12, 0.24)],
)
DELSOL["cutouts"] = [[(x * M, Y(h * M)) for x, h in poly] for poly in
                     [[(1.34, 0.86), (1.34, 1.17), (1.93, 1.17), (1.97, 1.10), (2.20, 0.86)]]]      # the open targa

# 1987 Mazda RX-7 Turbo II (FC3S): 4,290 long, 2,430 wheelbase, 205/60R15.
FC = photo_car(
    "spiral_turbo", 4.29, 2.43, 1.019, 0.627,
    belt=[(0.15, 0.89), (0.50, 0.91), (1.00, 0.92), (1.45, 0.90), (2.60, 0.88), (2.84, 0.87), (3.45, 0.75), (4.03, 0.64),
          (4.22, 0.58)],
    nose=[(4.28, 0.50), (4.29, 0.43), (4.26, 0.27), (4.18, 0.22)],
    tail=[(0.08, 0.24), (0.00, 0.31), (0.00, 0.58), (0.11, 0.77), (0.15, 0.89)],
    rocker=0.22,
    roof=[(0.30, 0.93), (0.80, 1.06), (1.34, 1.22), (1.42, 1.23), (2.11, 1.22), (2.22, 1.18), (2.84, 0.87)],
    glass=[[(0.32, 0.93), (1.30, 1.21), (1.38, 1.21), (1.30, 0.96)],
           [(1.51, 0.88), (1.53, 1.17), (1.62, 1.20), (2.10, 1.20), (2.55, 0.87)]],
    head=(3.95, 0.40, 4.27, 0.48), tail_lamp=(0.06, 0.60, 0.22, 0.79), seams=[1.45, 2.80],
    popup=(3.55, 3.95, 0.64, 0.70), rim={"style": "turbine", "spokes": 5, "color": "silver"}, exhaust=[(0.10, 0.24)],
)

# 1991 Mitsubishi 3000GT VR-4: 4,580 long, 2,470 wheelbase, 245/45ZR17.
GT3000 = photo_car(
    "typhoon", 4.58, 2.47, 1.061, 0.654,
    belt=[(0.15, 0.91), (0.76, 0.95), (1.60, 0.92), (2.80, 0.91), (3.03, 0.95), (3.65, 0.87), (4.22, 0.75), (4.50, 0.66)],
    nose=[(4.57, 0.58), (4.59, 0.48), (4.57, 0.32), (4.50, 0.23)],
    tail=[(0.09, 0.33), (0.00, 0.39), (0.00, 0.62), (0.03, 0.70), (0.09, 0.87), (0.15, 0.91)],
    rocker=0.22,
    roof=[(0.80, 0.95), (1.20, 1.12), (1.57, 1.23), (1.95, 1.30), (2.26, 1.29), (2.42, 1.24), (3.03, 0.95)],
    glass=[[(0.80, 0.95), (1.55, 1.22), (1.62, 1.22), (1.62, 0.93)],
           [(1.70, 0.92), (1.72, 1.18), (1.95, 1.25), (2.28, 1.24), (2.95, 0.95)]],
    head=(4.40, 0.50, 4.58, 0.58), tail_lamp=(0.05, 0.73, 0.22, 0.85), seams=[1.66, 3.00],
    wing_m=(0.08, 0.75, 0.95, 1.08), rim={"style": "sixspoke", "spokes": 6, "color": "chrome"}, exhaust=[(0.10, 0.30)],
)

# 1999 Mitsubishi Lancer Evolution VI GSR: 4,350 long, 2,510 wheelbase, 225/45ZR17.
EVO6 = photo_car(
    "halberd_6", 4.35, 2.51, 0.966, 0.634,
    belt=[(0.05, 1.02), (0.68, 1.06), (1.00, 1.00), (1.50, 0.98), (2.70, 0.95), (3.11, 0.95), (3.65, 0.85), (4.11, 0.73),
          (4.28, 0.64)],
    nose=[(4.34, 0.54), (4.35, 0.45), (4.34, 0.35), (4.28, 0.23)],
    tail=[(0.15, 0.27), (0.02, 0.35), (0.00, 0.62), (0.05, 0.66), (0.05, 1.02)],
    rocker=0.22,
    roof=[(0.68, 1.08), (0.95, 1.22), (1.15, 1.33), (1.53, 1.39), (2.26, 1.38), (2.49, 1.31), (3.11, 0.95)],
    glass=[[(1.01, 0.98), (1.15, 1.22), (1.30, 1.30), (1.85, 1.31), (1.85, 0.98)],
           [(1.93, 0.97), (1.93, 1.31), (2.30, 1.30), (2.82, 0.95)]],
    head=(3.99, 0.60, 4.18, 0.72), tail_lamp=(0.03, 0.75, 0.17, 1.00), seams=[1.00, 1.90, 3.12],
    wing_m=(0.02, 0.62, 1.00, 1.20), rim={"style": "sixspoke", "spokes": 10, "color": "silver"}, flare=0.5,
    exhaust=[(0.12, 0.24)],
)

# 1991 Toyota MR2 Turbo (SW20): 4,170 long, 2,400 wheelbase, 205/60R14 front, 225/50R15 rear. Mid-engine.
SW20 = photo_car(
    "ronin", 4.17, 2.40, 0.932, 0.606,
    belt=[(0.13, 0.87), (0.50, 0.90), (0.95, 0.93), (1.40, 0.88), (2.50, 0.84), (2.88, 0.83), (3.45, 0.75), (3.88, 0.64),
          (4.11, 0.54)],
    nose=[(4.15, 0.47), (4.16, 0.40), (4.15, 0.33), (4.07, 0.25)],
    tail=[(0.11, 0.30), (0.01, 0.36), (0.01, 0.60), (0.13, 0.79), (0.13, 0.87)],
    rocker=0.22,
    roof=[(0.95, 0.95), (1.25, 1.06), (1.49, 1.14), (1.72, 1.18), (2.26, 1.16), (2.42, 1.12), (2.88, 0.83)],
    glass=[[(1.13, 0.89), (1.40, 1.10), (1.70, 1.15), (1.70, 0.88)],
           [(1.75, 0.88), (1.75, 1.15), (2.20, 1.14), (2.72, 0.84)]],
    head=(3.95, 0.45, 4.14, 0.52), tail_lamp=(0.08, 0.70, 0.28, 0.79), seams=[1.72, 2.90],
    popup=(3.40, 3.80, 0.70, 0.76), wing_m=(0.15, 0.65, 0.88, 0.95), rim={"style": "fivestar", "spokes": 5, "color": "silver"},
    exhaust=[(0.12, 0.28)],
)

# 1991 Acura NSX: 4,405 long, 2,530 wheelbase, 205/50ZR15 front, 225/50ZR16 rear. Traced over the photo (replaces
# the dimensions-only NSX above).
NSX = photo_car(
    "kirin", 4.405, 2.53, 1.007, 0.63,
    belt=[(0.13, 0.93), (0.80, 0.92), (1.20, 0.91), (1.80, 0.88), (2.70, 0.84), (3.07, 0.77), (3.65, 0.72), (4.03, 0.68),
          (4.22, 0.58)],
    nose=[(4.38, 0.47), (4.40, 0.37), (4.38, 0.27), (4.34, 0.22)],
    tail=[(0.09, 0.30), (0.01, 0.36), (0.01, 0.60), (0.11, 0.66), (0.13, 0.93)],
    rocker=0.18,
    roof=[(1.07, 0.91), (1.35, 0.99), (1.61, 1.06), (1.92, 1.12), (2.26, 1.12), (2.42, 1.08), (3.07, 0.77)],
    glass=[[(1.85, 0.83), (1.85, 1.10), (2.25, 1.10), (2.90, 0.80)],
           [(1.10, 0.91), (1.55, 1.05), (1.80, 1.10), (1.80, 0.84)]],
    head=(4.20, 0.43, 4.37, 0.49), tail_lamp=(0.00, 0.70, 0.10, 0.90), seams=[1.75, 3.05],
    popup=(3.70, 4.10, 0.70, 0.76), rim={"style": "fivestar", "spokes": 5, "color": "silver"}, exhaust=[(0.10, 0.25)],
)

# 1999 Honda Prelude Type SH (BB6): 4,520 long, 2,585 wheelbase, 205/50R16.
BB6 = photo_car(
    "overture", 4.52, 2.585, 1.021, 0.611,
    belt=[(0.18, 0.97), (0.60, 0.99), (1.07, 0.97), (1.60, 0.92), (2.70, 0.88), (2.99, 0.85), (3.65, 0.77), (4.22, 0.66),
          (4.42, 0.56)],
    nose=[(4.49, 0.48), (4.51, 0.39), (4.49, 0.30), (4.47, 0.25)],
    tail=[(0.05, 0.33), (0.00, 0.43), (0.03, 0.70), (0.13, 0.81), (0.18, 0.97)],
    rocker=0.22,
    roof=[(1.07, 0.97), (1.40, 1.10), (1.72, 1.22), (2.11, 1.23), (2.49, 1.16), (2.99, 0.85)],
    glass=[[(1.24, 0.93), (1.45, 1.12), (1.75, 1.19), (1.75, 0.92)],
           [(1.80, 0.91), (1.80, 1.20), (2.30, 1.19), (2.80, 0.87)]],
    head=(4.15, 0.54, 4.42, 0.66), tail_lamp=(0.12, 0.75, 0.28, 0.89), seams=[1.78, 3.00],
    rim={"style": "fivestar", "spokes": 5, "color": "silver"}, exhaust=[(0.12, 0.26)],
)

# 1989 Nissan Skyline GT-R (R32): 4,545 long, 2,615 wheelbase, 225/50R16.
R32 = photo_car(
    "kaminari_mk1", 4.545, 2.615, 0.977, 0.631,
    belt=[(0.10, 0.96), (0.60, 0.97), (1.10, 0.95), (2.00, 0.93), (3.00, 0.92), (3.50, 0.84), (4.10, 0.72), (4.45, 0.62)],
    nose=[(4.53, 0.55), (4.55, 0.45), (4.53, 0.25), (4.48, 0.20)],
    tail=[(0.12, 0.30), (0.01, 0.36), (0.01, 0.55), (0.10, 0.62), (0.10, 0.96)],
    rocker=0.22,
    roof=[(0.80, 0.97), (1.20, 1.15), (1.50, 1.28), (1.80, 1.31), (2.40, 1.29), (2.55, 1.25), (3.00, 0.92)],
    glass=[[(1.25, 0.95), (1.55, 1.23), (1.80, 1.25), (1.80, 0.95)],
           [(1.88, 0.95), (1.88, 1.26), (2.40, 1.25), (2.92, 0.93)]],
    head=(4.30, 0.60, 4.53, 0.68), tail_lamp=(0.00, 0.70, 0.08, 0.90), seams=[1.70, 3.00],
    wing_m=(0.08, 0.60, 0.96, 1.05), rim={"style": "fivestar", "spokes": 5, "color": "black"}, exhaust=[(0.10, 0.28)],
)

# 1995 Nissan Skyline GT-R (R33): 4,675 long, 2,720 wheelbase, 245/45ZR17.
R33 = photo_car(
    "kaminari_mk2", 4.675, 2.72, 0.983, 0.652,
    belt=[(0.10, 0.94), (0.70, 0.95), (1.25, 0.93), (2.00, 0.91), (3.10, 0.88), (3.60, 0.80), (4.20, 0.70), (4.55, 0.60)],
    nose=[(4.64, 0.52), (4.67, 0.42), (4.65, 0.25), (4.58, 0.18)],
    tail=[(0.10, 0.25), (0.01, 0.30), (0.01, 0.55), (0.10, 0.70), (0.10, 0.94)],
    rocker=0.20,
    roof=[(0.80, 0.95), (1.25, 1.10), (1.65, 1.24), (1.95, 1.28), (2.55, 1.26), (2.70, 1.20), (3.10, 0.88)],
    glass=[[(1.25, 0.92), (1.60, 1.20), (1.85, 1.23), (1.85, 0.91)],
           [(1.95, 0.90), (1.95, 1.24), (2.55, 1.22), (3.05, 0.89)]],
    head=(4.40, 0.58, 4.64, 0.66), tail_lamp=(0.00, 0.70, 0.08, 0.90), seams=[1.80, 3.15],
    wing_m=(0.08, 0.60, 0.94, 1.08), rim={"style": "sixspoke", "spokes": 6, "color": "silver"}, exhaust=[(0.10, 0.25)],
)

# 1972 Mazda RX-3 coupe: 4,075 long, 2,310 wheelbase, 165SR13 on steel wheels.
RX3 = photo_car(
    "prairie", 4.075, 2.31, 1.015, 0.59,
    belt=[(0.04, 0.80), (0.55, 0.92), (1.10, 0.95), (2.00, 0.91), (2.80, 0.88), (3.30, 0.84), (3.80, 0.80), (3.95, 0.75)],
    nose=[(4.00, 0.70), (4.02, 0.55), (4.00, 0.40), (3.95, 0.30)],
    tail=[(0.10, 0.25), (0.00, 0.40), (0.00, 0.62), (0.02, 0.74), (0.04, 0.80)],
    rocker=0.22,
    roof=[(0.55, 0.92), (0.90, 1.10), (1.30, 1.24), (1.60, 1.27), (2.30, 1.26), (2.50, 1.21), (2.95, 0.90)],
    glass=[[(1.25, 0.93), (1.35, 1.10), (1.70, 1.17), (1.70, 0.93)],
           [(1.78, 0.93), (1.78, 1.21), (2.40, 1.20), (2.80, 0.90)]],
    head=(3.80, 0.62, 4.01, 0.68), tail_lamp=(0.03, 0.66, 0.20, 0.72), seams=[1.70, 2.85],
    bumpers="chrome", front_bumper=(3.90, 4.04, 0.43, 0.50), rear_bumper=(-0.02, 0.20, 0.45, 0.52),
    rim={"style": "hubcap", "spokes": 4, "color": "silver"}, exhaust=[(0.30, 0.25)],
)

# 1991 Nissan Silvia K's (S13): 4,470 long, 2,475 wheelbase, 195/60R15.
S13 = photo_car(
    "sylph_13", 4.47, 2.475, 1.082, 0.615,
    belt=[(0.15, 0.83), (0.60, 0.87), (1.20, 0.89), (2.10, 0.87), (3.00, 0.86), (3.60, 0.76), (4.10, 0.66), (4.40, 0.56)],
    nose=[(4.46, 0.50), (4.47, 0.40), (4.45, 0.25), (4.40, 0.20)],
    tail=[(0.10, 0.25), (0.00, 0.32), (0.00, 0.55), (0.12, 0.62), (0.15, 0.83)],
    rocker=0.23,
    roof=[(0.70, 0.87), (1.10, 1.06), (1.50, 1.20), (1.80, 1.23), (2.30, 1.22), (2.55, 1.16), (3.00, 0.86)],
    glass=[[(1.20, 0.89), (1.55, 1.16), (1.70, 1.17), (1.70, 0.88)],
           [(1.80, 0.88), (1.80, 1.17), (2.30, 1.17), (2.90, 0.87)]],
    head=(4.20, 0.55, 4.45, 0.62), tail_lamp=(0.08, 0.60, 0.25, 0.72), seams=[1.75, 3.00],
    rim={"style": "phone", "spokes": 4, "color": "white"}, exhaust=[(0.15, 0.26)],
)

# 1999 Nissan Silvia Spec-R (S15): 4,445 long, 2,525 wheelbase, 205/55R16.
S15 = photo_car(
    "sylph_15", 4.445, 2.525, 1.028, 0.625,
    belt=[(0.10, 0.82), (0.60, 0.88), (1.20, 0.90), (2.10, 0.88), (2.90, 0.86), (3.50, 0.78), (4.00, 0.68), (4.35, 0.56)],
    nose=[(4.42, 0.48), (4.45, 0.40), (4.44, 0.22), (4.38, 0.16)],
    tail=[(0.10, 0.22), (0.00, 0.30), (0.00, 0.56), (0.05, 0.70), (0.10, 0.82)],
    rocker=0.17,
    roof=[(0.75, 0.90), (1.15, 1.07), (1.55, 1.21), (1.95, 1.24), (2.30, 1.22), (2.45, 1.18), (2.95, 0.86)],
    glass=[[(1.20, 0.92), (1.45, 1.10), (1.75, 1.17), (1.75, 0.90)],
           [(1.80, 0.89), (1.85, 1.17), (2.30, 1.17), (2.85, 0.87)]],
    head=(4.20, 0.55, 4.42, 0.62), tail_lamp=(0.06, 0.66, 0.22, 0.80), seams=[1.70, 2.95],
    rim={"style": "fivestar", "spokes": 5, "color": "silver"}, exhaust=[(0.12, 0.22)],
)

# 2000 Honda S2000 (AP1): 4,135 long, 2,400 wheelbase, 205/55R16 front, 225/50R16 rear; soft top up.
AP1 = photo_car(
    "tenshi", 4.135, 2.40, 0.922, 0.63,
    belt=[(0.10, 0.85), (0.60, 0.88), (1.40, 0.88), (2.40, 0.84), (2.90, 0.80), (3.40, 0.74), (3.90, 0.65), (4.08, 0.56)],
    nose=[(4.12, 0.48), (4.135, 0.40), (4.12, 0.28), (4.07, 0.22)],
    tail=[(0.10, 0.22), (0.00, 0.30), (0.00, 0.55), (0.05, 0.75), (0.10, 0.85)],
    rocker=0.22,
    roof=[(0.90, 0.92), (1.20, 1.10), (1.50, 1.20), (1.85, 1.22), (2.10, 1.18), (2.20, 1.12), (2.65, 0.85)],
    glass=[[(1.55, 0.92), (1.60, 1.15), (1.95, 1.15), (2.15, 1.05), (2.55, 0.86)]],
    head=(3.85, 0.55, 4.10, 0.62), tail_lamp=(0.05, 0.70, 0.30, 0.78), seams=[1.35, 2.60],
    rim={"style": "fivestar", "spokes": 5, "color": "silver"}, exhaust=[(0.12, 0.24)],
)

# 2004 Subaru Impreza WRX STI (GD, "blob eye"): 4,415 long, 2,525 wheelbase, 225/45R17.
GD = photo_car(
    "comet_gd", 4.415, 2.525, 0.943, 0.634,
    belt=[(0.15, 0.82), (0.70, 0.95), (1.20, 0.97), (2.10, 0.95), (3.00, 0.92), (3.60, 0.83), (4.10, 0.70), (4.35, 0.58)],
    nose=[(4.40, 0.50), (4.415, 0.42), (4.40, 0.25), (4.33, 0.18)],
    tail=[(0.10, 0.22), (0.00, 0.30), (0.00, 0.55), (0.08, 0.70), (0.15, 0.82)],
    rocker=0.20,
    roof=[(0.70, 0.95), (1.10, 1.18), (1.40, 1.28), (1.90, 1.32), (2.30, 1.30), (2.50, 1.23), (3.05, 0.92)],
    glass=[[(1.20, 0.97), (1.40, 1.20), (1.80, 1.25), (1.80, 0.96)],
           [(1.90, 0.96), (1.90, 1.25), (2.35, 1.23), (2.95, 0.93)]],
    head=(4.20, 0.52, 4.40, 0.63), tail_lamp=(0.05, 0.62, 0.30, 0.80), seams=[1.05, 1.85, 3.05],
    wing_m=(0.14, 0.62, 0.90, 1.14), rim={"style": "sixspoke", "spokes": 10, "color": "gold"}, exhaust=[(0.12, 0.22)],
    extras=[{"kind": "scoop", "rect": (3.40, 0.92, 3.85, 0.97)}],
)

REAL = {p["id"]: p for p in (R34, SUPRA, NSX, FD, VIPER, CHEVELLE, S180, DC2, EF, EG, EK, C5, DELSOL, FC, GT3000, EVO6, SW20, BB6, R32, R33, RX3, S13, S15, AP1, GD)}
