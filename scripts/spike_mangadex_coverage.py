"""MangaDex coverage spike for Miko canonical chapters (step-back 2026-09-29).

Question: could a server-side chapter catalog be seeded from MangaDex?
For the top-N works in the Miko canonical browse feed, resolve the
MangaDex manga id via title search, then pull the EN feed and measure:
  - title→id match rate
  - feed coverage (>=1 EN chapter), chapter counts, name freshness
  - externalUrl ratio (licensed titles point out to publisher sites)

Output: JSON lines per work + a summary to stdout.
"""
import json
import re
import sys
import time
import unicodedata
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

WORKER = "https://miko-comments.keypop3750.workers.dev"
MD = "https://api.mangadex.org"
TOP_N = int(sys.argv[1]) if len(sys.argv) > 1 else 300
KINDS = "manga,comic,manhwa,manhua,webtoon,oel,other"

UA = {"User-Agent": "Miko/1.0 coverage-spike"}
NONWORD = re.compile(r"[^\w\s]|_+", flags=re.UNICODE)
SPACES = re.compile(r"\s+")


def norm(s):
    if not s:
        return ""
    s = unicodedata.normalize("NFKC", s).lower()
    s = NONWORD.sub(" ", s)
    return SPACES.sub(" ", s).strip()


_last = [0.0]


def get(url):
    # ~3 req/s budget: MangaDex tolerates it without 429s.
    dt = time.monotonic() - _last[0]
    if dt < 0.34:
        time.sleep(0.34 - dt)
    _last[0] = time.monotonic()
    req = urllib.request.Request(url, headers=UA)
    for attempt in (3, 8):
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.loads(r.read().decode())
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(attempt)
                continue
            raise
        except Exception:
            if attempt == 8:
                raise
            time.sleep(attempt / 2)
    return None


def browse_works(n):
    out, page = [], 1
    while len(out) < n:
        d = get(f"{WORKER}/v1/works/browse?medium=manga&kinds={KINDS}"
                f"&sort=popularity&page={page}")
        if not d or not d.get("works"):
            break
        out += d["works"]
        if not d.get("has_more"):
            break
        page += 1
    return out[:n]


def find_mangadex_id(work):
    q = urllib.parse.quote(work["title"])
    d = get(f"{MD}/manga?title={q}&limit=5&order[relevance]=desc"
            "&contentRating[]=safe&contentRating[]=suggestive"
            "&contentRating[]=erotica&contentRating[]=pornographic")
    if not d or not d.get("data"):
        return None, None
    want = norm(work["title"])
    for m in d["data"]:
        a = m["attributes"]
        titles = [a["title"].get("en") or next(iter(a["title"].values()), "")]
        titles += [t.get("en", "") for t in a.get("altTitles", [])]
        titles += [next(iter(t.values()), "") for t in a.get("altTitles", [])]
        if any(norm(t) == want for t in titles):
            return m["id"], a["title"].get("en", "")
    return None, d["data"][0]["attributes"]["title"].get("en", "")


def feed_stats(md_id):
    total, got, named, external = 0, 0, 0, 0
    newest = 0
    offset = 0
    while True:
        d = get(
            f"{MD}/manga/{md_id}/feed?translatedLanguage[]=en"
            "&order[chapter]=asc&limit=500&includeEmptyPages=0&includeFutureUpdates=0"
            "&includeUnavailable=0&offset=%d" % offset
        )
        if not d or d.get("result") != "ok":
            break
        data = d.get("data", [])
        total = d.get("total", total)
        for ch in data:
            a = ch["attributes"]
            got += 1
            if a.get("title"):
                named += 1
            if a.get("externalUrl"):
                external += 1
            p = a.get("publishAt")
            if p:
                import datetime
                try:
                    ts = int(datetime.datetime.fromisoformat(
                        p.replace("Z", "+00:00")).timestamp())
                    newest = max(newest, ts)
                except Exception:
                    pass
        offset += len(data)
        if offset >= total or not data or total > 1500:
            break
    return total, got, named, external, newest


def main():
    works = browse_works(TOP_N)
    print(f"top works fetched: {len(works)}", file=sys.stderr)
    rows = []
    n_matched = n_fed = n_named = 0
    for i, w in enumerate(works):
        row = {"id": w["miko_work_id"], "title": w["title"], "kind": w.get("kind")}
        md_id, md_title = find_mangadex_id(w)
        row["md_id"] = md_id
        row["md_title"] = md_title
        if md_id:
            n_matched += 1
            total, got, named, external, newest = feed_stats(md_id)
            row.update(ch_total=total, ch_en=got, ch_named=named,
                       ch_external=external, newest_pub=newest)
            if got > 0:
                n_fed += 1
                if named > 0:
                    n_named += 1
        rows.append(row)
        if (i + 1) % 25 == 0:
            print(f"  {i+1}/{len(works)} matched={n_matched} fed={n_fed}",
                  file=sys.stderr)

    with open("scripts/mangadex_coverage.jsonl", "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    fed = [r for r in rows if r.get("ch_en", 0) > 0]
    counts = sorted(r["ch_en"] for r in fed)
    med = counts[len(counts) // 2] if counts else 0
    print(f"\n=== MangaDex coverage (top {len(rows)} browse works) ===")
    print(f"title matched to a MangaDex id : {n_matched}/{len(rows)}")
    print(f"with >=1 EN chapter            : {n_fed}/{len(rows)}")
    print(f"with named chapters            : {n_named}/{len(rows)}")
    print(f"median EN chapters (fed works) : {med}")
    licensed = [r["title"] for r in fed
                if r.get("ch_external", 0) > r["ch_en"] * 0.8]
    print(f"mostly externalUrl (licensed)  : {len(licensed)} — {licensed[:8]}")
    missing = [r["title"] for r in rows if not r.get("md_id")]
    print(f"no MD id (title search miss)   : {len(missing)} — {missing[:8]}")
    empty = [r["title"] for r in rows
             if r.get("md_id") and r.get("ch_en", 0) == 0]
    print(f"MD id but empty EN feed        : {len(empty)} — {empty[:8]}")


if __name__ == "__main__":
    main()
