#!/usr/bin/env python3
"""
build-pocket-bundle.py — build the Miko-hosted Pocket TTS engine bundle.

Pipeline (phase-04 decisions D-03 / D-07):

  1. Download the Marmalade v21 ``pocket-tts-en`` bundle tarball and verify
     its sha256 + size against the pinned constants below BEFORE extraction.
     Any mismatch aborts non-zero and no output tarball is written.
  2. Fetch the curated UK-accented VCTK voice WAVs listed in
     ``tools/pocket-uk-speakers.txt`` from ``kyutai/tts-voices`` on Hugging
     Face and drop them into ``voices/vctk_pNNN.wav``.
  3. Rewrite ``bundle.json``: append the new voice names to
     ``predefined_voices`` and write a ``voice_licenses`` map covering every
     bundled voice.
  4. Re-tar the tree deterministically (sorted entries, zeroed
     uid/gid/mtime) under a ``pocket-tts-en/`` root as
     ``pocket-tts-en-v2026_04-mixed-uk.tar.bz2``.
  5. Print the output filename, sha256, byte size, and ready-to-paste
     attribution rows + PocketEngineCatalog constants.

Usage:
    python tools/build-pocket-bundle.py                       # full build (~97 MB download)
    python tools/build-pocket-bundle.py --selftest            # offline sanity checks, no network
    python tools/build-pocket-bundle.py --emit-fixtures DIR   # also copy tokenizer.model +
                                                            # the rewritten bundle.json into DIR
    python tools/build-pocket-bundle.py --output-dir DIR      # override output location

Only the Python standard library is used (urllib.request, tarfile, hashlib,
json). Generated artifacts land in tools/build/ (gitignored) — never commit
them.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

# --- Locked pins (phase-04 D-03) --------------------------------------------
SOURCE_URL = (
    "https://github.com/maxwhipw/marmalade-tts-android-engines/"
    "releases/download/v21/pocket-tts-en-v2026_04-mixed.tar.bz2"
)
SOURCE_SHA256 = "3a3ed2810afbae1f963f4f4e3ebde767c2d3c7c622cc544f23170f0ca755b452"
SOURCE_SIZE_BYTES = 97_291_178
ARCHIVE_ROOT = "pocket-tts-en/"
OUTPUT_NAME = "pocket-tts-en-v2026_04-mixed-uk.tar.bz2"

HF_VOICE_URL_TEMPLATE = (
    "https://huggingface.co/kyutai/tts-voices/resolve/main/"
    "vctk/{speaker}_023_enhanced.wav"
)

TOOLS_DIR = Path(__file__).resolve().parent
SPEAKER_LIST = TOOLS_DIR / "pocket-uk-speakers.txt"
# `build` is a bare gitignore pattern — output here can never be committed.
DEFAULT_OUTPUT_DIR = TOOLS_DIR / "build"

ALLOWED_ACCENTS = {"English", "Scottish", "Welsh", "NorthernIrish", "Irish"}
SPEAKER_LINE = re.compile(r"^(p\d{3})\s+(\S+)\s*$")

# License for each of the 6 base voices in the v21 bundle (per upstream
# LICENSES/pocket-tts.md). Added VCTK speakers are all CC-BY-4.0 (CSTR VCTK
# Corpus, University of Edinburgh) — attribution rows print after a build.
BASE_VOICE_LICENSES = {
    "alba": "CC-BY-4.0",
    "azelma": "CC-BY-4.0",
    "eponine": "CC-BY-4.0",
    "fantine": "CC-BY-4.0",
    "javert": "CC0",
    "marius": "CC0",
}
VCTK_LICENSE = "CC-BY-4.0 (VCTK, U. Edinburgh)"

CHUNK = 1 << 20  # 1 MiB


# --- helpers -----------------------------------------------------------------

def parse_speaker_list(path: Path) -> list[tuple[str, str]]:
    """Parse the speaker list: one ``pNNN <accent>`` per line."""
    speakers: list[tuple[str, str]] = []
    seen: set[str] = set()
    for lineno, raw in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        match = SPEAKER_LINE.match(line)
        if not match:
            raise SystemExit(
                f"{path.name}:{lineno}: expected 'pNNN <accent>', got {line!r}"
            )
        speaker, accent = match.group(1), match.group(2)
        if accent not in ALLOWED_ACCENTS:
            raise SystemExit(
                f"{path.name}:{lineno}: accent {accent!r} not in "
                f"{sorted(ALLOWED_ACCENTS)} (research assumption A5)"
            )
        if speaker in seen:
            raise SystemExit(f"{path.name}:{lineno}: duplicate speaker {speaker}")
        seen.add(speaker)
        speakers.append((speaker, accent))
    return speakers


def download(url: str, dest: Path) -> tuple[str, int]:
    """Stream ``url`` to ``dest``; return (sha256_hex, size_bytes)."""
    hasher = hashlib.sha256()
    size = 0
    request = urllib.request.Request(
        url, headers={"User-Agent": "miko-pocket-bundle-builder/1.0"}
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        with open(dest, "wb") as out:
            while True:
                chunk = response.read(CHUNK)
                if not chunk:
                    break
                out.write(chunk)
                hasher.update(chunk)
                size += len(chunk)
    return hasher.hexdigest(), size


def sha256_and_size(path: Path) -> tuple[str, int]:
    hasher = hashlib.sha256()
    size = 0
    with open(path, "rb") as f:
        while True:
            chunk = f.read(CHUNK)
            if not chunk:
                break
            hasher.update(chunk)
            size += len(chunk)
    return hasher.hexdigest(), size


def is_wav(data: bytes) -> bool:
    """Minimal RIFF/WAVE sanity check (T-04-01-02)."""
    return len(data) > 44 and data[:4] == b"RIFF" and data[8:12] == b"WAVE"


# --- pipeline ----------------------------------------------------------------

def extract_source(tar_path: Path, dest_dir: Path) -> Path:
    """Extract the source tarball; return the bundle root directory.

    Enforces the expected single top-level directory (``pocket-tts-en/``)
    and guards against zip-slip.
    """
    dest_dir.mkdir(parents=True, exist_ok=True)
    base = dest_dir.resolve()
    with tarfile.open(tar_path, "r:bz2") as tar:
        for member in tar.getmembers():
            target = (dest_dir / member.name).resolve()
            if target != base and not str(target).startswith(str(base) + os.sep):
                raise SystemExit(f"zip-slip: {member.name!r} escapes {dest_dir}")
        tar.extractall(dest_dir)
    roots = [p for p in dest_dir.iterdir() if p.is_dir()]
    expected = ARCHIVE_ROOT.rstrip("/")
    if len(roots) != 1 or roots[0].name != expected:
        found = [p.name for p in dest_dir.iterdir()]
        raise SystemExit(
            f"expected single top-level dir {expected!r}, found {found}"
        )
    return roots[0]


def inject_voices(root: Path, speakers: list[tuple[str, str]], work_dir: Path) -> None:
    """Fetch each speaker's WAV into ``voices/vctk_pNNN.wav``."""
    voices_dir = root / "voices"
    if not voices_dir.is_dir():
        raise SystemExit(f"bundle has no voices/ directory at {voices_dir}")
    for speaker, _accent in speakers:
        url = HF_VOICE_URL_TEMPLATE.format(speaker=speaker)
        staging = work_dir / f"{speaker}.wav"
        try:
            _sha, size = download(url, staging)
        except Exception as exc:  # noqa: BLE001 — abort loudly, no output
            raise SystemExit(f"failed to fetch voice {speaker} from {url}: {exc}")
        data = staging.read_bytes()
        if not is_wav(data):
            raise SystemExit(
                f"{url}: not a RIFF/WAVE file or zero-length ({len(data)} bytes)"
            )
        dest = voices_dir / f"vctk_{speaker}.wav"
        dest.write_bytes(data)
        print(f"      + voices/{dest.name} ({size} bytes)")


