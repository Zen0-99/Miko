import type { Env } from './env';
import { err, now, ok } from './util';

// Phase 14-01: coverage-gap telemetry. Ingests ONLY the work id + medium —
// no user id, device id, or metadata — so MikoDB can rank which uncataloged
// (locally minted) works are popular enough to promote into the catalog.
// Phase 15: medium vocabulary is the three carriers {shows, manga, books};
// canonical-only ingest — legacy anime/novel requests are rejected.
const MEDIA = new Set(['shows', 'manga', 'books']);
const ID_MAX = 128;

interface SeenRequest {
    id?: string;
    medium?: string;
}

export async function handleIdSeen(req: Request, env: Env): Promise<Response> {
    let body: SeenRequest;
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    const id = typeof body.id === 'string' ? body.id : '';
    const medium = typeof body.medium === 'string' ? body.medium : '';
    if (!id.startsWith('mk:') || id.length > ID_MAX) return err(400, 'bad_id');
    if (!MEDIA.has(medium)) return err(400, 'bad_medium');

    await env.COMMENTS_DB.prepare(
        `INSERT INTO seen_ids (work_id, medium, first_seen, seen_count)
         VALUES (?1, ?2, ?3, 1)
         ON CONFLICT(work_id) DO UPDATE SET seen_count = seen_count + 1`,
    )
        .bind(id, medium, now())
        .run();
    return ok({ id, recorded: true });
}
