"""Menu scenes: the garage and the dealership showroom, each with an emissive (bloom) layer.

Both are 640x270 (the widest framebuffer); narrower screens show the centre. The car stands on
the lift / turntable with its wheels on row FLOOR_Y (from the top); moving lights, dust and
rain are animated in code.
"""
import math

import numpy as np

from common import P, bayer, canvas, dither_ramp, hline, put, rect, save, vline
from ui import draw_text, text_width

W, H = 640, 270
FLOOR_TOP = 200          # where the back wall meets the floor
FLOOR_Y = 232            # car wheels touch this row
RNG = np.random.default_rng(77)


def _r(a, b):
    return int(RNG.integers(a, b))


def _floor(out, base, near, stains=True, gloss=None):
    for y in range(FLOOR_TOP, H):
        t = (y - FLOOR_TOP) / (H - FLOOR_TOP)
        for x in range(W):
            out[y, x] = [base, near][dither_ramp(t * 1.1, x, y, 2)]
    hline(out, 0, W, FLOOR_TOP, P("steel", 2))
    hline(out, 0, W, FLOOR_TOP + 1, P("night", 0))
    if stains:
        for _ in range(9):
            cx, cy, rx = _r(20, W - 20), _r(FLOOR_TOP + 10, H - 6), _r(8, 26)
            for y in range(cy - 3, cy + 4):
                for x in range(cx - rx, cx + rx):
                    if ((x - cx) / rx) ** 2 + ((y - cy) / 3.5) ** 2 < 1 and bayer(x, y) > 0.35:
                        put(out, x, y, P("night", 1))
    for k in range(-6, 8):                                # floor joints receding to a vanishing point
        x0 = W / 2 + k * 70
        for y in range(FLOOR_TOP + 2, H, 1):
            t = (y - FLOOR_TOP) / (H - FLOOR_TOP)
            x = int(W / 2 + (x0 - W / 2) * (0.35 + t * 0.9))
            if (y // 2) % 2 == 0:
                put(out, x, y, P("night", 1))


def _neon_sign(out, glow, text, x, y, tube, scale=2):
    tw = text_width(text) * scale
    rect(out, x - 6, y - 5, x + tw + 6, y + 7 * scale + 5, P("night", 0))
    for gx in range(x - 6, x + tw + 6):
        for gy in (y - 5, y + 7 * scale + 4):
            put(out, gx, gy, tube)
            put(glow, gx, gy, tube)
    draw_text(out, text, x, y, P("yellow", 2), scale=scale)
    draw_text(glow, text, x, y, tube, scale=scale)


def _tools(out, x0, y0, x1, y1):
    rect(out, x0, y0, x1, y1, P("steel", 1))
    for y in range(y0 + 2, y1, 4):
        for x in range(x0 + 2, x1, 4):
            put(out, x, y, P("night", 1))
    hline(out, x0, x1, y0, P("steel", 3))
    # wrenches (diagonal bars with jaws), a hammer, a saw, a coiled hose
    for k in range(5):
        bx = x0 + 8 + k * 9
        for i in range(22 - k * 2):
            put(out, bx + i // 6, y0 + 8 + i, P("steel", 4 if i % 5 else 5))
        put(out, bx - 1, y0 + 7, P("steel", 4))
        put(out, bx + 1, y0 + 7, P("steel", 4))
    hx = x0 + 60
    rect(out, hx, y0 + 10, hx + 2, y0 + 34, P("orange", 0))
    rect(out, hx - 4, y0 + 8, hx + 6, y0 + 12, P("steel", 3))
    for i in range(30):
        put(out, x0 + 70 + i, y0 + 44 + i // 6, P("steel", 4))
        put(out, x0 + 70 + i, y0 + 45 + i // 6, P("steel", 3) if i % 2 else P("steel", 2))
    cx, cy = x1 - 18, y0 + 22
    for a in range(0, 360, 8):
        for r in (9, 7):
            put(out, cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)), P("red", 1))


def _poster(out, x0, y0, title, ramp):
    w, h = 40, 56
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            t = (y - y0) / h
            out[y, x] = [P(ramp, 2), P(ramp, 1), P("night", 2)][dither_ramp(t, x, y, 3)]
    for i in range(w):                                  # little car silhouette
        hgt = 5 if 8 < i < 30 else (3 if 4 < i < 36 else 0)
        for j in range(hgt):
            put(out, x0 + i, y0 + 38 - j, P("night", 0))
    for cx in (x0 + 10, x0 + 30):
        for a in range(0, 360, 30):
            put(out, cx + 2.5 * math.cos(math.radians(a)), y0 + 39 + 2.5 * math.sin(math.radians(a)), P("night", 0))
    draw_text(out, title, x0 + (w - text_width(title)) // 2, y0 + 6, P("yellow", 2))
    for x in range(x0, x0 + w):
        put(out, x, y0, P("steel", 5))
        put(out, x, y0 + h - 1, P("night", 0))


def _city_window(out, glow, x0, y0, x1, y1, mullion=0):
    for y in range(y0, y1):
        t = (y - y0) / (y1 - y0)
        for x in range(x0, x1):
            out[y, x] = [P("night", 1), P("night", 2), P("night", 3), P("magenta", 0)][dither_ramp(t ** 1.4, x, y, 4)]
    x = x0
    while x < x1:
        bw, bh = _r(10, 24), _r(10, int((y1 - y0) * 0.7))
        rect(out, x, y1 - bh, min(x1, x + bw), y1, P("night", 1))
        for _ in range(bw * bh // 30):
            wx, wy = x + _r(1, max(2, bw - 1)), y1 - bh + _r(2, max(3, bh - 2))
            if wx < x1 and (wx + wy) % 2 == 0:
                c = P("yellow", 0) if _r(0, 3) else P("cyan", 2)
                put(out, wx, wy, c)
                put(glow, wx, wy, c)
        x += bw + _r(0, 3)
    for x in range(x0 - 2, x1 + 2):
        for y in (y0 - 2, y0 - 1, y1, y1 + 1):
            put(out, x, y, P("steel", 2) if y in (y0 - 2, y1 + 1) else P("steel", 3))
    for y in range(y0 - 2, y1 + 2):
        for x in (x0 - 2, x0 - 1, x1, x1 + 1):
            put(out, x, y, P("steel", 2))
    if mullion:
        for x in range(x0 + mullion, x1, mullion):
            rect(out, x - 1, y0, x + 1, y1, P("steel", 2))
            vline(out, x - 1, y0, y1, P("steel", 3))
    else:
        vline(out, (x0 + x1) // 2, y0, y1, P("steel", 2))
        hline(out, x0, x1, (y0 + y1) // 2, P("steel", 2))


def garage():
    out = canvas(W, H)
    glow = canvas(W, H)
    # ceiling and back wall panels
    for y in range(FLOOR_TOP):
        for x in range(W):
            out[y, x] = P("night", 1) if y < 32 else P("night", 2)
    for x in range(0, W, 40):
        vline(out, x, 32, FLOOR_TOP, P("night", 1))
        vline(out, x + 1, 32, FLOOR_TOP, P("night", 3))
    for x in range(0, W, 24):                            # roof trusses
        for i in range(24):
            put(out, x + i, 30 - i // 3, P("steel", 1))
    rect(out, 0, 30, W, 36, P("steel", 1))
    hline(out, 0, W, 30, P("steel", 3))
    hline(out, 0, W, 150, P("night", 3))                 # wainscot line
    for y in range(151, FLOOR_TOP):
        for x in range(W):
            if bayer(x, y) > 0.7:
                out[y, x] = P("night", 1)
    # roll-up door on the left
    rect(out, 10, 66, 130, FLOOR_TOP, P("steel", 2))
    for y in range(66, FLOOR_TOP, 6):
        hline(out, 12, 128, y, P("steel", 3))
        hline(out, 12, 128, y + 4, P("steel", 1))
    rect(out, 6, 60, 134, 66, P("steel", 1))
    vline(out, 8, 60, FLOOR_TOP, P("night", 0))
    vline(out, 131, 60, FLOOR_TOP, P("night", 0))
    rect(out, 66, 186, 74, 190, P("yellow", 0))         # handle
    _tools(out, 146, 86, 252, 160)
    _neon_sign(out, glow, "NIGHT SHIFT", 254, 50, P("magenta", 3))
    draw_text(out, "GARAGE", 290, 78, P("cyan", 3), scale=1)
    draw_text(glow, "GARAGE", 290, 78, P("cyan", 3), scale=1)
    _poster(out, 420, 92, "DRAG", "magenta")
    _poster(out, 470, 92, "NITRO", "orange")
    _city_window(out, glow, 538, 70, 620, 136)
    # workbench and toolbox
    rect(out, 430, 168, 612, 172, P("steel", 3))
    hline(out, 430, 612, 168, P("steel", 5))
    for lx in (434, 606):
        rect(out, lx, 172, lx + 3, FLOOR_TOP, P("steel", 1))
    rect(out, 444, 140, 494, 168, P("red", 1))
    for dy in range(144, 166, 6):
        hline(out, 446, 492, dy, P("red", 0))
        hline(out, 462, 476, dy + 2, P("steel", 5))
    hline(out, 444, 494, 140, P("red", 3))
    # oil drum and tyre stack
    rect(out, 614, 158, 636, FLOOR_TOP, P("blue", 1))
    for y in (164, 180, 194):
        hline(out, 614, 636, y, P("blue", 3))
    vline(out, 616, 158, FLOOR_TOP, P("blue", 2))
    for k in range(4):
        ty = FLOOR_TOP - 8 - k * 7
        rect(out, 92, ty, 122, ty + 7, P("night", 0))
        hline(out, 94, 120, ty, P("steel", 1))
        hline(out, 98, 116, ty + 3, P("night", 1))
    _floor(out, P("night", 2), P("night", 3))
    # neon sheen on the floor under the sign
    for y in range(FLOOR_TOP + 4, FLOOR_TOP + 30):
        for x in range(250, 390):
            if bayer(x, y) > 0.55 + (y - FLOOR_TOP) / 60 and (x + y) % 3 == 0:
                put(out, x, y, P("magenta", 0))
    # two-post lift around the car spot
    for px in (196, 440):
        rect(out, px, 120, px + 8, FLOOR_Y + 4, P("steel", 2))
        vline(out, px, 120, FLOOR_Y + 4, P("steel", 4))
        vline(out, px + 7, 120, FLOOR_Y + 4, P("steel", 1))
        rect(out, px - 2, 116, px + 10, 120, P("yellow", 0))
    for x in range(204, 440):                          # platform with hazard stripes
        for y in range(FLOOR_Y, FLOOR_Y + 5):
            stripe = ((x + y) // 4) % 2 == 0
            out[y, x] = P("yellow", 0) if stripe else P("night", 0)
        put(out, x, FLOOR_Y, P("steel", 4))
    # hanging lamps
    for lx in (186, 456):
        vline(out, lx, 36, 52, P("steel", 1))
        for i in range(10):
            hline(out, lx - 2 - i // 2, lx + 3 + i // 2, 52 + i // 2, P("steel", 3) if i < 2 else P("steel", 2))
        hline(out, lx - 5, lx + 6, 57, P("yellow", 2))
        hline(glow, lx - 6, lx + 7, 57, P("yellow", 1))
        hline(glow, lx - 4, lx + 5, 58, P("yellow", 0))
    return out, glow


def showroom():
    out = canvas(W, H)
    glow = canvas(W, H)
    for y in range(FLOOR_TOP):
        for x in range(W):
            out[y, x] = P("night", 1) if y < 26 else P("night", 0)
    _city_window(out, glow, 0, 28, W, 186, mullion=80)
    rect(out, 0, 186, W, FLOOR_TOP, P("night", 1))
    hline(out, 0, W, 186, P("steel", 3))
    sign = "CHROME & CO. MOTORS"
    _neon_sign(out, glow, sign, (W - text_width(sign) * 2) // 2, 6, P("yellow", 0))
    for px in (12, W - 24):                             # pillars
        rect(out, px, 26, px + 12, FLOOR_TOP, P("night", 2))
        vline(out, px, 26, FLOOR_TOP, P("steel", 3))
        vline(out, px + 11, 26, FLOOR_TOP, P("night", 0))
    # glossy tile floor, reflecting the windows
    for y in range(FLOOR_TOP, H):
        t = (y - FLOOR_TOP) / (H - FLOOR_TOP)
        for x in range(W):
            col = P("night", 1) if bayer(x, y) > t * 0.6 else P("night", 0)
            if (x // 40 + (y - FLOOR_TOP) // 12) % 2 == 0:
                col = P("night", 2) if col == P("night", 1) else P("night", 1)
            out[y, x] = col
            src_y = FLOOR_TOP - (y - FLOOR_TOP) * 2 - 14      # mirrored window light, fading
            if 30 < src_y < 186 and out[src_y, x][0] > 60 and bayer(x, y) > 0.45 + t:
                out[y, x] = P("night", 3)
    # turntable
    cx, cy = W // 2, FLOOR_Y + 2
    for y in range(cy - 10, cy + 12):
        for x in range(cx - 110, cx + 111):
            d = ((x - cx) / 110) ** 2 + ((y - cy) / 10) ** 2
            if d < 1:
                rim = d > 0.82
                out[y, x] = P("steel", 3) if rim and y < cy else (P("steel", 1) if rim else (P("steel", 2) if (x // 6) % 2 else P("steel", 1)))
                if rim:
                    glow[y, x] = P("cyan", 1)
    for x in range(cx - 108, cx + 109):
        put(out, x, cy + 10, P("night", 0))
    return out, glow


# district outlines on the career map (x, y from the top); the game draws the race nodes on top
DISTRICTS = {        # inside x 84..556 so a 480-wide screen sees all of it
    "street": [(92, 152), (300, 148), (304, 258), (88, 262)],
    "sport": [(92, 22), (296, 18), (300, 124), (88, 128)],
    "pro": [(340, 18), (548, 22), (552, 128), (336, 124)],
    "outlaw": [(336, 148), (552, 152), (548, 262), (340, 258)],
}
DISTRICT_RAMPS = {"street": "yellow", "sport": "cyan", "pro": "magenta", "outlaw": "red"}


def career_map():
    from common import poly_mask
    out = canvas(W, H)
    glow = canvas(W, H)
    for y in range(H):
        for x in range(W):
            out[y, x] = P("night", 0) if (x // 8 + y // 8) % 2 else P("night", 1)
            if x % 16 == 0 or y % 16 == 0:
                out[y, x] = P("night", 1)
    # the river, winding between the two rows of districts
    for x in range(W):
        cy = 138 + 4 * math.sin(x / 40.0)
        for y in range(int(cy - 5), int(cy + 6)):
            out[y, x] = P("blue", 0) if abs(y - cy) < 4 else P("night", 2)
            if (x + y) % 9 == 0:
                out[y, x] = P("blue", 1)
    # districts: dim fill, city blocks, a glowing border in the tier colour
    for name, pts in DISTRICTS.items():
        ramp = DISTRICT_RAMPS[name]
        m = poly_mask(W, H, pts) >= 0.5
        for y, x in zip(*np.nonzero(m)):
            block = (x % 12 < 9) and (y % 10 < 7)
            if block:
                out[y, x] = P("night", 2) if (x * 7 + y * 3) % 5 else P("night", 3)
                if (x * 13 + y * 7) % 41 == 0:
                    out[y, x] = P(ramp, 0)                 # lit windows seen from above
        pad = np.pad(m, 1)
        edge = m & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
        for y, x in zip(*np.nonzero(edge)):
            out[y, x] = P(ramp, 1)
            glow[y, x] = P(ramp, 1)
    # one neon road joining the districts in race order: street, sport, pro, outlaw
    roads = [[(196, 150), (196, 126)], [(298, 70), (338, 70)], [(444, 126), (444, 150)]]
    for road in roads:
        for (ax, ay), (bx, by) in zip(road, road[1:]):
            n = int(max(abs(bx - ax), abs(by - ay)))
            for i in range(n + 1):
                x = int(ax + (bx - ax) * i / n)
                y = int(ay + (by - ay) * i / n)
                for d in (0, 1):
                    xx, yy = (x + d, y) if ax == bx else (x, y + d)
                    put(out, xx, yy, P("magenta", 2))
                    put(glow, xx, yy, P("magenta", 1))
    return out, glow


def build():
    g, gg = garage()
    save(g, "scenes/garage.png")
    save(gg, "scenes/garage_glow.png")
    s, sg = showroom()
    save(s, "scenes/showroom.png")
    save(sg, "scenes/showroom_glow.png")
    m, mg = career_map()
    save(m, "scenes/career_map.png")
    save(mg, "scenes/career_map_glow.png")
