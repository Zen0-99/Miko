"""Chatterbox PC render pipeline -> Miko .m4a (+uuid manifest) -> ABS library.

Renders chapter .txt files on this PC with Resemble AI Chatterbox (turbo or
standard) into the same .m4a+manifest-atom interchange the Miko app already
parses (the Phase-5 uuid atom contract — schema frozen, only the engine/voice
label VALUES change: engine="chatterbox-{model}", voice="chatterbox:{hash8}"),
then delivers into an Audiobookshelf library via a watched-folder tree
(--abs-library) or the upload API (--abs-upload).

Pipeline per chapter (scripts/miko_render.py supplies every stage but the
neural net): paragraph split -> KITTEN preprocess -> ~300-char chunks ->
infer_fn -> PCM16 concat with 0.3 s inter-chunk silence -> ffmpeg AAC-LC 96k
mono 44.1 kHz -> tmp-*.m4a -> os.replace -> uuid manifest atom.

Tracer / CI smoke: --stub-infer swaps the model for a 0.4 s sine, so the whole
pipeline runs with no GPU and without torch/chatterbox installed (both are
imported lazily, only on the non-stub path).

Usage:
    python scripts/miko_chatterbox_render.py chap01.txt --voice ref.wav \\
        --out out/ [--abs-library D:/abs/audiobooks --author "Author"]
    python scripts/miko_chatterbox_render.py book_dir/ --voice ref.wav ...
    python scripts/miko_chatterbox_render.py --queue queue.txt --voice ref.wav ...
    python scripts/miko_chatterbox_render.py chap01.txt --voice ref.wav --stub-infer

Queue file format (resumable; mirrors render-chapters.bat semantics):
    name | txt-path | out-dir | status      # one row per line, '#' = comment
Rows whose status is "done" are skipped on re-run; an interrupted row is marked
"partial". Row names must be bare components — '..' path escapes are rejected.

ABS credentials are ENVIRONMENT-ONLY (argv lands in shell history; the token is
never printed):
    ABS_URL          e.g. http://nas:13378
    ABS_TOKEN        API token sent as Bearer   (MIKO_ABS_TOKEN also honored)
    ABS_LIBRARY_ID   target library id — used by --abs-scan and as the upload's
                     'library' field
    ABS_FOLDER       optional upload 'folder' field (default: uploads)
"""

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
import wave

import numpy as np

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

OUT_SR = 44100          # AAC container rate — what ABS/players see
STUB_SR = 24000         # stub PCM rate == Chatterbox's native output rate
STUB_S = 0.4            # seconds of tone per chunk under --stub-infer
ENGINE_LABELS = {"turbo": "chatterbox-turbo", "standard": "chatterbox"}
INVALID_FS = re.compile(r'[\\/:*?"<>|]')
MAX_NAME = 120

# 16-byte extension UUID — MUST equal MIKO_MANIFEST_UUID in
# app/.../ui/reader/novel/tts/render/Mp4Atom.kt (verified: "MikoTTS"+0x01 +
# 7 zero bytes + 0x01). Also matches miko_render.MIKO_UUID.
MIKO_UUID = b"MikoTTS\x01" + b"\x00" * 7 + b"\x01"


def eprint(*a):
    print(*a, file=sys.stderr, flush=True)


# ---------- small helpers ----------

