"""The first-person cockpit: dashboards per era, steering wheels, shifters, and the
windshield frame (pillars, header, rearview mirror). Everything is 960 wide and drawn bottom-up
over the road; the game draws the live parts on top (needles, LCD bars, shift light, VTEC lamp,
aftermarket gauges) at the spots written to assets/data/cockpits.json.

Styles (a car's "cockpit" in its data, or chosen from its era):
  classic       60s-70s: painted metal, a chrome lip, big round gauges in chrome bezels, a thin wheel
  80s_analog    boxy black plastic, a square binnacle hood, satellite switch pods, square vents
  80s_digital   the same binnacle with an LCD panel (bars and numbers drawn live)
  90s           a soft rounded binnacle, three gauges with the tach in the middle, a leather wheel
  exotic_bare   the F40 kind: bare carbon, a felt dash top, a small binnacle, nothing else
  exotic_wedge  the Countach / Testarossa kind: a long flat wedge, tan leather, rows of square gauges
"""
import json
import math


from common import ASSETS, P, bayer, canvas, put, save

W = 960               # cockpit art width (wider than any screen)
DASH_H = 100          # dash canvas height, drawn from the bottom of the screen
FRAME_H = 270
CX = W // 2           # the driver's line: the wheel and the binnacle are straight ahead of the eyes
CENTRE_DX = 170       # the car's middle (the console) is this far toward the passenger side, at dash depth

STYLES = ["classic", "80s_analog", "80s_digital", "90s", "exotic_bare", "exotic_wedge"]

# Dash surfaces per style: (top, face, low, edge) and trim colours
LOOK = {
    "classic": dict(face=[(46, 40, 52), (62, 54, 70), (80, 70, 90)], trim="chrome", top=(30, 26, 36)),
    "80s_analog": dict(face=[(22, 22, 28), (32, 32, 40), (44, 44, 54)], trim="black", top=(16, 16, 20)),
    "80s_digital": dict(face=[(22, 22, 28), (32, 32, 40), (44, 44, 54)], trim="black", top=(16, 16, 20)),
    "90s": dict(face=[(26, 26, 32), (38, 38, 46), (52, 52, 62)], trim="silver", top=(20, 20, 26)),
    "exotic_bare": dict(face="carbon", trim="none", top=(38, 34, 40)),
    "exotic_wedge": dict(face=[(110, 78, 52), (140, 100, 66), (168, 124, 84)], trim="black", top=(70, 48, 32)),
}


def rgb(c, a=255):
    return (c[0], c[1], c[2], a)


def shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), 255)


