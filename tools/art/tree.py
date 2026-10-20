"""The Christmas tree: a front-facing tower in the median with one bulb column per lane.

tree.png holds the housing with unlit bulbs; bulbs.png holds the lit bulb sprites
(pre-stage, stage, amber, green, red). Bulb centres are written to data/tree.json so the game
lights the right spots.
"""
import json
import math
import os

from common import P, ASSETS, bayer, canvas, put, rect, vline, hline, save

W, H = 30, 92
COLS = [9, 21]                       # bulb column centres (far lane, near lane)
ROWS = [("prestage", 6, 2), ("stage", 12, 2), ("amber", 22, 4), ("amber", 32, 4), ("amber", 42, 4),
        ("green", 53, 4), ("red", 64, 4)]
LIT = {"prestage": ("yellow", 2), "stage": ("yellow", 2), "amber": ("orange", 2), "green": ("green", 3), "red": ("red", 3)}
OFF = {"prestage": ("steel", 2), "stage": ("steel", 2), "amber": ("skin", 1), "green": ("green", 0), "red": ("red", 0)}


def bulb(size, ramp, idx, lit):
    d = size * 2 + 3
    out = canvas(d, d)
    c = (d - 1) / 2
    for y in range(d):
        for x in range(d):
            r = math.hypot(x - c, y - c)
            if r <= size + 0.5:
                if lit:
                    col = P("yellow", 2) if r < size * 0.45 else P(ramp, idx)
                    if r > size - 0.3:
                        col = P(ramp, max(0, idx - 1))
                else:
                    col = P(ramp, idx) if r < size - 0.5 else P("night", 0)
                    if x < c and y < c and r < size * 0.5:
                        col = P(ramp, idx + 1)       # dull reflection on the dead lens
                out[y, x] = col
    return out


def tree():
    out = canvas(W, H)
    # pole
    rect(out, 13, 72, 17, H, P("steel", 2))
    vline(out, 13, 72, H, P("steel", 3))
    vline(out, 16, 72, H, P("steel", 1))
    # housing
    rect(out, 2, 0, W - 2, 72, P("night", 1))
    rect(out, 3, 1, W - 3, 71, P("night", 0))
    hline(out, 2, W - 2, 0, P("steel", 3))
    vline(out, 2, 0, 72, P("steel", 2))
    vline(out, W - 3, 0, 72, P("steel", 1))
    hline(out, 2, W - 2, 71, P("steel", 1))
    vline(out, 15, 2, 70, P("steel", 1))          # divider between the lanes
    # visors over each bulb row
    for _, ry, size in ROWS:
        for cx in COLS:
            hline(out, cx - size - 1, cx + size + 2, ry - size - 2, P("steel", 2))
    for name, ry, size in ROWS:
        ramp, idx = OFF[name]
        b = bulb(size, ramp, idx, False)
        d = b.shape[0]
        for cx in COLS:
            for y in range(d):
                for x in range(d):
                    if b[y, x, 3]:
                        out[ry - d // 2 + y, cx - d // 2 + x] = b[y, x]
    # base plate
    rect(out, 8, H - 3, 22, H, P("steel", 3))
    hline(out, 8, 22, H - 1, P("steel", 1))
    return out


def build():
    save(tree(), "fx/tree.png")
    sheet = []
    for name in ("prestage", "amber", "green", "red"):
        size = 2 if name == "prestage" else 4
        sheet.append((name, bulb(size, *LIT[name], True)))
    # bulbs.png: lit sprites side by side, 11px cells
    cell = 11
    out = canvas(cell * len(sheet), cell)
    for i, (_, b) in enumerate(sheet):
        d = b.shape[0]
        o = (cell - d) // 2
        out[o:o + d, i * cell + o:i * cell + o + d] = b
    save(out, "fx/bulbs.png")
    layout = {
        "width": W, "height": H, "cell": cell,
        "sprites": {name: i for i, (name, _) in enumerate(sheet)},
        "columns": COLS,
        "rows": [{"kind": name, "y": ry, "size": size} for name, ry, size in ROWS],
    }
    layout["sprites"]["stage"] = layout["sprites"]["prestage"]
    os.makedirs(os.path.join(ASSETS, "data"), exist_ok=True)
    with open(os.path.join(ASSETS, "data", "tree.json"), "w") as f:
        json.dump(layout, f, indent=2)
