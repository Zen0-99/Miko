package tachiyomi.data.extension.novel

interface NovelPluginStorage {

    fun writePluginFiles(
        pluginId: String,
        script: ByteArray,
        customJs: ByteArray?,
        customCss: ByteArray?,
    )

    fun deletePluginFiles(pluginId: String)

    fun readPluginScript(pluginId: String): ByteArray?

    fun readCustomJs(pluginId: String): ByteArray?

    fun readCustomCss(pluginId: String): ByteArray?
}
