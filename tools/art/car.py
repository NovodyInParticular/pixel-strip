"""Layered side-view car sprites, built from a profile (see roster.py).

Profiles are authored in car-local units (about 18 per metre): x from the rear bumper (0) to the
front, y down with the ground at y = GROUND. The sprite is drawn S times finer than that, so every
shape and threshold below is in profile units and goes through to_px / unit_x / unit_y; only
deliberately pixel-sized details (seams, glints, rivets) count actual pixels. Output layers share
one canvas (car + padding) so the game can stack and rotate them:

  body.png    paint layer, not colours: R = ramp index * 32 + 16 (0..7), G = shine mask
              (where the paint shop's sweep shows), A = coverage. A shader maps R through a paint ramp.
  body_n.png  normal map for the 2D lighting (x right, y up, z toward the viewer).
  popup.png   raised pop-up headlamp housings (paint layer like body.png), drawn with the lamps on.
  trim.png    true colours: glass (and the cabin through it), chrome, rubber, grille, vents,
              lamp lenses, mirrors, handles, pipes.
  lights.png  emissive pixels (head/tail lamps). Drawn lit at night and into the bloom buffer.
  wells.png   wheel-well interiors with the brake disc and caliper, drawn behind the wheels (they
              show between open spokes and don't turn with them).
  shadow.png  the soft contact shadow under the car, drawn first.
  wheel.png   strip of wheel frames: 4 rotation steps across one spoke period + 3 motion-blur frames.
  sprite.json anchors for the game (wheel centres, lamps, exhaust, ground row, nose), in pixels.
"""
import json
import math
import os

import numpy as np

from common import ASSETS, P, PALETTE, TRANSPARENT, bayer, canvas, disc_mask, edges, ellipse_mask, poly_mask, put, save

S = 1.5                       # pixels per profile unit (~27 px/m)
PAD_X, PAD_Y = 4, 4
CH = int(math.ceil(32 * S)) + PAD_Y * 2      # canvas height: 56
GROUND = 31.5                 # ground row in profile units
ROT_FRAMES, BLUR_FRAMES = 4, 3


def width_of(p):
    return int(math.ceil(p["length"] * S)) + PAD_X * 2


def px_x(x):
    return x * S + PAD_X


def px_y(y):
    return y * S + PAD_Y


def unit_x(x):
    """Profile x at the centre of pixel column x."""
    return (x + 0.5 - PAD_X) / S


def unit_y(y):
    return (y + 0.5 - PAD_Y) / S


def L(pts):
    """Profile points -> canvas pixels."""
    return [(px_x(x), px_y(y)) for x, y in pts]


def _interp_y(pts, x):
    """Linear interpolation of a polyline's y at x (profile units)."""
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        if x0 <= x <= x1:
            return y0 + (y1 - y0) * (x - x0) / max(1e-6, x1 - x0)
    return pts[0][1] if x < pts[0][0] else pts[-1][1]


def _hand_aa(cov, solid):
    """Pixels on the curve edges that are partly covered: softened by one tone."""
    return (cov > 0.2) & (cov < 0.8) & solid


def _glass_union(p, cw):
    m = np.zeros((CH, cw), dtype=np.float32)
    for g in p["glass"]:
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
PHOTO_EXTRAS = {"glass_text"}        # drawn over a photo-coloured car too (stickers the photo doesn't have)


def _tone(s, crease_t, hot, flat, x, y):
    """Paint ramp index (0 dark .. 7 light) for a point on the side, s = 0 at the beltline .. 1 at the
    rocker. The side reflects the world like a mirror bent down: the bright sky on the shoulder, a
    sharp dark horizon line a little under halfway, the darker ground below it, and a thin bounce of
    light just above the sill. Bands are flat paint; only a narrow seam between two bands is dithered,
    so panels stay smooth instead of speckled."""
    if s < 0.035:
        return 7 if hot else 6
    if not flat and abs(s - crease_t) < 0.025:
        return 7 if hot else 6                      # the body line catching the light
    if flat:
        bands = [(0.5, 5), (0.84, 5), (0.93, 4), (1.01, 2)]
    else:
        bands = [(crease_t, 5), (0.42, 5), (0.47, 4), (0.53, 2), (0.80, 3), (0.9, 3), (0.95, 2), (1.01, 1)]
        bands[1] = (0.42, 4 if crease_t > 0.3 else 5)
    seam = 0.03
    for k, (edge, tone) in enumerate(bands):
        if s < edge:
            prev_edge = bands[k - 1][0] if k else 0.0
            # just past the previous band's edge, mix the two tones with an ordered dither
            if k and s - prev_edge < seam and bayer(x, y) > (s - prev_edge) / seam:
                tone = bands[k - 1][1]
            if hot and s < 0.45:
                tone = min(7, tone + 1)             # a light source's glint on the upper side
            return tone
    return 1


PHOTO_SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "photo_src")
_PAL = np.array([c[:3] for cols in PALETTE.values() for c in cols], dtype=np.float32)


def photo_of(p):
    """The car's reference photo at sprite size (tools/art/photo_ref.py cache), or None to draw it procedurally."""
    f = os.path.join(PHOTO_SRC, f"{p['id']}.png")
    if not os.path.exists(f):
        return None
    from PIL import Image
    a = np.asarray(Image.open(f).convert("RGBA"))
    return a if a.shape[:2] == (CH, width_of(p)) else None


def _hsv(rgb):
    r, g, b = (rgb[..., i] / 255.0 for i in range(3))
    mx, mn = np.maximum(np.maximum(r, g), b), np.minimum(np.minimum(r, g), b)
    d = mx - mn
    h = np.zeros_like(mx)
    nz = d > 1e-6
    rm, gm = nz & (mx == r), nz & (mx == g) & (mx != r)
    bm = nz & ~rm & ~gm
    h[rm] = ((g - b)[rm] / d[rm]) % 6
    h[gm] = (b - r)[gm] / d[gm] + 2
    h[bm] = (r - g)[bm] / d[bm] + 4
    return h / 6.0, np.where(mx > 0, d / np.maximum(mx, 1e-6), 0), mx


def photo_split(p, photo, region, body_px, glass_px):
    """Splits the photo inside the car into paint and the rest. Paint gets a ramp index from the photo's own
    light and shade (so any colour still repaints it); glass gets the game's dark glass tones from the photo's
    light and shade (the seats, pillars and frames stay readable, the scenery behind doesn't); everything else
    (lamps, black trim, chrome, grille) keeps the photo's colour, snapped to the palette.
    Returns (paint mask, ramp index, trim RGBA)."""
    rgb = photo[..., :3].astype(np.float32)
    h, sat, v = _hsv(rgb)
    sample = body_px & region & ~glass_px
    ref_s = float(np.median(sat[sample])) if sample.any() else 0.0
    ref_v = float(np.median(v[sample])) if sample.any() else 0.6
    coloured = ref_s > 0.25
    if coloured:
        w = sat[sample]
        ang = h[sample] * 2 * np.pi
        ref_h = (np.arctan2((np.sin(ang) * w).sum(), (np.cos(ang) * w).sum()) / (2 * np.pi)) % 1.0
        dh = np.abs((h - ref_h + 0.5) % 1.0 - 0.5)
        same = (dh < 0.08) & (sat > 0.3 * ref_s)
        off_colour = (sat > 0.45) & (dh > 0.12)            # lamps, markers, badges in another colour
    else:
        same = (sat < 0.25) & (v > 0.5 * ref_v)
        off_colour = (sat > 0.45) & (v > 0.4)               # lamps are bright; a dull tint is just the paint in shadow
    black_trim = v < min(0.16, 0.4 * ref_v)                             # on a dark car, dark is mostly just paint
    if ref_v < 0.4:
        black_trim &= ~body_px                                          # and its sides are all paint
    # the body sides are paint unless a pixel is clearly something else; above the beltline only what matches
    paint = np.where(body_px, ~off_colour & ~black_trim, same | ((v > 0.85) & (sat < 0.3)))
    if ref_v < 0.4:
        paint |= ~off_colour                    # a dark car's pillars and shoulders are its paint, however dark
    paint &= region & ~glass_px
    # the silhouette's own edge is paint: a stray background pixel there would read as a hole
    pad = np.pad(region, 1)
    edge = region & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    paint |= edge & ~glass_px
    lum = rgb @ np.array([0.3, 0.59, 0.11], dtype=np.float32)
    # soften the photo's reflections a little so panels read as panels, not noise
    lp = np.pad(lum, 1, mode="edge")
    lum = (2 * lum + lp[:-2, 1:-1] + lp[2:, 1:-1] + lp[1:-1, :-2] + lp[1:-1, 2:]) / 6
    # the photo's light and shade relative to its own paint: the typical panel sits mid-ramp (4), the darkest
    # reflections go toward 1 and only the real highlights reach the top shades
    ref = paint & body_px if (paint & body_px).any() else paint
    knots = np.percentile(lum[ref], [2, 25, 50, 80, 97, 99.5]) if ref.any() else np.array([0, 60, 120, 180, 230, 250])
    knots = np.maximum.accumulate(knots + np.arange(6) * 1e-3)
    shade = np.interp(lum, knots, [1, 3, 4, 5, 6, 7])
    if ref_v < 0.4:
        # a dark car's photo is mostly reflections: blur them along the panels, keep the light-to-dark fall down the side
        sp = np.pad(shade, ((0, 0), (3, 3)), mode="edge")
        shade = sum(sp[:, i:i + shade.shape[1]] for i in range(7)) / 7
        rows = np.array([np.median(shade[y][paint[y]]) if paint[y].any() else 4.0 for y in range(shade.shape[0])])
        shade = 0.8 * rows[:, None] + 0.2 * shade      # mostly the side's top-to-bottom shading, not the scenery
        shade = 4 + (shade - 4) * 0.65          # a dark car's sky reflections are harsh; any other colour would look dirty
    idx = np.clip(np.round(shade), 1, 7).astype(np.int32)
    trim = canvas(photo.shape[1], CH)
    rest = region & ~paint & ~glass_px
    if rest.any():
        q = rgb[rest]
        near = ((q[:, None, :] - _PAL[None, :, :]) ** 2).sum(2).argmin(1)
        trim[rest, :3] = _PAL[near].astype(np.uint8)
        trim[rest, 3] = 255
    g = region & glass_px
    if g.any():
        gl = lum[g]
        lo, hi = np.percentile(gl, [5, 95])
        t = np.clip((gl - lo) / max(1.0, hi - lo), 0, 1)
        tones = [P("night", 0), P("night", 1), P("night", 2), P("night", 3), P("blue", 1), P("blue", 2)]
        trim[g] = np.array(tones, dtype=np.uint8)[np.minimum(5, (t * 6).astype(int))]
    return paint, idx, trim


