import { handleAlign } from './align';
import { handleGetBindings } from './bindings';
import { handleAppeal, handleMyAppeals } from './appeals';
import { handleRegister } from './auth';
import { handleBrowse, handleHydrate } from './browse';
import { handleCanonize, handleGetChapters } from './chaptermap';
import {
    handleDelete,
    handleGetComments,
    handlePostComment,
    handleReport,
    handleSetName,
    handleThreadCounts,
    handleVote,
} from './comments';
import type { Env } from './env';
import { handleResolve } from './resolve';
import { runMangadexCron } from './mangadexseed';
import { refreshWebtoonChart } from './webtoonchart';
import { handleIdSeen } from './seen';
import { Router } from './router';
import { RULESET } from './ruleset';
import { err, ok } from './util';

const router = new Router<Env>()
    .add('GET', '/v1/health', () => ok({ ok: true }))
    .add('GET', '/v1/ruleset', () =>
        ok({ ruleset_version: RULESET.version, ruleset: RULESET }))
    .add('POST', '/v1/works/resolve', (req, _p, _u, env) => handleResolve(req, env))
    .add('GET', '/v1/works/browse', (_req, _p, url, env) => handleBrowse(url, env))
    .add('GET', '/v1/works/hydrate', (_req, _p, url, env) => handleHydrate(url, env))
    .add('POST', '/v1/chapters/align', (req, _p, _u, env) => handleAlign(req, env))
    .add('POST', '/v1/chapters/canonize', (req, _p, _u, env) => handleCanonize(req, env))
    .add('GET', '/v1/works/:id/chapters', handleGetChapters)
    .add('GET', '/v1/works/:id/bindings', handleGetBindings)
    // Manual chart refresh — same X-Crawler-Key gate as crawler feeds.
    .add('POST', '/v1/charts/webtoon/refresh', (req, _p, _u, env) => {
        if (!env.CRAWLER_KEY || req.headers.get('x-crawler-key') !== env.CRAWLER_KEY) {
            return Promise.resolve(err(403, 'forbidden'));
        }
        return refreshWebtoonChart(env).then(ok);
    })
    .add('POST', '/v1/ids/seen', (req, _p, _u, env) => handleIdSeen(req, env))
    .add('POST', '/v1/device/register', (req, _p, _u, env) => handleRegister(req, env))
    .add('POST', '/v1/names', (req, _p, _u, env) => handleSetName(req, env))
    .add('GET', '/v1/comments', (req, _p, url, env) => handleGetComments(req, _p, url, env))
    .add('GET', '/v1/threads/counts', (_req, _p, url, env) => handleThreadCounts(_req, _p, url, env))
    .add('POST', '/v1/comments', (req, _p, _u, env) => handlePostComment(req, env))
    .add('POST', '/v1/comments/:id/vote', handleVote)
    .add('POST', '/v1/comments/:id/report', handleReport)
    .add('POST', '/v1/comments/:id/appeal', handleAppeal)
    .add('GET', '/v1/appeals/mine', (req, _p, _u, env) => handleMyAppeals(req, env))
    .add('DELETE', '/v1/comments/:id', handleDelete);

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        try {
            return await router.handle(request, env);
        } catch (e) {
            console.error('unhandled', e);
            return Response.json({ error: 'degraded' }, { status: 503 });
        }
    },

    /** [triggers] crons — MangaDex canonical-feed refresh/backlog plus
     *  the webtoon platform chart re-pull (boards change daily-ish). */
    async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
        ctx.waitUntil(
            Promise.allSettled([runMangadexCron(env), refreshWebtoonChart(env)])
                .then((rs) => rs.forEach((r) => {
                    if (r.status === 'rejected') console.error('cron leg failed', r.reason);
                })),
        );
    },
};
