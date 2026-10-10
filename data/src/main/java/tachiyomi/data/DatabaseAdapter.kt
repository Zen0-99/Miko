package tachiyomi.data

import app.cash.sqldelight.ColumnAdapter
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlin.time.Instant

object DateColumnAdapter : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)
    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}

private const val LIST_OF_STRINGS_SEPARATOR = ", "
object StringListColumnAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            emptyList()
        } else {
            databaseValue.split(LIST_OF_STRINGS_SEPARATOR)
        }
    override fun encode(value: List<String>) = value.joinToString(
        separator = LIST_OF_STRINGS_SEPARATOR,
    )
}

object MangaUpdateStrategyColumnAdapter : ColumnAdapter<UpdateStrategy, Long> {
    override fun decode(databaseValue: Long): UpdateStrategy =
        UpdateStrategy.entries.getOrElse(databaseValue.toInt()) { UpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: UpdateStrategy): Long = value.ordinal.toLong()
}

object AnimeUpdateStrategyColumnAdapter : ColumnAdapter<AnimeUpdateStrategy, Long> {
    override fun decode(databaseValue: Long): AnimeUpdateStrategy =
        AnimeUpdateStrategy.entries.getOrElse(databaseValue.toInt()) { AnimeUpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: AnimeUpdateStrategy): Long = value.ordinal.toLong()
}

object FetchTypeColumnAdapter : ColumnAdapter<FetchType, Long> {
    override fun decode(databaseValue: Long): FetchType =
        FetchType.entries.getOrElse(databaseValue.toInt()) { FetchType.Episodes }

    override fun encode(value: FetchType): Long = value.ordinal.toLong()
}

object NovelUpdateStrategyColumnAdapter : ColumnAdapter<NovelUpdateStrategy, Long> {
    override fun decode(databaseValue: Long): NovelUpdateStrategy =
        NovelUpdateStrategy.entries.getOrElse(databaseValue.toInt()) { NovelUpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: NovelUpdateStrategy): Long = value.ordinal.toLong()
}
