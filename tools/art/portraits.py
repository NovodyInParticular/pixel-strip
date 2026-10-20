"""Rival portraits: 48x48 pixel busts, built from a few parameters.

Light comes from the upper left; a coloured rim light from the right ties each rival to their
tier. Every character is original; boss 1 is drawn to the user's description of their friend.
"""
import math

import numpy as np

from common import P, bayer, canvas, put, save

S = 48
SKIN = {  # dark-to-light ramps drawn from the palette's skin ramp
    "light": [P("skin", 2), P("skin", 3), P("skin", 4), P("yellow", 2)],
    "tan": [P("skin", 1), P("skin", 2), P("skin", 3), P("skin", 4)],
    "brown": [P("skin", 0), P("skin", 1), P("skin", 2), P("skin", 3)],
    "dark": [P("night", 1), P("skin", 0), P("skin", 1), P("skin", 2)],
    "medium": [P("skin", 1), P("skin", 2), P("skin", 3), P("skin", 4)],
}
HAIR = {
    "black": [P("night", 0), P("night", 1), P("night", 2)],
    "brown": [P("skin", 0), P("skin", 1), P("orange", 0)],
    "silver": [P("steel", 3), P("steel", 5), P("steel", 6)],
    "red": [P("red", 0), P("red", 2), P("orange", 1)],
    "blonde": [P("orange", 1), P("yellow", 0), P("yellow", 1)],
    "pink": [P("magenta", 1), P("magenta", 3), P("magenta", 4)],
}


