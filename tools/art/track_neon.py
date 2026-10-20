"""Neon City at night: every parallax layer, its emissive (bloom) layer, props and the colour grade.

Layers tile horizontally. Their placement, parallax factors and draw order live in
assets/data/tracks/neon_city.json; the sizes here must match it.
"""
import math

import numpy as np
from PIL import Image

from common import P, bayer, canvas, dither_ramp, put, rect, hline, vline, save, ASSETS
from ui import draw_text, text_width

ID = "neon_city"
RNG = np.random.default_rng(1969)


def _r(a, b):
    return int(RNG.integers(a, b))


# --- Sky ----------------------------------------------------------------------------------

def sky():
    w, h = 64, 210
    out = canvas(w, h)
    stops = [P("night", 0), P("night", 1), P("night", 2), P("night", 3), P("night", 4),
             P("night", 5), P("magenta", 0), P("magenta", 1), P("magenta", 2), P("red", 3), P("orange", 2)]
    n = len(stops)
    for y in range(h):
        t = (y / (h - 1)) ** 1.7
        for x in range(w):
            out[y, x] = stops[dither_ramp(t, x, y, n)]
    return out


def moon():
    s = 44
    out = canvas(s, s)
    glow = canvas(s, s)
    c, r = s / 2, 13.5
    craters = [(-4, -3, 3.2), (5, 4, 2.4), (2, -7, 1.6), (-6, 6, 1.8), (7, -2, 1.2)]
    for y in range(s):
        for x in range(s):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy)
            if d <= r:
                lit = (dx * 0.8 - dy * 0.35) / r          # lit from the right, slightly above
                if lit > 0.45:
                    col = P("yellow", 2)
                elif lit > 0.0:
                    col = P("yellow", 1) if bayer(x, y) > (0.45 - lit) / 0.45 * 0.6 else P("steel", 6)
                elif lit > -0.45:
                    col = P("steel", 5)
                else:
                    col = P("magenta", 4) if bayer(x, y) > 0.5 else P("steel", 4)
                for cx, cy, cr in craters:
                    if math.hypot(dx - cx, dy - cy) < cr:
                        col = P("steel", 4) if lit > 0 else P("steel", 3)
                        if math.hypot(dx - cx + 0.7, dy - cy - 0.7) > cr - 0.6 and dx - cx > 0:
                            col = P("yellow", 2) if lit > 0 else P("steel", 5)   # lit crater rim
                if d > r - 1:
                    col = P("steel", 4) if lit < 0 else col
                out[y, x] = col
                glow[y, x] = col
            elif d < r + 6:
                a = (1 - (d - r) / 6) ** 2
                if a > bayer(x, y) * 0.9:
                    glow[y, x] = P("magenta", 2)
    return out, glow


def clouds():
    w, h = 512, 70
    out = canvas(w, h)
    banks = []
    for _ in range(9):
        cx, cy = _r(0, w), _r(10, 60)
        banks.append((cx, cy, _r(50, 130), _r(3, 7)))
    dens = np.zeros((h, w))
    for cx, cy, rx, ry in banks:
        for ox in (-w, 0, w):
            for y in range(h):
                for x in range(w):
                    dx = (x - cx - ox) / rx
                    dy = (y - cy) / ry
                    d = dx * dx + dy * dy * (1 + 0.4 * math.sin(x * 0.07 + cy))
                    if d < 1:
                        dens[y, x] = max(dens[y, x], 1 - d)
    for y in range(h):
        for x in range(w):
            v = dens[y, x]
            if v <= 0.08 + bayer(x, y) * 0.12:
                continue
            below = dens[min(h - 1, y + 2), x]
            if below < v - 0.05 or v < 0.25:
                col = P("magenta", 1) if v > 0.18 else P("magenta", 0)   # underside lit by the city
            else:
                col = P("night", 3) if v > 0.6 else P("night", 4)
            out[y, x] = col
    return out


# --- Skyline ------------------------------------------------------------------------------

