"""Layered side-view car sprites, built from a profile (see roster.py).

Profiles use car-local pixels: x from the rear bumper (0) to the front, y down with the ground
at y = GROUND. Output layers share one canvas (car + padding) so the game can stack and rotate them:

  body.png    paint layer, not colours: R = ramp index * 32 + 16 (0..7), G = shine mask
              (where the paint shop's sweep shows), A = coverage. A shader maps R through a paint ramp.
  body_n.png  normal map for the 2D lighting (x right, y up, z toward the viewer).
  popup.png   raised pop-up headlamp housings (paint layer like body.png), drawn with the lamps on.
  trim.png    true colours: glass, chrome, rubber, grille, vents, scoops, pipes, unlit lamps.
  lights.png  emissive pixels (head/tail lamps). Drawn lit at night and into the bloom buffer.
  wells.png   dark wheel-well interiors, drawn behind the wheels.
  wheel.png   strip of wheel frames: 4 rotation steps across one spoke period + 3 motion-blur frames.
  sprite.json anchors for the game (wheel centres, lamps, exhaust, ground row, nose).
"""
import json
import math
import os

import numpy as np
from PIL import Image

from common import ASSETS, P, TRANSPARENT, bayer, canvas, disc_mask, edges, poly_mask, put, save

PAD_X, PAD_Y = 4, 4
CH = 32 + PAD_Y * 2          # canvas height: 40
GROUND = 31.5                # ground row in car-local coordinates
ROT_FRAMES, BLUR_FRAMES = 4, 3


def width_of(p):
    return int(math.ceil(p["length"])) + PAD_X * 2


def L(pts):
    """Car-local points -> canvas points."""
    return [(x + PAD_X, y + PAD_Y) for x, y in pts]


def _interp_y(pts, x):
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        if x0 <= x <= x1:
            t = (x - x0) / max(1e-6, x1 - x0)
            return y0 + (y1 - y0) * t
    return pts[0][1] if x < pts[0][0] else pts[-1][1]


def _hand_aa(cov, solid):
    """Pixels with partial coverage next to the solid shape: filled with a darker step to soften stairs."""
    pad = np.pad(solid, 1)
    near = pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:]
    return (~solid) & near & (cov >= 0.3)


def _glass_union(p, cw):
    m = np.zeros((CH, cw), dtype=np.float32)
    for g in p["glass"] + [p["windshield"]] + ([p["rear_strip"]] if p.get("rear_strip") else []):
        m = np.maximum(m, poly_mask(cw, CH, L(g)))
    return m


def _paint_extras_mask(p, cw):
    m = np.zeros((CH, cw), dtype=np.float32)
    for e in p.get("extras", []):
        if e["kind"] in PAINTED:
            for poly in e["polys"]:
                m = np.maximum(m, poly_mask(cw, CH, L(poly)))
    return m


PAINTED = {"wing", "ducktail", "roof_spoiler", "lip"}


# --- Body (paint index layer) ----------------------------------------------------------------