def _ell(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2


def portrait(skin="tan", build=1.0, hair="buzz", hair_color="black", beard=None, eyes="plain", shirt=("night", 2),
             jacket=None, rim=("cyan", 2), bg=("night", 2), cap=None, earring=False, mechanic=None):
    out = canvas(S, S)
    sk = SKIN[skin]
    hc = HAIR[hair_color]
    rimc = P(*rim)
    # background: dark disc with a tier-coloured glow at the edge
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 24, y + 0.5 - 22) / 26
            out[y, x] = P(*bg) if d < 0.82 else (P("night", 1) if d < 0.95 or bayer(x, y) > 0.5 else P("night", 0))
    cx, cy = 24, 21
    head_rx = 9.0 + (build - 1.0) * 2.2
    head_ry = 11.0 + (build - 1.0) * 1.0
    # shoulders and torso
    sh = 15 + build * 6.5
    for y in range(31, S):
        t = (y - 31) / (S - 31)
        half = sh * (0.55 + 0.45 * min(1.0, t * 2.2))
        for x in range(S):
            dx = x + 0.5 - cx
            if abs(dx) < half:
                lit = -dx / half
                c = P(*shirt)
                if jacket and abs(dx) > half * 0.35:
                    c = P(*jacket)
                if lit < -0.55:
                    c = P("night", 0) if c != rimc else c
                if dx > half - 1.6:
                    c = rimc
                out[y, x] = c
    # neck
    nw = 4.0 + build * 2.2
    for y in range(28, 34):
        for x in range(S):
            dx = x + 0.5 - cx
            if abs(dx) < nw:
                out[y, x] = sk[0] if dx > 1 else sk[1]
    # collar shadow
    for x in range(int(cx - nw - 2), int(cx + nw + 2)):
        put(out, x, 33, P("night", 0))
    # ears
    for side in (-1, 1):
        ex = cx + side * (head_rx + 0.3)
        for y in range(cy - 2, cy + 4):
            for x in range(int(ex - 1.6), int(ex + 1.6)):
                if _ell(x + 0.5, y + 0.5, ex, cy + 1, 1.6, 3.0) < 1:
                    out[y, x] = sk[1] if side < 0 else sk[0]
        if earring and side > 0:
            put(out, int(ex), cy + 4, P("yellow", 1))
    # head with 4-step shading lit from the upper left, rim light on the right edge
    for y in range(S):
        for x in range(S):
            e = _ell(x + 0.5, y + 0.5, cx, cy, head_rx, head_ry)
            if e < 1:
                nx = (x + 0.5 - cx) / head_rx
                ny = (y + 0.5 - cy) / head_ry
                lit = -nx * 0.6 - ny * 0.5 + 0.2
                i = 3 if lit > 0.55 else (2 if lit > 0.05 else (1 if lit > -0.45 else 0))
                if i == 2 and bayer(x, y) > 0.7 and lit < 0.15:
                    i = 1
                if nx > 0.82 and e > 0.6:
                    out[y, x] = rimc
                else:
                    out[y, x] = sk[i]
    # beard: jaw and chin, mustache; leaves the cheeks above the jawline
    if beard:
        for y in range(S):
            for x in range(S):
                if _ell(x + 0.5, y + 0.5, cx, cy, head_rx + 0.4, head_ry + 0.6) >= 1:
                    continue
                ny = (y + 0.5 - cy) / head_ry
                nx = (x + 0.5 - cx) / head_rx
                full = beard == "full" and ny > 0.34 - 0.2 * (1 - abs(nx)) and not (abs(nx) < 0.38 and 0.38 < ny < 0.5)
                goatee = beard == "goatee" and abs(nx) < 0.4 and ny > 0.5
                stache = abs(nx) < 0.42 and 0.3 < ny < 0.38
                if full or goatee or (beard in ("full", "goatee") and stache):
                    c = hc[1] if (nx < 0 and ny < 0.75) else hc[0]
                    if bayer(x, y) > 0.85:
                        c = hc[2] if nx < 0 else hc[1]
                    out[y, x] = c
    # mouth (in the gap of a full beard)
    for x in range(cx - 2, cx + 3):
        put(out, x, cy + 6, P("night", 1) if beard else sk[0])
    # nose shadow and highlight
    put(out, cx + 1, cy + 2, sk[0])
    put(out, cx + 1, cy + 3, sk[0])
    put(out, cx - 1, cy + 1, sk[3])
    # eyes / shades / glasses
    ey = cy - 1
    if eyes == "shades":
        for x in range(cx - 7, cx + 8):
            for y in (ey - 1, ey):
                if x != cx:
                    put(out, x, y, P("night", 0))
        put(out, cx, ey - 1, P("steel", 3))                # bridge
        put(out, cx - 5, ey - 1, P("cyan", 3))
        put(out, cx + 3, ey - 1, P("cyan", 3))
    elif eyes == "glasses":
        for ox in (-5, 3):
            for x in range(cx + ox - 1, cx + ox + 4):
                put(out, x, ey - 2, P("steel", 4))
                put(out, x, ey + 2, P("steel", 3))
            put(out, cx + ox, ey, P("night", 0))
            put(out, cx + ox + 1, ey, P("night", 0))
        put(out, cx - 1, ey - 1, P("steel", 4))
    else:
        for ox in (-4, 3):
            put(out, cx + ox, ey, P("night", 0))
            put(out, cx + ox + 1, ey, P("night", 0))
            put(out, cx + ox, ey - 1, P("steel", 6))
        for ox in (-5, 2):                      # brows
            for k in range(3):
                put(out, cx + ox + k, ey - 3, hc[0])
    # hair
    for y in range(S):
        for x in range(S):
            e = _ell(x + 0.5, y + 0.5, cx, cy - 1, head_rx + 0.6, head_ry + 0.6)
            ny = (y + 0.5 - cy) / head_ry
            nx = (x + 0.5 - cx) / head_rx
            on = False
            if hair in ("buzz", "fade") and e < 1 and ny < -0.42 + (0.25 if abs(nx) > 0.7 else 0):
                on = hair == "buzz" or ny < -0.55 or bayer(x, y) > 0.5
            elif hair == "long" and (e < 1.05 and ny < -0.38 or (abs(nx) > 0.78 and ny < 1.1 and e < 1.6)):
                on = True
            elif hair == "spiky" and ny < -0.35 and e < 1.0 + 0.35 * abs(math.sin(nx * 9)):
                on = True
            elif hair == "mohawk" and abs(nx) < 0.22 and ny < -0.3 and e < 1.5:
                on = True
            if on:
                lit = -nx * 0.7 - ny * 0.4
                out[y, x] = hc[2] if lit > 0.6 else (hc[1] if lit > 0 else hc[0])
    if hair == "bald":
        # a shaved head catches the light: a small hot spot up and to the left
        for (dx, dy) in ((-4, -9), (-3, -9), (-5, -8), (-4, -8)):
            put(out, cx + dx, cy + dy, P("yellow", 2) if dy == -9 else sk[3])
    if cap:
        for y in range(S):
            for x in range(S):
                e = _ell(x + 0.5, y + 0.5, cx, cy - 2, head_rx + 1, head_ry + 0.5)
                ny = (y + 0.5 - cy) / head_ry
                if e < 1 and ny < -0.42:
                    out[y, x] = P(*cap) if (x + 0.5 - cx) < 3 else P(cap[0], max(0, cap[1] - 1))
        for x in range(cx - 4, cx + 13):                 # brim pointing forward
            put(out, x, int(cy - head_ry * 0.42) + 1, P(cap[0], max(0, cap[1] - 1)))
    if mechanic:
        _mechanic(out, cx, sh, mechanic)
    # thin dark outline around the silhouette for readability on any panel
    base = P(*bg)
    sil = np.zeros((S, S), dtype=bool)
    for y in range(S):
        for x in range(S):
            sil[y, x] = tuple(out[y, x]) not in (base, P("night", 1), P("night", 0))
    for y in range(1, S - 1):
        for x in range(1, S - 1):
            if not sil[y, x] and (sil[y - 1, x] or sil[y + 1, x] or sil[y, x - 1] or sil[y, x + 1]):
                out[y, x] = P("night", 0)
    return out


MICRO = {
    "J": ["..#", "..#", "..#", "#.#", ".#."],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."],
    "E": ["###", "#..", "##.", "#..", "###"],
}


