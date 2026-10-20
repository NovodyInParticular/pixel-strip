"""The other five tracks, from one parametric builder.

Every track keeps Neon City's layer stack and sizes (so the renderer and the JSON layout carry
over) but swaps in its own sky, sun/moon, far scenery, mid-ground, stands, wall, lamps, road,
foreground and colour grade. Placement lives in assets/data/tracks/<id>.json.
"""
import math

import numpy as np

from common import P, bayer, canvas, dither_ramp, hline, poly_mask, put, rect, save, vline
from ui import draw_text, text_width
import track_neon as neon

RNG = np.random.default_rng(77)


def _r(a, b):
    return int(RNG.integers(a, b))


# --- Sky and celestial --------------------------------------------------------------------------

def sky(stops):
    w, h = 64, 210
    out = canvas(w, h)
    n = len(stops)
    for y in range(h):
        t = (y / (h - 1)) ** 1.3
        for x in range(w):
            out[y, x] = stops[dither_ramp(t, x, y, n)]
    return out


def sun(r, ramp, glow_ramp, clip_bottom=None):
    s = int(r * 2 + 16)
    out = canvas(s, s)
    glow = canvas(s, s)
    c = s / 2
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - c, y + 0.5 - c)
            if clip_bottom is not None and y > clip_bottom:
                continue
            if d <= r:
                t = (y + 0.5 - (c - r)) / (2 * r)
                col = ramp[min(len(ramp) - 1, int(t * len(ramp)))]
                if y % 5 == 0 and t > 0.55:
                    continue                          # retro sun stripes
                out[y, x] = col
                glow[y, x] = col
            elif d < r + 8 and (1 - (d - r) / 8) ** 2 > bayer(x, y) * 0.9:
                glow[y, x] = glow_ramp
    return out, glow


def clouds(body, lit, count=8, w=512, h=70):
    out = canvas(w, h)
    dens = np.zeros((h, w))
    for _ in range(count):
        cx, cy, rx, ry = _r(0, w), _r(10, 60), _r(40, 120), _r(3, 7)
        for ox in (-w, 0, w):
            for y in range(max(0, cy - ry - 2), min(h, cy + ry + 2)):
                for x in range(w):
                    dx = (x - cx - ox) / rx
                    dy = (y - cy) / ry
                    d = dx * dx + dy * dy
                    if d < 1:
                        dens[y, x] = max(dens[y, x], 1 - d)
    for y in range(h):
        for x in range(w):
            v = dens[y, x]
            if v <= 0.08 + bayer(x, y) * 0.12:
                continue
            below = dens[min(h - 1, y + 2), x]
            out[y, x] = lit if (below < v - 0.05 or v < 0.25) else body
    return out


# --- Far scenery --------------------------------------------------------------------------------

def far_mesas(base, rim, haze):
    w, h = 512, 110
    out = canvas(w, h)
    x = 0
    while x < w:
        bw, bh = _r(40, 120), _r(20, 70)
        top = h - bh
        for xx in range(x, min(w, x + bw)):
            edge = min(xx - x, x + bw - xx)
            slope = min(1.0, edge / 10)               # cliffs: steep sides, flat tops
            t0 = int(top + (1 - slope) * 18)
            for yy in range(t0, h):
                f = (yy - (h - 40)) / 40
                col = base if not (f > 0 and f > bayer(xx, yy) * 1.2) else haze
                if yy == t0 or (yy < t0 + 2 and edge > 6):
                    col = rim
                out[yy, xx] = col
        x += bw + _r(-10, 30)
    return out, []


