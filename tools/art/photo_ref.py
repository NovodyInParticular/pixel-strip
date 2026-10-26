"""Side-photo references for drawing the cars (a checking tool, not part of the build).

The photos live outside the repo (~/Pictures/car-refs/PixelRacingCarList/<folder>/<n>.jpg). Each car in
photo_refs.json names its side photo and the pixel centres of its rear and front wheels; with the real
wheelbase that fixes the scale, so the photo can be redrawn in the profile's own space: x in metres
from the rear bumper (nose to the right, like the sprites), h in metres above the ground.

  python3 -I tools/art/photo_ref.py grid ID OUT.png        the photo with a pixel grid, to read wheel centres
  python3 -I tools/art/photo_ref.py metres ID OUT.png      the photo in metres with a 0.1 m grid, to read a profile
  python3 -I tools/art/photo_ref.py overlay ID OUT.png     the same with the car's current profile drawn over it
"""
import json
import math
import os
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
REFS = json.loads((HERE / "photo_refs.json").read_text())
PHOTOS = pathlib.Path(os.path.expanduser(REFS["dir"]))
PPM = 260                       # output pixels per metre


def _photo(ref):
    return Image.open(PHOTOS / ref["folder"] / ref["side"]).convert("RGB")


def grid(ref, out):
    img = _photo(ref)
    s = min(1.0, 1500 / img.width)
    img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    d = ImageDraw.Draw(img)
    step = 50 if img.width / s < 2500 else 100
    for v in range(0, round(img.width / s), step):
        c = (255, 0, 255) if v % (step * 4) == 0 else (0, 255, 255)
        d.line([(v * s, 0), (v * s, img.height)], fill=c, width=1)
        d.text((v * s + 2, 2), str(v), fill=c)
    for v in range(0, round(img.height / s), step):
        c = (255, 0, 255) if v % (step * 4) == 0 else (0, 255, 255)
        d.line([(0, v * s), (img.width, v * s)], fill=c, width=1)
        d.text((2, v * s + 2), str(v), fill=c)
    img.save(out)


def _axle_frame(ref):
    """Photo px of a point given in metres along / above the axle line (x from the guessed rear bumper)."""
    (rx, ry), (fx, fy) = ref["rear"], ref["front"]
    span = math.hypot(fx - rx, fy - ry)
    ex, ey = (fx - rx) / span, (fy - ry) / span       # along the axle line, rear wheel -> front wheel
    s = 1 if ex > 0 else -1                           # a car facing left comes out mirrored
    nx, ny = -ey * s, ex * s                          # towards the ground
    k = span / ref["wheelbase"]                       # photo px per metre

    def at(x, h):
        u, v = (x - ref["rear_axle"]) * k, (ref["tyre"] / 2 - h) * k
        return rx + ex * u + nx * v, ry + ey * u + ny * v
    return at


def rear_overhang(ref):
    """The real rear overhang: the real length less the wheelbase, split like the photo's overhangs."""
    if "ends" not in ref:
        return ref["rear_axle"]
    xr, xf = ref["ends"]
    r, f = ref["rear_axle"] - xr, xf - ref["rear_axle"] - ref["wheelbase"]
    return (ref["length"] - ref["wheelbase"]) * r / (r + f)


def naive_x(ref, x):
    """Metres from the real rear bumper -> metres in the plain photo frame. A photo shrinks the overhangs
    (the bumper tips sit on the centreline, farther from the camera than the wheels), so each overhang
    is stretched back out to the real length."""
    if "ends" not in ref:
        return x
    xr, xf = ref["ends"]
    ra, wb, L = ref["rear_axle"], ref["wheelbase"], ref["length"]
    R = rear_overhang(ref)
    if x < R:
        return ra - (R - x) * (ra - xr) / R
    if x > R + wb:
        return ra + wb + (x - R - wb) * (xf - ra - wb) / (L - wb - R)
    return ra + (x - R)


