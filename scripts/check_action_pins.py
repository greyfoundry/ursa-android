"""Reject mutable GitHub Action references in first-party workflows."""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
USES = re.compile(r"^\s*(?:-\s*)?uses:\s+[^@\s]+@([0-9a-f]{40})(?:\s+#.*)?\s*$")


def main() -> int:
    invalid: list[str] = []
    for workflow in sorted((ROOT / ".github" / "workflows").glob("*.yml")):
        for line_number, line in enumerate(workflow.read_text(encoding="utf-8").splitlines(), start=1):
            if "uses:" in line and not USES.match(line):
                invalid.append(f"{workflow.relative_to(ROOT)}:{line_number}: {line.strip()}")
    if invalid:
        print("GitHub Actions must be pinned to immutable commit hashes:", file=sys.stderr)
        print("\n".join(invalid), file=sys.stderr)
        return 1
    print("All GitHub Actions are pinned to immutable commit hashes.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
