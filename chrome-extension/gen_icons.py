#!/usr/bin/env python3
"""아이콘 생성 스크립트. 디자인을 바꾸고 싶으면 이 파일을 수정한 뒤
   `python3 gen_icons.py` 를 다시 실행하면 icons/ 가 갱신된다."""
import os
from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "icons")
os.makedirs(OUT, exist_ok=True)

BASE = 512  # 큰 크기로 그린 뒤 각 타깃으로 축소(안티에일리어싱 효과)


def rounded_gradient(size, radius_ratio, top, bot):
    """세로 그라데이션을 라운드 사각형으로 마스킹한 배경."""
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
    """가운데가 뚫린 흰색 캡슐(스타디움) 외곽선."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    c = size / 2
    ImageDraw.Draw(im).rounded_rectangle(
        [c - length / 2, c - thick / 2, c + length / 2, c + thick / 2],
        radius=thick / 2,
        outline=(255, 255, 255, 255),
        width=stroke,
    )
    return im


def compose():
    base = rounded_gradient(BASE, 0.23, (96, 165, 250), (37, 99, 235))  # #60a5fa→#2563eb
    cap = capsule(BASE, BASE * 0.42, BASE * 0.26, int(BASE * 0.060))
    cap = cap.rotate(45, resample=Image.BICUBIC, expand=False)
    off = int(BASE * 0.125)
    glyph = Image.new("RGBA", (BASE, BASE), (0, 0, 0, 0))
    glyph.alpha_composite(cap, (-off, -off))  # 좌상단 링크
    glyph.alpha_composite(cap, (off, off))    # 우하단 링크 (사슬처럼 겹침)
    base.alpha_composite(glyph)
    return base


def main():
    master = compose()
    for s in (16, 32, 48, 128):
        master.resize((s, s), Image.LANCZOS).save(os.path.join(OUT, f"icon{s}.png"))
    print("icons written to", OUT)


if __name__ == "__main__":
    main()
