export type Params = Record<string, string>;
export type Handler<E> = (
    req: Request,
    params: Params,
    url: URL,
    env: E,
) => Promise<Response> | Response;

interface Route<E> {
    method: string;
    segments: string[];
    handler: Handler<E>;
}

export class Router<E> {
    private routes: Route<E>[] = [];

    add(method: string, path: string, handler: Handler<E>): this {
        this.routes.push({ method, segments: path.split('/').filter(Boolean), handler });
        return this;
    }

    async handle(req: Request, env: E): Promise<Response> {
        const url = new URL(req.url);
        const parts = url.pathname.split('/').filter(Boolean);
        for (const r of this.routes) {
            if (r.method !== req.method || r.segments.length !== parts.length) continue;
            const params: Params = {};
            let matched = true;
            for (let i = 0; i < parts.length; i++) {
                const seg = r.segments[i];
                if (seg.startsWith(':')) {
                    params[seg.slice(1)] = decodeURIComponent(parts[i]);
                } else if (seg !== parts[i]) {
                    matched = false;
                    break;
                }
            }
            if (matched) return r.handler(req, params, url, env);
        }
        return Response.json({ error: 'not_found' }, { status: 404 });
    }
}