def _face_colour(style, x, y, t):
    """The dash face at a point: lit near its top edge, darker toward the floor; carbon weaves."""
    look = LOOK[style]
    if look["face"] == "carbon":
        weave = ((x // 2) + (y // 2)) % 2
        base = (34, 34, 40) if weave else (24, 24, 30)
        return shade(base, 1.25 - 0.45 * t)
    ramp = look["face"]
    i = 2 if t < 0.12 else (1 if t < 0.55 or bayer(x, y) > (t - 0.55) / 0.2 else 0)
    return rgb(ramp[i])


def gauge(out, cx, cy, r, bezel="chrome", face=(12, 12, 16), ticks=10, red_from=0.8, square=False):
    """A round (or square) gauge: a bezel lit from above, a dark face, tick marks, a red zone."""
    for y in range(int(cy - r - 2), int(cy + r + 3)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = max(abs(dx), abs(dy)) if square else math.hypot(dx, dy)
            if d > r + 2:
                continue
            if d > r:
                up = -dy / (r + 1)
                if bezel == "chrome":
                    c = P("steel", 6) if up > 0.5 else (P("steel", 4) if up > -0.2 else P("steel", 2))
                elif bezel == "silver":
                    c = P("steel", 4) if up > 0.3 else P("steel", 2)
                else:
                    c = P("night", 1) if up > 0 else P("night", 0)
                put(out, x, y, c)
            else:
                put(out, x, y, rgb(face))
    # ticks around the lower 3/4 of the dial, the last stretch red
    for k in range(ticks + 1):
        a = math.radians(225 - 270 * k / ticks)
        red = k / ticks >= red_from
        for rr in (r - 1.5, r - 3):
            put(out, cx + math.cos(a) * rr, cy - math.sin(a) * rr, P("red", 3) if red else P("steel", 5))


def dash(style, rhd=False):
    """Drawn for a left-hand-drive car (the console to the right of the driver); [rhd] mirrors everything
    but the gauge faces, so the dials still read the right way round."""
    out = canvas(W, DASH_H)
    look = LOOK[style]
    top_y = 22                                       # the dash's top edge
    # the dash slab: a gentle curve higher in the middle, the door's edge at the far left
    for x in range(W):
        bulge = 6 * math.exp(-((x - CX) / 260) ** 2)
        y0 = int(top_y - bulge)
        for y in range(y0, DASH_H):
            t = (y - y0) / (DASH_H - y0)
            put(out, x, y, _face_colour(style, x, y, t))
        put(out, x, y0, rgb(look["top"]))
        put(out, x, y0 + 1, shade(look["top"], 1.3))
        if look["trim"] == "chrome":
            for dy, c in ((4, P("steel", 6)), (5, P("steel", 4)), (6, P("steel", 2))):
                put(out, x, y0 + dy, c)
    # the door card at the driver's elbow: higher than the dash, a pull handle
    door_x = CX - 250
    for x in range(0, door_x):
        y0 = int(top_y - 10 - (door_x - x) * 0.08)
        for y in range(max(0, y0), DASH_H):
            put(out, x, y, shade(_face_colour(style, x, y, 0.6), 0.75) if y > y0 + 1 else shade(rgb(look["top"]), 1.5))
    for y in range(44, 50):
        for x in range(door_x - 70, door_x - 20):
            put(out, x, y, P("steel", 3) if y == 44 else (12, 12, 16, 255))
    # binnacle hood over the gauges
    if style in ("80s_analog", "80s_digital", "exotic_wedge"):
        x0, x1, y0, y1 = CX - 78, CX + 78, 6, 62
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = y == y0 or x in (x0, x1 - 1)
                put(out, x, y, rgb(look["top"]) if not edge else shade(look["top"], 1.6))
        for y in range(y0 + 5, y1):
            for x in range(x0 + 4, x1 - 4):
                put(out, x, y, (8, 8, 11, 255))
    elif style == "90s":
        for y in range(4, 64):
            for x in range(CX - 84, CX + 84):
                dx = (x - CX) / 84
                top = 4 + 14 * dx * dx
                if y >= top:
                    put(out, x, y, rgb(look["top"]) if y < top + 2 else (10, 10, 14, 255))
    elif style == "exotic_bare":
        for y in range(10, 54):
            for x in range(CX - 56, CX + 56):
                dx = (x - CX) / 56
                if y >= 10 + 8 * dx * dx:
                    put(out, x, y, (34, 30, 36, 255) if y < 13 + 8 * dx * dx else (10, 10, 12, 255))
    else:   # classic: gauges set straight into the painted dash
        pass

    # the passenger side: a glovebox seam
    for x in range(CX + CENTRE_DX + 70, CX + CENTRE_DX + 250):
        put(out, x, 70, (0, 0, 0, 90))
    meta = {"tach": None, "speedo": None, "lcd": None, "small": [], "pod": [CX - 200, 2], "shiftLight": [CX, 4],
            "switches": [CX + 130, 58], "vtec": None}
    if style in ("80s_analog", "80s_digital"):
        _satellite_pods(out)
        _square_vents(out, CX + CENTRE_DX - 30)
    elif style == "90s":
        _round_vents(out, CX + CENTRE_DX - 18)
    elif style == "exotic_bare":
        _rivets(out)
    later = []       # gauges outside the binnacle: drawn after any mirroring, at mirrored spots
    if style == "classic":
        # a chrome radio in the middle of the dash
        for y in range(56, 66):
            for x in range(CX + CENTRE_DX - 23, CX + CENTRE_DX + 23):
                put(out, x, y, P("steel", 5) if y == 56 else (P("steel", 3) if (x // 4) % 2 else P("steel", 2)))
        later = [(CX + 92 + k * 22, 46, 8) for k in range(3)]
    if rhd:
        out = out[:, ::-1].copy()
        meta["pod"][0] = W - meta["pod"][0]
        meta["switches"][0] = W - meta["switches"][0]
        later = [(W - x, y, r) for x, y, r in later]
    for x, y, r in later:
        gauge(out, x, y, r, "chrome", ticks=4, red_from=0.9)
    meta["small"] = [[x, y, r] for x, y, r in later]
    meta["centre"] = W - (CX + CENTRE_DX) if rhd else CX + CENTRE_DX
    if style == "classic":
        gauge(out, CX - 34, 42, 20, "chrome", ticks=8, red_from=0.85)
        gauge(out, CX + 34, 42, 20, "chrome", ticks=12, red_from=2)
        meta.update(tach=[CX - 34, 42, 20], speedo=[CX + 34, 42, 20])
    elif style == "80s_analog":
        gauge(out, CX - 34, 36, 18, "black", ticks=8, red_from=0.85, square=False)
        gauge(out, CX + 34, 36, 18, "black", ticks=12, red_from=2)
        meta.update(tach=[CX - 34, 36, 18], speedo=[CX + 34, 36, 18])
    elif style == "80s_digital":
        x0, y0, x1, y1 = CX - 66, 18, CX + 66, 56
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = y in (y0, y1 - 1) or x in (x0, x1 - 1)
                put(out, x, y, P("night", 2) if edge else ((14, 20, 18, 255) if (x + y) % 2 else (12, 17, 16, 255)))
        meta.update(lcd=[x0 + 3, y0 + 3, x1 - 3, y1 - 3])
    elif style == "90s":
        gauge(out, CX, 38, 22, "silver", ticks=9, red_from=0.8)
        gauge(out, CX - 50, 44, 17, "silver", ticks=12, red_from=2)
        gauge(out, CX + 50, 44, 12, "silver", ticks=4, red_from=0.9)
        meta.update(tach=[CX, 38, 22], speedo=[CX - 50, 44, 17], small=[[CX + 50, 44, 12]])
    elif style == "exotic_bare":
        gauge(out, CX - 26, 34, 15, "black", ticks=8, red_from=0.85)
        gauge(out, CX + 8, 32, 17, "black", ticks=10, red_from=0.8)
        gauge(out, CX + 40, 36, 11, "black", ticks=4, red_from=0.9)
        meta.update(tach=[CX + 8, 32, 17], speedo=[CX - 26, 34, 15], small=[[CX + 40, 36, 11]])
    elif style == "exotic_wedge":
        gauge(out, CX - 30, 34, 16, "black", ticks=8, red_from=0.85, square=True)
        gauge(out, CX + 30, 34, 16, "black", ticks=12, red_from=2, square=True)
        for k in range(4):
            gauge(out, CX - 54 + k * 36, 54, 6, "black", ticks=3, red_from=0.9, square=True)
        meta.update(tach=[CX - 30, 34, 16], speedo=[CX + 30, 34, 16], small=[[CX - 54 + k * 36, 54, 6] for k in range(4)])
    # VTEC: in the top-right corner of the cluster, over the rightmost gauge's shoulder
    dials = [g for g in (meta["tach"], meta["speedo"]) if g] + [g for g in meta["small"] if abs(g[0] - CX) < 90]
    if meta["lcd"]:
        right, top = meta["lcd"][2], meta["lcd"][1] - 10      # on the hood over the LCD, clear of its bars
    else:
        right = max(g[0] + g[2] for g in dials)
        top = min(g[1] - g[2] for g in dials)
    meta["vtec"] = [right - 23, top + 7]
    return out, meta


def _satellite_pods(out):
    """80s Japanese switch pods either side of the binnacle: blocks of square buttons."""
    for side in (-1, 1):
        x0 = CX + side * 96 - (12 if side < 0 else 0)
        for y in range(20, 52):
            for x in range(x0, x0 + 14):
                btn = (y - 22) % 8 < 5 and (x - x0) % 7 < 5 and y > 21
                put(out, x, y, P("steel", 1) if btn else (14, 14, 18, 255))


def _square_vents(out, x0):
    for k in range(2):
        for y in range(40, 54):
            for x in range(x0 + k * 34, x0 + k * 34 + 26):
                slat = (y - 40) % 3 == 0
                put(out, x, y, P("steel", 2) if slat else (10, 10, 12, 255))


def _round_vents(out, cx0):
    for k in range(2):
        cx, cy, r = cx0 + k * 36, 48, 9
        for y in range(cy - r - 1, cy + r + 2):
            for x in range(cx - r - 1, cx + r + 2):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r + 1:
                    put(out, x, y, P("steel", 3) if d > r else ((10, 10, 13, 255) if (y % 3) else P("steel", 1)))


def _rivets(out):
    for x in range(CX - 120, CX + 160, 14):
        put(out, x, 62, P("steel", 4))
        put(out, x + 1, 63, P("steel", 2))


def frame():
    """The windshield's frame: A-pillars leaning in from the dash corners, the roof header, the
    rearview mirror hanging from it (its glass left clear for the game to draw into)."""
    out = canvas(W, FRAME_H)
    header = 18
    for y in range(FRAME_H):
        for x in range(W):
            yy = FRAME_H - 1 - y                         # rows from the bottom
            # pillars: from the dash's ends up and inward to the header's ends
            t = yy / FRAME_H
            left = 150 + 80 * t
            right = W - 150 - 80 * t
            pillar_l = x < left and yy > 60
            pillar_r = x > right and yy > 60
            if y < header or pillar_l or pillar_r:
                edge = (abs(x - left) < 1.5 or abs(x - right) < 1.5 or y == header - 1)
                put(out, x, y, (22, 20, 30, 255) if not edge else (46, 42, 58, 255))
    # rearview mirror: housing and glass
    mx0, mx1, my0, my1 = CX - 44, CX + 44, header + 2, header + 20
    for y in range(header, my0):
        for x in range(CX - 3, CX + 3):
            put(out, x, y, (30, 28, 38, 255))
    for y in range(my0, my1):
        for x in range(mx0, mx1):
            edge = y in (my0, my1 - 1) or x in (mx0, mx1 - 1)
            put(out, x, y, (36, 34, 46, 255) if edge else (0, 0, 0, 0))
    meta = {"mirror": [mx0 + 1, FRAME_H - my1 + 1, mx1 - mx0 - 2, my1 - my0 - 2], "header": header}
    return out, meta


# --- Steering wheels ------------------------------------------------------------------------

WHEELS = {
    # name: radius, rim thickness, spokes (angles from the top, degrees), rim colour, spoke colour, hub radius
    "classic_thin": dict(r=82, thick=3, spokes=[90, 180, 270], rim=[(70, 44, 28), (112, 72, 44), (150, 104, 66)],
                         spoke="chrome", hub=12, ring=True),
    "80s_four": dict(r=74, thick=6, spokes=[100, 165, 195, 260], rim=[(18, 18, 22), (30, 30, 36), (48, 48, 56)],
                     spoke="black", hub=18),
    "90s_three": dict(r=70, thick=7, spokes=[90, 180, 270], rim=[(16, 16, 20), (28, 28, 34), (44, 44, 52)],
                      spoke="black", hub=24),
    "race_suede": dict(r=64, thick=7, spokes=[90, 180, 270], rim=[(20, 18, 24), (32, 30, 38), (44, 42, 52)],
                       spoke="drilled", hub=10, dish=True),
    "deep_dish": dict(r=66, thick=7, spokes=[90, 180, 270], rim=[(14, 14, 18), (26, 26, 32), (40, 40, 48)],
                      spoke="chrome", hub=16, dish=True),
    "drag_small": dict(r=54, thick=5, spokes=[90, 180, 270], rim=[(22, 22, 26), (36, 36, 42), (52, 52, 60)],
                       spoke="drilled", hub=9),
    "exotic_small": dict(r=62, thick=6, spokes=[90, 180, 270], rim=[(30, 22, 18), (48, 36, 30), (70, 54, 44)],
                         spoke="black", hub=14),
}


def steering_wheel(name):
    spec = WHEELS[name]
    r, th = spec["r"], spec["thick"]
    size = (r + th + 4) * 2
    c = size / 2
    out = canvas(size, size)
    for y in range(size):
        for x in range(size):
            dx, dy = x + 0.5 - c, y + 0.5 - c
            d = math.hypot(dx, dy)
            ang = (math.degrees(math.atan2(dx, -dy)) + 360) % 360       # 0 at the top, clockwise
            if r - th <= d <= r:
                # the rim: lit along its top-left, a dark inner edge, stitching on leather
                lit = (-dx - dy) / (d + 1e-6)
                band = (d - (r - th)) / th
                i = 2 if (lit > 0.3 and band > 0.4) else (1 if band > 0.25 else 0)
                col = rgb(spec["rim"][i])
                if name == "race_suede" and int(ang) % 30 == 0 and band > 0.4:
                    col = P("red", 2)                                   # the top-centre stripe and stitches
                put(out, x, y, col)
                continue
            if d < spec["hub"]:
                lit = (-dx - dy) / (d + 1e-6)
                put(out, x, y, (24, 24, 30, 255) if lit < 0.4 else (40, 40, 50, 255))
                continue
            if d < r - th:
                for sa in spec["spokes"]:
                    off = abs(((ang - sa + 180) % 360) - 180)
                    wpx = off * math.pi / 180 * d
                    width = 5 if spec["spoke"] == "chrome" else (8 if spec["spoke"] == "drilled" else 11)
                    if wpx < width:
                        if spec["spoke"] == "chrome":
                            col = P("steel", 6) if wpx < 1.5 else P("steel", 4)
                        elif spec["spoke"] == "drilled":
                            hole = int(d) % 9 < 4 and wpx < 3
                            col = (0, 0, 0, 0) if hole else (P("steel", 4) if wpx < 2 else P("steel", 3))
                        else:
                            col = (34, 34, 40, 255) if wpx < width - 2 else (22, 22, 28, 255)
                        put(out, x, y, col)
                if spec.get("ring") and abs(d - r * 0.45) < 1.2:
                    put(out, x, y, P("steel", 5))                     # the classic horn ring
    return out


# --- Shifters -------------------------------------------------------------------------------

def shifter(kind):
    """kind: tall (classic chrome lever and ball), short (90s leather boot), gated (exposed metal gate),
    sequential (a racing lever)."""
    w, h = 40, 72
    out = canvas(w, h)
    cx = w // 2
    if kind == "gated":
        for y in range(h - 20, h):
            for x in range(4, w - 4):
                slot = ((x - 4) % 10 in (4, 5) and y < h - 6) or (y in (h - 13, h - 12) and 8 < x < w - 8)
                put(out, x, y, (6, 6, 8, 255) if slot else (P("steel", 5) if (x + y) % 5 else P("steel", 4)))
    else:
        for y in range(h - 18, h):                       # boot or console surround
            for x in range(cx - 12 + (y - (h - 18)) // 3, cx + 12 - (y - (h - 18)) // 3 + 1):
                put(out, x, y, (24, 22, 28, 255) if (x + y) % 3 else (34, 32, 40, 255))
    lever_top = {"tall": 10, "short": 30, "gated": 14, "sequential": 18}[kind]
    for y in range(lever_top, h - 10):
        for x in range(cx - 1, cx + 2):
            put(out, x, y, P("steel", 5) if x == cx - 1 else P("steel", 3))
    # the knob
    kr = {"tall": 7, "short": 6, "gated": 6, "sequential": 5}[kind]
    for y in range(lever_top - kr, lever_top + kr):
        for x in range(cx - kr, cx + kr):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - lever_top)
            if (kind == "sequential" and abs(x + 0.5 - cx) < kr - 1) or d < kr:
                lit = (cx - x) + (lever_top - y) > 0
                if kind == "tall":
                    put(out, x, y, (230, 228, 220, 255) if lit else (170, 166, 158, 255))   # a white ball
                else:
                    put(out, x, y, (44, 42, 50, 255) if lit else (22, 20, 26, 255))
    return out, {"knob": [cx, h - lever_top]}


def build():
    meta = {"styles": {}, "wheels": {}, "frame": None}
    meta["stylesRhd"] = {}
    for s in STYLES:
        img, m = dash(s)
        save(img, f"cockpit/dash_{s}.png")
        meta["styles"][s] = m
        img, m = dash(s, rhd=True)
        save(img, f"cockpit/dash_{s}_rhd.png")
        meta["stylesRhd"][s] = m
    fr, fm = frame()
    save(fr, "cockpit/frame.png")
    meta["frame"] = fm
    for name, spec in WHEELS.items():
        save(steering_wheel(name), f"cockpit/wheel_{name}.png")
        meta["wheels"][name] = {"r": spec["r"]}
    meta["shifters"] = {}
    for k in ("tall", "short", "gated", "sequential"):
        img, m = shifter(k)
        save(img, f"cockpit/shifter_{k}.png")
        meta["shifters"][k] = m
    meta.update({"width": W, "dashHeight": DASH_H})
    with open(f"{ASSETS}/data/cockpits.json", "w") as f:
        json.dump(meta, f, indent=1)