def far_port(base, light):
    """Ships at anchor and gantry cranes across the harbour; red warning lights blink."""
    w, h = 512, 110
    out = canvas(w, h)
    blink = []
    hline(out, 0, w, h - 12, base)
    for k in range(3):                                  # cargo ships
        sx = 30 + k * 170
        rect(out, sx, h - 20, sx + 110, h - 10, base)
        rect(out, sx + 80, h - 34, sx + 100, h - 20, base)
        for c in range(6):
            rect(out, sx + 6 + c * 12, h - 27, sx + 16 + c * 12, h - 20, [P("red", 0), P("blue", 0), P("green", 0), P("orange", 0)][(c + k) % 4])
        for wx in range(sx + 82, sx + 98, 4):
            put(out, wx, h - 30, light)
    for k in range(4):                                  # gantry cranes
        cx = 60 + k * 128
        for leg in (cx, cx + 26):
            vline(out, leg, h - 70, h - 10, base)
            vline(out, leg + 1, h - 70, h - 10, base)
        for i in range(70):
            put(out, cx - 30 + i, h - 72, base)
            put(out, cx - 30 + i, h - 71, base)
        for i in range(0, 26, 3):
            put(out, cx + i, h - 40 - i % 6, base)
        blink.append((cx + 38, h - 73))
    for y in range(h - 10, h):
        for x in range(w):
            out[y, x] = P("night", 1) if (x + y) % 5 else P("blue", 0)
    return out, blink


def far_sea(cliff, sea, sparkle):
    """The sea to the horizon with sun sparkles, a headland and a lighthouse."""
    w, h = 512, 110
    out = canvas(w, h)
    for y in range(h - 44, h):
        for x in range(w):
            out[y, x] = sea[dither_ramp((y - (h - 44)) / 44, x, y, len(sea))]
            if y < h - 30 and (x * 7 + y * 13) % 23 == 0:
                out[y, x] = sparkle
    for x in range(0, 140):                             # headland on the left
        top = int(h - 44 - 30 * math.sin(min(1.0, x / 140) * math.pi / 2) * (1 - x / 170))
        for y in range(top, h - 40):
            out[y, x] = cliff
    rect(out, 96, h - 74, 102, h - 50, P("steel", 6))  # lighthouse
    for y in range(h - 70, h - 50, 6):
        hline(out, 96, 102, y, P("red", 2))
    rect(out, 95, h - 78, 103, h - 74, P("night", 1))
    return out, [(99, h - 77)]


def far_mountains(rock, snow, shade):
    w, h = 512, 110
    out = canvas(w, h)
    x = -40
    while x < w + 40:
        pw, ph = _r(80, 160), _r(50, 100)
        peak = x + pw // 2
        for xx in range(max(0, x), min(w, x + pw)):
            hgt = ph * (1 - abs(xx - peak) / (pw / 2))
            top = int(h - hgt)
            cap = max(4.0, hgt * 0.32)
            for yy in range(top, h):
                col = rock if xx < peak else shade
                depth = yy - top
                if depth < cap - 2 or (depth < cap + 2 and bayer(xx, yy) > (depth - cap + 2) / 4):
                    col = snow if xx < peak else P("blue", 4)           # snowcap: lit face, blue shadow face
                out[yy, xx] = col
        x += pw - _r(30, 60)
    return out, []


def far_stadium(base, light):
    """Old grandstand roofs and four tall light towers (their lamps glow)."""
    w, h = 512, 110
    out = canvas(w, h)
    for x in range(w):
        roof = h - 30 - int(6 * math.sin(x / 50.0))
        for y in range(roof, h):
            out[y, x] = base
    blink = []
    for k in range(4):
        tx = 60 + k * 128
        vline(out, tx, h - 90, h - 28, base)
        vline(out, tx + 1, h - 90, h - 28, base)
        rect(out, tx - 8, h - 98, tx + 10, h - 90, base)
        for ly in (h - 96, h - 93):
            for lx in range(tx - 6, tx + 9, 3):
                put(out, lx, ly, light)
    return out, blink


# --- Mid-ground -------------------------------------------------------------------------------