def sha256_8(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for blk in iter(lambda: f.read(1 << 20), b""):
            h.update(blk)
    return h.hexdigest()[:8]


def sanitize_component(name):
    """ABS-safe single path component: strip [\\/:*?"<>|], trim, cap 120."""
    return INVALID_FS.sub("", name).strip(" .")[:MAX_NAME].strip()


def check_name(name):
    """Validate a user-supplied item/file name: reject traversal outright,
    then sanitize. Returns the safe component; raises ValueError on escape."""
    if not name or not name.strip():
        raise ValueError("empty name")
    segs = re.split(r"[/\\]", name.strip())
    if any(s == ".." for s in segs):
        raise ValueError(f"unsafe name (contains '..'): {name!r}")
    if name.startswith(("/", "\\")) or re.match(r"^[A-Za-z]:", name):
        raise ValueError(f"unsafe name (absolute path): {name!r}")
    out = sanitize_component(name)
    if not out:
        raise ValueError(f"name sanitizes to nothing: {name!r}")
    return out


def safe_join(root, *parts):
    """Join + resolve, verifying the result stays inside root."""
    root_r = os.path.realpath(root)
    p = os.path.realpath(os.path.join(root_r, *parts))
    if p != root_r and not p.startswith(root_r + os.sep):
        raise ValueError(f"resolved path escapes root {root_r}: {p}")
    return p


def tmp_path_for(dst):
    return os.path.join(os.path.dirname(os.path.abspath(dst)),
                        "tmp-" + os.path.basename(dst))


def write_json_atomic(path, obj):
    tmp = tmp_path_for(path)
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)


def copy_atomic(src, dst):
    tmp = tmp_path_for(dst)
    shutil.copyfile(src, tmp)
    os.replace(tmp, dst)


def _find_manifest_atom(data):
    """Forward top-level atom walk (mirrors Mp4AtomReader.kt) ->
    (offset, size, header_size) of the Miko `uuid` atom, or None."""
    pos, n = 0, len(data)
    while pos + 8 <= n:
        size = int.from_bytes(data[pos:pos + 4], "big")
        fourcc = data[pos + 4:pos + 8]
        hdr = 8
        if size == 1:
            if pos + 16 > n:
                return None
            size = int.from_bytes(data[pos + 8:pos + 16], "big")
            hdr = 16
        elif size == 0:
            size = n - pos
        if size < hdr or pos + size > n:
            return None
        if (fourcc == b"uuid" and size >= hdr + 16
                and data[pos + hdr:pos + hdr + 16] == MIKO_UUID):
            return pos, size, hdr
        pos += size
    return None


def read_manifest(m4a_path):
    """Parse the trailing uuid atom -> manifest dict, or None. Never throws."""
    try:
        data = open(m4a_path, "rb").read()
        hit = _find_manifest_atom(data)
        if not hit:
            return None
        pos, size, hdr = hit
        payload = data[pos + hdr + 16:pos + size]
        return json.loads(payload.decode("utf-8"))
    except Exception:
        return None


def manifest_atom_bytes(m4a_path):
    """Raw bytes of the Miko uuid atom (header+uuid+payload), or None —
    for re-appending after an ffmpeg stream-copy remux, which drops unknown
    top-level atoms."""
    try:
        data = open(m4a_path, "rb").read()
        hit = _find_manifest_atom(data)
        if not hit:
            return None
        pos, size, _ = hit
        return data[pos:pos + size]
    except Exception:
        return None


# ---------- voice ref + model ----------

def probe_voice_ref(path, allow_torch=True):
    """--voice must exist and be decodable audio (soundfile/wave/torchaudio
    probe). Returns None on success; raises ValueError otherwise."""
    if not os.path.isfile(path):
        raise ValueError(f"no such file: {path}")
    ok = None
    try:
        import soundfile as sf
        try:
            ok = sf.info(path).frames > 0
        except Exception:
            ok = False
    except ImportError:
        pass
    if ok is None and path.lower().endswith(".wav"):
        try:
            with wave.open(path, "rb") as w:
                ok = w.getnframes() > 0
        except Exception:
            ok = False
    if ok is None and allow_torch:
        try:
            import torchaudio
            try:
                torchaudio.info(path)
                ok = True
            except Exception:
                ok = False
        except ImportError:
            pass
    if ok is None:
        eprint(f"  warning: cannot verify voice ref {path} "
               "(no soundfile/torchaudio installed) — continuing")
        return
    if not ok:
        raise ValueError(f"not decodable audio: {path}")


