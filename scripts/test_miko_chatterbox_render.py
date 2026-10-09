"""Tests for scripts/miko_chatterbox_render.py + the miko_render.render seam.

Everything here is model-free: --stub-infer exercises the full pipeline
(paragraphs -> chunks -> PCM concat -> ffmpeg AAC -> uuid manifest -> ABS
tree/queue) with no GPU, torch, or chatterbox install.
"""
import hashlib
import json
import os
import sys
import types
import wave

import numpy as np
import pytest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import miko_render
import miko_chatterbox_render as mcr


# ---------- helpers ----------

def make_txt(path, paras):
    path.write_text("\n\n".join(paras), encoding="utf-8")
    return str(path)


def make_wav(path, sr=24000, secs=0.1):
    t = np.arange(int(sr * secs)) / sr
    pcm = (0.2 * np.sin(2 * np.pi * 220 * t) * 32767).astype(np.int16)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())
    return str(path)


def ref_hash8(path):
    return hashlib.sha256(open(str(path), "rb").read()).hexdigest()[:8]


# ---------- stub-infer end-to-end ----------

def test_stub_infer_e2e(tmp_path):
    txt = make_txt(tmp_path / "chap01.txt",
                   ["Hello world, this is a test.",
                    "A second paragraph follows here.",
                    "And a third one to be safe."])
    ref = make_wav(tmp_path / "ref.wav")
    out = tmp_path / "out"
    rc = mcr.main([txt, "--voice", ref, "--out", str(out), "--stub-infer"])
    assert rc == 0

    m4a = out / "chap01.m4a"
    assert m4a.exists()
    assert not (out / "tmp-chap01.m4a").exists()  # atomic rename landed

    man = mcr.read_manifest(str(m4a))
    assert man is not None, "uuid manifest atom must parse"
    h8 = ref_hash8(ref)
    assert man["engine"] == "chatterbox-turbo"
    assert man["voice"] == f"chatterbox:{h8}"
    assert man["sampleRate"] == 44100
    assert man["sourceSampleRate"] == 24000
    assert man["version"] == 1
    assert man["chapterTitle"] == "chap01"
    # paragraph coverage: offsets for every paragraph, monotonic, in-range
    assert len(man["paragraphOffsetsMs"]) == 3
    assert man["paragraphOffsetsMs"] == sorted(man["paragraphOffsetsMs"])
    assert man["paragraphOffsetsMs"][0] == 0
    assert man["durationMs"] > 0
    assert man["paragraphTexts"] == ["Hello world, this is a test.",
                                     "A second paragraph follows here.",
                                     "And a third one to be safe."]


def test_stub_infer_abs_library(tmp_path):
    txt = make_txt(tmp_path / "chap01.txt", ["Only paragraph."])
    ref = make_wav(tmp_path / "ref.wav")
    out = tmp_path / "out"
    lib = tmp_path / "lib"
    rc = mcr.main([txt, "--voice", ref, "--out", str(out), "--stub-infer",
                   "--abs-library", str(lib), "--author", "Jane Writer",
                   "--narrator", ""])
    assert rc == 0

    item = lib / "Jane Writer" / "chap01"
    m4a = item / "01 - chap01.m4a"
    assert m4a.exists(), "ABS tree m4a missing"
    meta = json.loads((item / "metadata.json").read_text(encoding="utf-8"))
    assert meta["title"] == "chap01"
    assert meta["authors"] == ["Jane Writer"]
    assert f"chatterbox:{ref_hash8(ref)}" in meta["narrators"]
    assert meta["chapters"] and meta["chapters"][0]["end"] > 0
    # uuid atom survives the stream-copy retag
    man = mcr.read_manifest(str(m4a))
    assert man and man["engine"] == "chatterbox-turbo"


