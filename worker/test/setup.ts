import { applyD1Migrations, env } from 'cloudflare:test';

declare module 'cloudflare:test' {
    interface ProvidedEnv {
        COMMENTS_DB: D1Database;
        TEST_MIGRATIONS: D1Migration[];
        MANGABAKA_BASE: string;
        OPENLIBRARY_BASE?: string;
        JEV_MODERATION_KEY?: string;
        TYPESAFE_ENDPOINT?: string;
        CRAWLER_KEY?: string;
        WEBTOON_BASE?: string;
    }
}

await applyD1Migrations(env.COMMENTS_DB, env.TEST_MIGRATIONS);