def load_model(model_key, device=None):
    """Lazy chatterbox/torch import — NEVER called under --stub-infer.

    turbo -> chatterbox.tts_turbo (ChatterboxMultilingualTTS, the turbo-era
    package export; ChatterboxTurboTTS as fallback spelling), standard ->
    chatterbox.tts.ChatterboxTTS. Returns the loaded model; model.sr is read
    by the caller for the manifest's sourceSampleRate.
    """
    if model_key == "turbo":
        try:
            from chatterbox.tts_turbo import ChatterboxMultilingualTTS as cls
        except ImportError:
            from chatterbox.tts_turbo import ChatterboxTurboTTS as cls
    else:
        from chatterbox.tts import ChatterboxTTS as cls
    if device is None:
        try:
            import torch
            device = "cuda" if torch.cuda.is_available() else "cpu"
        except ImportError:
            device = "cpu"
    return cls.from_pretrained(device=device)


def to_pcm(wav):
    """chatterbox generate() output (torch tensor or ndarray) -> float32 1-D."""
    if hasattr(wav, "detach"):
        wav = wav.detach().float().cpu().numpy()
    return np.asarray(wav, dtype=np.float32).ravel()


def stub_infer(_chunk):
    """0.4 s 220 Hz sine per chunk — exercises the real pipeline model-free."""
    t = np.arange(int(STUB_S * STUB_SR), dtype=np.float32) / STUB_SR
    return 0.2 * np.sin(2.0 * np.pi * 220.0 * t)


# ---------- render ----------

def render_chapter(txt_path, out_m4a, title, *, infer_fn, source_sr,
                   engine_label, voice_label):
    """txt -> tmp-<name>.m4a -> os.replace(out_m4a). Returns the manifest
    dict embedded by miko_render.render (caller uses it for ABS chapters)."""
    import miko_render  # lazy — onnxruntime stays optional for this pipeline
    out_dir = os.path.dirname(os.path.abspath(out_m4a))
    os.makedirs(out_dir, exist_ok=True)
    tmp = tmp_path_for(out_m4a)
    try:
        manifest = miko_render.render(
            txt_path, tmp, title,
            infer_fn=infer_fn, engine_label=engine_label,
            voice_label=voice_label,
            sample_rate=OUT_SR, source_sample_rate=source_sr)
        os.replace(tmp, out_m4a)
    finally:
        if os.path.exists(tmp):
            try:
                os.remove(tmp)
            except OSError:
                pass
    return manifest


# ---------- ABS watched-folder tree ----------

def abs_series_label(series, book_number):
    if not series:
        return None
    bn = str(book_number).strip() if book_number is not None else ""
    return f"{series} #{bn}" if bn else series


class AbsBookWriter:
    """<root>/<Author>/<Title>/ item writer (SD-3). Incrementally adds
    '{NN} - {name}.m4a' tracks (ffmpeg stream-copy retag of the iTunes trkn
    tag) and rewrites metadata.json after each add, so an interrupted run
    still leaves a valid partial item. All writes are tmp-* + os.replace;
    every component is sanitized and resolved inside the root."""

    def __init__(self, *, root, author, title, series=None, book_number=None,
                 narrators=None, isbn=None, year=None, description="",
                 cover=None):
        a = sanitize_component(author) or "Unknown Author"
        t = check_name(title)
        if series:
            s = check_name(series)
            bn = str(book_number).strip() if book_number is not None else ""
            item_name = f"{bn} - {t}" if bn else t
            self.item_dir = safe_join(root, a, s, check_name(item_name))
        else:
            self.item_dir = safe_join(root, a, t)
        os.makedirs(self.item_dir, exist_ok=True)
        self.title = title
        self.author = author or ""
        self.series_label = abs_series_label(series, book_number)
        self.narrators = list(narrators or [])
        self.isbn = isbn or ""
        self.year = str(year) if year else ""
        self.description = description or ""
        self.cover = cover or None
        self.chapters = []
        self.cursor = 0.0
        self._cover_done = False

    def add_track(self, src_m4a, name, track_no, duration_s):
        base = sanitize_component(f"{track_no:02d} - {name}") \
               or f"{track_no:02d} - track"
        dst = safe_join(self.item_dir, base + ".m4a")
        if not os.path.exists(dst):
            tmp = safe_join(self.item_dir, "tmp-" + base + ".m4a")
            subprocess.run(
                ["ffmpeg", "-y", "-i", src_m4a, "-c", "copy",
                 "-metadata", f"track={track_no}", tmp],
                check=True, capture_output=True)
            # the remux drops the trailing Miko uuid atom — re-append it so
            # ABS-delivered files keep paragraph offsets for read-along
            atom = manifest_atom_bytes(src_m4a)
            if atom:
                with open(tmp, "ab") as f:
                    f.write(atom)
            os.replace(tmp, dst)
        self.chapters.append({"start": round(self.cursor, 3),
                              "end": round(self.cursor + float(duration_s), 3),
                              "title": name})
        self.cursor += float(duration_s)
        meta = {
            "title": self.title,
            "authors": [self.author] if self.author else [],
            "narrators": self.narrators,
            "series": [self.series_label] if self.series_label else [],
            "publishedYear": self.year,
            "isbn": self.isbn,
            "description": self.description,
            "chapters": self.chapters,
        }
        write_json_atomic(os.path.join(self.item_dir, "metadata.json"), meta)
        if self.cover and not self._cover_done:
            if os.path.isfile(self.cover):
                copy_atomic(self.cover, os.path.join(self.item_dir, "cover.jpg"))
            else:
                eprint(f"  warning: cover not found: {self.cover}")
            self._cover_done = True
        return dst


