"""Shared helpers for the Pixel Strip art generator: palette, canvases, coverage masks, dithering."""
import colorsys
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "assets")

SS = 8  # supersampling factor for coverage masks


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


# The world palette: 48 colours in named ramps, dark to light. Shadows lean purple/blue,
# highlights lean warm yellow. Paint ramps for car bodies are generated separately.
RAMPS = {
    "night":   ["#0b0a16", "#141026", "#1e1838", "#2a204c", "#3b2b63", "#52397a"],
    "blue":    ["#172452", "#1f3a80", "#2b5aae", "#3f86d8", "#73b9f2", "#b9e4ff"],
    "cyan":    ["#145a66", "#1f8f94", "#36c8bf", "#7cf2dc", "#d2fff2"],
    "magenta": ["#4a1650", "#7f2378", "#bf3592", "#ef5fae", "#ff9fcc"],
    "red":     ["#43122a", "#7d1a30", "#c42a38", "#f2533f"],
    "orange":  ["#9a3a22", "#d9652b", "#f99a3e"],
    "yellow":  ["#ffc94f", "#ffe98f", "#fff9dc"],
    "green":   ["#173d36", "#256b4a", "#46a356", "#93d66a"],
    "steel":   ["#22223a", "#36364f", "#4f5070", "#727594", "#a2a6c0", "#d4d7e8", "#f6f7ff"],
    "skin":    ["#3a2226", "#6b3a33", "#a35f48", "#d9946b", "#f4c8a0"],
}
PALETTE = {name: [hexc(c) for c in cols] for name, cols in RAMPS.items()}
assert sum(len(v) for v in PALETTE.values()) == 48, sum(len(v) for v in PALETTE.values())

TRANSPARENT = (0, 0, 0, 0)


def P(name, i):
    """Palette colour: ramp name and index (negative indices count from the light end)."""
    ramp = PALETTE[name]
    return ramp[max(-len(ramp), min(i, len(ramp) - 1))]


def canvas(w, h):
    return np.zeros((h, w, 4), dtype=np.uint8)


def save(arr, rel):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(arr, "RGBA").save(path, optimize=True)
    return path


def put(arr, x, y, col):
    h, w = arr.shape[:2]
    x, y = int(x), int(y)
    if 0 <= x < w and 0 <= y < h and col is not None:
        arr[y, x] = col


def rect(arr, x0, y0, x1, y1, col):
    """Filled rect, inclusive-exclusive, clipped."""
    h, w = arr.shape[:2]
    x0, x1 = max(0, int(x0)), min(w, int(x1))
    y0, y1 = max(0, int(y0)), min(h, int(y1))
    if x0 < x1 and y0 < y1:
        arr[y0:y1, x0:x1] = col


def hline(arr, x0, x1, y, col):
    rect(arr, x0, y, x1, y + 1, col)


def vline(arr, x, y0, y1, col):
    rect(arr, x, y0, x + 1, y1, col)


# --- Coverage masks: shapes drawn at SS x scale, averaged down to 0..1 per pixel ---------

def _mask_canvas(w, h):
    img = Image.new("L", (w * SS, h * SS), 0)
    return img, ImageDraw.Draw(img)


def _down(img, w, h):
    a = np.asarray(img, dtype=np.float32) / 255.0
    return a.reshape(h, SS, w, SS).mean(axis=(1, 3))


def poly_mask(w, h, pts):
    img, d = _mask_canvas(w, h)
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=255)
    return _down(img, w, h)


def ellipse_mask(w, h, cx, cy, rx, ry):
    img, d = _mask_canvas(w, h)
    d.ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=255)
    return _down(img, w, h)


def disc_mask(w, h, cx, cy, r):
    return ellipse_mask(w, h, cx, cy, r, r)


def line_mask(w, h, pts, width):
    img, d = _mask_canvas(w, h)
    d.line([(x * SS, y * SS) for x, y in pts], fill=255, width=max(1, int(width * SS)), joint="curve")
    return _down(img, w, h)


def edges(mask, thresh=0.5):
    """Boolean maps of a solid mask: inside, and inside pixels touching outside on each side."""
    m = mask >= thresh
    pad = np.pad(m, 1)
    up, down = ~pad[:-2, 1:-1] & m, ~pad[2:, 1:-1] & m
    left, right = ~pad[1:-1, :-2] & m, ~pad[1:-1, 2:] & m
    return m, up, down, left, right


# --- Dithering -------------------------------------------------------------------------

BAYER4 = (np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]], dtype=np.float32) + 0.5) / 16.0


def bayer(x, y):
    return BAYER4[y % 4, x % 4]


def dither_ramp(t, x, y, n):
    """Map continuous t in 0..1 to a ramp index 0..n-1 with ordered dithering between steps."""
    v = max(0.0, min(1.0, t)) * (n - 1)
    lo = int(np.floor(v))
    return min(n - 1, lo + (1 if (v - lo) > bayer(x, y) else 0))


def gradient_fill(arr, x0, y0, x1, y1, stops, axis="y"):
    """Dithered gradient through a list of colours (evenly spaced stops)."""
    n = len(stops)
    for y in range(int(y0), int(y1)):
        for x in range(int(x0), int(x1)):
            t = (y - y0) / max(1, (y1 - y0 - 1)) if axis == "y" else (x - x0) / max(1, (x1 - x0 - 1))
            put(arr, x, y, stops[dither_ramp(t, x, y, n)])


# --- Paint ramps: hue-shifted 8-step ramps for car bodies ---------------------------------

def paint_ramp(hue, sat=0.8, n=8, light_lo=0.07, light_hi=0.94, shadow_hue=0.70, hi_hue=0.13, shift=0.22):
    """Dark-to-light ramp. Shadows drift toward shadow_hue (blue/purple), highlights toward hi_hue."""
    out = []
    for i in range(n):
        t = i / (n - 1)
        if t < 0.5:
            h = _lerp_hue(shadow_hue, hue, 1 - (1 - t * 2) * shift)
        else:
            h = _lerp_hue(hue, hi_hue, (t - 0.5) * 2 * shift)
        l = light_lo + (light_hi - light_lo) * (t ** 1.15)
        s = sat * (0.55 + 0.45 * (1 - abs(t - 0.45) * 1.6))
        if t > 0.85:
            s *= 0.6
        r, g, b = colorsys.hls_to_rgb(h % 1.0, l, max(0.0, min(1.0, s)))
        out.append((int(r * 255), int(g * 255), int(b * 255), 255))
    return out


def _lerp_hue(a, b, t):
    d = ((b - a + 0.5) % 1.0) - 0.5
    return a + d * t


def grey_ramp(n=8, cool=0.66):
    out = []
    for i in range(n):
        t = i / (n - 1)
        l = 0.06 + 0.9 * t
        h = cool if t < 0.6 else 0.13
        s = 0.25 if t < 0.6 else 0.15
        r, g, b = colorsys.hls_to_rgb(h, l, s * (1 - abs(t - 0.5)))
        out.append((int(r * 255), int(g * 255), int(b * 255), 255))
    return out


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def nearest_palette(col):
    best, bd = None, 1e9
    for ramp in PALETTE.values():
        for c in ramp:
            d = sum((c[i] - col[i]) ** 2 for i in range(3))
            if d < bd:
                best, bd = c, d
    return best
