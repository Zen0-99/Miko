"""Render a downloaded novel chapter .txt to a Miko-compatible .m4a.

Faithful port of ChapterAudioRenderer + SupertonicEngine:
  paragraphTexts (Jsoup segmenter) -> SynthesisPipeline.prepare (KITTEN rules,
  emoji strip) -> chunkText (300 chars) -> per-chunk int8 ONNX infer (8 steps)
  -> PCM16 concat with 0.3 s inter-chunk silence -> AAC-LC 96k mono m4a
  -> uuid manifest atom appended.

Originally spike 007-render-server/render_chapter.py; promoted to a library —
sessions load lazily on first render() call so import is cheap.
"""
import html
import json
import math
import re
import struct
import subprocess
import sys
import unicodedata

import os
import numpy as np

# onnxruntime is imported lazily inside _load_engine() — keeps
# `import miko_render` usable on machines without it (e.g. a PC that only
# runs the Chatterbox pipeline). The cuDNN/cuBLAS DLL dir setup below moves
# with it; it exists only for ORT CUDA on Windows.
ort = None

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUNDLE = os.path.join(
    REPO_ROOT,
    ".planning/spikes/002-supertonic-embedded-in-miko/int8-bundle/"
    "sherpa-onnx-supertonic-3-tts-int8-2026-05-11",
)
SR = 44100
SID = int(os.environ.get("MIKO_VOICE_ID", "4"))  # 4 = F5 (app default)
STEPS = int(os.environ.get("MIKO_RENDER_STEPS", "8"))
SILENCE_S = 0.3
MIKO_UUID = b"MikoTTS\x01" + b"\x00" * 7 + b"\x01"

# ---------- bundle ----------

def load_indexer():
    return np.fromfile(f"{BUNDLE}/unicode_indexer.bin", dtype="<i4")

def load_voice(sid):
    raw = open(f"{BUNDLE}/voice.bin", "rb").read()
    hdr = np.frombuffer(raw[:48], dtype="<i8")
    ttl_dims, dp_dims = hdr[:3], hdr[3:]
    n_ttl, n_dp = int(np.prod(ttl_dims)), int(np.prod(dp_dims))
    floats = np.frombuffer(raw[48:], dtype="<f4")
    return floats[:n_ttl].reshape(ttl_dims)[sid:sid + 1].copy(), \
           floats[n_ttl:n_ttl + n_dp].reshape(dp_dims)[sid:sid + 1].copy()

USE_GPU = os.environ.get("RENDER_CPU", "") != "1"
PROVIDERS = (["CUDAExecutionProvider", "CPUExecutionProvider"] if USE_GPU
             else ["CPUExecutionProvider"])

def sess(name):
    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    so.log_severity_level = 3  # errors only — hushes MemcpyTransformer spam
    return ort.InferenceSession(f"{BUNDLE}/{name}.int8.onnx", so,
                                providers=PROVIDERS)

# ---------- paragraph segmentation (NovelParagraphSegmenter) ----------

def render_paragraph(inner_html):
    if "<" not in inner_html:
        return inner_html
    # Android Html.fromHtml equivalent for simple novel markup:
    # br/p boundaries -> newline, other tags dropped, entities decoded.
    t = re.sub(r"(?i)<\s*br\s*/?\s*>", "\n", inner_html)
    t = re.sub(r"(?i)</\s*(p|div|li|blockquote|h[1-6])\s*>", "\n", t)
    t = re.sub(r"<[^>]+>", "", t)
    return html.unescape(t).strip()

def paragraph_texts(raw):
    doc = raw
    doc = re.sub(r"(?is)<(style|script|noscript)\b.*?</\1>", "", doc)
    paras = re.findall(r"(?is)<p\b[^>]*>(.*?)</p\s*>", doc)
    if not paras:
        # fallback: body text split on blank lines
        body = re.sub(r"<[^>]+>", " ", doc)
        body = html.unescape(body)
        paras = [p for p in re.split(r"\n\s*\n|\r\n\s*\r\n", body) if p.strip()]
    out = []
    for p in paras:
        inner = p.strip()
        if not inner:
            continue
        t = render_paragraph(inner)
        if t.strip():
            out.append(t)
    return out

# ---------- preprocessing rules (KITTEN_DEFAULTS, ordered) ----------

