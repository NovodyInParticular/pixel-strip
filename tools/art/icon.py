"""Launcher icon: a 24x24 synthwave sunset over a strip, scaled by whole pixels per density."""
import os

import numpy as np
from PIL import Image

from common import P, ROOT, bayer, canvas, dither_ramp

DENSITIES = {"mdpi": 2, "hdpi": 3, "xhdpi": 4, "xxhdpi": 6, "xxxhdpi": 8}   # 48..192 px


def icon():
    s = 24
    out = canvas(s, s)
    sky = [P("night", 1), P("night", 3), P("magenta", 0), P("magenta", 1), P("magenta", 2)]
    for y in range(s):
        for x in range(s):
            out[y, x] = sky[dither_ramp(y / 14, x, y, len(sky))] if y < 15 else P("night", 1)
    sun = [P("yellow", 2), P("yellow", 1), P("yellow", 0), P("orange", 2), P("orange", 1), P("red", 3), P("magenta", 2)]
    cx, cy, r = 11.5, 11.5, 7.5
    for y in range(s):
        for x in range(s):
            if (x + 0.5 - cx - 0.5) ** 2 + (y + 0.5 - cy) ** 2 <= r * r and y < 15:
                if y in (9, 11, 13):
                    continue                       # synthwave stripes
                out[y, x] = sun[min(len(sun) - 1, int((y - (cy - r)) / (2 * r) * len(sun)))]
    for y in range(15, s):                         # strip with converging lane lines
        for x in range(s):
            d = (y - 14) / 10
            if abs(x - 11.5) < 0.6 + d * 0.4:
                out[y, x] = P("yellow", 0) if (y % 3) else P("night", 1)
            elif abs(abs(x - 11.5) - (2 + d * 11)) < 0.7:
                out[y, x] = P("cyan", 2)
            elif y == 15:
                out[y, x] = P("magenta", 3)
    return out


def build():
    img = Image.fromarray(icon(), "RGBA")
    for name, k in DENSITIES.items():
        d = os.path.join(ROOT, "android", "src", "main", "res", f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        img.resize((24 * k, 24 * k), Image.NEAREST).save(os.path.join(d, "ic_launcher.png"))
