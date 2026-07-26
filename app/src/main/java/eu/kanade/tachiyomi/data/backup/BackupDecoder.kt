package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupNovelSource
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.MikoBackup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.buffer
import okio.gzip
import okio.source
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

class BackupDecoder(
    private val context: Context,
    private val parser: ProtoBuf = Injekt.get(),
) {
    /**
     * Decode a potentially-gzipped backup.
     */
    fun decode(uri: Uri): Backup {
        return context.contentResolver.openInputStream(uri)!!.use { inputStream ->
            val source = inputStream.source().buffer()

            val peeked = source.peek().apply {
                require(2)
            }
            val id1id2 = peeked.readShort()
            val backupString = when (id1id2.toInt()) {
                0x1f8b -> source.gzip().buffer() // 0x1f8b is gzip magic bytes
                MAGIC_JSON_SIGNATURE1, MAGIC_JSON_SIGNATURE2, MAGIC_JSON_SIGNATURE3 -> {
                    throw IOException(context.stringResource(MR.strings.invalid_backup_file_json))
                }
                else -> source
            }.use { it.readByteArray() }

            try {
                if (BackupDetector.isLegacyBackup(backupString)) {
                    parser.decodeFromByteArray(LegacyBackup.serializer(), backupString)
                        .toBackup()
                } else {
                    val backup = parser.decodeFromByteArray(Backup.serializer(), backupString)
                    if (backup.backupNovels.isEmpty()) {
                        tryMikoBackup(backupString, backup)
                    } else {
                        backup
                    }
                }
            } catch (_: SerializationException) {
                throw IOException(context.stringResource(MR.strings.invalid_backup_file_unknown))
            }
        }
    }

    companion object {
        private const val MAGIC_JSON_SIGNATURE1 = 0x7b7d // `{}`
        private const val MAGIC_JSON_SIGNATURE2 = 0x7b22 // `{"`
        private const val MAGIC_JSON_SIGNATURE3 = 0x7b0a // `{\n`
    }

    private fun tryMikoBackup(bytes: ByteArray, backup: Backup): Backup {
        return try {
            val mikoBackup = parser.decodeFromByteArray(MikoBackup.serializer(), bytes)
            if (mikoBackup.backupNovels.isEmpty()) {
                backup
            } else {
                backup.copy(
                    backupNovels = mikoBackup.backupNovels.map { it.toBackupNovel() },
                    backupNovelCollection = mikoBackup.backupNovelCollections.map { it.toBackupCollection() },
                    backupNovelSources = mikoBackup.backupNovelSources.map { BackupNovelSource(it.name, it.sourceId, it.baseUrl) },
                )
            }
        } catch (_: SerializationException) {
            backup
        } catch (_: Exception) {
            backup
        }
    }
}
