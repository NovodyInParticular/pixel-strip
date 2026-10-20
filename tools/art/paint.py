"""Paint ramps for car bodies: one 8-colour hue-shifted ramp per row of assets/paint/ramps.png."""
import numpy as np

from common import grey_ramp, paint_ramp, save

# Order matters: the row index is the paint id used in data/paints.json.
PAINTS = [
    ("Torch Red", paint_ramp(0.995, sat=0.85)),
    ("Plum Crazy", paint_ramp(0.80, sat=0.7)),
    ("Grabber Teal", paint_ramp(0.49, sat=0.75, shadow_hue=0.68)),
    ("Hemi Orange", paint_ramp(0.055, sat=0.95)),
    ("Bumblebee", paint_ramp(0.14, sat=0.9, hi_hue=0.16)),
    ("Midnight", paint_ramp(0.68, sat=0.45, light_hi=0.78)),
    ("Bone White", paint_ramp(0.11, sat=0.18, light_lo=0.16, light_hi=0.98)),
    ("Gunmetal", grey_ramp()),
    ("Sublime", paint_ramp(0.25, sat=0.85, shadow_hue=0.55)),
    ("Petty Blue", paint_ramp(0.57, sat=0.8)),
    ("Highland Green", paint_ramp(0.38, sat=0.55, light_hi=0.82, hi_hue=0.2)),
    ("Quicksilver", paint_ramp(0.6, sat=0.12, light_lo=0.1, light_hi=0.96)),
    ("Pacific Blue", paint_ramp(0.62, sat=0.38, light_lo=0.14, light_hi=0.93, shift=0.12)),
    ("Jet Black", paint_ramp(0.68, sat=0.18, light_lo=0.03, light_hi=0.62, hi_hue=0.6, shift=0.05)),
]


def build():
    ramps = [r for _, r in PAINTS]
    out = np.zeros((len(ramps), 8, 4), dtype=np.uint8)
    for i, r in enumerate(ramps):
        out[i] = r
    save(out, "paint/ramps.png")
    return ramps