def test_stub_infer_abs_library_series_and_dir_grouping(tmp_path):
    d = tmp_path / "My Book"
    d.mkdir()
    make_txt(d / "a.txt", ["First chapter text."])
    make_txt(d / "b.txt", ["Second chapter text."])
    ref = make_wav(tmp_path / "ref.wav")
    lib = tmp_path / "lib"
    rc = mcr.main([str(d), "--voice", ref, "--out", str(tmp_path / "out"),
                   "--stub-infer", "--abs-library", str(lib),
                   "--author", "A U Thor", "--series", "Saga",
                   "--book-number", "3"])
    assert rc == 0
    # {Author}/{Series}/{N} - {Title}/ per SD-3
    item = lib / "A U Thor" / "Saga" / "3 - My Book"
    assert (item / "01 - a.m4a").exists()
    assert (item / "02 - b.m4a").exists()
    meta = json.loads((item / "metadata.json").read_text(encoding="utf-8"))
    assert meta["title"] == "My Book"
    assert meta["series"] == ["Saga #3"]
    assert len(meta["chapters"]) == 2
    assert meta["chapters"][1]["start"] >= meta["chapters"][0]["end"]


def test_stub_never_imports_torch_or_chatterbox(tmp_path, monkeypatch):
    for mod in ("torch", "torchaudio", "chatterbox", "chatterbox.tts",
                "chatterbox.tts_turbo", "soundfile"):
        monkeypatch.setitem(sys.modules, mod, None)  # any import -> ImportError
    monkeypatch.setattr(mcr, "load_model",
                        lambda *a, **k: pytest.fail("model load under stub"))
    txt = make_txt(tmp_path / "x.txt", ["Stub never touches the model."])
    ref = make_wav(tmp_path / "ref.wav")
    rc = mcr.main([txt, "--voice", ref, "--out", str(tmp_path / "o"),
                   "--stub-infer"])
    assert rc == 0
    assert (tmp_path / "o" / "x.m4a").exists()


# ---------- queue ----------

def test_queue_resume_skips_done(tmp_path, monkeypatch):
    t1 = make_txt(tmp_path / "a.txt", ["Alpha paragraph."])
    t2 = make_txt(tmp_path / "b.txt", ["Beta paragraph."])
    ref = make_wav(tmp_path / "ref.wav")
    o1, o2 = tmp_path / "o1", tmp_path / "o2"
    q = tmp_path / "queue.txt"
    q.write_text(f"# comment\nchap-a | {t1} | {o1} | done\n"
                 f"chap-b | {t2} | {o2} |\n", encoding="utf-8")

    calls = []
    orig = miko_render.render
    monkeypatch.setattr(miko_render, "render",
                        lambda *a, **k: (calls.append(a[0]), orig(*a, **k))[1])
    rc = mcr.main(["--queue", str(q), "--voice", ref, "--stub-infer"])
    assert rc == 0
    assert calls == [t2]                      # done row never re-rendered
    assert not (o1 / "chap-a.m4a").exists()
    assert (o2 / "chap-b.m4a").exists()
    lines = [l for l in q.read_text(encoding="utf-8").splitlines()
             if l.strip() and not l.lstrip().startswith("#")]
    statuses = [l.split("|")[-1].strip() for l in lines]
    assert statuses == ["done", "done"]       # both rows marked done now

    calls.clear()
    rc = mcr.main(["--queue", str(q), "--voice", ref, "--stub-infer"])
    assert rc == 0 and calls == []            # fully-done queue renders nothing


def test_queue_interrupt_marks_partial(tmp_path, monkeypatch):
    t1 = make_txt(tmp_path / "a.txt", ["Interrupt me."])
    ref = make_wav(tmp_path / "ref.wav")
    q = tmp_path / "queue.txt"
    q.write_text(f"chap-a | {t1} | {tmp_path / 'o'} |\n", encoding="utf-8")

    def boom(*a, **k):
        raise KeyboardInterrupt()
    monkeypatch.setattr(miko_render, "render", boom)
    rc = mcr.main(["--queue", str(q), "--voice", ref, "--stub-infer"])
    assert rc != 0
    assert "partial" in q.read_text(encoding="utf-8")


