import { identityFrom, requireIdentity } from './auth';
import type { Env } from './env';
import { evaluateName } from './moderation';
import { evaluateCommentFull } from './rules';
import { RULESET } from './ruleset';
import { addStrike, checkWrite, logModeration } from './strikes';
import { err, now, ok, sha256Hex } from './util';

// Phase 15: thread prefixes use the carrier vocabulary only — the 0006
// migration rewrote stored rows, so legacy anime:/novel: threads die here.
const THREAD_RE = /^(shows|manga|books):[^\s]{1,200}$/;
const DAY_MS = 86_400_000;

// ---------- names ----------

export async function handleSetName(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    let body: { name?: string };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });
    const name = (body.name ?? '').trim();
    const verdict = evaluateName(name);
    if (!verdict.ok) return err(422, 'rule', { rule: verdict.rule });

    // Discord-style discriminator: names aren't unique, (name, tag) is.
    // The tag is deterministic per (device, name) so it stays stable across
    // renames to the same name; a collision bumps by +1 until it fits.
    const tagBase = parseInt(
        (await sha256Hex(`tag:${id.device_hash}:${name.toLowerCase()}`)).slice(0, 8),
        16,
    );
    for (let bump = 0; bump < 100; bump++) {
        const tag = String((tagBase + bump) % 10000).padStart(4, '0');
        const clash = await env.COMMENTS_DB.prepare(
            'SELECT device_hash FROM identities WHERE LOWER(display_name) = LOWER(?) AND name_tag = ? AND device_hash != ?',
        ).bind(name, tag, id.device_hash).first();
        if (!clash) {
            await env.COMMENTS_DB.prepare(
                'UPDATE identities SET display_name = ?, name_tag = ?, name_set_at = ? WHERE device_hash = ?',
            ).bind(name, tag, now(), id.device_hash).run();
            return ok({ display_name: name, name_tag: tag });
        }
    }
    return err(409, 'name_taken');
}

// ---------- comments ----------

interface CommentRow {
    id: number;
    thread_id: string;
    device_hash: string;
    parent_id: number | null;
    body: string;
    created_at: number;
    edited_at: number | null;
    status: string;
    display_name: string | null;
    name_tag: string | null;
    score: number;
    replies: number;
    appeal_status?: string | null;
}

function commentJson(r: CommentRow, myVote?: number | null, mine = false) {
    return {
        id: r.id,
        thread_id: r.thread_id,
        parent_id: r.parent_id,
        author: { name: r.display_name ?? 'Anonymous', tag: r.name_tag },
        body: r.status === 'deleted' ? null : r.body,
        status: r.status,
        score: r.score,
        replies: r.replies,
        my_vote: myVote ?? null,
        mine,
        appeal_status: r.appeal_status ?? null,
        created_at: r.created_at,
        edited_at: r.edited_at,
    };
}

export async function handlePostComment(req: Request, env: Env): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;

    let body: { thread_id?: string; body?: string; parent_id?: number };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });

    const text = (body.body ?? '').trim();
    if (typeof body.thread_id !== 'string' || !THREAD_RE.test(body.thread_id)) {
        return err(400, 'bad_thread_id');
    }
    if (!id.display_name) return err(428, 'name_required');

    if (body.parent_id != null) {
        const parent = await env.COMMENTS_DB.prepare(
            'SELECT thread_id, status FROM comments WHERE id = ?',
        ).bind(body.parent_id).first<{ thread_id: string; status: string }>();
        if (!parent || parent.thread_id !== body.thread_id || parent.status === 'hidden') {
            return err(422, 'bad_parent');
        }
    }

    const verdict = await evaluateCommentFull(env, id.device_hash, text);
    if (!verdict.ok) {
        const suspendedUntil = await addStrike(env, id.device_hash);
        await logModeration(env, {
            device_hash: id.device_hash,
            layer: 'server_rule', rule_id: verdict.rule, action: 'reject',
        }, RULESET.version);
        return err(422, 'rule', { rule: verdict.rule, suspended_until: suspendedUntil });
    }

    const ts = now();
    const res = await env.COMMENTS_DB.prepare(
        `INSERT INTO comments (thread_id, device_hash, parent_id, body, created_at)
         VALUES (?,?,?,?,?)`,
    ).bind(body.thread_id, id.device_hash, body.parent_id ?? null, text, ts).run();

    const row = await env.COMMENTS_DB.prepare(`${SELECT_COMMENTS} WHERE c.id = ?`)
        .bind(res.meta.last_row_id).first<CommentRow>();
    return ok({ comment: commentJson(row!) }, 201);
}

const SELECT_COMMENTS = `
    SELECT c.*, i.display_name, i.name_tag,
           COALESCE(v.score, 0) AS score,
           (SELECT COUNT(*) FROM comments r WHERE r.parent_id = c.id) AS replies,
           (SELECT a.status FROM appeals a
            WHERE a.comment_id = c.id ORDER BY a.id DESC LIMIT 1) AS appeal_status
    FROM comments c
    LEFT JOIN identities i ON i.device_hash = c.device_hash
    LEFT JOIN (SELECT comment_id, SUM(value) AS score FROM votes GROUP BY comment_id) v
        ON v.comment_id = c.id
`;

