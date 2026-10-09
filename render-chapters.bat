@where python >nul 2>&1 || (echo Python 3 is required - not found on PATH. & pause & exit /b 1) & python -x "%~f0" %* & exit /b
"""Miko chapter renderer — pick a novel, render N unread chapters on THIS PC,
push the finished .m4a files back to the phone.

Single-file batch: line 1 is a cmd trampoline, everything below is Python
(invoked via `python -x`, which skips line 1). Rendering uses scripts/
miko_render.py — the faithful PC port of the in-app Supertonic pipeline
(spike 007) — with onnxruntime CUDA when available.

Flow:
  1. Resolve an adb device (cached serial -> `adb devices` -> mDNS -> manual ip:port)
  2. Pull the novel database via run-as -> miko-render-cache\\tachiyomi.noveldb
  3. Show library novels with unread counts, pick one
  4. Show reading position (first unread chapter), ask how many to render —
     N means N actual renders; already-rendered chapters are skipped free
  5. Per chapter: ensure the .txt exists on the device (download-only broadcast
     if missing), pull it, render .m4a locally, push to <novel>/audio/

Requires the DEBUG build (xyz.jmir.miko.dev) on the phone — it provides the
download trigger receiver. Interrupt-safe: an unfinished queue is saved to
miko-render-cache\\queue.txt and offered for resume on the next run.

NOTE (phase 22-05): the primary transport for PC renders is now the ABS
watched-folder pipeline (scripts/miko_chatterbox_render.py --abs-library);
this .bat remains the direct-ADB fallback path.

Env overrides:
  MIKO_VOICE_ID     voice index (default 4 = F5)
  MIKO_RENDER_STEPS diffusion steps (default 8)
  RENDER_CPU=1      force CPU (skip CUDA)
  MIKO_DOWNLOADS_DIR  absolute device path to the downloads tree
"""
import os
import re
import sqlite3
import subprocess
import sys
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CACHE_DIR = os.path.join(SCRIPT_DIR, "miko-render-cache")
DB_CACHE = os.path.join(CACHE_DIR, "tachiyomi.noveldb")
DEVICE_TXT = os.path.join(CACHE_DIR, "device.txt")
QUEUE_TXT = os.path.join(CACHE_DIR, "queue.txt")
TXT_DIR = os.path.join(CACHE_DIR, "txt")
OUT_DIR = os.path.join(CACHE_DIR, "audio")

sys.path.insert(0, os.path.join(SCRIPT_DIR, "scripts"))

PKG = "xyz.jmir.miko.dev"
ACTION = "xyz.jmir.miko.debug.RENDER_CHAPTER"
RECEIVER = "xyz.jmir.miko.dev/eu.kanade.tachiyomi.debug.RenderChapterReceiver"
REMOTE_DB = "databases/tachiyomi.noveldb"

# Where the app stores downloads on the device (user-picked storage root).
DOWNLOAD_ROOT_CANDIDATES = [
    "/sdcard/Documents/Yokai Debug/downloads",
    "/sdcard/Documents/Miko Debug/downloads",
    "/sdcard/Documents/Miko/downloads",
    "/sdcard/Audiobooks",
]

ADB = None       # resolved in main()
DEVICE = None    # adb serial
DOWNLOADS = None  # device downloads root


def run(args, timeout=30):
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout)


def shell(cmd, timeout=30):
    """adb shell — cmd is a single string, quote remote paths with shq()."""
    return run([ADB, "-s", DEVICE, "shell", cmd], timeout=timeout)


def shq(path):
    """Single-quote a path for `adb shell` (handles spaces and apostrophes)."""
    return "'" + path.replace("'", "'\\''") + "'"


def find_adb():
    candidates = [
        "adb",
        os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"),
    ]
    for cand in candidates:
        try:
            if subprocess.run([cand, "version"], capture_output=True).returncode == 0:
                return cand
        except FileNotFoundError:
            continue
    return None


def device_ok(serial):
    try:
        return run([ADB, "-s", serial, "get-state"], timeout=10).stdout.strip() == "device"
    except Exception:
        return False


def connected_serials():
    try:
        r = run([ADB, "devices"], timeout=10)
    except Exception:
        return []
    return [
        line.split()[0]
        for line in r.stdout.splitlines()
        if line.strip() and not line.startswith("List") and line.split()[-1] == "device"
    ]


