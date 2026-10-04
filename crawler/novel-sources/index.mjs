/**
 * Novel source adapter registry — ports of Zen0-99/MikoNovelSources Kotlin
 * extensions. `pkg` matches the Android package name exactly: WorkCarriers
 * joins bindings on it.
 *
 * Tiering: S = broadest English catalogs, A = WP-clone aggregators,
 * B = present but lower coverage/quality.
 */
import * as royalroad from './royalroad.mjs';
import * as scribblehub from './scribblehub.mjs';
import * as novelfire from './novelfire.mjs';
import * as freewebnovel from './freewebnovel.mjs';
import * as novelfull from './novelfull.mjs';
import * as readnovelfull from './readnovelfull.mjs';
import * as novelsonline from './novelsonline.mjs';
import * as boxnovel from './boxnovel.mjs';

export const SOURCES = {
    S: [royalroad, scribblehub, novelfire],
    A: [freewebnovel, novelfull, readnovelfull, novelsonline, boxnovel],
    B: [],
};
