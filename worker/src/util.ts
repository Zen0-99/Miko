export function ok(data: unknown, status = 200): Response {
    return Response.json(data, { status });
}

export function err(status: number, code: string, extra?: Record<string, unknown>): Response {
    return Response.json({ error: code, ...extra }, { status });
}

export function now(): number {
    return Date.now();
}

export async function sha256Hex(input: string): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(input));
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export function isHex64(s: unknown): s is string {
    return typeof s === 'string' && /^[0-9a-f]{64}$/.test(s);
}

// DEPRECATED: one-deploy-cycle legacy alias window (15-CONTEXT.md) —
// resolve/seen/align normalize these to canonical carriers so no legacy
// medium values land in new rows; drop once the app-side rename ships
// and the cleanup deploy removes this map.
export const MEDIUM_ALIASES: Record<string, string> = {
    anime: 'shows',
    novel: 'books',
};