ONES = ["zero","one","two","three","four","five","six","seven","eight","nine",
        "ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen",
        "seventeen","eighteen","nineteen"]
TENS = ["","","twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"]
C2O = {"zero":"zeroth","one":"first","two":"second","three":"third","four":"fourth",
       "five":"fifth","six":"sixth","seven":"seventh","eight":"eighth","nine":"ninth",
       "ten":"tenth","eleven":"eleventh","twelve":"twelfth","thirteen":"thirteenth",
       "fourteen":"fourteenth","fifteen":"fifteenth","sixteen":"sixteenth",
       "seventeen":"seventeenth","eighteen":"eighteenth","nineteen":"nineteenth",
       "twenty":"twentieth","thirty":"thirtieth","forty":"fortieth","fifty":"fiftieth",
       "sixty":"sixtieth","seventy":"seventieth","eighty":"eightieth","ninety":"ninetieth",
       "hundred":"hundredth","thousand":"thousandth","million":"millionth","billion":"billionth"}

def spell_u1000(n):
    if n < 20: return ONES[n]
    if n < 100:
        t, u = n // 10, n % 10
        return TENS[t] if u == 0 else f"{TENS[t]}-{ONES[u]}"
    h, rest = n // 100, n % 100
    return f"{ONES[h]} hundred" if rest == 0 else f"{ONES[h]} hundred {spell_u1000(rest)}"

def spell_cardinal(n):
    if n < 0: return "minus " + spell_cardinal(-n)
    if n < 1000: return spell_u1000(n)
    if n < 1_000_000:
        k, r = n // 1000, n % 1000
        p = f"{spell_u1000(k)} thousand"
        return p if r == 0 else f"{p} {spell_u1000(r)}"
    if n < 1_000_000_000:
        k, r = n // 1_000_000, n % 1_000_000
        p = f"{spell_u1000(k)} million"
        return p if r == 0 else f"{p} {spell_cardinal(r)}"
    k, r = n // 1_000_000_000, n % 1_000_000_000
    p = f"{spell_u1000(k)} billion"
    return p if r == 0 else f"{p} {spell_cardinal(r)}"

def spell_ordinal(n):
    if n < 0: return "minus " + spell_ordinal(-n)
    card = spell_cardinal(n)
    head, _, tail = card.rpartition(" ")
    if "-" in tail:
        th, _, tt = tail.rpartition("-")
        tail = th + "-" + C2O.get(tt, tt + "th")
    else:
        tail = C2O.get(tail, tail + "th")
    return (head + " " + tail).strip()

CURR = {"$": ("dollar","cent"), "£": ("pound","penny"), "€": ("euro","cent"), "¥": ("yen","")}
def expand_currency(t):
    def rep(m):
        sym, amount = m.group(1), m.group(2)
        major, minor = CURR.get(sym, ("units",""))
        if "." in amount:
            a, b = amount.split(".", 1)
            pieces = []
            an, bn = int(a or 0), int(b or 0)
            if an > 0: pieces.append(f"{an} {major}{'s' if an != 1 else ''}")
            if bn > 0 and minor:
                mn = "pennies" if (minor == "penny" and bn != 1) else minor + ("s" if bn != 1 else "")
                pieces.append(f"{bn} {mn}")
            return " and ".join(pieces) if pieces else amount
        n = int(amount)
        return f"{n} {major}{'s' if n != 1 else ''}"
    return re.sub(r"([$£€¥])(\d+(?:\.\d{1,2})?)", rep, t)

def expand_percentage(t):
    return re.sub(r"(\d+(?:\.\d+)?)%", lambda m: f"{m.group(1)} percent", t)

def expand_ordinal(t):
    return re.sub(r"\b(\d+)(?:st|nd|rd|th)\b", lambda m: spell_ordinal(int(m.group(1))), t)

def expand_time(t):
    def rep(m):
        h_, mi_, suf = int(m.group(1)), int(m.group(2)), (m.group(3) or "").strip()
        h = spell_cardinal(h_)
        if mi_ == 0 and suf: return f"{h} {suf}"
        if mi_ == 0 and h_ >= 13: return f"{h} hundred"
        if mi_ == 0: return f"{h} o'clock"
        mi = f"oh {spell_cardinal(mi_)}" if mi_ < 10 else spell_cardinal(mi_)
        return f"{h} {mi} {suf}" if suf else f"{h} {mi}"
    return re.sub(r"\b(\d{1,2}):(\d{2})\s*(AM|PM|am|pm|a\.m\.|p\.m\.)?\b", rep, t)