def mid_desert(wall_ramp, roof, sign):
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    x = 10
    names = ["GAS", "MOTEL", "EATS", "TIRES"]
    i = 0
    while x < w - 60:
        kind = i % 3
        if kind == 0:                                   # low adobe building with a sign
            bw, bh = _r(60, 100), _r(26, 40)
            rect(out, x, h - bh, x + bw, h, wall_ramp[1])
            hline(out, x, x + bw, h - bh, wall_ramp[2])
            for wx in range(x + 8, x + bw - 8, 14):
                rect(out, wx, h - bh + 10, wx + 6, h - bh + 18, P("night", 1))
            text = names[(i // 3) % len(names)]
            tw = text_width(text)
            sx, sy = x + (bw - tw) // 2, h - bh - 14
            rect(out, sx - 3, sy - 3, sx + tw + 3, sy + 9, P("night", 1))
            draw_text(out, text, sx, sy, sign)
            draw_text(glow, text, sx, sy, sign)
            vline(out, sx + tw // 2, sy + 9, h - bh, P("steel", 2))
        elif kind == 1:                                 # water tower
            vline(out, x + 6, h - 60, h, P("steel", 2))
            vline(out, x + 26, h - 60, h, P("steel", 2))
            rect(out, x, h - 80, x + 32, h - 60, roof)
            hline(out, x, x + 32, h - 80, P("steel", 4))
            bw = 40
        else:                                           # saguaro
            vline(out, x + 10, h - 50, h, P("green", 1))
            vline(out, x + 11, h - 50, h, P("green", 1))
            vline(out, x + 12, h - 50, h, P("green", 0))
            for side, (ay, ah) in ((-1, (h - 34, 14)), (1, (h - 40, 12))):
                ax = x + 11 + side * 5
                hline(out, min(ax, x + 11), max(ax, x + 11) + 1, ay, P("green", 1))
                vline(out, ax, ay - ah, ay + 1, P("green", 1))
            bw = 24
        x += (bw if kind else bw) + _r(14, 40)
        i += 1
    # power line along the top
    for x in range(w):
        put(out, x, h - 90 + int(4 * math.sin(x / 48.0 * math.pi)), P("night", 1))
    return out, glow


def mid_port():
    """Warehouses and stacked containers under sodium light."""
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    x = 0
    cols = [P("red", 1), P("blue", 1), P("green", 1), P("orange", 0), P("steel", 2)]
    while x < w:
        if _r(0, 2):
            bw, bh = _r(90, 150), _r(50, 80)
            rect(out, x, h - bh, x + bw, h, P("night", 2))
            for k in range(x, x + bw, 4):
                vline(out, k, h - bh, h, P("night", 1))
            for y in range(h - bh, h - bh + 10):        # sawtooth roof
                for k in range(x, x + bw):
                    if (k - x) % 20 > (y - (h - bh)) * 2:
                        out[y, k] = P("night", 1)
            dx = x + bw // 2 - 12
            rect(out, dx, h - 30, dx + 24, h, P("steel", 1))
            lx = x + 12
            rect(out, lx, h - bh + 14, lx + 6, h - bh + 17, P("orange", 2))
            rect(glow, lx - 2, h - bh + 13, lx + 8, h - bh + 19, P("orange", 1))
            x += bw + _r(4, 12)
        else:
            stacks = _r(2, 5)
            for s in range(stacks):
                for k in range(_r(2, 4)):
                    cx = x + k * 30
                    cy = h - 12 - s * 12
                    c = cols[_r(0, len(cols))]
                    rect(out, cx, cy - 11, cx + 29, cy, c)
                    for rib in range(cx + 2, cx + 28, 3):
                        vline(out, rib, cy - 10, cy - 1, P("night", 1) if c != P("steel", 2) else P("steel", 1))
            x += 100
    return out, glow


def mid_coast():
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    x = 0
    while x < w:
        if _r(0, 3) == 0:                              # beach house on stilts
            bw = _r(40, 60)
            rect(out, x, h - 50, x + bw, h - 20, [P("cyan", 1), P("magenta", 1), P("yellow", 0)][_r(0, 3)])
            for k in range(x, x + bw + 1, 6):
                vline(out, k, h - 20, h, P("night", 1))
            for i in range(bw // 2 + 4):
                hline(out, x - 4 + i, x + bw + 4 - i, h - 50 - i // 2, P("red", 0))
            rect(out, x + 8, h - 42, x + 16, h - 32, P("yellow", 1))
            rect(glow, x + 8, h - 42, x + 16, h - 32, P("orange", 1))
            x += bw + 20
        else:                                           # palm tree
            tx = x + 10
            hgt = _r(60, 100)
            for i in range(hgt):
                cx = tx + int(4 * math.sin(i / hgt * 2.0))
                put(out, cx, h - i, P("skin", 1))
                put(out, cx + 1, h - i, P("skin", 0))
            top = (tx + int(4 * math.sin(2.0)), h - hgt)
            for a in range(0, 360, 40):
                for r in range(1, 18):
                    ang = math.radians(a)
                    px = top[0] + math.cos(ang) * r
                    py = top[1] + math.sin(ang) * r * 0.5 + (r * r) / 40
                    put(out, px, py, P("green", 0) if r > 10 else P("green", 1))
            x += _r(30, 60)
    return out, glow


def mid_airfield():
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    x = 10
    num = 1
    while x < w - 80:
        if num % 3 != 0:                                # hangar with a curved roof and a big number
            bw = _r(110, 150)
            for xx in range(x, x + bw):
                t = (xx - x) / bw
                top = int(h - 50 - 18 * math.sin(t * math.pi))
                for y in range(top, h):
                    out[y, xx] = P("steel", 3) if (xx - x) % 6 else P("steel", 2)
                put(out, xx, top, P("steel", 6))
                put(out, xx, top + 1, P("steel", 6))
            rect(out, x + 20, h - 30, x + bw - 20, h, P("night", 1))
            draw_text(out, str(num), x + bw // 2 - 3, h - 46, P("orange", 1), scale=2)
            x += bw + 20
        else:                                           # control tower with a rotating beacon
            vline(out, x + 10, h - 90, h, P("steel", 3))
            rect(out, x + 6, h - 90, x + 18, h, P("steel", 3))
            rect(out, x, h - 104, x + 24, h - 90, P("night", 2))
            hline(out, x, x + 24, h - 97, P("cyan", 2))
            hline(glow, x, x + 24, h - 97, P("cyan", 1))
            rect(glow, x + 10, h - 108, x + 14, h - 104, P("green", 2))
            x += 50
        num += 1
    for k in range(0, w, 70):                           # snowy pines
        for i in range(24):
            hline(out, k - i // 2, k + i // 2 + 1, h - 30 + i, P("green", 0) if i % 6 else P("steel", 6))
    return out, glow


def mid_billboards():
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    ads = [("COLA-MAX", "red"), ("ACE PLUGS", "yellow"), ("SPEED OIL", "cyan"), ("BURGER 77", "orange")]
    for i, (text, ramp) in enumerate(ads):
        bx = 20 + i * 190
        bw = text_width(text) * 2 + 20
        for leg in (bx + 10, bx + bw - 12):
            vline(out, leg, h - 60, h, P("steel", 2))
        rect(out, bx, h - 100, bx + bw, h - 60, P(ramp, 1))
        rect(out, bx + 3, h - 97, bx + bw - 3, h - 63, P(ramp, 2))
        draw_text(out, text, bx + 10, h - 88, P("night", 1) if ramp == "yellow" else P("steel", 6), scale=2)
        hline(out, bx, bx + bw, h - 100, P("steel", 5))
        for lx in range(bx + 8, bx + bw - 4, 16):        # little lamps over each board
            put(out, lx, h - 102, P("yellow", 2))
            put(glow, lx, h - 102, P("yellow", 1))
            put(glow, lx, h - 101, P("yellow", 0))
    # scoreboard
    sx = 700
    rect(out, sx, h - 130, sx + 60, h - 70, P("night", 0))
    draw_text(out, "1977", sx + 8, h - 120, P("orange", 2))
    draw_text(glow, "1977", sx + 8, h - 120, P("orange", 2))
    vline(out, sx + 30, h - 70, h, P("steel", 2))
    return out, glow


# --- Stands, wall, poles, road, foreground ---------------------------------------------------------

def stands(roof, tier_a, tier_b, lights):
    W_, H_ = neon.STAND_W, neon.STAND_H
    out = canvas(W_, H_)
    glow = canvas(W_, H_)
    for y in range(0, 7):
        hline(out, 0, W_, y, roof if y else P("steel", 5))
    for x in range(8, W_, 24):
        if lights:
            rect(out, x, 5, x + 4, 7, P("yellow", 2))
            rect(glow, x - 1, 5, x + 5, 8, P("yellow", 1))
    for y in range(7, H_):
        hline(out, 0, W_, y, tier_a if (y // 6) % 2 else tier_b)
    for cx in range(0, W_, 96):
        rect(out, cx + 2, 0, cx + 6, H_, P("steel", 2))
    hline(out, 0, W_, H_ - 4, P("steel", 4))
    return out, glow


def wall(kind):
    W_, H_ = neon.WALL_W, neon.WALL_H
    out = canvas(W_, H_)
    if kind == "wood":
        for y in range(18, H_):
            for x in range(W_):
                out[y, x] = P("skin", 1) if (x // 4) % 2 else P("skin", 2)
                if y in (24, 36):
                    out[y, x] = P("skin", 0)
        for x in range(0, W_, 64):                      # hay bales
            rect(out, x + 20, 30, x + 44, H_, P("yellow", 0))
            for y in range(30, H_, 3):
                hline(out, x + 20, x + 44, y, P("orange", 2))
    elif kind == "containers":
        for x in range(0, W_, 48):
            c = [P("red", 1), P("blue", 1), P("green", 1), P("orange", 0)][(x // 48) % 4]
            rect(out, x, 8, x + 46, H_, c)
            for rib in range(x + 2, x + 46, 3):
                vline(out, rib, 9, H_, P("night", 1))
    elif kind == "guardrail":
        for x in range(0, W_, 32):
            vline(out, x, 26, H_, P("steel", 3))
            vline(out, x + 1, 26, H_, P("steel", 2))
        for y in (28, 29, 30, 34, 35):
            hline(out, 0, W_, y, P("steel", 5) if y in (28, 34) else P("steel", 3))
    elif kind == "snowbank":
        for x in range(W_):
            top = 22 + int(4 * math.sin(x / 20.0) + 2 * math.sin(x / 7.0))
            for y in range(top, H_):
                out[y, x] = P("steel", 6) if y < top + 3 else (P("steel", 5) if bayer(x, y) > 0.3 else P("blue", 4))
        for x in range(10, W_, 48):                     # orange marker poles
            for y in range(6, 30):
                put(out, x, y, P("orange", 1) if (y // 4) % 2 else P("steel", 6))
    else:   # painted retro wall
        for y in range(18, H_):
            for x in range(W_):
                out[y, x] = P("steel", 4) if y < 22 else P("steel", 3)
        for x in range(W_):
            for y in (28, 29, 33, 34):
                out[y, x] = P("orange", 1) if y < 30 else P("yellow", 0)
    return out, canvas(W_, H_)


def poles(kind, lamp):
    W_, H_ = neon.POLE_W, neon.POLE_H
    out = canvas(W_, H_)
    glow = canvas(W_, H_)
    px = 180
    if kind == "telephone":
        vline(out, px, 20, H_, P("skin", 0))
        vline(out, px + 1, 20, H_, P("skin", 1))
        hline(out, px - 10, px + 12, 30, P("skin", 0))
        return out, glow
    vline(out, px, 8, H_, P("steel", 2))
    vline(out, px + 1, 8, H_, P("steel", 1))
    for k in range(18):
        put(out, px + 1 + k, 8 - k // 6, P("steel", 2))
    hx, hy = px + 18, 4
    rect(out, hx - 3, hy, hx + 7, hy + 3, P("steel", 3))
    rect(out, hx - 2, hy + 3, hx + 6, hy + 4, lamp)
    rect(glow, hx - 3, hy + 2, hx + 7, hy + 5, lamp)
    return out, glow


def road(base_a, base_b, edge, wet=False, snow=False, patches=False):
    out = canvas(neon.ROAD_W, neon.ROAD_H)
    H_ = neon.ROAD_H
    for y in range(H_):
        for x in range(neon.ROAD_W):
            if y < 3 or y >= H_ - 7:
                col = edge
                if snow and (y < 3 or y > H_ - 5):
                    col = P("steel", 6) if bayer(x, y) > 0.3 else P("steel", 5)
            else:
                n = RNG.random()
                col = base_b if n > 0.82 else base_a
                if wet and n > 0.97:
                    col = P("blue", 1)
            out[y, x] = col
    hline(out, 0, neon.ROAD_W, 5, P("steel", 5))
    for x in range(neon.ROAD_W):
        if (x // 8) % 2 == 0 or not snow:
            put(out, x, 34, P("yellow", 0) if not snow else P("steel", 6))
            put(out, x, 35, P("yellow", 0) if not snow else P("steel", 6))
    hline(out, 0, neon.ROAD_W, H_ - 9, P("steel", 5))
    if patches:
        for _ in range(6):
            px, py = _r(0, neon.ROAD_W - 30), _r(8, H_ - 14)
            rect(out, px, py, px + _r(12, 30), py + _r(3, 6), base_b)
    if wet:   # puddles: dark, glossy patches the game also reflects lights in
        for _ in range(10):
            px, py = _r(0, neon.ROAD_W - 20), _r(8, H_ - 12)
            for yy in range(py, py + 3):
                for xx in range(px, px + _r(10, 24)):
                    if 0 <= xx < neon.ROAD_W:
                        out[yy, xx] = P("night", 0) if (xx + yy) % 4 else P("blue", 2)
    return out


def foreground(kind):
    w, h = 512, 34
    out = canvas(w, h)
    x = 0
    while x < w:
        if kind == "cacti":
            vline(out, x + 4, h - 16, h, P("green", 0))
            vline(out, x + 5, h - 16, h, P("green", 0))
            hline(out, x + 1, x + 4, h - 10, P("green", 0))
            vline(out, x + 1, h - 14, h - 10, P("green", 0))
            rect(out, x + 14, h - 5, x + 24, h, P("skin", 0))
        elif kind == "barrels":
            rect(out, x, h - 14, x + 9, h, P("blue", 0))
            hline(out, x, x + 9, h - 10, P("steel", 2))
            hline(out, x, x + 9, h - 4, P("steel", 2))
        elif kind == "palms":
            for i in range(8):
                hline(out, x + 8 - i, x + 9 + i, h - 2 - i, P("green", 0))
        elif kind == "snow":
            for i in range(8):
                hline(out, x + 2 - i, x + 14 + i, h - 8 + i, P("steel", 6) if i < 3 else P("steel", 5))
        else:   # tyre stacks
            for k in range(3):
                ty = h - 6 - k * 5
                rect(out, x, ty, x + 12, ty + 5, P("night", 0))
                hline(out, x + 1, x + 11, ty, P("steel", 1))
        x += _r(30, 90)
    return out


def gantry(text, tube, frame):
    w, h = 56, 120
    out = canvas(w, h)
    glow = canvas(w, h)
    for lx in (6, 46):
        rect(out, lx, 20, lx + 4, h, frame)
    rect(out, 0, 0, w, 24, P("night", 0))
    draw_text(out, text, (w - text_width(text)) // 2, 8, tube)
    draw_text(glow, text, (w - text_width(text)) // 2, 8, tube)
    rect(out, 12, 34, 44, 60, P("night", 0))
    draw_text(out, "ET", 22, 38, P("red", 3))
    draw_text(glow, "ET", 22, 38, P("red", 3))
    for k in range(4):
        rect(out, 15 + k * 7, 49, 20 + k * 7, 56, P("red", 1))
    return out, glow


# --- Colour grades --------------------------------------------------------------------------------

def grade(shadow, highlight, sat, lift):
    def g(c):
        luma = c @ np.array([0.299, 0.587, 0.114])
        c = c + (1 - luma) ** 2 * np.array(shadow)
        c = c + (luma ** 2) * np.array(highlight)
        c = luma + (c - luma) * sat
        c = lift + c * (1 - lift)
        return np.clip(c, 0, 1)
    return g


# --- Themes ------------------------------------------------------------------------------------

THEMES = {
    "desert": dict(
        mid_lift=40,
        sky=[P("blue", 2), P("blue", 3), P("cyan", 3), P("yellow", 1), P("yellow", 0), P("orange", 2), P("orange", 1)],
        sun=(19, [P("yellow", 2), P("yellow", 1), P("yellow", 0), P("orange", 2)], P("orange", 2)),
        clouds=(P("orange", 1), P("yellow", 1)),
        far=lambda: far_mesas(P("red", 1), P("orange", 1), P("orange", 0)),
        mid=lambda: mid_desert([P("skin", 1), P("skin", 2), P("skin", 3)], P("steel", 3), P("red", 3)),
        stands=(P("skin", 0), P("skin", 1), P("skin", 2), False),
        wall="wood", poles=("telephone", None),
        road=dict(base_a=P("night", 3), base_b=P("skin", 0), edge=P("yellow", 0)),
        fg="cacti", gantry=("FINISH", P("orange", 2), P("skin", 0)),
        grade=grade([0.03, 0.0, -0.02], [0.06, 0.02, -0.06], 1.12, 0.02)),
    "port": dict(
        mid_lift=40,
        sky=[P("night", 0), P("night", 1), P("night", 2), P("steel", 0), P("steel", 1), P("blue", 0)],
        sun=None,
        clouds=(P("steel", 1), P("steel", 2)),
        far=lambda: far_port(P("night", 1), P("yellow", 1)),
        mid=mid_port,
        stands=(P("steel", 1), P("night", 1), P("night", 2), True),
        wall="containers", poles=("lamp", P("orange", 2)),
        road=dict(base_a=P("night", 0), base_b=P("night", 1), edge=P("steel", 1), wet=True),
        fg="barrels", gantry=("FINISH", P("cyan", 3), P("steel", 2)),
        grade=grade([-0.02, 0.03, 0.05], [0.07, 0.02, -0.04], 1.05, 0.02)),
    "coast": dict(
        sky=[P("magenta", 0), P("magenta", 1), P("magenta", 2), P("magenta", 3), P("orange", 2), P("yellow", 0), P("yellow", 1)],
        sun=(22, [P("yellow", 2), P("yellow", 1), P("orange", 2), P("orange", 1), P("red", 3)], P("magenta", 3)),
        clouds=(P("magenta", 1), P("orange", 2)),
        far=lambda: far_sea(P("magenta", 0), [P("magenta", 1), P("blue", 1), P("night", 2)], P("yellow", 2)),
        mid=mid_coast,
        stands=(P("cyan", 0), P("cyan", 1), P("steel", 4), False),
        wall="guardrail", poles=("lamp", P("yellow", 1)),
        road=dict(base_a=P("night", 2), base_b=P("night", 3), edge=P("steel", 3)),
        fg="palms", gantry=("FINISH", P("magenta", 3), P("steel", 3)),
        grade=grade([0.04, -0.01, 0.04], [0.06, 0.0, 0.02], 1.1, 0.03)),
    "airfield": dict(
        mid_lift=40,
        sky=[P("blue", 2), P("blue", 3), P("blue", 4), P("steel", 5), P("steel", 6)],
        sun=None,
        clouds=(P("steel", 5), P("steel", 6)),
        far=lambda: far_mountains(P("steel", 3), P("steel", 6), P("steel", 2)),
        mid=mid_airfield,
        stands=(P("steel", 5), P("steel", 3), P("steel", 4), False),
        wall="snowbank", poles=("lamp", P("cyan", 3)),
        road=dict(base_a=P("steel", 2), base_b=P("steel", 3), edge=P("steel", 5), snow=True),
        fg="snow", gantry=("FINISH", P("cyan", 3), P("steel", 4)),
        grade=grade([-0.02, 0.01, 0.06], [0.0, 0.02, 0.05], 0.92, 0.04)),
    "speedway": dict(
        sky=[P("night", 2), P("night", 4), P("magenta", 0), P("magenta", 1), P("orange", 1), P("orange", 2)],
        sun=(14, [P("orange", 2), P("orange", 1), P("red", 3)], P("orange", 1)),
        clouds=(P("night", 4), P("magenta", 1)),
        far=lambda: far_stadium(P("night", 2), P("yellow", 2)),
        mid=mid_billboards,
        stands=(P("red", 0), P("night", 2), P("night", 3), True),
        wall="painted", poles=("lamp", P("yellow", 2)),
        road=dict(base_a=P("night", 2), base_b=P("steel", 1), edge=P("steel", 3), patches=True),
        fg="tyres", gantry=("FINISH", P("orange", 2), P("steel", 2)),
        grade=grade([0.03, 0.02, -0.01], [0.04, 0.02, -0.04], 0.85, 0.06)),
}


def build_theme(tid, t):
    base = f"tracks/{tid}"
    save(sky(t["sky"]), f"{base}/sky.png")
    if t["sun"]:
        r, ramp, gl = t["sun"]
        s, sg = sun(r, ramp, gl)
        save(s, f"{base}/sun.png")
        save(sg, f"{base}/sun_glow.png")
    save(clouds(*t["clouds"]), f"{base}/clouds.png")
    far, blink = t["far"]()
    save(far, f"{base}/far.png")
    from common import ASSETS
    with open(f"{ASSETS}/{base}/blinkers.txt", "w") as f:
        f.write("\n".join(f"{x} {y}" for x, y in blink) + "\n")
    mid, mg = t["mid"]()
    lift = t.get("mid_lift", 0)
    if lift:   # stand the scenery up behind the grandstand instead of below it
        mid = np.roll(mid, -lift, axis=0)
        mg = np.roll(mg, -lift, axis=0)
        mid[-lift:] = 0
        mg[-lift:] = 0
    save(mid, f"{base}/mid.png")
    save(mg, f"{base}/mid_glow.png")
    st, stg = stands(*t["stands"])
    save(st, f"{base}/stands.png")
    save(stg, f"{base}/stands_glow.png")
    save(neon.crowd(), f"{base}/crowd.png")
    wl, _ = wall(t["wall"])
    save(wl, f"{base}/wall.png")
    pl, plg = poles(*t["poles"])
    save(pl, f"{base}/poles.png")
    save(plg, f"{base}/poles_glow.png")
    save(road(**t["road"]), f"{base}/road.png")
    save(neon.rubber(), f"{base}/rubber.png")
    save(neon.start_line(), f"{base}/start_line.png")
    save(neon.finish_line(), f"{base}/finish_line.png")
    g, gg = gantry(*t["gantry"])
    save(g, f"{base}/finish_gantry.png")
    save(gg, f"{base}/finish_gantry_glow.png")
    save(foreground(t["fg"]), f"{base}/foreground.png")
    save(neon.lut(t["grade"]), f"{base}/lut.png")


def build():
    for tid, t in THEMES.items():
        build_theme(tid, t)
