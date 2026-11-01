"""Rear-view sprites for the cockpit view, traced and coloured from the user's rear photos.

Each car in REARS names its rear photo and a few outline measurements read off it (photo pixels):
the body's outer edges, the roof and the beltline (where the rear glass meets the deck) with their
widths, where the body reaches full width, the bumper's bottom, the ground, the left tyre's outer and
inner edges (the right one is mirrored) and an optional wing. Those make the silhouette mask; the
photo inside it is shrunk to sprite size and split like the side sprites (car.photo_split): paint gets
a ramp index so any paint colour repaints it, glass gets the game's dark glass tones, everything else
(lamps, bumpers, plates, exhausts) keeps the photo's colours snapped to the palette.

  python3 -I tools/art/rear.py cache [ID ...]     shrink the masked photos into photo_src/<id>_rear.png
  python3 -I tools/art/rear.py preview OUT.png     every rear sprite in a few paints, for checking
The build (gen_art.py -> build()) reads only photo_src, never ~/Pictures.

Outputs per car, in assets/cars/<id>/:
  rear.png        true-colour trim and glass (transparent where it's paint)
  rear_paint.png  paint: the ramp index 1..7 in red (index * 32), opaque where it's paint
  rear_lamps.png  the tail lamps, white, for the bloom and the brake lights
  rear.json       {"w", "h", "widthM"}: the sprite's size and the car's real width in metres
"""
import json
import os
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from common import ASSETS, P, PALETTE, canvas, save  # noqa: E402

SRC = HERE / "photo_src"
PXM = 48                         # sprite pixels per metre of car width
PHOTOS = pathlib.Path(os.path.expanduser("~/Pictures/car-refs/PixelRacingCarList"))
_PAL = np.array([c[:3] for cols in PALETTE.values() for c in cols], dtype=np.float32)

