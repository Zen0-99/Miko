// chapterparse.ts — robust chapter-name parser (Phase 12-04).
// Kotlin twin: app/.../data/mikodb/ChapterNumberParser.kt — keep logic
// identical; both consume worker/test/chapter_parser_golden.json.
//
// Handles: "Chapter 1", "Ch. 01", "142.5", "Vol. 2 Ch. 10",
// "Chapter Twenty-Three", "Ch. 30 Part 2", "30b", kind buckets
// (prologue/extra/afterword/notice), and garbage → kind 'unknown'
// (never force a number onto unparseable names).

export type ChapterKind =
    | 'chapter'
    | 'prologue'
    | 'epilogue'
    | 'afterword'
    | 'extra'
    | 'notice'
    | 'unknown';

export interface ParsedChapter {
    number: number | null;
    volume: number | null;
    part: number | null;
    kind: ChapterKind;
}

const ONES: Record<string, number> = {
    one: 1, two: 2, three: 3, four: 4, five: 5, six: 6, seven: 7,
    eight: 8, nine: 9, ten: 10, eleven: 11, twelve: 12, thirteen: 13,
    fourteen: 14, fifteen: 15, sixteen: 16, seventeen: 17,
    eighteen: 18, nineteen: 19,
};
const TENS: Record<string, number> = {
    twenty: 20, thirty: 30, forty: 40, fifty: 50, sixty: 60,
    seventy: 70, eighty: 80, ninety: 90,
};

const ROMAN: Record<string, number> = { i: 1, v: 5, x: 10, l: 50, c: 100, d: 500, m: 1000 };

export function romanToInt(s: string): number | null {
    if (!/^[ivxlcdm]+$/.test(s)) return null;
    let total = 0;
    for (let i = 0; i < s.length; i++) {
        const v = ROMAN[s[i]];
        const next = ROMAN[s[i + 1]] ?? 0;
        total += v < next ? -v : v;
    }
    return total > 0 ? total : null;
}

/** Consume leading number-words from tokens; returns value + count. */
export function wordToNumber(tokens: string[]): { value: number; consumed: number } | null {
    let i = 0;
    let total = 0;
    let any = false;
    if (tokens[i] === 'a' && tokens[i + 1] === 'hundred') i++; // "a hundred"
    if (tokens[i] !== undefined && ONES[tokens[i]] !== undefined && tokens[i + 1] === 'hundred') {
        total = ONES[tokens[i]] * 100;
        i += 2;
        any = true;
    } else if (tokens[i] === 'hundred') {
        total = 100;
        i++;
        any = true;
    }
    if (any && tokens[i] === 'and') i++;
    if (tokens[i] !== undefined && TENS[tokens[i]] !== undefined) {
        total += TENS[tokens[i]];
        i++;
        any = true;
        if (tokens[i] !== undefined && ONES[tokens[i]] !== undefined) {
            total += ONES[tokens[i]];
            i++;
        }
    } else if (tokens[i] !== undefined && ONES[tokens[i]] !== undefined) {
        total += ONES[tokens[i]];
        i++;
        any = true;
    }
    return any ? { value: total, consumed: i } : null;
}

const RE_KIND: Array<[RegExp, ChapterKind]> = [
    [/\b(prologue|foreword|preface)\b/, 'prologue'],
    [/\bepilogue\b/, 'epilogue'],
    [/\bafterword\b/, 'afterword'],
    [/\b(omake|extra|extras|bonus|special|oneshot|one[ -]shot|side[ -]stor(?:y|ies)|interlude|intermission|appendix|gaiden|omitted|spin[ -]off)\b/, 'extra'],
    [/\b(notice|announcement|news|hiatus)\b/, 'notice'],
];

const RE_VOLUME = /\bvol(?:ume)?s?\.?\s*(\d+(?:\.\d+)?)/;
const RE_VOLUME_V = /\bv(\d+)\b/;
const RE_CHAPTER = /\b(?:chapter|chap|ch|episode|ep|no)\.?\s*([0-9]+(?:\.[0-9]+)?[a-z]?|[a-z]+(?:[ -][a-z]+)*)/;
const RE_CHAPTER_HASH = /#\s*(\d+(?:\.\d+)?)/;
const RE_COMPACT = /\bv(\d+)\s*c(\d+(?:\.\d+)?)\b/;
const RE_NUMERIC_TOKEN = /^([0-9]+)(?:\.([0-9]+))?([a-z])?$/;
const RE_BARE = /^\s*\d+(?:\.\d+)?\s*$/;
const RE_PART = /\b(?:part|pt)\.?\s*(?:([0-9]+)|([ivxlcdm]+))\b/;
const RE_LOOSE_INT = /\b([0-9]+)\b/;

export function parseChapter(raw: string): ParsedChapter {
    const text = ` ${raw.toLowerCase().trim()} `;
    let volume: number | null = null;
    let number: number | null = null;
    let part: number | null = null;
    let kind: ChapterKind = 'chapter';

    for (const [re, k] of RE_KIND) {
        if (re.test(text)) {
            kind = k;
            break;
        }
    }

    const compact = RE_COMPACT.exec(text);
    const vol = RE_VOLUME.exec(text) ?? (compact ? null : RE_VOLUME_V.exec(text));
    if (vol) volume = parseFloat(vol[1]);
    if (compact) {
        volume = parseFloat(compact[1]);
        number = parseFloat(compact[2]);
    }

    if (number === null) {
        const m = RE_CHAPTER.exec(text) ?? RE_CHAPTER_HASH.exec(text);
        if (m) {
            const tok = m[1];
            const num = RE_NUMERIC_TOKEN.exec(tok);
            if (num) {
                number = parseFloat(num[2] ? `${num[1]}.${num[2]}` : num[1]);
                if (num[3]) part = num[3].charCodeAt(0) - 96; // "30b" → part 2
            } else {
                const w = wordToNumber(tok.split(/[\s-]+/));
                if (w) number = w.value;
            }
        }
    }

    // Explicit part marker.
    const pm = RE_PART.exec(text);
    if (pm) {
        part = pm[1] ? parseInt(pm[1], 10) : romanToInt(pm[2]);
    }

    // Bare "142.5" / "01"; bare letter-suffixed "30b" → part.
    if (number === null) {
        if (RE_BARE.test(raw)) {
            number = parseFloat(raw.trim());
        } else {
            const bs = /^\s*(\d+)([a-z])\s*$/.exec(raw);
            if (bs) {
                number = parseInt(bs[1], 10);
                part = part ?? bs[2].charCodeAt(0) - 96;
            }
        }
    }

    // Non-chapter kinds still get a lone trailing/standalone number so
    // "Extra 2" / "Side Story 3" merge across sources.
    if (number === null && kind !== 'chapter' && kind !== 'notice') {
        const loose = RE_LOOSE_INT.exec(text);
        if (loose) number = parseInt(loose[1], 10);
    }

    if (number === null && kind === 'chapter') kind = 'unknown';

    return { number, volume, part, kind };
}