# ---------- ABS HTTP (env-only credentials) ----------

def abs_env(name):
    return os.environ.get(name) or os.environ.get("MIKO_" + name) or ""


def _post(url, body, headers):
    """Thin urllib wrapper (monkeypatched in tests). Returns (status, text).
    The Bearer token travels inside `headers` only — never logged here or by
    callers."""
    req = urllib.request.Request(url, data=body, headers=headers,
                                 method="POST")
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except urllib.error.URLError as e:
        raise SystemExit(f"POST {url} failed: {e.reason}")


def upload_abs(m4a_path, *, title, author="", series_label=None):
    """POST {ABS_URL}/api/upload multipart {file, title, author, series,
    library, folder} — for servers where the library filesystem isn't
    reachable. Credentials from env only. Returns True on 2xx."""
    base = abs_env("ABS_URL").rstrip("/")
    token = abs_env("ABS_TOKEN")
    if not base or not token:
        eprint("  warning: --abs-upload needs ABS_URL and ABS_TOKEN env vars "
               "— upload skipped")
        return False
    library = abs_env("ABS_LIBRARY_ID")
    folder = abs_env("ABS_FOLDER") or "uploads"
    boundary = "miko-" + hashlib.sha1(os.urandom(16)).hexdigest()
    fields = {"title": title, "author": author, "library": library,
              "folder": folder}
    if series_label:
        fields["series"] = series_label
    body = b""
    for k, v in fields.items():
        body += (f"--{boundary}\r\nContent-Disposition: form-data; "
                 f'name="{k}"\r\n\r\n{v}\r\n').encode("utf-8")
    fname = os.path.basename(m4a_path)
    body += (f"--{boundary}\r\nContent-Disposition: form-data; "
             f'name="file"; filename="{fname}"\r\n'
             "Content-Type: audio/mp4\r\n\r\n").encode("utf-8")
    with open(m4a_path, "rb") as f:
        body += f.read()
    body += f"\r\n--{boundary}--\r\n".encode("utf-8")
    status, text = _post(
        f"{base}/api/upload", body,
        {"Authorization": f"Bearer {token}",
         "Content-Type": f"multipart/form-data; boundary={boundary}"})
    if 200 <= status < 300:
        eprint(f"  uploaded {fname} -> ABS library {library or '(default)'}"
               f"/{folder} ({status})")
        return True
    eprint(f"  warning: upload of {fname} failed ({status}) — the server may "
           "not honor the metadata fields on this version (RESEARCH §12 Q8); "
           "watched-folder delivery stays the reliable path")
    return False


