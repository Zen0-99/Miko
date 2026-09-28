#!/usr/bin/env node
// build-hs-seed.mjs — hentaistream catalog → chunked D1 seed SQL
//
// Run:  node build-hs-seed.mjs [catalog.json|catalog.json.gz] [outDir]
//       (no args → fetches the addon repo's data/catalog.json.gz)
//
// Emits <outDir>/seed-hs-001.sql … (~500 works per file, sized for
// `npx wrangler d1 execute miko-comments --remote --file=`):
//   INSERT OR IGNORE works_cache  — mk:hs:<canonical-id> rows,
//                                   medium='shows', nsfw=1, kind='series'
//   INSERT works_titles           — catalog name (normalized)
//   INSERT OR IGNORE external_ids — ('hs', alias) for the canonical id,
//                                   every providerSlug (bare + prefixed),
//                                   and every slugRegistry alias key
//
// Refresh cadence: the upstream catalog is rebuilt daily by the addon's
// database-update workflow; re-seed on the same cadence (or after a
// provider scrape wave). Slug canonicalization rules MUST match
// worker/src/hentaistream.ts (providerSlugs + slugRegistry port of the
// addon's src/cache/slugRegistry.js).

import { createHash } from 'node:crypto';
import { gunzipSync } from 'node:zlib';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const CATALOG_URL =
    'https://raw.githubusercontent.com/Zen0-99/hentaistream-addon/master/data/catalog.json.gz';
const ROWS_PER_FILE = 500;

const isMain = process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
const [,, src, outDir = 'seed-hs'] = process.argv;

// ---- TitleNormalizer twin (keep in sync with TitleNormalizer.kt /
// worker/src/normalize.ts) ----
const NONWORD = /[^\p{L}\p{N}\s]/gu;
const SPACES = /\s+/g;
export function normalize(t) {
    if (!t) return '';
    return t.normalize('NFKC').toLowerCase().replace(NONWORD, ' ').replace(SPACES, ' ').trim();
}

const sql = (s) => `'${String(s).replace(/'/g, "''")}'`;

async function loadCatalog() {
    if (src && fs.existsSync(src)) {
        const buf = fs.readFileSync(src);
        return JSON.parse(buf[0] === 0x1f && buf[1] === 0x8b ? gunzipSync(buf).toString() : buf.toString());
    }
    const res = await fetch(CATALOG_URL);
    if (!res.ok) throw new Error(`catalog fetch ${res.status}`);
    return JSON.parse(gunzipSync(Buffer.from(await res.arrayBuffer())).toString());
}

async function build() {
    const cat = await loadCatalog();
    const items = (cat.catalog ?? []).filter((i) => i?.id);

    // slugRegistry: normalized slug → canonical catalog id
    // (provider-ranked; ids missing from the catalog are dead).
    const byId = new Map(items.map((i) => [i.id, i]));
    const PROVIDER_RANK = ['hmm', 'hse', 'htv', 'hs', 'os'];
    const aliasToId = new Map();
    for (const item of items) {
        for (const [prov, slug] of Object.entries(item.providerSlugs ?? {})) {
            const s = String(slug).toLowerCase();
            if (!s) continue;
            aliasToId.set(s, item.id);
            aliasToId.set(`${prov}-${s}`, item.id);
        }
    }
    for (const [key, provs] of Object.entries(cat.slugRegistry ?? {})) {
        if (!provs) continue;
        const slug = key.toLowerCase();
        const ids = PROVIDER_RANK.map((p) => provs[p]?.id)
            .concat(Object.values(provs).map((e) => e?.id))
            .filter((id) => id && byId.has(id));
        if (ids.length && !aliasToId.has(slug)) aliasToId.set(slug, ids[0]);
        for (const [prov, e] of Object.entries(provs)) {
            if (e?.id && byId.has(e.id) && !aliasToId.has(`${prov}-${slug}`)) {
                aliasToId.set(`${prov}-${slug}`, e.id);
            }
        }
    }
    // id → all aliases
    const idToAliases = new Map();
    for (const [alias, id] of aliasToId) {
        if (!idToAliases.has(id)) idToAliases.set(id, new Set());
        idToAliases.get(id).add(alias);
    }

    fs.mkdirSync(outDir, { recursive: true });
    const fetchedAt = Date.now();
    let works = 0, titles = 0, extIds = 0, fileNo = 0, inFile = 0;
    let fh = null;
    const openFile = () => {
        fileNo += 1;
        fh = fs.createWriteStream(path.join(outDir, `seed-hs-${String(fileNo).padStart(3, '0')}.sql`));
        // No BEGIN/COMMIT — `wrangler d1 execute --remote --file=` rejects
        // raw transaction statements (D1 wants its JS transaction API);
        // every statement below is OR IGNORE so re-runs are idempotent.
    };
    const closeFile = async () => {
        await new Promise((res, rej) => { fh.once('error', rej); fh.end(res); });
    };
    const w = (line) => fh.write(line + '\n');

    for (const item of items) {
        if (inFile === 0) openFile();
        const workId = `mk:hs:${item.id}`;
        const title = item.name || item.id;
        const year = /(\d{4})/.exec(String(item.year ?? item.releaseInfo ?? ''))?.[1] ?? null;
        // kind='series' — catalog `type` is always 'series'; `genres` are
        // untrusted (studio names leak in). nsfw=1 per the locked caveat:
        // every hentaistream row is adult content.
        w(`INSERT OR IGNORE INTO works_cache (miko_work_id, type, title, normalized_title, year, provider_links, medium, nsfw, fetched_at) VALUES (${sql(workId)}, 'series', ${sql(title)}, ${sql(normalize(title))}, ${year ?? 'NULL'}, '[]', 'shows', 1, ${fetchedAt});`);
        works++;
        w(`INSERT OR IGNORE INTO works_titles (miko_work_id, title, lang, normalized) VALUES (${sql(workId)}, ${sql(title)}, NULL, ${sql(normalize(title))});`);
        titles++;
        const aliases = new Set([item.id, ...(idToAliases.get(item.id) ?? [])]);
        for (const alias of aliases) {
            w(`INSERT OR IGNORE INTO external_ids (provider, external_id, miko_work_id) VALUES ('hs', ${sql(alias)}, ${sql(workId)});`);
            extIds++;
        }
        inFile++;
        if (inFile >= ROWS_PER_FILE) {
            await closeFile();
            inFile = 0;
        }
    }
    if (inFile > 0) await closeFile();
    const skipped = (cat.catalog?.length ?? 0) - works;
    console.log(JSON.stringify({
        files: fileNo, works, titles, external_ids: extIds, skipped,
        catalog_sha256: createHash('sha256').update(JSON.stringify(cat.catalog)).digest('hex'),
    }, null, 2));
}

if (isMain) build().catch((e) => { console.error(e); process.exit(1); });