# x: body edges; roof/belt: (y, left x, right x); full: y where the body is full width; bump: bumper bottom;
# ground: tyre bottom; tyre: left tyre (outer x, inner x); wing: (top y, bottom y, left x, right x); width_m: real width;
# lamps: the left tail lamps' boxes (x0, y0, x1, y1), mirrored for the right, where the colour alone can't tell lamp from paint
REARS = {
    "kite_ef": dict(photo="1989 Honda Civic DX/1.jpg", x=(211, 793), roof=(50, 342, 658), belt=(196, 247, 766), full=205,
                    bump=390, ground=421, tyre=(253, 332), width_m=1.68),
    "sylph_180": dict(photo="240/3.jpg", x=(140, 1530), roof=(33, 441, 1234), belt=(309, 320, 1356), full=353, bump=870,
                      ground=1102, tyre=(150, 353), width_m=1.69),
    "vortex": dict(photo="Corvvett/2.jpg", x=(366, 1559), roof=(215, 619, 1289), belt=(392, 518, 1390), full=442, bump=884,
                   ground=980, tyre=(371, 518), width_m=1.87),
    "kite_sol": dict(photo="del sol/1.jpg", x=(202, 1014), roof=(166, 355, 861), belt=(300, 300, 916), full=332, bump=640,
                     ground=750, tyre=(202, 300), wing=(253, 308, 324, 900), width_m=1.695),
    "kite": dict(photo="EG/1.jpg", x=(75, 473), roof=(39, 153, 396), belt=(125, 114, 435), full=136, bump=282, ground=332,
                 tyre=(83, 130), width_m=1.695),
    "kite_rx": dict(photo="EK/2.jpg", x=(257, 963), roof=(110, 403, 789), belt=(292, 324, 903), full=300, bump=608, ground=624,
                    tyre=(276, 340), width_m=1.695, lamps=[(268, 332, 342, 442)]),
    "spiral_turbo": dict(photo="Fc/2.jpg", x=(166, 951), roof=(133, 333, 789), belt=(268, 253, 854), full=311, bump=608,
                         ground=680, tyre=(174, 246), wing=(272, 304, 217, 897), width_m=1.69),
    "spiral_r": dict(photo="FD/1.jpg", x=(173, 1091), roof=(182, 387, 884), belt=(295, 312, 943), full=362, bump=724,
                     ground=800, tyre=(173, 269), wing=(278, 354, 269, 985), width_m=1.76),
    "typhoon": dict(photo="GT3000/1.jpg", x=(59, 1989), roof=(67, 539, 1509), belt=(291, 404, 1644), full=377, bump=1105,
                    ground=1280, tyre=(81, 377), wing=(229, 364, 113, 1935), width_m=1.84),
    "arrow": dict(photo="Integra/2.jpg", x=(269, 1005), roof=(216, 446, 834), belt=(328, 387, 909), full=354, bump=648,
                  ground=750, tyre=(269, 362), width_m=1.695),
    "halberd_6": dict(photo="Lancer/1.jpg", x=(163, 644), roof=(102, 253, 547), belt=(195, 226, 584), full=205, bump=421,
                      ground=484, tyre=(166, 232), wing=(124, 200, 224, 582), width_m=1.77),
    "ronin": dict(photo="MR2/3.jpg", x=(148, 1132), roof=(152, 328, 935), belt=(236, 295, 968), full=337, bump=691,
                  ground=792, tyre=(152, 269), wing=(236, 312, 219, 1044), width_m=1.695, lamps=[(168, 391, 455, 472)]),
    "kirin": dict(photo="NSX/2.jpg", x=(424, 1332), roof=(354, 614, 1146), belt=(475, 521, 1204), full=475, bump=799,
                  ground=903, tyre=(428, 556), width_m=1.81),
    "overture": dict(photo="Prelude/3.jpg", x=(202, 1824), roof=(296, 728, 1469), belt=(472, 593, 1576), full=512, bump=1172,
                     ground=1294, tyre=(270, 458), wing=(410, 499, 337, 1663), width_m=1.75, lamps=[(237, 606, 660, 781)]),
    "kaminari_mk1": dict(photo="R32/2.jpg", x=(139, 793), roof=(84, 283, 645), belt=(166, 240, 688), full=209, bump=467,
                         ground=547, tyre=(147, 240), wing=(154, 203, 197, 737), width_m=1.755),
    "kaminari_mk2": dict(photo="R33/2.jpg", x=(249, 1031), roof=(216, 404, 884), belt=(328, 362, 926), full=345, bump=665,
                         ground=766, tyre=(249, 371), wing=(261, 345, 354, 926), width_m=1.78),
    "kaminari_mk3": dict(photo="R34/2.jpg", x=(205, 982), roof=(131, 363, 829), belt=(260, 308, 884), full=276, bump=600,
                         ground=703, tyre=(213, 332), wing=(177, 221, 276, 908), width_m=1.785),
    "prairie": dict(photo="Rx3/3.jpg", x=(538, 1377), roof=(379, 695, 1225), belt=(556, 632, 1294), full=581, bump=897,
                    ground=1048, tyre=(571, 667), width_m=1.6, lamps=[(561, 662, 889, 745)]),
    "sylph_13": dict(photo="s13/2.jpg", x=(150, 1045), roof=(197, 355, 853), belt=(347, 237, 963), full=363, bump=750,
                     ground=853, tyre=(163, 276), width_m=1.69),
    "sylph_15": dict(photo="s15/2.jpg", x=(258, 972), roof=(290, 404, 851), belt=(371, 354, 867), full=413, bump=716,
                     ground=783, tyre=(261, 362), wing=(362, 401, 325, 909), width_m=1.695),
    "tenshi": dict(photo="S2000/2.jpg", x=(307, 1319), roof=(279, 515, 1115), belt=(461, 418, 1201), full=493, bump=879,
                   ground=997, tyre=(311, 455), width_m=1.75),
    "tsunami": dict(photo="Supra/2.jpg", x=(147, 1133), roof=(97, 402, 887), belt=(234, 301, 1004), full=268, bump=661,
                    ground=770, tyre=(151, 301), width_m=1.81, lamps=[(259, 293, 532, 360)]),
    "comet_gd": dict(photo="WRX/3.jpg", x=(152, 581), roof=(63, 246, 488), belt=(145, 203, 531), full=159, bump=343,
                     ground=386, tyre=(159, 222), wing=(82, 105, 184, 551), width_m=1.74),
}