MONTHS = ["January","February","March","April","May","June","July",
          "August","September","October","November","December"]
def expand_date(t):
    def rep(m):
        mo, dy, yr = int(m.group(1)), int(m.group(2)), m.group(3)
        return f"{MONTHS[mo-1]} {spell_ordinal(dy)}, {yr}" if 1 <= mo <= 12 else m.group(0)
    return re.sub(r"\b(\d{1,2})/(\d{1,2})/(\d{4})\b", rep, t)

def expand_email(t):
    return re.sub(r"\b([a-zA-Z0-9_.+-]+)@([a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+)\b",
                  lambda m: f"{m.group(1)} at {' dot '.join(m.group(2).split('.'))}", t)

def expand_url(t):
    return re.sub(r"https?://(www\.)?([a-zA-Z0-9.-]+\.[a-zA-Z]{2,})\S*",
                  lambda m: " dot ".join(m.group(2).split(".")), t)

KNOWN_EXTS = set("""txt pdf doc docx xls xlsx csv json yaml yml xml html htm css js ts py rb go rs
java cpp hpp c h sh bash zsh fish bat ps1 md rst tex log conf cfg ini toml png jpg jpeg gif svg webp
bmp ico mp3 wav ogg flac aac m4a mp4 mkv avi mov webm zip tar gz bz2 xz rar 7z exe msi deb rpm apk dmg
sql db sqlite onnx pt bin safetensors""".split())
def expand_filename(t):
    def rep(m):
        name, ext = m.group(1), m.group(2)
        if name.isdigit() or ext.lower() not in KNOWN_EXTS:
            return m.group(0)
        return f"{name} dot {' '.join(ext.upper())}"
    return re.sub(r"\b(\w+)\.([a-zA-Z]{1,5})\b", rep, t)

COMMON_ABBR = {"e.g.":"for example","i.e.":"that is","etc.":"et cetera","vs.":"versus",
               "mr.":"mister","mrs.":"missus","ms.":"miss","dr.":"doctor","sr.":"senior",
               "jr.":"junior","st.":"saint","ft.":"feet","lb.":"pounds","oz.":"ounces",
               "ex.":"for example"}
ABBR_RE = re.compile(r"\b(?:[A-Z]\.){2,}|\b(?:e\.g\.|i\.e\.|etc\.|vs\.|"
                     r"[Mm]r\.|[Mm]rs\.|[Mm]s\.|[Dd]r\.|[Ss]r\.|[Jj]r\.|"
                     r"[Ss]t\.|ft\.|lb\.|oz\.|[Ee]x\.)")
def expand_abbreviation(t):
    def rep(m):
        v = m.group(0)
        if v.lower() in COMMON_ABBR: return COMMON_ABBR[v.lower()]
        letters = v.replace(".", "")
        if letters.isupper() and len(letters) <= 6:
            return " ".join(letters)
        return v
    return ABBR_RE.sub(rep, t)

def expand_number(t):
    def rep(m):
        raw = m.group(0)
        try:
            if "." in raw:
                a, b = raw.split(".", 1)
                frac = " ".join(spell_cardinal(int(c)) for c in b)
                return f"{spell_cardinal(int(a or 0))} point {frac}"
            n = int(raw)
            return raw if 1900 <= n <= 2099 else spell_cardinal(n)
        except Exception:
            return raw
    return re.sub(r"\b\d+(?:\.\d+)?\b", rep, t)

MATH = {"+":"plus","×":"times","÷":"divided by","=":"equals","≠":"not equal to",
        "<":"less than",">":"greater than","≤":"less than or equal to",
        "≥":"greater than or equal to","±":"plus or minus"}
def expand_math(t):
    return re.sub(r"(?<=\s)([+×÷=≠<>≤≥±])(?=\s)", lambda m: MATH[m.group(1)], t)

def expand_ampersand(t):
    return re.sub(r"\s&\s", " and ", t)

def expand_hashtag(t):
    return re.sub(r"#(\w+)",
                  lambda m: f"number {m.group(1)}" if m.group(1).isdigit()
                  else f"hashtag {m.group(1)}", t)

