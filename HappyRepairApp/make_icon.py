#!/usr/bin/env python3
"""Generate a simple launcher icon: rounded square + role letter."""
import sys

from PIL import Image, ImageDraw, ImageFont

out, letter, bg = sys.argv[1], sys.argv[2], sys.argv[3]
SIZE = 192
img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([4, 4, SIZE - 4, SIZE - 4], radius=42, fill=bg)

font = None
for path in (
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
):
    try:
        font = ImageFont.truetype(path, 110)
        break
    except OSError:
        pass
if font is None:
    font = ImageFont.load_default()

bbox = d.textbbox((0, 0), letter, font=font)
w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
d.text(((SIZE - w) / 2 - bbox[0], (SIZE - h) / 2 - bbox[1]), letter, font=font, fill="white")
img.save(out)
print(out)
