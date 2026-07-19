package eu.kanade.tachiyomi.ui.reader.novel.tts

import android.content.Context
import eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import tachiyomi.core.common.util.system.logcat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Represents a voice that has been downloaded and extracted to local storage.
 */
data class InstalledNeuralVoice(
    val voiceId: String,
    val path: File,
    val modelFile: File,
    val tokensFile: File,
    val family: String,
    val displayName: String,
    val sampleRateHz: Int,
)

/**
 * Manages installation state and downloads for sherpa-onnx neural TTS voices.
 *
 * Voices are stored under `context.filesDir/voices/<voiceId>/`. Each voice
 * directory is expected to contain at least a `*.onnx` model file and a
 * `tokens.txt` file (Piper voices also ship an `espeak-ng-data/` folder).
 *
 * Downloads are streamed to `context.cacheDir/downloads/<voiceId>.tar.bz2.part`,
 * optionally verified against a SHA-256 checksum, then extracted (stripping the
 * leading top-level directory common to sherpa-onnx tar bundles) into the
 * voice directory. The cached archive is deleted after successful extraction.
 */
class NeuralVoiceManager(
    private val context: Context,
    private val preferences: NovelTtsPreferences,
) {

    /** Root directory for installed voices. */
    val voicesDir: File
        get() = File(context.filesDir, "voices").also { it.mkdirs() }

    /** Directory used to stage in-progress downloads. */
    private val downloadsDir: File
        get() = File(context.cacheDir, "downloads").also { it.mkdirs() }

    /** HTTP client used for downloads. A plain client is sufficient. */
    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    // ------------------------------------------------------------------
    // Installed voice queries
    // ------------------------------------------------------------------

    /**
     * Scan [voicesDir] for installed voices. A subdirectory is considered an
     * installed voice if it contains at least one `.onnx` file and a
     * `tokens.txt` file.
     */
    fun getInstalledVoices(): List<InstalledNeuralVoice> {
        if (!voicesDir.exists()) return emptyList()
        val result = mutableListOf<InstalledNeuralVoice>()
        voicesDir.listFiles { f -> f.isDirectory }?.forEach { dir ->
            val installed = scanVoiceDir(dir) ?: return@forEach
            result.add(installed)
        }
        return result
    }

    /** Whether a voice with the given [voiceId] is installed. */
    fun isVoiceInstalled(voiceId: String): Boolean = getVoicePath(voiceId) != null

    /** The installed directory for [voiceId], or null if not installed. */
    fun getVoicePath(voiceId: String): File? {
        val dir = File(voicesDir, voiceId)
        if (!dir.isDirectory) return null
        return if (scanVoiceDir(dir) != null) dir else null
    }

    /** The set of installed voice ids. */
    fun getInstalledVoiceIds(): Set<String> = getInstalledVoices().map { it.voiceId }.toSet()

    // ------------------------------------------------------------------
    // Download / extract
    // ------------------------------------------------------------------

    /**
     * Download and extract a voice bundle. Runs on [Dispatchers.IO].
     *
     * @param entry catalog entry to download
     * @param onProgress callback receiving a value in 0..1 during download
     * @return [Result] containing the extracted voice directory on success
     */
    suspend fun downloadVoice(
        entry: NeuralVoiceEntry,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetDir = File(voicesDir, entry.id)
            if (targetDir.exists()) {
                logcat(LogPriority.INFO) { "Voice ${entry.id} already installed at ${targetDir.absolutePath}" }
                return@withContext Result.success(targetDir)
            }

            // 1. Download the tar.bz2 to the cache staging area.
            val archiveFile = downloadArchive(entry, onProgress)

            // 2. Verify checksum if provided.
            entry.sha256?.let { expected ->
                val actual = sha256(archiveFile)
                if (!actual.equals(expected, ignoreCase = true)) {
                    archiveFile.delete()
                    return@withContext Result.failure(
                        IOException("SHA-256 mismatch for ${entry.id}: expected=$expected actual=$actual"),
                    )
                }
                logcat(LogPriority.INFO) { "Checksum verified for ${entry.id}" }
            }

            // 3. Extract into the voice directory.
            targetDir.mkdirs()
            extractTarBz2(archiveFile, targetDir)

            // 4. Validate the extraction produced a usable voice.
            val installed = scanVoiceDir(targetDir)
            if (installed == null) {
                targetDir.deleteRecursively()
                archiveFile.delete()
                return@withContext Result.failure(
                    IOException("Extraction of ${entry.id} did not yield a valid voice (missing .onnx or tokens.txt)"),
                )
            }

            // 5. Clean up the cached archive.
            archiveFile.delete()

            // 6. Update preferences to point at the newly installed voice.
            preferences.neuralModelPath().set(targetDir.absolutePath)
            preferences.neuralModelType().set(resolveModelType(entry.family))
            preferences.neuralVoicesDownloaded().set(getInstalledVoices().size)

            logcat(LogPriority.INFO) { "Voice ${entry.id} installed at ${targetDir.absolutePath}" }
            Result.success(targetDir)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to download voice ${entry.id}" }
            Result.failure(e)
        }
    }

    /** Download [entry]'s bundle to the cache staging area, reporting progress. */
    private fun downloadArchive(
        entry: NeuralVoiceEntry,
        onProgress: (Float) -> Unit,
    ): File {
        val partFile = File(downloadsDir, "${entry.id}.tar.bz2.part")
        partFile.delete()
        val request = Request.Builder().url(entry.bundleUrl).get().build()

        logcat(LogPriority.INFO) { "Downloading voice ${entry.id} from ${entry.bundleUrl}" }

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} downloading ${entry.bundleUrl}")
            }
            val body = response.body ?: throw IOException("Empty response body for ${entry.bundleUrl}")
            val totalBytes = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(partFile).use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var bytesRead = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        bytesRead += read
                        if (totalBytes > 0) {
                            onProgress((bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }

        // Rename .part -> .tar.bz2 (kept as .part to allow resumable semantics later).
        val finalFile = File(downloadsDir, "${entry.id}.tar.bz2")
        if (!partFile.renameTo(finalFile)) {
            // Fallback: copy if rename fails (e.g. cross-device).
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        onProgress(1f)
        return finalFile
    }

    /**
     * Extract a tar.bz2 archive into [targetDir], stripping the leading
     * top-level directory that sherpa-onnx bundles include.
     */
    private fun extractTarBz2(archive: File, targetDir: File) {
        FileInputStream(archive).use { fis ->
            BZip2CompressorInputStream(fis).use { bzis ->
                TarArchiveInputStream(bzis).use { tis ->
                    var entry = tis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (entry.isDirectory) {
                            entry = tis.nextEntry
                            continue
                        }
                        // Strip the leading top-level directory component.
                        val relative = stripLeadingDir(name)
                        val outFile = File(targetDir, relative)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            tis.copyTo(fos)
                        }
                        entry = tis.nextEntry
                    }
                }
            }
        }
    }

    /** Strip the first path segment from a tar entry name. */
    private fun stripLeadingDir(name: String): String {
        val normalized = name.replace('\\', '/')
        val firstSlash = normalized.indexOf('/')
        return if (firstSlash >= 0) {
            normalized.substring(firstSlash + 1)
        } else {
            normalized
        }.trimStart('/')
    }

    // ------------------------------------------------------------------
    // Uninstall
    // ------------------------------------------------------------------

    /**
     * Delete an installed voice. Returns true if the voice directory was
     * removed (or did not exist).
     */
    fun uninstallVoice(voiceId: String): Boolean {
        val dir = File(voicesDir, voiceId)
        if (!dir.exists()) return true
        val deleted = dir.deleteRecursively()
        if (deleted) {
            // Clear the model path preference if it pointed at this voice.
            if (preferences.neuralModelPath().get() == dir.absolutePath) {
                preferences.neuralModelPath().set("")
            }
            preferences.neuralVoicesDownloaded().set(getInstalledVoices().size)
            logcat(LogPriority.INFO) { "Uninstalled voice $voiceId" }
        } else {
            logcat(LogPriority.WARN) { "Failed to delete voice directory for $voiceId" }
        }
        return deleted
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Scan a voice directory and return an [InstalledNeuralVoice] if it
     * contains a valid model + tokens pair, otherwise null.
     */
    private fun scanVoiceDir(dir: File): InstalledNeuralVoice? {
        val files = dir.listFiles() ?: return null
        val modelFile = files.firstOrNull { it.isFile && it.name.endsWith(".onnx") } ?: return null
        val tokensFile = File(dir, "tokens.txt")
        if (!tokensFile.isFile) return null

        val voiceId = dir.name
        val entry = NeuralTtsVoiceCatalog.findById(voiceId)
        val family = entry?.family ?: inferFamily(voiceId)
        val displayName = entry?.displayName ?: voiceId.replace("_", " ")
        val sampleRate = entry?.sampleRateHz ?: 22050

        return InstalledNeuralVoice(
            voiceId = voiceId,
            path = dir,
            modelFile = modelFile,
            tokensFile = tokensFile,
            family = family,
            displayName = displayName,
            sampleRateHz = sampleRate,
        )
    }

    /** Infer a family string from a voice id when the entry is not in the catalog. */
    private fun inferFamily(voiceId: String): String = when {
        voiceId.contains("kokoro") -> "kokoro"
        voiceId.contains("matcha") -> "matcha"
        voiceId.contains("kitten") -> "kitten"
        voiceId.contains("zipvoice") -> "zipvoice"
        voiceId.contains("piper") -> "piper"
        else -> "piper"
    }

    /**
     * Resolve the sherpa-onnx model type string stored in preferences from a
     * voice family. Maps "kitten" to "piper" (kitten is a piper-derived family)
     * since the engine treats them identically.
     */
    fun resolveModelType(family: String): String = when (family) {
        "piper", "kokoro", "matcha", "zipvoice" -> family
        "kitten" -> "piper"
        else -> "piper"
    }

    /** Compute the SHA-256 hex digest of a file. */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = fis.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val BUFFER_BYTES = 64 * 1024 // 64 KiB
    }
}