def resolve_device():
    cached = ""
    if os.path.exists(DEVICE_TXT):
        cached = open(DEVICE_TXT, encoding="utf-8").read().strip()
    if cached and device_ok(cached):
        return cached

    serials = connected_serials()
    if len(serials) == 1:
        return serials[0]
    if len(serials) > 1:
        print("\nMultiple devices connected:")
        for i, s in enumerate(serials, 1):
            print(f"  [{i}] {s}")
        pick = input("Which one? ").strip()
        if pick.isdigit() and 1 <= int(pick) <= len(serials):
            return serials[int(pick) - 1]

    candidates = [cached] if cached else []
    try:
        mdns = run([ADB, "mdns", "services"], timeout=15).stdout
        candidates += re.findall(r"(\d{1,3}(?:\.\d{1,3}){3}:\d+)", mdns)
    except Exception:
        pass
    for cand in dict.fromkeys(c for c in candidates if c):
        print(f"Trying adb connect {cand} ...")
        run([ADB, "connect", cand], timeout=15)
        if device_ok(cand):
            return cand

    print("\nNo device reachable. On the phone:")
    print("  Settings > Developer options > Wireless debugging")
    print("  (the toggle screen shows  IP:PORT , e.g. 192.168.1.139:40025)")
    serial = input("Enter IP:PORT (blank = continue without device): ").strip()
    if serial:
        run([ADB, "connect", serial], timeout=15)
        if device_ok(serial):
            return serial
        print("Could not connect to that address.")
    return None


def remote_exists(path):
    return shell(f"test -f {shq(path)} && echo yes").stdout.strip() == "yes"


def remote_is_dir(path):
    return shell(f"test -d {shq(path)} && echo yes").stdout.strip() == "yes"


def find_downloads_root(source_dir):
    """Locate the app's downloads tree by checking which root holds the
    picked novel's source dir."""
    override = os.environ.get("MIKO_DOWNLOADS_DIR", "").strip()
    roots = [override] if override else DOWNLOAD_ROOT_CANDIDATES
    for root in roots:
        if remote_is_dir(f"{root}/{source_dir}"):
            return root
    # fall back to any candidate that exists at all
    for root in roots:
        if root and remote_is_dir(root):
            return root
    return None


def pull_db():
    p = subprocess.run(
        [ADB, "-s", DEVICE, "exec-out", "run-as", PKG, "cat", REMOTE_DB],
        capture_output=True,
    )
    if p.returncode == 0 and len(p.stdout) > 4096:
        with open(DB_CACHE, "wb") as f:
            f.write(p.stdout)
        return True
    return False


def query(sql, params=()):
    con = sqlite3.connect(DB_CACHE)
    try:
        return con.execute(sql, params).fetchall()
    finally:
        con.close()


# --- filename rules ported from DiskUtil.buildValidFilename /
#     NovelDownloadProvider.getChapterDirName -------------------------------

INVALID_CHARS = set('"*/:<>?\\|')


def valid_filename(name):
    name = name.strip(". ")
    if not name:
        return "(invalid)"
    out = "".join(
        c if (ord(c) > 0x1F and ord(c) != 0x7F and c not in INVALID_CHARS) else "_"
        for c in name
    )
    return out[:240]


def chapter_basename(chapter_name, scanlator):
    base = chapter_name if chapter_name.strip() else "Chapter"
    if scanlator and scanlator.strip():
        base = f"{scanlator.strip()}_{base}"
    return valid_filename(base)


def source_dir_name(source_name, lang):
    return valid_filename(f"{source_name} ({(lang or 'en').upper()})")


def ask(prompt, valid):
    while True:
        try:
            ans = input(prompt).strip()
        except EOFError:
            print("\nNo input - exiting.")
            sys.exit(0)
        if valid(ans):
            return ans
        print("  invalid input - try again.")


def save_queue(queue, remaining=None):
    with open(QUEUE_TXT, "w", encoding="utf-8") as f:
        if remaining is not None:
            f.write(f"#remaining\t{remaining}\n")
        for row in queue:
            f.write("\t".join(str(x) for x in row) + "\n")


def load_queue():
    """Returns (pending_rows, remaining_target). remaining_target is None when
    the saved run was 'all' or the file predates the header."""
    if not os.path.exists(QUEUE_TXT):
        return [], None
    remaining = None
    out = []
    for line in open(QUEUE_TXT, encoding="utf-8"):
        parts = line.rstrip("\n").split("\t")
        if parts[0] == "#remaining":
            remaining = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else None
        elif parts and parts[0].isdigit():
            # (chapter_id, name, scanlator, remote_txt, remote_m4a)
            out.append(tuple(parts))
    return out, remaining


