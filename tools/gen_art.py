#!/usr/bin/env python3
"""Generates every Pixel Strip image into assets/. Run: python3 tools/gen_art.py [--preview DIR] [--only ID,ID]"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "art"))

import numpy as np
from PIL import Image

import car
import fx
import icon
import paint
import roster
import track_neon
import tree
import ui
from common import P


def main():
    preview_dir = None
    if "--preview" in sys.argv:
        preview_dir = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(preview_dir, exist_ok=True)
    only = None
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split(","))

    ramps = paint.build()
    cars = [p for p in roster.ROSTER if only is None or p["id"] in only]
    built = [(p, car.build(p)) for p in cars]
    if only is None:
        track_neon.build()
        ui.build()
        icon.build()
        tree.build()
        fx.build()

    if preview_dir:
        # every car on one sheet, 3x, each in a different paint
        rows = []
        for i, (p, layers) in enumerate(built):
            img = car.preview_image(p, layers, ramps[i % len(ramps)])
            cell = np.zeros((car.CH, 112, 4), dtype=np.uint8)
            cell[:] = P("night", 2)
            cell[:, :img.shape[1]] = img
            rows.append(cell)
        cols = 4
        while len(rows) % cols:
            rows.append(np.zeros_like(rows[0]))
        grid = np.concatenate([np.concatenate(rows[r:r + cols], axis=1) for r in range(0, len(rows), cols)], axis=0)
        Image.fromarray(grid).resize((grid.shape[1] * 3, grid.shape[0] * 3), Image.NEAREST).save(os.path.join(preview_dir, "roster.png"))
        if only is None:
            track_neon.preview(os.path.join(preview_dir, "track.png"))
    print(f"art generated ({len(built)} cars)")


if __name__ == "__main__":
    main()
