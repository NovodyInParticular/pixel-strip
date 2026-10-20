"""Effect sprites: smoke puffs (pixel blobs, lit from above, dithered edges)."""
import math

import numpy as np

from common import P, bayer, canvas, save

PUFF_SIZES = [6, 10, 16, 24, 32]
CELL = 34


def puff(size, seed):
    rng = np.random.default_rng(seed)
    out = canvas(CELL, CELL)
    c = CELL / 2
    lobes = [(c + rng.uniform(-0.25, 0.25) * size, c + rng.uniform(-0.2, 0.2) * size, size * rng.uniform(0.28, 0.42)) for _ in range(4)]
    lobes.append((c, c, size * 0.38))
    for y in range(CELL):
        for x in range(CELL):
            best, bl = 9.0, None
            for lob in lobes:
                d = math.hypot(x + 0.5 - lob[0], y + 0.5 - lob[1]) / lob[2]
                if d < best:
                    best, bl = d, lob
            if best < 1.0 and (best < 0.8 or bayer(x, y) > (best - 0.8) / 0.2):
                # each lobe is a little sphere lit from the top-left: cauliflower billows
                lx, ly, lr = bl
                nx, ny = (x + 0.5 - lx) / lr, (y + 0.5 - ly) / lr
                lit = -ny * 0.8 - nx * 0.35 + (c - (y + 0.5)) / size * 0.6
                if lit > 0.45:
                    col = P("steel", 6)
                elif lit > 0.05:
                    col = P("steel", 5)
                elif lit > -0.3:
                    col = P("steel", 4) if bayer(x, y) > 0.4 else P("steel", 5)
                elif lit > -0.6:
                    col = P("steel", 4)
                else:
                    col = P("steel", 3)
                out[y, x] = col
    return out


def flame(frame):
    """Exhaust flame pointing left (backward), 18x9: white-hot core, orange body, red flicker tips."""
    w, h = 18, 9
    out = canvas(w, h)
    rng = np.random.default_rng(40 + frame)
    length = [14, 17, 12][frame]
    for y in range(h):
        for x in range(w):
            t = (w - 1 - x) / length                      # 0 at the pipe (right), 1 at the tip
            if t > 1:
                continue
            half = (1 - t) * 3.6 + 0.6 + rng.uniform(-0.6, 0.6)
            d = abs(y - (h - 1) / 2)
            if d > half:
                continue
            core = d < half * 0.4 and t < 0.45
            if core:
                col = P("yellow", 2)
            elif t < 0.5:
                col = P("yellow", 0) if d < half * 0.75 else P("orange", 2)
            elif t < 0.8:
                col = P("orange", 1) if bayer(x, y) > t - 0.4 else P("red", 3)
            else:
                col = P("red", 2) if bayer(x, y) > 0.5 else P("red", 3)
            out[y, x] = col
    return out


def nitrous_jet():
    """Purge plume rising from the nose: pale blue-white puff column, 12x30."""
    w, h = 12, 30
    out = canvas(w, h)
    for y in range(h):
        t = 1 - y / (h - 1)                               # 0 at the nozzle (bottom)
        half = 1 + t * 4.5
        for x in range(w):
            d = abs(x + 0.5 - w / 2)
            if d < half and (d < half - 1 or bayer(x, y) > 0.5):
                col = P("steel", 6) if d < half * 0.5 else (P("blue", 5) if t < 0.7 else P("blue", 4))
                if t > 0.85 and bayer(x, y) > (1 - t) * 4:
                    continue
                out[y, x] = col
    return out


def haze():
    """Heat-haze distortion tile: R/G = offset direction around 0.5, A = strength (soft disc)."""
    s = 32
    out = canvas(s, s)
    rng = np.random.default_rng(7)
    phases = rng.uniform(0, 6.28, 4)
    for y in range(s):
        for x in range(s):
            d = math.hypot(x + 0.5 - s / 2, y + 0.5 - s / 2) / (s / 2)
            if d >= 1:
                continue
            ox = math.sin(y * 0.55 + phases[0]) * 0.6 + math.sin(y * 1.3 + x * 0.2 + phases[1]) * 0.4
            oy = math.sin(x * 0.5 + phases[2]) * 0.6 + math.sin(x * 1.1 - y * 0.3 + phases[3]) * 0.4
            out[y, x] = (int(127 + ox * 120), int(127 + oy * 120), 0, int(255 * (1 - d) ** 1.5))
    return out


def build():
    strip = np.concatenate([puff(s, i * 17) for i, s in enumerate(PUFF_SIZES)], axis=1)
    save(strip, "fx/smoke.png")
    save(np.concatenate([flame(i) for i in range(3)], axis=1), "fx/flame.png")
    save(nitrous_jet(), "fx/purge.png")
    save(haze(), "fx/haze.png")
