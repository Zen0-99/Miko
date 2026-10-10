package tachiyomi.data.extension.novel

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat

class DefaultNovelPluginStorage(
    baseDirPath: String,
) : NovelPluginStorage {

    private val baseDir = Path(baseDirPath)

    private fun pluginDir(): Path {
        if (!SystemFileSystem.exists(baseDir)) {
            SystemFileSystem.createDirectories(baseDir)
        }
        return baseDir
    }

    override fun writePluginFiles(
        pluginId: String,
        script: ByteArray,
        customJs: ByteArray?,
        customCss: ByteArray?,
    ) {
        pluginDir()
        SystemFileSystem.sink(pluginScriptFile(pluginId)).buffered().use { sink ->
            sink.write(script)
        }
        logcat(LogPriority.INFO) {
            "Novel plugin script saved id=$pluginId file=${pluginScriptFile(pluginId)} bytes=${script.size}"
        }

        customJs?.let {
            SystemFileSystem.sink(customJsFile(pluginId)).buffered().use { sink ->
                sink.write(it)
            }
            logcat(LogPriority.INFO) {
                "Novel plugin custom JS saved id=$pluginId file=${customJsFile(pluginId)} bytes=${it.size}"
            }
        }
        customCss?.let {
            SystemFileSystem.sink(customCssFile(pluginId)).buffered().use { sink ->
                sink.write(it)
            }
            logcat(LogPriority.INFO) {
                "Novel plugin custom CSS saved id=$pluginId file=${customCssFile(pluginId)} bytes=${it.size}"
            }
        }
    }

    override fun deletePluginFiles(pluginId: String) {
        deleteIfExists(pluginScriptFile(pluginId))
        deleteIfExists(customJsFile(pluginId))
        deleteIfExists(customCssFile(pluginId))
    }

    override fun readPluginScript(pluginId: String): ByteArray? {
        val file = pluginScriptFile(pluginId)
        return if (SystemFileSystem.exists(file)) {
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        } else {
            logcat(LogPriority.WARN) { "Novel plugin script missing id=$pluginId file=$file" }
            null
        }
    }

    override fun readCustomJs(pluginId: String): ByteArray? {
        val file = customJsFile(pluginId)
        return if (SystemFileSystem.exists(file)) {
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        } else {
            null
        }
    }

    override fun readCustomCss(pluginId: String): ByteArray? {
        val file = customCssFile(pluginId)
        return if (SystemFileSystem.exists(file)) {
            SystemFileSystem.source(file).buffered().use { it.readByteArray() }
        } else {
            null
        }
    }

    private fun deleteIfExists(path: Path) {
        if (SystemFileSystem.exists(path)) {
            SystemFileSystem.delete(path)
        }
    }

    private fun pluginScriptFile(pluginId: String): Path {
        return Path(pluginDir(), "${sanitizeId(pluginId)}.js")
    }

    private fun customJsFile(pluginId: String): Path {
        return Path(pluginDir(), "${sanitizeId(pluginId)}.custom.js")
    }

    private fun customCssFile(pluginId: String): Path {
        return Path(pluginDir(), "${sanitizeId(pluginId)}.custom.css")
    }

    private fun sanitizeId(value: String): String {
        return value.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