def abs_scan():
    """POST {ABS_URL}/api/libraries/{ABS_LIBRARY_ID}/scan — all three env vars
    required; absent -> warn + skip. Token never printed."""
    base = abs_env("ABS_URL").rstrip("/")
    token = abs_env("ABS_TOKEN")
    lib = abs_env("ABS_LIBRARY_ID")
    if not (base and token and lib):
        eprint("  warning: --abs-scan needs ABS_URL, ABS_TOKEN and "
               "ABS_LIBRARY_ID env vars — scan skipped")
        return False
    status, _ = _post(f"{base}/api/libraries/{lib}/scan", b"",
                      {"Authorization": f"Bearer {token}"})
    if 200 <= status < 300:
        eprint(f"  ABS library scan triggered (library {lib})")
        return True
    eprint(f"  warning: scan request failed ({status})")
    return False


# ---------- queue ----------

def load_queue(path):
    """Parse 'name | txt-path | out-dir | status' rows ('#' comments, blanks
    skipped). Relative txt paths resolve against the queue file's dir. The
    original columns are kept in 'cols' for byte-stable rewrites."""
    rows = []
    qdir = os.path.dirname(os.path.abspath(path))
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            cols = [c.strip() for c in line.split("|")]
            while len(cols) < 4:
                cols.append("")
            name, txt_raw, out_raw, status = cols[:4]
            txt = txt_raw
            if txt and not os.path.isabs(txt):
                txt = os.path.normpath(os.path.join(qdir, txt))
            rows.append({"cols": cols[:4], "name": name, "txt": txt,
                         "out": out_raw, "status": status})
    return rows


def save_queue(path, rows):
    """Atomic rewrite — tmp- sibling then os.replace; only the status column
    changes."""
    tmp = tmp_path_for(path)
    with open(tmp, "w", encoding="utf-8") as f:
        for r in rows:
            cols = list(r["cols"])
            cols[3] = r["status"]
            f.write(" | ".join(cols) + "\n")
    os.replace(tmp, path)


# ---------- job construction ----------

def build_jobs(args):
    """-> [{name, txt, out, m4a, status?, cols?}]. Names are validated
    (traversal rejected) before anything renders."""
    jobs = []
    if args.queue:
        rows = load_queue(args.queue)
        if not rows:
            raise SystemExit(f"queue file has no rows: {args.queue}")
        for r in rows:
            try:
                r["name"] = check_name(r["name"])
            except ValueError as e:
                raise SystemExit(f"queue row rejected: {e}")
            out_dir = r["out"] or args.out
            r["m4a"] = os.path.join(out_dir, r["name"] + ".m4a")
            if r["status"] != "done" and not os.path.isfile(r["txt"]):
                eprint(f"  warning: txt missing for '{r['name']}': {r['txt']}")
            jobs.append(r)
        return jobs
    inp = args.input
    if os.path.isdir(inp):
        names = sorted(f for f in os.listdir(inp) if f.lower().endswith(".txt"))
        if not names:
            raise SystemExit(f"no .txt files in {inp}")
        for f in names:
            try:
                stem = check_name(os.path.splitext(f)[0])
            except ValueError as e:
                raise SystemExit(f"input filename rejected: {e}")
            jobs.append({"name": stem, "txt": os.path.join(inp, f),
                         "out": args.out,
                         "m4a": os.path.join(args.out, stem + ".m4a")})
    elif os.path.isfile(inp):
        try:
            stem = check_name(os.path.splitext(os.path.basename(inp))[0])
        except ValueError as e:
            raise SystemExit(f"input filename rejected: {e}")
        jobs.append({"name": stem, "txt": inp, "out": args.out,
                     "m4a": os.path.join(args.out, stem + ".m4a")})
    else:
        raise SystemExit(f"input not found: {inp}")
    return jobs


def group_jobs(args, jobs):
    """Assign each job an ABS item title ('group'). --title forces a single
    item for all jobs; dir input groups under the directory name; queue rows
    are independent items unless --title groups them; single file = itself."""
    groups = {}
    if args.title:
        g = args.title
        for j in jobs:
            j["group"] = g
            groups.setdefault(g, []).append(j)
    elif args.queue:
        for j in jobs:
            j["group"] = j["name"]
            groups.setdefault(j["name"], []).append(j)
    elif args.input and os.path.isdir(args.input):
        g = os.path.basename(os.path.normpath(args.input))
        for j in jobs:
            j["group"] = g
            groups.setdefault(g, []).append(j)
    else:
        g = jobs[0]["name"]
        for j in jobs:
            j["group"] = g
            groups.setdefault(g, []).append(j)
    return groups


