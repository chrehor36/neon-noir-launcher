"""Draws the Neon Noir wallpapers (dark and light) for CleanYourTV.

Run from this folder:  python3 make_neon_noir.py
Writes neon-noir/wallpapers/{morning,evening,night}.jpg.
Released under CC0 1.0. Needs Pillow and NumPy.
"""
import os
import numpy as np
from PIL import Image

W, H = 1920, 1080
HERE = os.path.dirname(os.path.abspath(__file__))

def glow(cx, cy, r, color, alpha):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / r
    a = np.clip(1.0 - d, 0, 1) * alpha
    return np.array(color, np.float32), a[..., None]

def rain_mask():
    # thin diagonal streaks every 33 px, tilted like the mockup's 105-degree rain
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    u = xx + yy * np.tan(np.radians(15))
    stripe = (np.mod(u, 33.0) < 1.4).astype(np.float32)
    # break the streaks into dashes so it reads as rain, not a grid
    rng = np.random.default_rng(7)
    breaks = rng.random((H // 6 + 1, W // 33 + 2)) < 0.55
    col = (u // 33).astype(int) % breaks.shape[1]
    row = (yy // 6).astype(int)
    return stripe * breaks[row, col]

def scanlines():
    yy = np.arange(H)
    return (np.mod(yy, 6) < 1.5).astype(np.float32)[:, None] * np.ones((1, W), np.float32)

def quiet_zones():
    # soften the texture behind the clock (centre) and the dock (bottom) so text stays easy to read
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    clock = np.exp(-(((xx - W / 2) / 520) ** 2 + ((yy - H * 0.36) / 300) ** 2))
    dock = np.clip((yy - H * 0.70) / (H * 0.18), 0, 1)
    return np.clip(1.0 - 0.75 * np.maximum(clock, dock), 0.25, 1)[..., None]

MOODS_DARK = {
    "morning": [((226, 120, 80), 0.40), ((226, 176, 74), 0.26)],
    "evening": [((168, 19, 29), 0.55), ((226, 176, 74), 0.30)],
    "night":   [((30, 60, 130), 0.45), ((168, 19, 29), 0.30)],
}

def draw(bg, moods, rain_rgb, rain_a, scan_rgb, scan_a, folder):
    rain = rain_mask()[..., None]
    scan = scanlines()[..., None]
    quiet = quiet_zones()
    for mood, ((c1, a1), (c2, a2)) in moods.items():
        img = np.ones((H, W, 3), np.float32) * np.array(bg, np.float32)
        for (c, a), (cx, cy, r) in (((c1, a1), (W * 0.83, H * 0.11, 860)), ((c2, a2), (W * 0.11, H * 0.56, 780))):
            col, al = glow(cx, cy, r, c, a)
            img = img * (1 - al) + col * al
        ra = rain * rain_a * quiet
        img = img * (1 - ra) + np.array(rain_rgb, np.float32) * ra
        sa = scan * scan_a
        img = img * (1 - sa) + np.array(scan_rgb, np.float32) * sa
        out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
        out.save(os.path.join(HERE, "wallpapers", mood + ".jpg"), quality=88, subsampling=0, optimize=True)

draw((10, 6, 6), MOODS_DARK, (240, 200, 170), 0.10, (0, 0, 0), 0.20, "neon-noir")
print("done")
