export interface Env {
    COMMENTS_DB: D1Database;
    /** Override for tests/staging — defaults to https://api.mangabaka.org. */
    MANGABAKA_BASE?: string;
    /** Override for tests/staging — defaults to https://api.mangadex.org. */
    MANGADEX_BASE?: string;
    /** Override for tests/staging — defaults to https://openlibrary.org. */
    OPENLIBRARY_BASE?: string;
    /** Override for tests/staging — defaults to https://v3-cinemeta.strem.io. */
    CINEMETA_BASE?: string;
    /** Override for tests — defaults to the hentaistream-addon repo's
     *  catalog.json.gz (gzip-magic sniffed; plain .json works too). */
    HENTAISTREAM_CATALOG_URL?: string;
    /** TypeSafe/Jev API key — Phase 10 appeals. */
    JEV_MODERATION_KEY?: string;
    /** Override for tests — defaults to the TypeSafe systemone endpoint. */
    TYPESAFE_ENDPOINT?: string;
}
