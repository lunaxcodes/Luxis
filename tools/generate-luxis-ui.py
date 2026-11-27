"""Generate the Luxis GUI's shared 4x UI texture. Uses only the Python standard library."""

import math
import struct
import zlib
from pathlib import Path


SIZE = 256
SCALE = 4
pixels = bytearray(SIZE * SIZE * 4)


def clamp(x):
    return max(0.0, min(1.0, x))


def sprite(u, v, size, radius, material):
    # One texel of extruded padding on each side for bilinear filtering.
    extent = size * SCALE
    for py in range(-1, extent + 1):
        for px in range(-1, extent + 1):
            x = (max(0, min(extent - 1, px)) + 0.5) / SCALE
            y = (max(0, min(extent - 1, py)) + 0.5) / SCALE
            qx = abs(x - size / 2) - (size / 2 - radius)
            qy = abs(y - size / 2) - (size / 2 - radius)
            distance = math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - radius
            coverage = clamp(0.5 - distance * SCALE)
            if material == "shadow":
                rgba = (255, 255, 255, round(255 * clamp(-distance / 7) ** 2))
            elif material == "outline":
                rgba = (255, 255, 255, round(255 * coverage * clamp((distance + 1.4) * SCALE)))
            elif material == "mask":
                rgba = (255, 255, 255, round(255 * coverage))
            else:
                t = y / size
                top, bottom, rim = material
                sheen = 1.0 - t
                edge = clamp((distance + 1.35) * SCALE)
                bevel = rim * edge * (0.42 + 0.58 * sheen)
                rgb = [round(a * sheen + b * t + bevel) for a, b in zip(top, bottom)]
                rgba = (*rgb, round(255 * coverage))
            at = ((v + py) * SIZE + u + px) * 4
            pixels[at:at + 4] = bytes(rgba)


# Must agree with the source rectangles in Draw.java.
# These are luminance masks; every surface gets its color from Java.
sprite(2, 2, 40, 17, ((175,) * 3, (82,) * 3, 72))
sprite(164, 2, 22, 9, ((177,) * 3, (121,) * 3, 48))
sprite(164, 92, 20, 7, ((167,) * 3, (111,) * 3, 47))
sprite(2, 164, 20, 10, "mask")
sprite(84, 164, 22, 9, "outline")
sprite(174, 174, 20, 8, "shadow")


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


scanlines = b"".join(b"\0" + pixels[y * SIZE * 4:(y + 1) * SIZE * 4] for y in range(SIZE))
output = Path(__file__).resolve().parents[1] / "luxis-ui-resources/assets/luxis/textures/gui/surfaces.png"
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(scanlines, 9))
    + chunk(b"IEND", b"")
)