# ---------- CLI ----------

def build_parser():
    ap = argparse.ArgumentParser(
        prog="miko_chatterbox_render",
        description="Render chapter .txt -> Chatterbox TTS -> Miko .m4a "
                    "(uuid manifest) -> ABS watched-folder tree or upload.",
        epilog="ABS credentials come from env vars only: ABS_URL, ABS_TOKEN, "
               "ABS_LIBRARY_ID, ABS_FOLDER (see module docstring).")
    ap.add_argument("input", nargs="?",
                    help="chapter .txt file or a directory of .txt files")
    ap.add_argument("--queue", metavar="FILE",
                    help="queue file: 'name | txt-path | out-dir | status' rows")
    ap.add_argument("--voice", required=True, metavar="REF_WAV",
                    help="reference audio for voice cloning (sha256 -> "
                         "chatterbox:{hash8} voice label)")
    ap.add_argument("--out", default="./out", metavar="DIR",
                    help="output dir for rendered .m4a (default ./out)")
    ap.add_argument("--model", choices=["turbo", "standard"], default="turbo",
                    help="chatterbox variant (default turbo -> engine label "
                         "'chatterbox-turbo'; standard -> 'chatterbox')")
    ap.add_argument("--device", default=None,
                    help="torch device (default: cuda if available else cpu)")
    ap.add_argument("--stub-infer", action="store_true",
                    help="replace the model with a 0.4 s sine — full pipeline "
                         "smoke test, no GPU/torch/chatterbox needed")
    ap.add_argument("--exaggeration", type=float, default=0.5)
    ap.add_argument("--cfg-weight", type=float, default=0.5)
    ap.add_argument("--abs-library", metavar="ROOT",
                    help="ABS library folder root — writes "
                         "<Author>/<Title>/NN - name.m4a + metadata.json")
    ap.add_argument("--abs-upload", action="store_true",
                    help="POST each .m4a to {ABS_URL}/api/upload "
                         "(ABS_URL/ABS_TOKEN env required)")
    ap.add_argument("--abs-scan", action="store_true",
                    help="POST {ABS_URL}/api/libraries/{ABS_LIBRARY_ID}/scan "
                         "after rendering (env-gated; warns + skips if unset)")
    ap.add_argument("--author", default="")
    ap.add_argument("--narrator", default="",
                    help="ABS narrator name (the chatterbox:{hash8} voice "
                         "label is always added to narrators[])")
    ap.add_argument("--series", default="")
    ap.add_argument("--book-number", default="",
                    help="series sequence N — '<Series> #N' sidecar label and "
                         "'<Author>/<Series>/<N> - <Title>/' folder")
    ap.add_argument("--isbn", default="")
    ap.add_argument("--year", default="", metavar="YYYY",
                    help="publishedYear metadata")
    ap.add_argument("--cover", default="", metavar="IMG",
                    help="cover image copied to cover.jpg in the ABS item")
    ap.add_argument("--title", default="",
                    help="ABS item title override (groups all inputs into one "
                         "item; default: file stem / dir name / queue row name)")
    ap.add_argument("--track", type=int, default=0,
                    help="track number tag for single-chapter renders "
                         "(default: auto)")
    return ap