def body_layer(p):
    cw = width_of(p)
    body = poly_mask(cw, CH, L(p["body"]))
    ar_px = p["arch_r"] * S
    for cx, cy in (p["rear_wheel"], p["front_wheel"]):
        body = np.minimum(body, 1 - disc_mask(cw, CH, px_x(cx), px_y(cy), ar_px))
    gh = poly_mask(cw, CH, L(p["greenhouse"]))
    glass = _glass_union(p, cw)
    gh = np.minimum(gh, 1 - (glass >= 0.5))
    for cut in p.get("cutouts", []):            # open roofs: see-through, nothing drawn
        c = poly_mask(cw, CH, L(cut))
        gh = np.minimum(gh, 1 - c)
        body = np.minimum(body, 1 - c)
    extras = _paint_extras_mask(p, cw)

    solid_b = body >= 0.5
    solid_g = (gh >= 0.5) & ~solid_b
    solid_e = (extras >= 0.5) & ~solid_b & ~solid_g
    solid = solid_b | solid_g | solid_e
    idx = np.full((CH, cw), -1, dtype=np.int32)
    shine = np.zeros((CH, cw), dtype=np.uint8)

    yr = p["rocker_y"]
    glass_x0 = min(x for g in p["glass"] for x, _ in g) if p["glass"] else 0
    glass_x1 = max(x for g in p["glass"] for x, _ in g) if p["glass"] else p["length"]
    roof_y = min(y for _, y in p["greenhouse"]) + 1.2
    front_end = p["length"]
    flat = bool(p.get("flat_sides"))
    for y in range(CH):
        ly = unit_y(y)
        for x in range(cw):
            lx = unit_x(x)
            if solid_b[y, x]:
                yb = _interp_y(p["beltline"], lx)
                s = (ly - yb) / max(1.0, yr - yb)
                hot = any(abs(lx - (a + b) / 2) < 0.6 for a, b in p["hotspots"]) and s < 0.3
                i = _tone(s, p["crease_t"], hot, flat, x, y)
                shine[y, x] = 255 if s < 0.07 else (220 if s < p["crease_t"] else (160 if s < 0.44 else 90))
                if lx < 2.5 or lx > front_end - 1.8:     # panels turning away at the ends
                    i = max(1, i - 2)
                elif lx < 4.5 or lx > front_end - 3.5:
                    i = max(1, i - 1)
                idx[y, x] = i
            elif solid_g[y, x]:
                if ly < roof_y:
                    # roof: the sky's brightest reflection, with a glint along the leading edge
                    i = 7 if (glass_x0 + 4) < lx < (glass_x0 + 9) else 6
                    shine[y, x] = 255
                elif lx < glass_x0:      # sail / C-pillar: lit at the top, falling off toward the deck
                    t = (ly - roof_y) / max(1.0, (_interp_y(p["beltline"], lx) - roof_y))
                    i = 5 if t < 0.3 else (4 if t < 0.65 else 3)
                    if 0.25 < t < 0.4 and bayer(x, y) > 0.5:
                        i = 4
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

    # Door seams: a dark one-pixel cut with a lit lip on the trailing side, and the door's lower edge
    for sx in p["door_seams"]:
        x = int(px_x(sx))
        for y in range(CH):
            if 0 <= x < cw and solid_b[y, x]:
                yb = _interp_y(p["beltline"], unit_x(x))
                ly = unit_y(y)
                if yb + 1.5 < ly < yr - 1:
                    idx[y, x] = 1
                    if x + 1 < cw and solid_b[y, x + 1] and idx[y, x + 1] < 7:
                        idx[y, x + 1] = min(7, idx[y, x + 1] + 1)

    # Wheel-arch lips: highlight the upper rim of each arch, shadow the inside edge
    lip = (1.0 + p.get("flare", 0.0)) * S
    for cx, cy in (p["rear_wheel"], p["front_wheel"]):
        cxp, cyp = px_x(cx), px_y(cy)
        for y in range(CH):
            for x in range(cw):
                if not solid_b[y, x]:
                    continue
                d = math.hypot(x + 0.5 - cxp, y + 0.5 - cyp)
                if ar_px - 0.3 <= d < ar_px + lip and y + 0.5 < cyp:
                    up = (cyp - (y + 0.5)) / ar_px
                    inner = d < ar_px + 0.9
                    idx[y, x] = (6 if up > 0.55 else 2) if inner else (5 if up > 0.4 else 3)
                    if p.get("flare", 0) > 0 and d > ar_px + 0.8 * S and up > 0.3:
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
                idx[y, x] = 6 if (x % 9 == 0) else 7

    aa = _hand_aa(np.maximum(np.maximum(body, gh), extras), solid)
    for y in range(CH):
        for x in range(cw):
            if aa[y, x]:
                nb = [idx[yy, xx] for yy, xx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1))
                      if 0 <= yy < CH and 0 <= xx < cw and idx[yy, xx] >= 0]
                if nb and y < CH * 0.6:   # only soften the upper curves; the bottom stays crisp
                    idx[y, x] = max(0, max(nb) - 3)

    photo = photo_of(p)
    if photo is not None:
        region = (solid | (glass >= 0.5) | (poly_mask(cw, CH, L(p["windshield"])) >= 0.5)) & (photo[..., 3] > 0)
        for cut in p.get("cutouts", []):
            region &= poly_mask(cw, CH, L(cut)) < 0.5
        paint, pidx, trim = photo_split(p, photo, region, solid_b, (glass >= 0.5) | (poly_mask(cw, CH, L(p["windshield"])) >= 0.5))
        keep_edge = idx == 0                            # the dark bottom edge stays
        idx = np.where(region & ~keep_edge, np.where(paint, pidx, np.maximum(1, idx)), idx)
        idx[region & (idx < 0)] = 1
        p["_photo_trim"] = trim
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
            lx, ly = unit_x(x), unit_y(y)
            if x0 <= lx <= x1 and top <= ly <= base:
                face = lx > x1 - 1.2
                idx[y, x] = 1 if face else (6 if ly < top + 0.8 else 4)
    return _encode(idx, np.zeros((CH, cw), dtype=np.uint8)), idx


# --- Normal map ----------------------------------------------------------------------------