def _mechanic(out, cx, sh, name):
    """Work shirt details: a collar, an oval name patch, a grease smudge, a shop rag over the shoulder."""
    for x in range(cx - 7, cx + 8):                   # open collar points
        y = 31 + abs(x - cx) // 3
        put(out, x, y, P("blue", 2) if x < cx else P("blue", 1))
    put(out, cx, 34, P("night", 0))
    put(out, cx, 36, P("steel", 4))                   # shirt buttons
    put(out, cx, 40, P("steel", 4))
    px, py = cx - 15, 37                              # name patch over the left chest
    for y in range(py, py + 9):
        for x in range(px, px + 15):
            e = ((x + 0.5 - px - 7.5) / 7.5) ** 2 + ((y + 0.5 - py - 4.5) / 4.5) ** 2
            if e < 1:
                out[y, x] = P("steel", 6) if e < 0.72 else P("red", 2)
    # stitched name in a tiny 3x5 hand, centred on the patch
    text = [MICRO[c] for c in name if c in MICRO]
    tx = px + (15 - (len(text) * 4 - 1)) // 2
    for k, rows in enumerate(text):
        for gy, row in enumerate(rows):
            for gx, c in enumerate(row):
                if c == "#":
                    put(out, tx + k * 4 + gx, py + 2 + gy, P("blue", 1))
    for (dx, dy) in ((5, 3), (6, 3), (6, 4)):         # grease smudge on the cheek
        put(out, cx + dx, 21 + dy, P("night", 1))
    for y in range(31, 41):                           # red shop rag over the right shoulder
        for x in range(int(cx + sh * 0.55), int(cx + sh * 0.55) + 4):
            if 0 <= x < S:
                out[y, x] = P("red", 2) if (y // 2) % 2 else P("red", 1)


CHARACTERS = {
    # Boss 1: Joe "Ground Control": stocky, short dark fade, full beard, shades, black tee
    "joe": dict(skin="medium", build=1.45, hair="bald", hair_color="black", beard="full", eyes="shades", shirt=("blue", 0), rim=("cyan", 2), mechanic="JOE"),
    # Boss 2: "Rotor", the FC's driver (original character)
    "rotor": dict(skin="light", build=0.9, hair="spiky", hair_color="silver", beard=None, eyes="plain", shirt=("night", 2), jacket=("red", 1), rim=("red", 3), earring=True),
    # Street rivals
    "dash": dict(skin="tan", build=0.95, hair="long", hair_color="brown", eyes="plain", shirt=("cyan", 1), rim=("yellow", 0)),
    "tiny": dict(skin="dark", build=1.6, hair="buzz", hair_color="black", beard="goatee", eyes="plain", shirt=("orange", 0), rim=("yellow", 0)),
    "kat": dict(skin="light", build=0.85, hair="long", hair_color="pink", eyes="glasses", shirt=("night", 3), rim=("yellow", 0)),
    # Sport rivals
    "vega": dict(skin="tan", build=1.05, hair="mohawk", hair_color="red", eyes="plain", shirt=("blue", 1), jacket=("night", 1), rim=("magenta", 3)),
    "hollis": dict(skin="light", build=1.2, hair="buzz", hair_color="blonde", beard="full", eyes="plain", shirt=("green", 1), cap=("red", 2), rim=("magenta", 3)),
    "mika": dict(skin="tan", build=0.9, hair="long", hair_color="black", eyes="shades", shirt=("magenta", 1), rim=("magenta", 3), earring=True),
    # Pro rivals and boss
    "ace": dict(skin="tan", build=1.0, hair="spiky", hair_color="blonde", eyes="shades", shirt=("orange", 1), rim=("magenta", 4)),
    "bruno": dict(skin="brown", build=1.55, hair="buzz", hair_color="black", beard="goatee", eyes="plain", shirt=("night", 3), jacket=("steel", 2), rim=("magenta", 4)),
    "lin": dict(skin="light", build=0.85, hair="long", hair_color="black", eyes="plain", shirt=("cyan", 0), rim=("magenta", 4)),
    "volt": dict(skin="light", build=0.95, hair="buzz", hair_color="silver", eyes="glasses", shirt=("steel", 5), rim=("magenta", 4)),
    # Outlaw rivals and boss
    "reaper": dict(skin="tan", build=1.1, hair="long", hair_color="black", beard="goatee", eyes="shades", shirt=("night", 1), jacket=("night", 2), rim=("red", 3)),
    "saint": dict(skin="light", build=1.0, hair="buzz", hair_color="blonde", eyes="plain", shirt=("steel", 6), rim=("red", 3)),
    "max": dict(skin="dark", build=1.5, hair="mohawk", hair_color="black", beard="full", eyes="plain", shirt=("red", 1), rim=("red", 3), earring=True),
    "preacher": dict(skin="medium", build=1.2, hair="long", hair_color="silver", beard="full", eyes="shades", shirt=("night", 1), jacket=("night", 2), rim=("red", 3)),
}


def build():
    for name, kw in CHARACTERS.items():
        save(portrait(**kw), f"portraits/{name}.png")
