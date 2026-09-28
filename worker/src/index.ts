import { handleAlign } from './align';
import { handleAppeal, handleMyAppeals } from './appeals';
import { handleRegister } from './auth';
import { handleBrowse } from './browse';
import { handleCanonize, handleGetChapters } from './chaptermap';
import {
    handleDelete,
    handleGetComments,
    handlePostComment,
    handleReport,
    handleSetName,
    handleVote,
} from './comments';
import type { Env } from './env';
import { handleResolve } from './resolve';
import { handleIdSeen } from './seen';
import { Router } from './router';
import { RULESET } from './ruleset';
import { ok } from './util';

const router = new Router<Env>()
    .add('GET', '/v1/health', () => ok({ ok: true }))
    .add('GET', '/v1/ruleset', () =>
        ok({ ruleset_version: RULESET.version, ruleset: RULESET }))
    .add('POST', '/v1/works/resolve', (req, _p, _u, env) => handleResolve(req, env))
    .add('GET', '/v1/works/browse', (_req, _p, url, env) => handleBrowse(url, env))
    .add('POST', '/v1/chapters/align', (req, _p, _u, env) => handleAlign(req, env))
    .add('POST', '/v1/chapters/canonize', (req, _p, _u, env) => handleCanonize(req, env))
    .add('GET', '/v1/works/:id/chapters', handleGetChapters)
    .add('POST', '/v1/ids/seen', (req, _p, _u, env) => handleIdSeen(req, env))
    .add('POST', '/v1/device/register', (req, _p, _u, env) => handleRegister(req, env))
    .add('POST', '/v1/names', (req, _p, _u, env) => handleSetName(req, env))
    .add('GET', '/v1/comments', (req, _p, url, env) => handleGetComments(req, _p, url, env))
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
};