def body_layer(p):
    cw = width_of(p)
    body = poly_mask(cw, CH, L(p["body"]))
    for cx, cy in (p["rear_wheel"], p["front_wheel"]):
        body = np.minimum(body, 1 - disc_mask(cw, CH, cx + PAD_X, cy + PAD_Y, p["arch_r"]))
    gh = poly_mask(cw, CH, L(p["greenhouse"]))
    glass = _glass_union(p, cw)
    gh = np.minimum(gh, 1 - (glass >= 0.5))
    extras = _paint_extras_mask(p, cw)

    solid_b = body >= 0.5
    solid_g = (gh >= 0.5) & ~solid_b
    solid_e = (extras >= 0.5) & ~solid_b & ~solid_g
    solid = solid_b | solid_g | solid_e
    idx = np.full((CH, cw), -1, dtype=np.int32)
    shine = np.zeros((CH, cw), dtype=np.uint8)

    yr = p["rocker_y"] + PAD_Y
    glass_x0 = min(x for g in p["glass"] for x, _ in g) if p["glass"] else 0
    glass_x1 = max(x for g in p["glass"] for x, _ in g) if p["glass"] else p["length"]
    roof_y = min(y for _, y in p["greenhouse"]) + 1.2
    front_end = p["length"]
    for y in range(CH):
        for x in range(cw):
            lx = x - PAD_X + 0.5
            ly = y - PAD_Y + 0.5
            if solid_b[y, x]:
                yb = _interp_y(p["beltline"], lx) + PAD_Y
                s = (y + 0.5 - yb) / max(1.0, yr - yb)
                hot = any(a <= lx <= b for a, b in p["hotspots"])
                if s < 0.07:
                    i = 7 if hot else 6
                    shine[y, x] = 255
                elif abs(s - p["crease_t"]) < 0.045:
                    i = 7 if hot else 6     # body character line catching the light
                    shine[y, x] = 255
                elif s < p["crease_t"]:
                    i = 5
                    shine[y, x] = 220
                elif s < 0.44:
                    i = 4 if s > 0.3 or bayer(x, y) > (s - p["crease_t"]) / 0.1 else 5
                    shine[y, x] = 160
                elif s < 0.58:
                    i = 4 if bayer(x, y) > (s - 0.44) / 0.14 else 3   # soft horizon line
                elif s < 0.8:
                    i = 3 if s < 0.7 else (3 if bayer(x, y) > (s - 0.7) / 0.1 else 2)
                else:
                    i = 2 if s < 0.9 else 1
                if lx < 2.5 or lx > front_end - 1.8:     # panels turning away at the ends
                    i = max(1, i - 2)
                elif lx < 4.5 or lx > front_end - 3.5:
                    i = max(1, i - 1)
                idx[y, x] = i
            elif solid_g[y, x]:
                if ly < roof_y:
                    i = 7 if (glass_x0 + 4) < lx < (glass_x0 + 9) else 6
                    shine[y, x] = 255
                elif lx < glass_x0:      # sail / C-pillar: lit at the top, falling off toward the deck
                    t = (ly - roof_y) / max(1.0, (_interp_y(p["beltline"], lx) - roof_y))
                    i = 5 if t < 0.35 else (4 if t < 0.7 else 3)
                    shine[y, x] = 160
                else:
                    i = 4 if lx > glass_x1 else 5
                idx[y, x] = i
            elif solid_e[y, x]:
                # painted extras (wings, lips): lit top edge, darker underside
                above = y > 0 and solid_e[y - 1, x]
                below = y < CH - 1 and solid_e[y + 1, x]
                i = 6 if not above else (2 if not below else 4)
                shine[y, x] = 200 if not above else 0
                idx[y, x] = i

    # Door seams: dark cut with a lit lip on the trailing side
    for sx in p["door_seams"]:
        x = int(sx + PAD_X)
        for y in range(CH):
            if 0 <= x < cw and solid_b[y, x]:
                lx = x - PAD_X
                yb = _interp_y(p["beltline"], lx) + PAD_Y
                if yb + 1.5 < y < yr - 1:
                    idx[y, x] = 1
                    if x + 1 < cw and solid_b[y, x + 1] and idx[y, x + 1] < 7:
                        idx[y, x + 1] = min(7, idx[y, x + 1] + 1)

    # Wheel-arch lips: highlight the upper rim of each arch, shadow the inside edge
    lip = 1.0 + p.get("flare", 0.0)
    for cx, cy in (p["rear_wheel"], p["front_wheel"]):
        cx += PAD_X
        cy += PAD_Y
        for y in range(CH):
            for x in range(cw):
                if not solid_b[y, x]:
                    continue
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if p["arch_r"] - 0.2 <= d < p["arch_r"] + lip and y + 0.5 < cy:
                    up = (cy - (y + 0.5)) / p["arch_r"]
                    idx[y, x] = 6 if up > 0.55 else 2
                    if p.get("flare", 0) > 0 and d > p["arch_r"] + 0.8 and up > 0.3:
                        idx[y, x] = 5          # flared fender: a second, softer band

    # Selective outline: dark along the bottom and ends, soft on top edges
    _, up, down, left, right = edges(solid.astype(np.float32))
    for y in range(CH):
        for x in range(cw):
            if not solid[y, x]:
                continue
            if down[y, x]:
                idx[y, x] = 0
            elif left[y, x] or right[y, x]:
                idx[y, x] = min(idx[y, x], 1 if not up[y, x] else 3)
            elif up[y, x] and idx[y, x] >= 7:
                idx[y, x] = 6 if (x % 7 == 0) else 7

    aa = _hand_aa(np.maximum(np.maximum(body, gh), extras), solid)
    for y in range(CH):
        for x in range(cw):
            if aa[y, x]:
                nb = [idx[yy, xx] for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                      if 0 <= yy < CH and 0 <= xx < cw and idx[yy, xx] >= 0]
                if nb and y < CH * 0.6:   # only soften the upper curves; the bottom stays crisp
                    idx[y, x] = max(0, max(nb) - 3)

    return _encode(idx, shine), idx