def far_skyline():
    w, h = 512, 110
    out = canvas(w, h)
    blinkers = []
    x = 0
    while x < w:
        bw = _r(14, 38)
        bh = _r(50, 108)
        top = h - bh
        kind = _r(0, 5)
        for yy in range(top, h):
            for xx in range(x, min(w, x + bw)):
                fog = (yy - (h - 60)) / 60
                col = P("night", 3)
                if fog > 0 and fog > bayer(xx, yy) * 1.2:
                    col = P("night", 4) if fog < 0.6 else P("night", 5)
                if xx == x + bw - 1 and yy < h - 30:
                    col = P("magenta", 0)          # rim of horizon glow on the right edges
                out[yy, xx] = col
        if kind == 0:         # spire
            for k in range(_r(8, 18)):
                put(out, x + bw // 2, top - k, P("night", 2))
            blinkers.append((x + bw // 2, top - 18))
        elif kind == 1:       # stepped top
            rect(out, x + 3, top - 5, x + bw - 3, top, P("night", 2))
        elif kind == 2:       # water tower
            rect(out, x + 4, top - 6, x + 10, top - 1, P("night", 2))
            put(out, x + 5, top - 1, P("night", 2))
            put(out, x + 9, top - 1, P("night", 2))
        # windows: a few warm and cool pinpricks
        for _ in range(bw * bh // 45):
            wx, wy = x + 2 + _r(0, max(1, bw - 4)), top + 3 + _r(0, max(1, bh - 10))
            if (wx + wy) % 2 == 0 and wy < h - 8:
                put(out, wx, wy, P("yellow", 0) if _r(0, 3) else P("cyan", 2))
        x += bw + _r(-4, 3)
    return out, [(bx % w, max(1, by)) for bx, by in blinkers]


SIGNS = ["NITRO", "MOTEL", "ARCADE", "RAMEN", "24H", "VOLTAGE", "DINER", "KARAOKE", "HOTEL"]


def mid_buildings():
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    signs = list(SIGNS)
    x = 0
    sign_i = 0
    while x < w - 20:
        bw = _r(46, 92)
        bh = _r(48, 112) if _r(0, 4) else _r(110, 140)
        top = h - bh
        bw = min(bw, w - x)
        base = P("night", 1)
        face = P("night", 2)
        for yy in range(top, h):
            for xx in range(x, x + bw):
                col = face
                if xx < x + 2:
                    col = P("cyan", 0) if yy > top + 2 else P("night", 3)     # cyan rim from the signs
                elif xx > x + bw - 4:
                    col = base
                if yy < top + 2:
                    col = P("night", 3)
                out[yy, xx] = col
        # ledge every few floors
        for ly in range(top + 10, h, 18):
            hline(out, x, x + bw, ly, P("night", 3))
            hline(out, x, x + bw, ly + 1, P("night", 0))
        # window grid
        cols = (bw - 8) // 6
        for wy in range(top + 5, h - 6, 6):
            for c in range(cols):
                wx = x + 5 + c * 6
                lit = RNG.random()
                if lit < 0.35:
                    colw = P("yellow", 0) if lit < 0.2 else P("orange", 2)
                    glow_c = P("orange", 0)
                elif lit < 0.45:
                    colw = P("cyan", 2)
                    glow_c = P("cyan", 0)
                else:
                    colw = P("night", 3) if lit < 0.8 else P("blue", 0)
                    glow_c = None
                rect(out, wx, wy, wx + 3, wy + 3, colw)
                put(out, wx, wy, P("night", 4) if glow_c is None else colw)
                if glow_c is not None:
                    hline(out, wx, wx + 3, wy + 2, P("orange", 1) if colw == P("yellow", 0) else colw)
                    if lit < 0.12:
                        rect(glow, wx, wy, wx + 3, wy + 3, glow_c)
        # rooftop clutter
        if _r(0, 2):
            rect(out, x + 6, top - 4, x + 14, top, P("night", 2))
            rect(out, x + bw - 16, top - 3, x + bw - 9, top, P("night", 2))
        # neon sign: vertical blade or a horizontal box
        text = signs[sign_i % len(signs)]
        sign_i += 1
        tube = [P("magenta", 3), P("cyan", 3), P("yellow", 1), P("red", 3)][_r(0, 4)]
        visible_bottom = h - 58      # the grandstand covers the rest
        if _r(0, 2) and top + 14 + len(text) * 9 + 2 < visible_bottom:
            sx, sy = x + bw - 14, top + 14
            rect(out, sx - 2, sy - 3, sx + 9, sy + len(text) * 9 + 1, P("night", 0))
            rect(glow, sx - 2, sy - 3, sx + 9, sy + len(text) * 9 + 1, P("night", 0))
            for k, ch in enumerate(text):
                draw_text(out, ch, sx + 1, sy + k * 9, tube)
                draw_text(glow, ch, sx + 1, sy + k * 9, tube)
            vline(out, sx - 3, sy - 3, sy + len(text) * 9 + 1, P("steel", 2))
        else:
            tw = text_width(text)
            sx = x + max(3, (bw - tw) // 2)
            sy = top + 8
            if sx + tw < x + bw:
                rect(out, sx - 3, sy - 3, sx + tw + 3, sy + 10, P("night", 0))
                for gx in range(sx - 3, sx + tw + 3):       # sign border tube
                    put(out, gx, sy - 3, tube)
                    put(out, gx, sy + 9, tube)
                    put(glow, gx, sy - 3, tube)
                    put(glow, gx, sy + 9, tube)
                draw_text(out, text, sx, sy, P("yellow", 2) if tube != P("yellow", 1) else P("magenta", 3))
                draw_text(glow, text, sx, sy, tube)
        x += bw + _r(2, 10)
    return out, glow


# --- Grandstands and crowd ------------------------------------------------------------------

STAND_W, STAND_H = 384, 64
CROWD_H = 34


def stands():
    out = canvas(STAND_W, STAND_H)
    glow = canvas(STAND_W, STAND_H)
    # roof canopy
    for y in range(0, 7):
        for x in range(STAND_W):
            out[y, x] = P("steel", 3) if y == 0 else (P("steel", 2) if y < 5 else P("night", 1))
    for x in range(8, STAND_W, 24):                     # canopy lights
        rect(out, x, 5, x + 4, 7, P("yellow", 2))
        rect(glow, x - 1, 5, x + 5, 8, P("yellow", 1))
    # back wall and tiers
    for y in range(7, STAND_H):
        for x in range(STAND_W):
            out[y, x] = P("night", 1) if (y // 6) % 2 else P("night", 2)
    for ty in range(12, STAND_H, 6):
        hline(out, 0, STAND_W, ty, P("steel", 1))
    for cx in range(0, STAND_W, 96):                     # support columns
        rect(out, cx + 2, 0, cx + 6, STAND_H, P("steel", 2))
        vline(out, cx + 2, 0, STAND_H, P("steel", 3))
    # front rail
    hline(out, 0, STAND_W, STAND_H - 4, P("steel", 4))
    hline(out, 0, STAND_W, STAND_H - 3, P("steel", 2))
    return out, glow


SHIRTS = [("red", 1), ("cyan", 0), ("magenta", 1), ("blue", 1), ("green", 1), ("steel", 2), ("night", 4), ("night", 3), ("steel", 3)]
BRIGHT = [("red", 2), ("cyan", 2), ("magenta", 3), ("yellow", 0), ("steel", 5)]


def crowd():
    """Three frames stacked vertically: idle, bounce, cheer (arms up)."""
    frames = [canvas(STAND_W, CROWD_H) for _ in range(3)]
    people = []
    for row in range(4):
        y = 8 + row * 6
        x = _r(0, 4)
        while x < STAND_W - 4:
            shirt = BRIGHT[_r(0, len(BRIGHT))] if RNG.random() < 0.18 else SHIRTS[_r(0, len(SHIRTS))]
            skin = ("skin", _r(1, 4) if row < 2 else _r(0, 3))
            people.append((x, y, shirt, skin, _r(0, 3), RNG.random()))
            x += _r(5, 8)
    for f, img in enumerate(frames):
        for (x, y, shirt, skin, hair, phase) in sorted(people, key=lambda p: p[1]):
            bob = 0
            arms = False
            if f == 1:
                bob = -1 if phase < 0.5 else 0
            elif f == 2:
                bob = -1 if phase < 0.7 else 0
                arms = phase < 0.55
            yy = y + bob
            sc = P(*shirt)
            sk = P(*skin)
            # shoulders / torso
            rect(img, x, yy + 3, x + 4, yy + 6, sc)
            put(img, x, yy + 3, P("night", 1))
            put(img, x + 3, yy + 3, P("night", 1))
            # head
            rect(img, x + 1, yy, x + 3, yy + 3, sk)
            put(img, x + 2, yy, P("skin", 0) if hair == 0 else (P("yellow", 0) if hair == 1 else P("night", 0)))
            put(img, x + 1, yy, P("night", 0) if hair != 1 else P("orange", 1))
            if arms:
                put(img, x, yy + 1, sk)
                put(img, x, yy + 2, sc)
                put(img, x + 3, yy + 1, sk)
                put(img, x + 3, yy + 2, sc)
    return np.concatenate(frames, axis=0)


def photographers_flash():
    s = 7
    out = canvas(s, s)
    c = 3
    for y in range(s):
        for x in range(s):
            if x == c or y == c:
                d = abs(x - c) + abs(y - c)
                if d <= 3:
                    out[y, x] = P("yellow", 2) if d <= 1 else P("cyan", 3)
    return out


# --- Trackside -----------------------------------------------------------------------------

WALL_W, WALL_H = 384, 46
BANNERS = [("HYPERNOVA", "magenta"), ("GRIPZ", "cyan"), ("OCTANE 9", "yellow"), ("PISTON PUNK", "red")]


def wall():
    out = canvas(WALL_W, WALL_H)
    glow = canvas(WALL_W, WALL_H)
    # chain-link fence
    for y in range(0, 22):
        for x in range(WALL_W):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                out[y, x] = P("steel", 2) if y > 2 else P("steel", 3)
    for x in range(0, WALL_W, 32):
        vline(out, x, 0, 24, P("steel", 3))
        vline(out, x + 1, 0, 24, P("steel", 1))
    hline(out, 0, WALL_W, 0, P("steel", 4))
    # concrete barrier
    for y in range(22, WALL_H):
        for x in range(WALL_W):
            if y < 24:
                col = P("steel", 4)
            elif y < 38:
                col = P("steel", 3) if bayer(x, y) > (y - 24) / 14 else P("steel", 2)
            else:
                col = P("steel", 1) if y < WALL_H - 2 else P("night", 1)
            out[y, x] = col
    for x in range(0, WALL_W, 48):          # barrier joints
        vline(out, x, 23, WALL_H, P("steel", 1))
    # sponsor banners on the barrier
    for i, (text, ramp) in enumerate(BANNERS):
        bx = 12 + i * 96
        tw = text_width(text)
        rect(out, bx, 26, bx + tw + 8, 37, P(ramp, 1))
        hline(out, bx, bx + tw + 8, 26, P(ramp, -1))
        hline(out, bx, bx + tw + 8, 36, P(ramp, 0))
        draw_text(out, text, bx + 4, 28, P("yellow", 2) if ramp != "yellow" else P("night", 1))
    return out, glow


POLE_W, POLE_H = 384, 150


def poles():
    """Light poles leaning over the strip. The lamp heads are emissive."""
    out = canvas(POLE_W, POLE_H)
    glow = canvas(POLE_W, POLE_H)
    px = 180
    vline(out, px, 8, POLE_H, P("steel", 2))
    vline(out, px + 1, 8, POLE_H, P("steel", 1))
    vline(out, px - 1, 8, POLE_H, P("steel", 3))
    for k in range(18):                       # arm reaching toward the road
        put(out, px + 1 + k, 8 - k // 6, P("steel", 2))
        put(out, px + 1 + k, 9 - k // 6, P("steel", 1))
    hx, hy = px + 18, 4
    rect(out, hx - 3, hy, hx + 7, hy + 3, P("steel", 3))
    rect(out, hx - 2, hy + 3, hx + 6, hy + 4, P("yellow", 2))
    rect(glow, hx - 3, hy + 2, hx + 7, hy + 5, P("yellow", 1))
    # small banner flags on the pole
    rect(out, px + 2, 40, px + 10, 70, P("magenta", 1))
    rect(out, px + 2, 40, px + 10, 42, P("magenta", 3))
    draw_text(out, "P", px + 3, 46, P("yellow", 2))
    draw_text(out, "S", px + 3, 56, P("yellow", 2))
    return out, glow


# --- Road ------------------------------------------------------------------------------------

ROAD_W, ROAD_H = 256, 67
FAR_LANE_Y, NEAR_LANE_Y = 22, 50     # wheel contact rows inside the road layer


def road():
    out = canvas(ROAD_W, ROAD_H)
    for y in range(ROAD_H):
        for x in range(ROAD_W):
            if y < 3:
                col = P("steel", 3) if y == 0 else P("steel", 2)               # far curb
            elif y >= ROAD_H - 7:
                col = P("steel", 2) if y < ROAD_H - 5 else (P("steel", 1) if y < ROAD_H - 2 else P("night", 1))
            else:
                n = RNG.random()
                depth = (y - 3) / (ROAD_H - 10)
                col = P("night", 2) if n > 0.82 - depth * 0.15 else P("night", 1)
                if n > 0.995:
                    col = P("steel", 1)
                elif n < 0.015:
                    col = P("night", 0)
            out[y, x] = col
    hline(out, 0, ROAD_W, 5, P("steel", 4))                        # far lane edge
    for x in range(ROAD_W):                                        # centre line, slightly worn
        if RNG.random() > 0.08:
            put(out, x, 34, P("yellow", 0))
            put(out, x, 35, P("orange", 2) if RNG.random() > 0.3 else P("yellow", 0))
    hline(out, 0, ROAD_W, ROAD_H - 9, P("steel", 4))                # near lane edge
    # puddle-ish sheen streaks reflecting neon
    for _ in range(14):
        sx, sy = _r(0, ROAD_W), _r(8, ROAD_H - 12)
        if abs(sy - 34) > 3:
            for k in range(_r(4, 12)):
                put(out, sx + k, sy, P("night", 3) if k % 3 else P("magenta", 0))
    return out


def rubber():
    """Dark launch-pad rubber, drawn over the road around the start line."""
    w, h = 320, ROAD_H
    out = canvas(w, h)
    for lane_y in (FAR_LANE_Y, NEAR_LANE_Y):
        for y in range(lane_y - 4, lane_y + 3):
            for x in range(w):
                fade = 1 - x / w
                if fade > bayer(x, y) * 1.1 and abs(y - lane_y) < 3 + fade * 1.5:
                    out[y, x] = P("night", 0)
    return out


def start_line():
    w, h = 10, ROAD_H
    out = canvas(w, h)
    for y in range(3, h - 7):
        x0 = int((h - y) * 0.12)
        for x in range(x0, x0 + 3):
            out[y, x] = P("steel", 6) if y % 6 else P("steel", 4)
    return out


def finish_line():
    w, h = 16, ROAD_H
    out = canvas(w, h)
    for y in range(3, h - 7):
        x0 = int((h - y) * 0.12)
        for x in range(8):
            c = ((x // 2) + (y // 3)) % 2
            out[y, x0 + x] = P("steel", 6) if c else P("night", 0)
    return out


def finish_gantry():
    """Timing tower and FINISH board on the far side of the strip."""
    w, h = 56, 120
    out = canvas(w, h)
    glow = canvas(w, h)
    for lx in (6, 46):
        rect(out, lx, 20, lx + 4, h, P("steel", 2))
        vline(out, lx, 20, h, P("steel", 3))
    rect(out, 0, 0, w, 24, P("night", 0))
    hline(out, 0, w, 0, P("steel", 4))
    hline(out, 0, w, 23, P("steel", 2))
    draw_text(out, "FINISH", 10, 8, P("magenta", 3))
    draw_text(glow, "FINISH", 10, 8, P("magenta", 3))
    for k in range(0, w, 4):
        put(out, k + 1, 2, P("yellow", 2))
        put(out, k + 1, 21, P("yellow", 2))
        put(glow, k + 1, 2, P("yellow", 1))
        put(glow, k + 1, 21, P("yellow", 1))
    # ET board
    rect(out, 12, 34, 44, 60, P("night", 0))
    rect(out, 12, 34, 44, 35, P("steel", 3))
    draw_text(out, "ET", 22, 38, P("red", 3))
    draw_text(glow, "ET", 22, 38, P("red", 3))
    for k in range(4):
        rect(out, 15 + k * 7, 49, 20 + k * 7, 56, P("red", 1))
    return out, glow


def foreground():
    w, h = 512, 34
    out = canvas(w, h)
    x = 0
    while x < w:
        kind = _r(0, 4)
        if kind == 0:       # crowd-control barrier
            bw = 30
            hline(out, x, x + bw, h - 16, P("steel", 1))
            hline(out, x, x + bw, h - 15, P("night", 0))
            for k in range(x, x + bw, 3):
                vline(out, k, h - 15, h - 4, P("night", 0))
            vline(out, x, h - 18, h, P("steel", 1))
            vline(out, x + bw - 1, h - 18, h, P("steel", 1))
            hline(out, x, x + bw, h - 17, P("magenta", 1))     # neon rim light
        elif kind == 1:     # tyre stack
            for k in range(3):
                ty = h - 6 - k * 5
                rect(out, x, ty, x + 12, ty + 5, P("night", 0))
                hline(out, x + 1, x + 11, ty, P("steel", 1))
                hline(out, x + 2, x + 10, ty + 2, P("night", 1))
            hline(out, x + 1, x + 11, h - 16, P("cyan", 0))
            bw = 12
        elif kind == 2:     # cone
            for k in range(8):
                hline(out, x + 4 - k // 2, x + 5 + k // 2, h - 9 + k, P("orange", 1) if k not in (3, 4) else P("steel", 6))
            hline(out, x, x + 10, h - 1, P("night", 0))
            bw = 10
        else:
            bw = _r(8, 40)
        x += bw + _r(14, 60)
    return out


# --- Colour grade ---------------------------------------------------------------------------

def lut(grade):
    """16x16x16 LUT laid out as a 256x16 strip (blue picks the 16px slice)."""
    n = 16
    out = np.zeros((n, n * n, 4), dtype=np.uint8)
    for b in range(n):
        for g in range(n):
            for r in range(n):
                rgb = np.array([r, g, b], dtype=np.float32) / (n - 1)
                out[g, b * n + r, :3] = np.clip(grade(rgb) * 255, 0, 255)
                out[g, b * n + r, 3] = 255
    return out


def neon_grade(c):
    luma = c @ np.array([0.299, 0.587, 0.114])
    shadows = (1 - luma) ** 2
    hi = luma ** 2
    c = c + shadows * np.array([0.02, -0.005, 0.06])     # lift shadows into blue-violet
    c = c + hi * np.array([0.05, 0.0, -0.03])              # warm, pink-leaning highlights
    c = luma + (c - luma) * 1.12                           # a touch more saturation
    c = 0.03 + c * 0.97                                    # soft black level
    return np.clip(c, 0, 1)


# --- Build -----------------------------------------------------------------------------------

def build():
    base = f"tracks/{ID}"
    save(sky(), f"{base}/sky.png")
    m, mg = moon()
    save(m, f"{base}/moon.png")
    save(mg, f"{base}/moon_glow.png")
    save(clouds(), f"{base}/clouds.png")
    far, blinkers = far_skyline()
    save(far, f"{base}/far.png")
    mid, mid_glow = mid_buildings()
    save(mid, f"{base}/mid.png")
    save(mid_glow, f"{base}/mid_glow.png")
    st, st_glow = stands()
    save(st, f"{base}/stands.png")
    save(st_glow, f"{base}/stands_glow.png")
    save(crowd(), f"{base}/crowd.png")
    save(photographers_flash(), "fx/flash.png")
    wl, wl_glow = wall()
    save(wl, f"{base}/wall.png")
    pl, pl_glow = poles()
    save(pl, f"{base}/poles.png")
    save(pl_glow, f"{base}/poles_glow.png")
    save(road(), f"{base}/road.png")
    save(rubber(), f"{base}/rubber.png")
    save(start_line(), f"{base}/start_line.png")
    save(finish_line(), f"{base}/finish_line.png")
    fg, fg_glow = finish_gantry()
    save(fg, f"{base}/finish_gantry.png")
    save(fg_glow, f"{base}/finish_gantry_glow.png")
    save(foreground(), f"{base}/foreground.png")
    save(lut(neon_grade), f"{base}/lut.png")
    with open(f"{ASSETS}/{base}/blinkers.txt", "w") as f:
        f.write("\n".join(f"{x} {y}" for x, y in blinkers) + "\n")


def preview(path):
    """Rough static composite at 480x270, 2x, to eyeball the layer stack without the game."""
    W, H = 480, 270
    img = Image.new("RGBA", (W, H))
    base = f"{ASSETS}/tracks/{ID}"

    def tile(name, y_top, xoff=0):
        src = Image.open(f"{base}/{name}.png")
        x = -xoff % src.width - src.width
        while x < W:
            img.alpha_composite(src, (x, y_top))
            x += src.width

    tile("sky", 0)
    img.alpha_composite(Image.open(f"{base}/moon.png"), (360, 22))
    tile("clouds", 20, 40)
    tile("far", 175 - 110, 60)
    tile("mid", 186 - 160, 130)
    tile("stands", 196 - 64, 200)
    crowd_img = Image.open(f"{base}/crowd.png").crop((0, 0, STAND_W, CROWD_H))
    for x in range(-200, W, STAND_W):
        img.alpha_composite(crowd_img, (x, 196 - 64 + 14))
    tile("poles", 203 - 150 + 4, 260)
    tile("wall", 203 - WALL_H, 260)
    tile("road", 203)
    img.alpha_composite(Image.open(f"{base}/finish_gantry.png"), (380, 203 - 120 + 10))
    tile("foreground", H - 34, 400)
    img.resize((W * 2, H * 2), Image.NEAREST).save(path)
