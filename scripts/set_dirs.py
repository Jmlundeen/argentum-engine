"""Shared mapping between `definitions/<dir>` directories and set codes.

The directory name under `mtg-sets/.../definitions/` usually equals the lowercase
set code, but can't always: `con` is a reserved filename on Windows (DOS device
name), so Conflux lives in `definitions/conflux/`. The authoritative code is the
`override val code = "..."` declaration in each directory's `*Set.kt` — scripts
must read that instead of trusting the directory name.
"""

from __future__ import annotations

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFINITIONS_ROOT = REPO_ROOT / "mtg-sets/src/main/kotlin/com/wingedsheep/mtg/sets/definitions"

SET_CODE_RE = re.compile(r'override\s+val\s+code\s*=\s*"([^"]+)"')


def set_dir_codes() -> dict[str, str]:
    """Map each definitions/<dir> name to its lowercase set code."""
    codes: dict[str, str] = {}
    if not DEFINITIONS_ROOT.is_dir():
        return codes
    for d in sorted(DEFINITIONS_ROOT.iterdir()):
        if not d.is_dir():
            continue
        code = None
        for set_kt in sorted(d.glob("*Set.kt")):
            m = SET_CODE_RE.search(set_kt.read_text(encoding="utf-8"))
            if m:
                code = m.group(1).lower()
                break
        codes[d.name] = code or d.name
    return codes


def dir_for_codes() -> dict[str, str]:
    """Reverse map: lowercase set code -> definitions/<dir> name."""
    return {code: d for d, code in set_dir_codes().items()}


def scaffolded_set_codes() -> set[str]:
    """Lowercase set codes of every scaffolded definitions/<dir>."""
    return set(set_dir_codes().values())