def broadcast_download(chapter_id):
    """Fire-and-mostly-forget: the receiver kicks the download job and returns
    fast, but even if `am` stalls we don't fail — the caller polls for the
    chapter text anyway, and the receiver keeps working device-side."""
    try:
        subprocess.run(
            [ADB, "-s", DEVICE, "shell", "am", "broadcast",
             "-a", ACTION, "-n", RECEIVER,
             "--el", "chapter_id", str(chapter_id),
             "--ez", "download_only", "true"],
            capture_output=True, text=True, timeout=60,
        )
    except subprocess.TimeoutExpired:
        print("      broadcast ack timed out - continuing to poll for the text")


def ensure_chapter_txt(chapter_id, remote_txt, name):
    """Make sure the chapter text exists on the device; download via the
    debug receiver if not. Returns True once the file is visible."""
    if remote_exists(remote_txt):
        return True
    print(f"      '{name}' not downloaded - asking the app to fetch it...")
    broadcast_download(chapter_id)
    deadline = time.time() + 180
    while time.time() < deadline:
        if remote_exists(remote_txt):
            return True
        time.sleep(3)
    print("      download timed out.")
    return False


def render_queue(queue, novel_dir, target=None):
    """queue rows: (chapter_id, name, scanlator) — the novel's full unread list
    in reading order. target = how many chapters to actually render (None =
    all); already-rendered skips don't count toward it."""
    pending = [
        (str(cid), name, chapter_basename(name, scanlator),
         f"{novel_dir}/{chapter_basename(name, scanlator)}.txt",
         f"{novel_dir}/audio/{chapter_basename(name, scanlator)}.m4a")
        for cid, name, scanlator in queue
    ]
    process_pending(pending, target)


def process_pending(pending, target=None):
    """Rows: (chapter_id, name, base, remote_txt, remote_m4a).
    Pulls the chapter .txt, renders the .m4a on this PC, pushes it into the
    novel's audio/ dir. Stops after `target` successful renders (None = drain
    the list); skipped/failed chapters don't count. Saves leftovers to
    queue.txt for resume."""
    if not pending:
        return
    try:
        import miko_render  # lazy — heavy onnxruntime import only when needed
    except ImportError as e:
        print(f"\nMissing dependency: {e}")
        print("Install with:  pip install onnxruntime-gpu numpy")
        print("(onnxruntime alone falls back to CPU rendering; ffmpeg must be on PATH)")
        return

    novel_dir = os.path.dirname(pending[0][3])
    shell(f"mkdir -p {shq(novel_dir + '/audio')}")
    rendered = 0
    save_queue(pending, target)
    try:
        while pending and (target is None or rendered < target):
            cid, name, base, remote_txt, remote_m4a = pending[0]

            def checkpoint():
                save_queue(pending, target - rendered if target else None)

            if remote_exists(remote_m4a):
                print(f"\n  skip  {name} (already rendered on device)")
                pending.pop(0)
                checkpoint()
                continue
            label = f"{rendered + 1}/{target}" if target else f"{rendered + 1}"
            print(f"\n[{label}] {name}")
            if not ensure_chapter_txt(int(cid), remote_txt, name):
                print("      no chapter text - skipping")
                pending.pop(0)
                checkpoint()
                continue
            local_txt = os.path.join(TXT_DIR, base + ".txt")
            subprocess.run([ADB, "-s", DEVICE, "pull", remote_txt, local_txt],
                           capture_output=True, timeout=60)
            if not (os.path.exists(local_txt) and os.path.getsize(local_txt) >= 100):
                print("      pull failed - skipping")
                pending.pop(0)
                checkpoint()
                continue
            local_m4a = os.path.join(OUT_DIR, base + ".m4a")
            t0 = time.time()
            miko_render.render(local_txt, local_m4a, name)
            print(f"      rendered in {(time.time() - t0) / 60:.1f} min - pushing")
            r = subprocess.run([ADB, "-s", DEVICE, "push", local_m4a, remote_m4a],
                               capture_output=True, text=True, timeout=300)
            if r.returncode != 0:
                print(f"      PUSH FAILED: {r.stderr.strip()}")
                break
            print("      pushed.")
            rendered += 1
            pending.pop(0)
            checkpoint()
    except KeyboardInterrupt:
        print("\nInterrupted.")
    finally:
        if pending:
            left = target - rendered if target else None
            save_queue(pending, left)
            print(f"Saved {len(pending)} unfinished chapter(s)"
                  + (f" ({left} render(s) left)" if left else "")
                  + " - resume next run.")
        elif os.path.exists(QUEUE_TXT):
            os.remove(QUEUE_TXT)
        print(f"\nRendered {rendered} chapter(s)"
              + (f" of {target} requested." if target else "."))