def _masks(r, img_w, img_h):
    """The silhouette (and its body-below-the-belt and glass parts) at photo resolution, as L images."""
    x0, x1 = r["x"]
    cx = (x0 + x1) / 2
    ry, rl, rr = r["roof"]
    by, bl, br = r["belt"]
    w = x1 - x0
    c = 0.05 * w
    sil, body, glass, special = (Image.new("L", (img_w, img_h), 0) for _ in range(4))
    ds, db, dg, dx = ImageDraw.Draw(sil), ImageDraw.Draw(body), ImageDraw.Draw(glass), ImageDraw.Draw(special)
    # cabin: a trapezoid with rounded top corners
    rc = 0.12 * (rr - rl)
    cabin = [(rl + rc, ry), (rr - rc, ry), (rr, ry + rc * 0.6), (br, by), (bl, by), (rl, ry + rc * 0.6)]
    ds.polygon(cabin, fill=255)
    # the rear glass: the cabin less its pillars and the roof's edge
    gi = 0.09 * (br - bl)
    gt = ry + 0.12 * (by - ry)
    dg.polygon([(rl + gi * 0.6, gt), (rr - gi * 0.6, gt), (br - gi, by - 0.04 * (by - ry)), (bl + gi, by - 0.04 * (by - ry))], fill=255)
    # body: from the belt out to full width, then down to the bumper, corners rounded
    f, bb = r["full"], r["bump"]
    shape = [(bl, by), (br, by), (x1 - c, f), (x1, f + c), (x1, bb - c * 1.2), (x1 - c * 1.5, bb), (x0 + c * 1.5, bb),
             (x0, bb - c * 1.2), (x0, f + c), (x0 + c, f)]
    ds.polygon(shape, fill=255)
    db.polygon(shape, fill=255)
    # tyres under the bumper, mirrored about the centre line
    to, ti = r["tyre"]
    tt = bb - 0.06 * (r["ground"] - ry)
    for a, b in ((to, ti), (2 * cx - ti, 2 * cx - to)):
        ds.rounded_rectangle([min(a, b), tt, max(a, b), r["ground"]], radius=0.15 * abs(b - a), fill=255)
    dx.rectangle([x0, bb, x1, r["ground"]], fill=128)              # under the bumper: tyres, exhausts, never paint
    for lx0, ly0, lx1, ly1 in r.get("lamps", []):
        dx.rectangle([lx0, ly0, lx1, ly1], fill=255)
        dx.rectangle([2 * cx - lx1, ly0, 2 * cx - lx0, ly1], fill=255)
    # a wing: the plane and an upright near each end
    if "wing" in r:
        wt, wb, wl, wr = r["wing"]
        ds.rectangle([wl, wt, wr, wt + max(4, 0.35 * (wb - wt))], fill=255)
        uw = 0.05 * (wr - wl)
        for ux in (wl + 0.06 * (wr - wl), wr - 0.06 * (wr - wl) - uw):
            ds.rectangle([ux, wt, ux + uw, max(wb, by)], fill=255)
    return sil, body, glass, special