HTML_ENT = {"&amp;":"&","&lt;":"<","&gt;":">","&quot;":'"',"&apos;":"'","&nbsp;":" "}
def strip_html(t):
    out = re.sub(r"<[^>]+>", " ", t)
    for k, v in HTML_ENT.items(): out = out.replace(k, v)
    out = re.sub(r"&#(\d+);", lambda m: chr(int(m.group(1))) if int(m.group(1)) <= 0x10FFFF else m.group(0), out)
    out = re.sub(r"&#x([0-9a-fA-F]+);", lambda m: chr(int(m.group(1),16)) if int(m.group(1),16) <= 0x10FFFF else m.group(0), out)
    return out

def strip_markdown(t):
    out = re.sub(r"!\[([^\]]*)\]\([^)]*\)", r"\1", t)
    out = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", out)
    out = re.sub(r"```[^\n`]*\n?(.*?)```", r"\1", out, flags=re.S)
    out = re.sub(r"`([^`\n]+?)`", r"\1", out)
    out = re.sub(r"\*\*([^*\n]+?)\*\*", r"\1", out)
    out = re.sub(r"(?<!\w)__([^_\n]+?)__(?!\w)", r"\1", out)
    out = re.sub(r"~~([^~\n]+?)~~", r"\1", out)
    out = re.sub(r"(?<!\*)\*([^*\n]+?)\*(?!\*)", r"\1", out)
    out = re.sub(r"(?<!\w)_([^_\n]+?)_(?!\w)", r"\1", out)
    out = re.sub(r"(?m)^[ \t]*#{1,6}[ \t]+", "", out)
    out = re.sub(r"(?m)^[ \t]*>[ \t]?", "", out)
    out = re.sub(r"(?m)^[ \t]*[-*+][ \t]+", "", out)
    return out

EMOJI_RE = re.compile("[" "\U0001F300-\U0001FAFF" "\u2600-\u27BF"
                      "\U0001F1E6-\U0001F1FF" "\u200D\uFE0F\u20E3" "]+")
def strip_emoji(t):
    return EMOJI_RE.sub(" ", t)

TERMINAL_PUNCT = ".!?,;:…。、！？．，：；"
def terminal_punct(t):
    tr = t.rstrip()
    if not tr: return t
    return tr if tr[-1] in TERMINAL_PUNCT else tr + "."

def collapse_repeated(t):
    t = re.sub(r"\.{3,}", "…", t)
    t = re.sub(r"!{2,}", "!", t)
    t = re.sub(r"\?{2,}", "?", t)
    return t

# KITTEN order from PreprocessingRules.ALL
RULES = [strip_emoji, strip_markdown, strip_html, expand_email, expand_url,
         expand_currency, expand_percentage, expand_time, expand_date,
         expand_ordinal, expand_abbreviation, expand_filename, expand_number,
         expand_math, expand_ampersand, expand_hashtag, collapse_repeated,
         terminal_punct]

EMOTION_EMOJIS = set("😍😡😎😭🙄😁🙂🤣😮😅🤔")
def prepare(text):
    """SynthesisPipeline.prepare minus prosody shaping (Neutral-only for SS)."""
    if not text.strip(): return None
    if any(e in text for e in EMOTION_EMOJIS):
        print("  WARNING: emotion emoji present — prosody shaping not ported!")
    out = text
    for rule in RULES:
        out = rule(out)
    for e in EMOTION_EMOJIS:
        out = out.replace(e, " ")   # EmojiProsody.stripEmojis
    return out if out.strip() else None

# ---------- chunker (verbatim port) ----------

ABBREVIATIONS = ["Dr.","Mr.","Mrs.","Ms.","Prof.","Sr.","Jr.","St.","Ave.","Rd.",
                 "Blvd.","Dept.","Inc.","Ltd.","Co.","Corp.","etc.","vs.","i.e.","e.g.","Ph.D."]
MAX_CHUNK = 300

def _split_sentences(text):
    """Kotlin (?<!(?:abbrs))(?<=[.!?])\\s+ — split after sentence-ending
    punctuation unless it closes a known abbreviation."""
    parts, start = [], 0
    for m in re.finditer(r"[.!?]\s+", text):
        end_punct = m.start() + 1  # position just after the . ! or ?
        if any(text[:end_punct].endswith(a) for a in ABBREVIATIONS):
            continue
        parts.append(text[start:m.end()])
        start = m.end()
    parts.append(text[start:])
    return parts