def main():
    global ADB, DEVICE, DOWNLOADS
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    for d in (CACHE_DIR, TXT_DIR, OUT_DIR):
        os.makedirs(d, exist_ok=True)

    ADB = find_adb()
    if ADB is None:
        print("adb not found (looked on PATH and in the default SDK folder).")
        return

    DEVICE = resolve_device()
    if DEVICE:
        with open(DEVICE_TXT, "w", encoding="utf-8") as f:
            f.write(DEVICE)
        print(f"\nDevice: {DEVICE}")
        if pull_db():
            print("Library database pulled -> cache refreshed.")
        elif os.path.exists(DB_CACHE):
            print("DB pull failed - using the cached snapshot.")
        else:
            print("DB pull failed and no cache exists yet.")
            return
    elif os.path.exists(DB_CACHE):
        print("\nNo device - using the cached library snapshot.")
    else:
        print("\nNo device and no cached library - nothing to do.")
        return

    # ---- resume an interrupted queue? ----
    pending, remaining = load_queue()
    if pending:
        print(f"\nFound a saved queue of {len(pending)} chapter(s) from a previous run"
              + (f" ({remaining} render(s) left)." if remaining else "."))
        if DEVICE is None:
            print("Connect the device to resume it.")
        elif ask("Resume it now? [y/n] ", lambda a: a.lower() in ("y", "n")) == "y":
            process_pending(pending, remaining)
            return
        else:
            os.remove(QUEUE_TXT)

    # ---- pick a novel ----
    novels = query(
        "SELECT n._id, n.title, s.name AS sname, s.lang, "
        " (SELECT COUNT(*) FROM novelchapters c "
        "   WHERE c.novel_id = n._id AND c.read = 0) AS unread "
        "FROM novels n LEFT JOIN novelsources s ON s._id = n.source "
        "WHERE n.favorite = 1 ORDER BY n.title COLLATE NOCASE"
    )
    if not novels:
        print("No novels in the library.")
        return

    print("\n== Library ==")
    for i, (_, title, sname, lang, unread) in enumerate(novels, 1):
        print(f"  [{i:>2}] {title}  ({unread} unread)")

    pick = ask("\nPick a novel number: ",
               lambda a: a.isdigit() and 1 <= int(a) <= len(novels))
    novel_id, title, sname, lang, _ = novels[int(pick) - 1]

    # ---- position + how many ----
    chapters = query(
        "SELECT _id, name, scanlator, read FROM novelchapters "
        "WHERE novel_id = ? ORDER BY source_order",
        (novel_id,),
    )
    unread = [(cid, name, scan or "") for cid, name, scan, read in chapters if not read]
    read_count = len(chapters) - len(unread)

    print(f"\n== {title} ==")
    print(f"  {read_count} read, {len(unread)} unread, {len(chapters)} total")
    if not unread:
        print("  Nothing unread - all caught up.")
        return
    print(f"  Next up: {unread[0][1]}")

    raw = ask(
        f"\nHow many chapters to render? [1-{len(unread)}, or 'all'] ",
        lambda a: a.lower() == "all" or (a.isdigit() and int(a) >= 1),
    )
    target = None if raw.lower() == "all" else int(raw)
    if target is None or target >= len(unread):
        print(f"  Rendering all {len(unread)} unread chapters"
              " (already-rendered ones are skipped).")
    else:
        print(f"  Rendering {target} chapters starting at '{unread[0][1]}'"
              " (already-rendered ones don't count).")

    if DEVICE is None:
        print("\nNo device connected - can't fetch chapter text or push audio.")
        return

    sdir = source_dir_name(sname or "Unknown", lang)
    DOWNLOADS = find_downloads_root(sdir)
    if DOWNLOADS is None:
        print("\nCould not find the downloads dir on the device.")
        print("Set MIKO_DOWNLOADS_DIR to the app's downloads path and retry.")
        return
    novel_dir = f"{DOWNLOADS}/{sdir}/{valid_filename(title)}"
    print(f"  device dir: {novel_dir}")

    render_queue(unread, novel_dir, target)
    print("\nDone. Rendered chapters are in the novel's audio/ folder on the")
    print("device - open the player and they show up as rendered.")


try:
    main()
except KeyboardInterrupt:
    print("\nAborted.")
finally:
    if sys.stdin.isatty():
        try:
            input("\nPress Enter to close...")
        except EOFError:
            pass
