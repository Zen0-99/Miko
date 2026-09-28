import type { Env } from './env';

const DEFAULT_ENDPOINT = 'https://api.typesafe.ai/v1/systemone';
const TIMEOUT_MS = 5_000;

export interface AppealVerdict {
    choice: 'restore' | 'uphold';
    confidence: number;
}

interface SystemOneResponse {
    answers?: {
        verdict?: {
            type: string;
            choice?: string;
            probabilities?: Record<string, number>;
            confidence?: number;
        };
    };
}

export interface AppealState {
    comment_body: string;
    triggered_rule: string;
    ruleset_version: number;
    report_weight: number;
    appeal_reason: string | null;
}

/**
 * Ask Jev whether a hidden comment should be restored. A single Choice
 * question — appeals are binary and rare, so cost stays negligible
 * (~$0.00007/decision). Returns null on any failure: the caller keeps
 * the appeal `pending` rather than guessing.
 */
export async function judgeAppeal(
    env: Env,
    state: AppealState,
): Promise<AppealVerdict | null> {
    const key = env.JEV_MODERATION_KEY;
    if (!key) return null;

    try {
        const res = await fetch(env.TYPESAFE_ENDPOINT ?? DEFAULT_ENDPOINT, {
            method: 'POST',
            headers: {
                authorization: `Bearer ${key}`,
                'content-type': 'application/json',
            },
            body: JSON.stringify({
                model: 'jev-latest',
                state,
                questions: {
                    verdict: {
                        type: 'choice',
                        instructions: {
                            question:
                                'A user comment was hidden by automated moderation ' +
                                '(deterministic rule or report threshold) and the author ' +
                                'appealed. Does the comment break the stated rules?',
                            comment: '`comment_body`',
                            rule_triggered: '`triggered_rule`',
                            appeal_reason: '`appeal_reason`',
                        },
                        criteria: {
                            restore:
                                'The comment does not actually violate the triggered ' +
                                'rule — the hide was a false positive (matched pattern ' +
                                'used innocently, mass-report abuse, etc.).',
                            uphold:
                                'The comment genuinely violates the triggered rule or ' +
                                'the appeal gives no credible reason the rule was ' +
                                'misapplied.',
                        },
                    },
                },
            }),
            signal: AbortSignal.timeout(TIMEOUT_MS),
        });
        if (!res.ok) {
            console.error('jev http', res.status);
            return null;
        }
        const body = await res.json<SystemOneResponse>();
        const choice = body.answers?.verdict;
        if (choice?.choice !== 'restore' && choice?.choice !== 'uphold') {
            console.error('jev bad answer', JSON.stringify(body));
            return null;
        }
        return { choice: choice.choice, confidence: choice.confidence ?? 0 };
    } catch (e) {
        console.error('jev fetch failed', e);
        return null;
    }
}
