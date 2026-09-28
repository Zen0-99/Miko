import rulesetJson from '../ruleset.json';

export interface Ruleset {
    version: number;
    comment: {
        min_len: number;
        max_len: number;
        max_links: number;
        blocklist: string[];
        blocked_domains: string[];
        spam_patterns: string[];
        rate: { posts_per_min: number; posts_per_hour: number };
        duplicate_window_h: number;
    };
    name: {
        min_len: number;
        max_len: number;
        pattern: string;
        reserved: string[];
    };
}

export const RULESET = rulesetJson as unknown as Ruleset;