def chunk_text(text, maxlen=MAX_CHUNK):
    trimmed = text.strip()
    if not trimmed: return [""]
    chunks = []
    for para in re.split(r"\n\s*\n", trimmed):
        p = para.strip()
        if not p: continue
        if len(p) <= maxlen:
            chunks.append(p); continue
        current = ""
        for sentence in _split_sentences(p):
            s = sentence.strip()
            if not s: continue
            if len(s) > maxlen:
                if current: chunks.append(current.strip()); current = ""
                for part in s.split(","):
                    pt = part.strip()
                    if not pt: continue
                    if len(pt) > maxlen:
                        wc = ""
                        for word in re.split(r"\s+", pt):
                            if len(wc) + len(word) + 1 > maxlen and wc:
                                chunks.append(wc.strip()); wc = ""
                            if wc: wc += " "
                            wc += word
                        if wc: chunks.append(wc.strip())
                    else:
                        if len(current) + len(pt) + 1 > maxlen and current:
                            chunks.append(current.strip()); current = ""
                        if current: current += ", "
                        current += pt
                continue
            if len(current) + len(s) + 1 > maxlen and current:
                chunks.append(current.strip()); current = ""
            if current: current += " "
            current += s
        if current: chunks.append(current.strip())
    return chunks or [""]

# ---------- engine ----------

_engine = None


def _load_engine():
    global _engine, indexer, style_ttl, style_dp, dp, te, ve, voc, ort
    if _engine:
        return
    # pip-installed cuDNN/cuBLAS for ORT CUDA on Windows — no-op without them
    _SP = os.path.join(os.path.dirname(sys.executable), "Lib", "site-packages")
    _dll_dirs = [os.path.join(_SP, d) for d in
                 (r"nvidia\cudnn\bin", r"nvidia\cublas\bin", r"nvidia\cuda_runtime\bin",
                  r"nvidia\cufft\bin", r"nvidia\cuda_nvrtc\bin", r"torch\lib")]
    _dll_dirs = [d for d in _dll_dirs if os.path.isdir(d)]
    os.environ["PATH"] = ";".join(_dll_dirs) + ";" + os.environ.get("PATH", "")
    for _d in _dll_dirs:
        try:
            os.add_dll_directory(_d)
        except OSError:
            pass
    import onnxruntime as ort_mod
    ort = ort_mod
    indexer = load_indexer()
    style_ttl, style_dp = load_voice(SID)
    dp, te, ve, voc = (sess(n) for n in
                       ("duration_predictor", "text_encoder", "vector_estimator", "vocoder"))
    _engine = True

def engine_preprocess(text, lang="en"):
    t = unicodedata.normalize("NFKD", text)
    t = "".join(ch for ch in t if not (0x1F300 <= ord(ch) <= 0x1FAFF or 0x2600 <= ord(ch) <= 0x27BF))
    for k, v in {"–":"-","‑":"-","—":"-","_":" ","“":'"',"”":'"',
                 "‘":"'","’":"'","´":"'","`":"'","[":" ","]":" ",
                 "|":" ","/":" ","#":" ","→":" ","←":" "}.items():
        t = t.replace(k, v)
    t = t.replace("@", " at ").replace("e.g.,", "for example, ").replace("i.e.,", "that is, ")
    for a, b in [(" ,",","),(" .","."),(" !","!"),(" ?","?"),(" ;",";"),(" :",":"),(" '","'")]:
        t = t.replace(a, b)
    t = " ".join(t.split())
    if not t or t[-1] not in ".!?;:,'\"“”‘’)]}…。」』】〉》›»":
        t += "."
    return f"<{lang}>{t}</{lang}>"

def process(text):
    t = engine_preprocess(text)
    ids = np.array([indexer[ord(c)] if ord(c) < len(indexer) else 0 for c in t], dtype=np.int64)
    return ids[None, :], np.ones((1, 1, len(ids)), dtype=np.float32)

