import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineWorkersConfig, readD1Migrations } from '@cloudflare/vitest-pool-workers/config';

export default defineWorkersConfig(async () => {
    const migrations = await readD1Migrations(
        path.join(path.dirname(fileURLToPath(import.meta.url)), 'migrations'),
    );
    return {
        test: {
            setupFiles: ['./test/setup.ts'],
            poolOptions: {
                workers: {
                    wrangler: { configPath: './wrangler.toml' },
                    miniflare: {
                        bindings: {
                            TEST_MIGRATIONS: migrations,
                            // Unreachable — tests exercise the offline/degraded
                            // path deterministically; live-mirror behavior is
                            // verified against staging, not in unit tests.
                            // (A closed high port, not a fetch bad-port or a
                            // DNS miss, so taxonomy tests can still intercept
                            // it via fetchMock.)
                            MANGABAKA_BASE: 'http://127.0.0.1:59999',
                            // Book tests intercept this origin via fetchMock.
                            OPENLIBRARY_BASE: 'http://ol.test',
                            // Shows tests intercept these via fetchMock;
                            // the catalog URL serves plain JSON (the
                            // loader sniffs the gzip magic).
                            CINEMETA_BASE: 'http://cinemeta.test',
                            HENTAISTREAM_CATALOG_URL: 'http://hs.test/catalog.json',
                            // Appeals tests intercept api.typesafe.ai via
                            // fetchMock; a non-empty key enables the path.
                            JEV_MODERATION_KEY: 'test-key',
                        },
                    },
                },
            },
        },
    };
});
