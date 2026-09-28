import { env, SELF } from 'cloudflare:test';
import { describe, expect, it } from 'vitest';

async function seen(body: unknown): Promise<Response> {
    return SELF.fetch('http://test/v1/ids/seen', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
    });
}

describe('ids/seen', () => {
    it('rejects malformed bodies', async () => {
        expect((await seen({})).status).toBe(400);
        expect((await seen({ id: 'not-a-miko-id', medium: 'manga' })).status).toBe(400);
        // 'comic' is a kind, not a carrier — unknown mediums are rejected.
        expect((await seen({ id: 'mk:m:abc', medium: 'comic' })).status).toBe(400);
        expect((await seen({ id: 'mk:m:abc', medium: 'bogus' })).status).toBe(400);
    });

    it('records and increments a seen id', async () => {
        const id = `mk:m:${'a'.repeat(64)}`;
        expect((await seen({ id, medium: 'manga' })).status).toBe(200);
        expect((await seen({ id, medium: 'manga' })).status).toBe(200);

        const row = await env.COMMENTS_DB.prepare(
            'SELECT work_id, medium, seen_count FROM seen_ids WHERE work_id = ?1',
        )
            .bind(id)
            .first<{ work_id: string; medium: string; seen_count: number }>();
        expect(row?.work_id).toBe(id);
        expect(row?.medium).toBe('manga');
        expect(row?.seen_count).toBe(2);
    });

    it('accepts the shows carrier', async () => {
        const id = `mk:m:${'d'.repeat(64)}`;
        expect((await seen({ id, medium: 'shows' })).status).toBe(200);
        const row = await env.COMMENTS_DB.prepare(
            'SELECT medium FROM seen_ids WHERE work_id = ?1',
        ).bind(id).first<{ medium: string }>();
        expect(row?.medium).toBe('shows');
    });

    it('rejects legacy mediums (alias window closed)', async () => {
        const idA = `mk:m:${'e'.repeat(64)}`;
        const idB = `mk:m:${'f'.repeat(64)}`;
        expect((await seen({ id: idA, medium: 'anime' })).status).toBe(400);
        expect((await seen({ id: idB, medium: 'novel' })).status).toBe(400);
    });

    it('stores no user or device fields', async () => {
        const cols = await env.COMMENTS_DB.prepare(
            "SELECT name FROM pragma_table_info('seen_ids')",
        ).all<{ name: string }>();
        const names = cols.results.map((c) => c.name);
        expect(names).not.toContain('device_id');
        expect(names).not.toContain('user_id');
    });
});
