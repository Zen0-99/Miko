import { RULESET } from './ruleset';

export type RuleResult = { ok: true } | { ok: false; rule: string };

/**
 * Pure ruleset checks — dependency-free so the Kotlin twin in Phase 9
 * can reimplement the same pipeline from the same ruleset.json.
 * Stateful checks (rate limits, duplicates) live in comments.ts via
 * injected queries — wired in plan 08-03 alongside strikes/logging.
 */

const URL_RE = /https?:\/\/[^\s)]+/gi;

export function evaluateComment(body: string): RuleResult {
    const r = RULESET.comment;
    const len = body.trim().length;
    if (len < r.min_len) return { ok: false, rule: 'min_len' };
    if (len > r.max_len) return { ok: false, rule: 'max_len' };
    const links = body.match(URL_RE) ?? [];
    if (links.length > r.max_links) return { ok: false, rule: 'max_links' };
    for (const domain of r.blocked_domains) {
        if (links.some((l) => l.toLowerCase().includes(domain.toLowerCase()))) {
            return { ok: false, rule: 'blocked_domain' };
        }
    }
    const lower = body.toLowerCase();
    for (const word of r.blocklist) {
        if (lower.includes(word.toLowerCase())) return { ok: false, rule: 'blocklist' };
    }
    for (const pattern of r.spam_patterns) {
        try {
            // Patterns are engine-agnostic: the 'iu' flags are applied by
            // the evaluator, not baked into the pattern (JS lacks (?i)).
            if (new RegExp(pattern, 'iu').test(body)) return { ok: false, rule: 'spam' };
        } catch {
            // Malformed pattern in a shipped ruleset — skip, don't 500.
        }
    }
    return { ok: true };
}

export function evaluateName(name: string): RuleResult {
    const r = RULESET.name;
    const trimmed = name.trim();
    if (trimmed.length < r.min_len) return { ok: false, rule: 'min_len' };
    if (trimmed.length > r.max_len) return { ok: false, rule: 'max_len' };
    try {
        if (!new RegExp(r.pattern, 'u').test(trimmed)) {
            return { ok: false, rule: 'pattern' };
        }
    } catch {
        return { ok: false, rule: 'pattern' };
    }
    if (r.reserved.some((w) => w.toLowerCase() === trimmed.toLowerCase())) {
        return { ok: false, rule: 'reserved' };
    }
    return { ok: true };
}