def cache(ids):
    """Masks and shrinks each car's rear photo into photo_src/<id>_rear.png (alpha = silhouette, green = body, blue = glass in a
    second image) so the build never needs the photos."""
    SRC.mkdir(exist_ok=True)
    for cid in ids:
        r = REARS[cid]
        img = Image.open(PHOTOS / r["photo"]).convert("RGB")
        sil, body, glass, special = _masks(r, img.width, img.height)
        x0, x1 = r["x"]
        top = min([r["roof"][0]] + ([r["wing"][0]] if "wing" in r else []))
        box = (int(x0), int(top), int(x1) + 1, int(r["ground"]) + 1)
        tw = round(r["width_m"] * PXM)
        th = round(tw * (box[3] - box[1]) / (box[2] - box[0]))
        rgb = img.crop(box).resize((tw, th), Image.LANCZOS)
        ms = [m.crop(box).resize((tw, th), Image.BOX) for m in (sil, body, glass)]
        ms.append(special.crop(box).resize((tw, th), Image.NEAREST))
        out = np.zeros((th, tw, 4), dtype=np.uint8)
        out[..., :3] = np.asarray(rgb)
        out[..., 3] = np.asarray(ms[0])
        Image.fromarray(out, "RGBA").save(SRC / f"{cid}_rear.png")
        parts = np.zeros((th, tw, 4), dtype=np.uint8)
        parts[..., 0] = np.asarray(ms[3])
        parts[..., 1] = np.asarray(ms[1])
        parts[..., 2] = np.asarray(ms[2])
        parts[..., 3] = 255
        Image.fromarray(parts, "RGBA").save(SRC / f"{cid}_rear_parts.png")
        print(cid, tw, th)


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


def split(photo, region, body_px, glass_px, lamp_box=None, under=None):
    """Paint mask + ramp index, trim RGBA and the lamp mask. Like car.photo_split, tuned for a car seen from behind:
    the tail lamps are found as bright red/amber patches so a red car keeps its lamps."""
    rgb = photo[..., :3].astype(np.float32)
    h, sat, v = _hsv(rgb)
    sample = body_px & region & ~glass_px
    ref_s = float(np.median(sat[sample])) if sample.any() else 0.0
    ref_v = float(np.median(v[sample])) if sample.any() else 0.6
    if lamp_box is None:
        lamp_box = np.zeros(region.shape, bool)
    if under is None:
        under = np.zeros(region.shape, bool)
    red = ((h < 0.07) | (h > 0.93)) & (sat > 0.45)
    amber = (h >= 0.05) & (h < 0.14) & (sat > 0.55) & (v > 0.6)
    bright = (sat < 0.2) & (v > 0.75)                  # plates, chrome, reversing lamps: never paint
    if ref_s > 0.25:
        w = sat[sample]
        ang = h[sample] * 2 * np.pi
        ref_h = (np.arctan2((np.sin(ang) * w).sum(), (np.cos(ang) * w).sum()) / (2 * np.pi)) % 1.0
        dh = np.abs((h - ref_h + 0.5) % 1.0 - 0.5)
        off_colour = (sat > 0.45) & (dh > 0.1)
        same = (dh < 0.08) & (sat > 0.3 * ref_s)
        amber &= dh > 0.06                             # a yellow or orange car's paint isn't a lamp
        red &= dh > 0.06                               # nor a red car's: its lamps come from the boxes
        lamp = (red | amber) & (v > 0.25)
        paint_body = ~off_colour & ~(v < min(0.12, 0.35 * ref_v)) & ~bright
    else:
        same = (sat < 0.25) & (v > 0.5 * ref_v)
        off_colour = (sat > 0.45) & (v > 0.3)
        lamp = (red | amber) & (v > 0.25)
        paint_body = ~off_colour & ~(v < min(0.16, 0.4 * ref_v))
        if ref_v < 0.4:
            paint_body = ~off_colour & ~(v > max(0.55, ref_v + 0.35))      # a dark car: everything but chrome and lamps
    lamp = (lamp & ~lamp_box) | (lamp_box & ((red | amber) | (v > 0.55) | (sat > 0.35)))
    paint = np.where(body_px, paint_body, same) & ~lamp & ~lamp_box & ~under
    paint &= region & ~glass_px
    lum = rgb @ np.array([0.3, 0.59, 0.11], dtype=np.float32)
    lp = np.pad(lum, 1, mode="edge")
    lum = (2 * lum + lp[:-2, 1:-1] + lp[2:, 1:-1] + lp[1:-1, :-2] + lp[1:-1, 2:]) / 6
    ref = paint & body_px if (paint & body_px).any() else paint
    knots = np.percentile(lum[ref], [2, 25, 50, 80, 97, 99.5]) if ref.any() else np.array([0, 60, 120, 180, 230, 250])
    knots = np.maximum.accumulate(knots + np.arange(6) * 1e-3)
    shade = np.interp(lum, knots, [1, 3, 4, 5, 6, 7])
    if ref_v < 0.4:
        sp = np.pad(shade, ((0, 0), (2, 2)), mode="edge")
        shade = sum(sp[:, i:i + shade.shape[1]] for i in range(5)) / 5
        shade = 4 + (shade - 4) * 0.65
    idx = np.clip(np.round(shade), 1, 7).astype(np.int32)
    hgt, wid = region.shape
    trim = canvas(wid, hgt)
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
    return paint, idx, trim, lamp & region & ~glass_px


