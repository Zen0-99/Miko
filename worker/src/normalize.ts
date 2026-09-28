// Twin of scripts/mikodb/build-manga-map.mjs `normalize` and
// app TitleNormalizer.kt — NFKC + lowercase + non-word strip +
// space collapse. Golden vectors: app/src/test/resources/
// title_normalizer_golden.json. Keep all three byte-identical.
const NONWORD = /[^\p{L}\p{N}\s]/gu;
const SPACES = /\s+/g;

export function normalize(t: string | null | undefined): string {
    if (!t) return '';
    return t.normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(SPACES, ' ').trim();
}