def test_queue_name_path_escape_rejected(tmp_path):
    t1 = make_txt(tmp_path / "a.txt", ["Hello."])
    ref = make_wav(tmp_path / "ref.wav")
    q = tmp_path / "queue.txt"
    q.write_text(f"../../evil | {t1} | {tmp_path / 'o'} |\n",
                 encoding="utf-8")
    with pytest.raises(SystemExit) as e:
        mcr.main(["--queue", str(q), "--voice", ref, "--stub-infer"])
    assert e.value.code != 0
    assert not (tmp_path / "evil.m4a").exists()
    assert not (tmp_path.parent / "evil.m4a").exists()
    assert not (tmp_path / "o" / "evil.m4a").exists()


# ---------- supertonic default path unchanged ----------

def test_supertonic_default_labels_intact(tmp_path, monkeypatch):
    """infer_fn=None -> supertonic defaults; labels/fields unchanged."""
    monkeypatch.setattr(miko_render, "_load_engine", lambda: None)
    sr = miko_render.SR
    monkeypatch.setattr(
        miko_render, "infer",
        lambda chunk: 0.1 * np.sin(
            2 * np.pi * 220 * np.arange(int(0.2 * sr)) / sr).astype(np.float32))
    txt = make_txt(tmp_path / "c.txt", ["On-device engine still works."])
    m4a = tmp_path / "c.m4a"
    man = miko_render.render(txt, str(m4a), "c")   # infer_fn=None -> default
    assert man["engine"] == "supertonic-3-int8"
    assert man["voice"] == "supertonic:F5"
    assert man["sampleRate"] == 44100
    assert "sourceSampleRate" not in man
    parsed = mcr.read_manifest(str(m4a))
    assert parsed == man


# ---------- model selection (task-2 monkeypatched cases) ----------

def _fake_modules(monkeypatch):
    turbo_mod = types.ModuleType("chatterbox.tts_turbo")

    class FakeTurbo:
        @classmethod
        def from_pretrained(cls, device=None):
            m = cls()
            m.sr = 24000
            m.kind = "turbo"
            return m

        def generate(self, text, audio_prompt_path=None, cfg_weight=None,
                     exaggeration=None):
            assert audio_prompt_path and audio_prompt_path.endswith("ref.wav")
            return np.ones(2400, dtype=np.float32) * 0.01

    turbo_mod.ChatterboxMultilingualTTS = FakeTurbo
    std_mod = types.ModuleType("chatterbox.tts")

    class FakeStd:
        @classmethod
        def from_pretrained(cls, device=None):
            m = cls()
            m.sr = 24000
            m.kind = "standard"
            return m

        def generate(self, text, audio_prompt_path=None, cfg_weight=None,
                     exaggeration=None):
            return np.ones(2400, dtype=np.float32) * 0.01

    std_mod.ChatterboxTTS = FakeStd
    pkg = types.ModuleType("chatterbox")
    torch_mod = types.SimpleNamespace(
        cuda=types.SimpleNamespace(is_available=lambda: False))
    monkeypatch.setitem(sys.modules, "chatterbox", pkg)
    monkeypatch.setitem(sys.modules, "chatterbox.tts_turbo", turbo_mod)
    monkeypatch.setitem(sys.modules, "chatterbox.tts", std_mod)
    monkeypatch.setitem(sys.modules, "torch", torch_mod)


def test_load_model_selects_variant(monkeypatch):
    _fake_modules(monkeypatch)
    m = mcr.load_model("turbo")
    assert m.kind == "turbo"
    m = mcr.load_model("standard")
    assert m.kind == "standard"


def test_non_stub_render_uses_model_and_ref(tmp_path, monkeypatch):
    _fake_modules(monkeypatch)
    txt = make_txt(tmp_path / "r.txt", ["Real model path, fake weights."])
    ref = make_wav(tmp_path / "ref.wav")
    rc = mcr.main([txt, "--voice", ref, "--out", str(tmp_path / "o"),
                   "--model", "turbo"])
    assert rc == 0
    man = mcr.read_manifest(str(tmp_path / "o" / "r.m4a"))
    assert man["engine"] == "chatterbox-turbo"
    assert man["sourceSampleRate"] == 24000
    assert man["voice"] == f"chatterbox:{ref_hash8(ref)}"