def rewrite_bundle_json(root: Path, speakers: list[tuple[str, str]]) -> list[str]:
    """Append vctk voices to ``predefined_voices`` and write ``voice_licenses``.

    Returns the list of voice names actually added.
    """
    bundle_path = root / "bundle.json"
    bundle = json.loads(bundle_path.read_text(encoding="utf-8"))

    predefined = bundle.setdefault("predefined_voices", [])
    if not isinstance(predefined, list):
        raise SystemExit("bundle.json: predefined_voices is not a list")

    added: list[str] = []
    for speaker, _accent in speakers:
        name = f"vctk_{speaker}"
        if name not in predefined:
            predefined.append(name)
            added.append(name)

    # Our verified map wins over any pre-existing voice_licenses block.
    existing = bundle.get("voice_licenses")
    licenses = {**(existing if isinstance(existing, dict) else {}), **BASE_VOICE_LICENSES}
    for name in added:
        licenses[name] = VCTK_LICENSE

    missing = [v for v in predefined if v not in licenses]
    if missing:
        raise SystemExit(
            f"bundle.json: predefined_voices with no known license: {missing} "
            "— refusing to ship a voice without a license entry"
        )
    bundle["voice_licenses"] = {name: licenses[name] for name in sorted(licenses)}

    bundle_path.write_text(
        json.dumps(bundle, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return added


def _normalize(info: tarfile.TarInfo, is_dir: bool) -> None:
    """Zero out metadata so the output tarball is byte-reproducible."""
    info.uid = info.gid = 0
    info.uname = info.gname = ""
    info.mtime = 0
    info.mode = 0o755 if is_dir else 0o644


def retar(root: Path, out_path: Path) -> None:
    """Write ``root`` as a deterministic .tar.bz2 under the ``pocket-tts-en/``
    archive root. Writes via a .part file so a crash never leaves a partial
    output tarball behind."""
    root_name = ARCHIVE_ROOT.rstrip("/")
    part_path = out_path.parent / (out_path.name + ".part")
    entries = sorted(root.rglob("*"), key=lambda p: p.relative_to(root).as_posix())
    with tarfile.open(part_path, "w:bz2") as tar:
        root_info = tarfile.TarInfo(root_name + "/")
        root_info.type = tarfile.DIRTYPE
        _normalize(root_info, is_dir=True)
        tar.addfile(root_info)
        for path in entries:
            arcname = f"{root_name}/{path.relative_to(root).as_posix()}"
            if path.is_dir():
                info = tarfile.TarInfo(arcname)
                info.type = tarfile.DIRTYPE
                _normalize(info, is_dir=True)
                tar.addfile(info)
            else:
                info = tarfile.TarInfo(arcname)
                info.type = tarfile.REGTYPE
                info.size = path.stat().st_size
                _normalize(info, is_dir=False)
                with open(path, "rb") as f:
                    tar.addfile(info, f)
    os.replace(part_path, out_path)


def emit_fixtures(root: Path, target: Path) -> None:
    """Copy ``tokenizer.model`` + the rewritten ``bundle.json`` into ``target``
    (e.g. ``app/src/test/resources/pocket/`` for JVM tests)."""
    target.mkdir(parents=True, exist_ok=True)
    for name in ("tokenizer.model", "bundle.json"):
        src = root / name
        if not src.is_file():
            raise SystemExit(f"fixture source missing: {src}")
        shutil.copy2(src, target / name)
        print(f"      fixture -> {target / name}")


# --- entry points ------------------------------------------------------------

def selftest() -> int:
    """Offline sanity checks — no network, no writes. Exit 0 on pass."""
    results: list[str] = []
    ok = True

    def check(name: str, cond: bool, detail: str = "") -> None:
        nonlocal ok
        ok = ok and cond
        suffix = f" ({detail})" if detail else ""
        results.append(f"  [{'ok' if cond else 'FAIL'}] {name}{suffix}")

    check(
        "source sha256 is 64 lowercase hex",
        bool(re.fullmatch(r"[0-9a-f]{64}", SOURCE_SHA256)),
    )
    check("archive root is pocket-tts-en/", ARCHIVE_ROOT == "pocket-tts-en/")
    check("source size is 97,291,178", SOURCE_SIZE_BYTES == 97_291_178)
    check(
        "source URL is the v21 mixed tarball",
        "/v21/" in SOURCE_URL
        and SOURCE_URL.endswith("pocket-tts-en-v2026_04-mixed.tar.bz2"),
    )
    check(
        "output name is the -uk variant",
        OUTPUT_NAME == "pocket-tts-en-v2026_04-mixed-uk.tar.bz2",
    )
    check(
        "license map covers exactly the 6 base voices",
        set(BASE_VOICE_LICENSES)
        == {"alba", "azelma", "eponine", "fantine", "javert", "marius"},
    )
    check(
        "base licenses within {CC-BY-4.0, CC0}",
        set(BASE_VOICE_LICENSES.values()) <= {"CC-BY-4.0", "CC0"},
    )
    check("HF URL template targets vctk/", "/vctk/" in HF_VOICE_URL_TEMPLATE)
    try:
        speakers = parse_speaker_list(SPEAKER_LIST)
        check("speaker list parses", True)
        check("speaker list has >= 4 entries", len(speakers) >= 4, f"{len(speakers)} entries")
        check(
            "all speaker accents allowed",
            all(accent in ALLOWED_ACCENTS for _s, accent in speakers),
        )
        check("no duplicate speakers", len({s for s, _a in speakers}) == len(speakers))
    except SystemExit as exc:
        check(f"speaker list parses ({exc})", False)
    check("is_wav rejects junk", not is_wav(b"not a wav"))
    check(
        "is_wav accepts a minimal RIFF/WAVE header",
        is_wav(b"RIFF" + b"\x00" * 4 + b"WAVE" + b"\x00" * 40),
    )

    print("selftest:")
    print("\n".join(results))
    if ok:
        print("selftest OK")
        return 0
    print("selftest FAILED", file=sys.stderr)
    return 1


def build_bundle(output_dir: Path, fixtures_dir: Path | None) -> int:
    speakers = parse_speaker_list(SPEAKER_LIST)
    if not speakers:
        raise SystemExit(f"{SPEAKER_LIST}: no speakers to inject")

    with tempfile.TemporaryDirectory(prefix="pocket-bundle-") as tmp:
        work = Path(tmp)

        print(f"[1/5] downloading {SOURCE_URL}")
        src_tar = work / "source.tar.bz2"
        sha, size = download(SOURCE_URL, src_tar)
        print(f"      {size} bytes, sha256 {sha}")
        if sha != SOURCE_SHA256:
            raise SystemExit(
                f"source sha256 mismatch: got {sha}, want {SOURCE_SHA256}\n"
                "aborting — no output tarball written"
            )
        if size != SOURCE_SIZE_BYTES:
            raise SystemExit(
                f"source size mismatch: got {size}, want {SOURCE_SIZE_BYTES}\n"
                "aborting — no output tarball written"
            )

        print("[2/5] extracting")
        root = extract_source(src_tar, work / "tree")

        print(f"[3/5] injecting {len(speakers)} VCTK voices")
        inject_voices(root, speakers, work)

        print("[4/5] rewriting bundle.json")
        added = rewrite_bundle_json(root, speakers)
        print(f"      predefined_voices += {added}")

        if fixtures_dir is not None:
            emit_fixtures(root, fixtures_dir)

        output_dir.mkdir(parents=True, exist_ok=True)
        out_path = output_dir / OUTPUT_NAME
        print(f"[5/5] writing {out_path}")
        retar(root, out_path)

    out_sha, out_size = sha256_and_size(out_path)
    print()
    print("=== bundle built ===")
    print(f"file:   {out_path}")
    print(f"sha256: {out_sha}")
    print(f"size:   {out_size}")
    print()
    print("attribution rows (paste into the voice-credits / LICENSES doc):")
    for speaker, accent in speakers:
        print(
            f"| vctk_{speaker} | vctk/{speaker}_023_enhanced.wav "
            f"| VCTK ({accent}) | CC-BY-4.0 |"
        )
    print()
    print("paste into PocketEngineCatalog.kt once the bundle is published:")
    print(f'    const val MIKO_BUNDLE_SHA256 = "{out_sha}"')
    print(f"    const val MIKO_BUNDLE_SIZE_BYTES = {out_size}L")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument(
        "--selftest",
        action="store_true",
        help="run offline sanity checks and exit (no network)",
    )
    parser.add_argument(
        "--emit-fixtures",
        metavar="DIR",
        type=Path,
        default=None,
        help="also copy tokenizer.model + the rewritten bundle.json into DIR",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DEFAULT_OUTPUT_DIR,
        help=f"where to write {OUTPUT_NAME} (default: {DEFAULT_OUTPUT_DIR})",
    )
    args = parser.parse_args(argv)
    if args.selftest:
        return selftest()
    return build_bundle(args.output_dir, args.emit_fixtures)


if __name__ == "__main__":
    sys.exit(main())
