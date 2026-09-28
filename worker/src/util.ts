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