def normal_layer(p, idx):
    cw = width_of(p)
    out = canvas(cw, CH)
    yr = p["rocker_y"]
    gh = poly_mask(cw, CH, L(p["greenhouse"])) >= 0.5
    glass_x0 = min(x for g in p["glass"] for x, _ in g) if p["glass"] else 0
    roof_y = min(y for _, y in p["greenhouse"]) + 1.2
    belt_min = min(y for _, y in p["beltline"])
    ar_px = p["arch_r"] * S
    for y in range(CH):
        for x in range(cw):
            if idx[y, x] < 0:
                continue
            lx, ly = unit_x(x), unit_y(y)
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
                yb = _interp_y(p["beltline"], lx)
                s_ = (ly - yb) / max(1.0, yr - yb)
                if s_ < 0.07:
                    ny = 0.8
                elif abs(s_ - p["crease_t"]) < 0.045:
                    ny = 0.55
                elif s_ < 0.44:
                    ny = 0.22
                elif p.get("flat_sides") and s_ < 0.92:
                    ny = 0.12                              # slab door skin, facing out all the way down
                elif s_ < 0.7:
                    ny = 0.0
                elif s_ < 0.88:
                    ny = -0.35
                else:
                    ny = -0.65
                end = p["length"]
                nx = -0.65 if lx < 3 else (-0.3 if lx < 5 else (0.65 if lx > end - 2 else (0.3 if lx > end - 4 else 0.0)))
            for cx, cy in (p["rear_wheel"], p["front_wheel"]):
                dx, dy = x + 0.5 - px_x(cx), y + 0.5 - px_y(cy)
                d = math.hypot(dx, dy)
                if ar_px - 0.3 <= d < ar_px + 1.2 * S:
                    nx = dx / d * 0.7
                    ny = -dy / d * 0.7
            nz = math.sqrt(max(0.05, 1 - nx * nx - ny * ny))
            out[y, x] = (int((nx * 0.5 + 0.5) * 255), int((ny * 0.5 + 0.5) * 255), int((nz * 0.5 + 0.5) * 255), 255)
    return out


# --- Trim (true colours) --------------------------------------------------------------------

def _poly_pixels(cw, poly):
    m = poly_mask(cw, CH, L(poly)) >= 0.5
    return np.nonzero(m)


def _rect(p_out, x0, y0, x1, y1, col_fn):
    """Fill a profile-unit rectangle; col_fn gets canvas pixel coordinates."""
    for y in range(int(round(px_y(y0))), int(round(px_y(y1)))):
        for x in range(int(round(px_x(x0))), int(round(px_x(x1)))):
            if 0 <= y < CH and 0 <= x < p_out.shape[1]:
                c = col_fn(x, y)
                if c is not None:
                    p_out[y, x] = c


def _rect_px(x0, y0, x1, y1):
    """A profile-unit rectangle's pixel bounds (inclusive start, exclusive end)."""
    return int(round(px_x(x0))), int(round(px_y(y0))), int(round(px_x(x1))), int(round(px_y(y1)))


def trim_layer(p, idx):
    cw = width_of(p)
    out = canvas(cw, CH)
    if p.get("_photo_trim") is not None:
        # drawn from the photo: its glass, lamps, trim and badges are already there
        out[:] = p["_photo_trim"]
        for ex, ey in p["exhaust_tips"]:
            _tip(out, int(px_x(ex)), int(px_y(ey)))
        for e in p.get("extras", []):
            if e["kind"] in PHOTO_EXTRAS:
                _trim_extra(out, cw, idx, e, p)
        return out
    for k, g in enumerate(p["glass"]):
        _glass(out, cw, g, p, tint_dark=(k > 0 and p.get("dark_quarters", False)), main=(g is _main_glass(p)))
    _windshield(out, cw, p["windshield"])
    if p.get("rear_strip"):
        _windshield(out, cw, p["rear_strip"], dim=True)

    # Window surround on the main side glass
    if p["glass"] and p.get("chrome_surround", True):
        main = _main_glass(p)
        m = poly_mask(cw, CH, L(main)) >= 0.5
        ys, _ = np.nonzero(m)
        y0 = ys.min() if len(ys) else 0
        pad = np.pad(m, 1)
        ring = (~m) & (pad[:-2, 1:-1] | pad[2:, 1:-1] | pad[1:-1, :-2] | pad[1:-1, 2:])
        chrome = p.get("bumpers") == "chrome"
        for y, x in zip(*np.nonzero(ring)):
            if idx[y, x] >= 0 and out[y, x, 3] == 0:
                if chrome:
                    out[y, x] = P("steel", 6) if y < y0 + 1 else (P("steel", 4) if y < y0 + 3 else P("steel", 3))
                else:
                    out[y, x] = P("night", 1) if y < y0 + 2 else P("night", 0)

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
                # textured black plastic: a lit top edge, a faint mid crease, grain
                out[y, x] = P("steel", 2) if y == top else (P("steel", 1) if (x * 3 + y) % 7 else P("night", 1))
        else:   # body-colour bumper: just a dark parting line on top
            ys, xs = _poly_pixels(cw, b)
            if len(ys):
                top = ys.min()
                for y, x in zip(ys, xs):
                    if y == top:
                        out[y, x] = P("night", 1)

    if p.get("grille"):
        gx0, gy0, gx1, gy1 = _rect_px(*p["grille"])
        for y in range(gy0, gy1):
            for x in range(gx0, gx1):
                if 0 <= y < CH and 0 <= x < cw:
                    edge = y in (gy0, gy1 - 1) or x in (gx0, gx1 - 1)
                    out[y, x] = P("steel", 4) if edge else (P("steel", 2) if (y - gy0) % 2 == 0 else P("night", 0))

    _lamp_lenses(out, p)

    for (mx, my), ramp in p["markers"]:
        x0, y0 = int(px_x(mx)), int(px_y(my))
        for dx in range(3):
            put(out, x0 + dx, y0, P(ramp, 1) if dx < 2 else P(ramp, 0))
            put(out, x0 + dx, y0 + 1, P(ramp, 0))

    if p.get("handle"):
        hx, hy = p["handle"]
        x0, y0 = int(px_x(hx)), int(px_y(hy))
        dark = style == "black"
        for dx in range(4):
            put(out, x0 + dx, y0, P("steel", 3) if dark else P("steel", 6 if dx < 2 else 5))     # the grip, lit along its top
            put(out, x0 + dx, y0 + 1, P("night", 1) if dark else P("steel", 3))
        put(out, x0 - 1, y0 + 1, P("night", 0))                                                   # the pocket behind it
        put(out, x0 + 4, y0 + 1, P("night", 0))
    if p.get("mirror"):
        _mirror(out, p, style)
    if p.get("gas_cap"):
        gx, gy = p["gas_cap"]
        x0, y0 = int(px_x(gx)), int(px_y(gy))
        for dy in range(3):
            for dx in range(3):
                c = P("steel", 6) if dx + dy == 0 else (P("steel", 2) if dx + dy >= 3 else P("steel", 4))
                put(out, x0 + dx, y0 + dy, c)

    # Rocker trim between the wheels (unless the sills are body colour)
    if p.get("rocker_trim", True):
        rx0 = int(px_x(p["rear_wheel"][0] + p["arch_r"] + 1))
        rx1 = int(px_x(p["front_wheel"][0] - p["arch_r"] - 1))
        ry = int(px_y(p["rocker_y"])) - 1
        for x in range(rx0, rx1):
            if 0 <= ry < CH and idx[ry, x] >= 0:
                out[ry, x] = P("steel", 1)
                out[ry - 1, x] = P("steel", 3) if x % 2 else P("steel", 2)

    for ex, ey in p["exhaust_tips"]:
        _tip(out, int(px_x(ex)), int(px_y(ey)))

    for e in p.get("extras", []):
        _trim_extra(out, cw, idx, e, p)
    return out


def _main_glass(p):
    return max(p["glass"], key=lambda g: max(x for x, _ in g) - min(x for x, _ in g)) if p["glass"] else None


def _tip(out, x, y):
    """A tailpipe seen from the side: a lit rolled lip, the dark bore, the pipe under it."""
    for dx, dy, c in ((0, 0, P("steel", 6)), (1, 0, P("steel", 5)), (2, 0, P("steel", 4)), (0, 1, P("steel", 4)),
                      (1, 1, P("night", 0)), (2, 1, P("steel", 3)), (0, 2, P("steel", 2)), (1, 2, P("steel", 2)), (2, 2, P("steel", 1))):
        put(out, x + dx, y + dy, c)


def _mirror(out, p, style):
    """A door mirror: a housing with a lit top, the glass edge, a short stalk to the door."""
    mx, my = p["mirror"]
    x0, y0 = int(px_x(mx)), int(px_y(my))
    chrome = style == "chrome"
    top, mid, low = (P("steel", 6), P("steel", 5), P("steel", 3)) if chrome else (P("night", 3), P("night", 2), P("night", 1))
    for dx in range(4):
        put(out, x0 + dx, y0, top)
        put(out, x0 + dx, y0 + 1, mid if dx < 3 else P("blue", 3))
        put(out, x0 + dx, y0 + 2, low)
    put(out, x0 + 1, y0 + 3, P("night", 1))
    put(out, x0 + 2, y0 + 3, P("night", 0))


