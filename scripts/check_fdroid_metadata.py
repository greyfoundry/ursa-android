"""Validate the F-Droid metadata against the Android release configuration."""

from __future__ import annotations

import re
import sys
from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]
METADATA_PATH = ROOT / "fdroid" / "dev.astoris.ursa.yml"
GRADLE_PATH = ROOT / "app" / "build.gradle.kts"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def gradle_value(pattern: str, label: str) -> str:
    match = re.search(pattern, GRADLE_PATH.read_text(encoding="utf-8"), re.MULTILINE)
    require(match is not None, f"Could not read {label} from {GRADLE_PATH}")
    return match.group(1)


def main() -> int:
    metadata = yaml.safe_load(METADATA_PATH.read_text(encoding="utf-8"))
    require(isinstance(metadata, dict), "F-Droid metadata must be a mapping")

    version_name = gradle_value(r'versionName\s*=\s*"([^"]+)"', "versionName")
    version_code = int(gradle_value(r"versionCode\s*=\s*(\d+)", "versionCode"))

    require(metadata.get("RepoType") == "git", "RepoType must be git")
    require(metadata.get("AutoUpdateMode") == "Version", "AutoUpdateMode must be Version")
    require(
        str(metadata.get("UpdateCheckMode", "")).startswith("Tags ^v[0-9]"),
        "UpdateCheckMode must track vX.Y.Z tags",
    )
    require(metadata.get("CurrentVersion") == version_name, "CurrentVersion differs from app versionName")
    require(
        metadata.get("CurrentVersionCode") == version_code,
        "CurrentVersionCode differs from app versionCode",
    )
    require(
        re.fullmatch(r"[0-9a-f]{64}", str(metadata.get("AllowedAPKSigningKeys", ""))) is not None,
        "AllowedAPKSigningKeys must be a SHA-256 fingerprint",
    )

    builds = metadata.get("Builds")
    require(isinstance(builds, list) and len(builds) == 1, "Expected one current F-Droid build")
    build = builds[0]
    require(build.get("versionName") == version_name, "Build versionName differs from app versionName")
    require(build.get("versionCode") == version_code, "Build versionCode differs from app versionCode")
    require(re.fullmatch(r"[0-9a-f]{40}", str(build.get("commit", ""))) is not None, "Build commit must be a full SHA")
    require(build.get("subdir") == "app", "Build subdir must be app")
    require(build.get("gradle") in ([True], ["yes"]), "Build must use Gradle")

    print(f"F-Droid metadata is consistent with URSA {version_name} ({version_code}).")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValueError as error:
        print(f"F-Droid metadata validation failed: {error}", file=sys.stderr)
        raise SystemExit(1)