def sprite(cid):
    photo = np.asarray(Image.open(SRC / f"{cid}_rear.png").convert("RGBA"))
    parts = np.asarray(Image.open(SRC / f"{cid}_rear_parts.png").convert("RGBA"))
    region = photo[..., 3] >= 128
    body_px = parts[..., 1] >= 128
    glass_px = (parts[..., 2] >= 128) & ~body_px
    paint, idx, trim, lamp = split(photo, region, body_px, glass_px, parts[..., 0] >= 200, (parts[..., 0] > 60) & (parts[..., 0] < 200))
    # a one-pixel dark outline round the silhouette keeps it readable against any scenery
    pad = np.pad(region, 1)
    edge = region & ~(pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:])
    lower = edge & ~paint
    trim[lower] = P("night", 0)
    pimg = canvas(region.shape[1], region.shape[0])
    pimg[paint, 0] = (idx[paint] * 32).astype(np.uint8)
    pimg[paint, 3] = 255
    limg = canvas(region.shape[1], region.shape[0])
    limg[lamp] = (255, 255, 255, 255)
    return trim, pimg, limg


def build():
    for cid, r in REARS.items():
        if not (SRC / f"{cid}_rear.png").exists():
            continue
        trim, pimg, limg = sprite(cid)
        save(trim, f"cars/{cid}/rear.png")
        save(pimg, f"cars/{cid}/rear_paint.png")
        save(limg, f"cars/{cid}/rear_lamps.png")
        meta = {"w": int(trim.shape[1]), "h": int(trim.shape[0]), "widthM": r["width_m"]}
        (pathlib.Path(ASSETS) / f"cars/{cid}/rear.json").write_text(json.dumps(meta) + "\n")


def preview(out):
    """Every rear sprite painted in three of the game's paints, scaled up, on a mid-grey road."""
    ramps = np.asarray(Image.open(os.path.join(ASSETS, "paint/ramps.png")).convert("RGBA"))
    tiles = []
    for cid in REARS:
        trim, pimg, _ = sprite(cid)
        row = []
        for paint_row in (None, 2, 12):
            t = np.zeros_like(trim)
            t[...] = (70, 70, 84, 255)
            m = pimg[..., 3] > 0
            if paint_row is None:
                src = np.asarray(Image.open(SRC / f"{cid}_rear.png").convert("RGBA"))
                t[m] = src[m]
            else:
                t[m] = ramps[paint_row, pimg[m, 0] // 32]
            o = trim[..., 3] > 0
            t[o] = trim[o]
            row.append(t)
        tiles.append(np.concatenate(row, axis=1))
    w = max(t.shape[1] for t in tiles)
    cols = 3
    rows = [tiles[i:i + cols] for i in range(0, len(tiles), cols)]
    hh = max(t.shape[0] for t in tiles) + 4
    sheet = np.zeros((hh * len(rows), (w + 6) * cols, 4), dtype=np.uint8)
    sheet[...] = (30, 30, 40, 255)
    for ri, rr in enumerate(rows):
        for ci, t in enumerate(rr):
            sheet[ri * hh:ri * hh + t.shape[0], ci * (w + 6):ci * (w + 6) + t.shape[1]] = t
    Image.fromarray(sheet).resize((sheet.shape[1] * 3, sheet.shape[0] * 3), Image.NEAREST).save(out)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "cache":
        cache(sys.argv[2:] or list(REARS))
    elif cmd == "preview":
        preview(sys.argv[2])
    else:
        print(__doc__)
