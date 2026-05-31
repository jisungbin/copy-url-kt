#!/usr/bin/env python3
"""앱 번들 아이콘용 iconset 생성 (컬러 그라데이션 라운드 + 흰 링크).
   이후 `iconutil -c icns build/appicon/CopyUrl.iconset` 로 .icns 변환."""
import os
from PIL import Image, ImageDraw

ICONSET = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "build", "appicon", "CopyUrl.iconset"
)
os.makedirs(ICONSET, exist_ok=True)


def rounded_gradient(size, radius_ratio, top, bot):
    grad = Image.new("RGBA", (size, size))
    gd = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / (size - 1)
        gd.line(
            [(0, y), (size, y)],
            fill=(
                int(top[0] * (1 - t) + bot[0] * t),
                int(top[1] * (1 - t) + bot[1] * t),
                int(top[2] * (1 - t) + bot[2] * t),
                255,
            ),
        )
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(size * radius_ratio), fill=255
    )
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(grad, (0, 0), mask)
    return out


def capsule(size, length, thick, stroke):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = size / 2
    ImageDraw.Draw(im).rounded_rectangle(
        [c - length / 2, c - thick / 2, c + length / 2, c + thick / 2],
        radius=thick / 2,
        outline=(255, 255, 255, 255),
        width=stroke,
    )
    return im


def compose(size):
    base = rounded_gradient(size, 0.23, (96, 165, 250), (37, 99, 235))
    cap = capsule(size, size * 0.42, size * 0.26, max(2, int(size * 0.060)))
    cap = cap.rotate(45, resample=Image.BICUBIC, expand=False)
    off = int(size * 0.125)
    glyph = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glyph.alpha_composite(cap, (-off, -off))
    glyph.alpha_composite(cap, (off, off))
    base.alpha_composite(glyph)
    return base


SIZES = {
    "icon_16x16.png": 16, "icon_16x16@2x.png": 32,
    "icon_32x32.png": 32, "icon_32x32@2x.png": 64,
    "icon_128x128.png": 128, "icon_128x128@2x.png": 256,
    "icon_256x256.png": 256, "icon_256x256@2x.png": 512,
    "icon_512x512.png": 512, "icon_512x512@2x.png": 1024,
}


def main():
    master = compose(1024)
    for name, sz in SIZES.items():
        master.resize((sz, sz), Image.LANCZOS).save(os.path.join(ICONSET, name))
    print("iconset saved to", ICONSET)


if __name__ == "__main__":
    main()