def to_metres(ref):
    """The photo resampled into profile space; returns (image, length, height)."""
    img = np.asarray(_photo(ref), dtype=np.float32)
    at = _axle_frame(ref)
    length, height = ref["length"], 1.7
    w, h = round((length + 0.4) * PPM), round(height * PPM)
    xs = np.array([naive_x(ref, ox / PPM - 0.2) for ox in range(w)])
    hs = (h - 1 - np.arange(h)) / PPM
    X, H = np.meshgrid(xs, hs)
    px, py = at(X, H)
    x0, y0 = np.floor(px).astype(int), np.floor(py).astype(int)
    ok = (x0 >= 0) & (y0 >= 0) & (x0 < img.shape[1] - 1) & (y0 < img.shape[0] - 1)
    x0c, y0c = np.clip(x0, 0, img.shape[1] - 2), np.clip(y0, 0, img.shape[0] - 2)
    fx, fy = (px - x0)[..., None], (py - y0)[..., None]
    out = (img[y0c, x0c] * (1 - fx) * (1 - fy) + img[y0c, x0c + 1] * fx * (1 - fy)
           + img[y0c + 1, x0c] * (1 - fx) * fy + img[y0c + 1, x0c + 1] * fx * fy)
    out[~ok] = 0
    return Image.fromarray(out.astype(np.uint8)), length, height


def _gridded(img, length, height):
    d = ImageDraw.Draw(img, "RGBA")
    for i in range(-2, int((length + 0.2) * 10) + 1):
        X = round((i / 10 + 0.2) * PPM)
        col = (255, 0, 255, 200) if i % 5 == 0 else (0, 255, 255, 70)
        d.line([(X, 0), (X, img.height)], fill=col)
        if i % 5 == 0:
            d.text((X + 2, 2), f"{i / 10:.1f}", fill=(255, 255, 0, 255))
    for i in range(int(height * 10) + 1):
        Y = img.height - 1 - round(i / 10 * PPM)
        col = (255, 0, 255, 200) if i % 5 == 0 else (0, 255, 255, 70)
        d.line([(0, Y), (img.width, Y)], fill=col)
        d.text((2, Y - 12), f"{i / 10:.1f}", fill=(255, 255, 0, 255))
    return img


def overlay(ref, img, cid):
    sys.path.insert(0, str(HERE))
    import roster
    from car import GROUND
    from real_cars import M
    p = next(q for q in roster.ROSTER if q["id"] == cid)
    d = ImageDraw.Draw(img, "RGBA")

    def P(x, y):                                       # profile units -> output pixels
        return ((x / M + 0.2) * PPM, img.height - 1 - (GROUND - y) / M * PPM)

    def poly(pts, col, close=False, width=2):
        q = [P(x, y) for x, y in pts]
        if close:
            q.append(q[0])
        d.line(q, fill=col, width=width)

    poly(p["body"], (255, 60, 60, 255), close=True)
    poly(p["greenhouse"], (60, 255, 60, 255))
    for g in p["glass"]:
        poly(g, (80, 160, 255, 255), close=True)
    for (cx, cy) in (p["rear_wheel"], p["front_wheel"]):
        x0, y0 = P(cx - p["wheel_r"], cy - p["wheel_r"])
        x1, y1 = P(cx + p["wheel_r"], cy + p["wheel_r"])
        d.ellipse([x0, y0, x1, y1], outline=(255, 255, 0, 255), width=2)
    for key, col in (("head_lamp", (255, 255, 255, 255)), ("tail_lamp", (255, 0, 0, 255))):
        x0, y0, x1, y1 = p[key]
        d.rectangle([P(x0, y0), P(x1, y1)], outline=col, width=1)
    for e in p["extras"]:
        for pts in e.get("polys", []):
            poly(pts, (255, 160, 0, 255), close=True, width=1)


def main():
    mode, cid, out = sys.argv[1:4]
    ref = REFS["cars"][cid]
    if mode == "grid":
        grid(ref, out)
        return
    img, length, height = to_metres(ref)
    img = _gridded(img, length, height)
    d = ImageDraw.Draw(img)
    r = ref["tyre"] / 2 * PPM
    ra = rear_overhang(ref)
    print(f"{cid}: rear axle {ra:.3f} m from the rear bumper")
    for ax in (ra, ra + ref["wheelbase"]):
        cx, cy = (ax + 0.2) * PPM, img.height - 1 - r
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 0))
    if mode == "overlay":
        overlay(ref, img, cid)
    img.save(out)


if __name__ == "__main__":
    main()