def main(argv=None):
    ap = build_parser()
    args = ap.parse_args(argv)
    if args.queue and args.input:
        ap.error("positional input and --queue are mutually exclusive")
    if not args.queue and not args.input:
        ap.error("need a .txt file, a directory of .txt files, or --queue FILE")

    ref = os.path.abspath(args.voice)
    try:
        probe_voice_ref(ref, allow_torch=not args.stub_infer)
    except ValueError as e:
        ap.error(f"--voice: {e}")
    voice_label = f"chatterbox:{sha256_8(ref)}"
    engine_label = ENGINE_LABELS[args.model]
    narrators = ([args.narrator] if args.narrator else []) + [voice_label]
    series_label = abs_series_label(args.series, args.book_number)

    jobs = build_jobs(args)
    groups = group_jobs(args, jobs)
    # stable per-item track numbering across queue resumes
    track_no = {}
    for _title, js in groups.items():
        for i, j in enumerate(js, 1):
            track_no[id(j)] = args.track if (args.track and len(js) == 1) else i

    # ---- engine (fail fast before any rendering) ----
    if args.stub_infer:
        infer_fn = stub_infer
        src_sr = STUB_SR
        eprint("stub-infer: 0.4 s sine per chunk (no model loaded)")
    else:
        try:
            model_obj = load_model(args.model, device=args.device)
        except ImportError as e:
            raise SystemExit(
                f"chatterbox not installed ({e}) — `pip install chatterbox-tts`"
                ", or smoke-test the pipeline with --stub-infer")
        except Exception as e:
            raise SystemExit(f"failed to load chatterbox '{args.model}': {e}")
        src_sr = int(getattr(model_obj, "sr", 0) or 24000)
        eprint(f"chatterbox '{args.model}' loaded (sr={src_sr})")

        def infer_fn(chunk):
            return to_pcm(model_obj.generate(
                chunk, audio_prompt_path=ref,
                cfg_weight=args.cfg_weight, exaggeration=args.exaggeration))

    # ---- render loop ----
    writers = {}

    def abs_add(job, manifest):
        if not args.abs_library:
            return
        g = job["group"]
        w = writers.get(g)
        if w is None:
            w = AbsBookWriter(
                root=args.abs_library, author=args.author, title=g,
                series=args.series, book_number=args.book_number,
                narrators=narrators, isbn=args.isbn, year=args.year,
                cover=args.cover)
            writers[g] = w
        w.add_track(job["m4a"], job["name"], track_no[id(job)],
                    manifest.get("durationMs", 0) / 1000.0)

    rendered = errs = skipped = 0
    total = len(jobs)
    for idx, job in enumerate(jobs, 1):
        if job.get("status") == "done":
            skipped += 1
            eprint(f"[{idx}/{total}] {job['name']} — already done, skipped")
            # keep the ABS group complete on a resumed run
            if args.abs_library and os.path.exists(job["m4a"]):
                man = read_manifest(job["m4a"])
                if man:
                    abs_add(job, man)
            continue
        try:
            manifest = render_chapter(
                job["txt"], job["m4a"], job["name"],
                infer_fn=infer_fn, source_sr=src_sr,
                engine_label=engine_label, voice_label=voice_label)
        except KeyboardInterrupt:
            if args.queue:
                job["status"] = "partial"
                save_queue(args.queue, jobs)
            eprint("interrupted — queue saved, rerun to resume")
            return 130
        except SystemExit:
            raise
        except Exception as e:
            errs += 1
            if args.queue:
                job["status"] = "failed"
                save_queue(args.queue, jobs)
                eprint(f"[{idx}/{total}] {job['name']} FAILED: {e}")
                continue
            raise
        rendered += 1
        if args.queue:
            job["status"] = "done"
            save_queue(args.queue, jobs)
        abs_add(job, manifest)
        if args.abs_upload:
            upload_abs(job["m4a"], title=job["group"], author=args.author,
                       series_label=series_label)
        eprint(f"[{idx}/{total}] {job['name']} — "
               f"{len(manifest['paragraphOffsetsMs'])}p — "
               f"{manifest['durationMs'] / 1000:.0f}s — {job['m4a']}")

    # ---- ABS wrap-up ----
    if args.abs_library:
        root = os.path.abspath(args.abs_library)
        eprint(f"ABS tree written under {root}"
               + ("" if not writers else
                  f" ({len(writers)} item(s))"))
        if args.abs_scan:
            abs_scan()
        else:
            eprint("rescan your ABS library to import (or rerun "
                   "with --abs-scan)")
    elif args.abs_scan:
        abs_scan()

    eprint(f"done: {rendered} rendered"
           + (f", {skipped} skipped" if skipped else "")
           + (f", {errs} failed" if errs else ""))
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main())
