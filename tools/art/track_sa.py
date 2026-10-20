"""San Antonio at dusk: Joe's home track (only his boss race uses it).

The Tower of the Americas on its own slow layer, a downtown skyline with the Tower Life
Building, the Alamo's facade and San Fernando's twin towers, colourful Tex-Mex shopfronts under
papel picado and string lights, the River Walk's limestone arches and umbrellas behind the wall,
iron lamp posts with flower baskets, prickly pear and agave, and a welcome arch over the start.
"""
import math

import numpy as np

from common import ASSETS, P, bayer, canvas, dither_ramp, hline, put, rect, save, vline
from ui import draw_text, text_width
import track_neon as neon
import tracks

ID = "sanantonio"
RNG = np.random.default_rng(210)

LIME = [P("skin", 2), P("skin", 3), P("skin", 4), P("yellow", 2)]      # limestone, shadow to sunlit
PAPEL = [P("magenta", 3), P("cyan", 3), P("yellow", 1), P("green", 3), P("orange", 2), P("red", 3)]


def _r(a, b):
    return int(RNG.integers(a, b))


def tower_of_the_americas():
    """60x176: shaft, observation pod with lit windows, needle with a beacon."""
    w, h = 60, 176
    out = canvas(w, h)
    glow = canvas(w, h)
    cx = w // 2
    for y in range(44, h):                              # shaft: slightly wider at the foot
        t = (y - 44) / (h - 44)
        half = 3.5 + t * 2.5
        for x in range(int(cx - half), int(cx + half) + 1):
            lit = x < cx - half * 0.2
            out[y, x] = P("steel", 5) if lit else (P("steel", 3) if x < cx + half - 1 else P("steel", 2))
        if y % 22 == 0:                                 # construction joints
            hline(out, int(cx - half), int(cx + half) + 1, y, P("steel", 2))
    for i in range(10):                                 # flared base
        hline(out, cx - 6 - i, cx + 7 + i, h - 10 + i, P("steel", 3) if i < 6 else P("steel", 2))
    # the pod: three stacked rings, the middle one a band of glass
    for y in range(28, 46):
        if y < 32:
            half = 13 + (y - 28)
            col = P("steel", 5) if y < 30 else P("steel", 4)
        elif y < 40:
            half = 17
            col = None
        else:
            half = 17 - (y - 40) * 1.4
            col = P("steel", 3)
        for x in range(int(cx - half), int(cx + half) + 1):
            if col is None:
                lit = (x // 2) % 2 == 0
                c = P("yellow", 1) if lit else P("orange", 1)
                if y in (32, 39):
                    c = P("steel", 2)
                out[y, x] = c
                if y not in (32, 39):
                    glow[y, x] = P("orange", 1) if lit else P("orange", 0)
            else:
                out[y, x] = col if x < cx + half - 2 else P("steel", 2)
    # needle and its beacon
    vline(out, cx, 8, 28, P("steel", 4))
    vline(out, cx + 1, 14, 28, P("steel", 2))
    rect(out, cx - 1, 6, cx + 2, 8, P("red", 3))
    rect(glow, cx - 2, 5, cx + 3, 9, P("red", 3))
    return out, glow


def skyline():
    """Downtown silhouettes at dusk, with the stepped neo-gothic Tower Life Building."""
    w, h = 512, 110
    out = canvas(w, h)
    blink = []
    x = 0
    k = 0
    while x < w:
        if k % 5 == 2:                                   # Tower Life: stepped octagonal tower with a pyramid cap
            for step, (sw, sh) in enumerate(((30, 40), (22, 22), (14, 14))):
                bx = x + (30 - sw) // 2
                top = h - 40 - sum(s for _, s in ((30, 40), (22, 22), (14, 14))[:step + 1]) + 40
                rect(out, bx, top, bx + sw, h if step == 0 else top + sh, P("night", 3))
                vline(out, bx, top, top + sh, P("magenta", 0))
            for i in range(10):
                hline(out, x + 10 + i // 2, x + 21 - i // 2, h - 76 - 10 + i, P("night", 3))
            blink.append((x + 15, h - 88))
            bw = 30
        else:
            bw, bh = _r(16, 40), _r(30, 80)
            top = h - bh
            for yy in range(top, h):
                for xx in range(x, min(w, x + bw)):
                    fog = (yy - (h - 50)) / 50
                    col = P("night", 3) if not (fog > 0 and fog > bayer(xx, yy) * 1.2) else P("night", 4)
                    if xx == x + bw - 1:
                        col = P("magenta", 0)
                    out[yy, xx] = col
            for _ in range(bw * bh // 40):
                wx, wy = x + 2 + _r(0, max(1, bw - 4)), top + 3 + _r(0, max(1, bh - 10))
                if (wx + wy) % 2 == 0:
                    put(out, wx, wy, P("yellow", 0) if _r(0, 3) else P("orange", 2))
        x += bw + _r(0, 6)
        k += 1
    return out, blink


def _alamo(out, x, base):
    """The Alamo's facade: limestone, the curved parapet, arched door, upper window, niches."""
    w, hgt = 74, 46
    top = base - hgt
    for y in range(top, base):
        for xx in range(x, x + w):
            t = (xx - x) / w
            out[y, xx] = LIME[2] if t < 0.55 else LIME[1]
            if (xx * 7 + y * 3) % 17 == 0:
                out[y, xx] = LIME[0]                     # worn stone
    # the famous hump: a bell curve rising over the centre, with little scrolls
    cx = x + w // 2
    for xx in range(x + 14, x + w - 14):
        d = (xx - cx) / 23
        rise = int(13 * math.exp(-d * d * 2.2))
        for y in range(top - rise, top):
            out[y, xx] = LIME[2] if xx < cx + 4 else LIME[1]
        put(out, xx, top - rise, LIME[3])
    hline(out, x, x + w, top, LIME[3])
    # pilasters and niches either side of the door
    for px in (x + 8, x + 22, x + w - 26, x + w - 12):
        rect(out, px, top + 6, px + 4, base, LIME[3])
        vline(out, px + 3, top + 6, base, LIME[0])
    for nx in (x + 13, x + w - 21):
        for ny in (top + 10, top + 26):
            rect(out, nx, ny, nx + 6, ny + 10, LIME[0])
            for i in range(3):
                hline(out, nx + i, nx + 6 - i, ny - i, LIME[0])
    # arched door and the window above it
    for y in range(top + 18, base):
        for xx in range(cx - 7, cx + 8):
            r = math.hypot(xx - cx, max(0, top + 24 - y))
            if r < 7.5:
                out[y, xx] = P("night", 1) if xx > cx - 6 else P("night", 2)
    for y in range(top + 4, top + 14):
        for xx in range(cx - 3, cx + 4):
            if math.hypot(xx - cx, max(0, top + 7 - y)) < 3.5:
                out[y, xx] = P("night", 1)
    return w


def _cathedral(out, glow, x, base):
    """San Fernando: twin gothic towers with pointed caps and a rose window."""
    w = 48
    for tx in (x, x + w - 14):
        rect(out, tx, base - 86, tx + 14, base, LIME[1])
        vline(out, tx, base - 86, base, LIME[3])
        for i in range(14):                              # pointed cap
            hline(out, tx + 7 - i // 2, tx + 7 + i // 2 + 1, base - 86 - 14 + i, LIME[0] if i < 13 else LIME[3])
        rect(out, tx + 6, base - 104, tx + 8, base - 100, LIME[3])     # cross
        hline(out, tx + 5, tx + 9, base - 103, LIME[3])
        for wy in range(base - 74, base - 20, 16):
            rect(out, tx + 5, wy, tx + 9, wy + 8, P("night", 1))
    rect(out, x + 14, base - 60, x + w - 14, base, LIME[2])
    for i in range(10):
        hline(out, x + 14 + i, x + w - 14 - i, base - 60 - i, LIME[1])
    cx, cy = x + w // 2, base - 42
    for a in range(0, 360, 30):
        for r in range(1, 6):
            px = cx + math.cos(math.radians(a)) * r
            py = cy + math.sin(math.radians(a)) * r
            put(out, px, py, PAPEL[(a // 30) % len(PAPEL)] if r > 2 else P("yellow", 2))
            put(glow, px, py, P("orange", 1))
    rect(out, cx - 4, base - 20, cx + 4, base, P("night", 1))
    return w


def _shop(out, glow, x, base, color, name):
    w, hgt = text_width(name) + 16, _r(40, 56)
    top = base - hgt
    rect(out, x, top, x + w, base, color)
    hline(out, x, x + w, top, P("steel", 6))
    vline(out, x + w - 1, top, base, P("night", 1))
    # balcony with iron railing on the upper floor
    rect(out, x + 4, top + 8, x + w - 4, top + 18, P("night", 1))
    for bx in range(x + 6, x + w - 6, 8):
        rect(out, bx, top + 9, bx + 4, top + 17, P("yellow", 1))
        rect(glow, bx, top + 9, bx + 4, top + 17, P("orange", 0))
    hline(out, x + 2, x + w - 2, top + 19, P("night", 0))
    for bx in range(x + 2, x + w - 2, 2):
        put(out, bx, top + 20, P("night", 0))
    # painted sign over the door
    sx = x + 8
    rect(out, sx - 2, base - 26, sx + text_width(name) + 2, base - 16, P("night", 1))
    draw_text(out, name, sx, base - 24, P("yellow", 2))
    draw_text(glow, name, sx, base - 24, P("orange", 2))
    rect(out, x + w // 2 - 4, base - 12, x + w // 2 + 4, base, P("night", 0))
    return w


def _papel_picado(out, glow, x0, x1, y):
    """A string of cut-paper flags, sagging between two points, with lights between them."""
    n = 0
    for x in range(x0, x1):
        t = (x - x0) / max(1, x1 - x0)
        sag = int(5 * math.sin(t * math.pi))
        put(out, x, y + sag, P("night", 1))
        if (x - x0) % 6 == 2:
            col = PAPEL[n % len(PAPEL)]
            for fy in range(4):
                for fx in range(4):
                    if not (fy == 3 and fx in (1, 2)):           # notched hem
                        put(out, x + fx - 1, y + sag + 1 + fy, col)
            put(out, x, y + sag + 2, P("night", 1))              # cut-out hole
            n += 1
        if (x - x0) % 6 == 5:
            put(out, x, y + sag + 1, P("yellow", 2))
            put(glow, x, y + sag + 1, P("yellow", 1))


def mid():
    w, h = 768, 160
    out = canvas(w, h)
    glow = canvas(w, h)
    base = h - 40               # stands in front cover the bottom 40 px
    x = 6
    shops = [("TACOS", P("magenta", 2)), ("PANADERIA", P("cyan", 1)), ("CANTINA", P("orange", 1)),
             ("PALETAS", P("green", 2)), ("MARIACHI", P("red", 1)), ("TAMALES", P("yellow", 0))]
    plan = ["alamo", 0, 1, "cathedral", 2, 3, 4, 5]
    for item in plan:
        if item == "alamo":
            x += _alamo(out, x, base) + 10
        elif item == "cathedral":
            x += _cathedral(out, glow, x, base) + 10
        else:
            name, col = shops[item]
            start = x
            x += _shop(out, glow, x, base, col, name) + 4
            _papel_picado(out, glow, start - 2, x + 2, base - 62)
    return out, glow


def river_walk_wall():
    """The River Walk behind the barrier: café umbrellas, a limestone railing, arches over green water."""
    W_, H_ = neon.WALL_W, neon.WALL_H
    out = canvas(W_, H_)
    glow = canvas(W_, H_)
    # umbrellas and potted plants peeking over
    for ux in range(10, W_, 54):
        cols = [P("red", 2), P("steel", 6)] if (ux // 54) % 2 else [P("cyan", 2), P("yellow", 1)]
        for i in range(9):
            for xx in range(ux + 8 - i, ux + 9 + i):
                put(out, xx, 2 + i, cols[((xx - ux) // 3) % 2])
        vline(out, ux + 8, 11, 20, P("steel", 2))
    for px in range(36, W_, 54):
        rect(out, px, 14, px + 6, 20, P("red", 0))
        for i in range(5):
            put(out, px + i + 1, 12 - (i % 2), P("green", 2))
    # limestone railing with balusters
    rect(out, 0, 20, W_, 23, LIME[2])
    hline(out, 0, W_, 20, LIME[3])
    for bx in range(0, W_, 5):
        rect(out, bx + 1, 23, bx + 3, 28, LIME[1])
    rect(out, 0, 28, W_, 30, LIME[1])
    # arches over the river
    for y in range(30, H_):
        for x in range(W_):
            out[y, x] = LIME[1] if (x + y) % 9 else LIME[0]
    for ax in range(4, W_, 32):
        cx = ax + 12
        for y in range(32, H_):
            for x in range(ax, ax + 24):
                if math.hypot(x - cx, max(0, 40 - y)) < 11:
                    # the San Antonio River: green water with lamp sparkle
                    out[y, x] = P("green", 0) if (x + y * 2) % 7 else P("green", 1)
                    if y > H_ - 6 and (x * 3 + y) % 11 == 0:
                        out[y, x] = P("yellow", 1)
                        glow[y, x] = P("orange", 0)
        for x in range(ax, ax + 24):
            for y in range(30, 42):
                if abs(math.hypot(x - cx, 40 - y) - 11.5) < 0.8:
                    put(out, x, y, LIME[3])
    return out, glow


def welcome_arch():
    """A neon arch over the start line."""
    w, h = 140, 60
    out = canvas(w, h)
    glow = canvas(w, h)
    for lx in (4, w - 10):
        rect(out, lx, 18, lx + 6, h, P("night", 1))
        vline(out, lx, 18, h, P("steel", 3))
    for x in range(w):
        y = int(18 - 14 * math.sin(x / (w - 1) * math.pi))
        for d in range(6):
            put(out, x, y + d, P("night", 0) if 0 < d < 5 else P("magenta", 3))
        put(glow, x, y, P("magenta", 3))
        put(glow, x, y + 5, P("magenta", 3))
    t1, t2 = "WELCOME TO", "SAN ANTONIO"
    draw_text(out, t1, (w - text_width(t1)) // 2, 10, P("cyan", 3))
    draw_text(glow, t1, (w - text_width(t1)) // 2, 10, P("cyan", 3))
    draw_text(out, t2, (w - text_width(t2)) // 2, 20, P("yellow", 2))
    draw_text(glow, t2, (w - text_width(t2)) // 2, 20, P("orange", 2))
    return out, glow


def iron_lamps():
    W_, H_ = neon.POLE_W, neon.POLE_H
    out = canvas(W_, H_)
    glow = canvas(W_, H_)
    px = 180
    vline(out, px, 14, H_, P("night", 0))
    vline(out, px + 1, 14, H_, P("night", 1))
    for i in range(10):                                  # scrolled bracket
        put(out, px + 2 + i, 14 - int(3 * math.sin(i / 9 * math.pi)), P("night", 0))
    hx, hy = px + 14, 8
    rect(out, hx - 3, hy, hx + 4, hy + 2, P("night", 0))
    rect(out, hx - 2, hy + 2, hx + 3, hy + 7, P("yellow", 1))
    rect(glow, hx - 3, hy + 1, hx + 4, hy + 8, P("orange", 1))
    rect(out, hx - 1, hy + 7, hx + 2, hy + 8, P("night", 0))
    # hanging flower basket
    rect(out, px - 8, 34, px - 2, 38, P("skin", 1))
    for i in range(7):
        put(out, px - 9 + i, 33 - (i % 2), [P("magenta", 3), P("red", 3), P("yellow", 1)][i % 3])
        put(out, px - 9 + i, 39 + (i % 3), P("green", 2))
    hline(out, px - 6, px, 30, P("night", 1))
    return out, glow


def foreground():
    w, h = 512, 34
    out = canvas(w, h)
    x = 0
    while x < w:
        kind = _r(0, 3)
        if kind == 0:                                    # prickly pear pads
            for (cx, cy, rx, ry) in ((6, h - 6, 5, 4), (12, h - 11, 4, 5), (3, h - 13, 3, 4)):
                for y in range(cy - ry, cy + ry):
                    for xx in range(cx - rx, cx + rx):
                        if ((xx - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 < 1:
                            put(out, x + xx, y, P("green", 1) if xx < cx else P("green", 0))
            put(out, x + 12, h - 16, P("magenta", 3))     # a bloom
        elif kind == 1:                                  # agave
            for i in range(-6, 7):
                for k in range(abs(i) // 2, 9 - abs(i) // 2):
                    put(out, x + 8 + i + (i * k) // 8, h - 1 - k, P("cyan", 0) if i < 0 else P("green", 0))
        else:                                            # bollard
            rect(out, x, h - 12, x + 4, h, P("night", 0))
            hline(out, x, x + 4, h - 12, P("steel", 3))
        x += _r(24, 70)
    return out


def build():
    base = f"tracks/{ID}"
    save(tracks.sky([P("night", 2), P("night", 4), P("magenta", 0), P("magenta", 1), P("magenta", 2), P("orange", 2), P("yellow", 0)]), f"{base}/sky.png")
    s, sg = tracks.sun(16, [P("yellow", 2), P("yellow", 1), P("orange", 2), P("orange", 1)], P("magenta", 3))
    save(s, f"{base}/sun.png")
    save(sg, f"{base}/sun_glow.png")
    save(tracks.clouds(P("magenta", 1), P("orange", 2), count=7), f"{base}/clouds.png")
    t, tg = tower_of_the_americas()
    save(t, f"{base}/tower.png")
    save(tg, f"{base}/tower_glow.png")
    far, blink = skyline()
    save(far, f"{base}/far.png")
    with open(f"{ASSETS}/{base}/blinkers.txt", "w") as f:
        f.write("\n".join(f"{x} {y}" for x, y in blink) + "\n")
    m, mg = mid()
    save(m, f"{base}/mid.png")
    save(mg, f"{base}/mid_glow.png")
    st, stg = tracks.stands(P("red", 1), P("night", 2), P("night", 3), True)
    save(st, f"{base}/stands.png")
    save(stg, f"{base}/stands_glow.png")
    save(neon.crowd(), f"{base}/crowd.png")
    wl, wg = river_walk_wall()
    save(wl, f"{base}/wall.png")
    save(wg, f"{base}/wall_glow.png")
    pl, plg = iron_lamps()
    save(pl, f"{base}/poles.png")
    save(plg, f"{base}/poles_glow.png")
    save(tracks.road(P("night", 2), P("night", 3), P("steel", 3)), f"{base}/road.png")
    save(neon.rubber(), f"{base}/rubber.png")
    save(neon.start_line(), f"{base}/start_line.png")
    save(neon.finish_line(), f"{base}/finish_line.png")
    g, gg = tracks.gantry("FINISH", P("magenta", 3), P("night", 1))
    save(g, f"{base}/finish_gantry.png")
    save(gg, f"{base}/finish_gantry_glow.png")
    a, ag = welcome_arch()
    save(a, f"{base}/welcome.png")
    save(ag, f"{base}/welcome_glow.png")
    save(foreground(), f"{base}/foreground.png")
    save(neon.lut(tracks.grade([0.04, 0.0, 0.03], [0.07, 0.03, -0.05], 1.12, 0.02)), f"{base}/lut.png")
