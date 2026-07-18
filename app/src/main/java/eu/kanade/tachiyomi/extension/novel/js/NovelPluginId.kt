package eu.kanade.tachiyomi.extension.novel.js

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Generates stable source IDs for JS-based novel plugins.
 *
 * JS plugins use SHA-256(pluginId) → positive Long, which differs from
 * APK extensions that use MD5(name.lowercase()/lang/versionId). This
 * difference is handled by [eu.kanade.tachiyomi.data.backup.restore.SourceIdMapper]
 * during backup restore.
 */
object NovelPluginId {
    fun toSourceId(pluginId: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(pluginId.toByteArray())
        val value = ByteBuffer.wrap(digest).long
        return value and Long.MAX_VALUE
    }
}
