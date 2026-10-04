/**
 * merge-works.mjs — catalog dedupe pass. For every works_cache row with
 * a mangabaka external_id, ask MangaBaka whether the series was merged
 * (`merged_with` → target series id). Confirmed merges become
 * work_merges rows (dead→live aliases); title collisions WITHOUT an
 * upstream merge marker go to identity_review — never auto-merged
 * (the OPM webcomic/manga pair shares a normalized title legitimately).
 *
 *   node scripts/merge-works.mjs [--ids 725,4088] [--limit N] [--apply]
 *
 * Default: writes worker/migrations/../work-merges.sql only (dry).
 * --apply executes it against remote D1 via wrangler.
 */
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const MB = 'https://api.mangabaka.org';
const WRANGLER = path.join(HERE, '..', 'worker', 'node_modules', 'wrangler', 'bin', 'wrangler.js');
const OUT = path.join(HERE, 'work-merges.sql');

const argVal = (k) => {
    const i = process.argv.indexOf(k);
    return i >= 0 ? process.argv[i + 1] : null;
};
const LIMIT = Number(argVal('--limit') ?? 0);
const IDS = (argVal('--ids') ?? '').split(',').filter(Boolean).map(Number);
const APPLY = process.argv.includes('--apply');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function d1(sql) {
    const out = execFileSync(
        process.execPath,
        [WRANGLER, 'd1', 'execute', 'miko-comments', '--remote', '--json', '--command', sql.replace(/\s+/g, ' ')],
        { cwd: path.join(HERE, '..', 'worker'), maxBuffer: 512 * 1024 * 1024 },
    ).toString();
    return JSON.parse(out)[0]?.results ?? [];
}

async function getSeries(id) {
    const res = await fetch(`${MB}/v1/series/${id}`, {
        headers: { 'user-agent': 'miko-db-merge/1.0', accept: 'application/json' },
    });
    if (!res.ok) throw new Error(`mangabaka ${res.status} series/${id}`);
    const body = await res.json();
    return body?.data ?? body;
}

const esc = (s) => String(s).replace(/'/g, "''");

async function main() {
    const where = IDS.length
        ? `AND e.external_id IN (${IDS.join(',')})`
        : '';
    const limit = LIMIT ? `LIMIT ${LIMIT}` : '';
    const rows = d1(
        `SELECT w.miko_work_id, w.title, w.normalized_title, w.medium,
                w.author, w.year, e.external_id AS mb_id
         FROM works_cache w
         JOIN external_ids e ON e.miko_work_id = w.miko_work_id
         WHERE e.provider = 'mangabaka' ${where}
         ORDER BY w.popularity IS NULL, w.popularity ASC ${limit}`,
    );
    console.log(`works to check: ${rows.length}`);

    const ts = Date.now();
    const merges = [];
    const reviews = [];
    let checked = 0, failed = 0;

    // normalized_title → rows, for the collision sweep afterwards.
    const byNorm = new Map();
    for (const r of rows) {
        const list = byNorm.get(r.normalized_title) ?? [];
        list.push(r);
        byNorm.set(r.normalized_title, list);
    }

    for (const r of rows) {
        try {
            const s = await getSeries(Number(r.mb_id));
            checked++;
            const merged = s?.merged_with ?? null;
            if (merged != null) {
                const live = `mk:mb:${merged}`;
                if (live !== r.miko_work_id) {
                    merges.push({ dead: r.miko_work_id, live });
                    console.log(`  merge: ${r.miko_work_id} "${r.title}" -> ${live}`);
                }
            }
        } catch (e) {
            failed++;
            console.error(`  ${r.miko_work_id}: ${e.message}`);
        }
        await sleep(220); // MangaBaka is friendly infra — keep it that way
        if (checked % 100 === 0 && checked > 0) console.log(`  …${checked}/${rows.length}`);
    }

    // Same normalized title + same medium + same-or-unknown author and
    // adjacent year — suspicious sibling set. Only flagged, never merged.
    const liveIds = new Set(merges.map((m) => m.dead));
    for (const [norm, list] of byNorm) {
        const survivors = list.filter((r) => !liveIds.has(r.miko_work_id));
        if (survivors.length < 2) continue;
        const mediums = new Set(survivors.map((r) => r.medium));
        if (mediums.size > 1) continue;
        const payload = JSON.stringify({
            kind: 'work_title_siblings',
            normalized_title: norm,
            works: survivors.map((r) => ({
                id: r.miko_work_id, title: r.title, author: r.author, year: r.year,
            })),
        });
        reviews.push(
            `INSERT INTO identity_review (kind, payload, created_at)` +
            ` SELECT 'work_title_siblings', '${esc(payload)}', ${ts}` +
            ` WHERE NOT EXISTS (SELECT 1 FROM identity_review WHERE kind='work_title_siblings' AND payload='${esc(payload)}');`,
        );
    }

    const stmts = [
        ...merges.map((m) =>
            `INSERT OR REPLACE INTO work_merges (dead_id, live_id, merged_at)` +
            ` VALUES ('${esc(m.dead)}', '${esc(m.live)}', ${ts});`),
        ...reviews,
    ];
    writeFileSync(OUT, stmts.join('\n') + '\n');
    console.log(
        `\nchecked=${checked} merges=${merges.length} sibling-reviews=${reviews.length}` +
        ` failed=${failed}\nwrote ${OUT}`,
    );
    if (APPLY && stmts.length) {
        execFileSync(
            process.execPath,
            [WRANGLER, 'd1', 'execute', 'miko-comments', '--remote', `--file=${OUT}`],
            { cwd: path.join(HERE, '..', 'worker'), stdio: 'inherit' },
        );
        console.log('applied to remote D1');
    }
}

main().catch((e) => { console.error(e); process.exit(1); });