def _encode(idx, shine):
    out = canvas(idx.shape[1], CH)
    m = idx >= 0
    out[m, 0] = idx[m] * 32 + 16
    out[m, 1] = shine[m]
    out[m, 3] = 255
    return out


def popup_layer(p):
    """Raised pop-up lamp housings (paint index), or None for fixed lamps."""
    pu = p.get("popup")
    if not pu:
        return None, None
    cw = width_of(p)
    idx = np.full((CH, cw), -1, dtype=np.int32)
    x0, x1, base, top = pu
    for y in range(CH):
        for x in range(cw):
            lx, ly = x - PAD_X + 0.5, y - PAD_Y + 0.5
            if x0 <= lx <= x1 and top <= ly <= base:
                face = lx > x1 - 1.2
                idx[y, x] = 1 if face else (6 if ly < top + 1 else 4)
    return _encode(idx, np.zeros((CH, cw), dtype=np.uint8)), idx


# --- Normal map ----------------------------------------------------------------------------

def normal_layer(p, idx):
    cw = width_of(p)
    out = canvas(cw, CH)
    yr = p["rocker_y"] + PAD_Y
    gh = poly_mask(cw, CH, L(p["greenhouse"])) >= 0.5
    glass_x0 = min(x for g in p["glass"] for x, _ in g) if p["glass"] else 0
    roof_y = min(y for _, y in p["greenhouse"]) + 1.2
    belt_min = min(y for _, y in p["beltline"])
    for y in range(CH):
        for x in range(cw):
            if idx[y, x] < 0:
                continue
            lx = x - PAD_X + 0.5
            ly = y - PAD_Y + 0.5
            if gh[y, x] and ly < _interp_y(p["beltline"], lx) - 0.4:
                if ly < roof_y:
                    nx, ny = 0.0, 0.85                     # roof
                elif lx < glass_x0:
                    nx, ny = -0.35, 0.45                   # sail panel sloping back
                else:
                    nx, ny = 0.35, 0.3                     # A-pillar leaning forward
            elif ly < belt_min - 0.5:
                nx, ny = 0.0, 0.7                          # wings, ducktails: top surfaces
            else:
                yb = _interp_y(p["beltline"], lx) + PAD_Y
                s_ = (y + 0.5 - yb) / max(1.0, yr - yb)
                if s_ < 0.07:
                    ny = 0.8
                elif abs(s_ - p["crease_t"]) < 0.045:
                    ny = 0.55
                elif s_ < 0.44:
                    ny = 0.22
                elif s_ < 0.7:
                    ny = 0.0
                elif s_ < 0.88:
                    ny = -0.35
                else:
                    ny = -0.65
                end = p["length"]
                nx = -0.65 if lx < 3 else (-0.3 if lx < 5 else (0.65 if lx > end - 2 else (0.3 if lx > end - 4 else 0.0)))
            for cx, cy in (p["rear_wheel"], p["front_wheel"]):
                d = math.hypot(x + 0.5 - cx - PAD_X, y + 0.5 - cy - PAD_Y)
                if p["arch_r"] - 0.2 <= d < p["arch_r"] + 1.2:
                    nx = (x + 0.5 - cx - PAD_X) / d * 0.7
                    ny = -(y + 0.5 - cy - PAD_Y) / d * 0.7
            nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
            out[y, x] = (int((nx * 0.5 + 0.5) * 255), int((ny * 0.5 + 0.5) * 255), int((nz * 0.5 + 0.5) * 255), 255)
    return out


# --- Trim (true colours) --------------------------------------------------------------------

def _poly_pixels(cw, poly):
    m = poly_mask(cw, CH, L(poly)) >= 0.5
    return np.nonzero(m)


def _rect(p_out, x0, y0, x1, y1, col_fn):
    for y in range(int(round(y0 + PAD_Y)), int(round(y1 + PAD_Y))):
        for x in range(int(round(x0 + PAD_X)), int(round(x1 + PAD_X))):
            if 0 <= y < CH and 0 <= x < p_out.shape[1]:
                c = col_fn(x, y)
                if c is not None:
                    p_out[y, x] = c