def test_voice_ref_validation(tmp_path):
    txt = make_txt(tmp_path / "v.txt", ["Hi."])
    with pytest.raises(SystemExit) as e:
        mcr.main([txt, "--voice", str(tmp_path / "nope.wav"),
                  "--out", str(tmp_path / "o"), "--stub-infer"])
    assert e.value.code != 0
    bad = tmp_path / "bad.wav"
    bad.write_bytes(b"this is not audio")
    with pytest.raises(SystemExit) as e2:
        mcr.main([txt, "--voice", str(bad), "--out", str(tmp_path / "o"),
                  "--stub-infer"])
    assert e2.value.code != 0


# ---------- ABS scan / upload (captured HTTP, token hygiene) ----------

def test_abs_scan_request_shape_and_token_hygiene(tmp_path, monkeypatch,
                                                  capsys):
    calls = []
    monkeypatch.setattr(
        mcr, "_post",
        lambda url, body, headers: (calls.append((url, body, headers)),
                                    (200, "{}"))[1])
    monkeypatch.setenv("ABS_URL", "http://abs.test:13378/")
    monkeypatch.setenv("ABS_TOKEN", "SECRET-TOKEN-123")
    monkeypatch.setenv("ABS_LIBRARY_ID", "lib42")
    assert mcr.abs_scan() is True
    url, body, headers = calls[0]
    assert url == "http://abs.test:13378/api/libraries/lib42/scan"
    assert headers["Authorization"] == "Bearer SECRET-TOKEN-123"
    out = capsys.readouterr()
    assert "SECRET-TOKEN-123" not in out.out + out.err

    # env missing -> warn + skip, no request
    monkeypatch.delenv("ABS_LIBRARY_ID")
    calls.clear()
    assert mcr.abs_scan() is False
    assert calls == []
    assert "SECRET-TOKEN-123" not in capsys.readouterr().err


def test_abs_upload_multipart_and_token_hygiene(tmp_path, monkeypatch, capsys):
    m4a = tmp_path / "chap.m4a"
    m4a.write_bytes(b"\x00" * 64 + b"fake-m4a-bytes")
    calls = []
    monkeypatch.setattr(
        mcr, "_post",
        lambda url, body, headers: (calls.append((url, body, headers)),
                                    (200, "{}"))[1])
    monkeypatch.setenv("ABS_URL", "http://abs.test:13378")
    monkeypatch.setenv("ABS_TOKEN", "SECRET-TOKEN-123")
    monkeypatch.setenv("ABS_LIBRARY_ID", "lib42")
    monkeypatch.setenv("ABS_FOLDER", "audiobooks")
    assert mcr.upload_abs(str(m4a), title="T", author="A",
                        series_label="S #2") is True
    url, body, headers = calls[0]
    assert url == "http://abs.test:13378/api/upload"
    assert headers["Authorization"] == "Bearer SECRET-TOKEN-123"
    # token rides the header only — never the multipart body, never logs
    assert b"SECRET-TOKEN-123" not in body
    assert b'filename="chap.m4a"' in body
    assert b'name="library"' in body and b"lib42" in body
    assert b'name="folder"' in body and b"audiobooks" in body
    assert b"fake-m4a-bytes" in body
    out = capsys.readouterr()
    assert "SECRET-TOKEN-123" not in out.out + out.err


def test_abs_upload_without_env_skips(tmp_path, monkeypatch, capsys):
    for v in ("ABS_URL", "ABS_TOKEN", "MIKO_ABS_URL", "MIKO_ABS_TOKEN"):
        monkeypatch.delenv(v, raising=False)
    calls = []
    monkeypatch.setattr(mcr, "_post",
                        lambda *a: (calls.append(a), (200, ""))[1])
    m4a = tmp_path / "x.m4a"
    m4a.write_bytes(b"x")
    assert mcr.upload_abs(str(m4a), title="T") is False
    assert calls == []
    assert "needs ABS_URL and ABS_TOKEN" in capsys.readouterr().err
