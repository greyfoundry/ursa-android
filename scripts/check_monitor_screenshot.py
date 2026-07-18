"""Keep the public monitor-screen screenshots visually identical and device-sized."""

from __future__ import annotations

import hashlib
import struct
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
README_SCREENSHOT = ROOT / "docs" / "assets" / "screenshots" / "02-monitors-dark.png"
FASTLANE_SCREENSHOT = (
    ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "phoneScreenshots" / "1_monitors.png"
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
EXPECTED_SIZE = (1080, 2400)


def png_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if data[:8] != PNG_SIGNATURE or data[12:16] != b"IHDR":
        raise ValueError(f"{path} is not a PNG with an IHDR header")
    return struct.unpack(">II", data[16:24])


def main() -> int:
    for screenshot in (README_SCREENSHOT, FASTLANE_SCREENSHOT):
        if png_size(screenshot) != EXPECTED_SIZE:
            raise ValueError(f"{screenshot} must be a {EXPECTED_SIZE[0]}x{EXPECTED_SIZE[1]} Pixel screenshot")

    readme_hash = hashlib.sha256(README_SCREENSHOT.read_bytes()).digest()
    fastlane_hash = hashlib.sha256(FASTLANE_SCREENSHOT.read_bytes()).digest()
    if readme_hash != fastlane_hash:
        raise ValueError("README and Fastlane monitor screenshots differ")

    print("Monitor screenshot assets match the approved Pixel baseline.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as error:
        print(f"Monitor screenshot validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