def trim_layer(p, idx):
    cw = width_of(p)
    out = canvas(cw, CH)
    for k, g in enumerate(p["glass"]):
        _glass(out, cw, g, tint_dark=(k > 0 and p.get("dark_quarters", False)))
    _windshield(out, cw, p["windshield"])
    if p.get("rear_strip"):
        _windshield(out, cw, p["rear_strip"], dim=True)

    # Window surround on the main side glass
    if p["glass"] and p.get("chrome_surround", True):
        main = max(p["glass"], key=lambda g: max(x for x, _ in g) - min(x for x, _ in g))
        m = poly_mask(cw, CH, L(main)) >= 0.5
        ys, _ = np.nonzero(m)
        y0 = ys.min() if len(ys) else 0
        pad = np.pad(m, 1)
        ring = (~m) & (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:])
        surround = P("steel", 5) if p.get("bumpers") == "chrome" else P("night", 1)
        for y, x in zip(*np.nonzero(ring)):
            if idx[y, x] >= 0 and out[y, x, 3] == 0:
                out[y, x] = surround if y < y0 + 2 else (P("steel", 3) if p.get("bumpers") == "chrome" else P("night", 0))

    style = p.get("bumpers", "chrome")
    for key in ("front_bumper", "rear_bumper"):
        b = p.get(key)
        if not b:
            continue
        if style == "chrome":
            _chrome_bar(out, cw, b)
        elif style == "black":
            ys, xs = _poly_pixels(cw, b)
            top = ys.min() if len(ys) else 0
            for y, x in zip(ys, xs):
                out[y, x] = P("steel", 2) if y == top else (P("steel", 1) if (x + y) % 5 else P("night", 1))
        else:   # body-colour bumper: just a dark parting line on top
            ys, xs = _poly_pixels(cw, b)
            if len(ys):
                top = ys.min()
                for y, x in zip(ys, xs):
                    if y == top:
                        out[y, x] = P("night", 1)

    if p.get("grille"):
        gx0, gy0, gx1, gy1 = p["grille"]
        _rect(out, gx0, gy0, gx1, gy1, lambda x, y: P("steel", 2) if y % 2 == 0 else P("night", 0))

    tx0, ty0, tx1, ty1 = p["tail_lamp"]
    _rect(out, tx0, ty0, tx1 + 0.6, ty1, lambda x, y: P("red", 1) if y % 2 else P("red", 0))
    hx0, hy0, hx1, hy1 = p["head_lamp"]
    _rect(out, hx0, hy0, hx1, hy1, lambda x, y: P("steel", 4))

    for (mx, my), ramp in p["markers"]:
        put(out, mx + PAD_X, my + PAD_Y, P(ramp, 1))
        put(out, mx + PAD_X + 1, my + PAD_Y, P(ramp, 0))

    if p.get("handle"):
        hx, hy = p["handle"]
        for dx, dy, c in ((0, 0, P("steel", 5)), (1, 0, P("steel", 4)), (0, 1, P("night", 1)), (1, 1, P("night", 1))):
            put(out, hx + dx + PAD_X, hy + dy + PAD_Y, c)
    if p.get("mirror"):
        mx, my = p["mirror"]
        mc = [P("steel", 5), P("steel", 6), P("steel", 3), P("steel", 2), P("steel", 1)] if style == "chrome" else \
             [P("night", 2), P("night", 3), P("night", 1), P("night", 1), P("night", 0)]
        for (dx, dy), c in zip(((0, 0), (1, 0), (0, 1), (1, 1), (0, 2)), mc):
            put(out, mx + dx + PAD_X, my + dy + PAD_Y, c)
    if p.get("gas_cap"):
        gx, gy = p["gas_cap"]
        for dx, dy, c in ((0, 0, P("steel", 6)), (1, 0, P("steel", 4)), (0, 1, P("steel", 3)), (1, 1, P("steel", 2))):
            put(out, gx + dx + PAD_X, gy + dy + PAD_Y, c)

    # Rocker trim between the wheels
    rx0 = p["rear_wheel"][0] + p["arch_r"] + 1
    rx1 = p["front_wheel"][0] - p["arch_r"] - 1
    ry = int(p["rocker_y"] + PAD_Y) - 1
    for x in range(int(rx0 + PAD_X), int(rx1 + PAD_X)):
        if 0 <= ry < CH and idx[ry, x] >= 0:
            out[ry, x] = P("steel", 1)
            out[ry - 1, x] = P("steel", 3) if x % 2 else P("steel", 2)

    for ex, ey in p["exhaust_tips"]:
        put(out, ex + PAD_X, ey + PAD_Y, P("steel", 5))
        put(out, ex + PAD_X + 1, ey + PAD_Y, P("steel", 3))
        put(out, ex + PAD_X + 1, ey + PAD_Y + 1, P("night", 0))

    for e in p.get("extras", []):
        _trim_extra(out, cw, idx, e, p)
    return out