export async function handleGetComments(req: Request, _p: unknown, url: URL, env: Env): Promise<Response> {
    const threadId = url.searchParams.get('thread_id');
    if (!threadId || !THREAD_RE.test(threadId)) return err(400, 'bad_thread_id');
    const before = Number(url.searchParams.get('before')) || Number.MAX_SAFE_INTEGER;
    const limit = Math.min(Number(url.searchParams.get('limit')) || 50, 100);

    const me = await identityFrom(req, env); // optional — readers need no auth

    // Own hidden comments are returned (status='hidden', `mine` flag set)
    // so the author can see and appeal them; everyone else's stay hidden.
    const { results } = await env.COMMENTS_DB.prepare(
        `${SELECT_COMMENTS}
         WHERE c.thread_id = ? AND c.id < ?
           AND (c.status IN ('visible','deleted') OR c.device_hash = ?)
         ORDER BY c.id DESC LIMIT ?`,
    ).bind(threadId, before, me?.device_hash ?? '', limit).all<CommentRow>();

    const votes = new Map<number, number>();
    if (me && results.length) {
        const ids = results.map((r) => r.id);
        const marks = await env.COMMENTS_DB.prepare(
            `SELECT comment_id, value FROM votes
             WHERE device_hash = ? AND comment_id IN (${ids.map(() => '?').join(',')})`,
        ).bind(me.device_hash, ...ids).all<{ comment_id: number; value: number }>();
        for (const m of marks.results) votes.set(m.comment_id, m.value);
    }

    return ok({
        comments: results.map(
            (r) => commentJson(r, me ? votes.get(r.id) : undefined, me?.device_hash === r.device_hash),
        ),
        next_before: results.length === limit ? results[results.length - 1].id : null,
    });
}

// ---------- votes ----------

export async function handleVote(
    req: Request,
    params: Record<string, string>,
    _u: URL,
    env: Env,
): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const commentId = Number(params.id);
    if (!Number.isInteger(commentId)) return err(400, 'bad_id');
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });

    let body: { value?: number };
    try {
        body = await req.json();
    } catch {
        return err(400, 'bad_json');
    }
    if (![-1, 0, 1].includes(body.value ?? NaN)) return err(400, 'bad_value');

    const exists = await env.COMMENTS_DB.prepare(
        'SELECT id FROM comments WHERE id = ?',
    ).bind(commentId).first();
    if (!exists) return err(404, 'not_found');

    if (body.value === 0) {
        await env.COMMENTS_DB.prepare(
            'DELETE FROM votes WHERE comment_id = ? AND device_hash = ?',
        ).bind(commentId, id.device_hash).run();
    } else {
        await env.COMMENTS_DB.prepare(
            `INSERT INTO votes (comment_id, device_hash, value, updated_at)
             VALUES (?,?,?,?)
             ON CONFLICT(comment_id, device_hash) DO UPDATE SET
               value = excluded.value, updated_at = excluded.updated_at`,
        ).bind(commentId, id.device_hash, body.value, now()).run();
    }
    const { score } = await env.COMMENTS_DB.prepare(
        'SELECT COALESCE(SUM(value),0) AS score FROM votes WHERE comment_id = ?',
    ).bind(commentId).first<{ score: number }>() as { score: number };
    return ok({ score });
}

// ---------- reports ----------

export async function handleReport(
    req: Request,
    params: Record<string, string>,
    _u: URL,
    env: Env,
): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const commentId = Number(params.id);
    if (!Number.isInteger(commentId)) return err(400, 'bad_id');

    const exists = await env.COMMENTS_DB.prepare(
        'SELECT id, status FROM comments WHERE id = ?',
    ).bind(commentId).first<{ id: number; status: string }>();
    if (!exists) return err(404, 'not_found');
    const gate = await checkWrite(env, id.device_hash);
    if (!gate.allowed) return err(403, 'suspended', { suspended_until: gate.suspended_until });

    // Reporter-age weighting: devices ≥30d old count 4× new ones.
    const weight = now() - id.created_at >= 30 * DAY_MS ? 1.0 : 0.25;
    await env.COMMENTS_DB.prepare(
        'INSERT OR IGNORE INTO reports (comment_id, device_hash, weight, created_at) VALUES (?,?,?,?)',
    ).bind(commentId, id.device_hash, weight, now()).run();

    // Auto-hide at cumulative weight ≥3 (no author strike — appeals and
    // false-report pile-ons route through Phase 10 review instead).
    const { total } = await env.COMMENTS_DB.prepare(
        'SELECT COALESCE(SUM(weight),0) AS total FROM reports WHERE comment_id = ?',
    ).bind(commentId).first<{ total: number }>() as { total: number };
    if (total >= 3 && exists.status === 'visible') {
        await env.COMMENTS_DB.prepare(
            "UPDATE comments SET status = 'hidden' WHERE id = ?",
        ).bind(commentId).run();
        await logModeration(env, {
            comment_id: commentId,
            layer: 'report_threshold', rule_id: 'auto_hide', action: 'hide',
        }, RULESET.version);
        return ok({ reported: true, hidden: true });
    }
    return ok({ reported: true });
}

// ---------- delete ----------

export async function handleDelete(
    req: Request,
    params: Record<string, string>,
    _u: URL,
    env: Env,
): Promise<Response> {
    const id = await requireIdentity(req, env);
    if (id instanceof Response) return id;
    const commentId = Number(params.id);
    if (!Number.isInteger(commentId)) return err(400, 'bad_id');

    const res = await env.COMMENTS_DB.prepare(
        `UPDATE comments SET status = 'deleted', body = ''
         WHERE id = ? AND device_hash = ? AND status = 'visible'`,
    ).bind(commentId, id.device_hash).run();
    if (!res.meta.changes) return err(404, 'not_found');
    return ok({ deleted: true });
}