def infer(text, speed=1.0):
    text_ids, text_mask = process(text)
    dur = float(np.asarray(dp.run(None, {"text_ids": text_ids, "style_dp": style_dp,
                                         "text_mask": text_mask})[0]).ravel()[0]) / speed
    wav_len = int(dur * SR)
    text_emb = te.run(None, {"text_ids": text_ids, "style_ttl": style_ttl,
                             "text_mask": text_mask})[0]
    lat_len = max(1, math.ceil(wav_len / 3072))
    xt = np.random.standard_normal((1, 144, lat_len)).astype(np.float32)
    latent_mask = np.ones((1, 1, lat_len), np.float32)
    for i in range(STEPS):
        xt = ve.run(None, {"noisy_latent": xt, "text_emb": text_emb,
                           "style_ttl": style_ttl, "latent_mask": latent_mask,
                           "text_mask": text_mask,
                           "current_step": np.array([float(i)], np.float32),
                           "total_step": np.array([float(STEPS)], np.float32)})[0]
    wav = voc.run(None, {"latent": xt})[0].ravel()
    return wav[:wav_len]

# ---------- render ----------

def render(txt_path, m4a_path, title, infer_fn=None,
           engine_label="supertonic-3-int8", voice_label=None,
           sample_rate=SR, source_sample_rate=None):
    """Render a chapter .txt -> AAC .m4a + appended uuid manifest atom.

    Engine-agnostic seam: `infer_fn(text_chunk) -> float32 PCM` is called per
    text chunk; None selects the bundled supertonic int8 path (identical output
    to before the seam existed — same chunks, writer, and manifest values).

    PCM math runs at `source_sample_rate` (the synthesizer's native rate —
    defaults to `sample_rate`); ffmpeg resamples to `sample_rate` in the AAC
    container. The manifest records `sampleRate` (what the player sees in the
    container) and, when different, `sourceSampleRate` as an informational
    field (app readers ignore unknown keys).

    Returns the manifest dict that was embedded.
    """
    src_sr = int(source_sample_rate or sample_rate)
    if infer_fn is None:
        _load_engine()
        infer_fn = infer
    raw = open(txt_path, encoding="utf-8").read()
    paras = paragraph_texts(raw)
    print(f"{len(paras)} paragraphs")
    silence = np.zeros(int(SILENCE_S * src_sr), np.int16)
    all_pcm = []
    offsets_ms = []
    cum = 0
    for i, para in enumerate(paras):
        offsets_ms.append(round(cum * 1000 / src_sr))
        engine_text = prepare(para)
        if engine_text is None:
            continue
        first = True
        para_samples = 0
        for chunk in chunk_text(engine_text):
            wav = infer_fn(chunk)
            pcm = (np.clip(wav, -1, 1) * 32767).astype(np.int16)
            if not first:
                pcm = np.concatenate([silence, pcm])
            first = False
            all_pcm.append(pcm)
            para_samples += len(pcm)
        cum += para_samples
        if i % 10 == 0:
            print(f"  para {i}/{len(paras)}  {cum/src_sr:.0f}s", flush=True)
    pcm = np.concatenate(all_pcm)
    duration_ms = round(len(pcm) * 1000 / src_sr)

    wav_tmp = m4a_path + ".wav"
    import wave
    with wave.open(wav_tmp, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(src_sr)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-i", wav_tmp, "-c:a", "aac", "-b:a", "96k",
                    "-ac", "1", "-ar", str(sample_rate), m4a_path],
                   check=True, capture_output=True)

    manifest = {
        "version": 1, "voice": voice_label or "supertonic:F5",
        "engine": engine_label,
        "chapterTitle": title, "sampleRate": sample_rate, "speechRate": 1.0,
        "pitch": 1.0,
        "durationMs": duration_ms, "paragraphOffsetsMs": offsets_ms,
        "paragraphTexts": paras, "text": "\n\n".join(paras),
    }
    if source_sample_rate:
        manifest["sourceSampleRate"] = int(source_sample_rate)
    payload = json.dumps(manifest, ensure_ascii=False).encode("utf-8")
    atom = struct.pack(">I", 24 + len(payload)) + b"uuid" + MIKO_UUID + payload
    with open(m4a_path, "ab") as f:
        f.write(atom)
    os.remove(wav_tmp)
    print(f"done: {m4a_path}  {duration_ms/60000:.1f} min audio")
    return manifest

if __name__ == "__main__":
    import time
    if len(sys.argv) >= 3:
        t0 = time.time()
        render(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3
               else os.path.splitext(os.path.basename(sys.argv[2]))[0])
        print(f"  render wall: {(time.time()-t0)/60:.1f} min")
    else:
        print("usage: miko_render.py <chapter.txt> <out.m4a> [title]")