def _glass(out, cw, poly, tint_dark=False):
    ys, xs = _poly_pixels(cw, poly)
    if not len(ys):
        return
    y0, y1, xmin = ys.min(), ys.max(), xs.min()
    for y, x in zip(ys, xs):
        t = (y - y0) / max(1, y1 - y0)
        d = (x - xmin) - (y1 - y) * 1.3
        streak = 6 <= d < 9 or 12 <= d < 13
        if tint_dark:
            col = P("night", 1) if not streak else P("night", 3)
        elif streak:
            col = P("blue", 3) if t < 0.5 else P("blue", 2)
        elif t < 0.22:
            col = P("blue", 1)
        elif t < 0.6:
            col = P("night", 3) if bayer(x, y) > (t - 0.22) / 0.38 else P("night", 2)
        else:
            col = P("night", 1)
        out[y, x] = col


def _windshield(out, cw, poly, dim=False):
    ys, xs = _poly_pixels(cw, poly)
    for y, x in zip(ys, xs):
        if dim:
            out[y, x] = P("night", 2) if (x + y) % 4 else P("blue", 1)
        else:
            out[y, x] = P("blue", 2) if (x + y) % 5 else P("blue", 4)


def _chrome_bar(out, cw, poly):
    ys, xs = _poly_pixels(cw, poly)
    if not len(ys):
        return
    top, bot = ys.min(), ys.max()
    for y, x in zip(ys, xs):
        out[y, x] = P("steel", 6) if y == top else (P("steel", 2) if y == bot else P("steel", 4))
    put(out, xs.min() + 1 + (xs.max() - xs.min()) // 2, top, P("yellow", 2))


def _trim_extra(out, cw, idx, e, p):
    k = e["kind"]
    if k in PAINTED:
        return
    if k in ("vent", "gills", "intake"):
        x0, y0, x1, y1 = e["rect"]
        slots = e.get("slots", 3)
        for y in range(int(y0 + PAD_Y), int(y1 + PAD_Y)):
            for x in range(int(x0 + PAD_X), int(x1 + PAD_X)):
                if not (0 <= x < cw and 0 <= y < CH):
                    continue
                if k == "intake":
                    edge = y == int(y0 + PAD_Y)
                    out[y, x] = P("night", 2) if edge else (P("night", 0) if (x + y) % 3 else P("night", 1))
                else:
                    w = (x1 - x0) / slots
                    inside = ((x - x0 - PAD_X) % w) < w * 0.55
                    out[y, x] = P("night", 0) if inside else (P("steel", 2) if y == int(y0 + PAD_Y) else TRANSPARENT)
    elif k in ("scoop", "shaker"):
        x0, y0, x1, y1 = e["rect"]
        for y in range(int(y0 + PAD_Y), int(y1 + PAD_Y)):
            for x in range(int(x0 + PAD_X), int(x1 + PAD_X)):
                if 0 <= x < cw and 0 <= y < CH:
                    top = y == int(y0 + PAD_Y)
                    front = x == int(x1 + PAD_X) - 1
                    if k == "shaker":
                        out[y, x] = P("night", 0) if front else (P("steel", 2) if top else P("night", 2))
                    else:
                        out[y, x] = P("night", 0) if front else (P("night", 3) if top else P("night", 1))
    elif k == "sidepipe":
        x0, y0, x1, y1 = e["rect"]
        for y in range(int(y0 + PAD_Y), int(y1 + PAD_Y)):
            for x in range(int(x0 + PAD_X), int(x1 + PAD_X)):
                if 0 <= x < cw and 0 <= y < CH:
                    t = (y - y0 - PAD_Y) / max(1, (y1 - y0))
                    shield = (x // 2) % 3 == 0
                    out[y, x] = P("steel", 6) if t < 0.3 else (P("steel", 4) if t < 0.7 else P("steel", 2))
                    if shield and 0.2 < t < 0.8:
                        out[y, x] = P("steel", 3)
        put(out, x0 + PAD_X - 1, (y0 + y1) / 2 + PAD_Y, P("night", 0))
    elif k == "rollbar":
        for (ax, ay), (bx, by) in zip(e["pts"], e["pts"][1:]):
            n = int(max(abs(bx - ax), abs(by - ay)) * 2) + 1
            for i in range(n + 1):
                t = i / n
                put(out, ax + (bx - ax) * t + PAD_X, ay + (by - ay) * t + PAD_Y, P("steel", 2))
    elif k == "headrest":
        x0, y0, x1, y1 = e["rect"]
        _rect(out, x0, y0, x1, y1, lambda x, y: P("night", 1) if (x + y) % 4 else P("night", 2))
    elif k == "skirt":
        x0, y0, x1, y1 = e["rect"]
        _rect(out, x0, y0, x1, y1, lambda x, y: P("night", 1) if idx[y, x] >= 0 else None)
    elif k == "stripe":
        x0, y0, x1, y1 = e["rect"]
        c = P(*e.get("color", ("steel", 6)))
        _rect(out, x0, y0, x1, y1, lambda x, y: c if idx[y, x] >= 0 else None)


def lights_layer(p):
    cw = width_of(p)
    out = canvas(cw, CH)
    tx0, ty0, tx1, ty1 = p["tail_lamp"]
    if p.get("tail_style") == "round":
        n = p.get("tail_count", 2)
        h = (ty1 - ty0) / n
        for k in range(n):
            _rect(out, tx0, ty0 + k * h + 0.2, tx1 + 0.6, ty0 + (k + 1) * h - 0.3,
                  lambda x, y: P("red", 3) if (y % 2 == 0) else P("orange", 1))
    else:
        _rect(out, tx0, ty0, tx1 + 0.6, ty1, lambda x, y: P("red", 3) if (y % 2 == 0) else P("orange", 1))
    hx0, hy0, hx1, hy1 = p["head_lamp"]
    edge = int(round(hx1 + PAD_X)) - 1
    _rect(out, hx0, hy0, hx1, hy1, lambda x, y: P("yellow", 2) if x == edge else P("yellow", 1))
    pu = p.get("popup")
    if pu:
        x0, x1, base, top = pu
        _rect(out, x1 - 1.0, top + 0.5, x1 + 0.2, base, lambda x, y: P("yellow", 2))
    return out


def wells_layer(p):
    cw = width_of(p)
    out = canvas(cw, CH)
    body = poly_mask(cw, CH, L(p["body"]))
    top = min(y for _, y in p["body"]) + PAD_Y
    for cx, cy in (p["rear_wheel"], p["front_wheel"]):
        well = disc_mask(cw, CH, cx + PAD_X, cy + PAD_Y, p["arch_r"])
        m = (well >= 0.5) & (body >= 0.5)
        for y, x in zip(*np.nonzero(m)):
            if y > top:
                out[y, x] = P("night", 0) if (y + x) % 3 else P("night", 1)
    return out


# --- Wheels ----------------------------------------------------------------------------------

RIM_COLORS = {
    "chrome": [P("steel", 6), P("steel", 5), P("steel", 4), P("steel", 3)],
    "silver": [P("steel", 5), P("steel", 4), P("steel", 3), P("steel", 2)],
    "white": [P("steel", 6), P("steel", 6), P("steel", 5), P("steel", 4)],
    "gold": [P("yellow", 1), P("yellow", 0), P("orange", 2), P("orange", 1)],
    "black": [P("steel", 3), P("steel", 2), P("night", 2), P("night", 1)],
}


def _wheel_sample(px, py, angle, p):
    """Colour of one wheel at a point (frame-local coords around the centre), rotated by angle."""
    rim = p["rim"]
    r_tire = p["wheel_r"]
    r_rim = r_tire * rim.get("size", 0.66)
    cols = RIM_COLORS[rim.get("color", "chrome")]
    d = math.hypot(px, py)
    if d > r_tire:
        return None
    a = math.atan2(py, px) - angle
    lit = (-px - py) / (d + 1e-6)
    if d > r_rim + 0.35:
        # Tyre: cool dark rubber, sidewall lit from the top-left, tread edge darkest
        if d > r_tire - 0.75:
            return P("night", 0)
        if rim.get("letters") and abs(d - (r_tire + r_rim) / 2 - 0.2) < 0.45:
            seg = (a % (2 * math.pi)) / (2 * math.pi) * 24
            if int(seg) % 4 == 0 and math.sin(a) > 0.3:
                return P("steel", 4)
        if lit > 0.6:
            return P("steel", 1)
        if lit > -0.3:
            return P("steel", 0)
        return P("night", 1)
    if d > r_rim - 0.5:                                   # rim lip
        return cols[0] if py < -0.5 else (cols[2] if py < 1.5 else cols[3])
    style = rim["style"]
    n = rim.get("spokes", 5)
    if d < 1.1:
        return P("yellow", 2) if (px < 0 and py < 0) else cols[1]
    period = 2 * math.pi / n
    sa = (a % period) - period / 2                        # angle from the nearest spoke centre
    w = abs(sa) * d
    if style == "hubcap":                                 # steel wheel with a domed cap
        if d < r_rim * 0.75:
            return cols[0] if lit > 0.4 else (cols[1] if lit > -0.3 else cols[2])
        return P("night", 2) if int((a % (2 * math.pi)) / (2 * math.pi) * 10) % 2 else P("steel", 2)
    if style == "phone":                                  # disc with round holes
        hole = abs(sa) * (r_rim * 0.62) < 0.75 and abs(d - r_rim * 0.62) < 0.9
        if hole:
            return P("night", 1)
        return cols[0] if lit > 0.35 else (cols[1] if lit > -0.35 else cols[2])
    if style == "rally":                                  # painted disc with slots and a trim ring
        if d > r_rim - 1.2:
            return cols[0] if lit > 0 else cols[1]
        slot = w < 0.55 and r_rim * 0.35 < d < r_rim * 0.8
        if slot:
            return P("night", 1)
        return cols[2] if lit > 0 else cols[3]
    if style == "turbine":                                # many thin curved blades
        sa2 = ((a + d * 0.35) % period) - period / 2
        return cols[1] if abs(sa2) * d < 0.4 else (P("night", 1) if d > 2.2 else cols[3])
    if style == "mesh":                                   # crossing fine spokes
        a1 = ((a + d * 0.25) % period) - period / 2
        a2 = ((a - d * 0.25) % period) - period / 2
        on = abs(a1) * d < 0.32 or abs(a2) * d < 0.32
        return (cols[0] if lit > 0 else cols[2]) if on else P("night", 1)
    # spoked styles: magnum (wide), fivestar/sixspoke/sevenspoke, three
    width = {"magnum": 0.75, "three": 1.2}.get(style, 0.48)
    if w < width:
        return cols[0] if lit > 0.2 else (cols[1] if lit > -0.5 else cols[2])
    return P("night", 1) if d > 2.4 else cols[3]


def _mode(samples):
    cols = [s for s in samples if s is not None]
    if len(cols) < len(samples) * 0.5:
        return TRANSPARENT
    counts = {}
    for c in cols:
        counts[c] = counts.get(c, 0) + 1
    return max(counts, key=counts.get)


WHEEL_PALETTE = [P("steel", i) for i in range(7)] + [P("night", 0), P("night", 1), P("night", 2), P("yellow", 2),
                                                     P("yellow", 1), P("yellow", 0), P("orange", 2), P("orange", 1)]


def _avg_to_palette(samples):
    cols = [s for s in samples if s is not None]
    if len(cols) < len(samples) * 0.5:
        return TRANSPARENT
    avg = [sum(c[i] for c in cols) / len(cols) for i in range(3)]
    best, bd = None, 1e9
    for c in WHEEL_PALETTE:
        dd = sum((c[i] - avg[i]) ** 2 for i in range(3))
        if dd < bd:
            best, bd = c, dd
    return best


def wheel_size(p):
    s = int(math.ceil(p["wheel_r"] * 2)) + 4
    return s + (s % 2)


def wheel_frames(p):
    n = p["rim"].get("spokes", 5)
    period = 2 * math.pi / n
    size = wheel_size(p)
    frames = []
    sub = 4
    c = size / 2
    for f in range(ROT_FRAMES + BLUR_FRAMES):
        img = canvas(size, size)
        if f < ROT_FRAMES:
            angles = [period * f / ROT_FRAMES]
        else:
            spread = [period * 0.5, period, period * 2.5][f - ROT_FRAMES]
            angles = [spread * k / 12 for k in range(12)]
        for y in range(size):
            for x in range(size):
                samples = []
                for sy in range(sub):
                    for sx in range(sub):
                        px = x + (sx + 0.5) / sub - c
                        py = y + (sy + 0.5) / sub - c
                        for a in angles:
                            samples.append(_wheel_sample(px, py, a, p))
                img[y, x] = _mode(samples) if f < ROT_FRAMES else _avg_to_palette(samples)
        frames.append(img)
    return np.concatenate(frames, axis=1)


# --- Output ----------------------------------------------------------------------------------

def sprite_json(p):
    hx0, hy0, hx1, hy1 = p["head_lamp"]
    tx0, ty0, tx1, ty1 = p["tail_lamp"]
    ex, ey = p["exhaust"]
    return {
        "dir": f"cars/{p['id']}",
        "width": width_of(p),
        "height": CH,
        "rearWheel": [p["rear_wheel"][0] + PAD_X, p["rear_wheel"][1] + PAD_Y],
        "frontWheel": [p["front_wheel"][0] + PAD_X, p["front_wheel"][1] + PAD_Y],
        "wheelSize": wheel_size(p),
        "wheelFrames": ROT_FRAMES,
        "blurFrames": BLUR_FRAMES,
        "spokes": p["rim"].get("spokes", 5),
        "headlight": [hx1 + PAD_X, (hy0 + hy1) / 2 + PAD_Y],
        "taillight": [tx0 + PAD_X, (ty0 + ty1) / 2 + PAD_Y],
        "exhaust": [ex + PAD_X, ey + PAD_Y],
        "groundY": GROUND + PAD_Y,
        "frontX": p["length"] + PAD_X,
        "popup": bool(p.get("popup")),
    }


def build(p):
    body, idx = body_layer(p)
    popup, pidx = popup_layer(p)
    nidx = idx.copy()
    if pidx is not None:
        nidx[pidx >= 0] = pidx[pidx >= 0]
    trim = trim_layer(p, idx)
    lights = lights_layer(p)
    wells = wells_layer(p)
    wheels = wheel_frames(p)
    base = f"cars/{p['id']}"
    save(body, f"{base}/body.png")
    save(normal_layer(p, nidx), f"{base}/body_n.png")
    if popup is not None:
        save(popup, f"{base}/popup.png")
    save(trim, f"{base}/trim.png")
    save(lights, f"{base}/lights.png")
    save(wells, f"{base}/wells.png")
    save(wheels, f"{base}/wheel.png")
    with open(os.path.join(ASSETS, base, "sprite.json"), "w") as f:
        json.dump(sprite_json(p), f, indent=2)
    return body, idx, trim, lights, wells, wheels, popup, pidx


# --- Preview (for checking the art, not used by the game) ----------------------------------

def preview_image(p, layers, ramp, lit=True, bg=None):
    body, idx, trim, lights, wells, wheels, popup, pidx = layers
    cw = width_of(p)
    img = np.zeros((CH, cw, 4), dtype=np.uint8)
    img[:] = bg if bg is not None else P("night", 2)

    def blit(src):
        m = src[..., 3] > 0
        img[m] = src[m]

    blit(wells)
    ws = wheel_size(p)
    for (cx, cy) in (p["rear_wheel"], p["front_wheel"]):
        fx = int(math.floor(cx + PAD_X - ws / 2))
        fy = int(math.floor(cy + PAD_Y - ws / 2))
        frame = wheels[:, 0:ws]
        for y in range(ws):
            for x in range(ws):
                if frame[y, x, 3] and 0 <= fy + y < CH and 0 <= fx + x < cw:
                    img[fy + y, fx + x] = frame[y, x]
    colored = canvas(cw, CH)
    for src_idx in [idx] + ([pidx] if (pidx is not None and lit) else []):
        for y, x in zip(*np.nonzero(src_idx >= 0)):
            colored[y, x] = ramp[src_idx[y, x]]
    blit(colored)
    blit(trim)
    if lit:
        blit(lights)
    return img