def _lamp_lenses(out, p):
    """Unlit lamps: a tail lamp with a lens rim and segments; a headlamp with a chrome rim and a reflector."""
    tx0, ty0, tx1, ty1 = _rect_px(*p["tail_lamp"])
    tx1 = max(tx1, tx0 + 2)
    for y in range(ty0, ty1):
        for x in range(tx0, tx1):
            rim = y in (ty0, ty1 - 1)
            seg = (y - ty0) % 3 == 2
            put(out, x, y, P("red", 0) if rim else (P("red", 1) if not seg else P("red", 0)))
    hx0, hy0, hx1, hy1 = _rect_px(*p["head_lamp"])
    hx1 = max(hx1, hx0 + 2)
    hy1 = max(hy1, hy0 + 2)
    for y in range(hy0, hy1):
        for x in range(hx0, hx1):
            rim = y in (hy0, hy1 - 1) or x == hx0
            c = P("steel", 6) if (rim and y == hy0) else (P("steel", 3) if rim else (P("steel", 5) if (x + y) % 3 else P("steel", 4)))
            put(out, x, y, c)
    put(out, (hx0 + hx1) // 2, (hy0 + hy1) // 2, P("steel", 6))          # the reflector's bright centre


def _glass(out, cw, poly, p, tint_dark=False, main=False):
    """Side glass: the sky reflected along the top, the dark cabin below, two diagonal glints. The
    main window shows the driver's seat headrest and the top of the steering wheel through it."""
    ys, xs = _poly_pixels(cw, poly)
    if not len(ys):
        return
    y0, y1, xmin, xmax = ys.min(), ys.max(), xs.min(), xs.max()
    for y, x in zip(ys, xs):
        t = (y - y0) / max(1, y1 - y0)
        d = (x - xmin) - (y1 - y) * 1.3
        streak = 9 <= d < 11 or 15 <= d < 16
        if tint_dark:
            col = P("night", 1) if not streak else P("night", 3)
        elif streak:
            col = P("blue", 3) if t < 0.35 else (P("blue", 2) if t < 0.7 else P("night", 3))
        elif t < 0.16:
            col = P("blue", 2)
        elif t < 0.3:
            col = P("blue", 1) if bayer(x, y) > (t - 0.16) / 0.14 else P("night", 3)
        elif t < 0.62:
            col = P("night", 3) if bayer(x, y) > (t - 0.3) / 0.32 else P("night", 2)
        else:
            col = P("night", 1)
        out[y, x] = col
    if main and not tint_dark:
        w = xmax - xmin
        # headrest toward the back of the window, the wheel rim toward the front
        hx, hy = xmin + int(w * 0.3), y0 + int((y1 - y0) * 0.35)
        for dy in range(4):
            for dx in range(4):
                if (dx, dy) not in ((0, 0), (3, 0)) and 0 <= hy + dy <= y1:
                    if out[hy + dy, hx + dx, 3]:
                        out[hy + dy, hx + dx] = P("night", 0) if dy else P("night", 2)
        wx, wy = xmin + int(w * 0.72), y0 + int((y1 - y0) * 0.55)
        for dx in range(-3, 4):
            yy = wy + (abs(dx) * abs(dx)) // 4
            if 0 <= yy < CH and out[yy, wx + dx, 3]:
                out[yy, wx + dx] = P("night", 0)


def _windshield(out, cw, poly, dim=False):
    """The windshield (or rear glass) seen almost edge-on: dark glass with the sky in one thin line
    along its top edge, not a bright band."""
    ys, xs = _poly_pixels(cw, poly)
    if not len(ys):
        return
    top = {}
    for y, x in zip(ys, xs):
        top[x] = min(top.get(x, CH), y)
    for y, x in zip(ys, xs):
        if dim:
            out[y, x] = P("night", 2)
        else:
            out[y, x] = P("night", 3) if y == top[x] else P("night", 2)


def _chrome_bar(out, cw, poly):
    """Chrome reflects the world in bands: a hot sky line on top, the dark horizon, the ground under it."""
    ys, xs = _poly_pixels(cw, poly)
    if not len(ys):
        return
    top, bot = ys.min(), ys.max()
    hgt = max(1, bot - top)
    for y, x in zip(ys, xs):
        t = (y - top) / hgt
        c = P("steel", 6) if t < 0.2 else (P("steel", 5) if t < 0.4 else (P("steel", 1) if t < 0.55 else (P("steel", 3) if t < 0.85 else P("steel", 2))))
        out[y, x] = c
    put(out, xs.min() + 1 + (xs.max() - xs.min()) // 2, top, P("yellow", 2))


def _trim_extra(out, cw, idx, e, p):
    k = e["kind"]
    if k in PAINTED:
        return
    if k in ("vent", "gills", "intake"):
        x0, y0, x1, y1 = _rect_px(*e["rect"])
        slots = e.get("slots", 3)
        for y in range(y0, y1):
            for x in range(x0, x1):
                if not (0 <= x < cw and 0 <= y < CH):
                    continue
                if k == "intake":
                    edge = y == y0
                    out[y, x] = P("night", 2) if edge else (P("night", 0) if (x + y) % 3 else P("night", 1))
                else:
                    w = (x1 - x0) / slots
                    inside = ((x - x0) % w) < w * 0.55
                    out[y, x] = P("night", 0) if inside else (P("steel", 2) if y == y0 else TRANSPARENT)
    elif k in ("scoop", "shaker"):
        x0, y0, x1, y1 = _rect_px(*e["rect"])
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < cw and 0 <= y < CH:
                    top = y == y0
                    front = x == x1 - 1
                    if k == "shaker":
                        out[y, x] = P("night", 0) if front else (P("steel", 2) if top else P("night", 2))
                    else:
                        out[y, x] = P("night", 0) if front else (P("night", 3) if top else P("night", 1))
    elif k == "sidepipe":
        x0, y0, x1, y1 = _rect_px(*e["rect"])
        for y in range(y0, y1):
            for x in range(x0, x1):
                if 0 <= x < cw and 0 <= y < CH:
                    t = (y - y0) / max(1, (y1 - y0))
                    shield = (x // 3) % 3 == 0
                    out[y, x] = P("steel", 6) if t < 0.25 else (P("steel", 5) if t < 0.45 else (P("steel", 3) if t < 0.75 else P("steel", 2)))
                    if shield and 0.2 < t < 0.8:
                        out[y, x] = P("steel", 4)
        put(out, x0 - 1, (y0 + y1) // 2, P("night", 0))
    elif k == "rollbar":
        _poly_line(out, e["pts"], P("steel", 2))
    elif k == "headrest":
        x0, y0, x1, y1 = e["rect"]
        _rect(out, x0, y0, x1, y1, lambda x, y: P("night", 1) if (x + y) % 4 else P("night", 2))
    elif k == "skirt":
        x0, y0, x1, y1 = e["rect"]
        _rect(out, x0, y0, x1, y1, lambda x, y: P("night", 1) if idx[y, x] >= 0 else None)
    elif k == "glass_text":
        # a sticker on the glass: a broken white line of tiny letters
        x0, y0, x1, y1 = e["rect"]
        _rect(out, x0, y0, x1, y1, lambda x, y: P("steel", 6) if x % 3 != 2 else None)
    elif k == "stripe":
        x0, y0, x1, y1 = e["rect"]
        c = P(*e.get("color", ("steel", 6)))
        _rect(out, x0, y0, x1, y1, lambda x, y: c if idx[y, x] >= 0 else None)
    elif k == "fill":
        # a solid trim colour over the body (black pillars and the like)
        x0, y0, x1, y1 = e["rect"]
        c = P(*e.get("color", ("night", 0)))
        _rect(out, x0, y0, x1, y1, lambda x, y: c)
    elif k == "outline":
        # a panel's shut line: a one-pixel ring (fuel doors, hatches)
        c = P(*e.get("color", ("night", 1)))
        xs, ys, xe, ye = _rect_px(*e["rect"])
        xe -= 1
        ye -= 1
        _rect(out, *e["rect"], lambda x, y: c if (y in (ys, ye) or x in (xs, xe)) and idx[y, x] >= 0 else None)
    elif k == "line":
        _poly_line(out, e["pts"], P(*e.get("color", ("night", 1))))


def _poly_line(out, pts, c):
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        n = int(max(abs(bx - ax), abs(by - ay)) * S * 2) + 1
        for i in range(n + 1):
            t = i / n
            put(out, px_x(ax + (bx - ax) * t), px_y(ay + (by - ay) * t), c)


def lights_layer(p):
    cw = width_of(p)
    out = canvas(cw, CH)
    tx0, ty0, tx1, ty1 = _rect_px(*p["tail_lamp"])
    tx1 = max(tx1, tx0 + 2)
    if p.get("tail_style") == "round":
        n = p.get("tail_count", 2)
        h = (ty1 - ty0) / n
        for k in range(n):
            for y in range(int(ty0 + k * h), int(ty0 + (k + 1) * h - 0.5)):
                for x in range(tx0, tx1):
                    put(out, x, y, P("red", 3) if (y + k) % 2 == 0 else P("orange", 1))
    else:
        for y in range(ty0, ty1):
            for x in range(tx0, tx1):
                put(out, x, y, P("red", 3) if (y - ty0) % 3 != 2 else P("orange", 1))
    hx0, hy0, hx1, hy1 = _rect_px(*p["head_lamp"])
    hx1 = max(hx1, hx0 + 2)
    hy1 = max(hy1, hy0 + 2)
    for y in range(hy0, hy1):
        for x in range(hx0, hx1):
            put(out, x, y, P("yellow", 2) if x >= hx1 - 1 or (x + y) % 3 == 0 else P("yellow", 1))
    pu = p.get("popup")
    if pu:
        x0, x1, base, top = pu
        _rect(out, x1 - 1.0, top + 0.5, x1 + 0.2, base, lambda x, y: P("yellow", 2))
    return out


def _open_spokes(p):
    """Whether the car's own rims show what's behind them (so the brake goes in the well)."""
    return p["rim"]["style"] in ("magnum", "fivestar", "sixspoke", "three", "mesh", "split", "turbine")


def wells_layer(p):
    """Dark wheel-well interiors, and behind each wheel a brake disc and a caliper: they show through
    open spokes, and because they're on this layer they stay put while the wheel turns."""
    cw = width_of(p)
    out = canvas(cw, CH)
    body = poly_mask(cw, CH, L(p["body"]))
    top = px_y(min(y for _, y in p["body"]))
    ar_px = p["arch_r"] * S
    for cx, cy in (p["rear_wheel"], p["front_wheel"]):
        cxp, cyp = px_x(cx), px_y(cy)
        well = disc_mask(cw, CH, cxp, cyp, ar_px)
        m = (well >= 0.5) & (body >= 0.5)
        for y, x in zip(*np.nonzero(m)):
            if y > top:
                out[y, x] = P("night", 0) if (y + x) % 3 else P("night", 1)
        r_disc = p["wheel_r"] * S * 0.55
        for y in range(int(cyp - r_disc) - 1, int(cyp + r_disc) + 2):
            for x in range(int(cxp - r_disc) - 1, int(cxp + r_disc) + 2):
                d = math.hypot(x + 0.5 - cxp, y + 0.5 - cyp)
                if d < r_disc and 0 <= y < CH and 0 <= x < cw:
                    lit = (cxp - (x + 0.5)) + (cyp - (y + 0.5)) > 0
                    out[y, x] = P("night", 0) if d < r_disc * 0.3 else (P("steel", 3) if d > r_disc - 1.2 else (P("steel", 2) if lit else P("steel", 1)))
        # caliper: a block hugging the disc's rear-top edge
        cal = p.get("caliper", "red" if p["rim"].get("color") in ("silver", "black") else "steel")
        for y in range(int(cyp - r_disc * 0.9), int(cyp - r_disc * 0.1)):
            for x in range(int(cxp - r_disc * 0.95), int(cxp - r_disc * 0.55)):
                if 0 <= y < CH and 0 <= x < cw:
                    out[y, x] = P(cal, 2) if y == int(cyp - r_disc * 0.9) else P(cal, 1)
    return out


def shadow_layer(p):
    """The contact shadow: a soft dark ellipse under the car, darkest under the tyres."""
    cw = width_of(p)
    out = canvas(cw, CH)
    gy = px_y(GROUND)
    cx = px_x(p["length"] / 2)
    rx = p["length"] * S * 0.52
    soft = ellipse_mask(cw, CH, cx, gy + 0.5, rx, 2.4)
    core = np.zeros_like(soft)
    for wx, _ in (p["rear_wheel"], p["front_wheel"]):
        core = np.maximum(core, ellipse_mask(cw, CH, px_x(wx), gy + 0.3, p["wheel_r"] * S * 1.1, 1.4))
    a = np.clip(soft * 0.55 + core * 0.4, 0, 1)
    out[..., 0], out[..., 1], out[..., 2] = 6, 4, 14
    out[..., 3] = (a * 255).astype(np.uint8)
    return out


# --- Wheels ----------------------------------------------------------------------------------

RIM_COLORS = {
    "chrome": [P("steel", 6), P("steel", 5), P("steel", 4), P("steel", 3)],
    "silver": [P("steel", 5), P("steel", 4), P("steel", 3), P("steel", 2)],
    "white": [P("steel", 6), P("steel", 6), P("steel", 5), P("steel", 4)],
    "gold": [P("yellow", 1), P("yellow", 0), P("orange", 2), P("orange", 1)],
    "black": [P("steel", 3), P("steel", 2), P("night", 2), P("night", 1)],
}

# Between open spokes the wheel is transparent: the brake on the well layer shows through.
GAP = object()


def _wheel_sample(px, py, angle, p):
    """Colour of one wheel at a point (profile units around the centre), rotated by angle."""
    rim = p["rim"]
    r_tire = p["wheel_r"]
    r_rim = r_tire * rim.get("size", 0.66)
    cols = RIM_COLORS[rim.get("color", "chrome")]
    d = math.hypot(px, py)
    if d > r_tire:
        return None
    a = math.atan2(py, px) - angle
    lit = (-px - py) / (d + 1e-6)
    if rim["style"] == "split":
        return _split_sample(px, py, d, a, lit, r_tire, r_tire * rim.get("size", 0.72), rim.get("spokes", 8))
    if d > r_rim + 0.35:
        # Tyre: dark rubber, the sidewall's bulge lit from the top-left, the tread's shoulder darkest
        if d > r_tire - 0.55:
            return P("night", 0)
        if rim.get("letters") and abs(d - (r_tire + r_rim) / 2 - 0.2) < 0.4:
            seg = (a % (2 * math.pi)) / (2 * math.pi) * 32
            if int(seg) % 4 != 3 and math.sin(a) > 0.2:
                return P("steel", 4)
        bulge = 1 - abs((d - r_rim) / (r_tire - r_rim) - 0.45) * 2
        if lit > 0.55 and bulge > 0.3:
            return P("steel", 1)
        if lit > -0.3:
            return P("steel", 0) if bulge > -0.2 else P("night", 1)
        return P("night", 1)
    if d > r_rim - 0.45:                                  # rim lip, polished
        return cols[0] if py < -0.4 else (cols[1] if py < 0.4 else (cols[2] if py < 1.5 else cols[3]))
    style = rim["style"]
    n = rim.get("spokes", 5)
    if d < 0.8:
        return P("yellow", 2) if (px < 0 and py < 0) else cols[1]        # the centre cap
    if d < 1.4:
        return cols[2] if (int(math.degrees(a) // 72) % 2) else cols[1]  # lug nuts around it
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
        return cols[1] if abs(sa2) * d < 0.42 else (GAP if d > 2.2 else cols[3])
    if style == "mesh":                                   # crossing fine spokes
        a1 = ((a + d * 0.25) % period) - period / 2
        a2 = ((a - d * 0.25) % period) - period / 2
        on = abs(a1) * d < 0.3 or abs(a2) * d < 0.3
        return (cols[0] if lit > 0 else cols[2]) if on else GAP
    # spoked styles: magnum (wide), fivestar/sixspoke, three; each spoke lit on its leading edge
    width = {"magnum": 0.75, "three": 1.2}.get(style, 0.48)
    if w < width:
        edge = sa < 0
        return cols[0] if (lit > 0.2 and edge) else (cols[1] if lit > -0.5 else cols[2])
    return GAP if d > 1.9 else cols[3]


# True neutral blacks for the gloss-black wheel: the palette's darks are all tinted blue-purple.
BLACK = [(5, 5, 6, 255), (14, 14, 15, 255), (26, 26, 28, 255), (44, 44, 47, 255), (110, 110, 114, 255)]


def _split_sample(px, py, d, a, lit, r_tire, r_rim, n):
    """Gloss-black spoked wheel (RPF1 style) on a fat black tyre, in neutral blacks (BLACK) rather
    than the palette: the spokes are wedges a shade lighter than the gaps between them, and the
    gloss shows on the lip."""
    if d > r_tire - 0.2:                                  # keep the tyre's outline round, no nubs
        return None
    if d > r_rim + 0.4:                                   # tyre
        if d > r_tire - 0.9:
            return BLACK[0]
        return BLACK[2] if lit > 0.6 else BLACK[1]
    if d > r_rim - 0.6:                                   # lip: gloss black, one bright arc up top
        return BLACK[4] if lit > 0.75 else (BLACK[2] if lit > 0.2 else BLACK[0])
    if d < 1.0:                                           # centre cap
        return BLACK[3]
    period = 2 * math.pi / n
    sa = abs((a % period) - period / 2) / period          # 0 on a spoke's centre line, 0.5 between spokes
    if sa < 0.24:                                         # spoke wedge: black, lit edge toward the light
        return BLACK[3] if lit > 0.3 else BLACK[2]
    return GAP


WHEEL_PALETTE = [P("steel", i) for i in range(7)] + [P("night", 0), P("night", 1), P("night", 2), P("yellow", 2),
                                                     P("yellow", 1), P("yellow", 0), P("orange", 2), P("orange", 1)]


def wheel_size(p):
    s = int(math.ceil(p["wheel_r"] * S * 2)) + 4
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
            spread = ([period * 0.25, period * 0.4, period * 0.55] if p["rim"]["style"] == "split"   # turned in game
                      else [period * 0.5, period, period * 2.5])[f - ROT_FRAMES]
            angles = [spread * k / 12 for k in range(12)]
        for y in range(size):
            for x in range(size):
                samples = []
                for sy in range(sub):
                    for sx in range(sub):
                        ux = (x + (sx + 0.5) / sub - c) / S
                        uy = (y + (sy + 0.5) / sub - c) / S
                        for a in angles:
                            samples.append(_wheel_sample(ux, uy, a, p))
                # the split wheel keeps its own colours when blurred, so a blurred wheel matches a sharp one
                img[y, x] = _mode_gap(samples) if f < ROT_FRAMES or p["rim"]["style"] == "split" else _avg_gap(samples)
        frames.append(img)
    return np.concatenate(frames, axis=1)


def _mode_gap(samples):
    """Most common colour; an open gap between spokes stays see-through."""
    inside = [s for s in samples if s is not None]
    if len(inside) < len(samples) * 0.5:
        return TRANSPARENT
    gaps = sum(1 for s in inside if s is GAP)
    cols = [s for s in inside if s is not GAP]
    if gaps >= len(inside) * 0.5 or not cols:
        return TRANSPARENT
    counts = {}
    for c in cols:
        counts[c] = counts.get(c, 0) + 1
    return max(counts, key=counts.get)


def _avg_gap(samples):
    inside = [s for s in samples if s is not None]
    if len(inside) < len(samples) * 0.5:
        return TRANSPARENT
    cols = [P("night", 1) if s is GAP else s for s in inside]
    avg = [sum(c[i] for c in cols) / len(cols) for i in range(3)]
    best, bd = None, 1e9
    for c in WHEEL_PALETTE:
        dd = sum((c[i] - avg[i]) ** 2 for i in range(3))
        if dd < bd:
            best, bd = c, dd
    return best


# --- Visible upgrades -----------------------------------------------------------------------

def _deck(p):
    """Rear deck: (x range, y) where a wing mounts."""
    return 1.5, 10.0, _interp_y(p["beltline"], 5.0)


def _hood(p):
    """Hood: (x0, x1, function x -> y) from the windshield base to the nose."""
    x0 = p["greenhouse"][-1][0] + 2
    x1 = p["length"] - 4
    return x0, x1, lambda x: _interp_y(p["beltline"], x)


def _paint_shape(p, polys, top_index=6):
    """Paint-index layer from polygons: lit top edge, mid body, dark underside, dark outline."""
    cw = width_of(p)
    m = np.zeros((CH, cw), dtype=np.float32)
    for poly in polys:
        m = np.maximum(m, poly_mask(cw, CH, L(poly)))
    solid = m >= 0.5
    idx = np.full((CH, cw), -1, dtype=np.int32)
    shine = np.zeros((CH, cw), dtype=np.uint8)
    for y, x in zip(*np.nonzero(solid)):
        above = y > 0 and solid[y - 1, x]
        above2 = y > 1 and solid[y - 2, x]
        below = y < CH - 1 and solid[y + 1, x]
        left = x > 0 and solid[y, x - 1]
        right = x < cw - 1 and solid[y, x + 1]
        i = top_index if not above else ((top_index - 1) if not above2 else (1 if not below else 4))
        if not left or not right:
            i = min(i, 2)
        idx[y, x] = i
        shine[y, x] = 200 if not above else 0
    return _encode(idx, shine), idx


def wing_layers(p):
    x0, x1, deck = _deck(p)
    d = GROUND - deck                 # deck height above ground, profile units
    Yh = lambda h: GROUND - h
    out = {}
    out["wing_ducktail"] = [[(0, Yh(d)), (7, Yh(d + 0.2)), (7, Yh(d + 1.0)), (0.5, Yh(d + 1.8))]]
    blade = d + 3.2
    out["wing_gt"] = [[(-0.5, Yh(blade + 1.8)), (12, Yh(blade + 1.4)), (12.5, Yh(blade + 0.2)), (0, Yh(blade - 0.2))],
                      [(2.5, Yh(d)), (3.7, Yh(d)), (3.6, Yh(blade)), (2.4, Yh(blade))],
                      [(9, Yh(d)), (10.2, Yh(d)), (10.1, Yh(blade)), (8.9, Yh(blade))],
                      [(-0.8, Yh(blade + 2.6)), (0.4, Yh(blade + 2.6)), (0.4, Yh(blade - 1)), (-0.8, Yh(blade - 1))]]
    blade = d + 9.0
    out["wing_drag"] = [[(-2, Yh(blade + 2.6)), (11, Yh(blade + 2.0)), (11.5, Yh(blade + 0.4)), (-1.5, Yh(blade - 0.3))],
                        [(1.5, Yh(d - 1)), (2.8, Yh(d - 1)), (2.6, Yh(blade)), (1.3, Yh(blade))],
                        [(7.5, Yh(d - 1)), (8.8, Yh(d - 1)), (8.6, Yh(blade)), (7.3, Yh(blade))],
                        [(-2.4, Yh(blade + 4)), (-1.0, Yh(blade + 4)), (-1.0, Yh(blade - 2)), (-2.4, Yh(blade - 2))]]
    return {k: _paint_shape(p, v)[0] for k, v in out.items()}


def scoop_layer(p):
    hx0, hx1, hy = _hood(p)
    cx = hx0 + (hx1 - hx0) * 0.35
    y = hy(cx)
    return _paint_shape(p, [[(cx - 5, y + 0.3), (cx - 4, y - 2.4), (cx + 4, y - 2.6), (cx + 4.4, y + 0.3)]])[0]


def _arch_ring(cx, cy, r0, r1, lift=0.0, steps=18):
    """Upper half-ring around a wheel arch, from r0 to r1 (a flare's lip)."""
    outer = [(cx + r1 * math.cos(math.pi - math.pi * i / steps), cy - r1 * math.sin(math.pi * i / steps) - lift) for i in range(steps + 1)]
    inner = [(cx + r0 * math.cos(math.pi * i / steps), cy - r0 * math.sin(math.pi - math.pi * i / steps)) for i in range(steps + 1)]
    return outer + inner


def _fill(cw, polys, col_fn):
    out = canvas(cw, CH)
    for poly in polys:
        for y, x in zip(*_poly_pixels(cw, poly)):
            c = col_fn(x, y)
            if c is not None:
                out[y, x] = c
    return out


def body_part_layers(p):
    """Body kit pieces, one layer each. Painted parts go through the paint shader (body colour);
    the *_t layers are true-colour details (intakes, splitters, fins, vents, carbon, cage, tint).

    Visual ids match parts.json: bumper1-3, skirts1-2, rear1-2, hood1-2, flares, widebody,
    carbon, cage, chute; tint (window film) is a cosmetic.
    """
    cw = width_of(p)
    rx0 = p["rear_wheel"][0] + p["arch_r"] - 0.5
    rx1 = p["front_wheel"][0] - p["arch_r"] + 0.5
    ry = p["rocker_y"]
    L_ = p["length"]
    paint, trim = {}, {}
    black = lambda x, y: P("night", 0) if (x + y) % 2 else P("night", 1)

    # front bumpers: a lip; a deeper street bumper with an intake; a race splitter with canards
    paint["bumper1"] = [[(L_ - 8, ry - 0.2), (L_ + 0.4, ry - 0.2), (L_ + 0.2, ry + 0.9), (L_ - 8, ry + 0.8)]]
    paint["bumper2"] = [[(L_ - 10, ry - 2.6), (L_ + 1.0, ry - 2.4), (L_ + 1.0, ry + 1.4), (L_ - 10, ry + 1.4)]]
    trim["bumper2"] = _fill(cw, [[(L_ - 5.5, ry - 1.4), (L_ + 0.4, ry - 1.4), (L_ + 0.4, ry + 0.2), (L_ - 5.5, ry + 0.2)]], black)
    paint["bumper3"] = [[(L_ - 11, ry - 2.6), (L_ + 1.2, ry - 2.4), (L_ + 1.2, ry + 1.0), (L_ - 11, ry + 1.0)]]
    trim["bumper3"] = _fill(cw, [[(L_ - 11, ry + 1.0), (L_ + 2.8, ry + 1.0), (L_ + 2.8, ry + 1.9), (L_ - 11, ry + 1.9)],   # splitter
                                 [(L_ - 3.5, ry - 4.4), (L_ + 0.8, ry - 4.0), (L_ + 0.8, ry - 3.3), (L_ - 3.5, ry - 3.6)],  # canard
                                 [(L_ - 5.5, ry - 1.2), (L_ + 0.6, ry - 1.2), (L_ + 0.6, ry + 0.3), (L_ - 5.5, ry + 0.3)]],
                             lambda x, y: P("steel", 1) if (x * 3 + y) % 5 == 0 else P("night", 0))
    # side skirts
    paint["skirts1"] = [[(rx0, ry - 0.6), (rx1, ry - 0.6), (rx1, ry + 1.4), (rx0, ry + 1.4)]]
    paint["skirts2"] = [[(rx0, ry - 0.6), (rx1, ry - 0.6), (rx1, ry + 2.0), (rx0, ry + 2.0)]]
    trim["skirts2"] = _fill(cw, [[(rx0 + 1, ry + 1.4), (rx1 - 1, ry + 1.4), (rx1 - 1, ry + 2.0), (rx0 + 1, ry + 2.0)]], lambda x, y: P("night", 0))
    # rear: a diffuser lip, then a race diffuser with fins
    paint["rear1"] = [[(-0.6, ry - 0.8), (8, ry - 0.8), (8, ry + 1.2), (-0.6, ry + 1.6)]]
    paint["rear2"] = [[(-1.0, ry - 1.0), (8, ry - 1.0), (8, ry + 1.4), (-1.0, ry + 2.0)]]
    trim["rear2"] = _fill(cw, [[(-0.6, ry + 0.8), (7, ry + 0.8), (7, ry + 2.6), (-0.6, ry + 2.6)]],
                          lambda x, y: P("steel", 2) if x % 2 == 0 else P("night", 0))
    # hoods: vented (louvres near the windshield, the turbo glinting through), cowl induction (a bulge facing the glass)
    hx0, hx1, hy = _hood(p)
    vx = hx0 + (hx1 - hx0) * 0.25
    trim["hood1"] = _fill(cw, [[(vx + k * 2.2, hy(vx) - 0.2), (vx + k * 2.2 + 1.2, hy(vx) - 0.2), (vx + k * 2.2 + 1.2, hy(vx) + 0.8), (vx + k * 2.2, hy(vx) + 0.8)]
                               for k in range(4)], lambda x, y: P("orange", 0) if (x + y) % 5 == 0 else P("night", 0))
    cx = hx0 + 1.5
    paint["hood2"] = [[(cx, hy(cx) - 2.2), (cx + 10, hy(cx + 10) - 0.4), (cx + 12, hy(cx + 12) + 0.3), (cx, hy(cx) + 0.3)]]
    # fenders: a flare lip around each arch; the widebody is thicker and runs a bulge along the side
    rw, fw, ar = p["rear_wheel"], p["front_wheel"], p["arch_r"]
    paint["flares"] = [_arch_ring(rw[0], rw[1], ar - 0.4, ar + 1.4), _arch_ring(fw[0], fw[1], ar - 0.4, ar + 1.4)]
    paint["widebody"] = [_arch_ring(rw[0], rw[1], ar - 0.6, ar + 2.6, lift=0.3), _arch_ring(fw[0], fw[1], ar - 0.6, ar + 2.6, lift=0.3),
                         [(rx0 - 1.5, ry - 2.6), (rx1 + 1.5, ry - 2.6), (rx1 + 1.5, ry + 0.8), (rx0 - 1.5, ry + 0.8)]]
    # carbon: a woven dark sheen over the hood and the deck
    weave = lambda x, y: P("night", 2) if (x // 2 + y // 2) % 2 else P("steel", 1)
    deck = _deck(p)
    trim["carbon"] = _fill(cw, [[(hx0, hy(hx0) - 0.2), (hx1, hy(hx1) - 0.2), (hx1, hy(hx1) + 1.2), (hx0, hy(hx0) + 1.2)],
                                [(deck[0], deck[2] - 0.2), (deck[1], deck[2] - 0.2), (deck[1], deck[2] + 1.2), (deck[0], deck[2] + 1.2)]], weave)
    # roll cage: tubes seen through the glass
    cage = canvas(cw, CH)
    for poly in p["glass"]:
        ys, xs = _poly_pixels(cw, poly)
        if len(xs) == 0:
            continue
        x0, x1, y0, y1 = xs.min(), xs.max(), ys.min(), ys.max()
        for y, x in zip(ys, xs):
            diag = abs((x - x0) - (y - y0) * (x1 - x0) / max(1, y1 - y0)) < 1.0
            if x in (x0 + 1, x1 - 1) or diag or y == y0 + 1:
                cage[y, x] = P("steel", 4) if (x + y) % 3 else P("steel", 5)
    trim["cage"] = cage
    # window film: a mask the game darkens to taste
    tint = canvas(cw, CH)
    for poly in p["glass"]:
        for y, x in zip(*_poly_pixels(cw, poly)):
            tint[y, x] = P("night", 0)
    trim["tint"] = tint
    # parachute pack on the rear bumper
    trim["chute"] = _fill(cw, [[(-1.6, ry - 6.5), (2.0, ry - 6.5), (2.0, ry - 3.2), (-1.6, ry - 3.2)]],
                          lambda x, y: P("red", 1) if y % 3 else P("night", 0))
    return {k: _paint_shape(p, v, 5)[0] for k, v in paint.items()}, trim


def trim_extra_layers(p):
    """True-colour parts: blower through the hood, intercooler behind the nose, side pipes, a side
    exit ahead of the rear wheel, and twin tips out the back. Returns the side and rear exhaust spots."""
    cw = width_of(p)
    hx0, hx1, hy = _hood(p)
    cx = hx0 + (hx1 - hx0) * 0.4
    top = hy(cx)
    blower = canvas(cw, CH)
    top_px = int(round(px_y(top - 4.5)))
    _rect(blower, cx - 4, top - 4.5, cx + 4, top + 0.5, lambda x, y: P("steel", 6) if y == top_px else (P("steel", 4) if x % 2 else P("steel", 3)))
    hat_x = int(round(px_x(cx + 3.5))) - 1
    _rect(blower, cx - 3, top - 7.5, cx + 3.5, top - 4.5, lambda x, y: P("night", 1) if x == hat_x else P("steel", 5))   # scoop hat
    _rect(blower, cx - 5, top - 1.5, cx + 5, top + 0.5, lambda x, y: P("steel", 2))
    intercooler = canvas(cw, CH)
    ix = p["length"] - 2.4
    iy = p["rocker_y"] - 2.6
    _rect(intercooler, ix - 1, iy - 3, ix + 2.4, iy + 0.4, lambda x, y: P("steel", 5) if (x + y) % 2 else P("steel", 3))
    side = canvas(cw, CH)
    x0 = p["rear_wheel"][0] + p["arch_r"] + 1
    x1 = p["front_wheel"][0] - p["arch_r"] - 1
    y0 = p["rocker_y"] + 0.6
    _trim_extra(side, cw, np.full((CH, cw), 1), {"kind": "sidepipe", "rect": (x0, y0, x1, y0 + 1.8)}, p)
    # side exit: a short turned-down pipe just ahead of the rear wheel
    exit_ = canvas(cw, CH)
    sx = p["rear_wheel"][0] + p["arch_r"] + 1.5
    sy = p["rocker_y"] + 0.4
    _rect(exit_, sx, sy, sx + 3.2, sy + 1.4, lambda x, y: P("steel", 5) if y == int(round(px_y(sy))) else P("steel", 3))
    _tip(exit_, int(px_x(sx)) - 1, int(px_y(sy)))
    dual = canvas(cw, CH)
    for k, (ex, ey) in enumerate(p["exhaust_tips"][:1] * 2):
        _tip(dual, int(px_x(ex)) - 1, int(px_y(ey)) + k * 3)
    return {"blower": blower, "intercooler": intercooler, "sidepipe": side, "sideexit": exit_, "dualtips": dual}, \
        (x0, y0 + 0.9), (sx, sy + 0.7)


def looks_build(p):
    base = f"cars/{p['id']}"
    for name, img in wing_layers(p).items():
        save(img, f"{base}/{name}.png")
    painted, trims = body_part_layers(p)
    for name, img in painted.items():
        save(img, f"{base}/body_{name}.png")
    for name, img in trims.items():
        save(img, f"{base}/body_{name}_t.png")
    save(scoop_layer(p), f"{base}/scoop.png")
    trims, side_exhaust, side_exit = trim_extra_layers(p)
    for name, img in trims.items():
        save(img, f"{base}/{name}.png")
    return side_exhaust, side_exit


RIM_STYLES = ["magnum", "rally", "fivestar", "mesh", "turbine", "phone", "three", "sixspoke", "mesh_black", "steelie_black", "split_black"]
# name -> (generator style, spokes, colour)
RIM_DEFAULTS = {"magnum": ("magnum", 5, "chrome"), "rally": ("rally", 6, "silver"), "fivestar": ("fivestar", 5, "silver"),
                "mesh": ("mesh", 10, "silver"), "turbine": ("turbine", 10, "silver"), "phone": ("phone", 6, "silver"),
                "three": ("three", 3, "silver"), "sixspoke": ("sixspoke", 6, "silver"),
                "mesh_black": ("mesh", 10, "black"), "steelie_black": ("rally", 6, "black"),
                "split_black": ("split", 8, "black")}


def rim_sheets(radii):
    """Shared wheel sheets: one per (tyre radius, rim style), used by any car with that tyre size."""
    for r in sorted(radii):
        for style in RIM_STYLES:
            gen, spokes, color = RIM_DEFAULTS[style]
            prof = {"wheel_r": r, "rim": {"style": gen, "spokes": spokes, "color": color, "letters": style in ("magnum", "rally")}}
            save(wheel_frames(prof), f"wheels/r{int(round(r * 10))}_{style}.png")


def decal_layers(p, idx):
    """White masks clipped to the body side; the game tints them. stripe, flames, number, checker.
    Also returns the door roundel's centre, where the game draws a crew emblem (the CREW decal)."""
    cw = width_of(p)
    yr = p["rocker_y"]
    body = (idx >= 0)
    gh = poly_mask(cw, CH, L(p["greenhouse"])) >= 0.5
    out = {k: canvas(cw, CH) for k in ("stripe", "flames", "number", "checker")}
    fx = px_x(p["front_wheel"][0])
    rx = px_x(p["rear_wheel"][0])
    num_cx = px_x((p["glass"][0][0][0] + p["glass"][0][-1][0]) / 2) if p["glass"] else (rx + fx) / 2
    num_cy = None
    for y in range(CH):
        ly = unit_y(y)
        for x in range(cw):
            lx = unit_x(x)
            if not body[y, x] or (gh[y, x] and ly < _interp_y(p["beltline"], lx)):
                continue
            yb = _interp_y(p["beltline"], lx)
            s_ = (ly - yb) / max(1.0, yr - yb)
            if 4 < lx < p["length"] - 4 and 0.3 < s_ < 0.42:
                out["stripe"][y, x] = (255, 255, 255, 255)
            # flames: tongues licking back from the front arch
            back = (fx + p["arch_r"] * 0.2 * S - x) / S
            if 0 < back < 42 and 0.28 < s_ < 0.92:
                tongue = math.sin((s_ - 0.28) * 14.0) * 0.5 + 0.5
                reach = 18 + 22 * tongue
                if back < reach and (back < reach - 3 or bayer(x, y) > 0.5):
                    out["flames"][y, x] = (255, 255, 255, 255)
            if s_ > 0.82 and 3 < lx < p["length"] - 3 and ((x // 3) + (y // 3)) % 2 == 0:
                out["checker"][y, x] = (255, 255, 255, 255)
            if num_cy is None and abs(x - num_cx) < 0.6 and 0.45 < s_:
                num_cy = y + 4
    door = (int(num_cx), num_cy) if num_cy is not None else (int((rx + fx) / 2), int(px_y(yr) - 9))
    if num_cy is not None:
        cx = int(num_cx)
        r2 = 38                                       # roundel radius ~6 px
        for y in range(num_cy - 7, num_cy + 8):
            for x in range(cx - 7, cx + 8):
                if 0 <= y < CH and 0 <= x < cw and body[y, x] and (x - cx) ** 2 + (y - num_cy) ** 2 <= r2:
                    out["number"][y, x] = (255, 255, 255, 255)
        # the number itself is cut out of the roundel: a chunky "7"
        seven = [(-3, -3), (-2, -3), (-1, -3), (0, -3), (1, -3), (2, -3), (1, -2), (2, -2), (0, -1), (1, -1), (0, 0), (-1, 1), (0, 1),
                 (-1, 2), (-1, 3)]
        for dx, dy in seven:
            if 0 <= num_cy + dy < CH:
                out["number"][num_cy + dy, cx + dx] = TRANSPARENT
    return out, door


# --- Output ----------------------------------------------------------------------------------

def _driver_anchor(p):
    """Top-left of the driver bust (fx/drivers.png): on the biggest side window, toward its back, sitting low."""
    bust_h = 13
    if not p["glass"]:
        xs = [x for x, _ in p["greenhouse"]]
        return [px_x(min(xs) + 3), px_y(p["beltline"][0][1]) - bust_h]     # roadster: just behind the screen
    main = _main_glass(p)
    x0 = min(x for x, _ in main)
    x1 = max(x for x, _ in main)
    bottom = max(y for _, y in main)
    return [px_x(x0 + (x1 - x0) * 0.38), px_y(bottom) - bust_h + 0.5]


def sprite_json(p, side_exhaust=None, door=None, side_exit=None):
    hx0, hy0, hx1, hy1 = p["head_lamp"]
    tx0, ty0, tx1, ty1 = p["tail_lamp"]
    ex, ey = p["exhaust"]
    return {
        "dir": f"cars/{p['id']}",
        "width": width_of(p),
        "height": CH,
        "scale": S,
        "rearWheel": [px_x(p["rear_wheel"][0]), px_y(p["rear_wheel"][1])],
        "frontWheel": [px_x(p["front_wheel"][0]), px_y(p["front_wheel"][1])],
        "wheelSize": wheel_size(p),
        "wheelFrames": ROT_FRAMES,
        "blurFrames": BLUR_FRAMES,
        "spokes": p["rim"].get("spokes", 5),
        "headlight": [px_x(hx1), px_y((hy0 + hy1) / 2)],
        "taillight": [px_x(tx0), px_y((ty0 + ty1) / 2)],
        "exhaust": [px_x(ex), px_y(ey)],
        "groundY": px_y(GROUND),
        "frontX": px_x(p["length"]),
        "popup": bool(p.get("popup")),
        "driver": _driver_anchor(p),
        "wheelRadius10": int(round(p["wheel_r"] * 10)),
        "sideExhaust": [px_x(side_exhaust[0]), px_y(side_exhaust[1])] if side_exhaust else None,
        "sideExit": [px_x(side_exit[0]), px_y(side_exit[1])] if side_exit else None,
        "door": list(door) if door else None,
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
    shadow = shadow_layer(p)
    base = f"cars/{p['id']}"
    save(body, f"{base}/body.png")
    save(normal_layer(p, nidx), f"{base}/body_n.png")
    if popup is not None:
        save(popup, f"{base}/popup.png")
    save(trim, f"{base}/trim.png")
    save(lights, f"{base}/lights.png")
    save(wells, f"{base}/wells.png")
    save(shadow, f"{base}/shadow.png")
    save(wheels, f"{base}/wheel.png")
    side_exhaust, side_exit = looks_build(p)
    decals, door = decal_layers(p, idx)
    for name, img in decals.items():
        save(img, f"{base}/decal_{name}.png")
    with open(os.path.join(ASSETS, base, "sprite.json"), "w") as f:
        json.dump(sprite_json(p, side_exhaust, door, side_exit), f, indent=2)
    return body, idx, trim, lights, wells, wheels, popup, pidx, shadow


# --- Preview (for checking the art, not used by the game) ----------------------------------

def preview_image(p, layers, ramp, lit=True, bg=None):
    body, idx, trim, lights, wells, wheels, popup, pidx, shadow = layers
    cw = width_of(p)
    img = np.zeros((CH, cw, 4), dtype=np.uint8)
    img[:] = bg if bg is not None else P("night", 2)

    def blit(src):
        m = src[..., 3] > 0
        img[m] = src[m]

    a = shadow[..., 3:4].astype(np.float32) / 255.0
    img[..., :3] = (img[..., :3] * (1 - a) + shadow[..., :3] * a).astype(np.uint8)
    blit(wells)
    ws = wheel_size(p)
    for (cx, cy) in (p["rear_wheel"], p["front_wheel"]):
        fx = int(math.floor(px_x(cx) - ws / 2))
        fy = int(math.floor(px_y(cy) - ws / 2))
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
