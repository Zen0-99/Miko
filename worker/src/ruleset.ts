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
        rate: {
            posts_per_min: number;
            posts_per_hour: number;
            /** Anonymous-device "trust level": identities younger than
             *  this many ms use the `new_device_*` hourly cap instead. */
            new_device_ms?: number;
            new_device_posts_per_hour?: number;
            /** Per-thread flood cap within `thread_burst_window_ms`. */
            thread_burst_per_window?: number;
            thread_burst_window_ms?: number;
        };
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
