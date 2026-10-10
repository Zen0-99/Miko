package eu.kanade.tachiyomi.extension.novel

import tachiyomi.domain.extension.novel.model.NovelPlugin

fun normalizeNovelLang(raw: String?): String {
    return tachiyomi.domain.extension.novel.normalizeNovelLang(raw)
}

fun NovelPlugin.Available.withNormalizedLang(): NovelPlugin.Available {
    val normalized = normalizeNovelLang(lang)
    return if (normalized == lang) this else copy(lang = normalized)
}

fun NovelPlugin.Installed.withNormalizedLang(): NovelPlugin.Installed {
    val normalized = normalizeNovelLang(lang)
    return if (normalized == lang) this else copy(lang = normalized)
}
