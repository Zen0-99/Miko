package eu.kanade.tachiyomi.ui.reader.novel.tts.render

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption

/**
 * Custom top-level MP4 atom carrying the [ChapterAudioManifest] JSON,
 * appended after `MediaMuxer.stop()` (muxer output is moov-last, so the atom
 * lands at EOF) — D-06.
 *
 * Wire format (spec-sanctioned `uuid` box, RESEARCH §2):
 *
 *   [4B BE uint32 size — includes the 8-byte header]
 *   [4B fourcc "uuid"]
 *   [16B MIKO_MANIFEST_UUID — extension identifier]
 *   [size-24B payload — UTF-8 manifest JSON]
 *
 * Every MP4 parser treats unknown/`uuid` top-level boxes as skippable, so
 * players ignore it; Miko reads it back via a forward top-level atom walk
 * ([Mp4AtomReader]). Never tail-scan: the atom header is at its start and
 * nothing marks it from the end.
 */

/**
 * 16-byte extension UUID: ASCII "MikoTTS" + format-version byte at [7],
 * reserved zeros, payload-version byte at [15].
 */
internal val MIKO_MANIFEST_UUID = byteArrayOf(
    0x4D, 0x69, 0x6B, 0x6F, 0x54, 0x54, 0x53, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
)

/** Upper bound on a manifest payload — bounds-checks the read-back walk. */
const val MAX_MANIFEST_BYTES = 4L * 1024 * 1024

private const val FOURCC_UUID = 0x75756964 // "uuid"
private const val ATOM_HEADER = 8L
private const val EXTENDED_HEADER = 16L // size==1 → 8-byte largesize follows
private const val UUID_FIELD_SIZE = 16L

object Mp4AtomAppender {

    /**
     * Append a `uuid` atom carrying [payload] at the end of [file].
     * JVM-side variant used by tests and the cacheDir-fallback encode path.
     */
    fun append(file: File, payload: ByteArray) {
        val atom = buildAtom(payload)
        FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.APPEND)
            .use { channel ->
                while (atom.hasRemaining()) channel.write(atom)
            }
    }

    /**
     * Append a `uuid` atom carrying [payload] to a SAF document.
     *
     * Primary path: `openOutputStream(uri, "wa")` (append mode — supported by
     * ExternalStorageProvider and SD-card providers). Fallback for providers
     * without "wa": open a "rw" [ParcelFileDescriptor] and position a
     * [FileChannel] at end-of-file (RESEARCH §3, risk 8).
     */
    fun append(context: Context, docUri: Uri, payload: ByteArray) {
        val atom = buildAtom(payload)

        val appendStream = runCatching {
            context.contentResolver.openOutputStream(docUri, "wa")
        }.getOrNull()
        if (appendStream != null) {
            appendStream.use { out ->
                out.write(atom.array(), atom.arrayOffset(), atom.remaining())
            }
            return
        }

        val pfd = context.contentResolver.openFileDescriptor(docUri, "rw")
            ?: throw IOException("Cannot open $docUri for manifest append")
        // NB: the channel/streams over pfd.fileDescriptor are intentionally
        // NOT closed — pfd.use owns the fd; double-close throws EBADF.
        pfd.use { descriptor ->
            val channel = FileInputStream(descriptor.fileDescriptor).channel
            channel.position(channel.size())
            while (atom.hasRemaining()) channel.write(atom)
        }
    }

    private fun buildAtom(payload: ByteArray): ByteBuffer {
        val atom = ByteBuffer.allocate((ATOM_HEADER + UUID_FIELD_SIZE + payload.size).toInt())
        atom.putInt((ATOM_HEADER + UUID_FIELD_SIZE + payload.size).toInt())
        atom.putInt(FOURCC_UUID)
        atom.put(MIKO_MANIFEST_UUID)
        atom.put(payload)
        atom.flip()
        return atom
    }
}

object Mp4AtomReader {

    /**
     * Walk top-level MP4 atoms forward from position 0; on a `uuid` atom
     * whose 16-byte UUID equals [MIKO_MANIFEST_UUID], return its payload as
     * a UTF-8 string.
     *
     * Corruption contract (RESEARCH §2): missing atom, truncated or
     * malformed structure, wrong UUID, declared payload larger than
     * [MAX_MANIFEST_BYTES], or any IO error → `null` (never throws). The
     * file still plays as plain audio.
     */
    fun read(channel: SeekableByteChannel): String? {
        return try {
            walk(channel)
        } catch (t: Throwable) {
            null
        }
    }

    private fun walk(channel: SeekableByteChannel): String? {
        val fileSize = channel.size()
        var pos = 0L
        val header = ByteBuffer.allocate(8)

        while (pos + ATOM_HEADER <= fileSize) {
            header.clear()
            channel.position(pos)
            readFully(channel, header)
            header.flip()

            val sizeField = header.int
            val fourcc = header.int
            var headerSize = ATOM_HEADER
            var atomSize = sizeField.toLong() and 0xFFFF_FFFFL

            when (sizeField) {
                1 -> {
                    // 64-bit largesize follows the 8-byte header.
                    if (pos + EXTENDED_HEADER > fileSize) return null
                    val large = ByteBuffer.allocate(8)
                    readFully(channel, large)
                    large.flip()
                    atomSize = large.long
                    headerSize = EXTENDED_HEADER
                }
                0 -> atomSize = fileSize - pos // atom runs to EOF
            }

            if (atomSize < headerSize || pos + atomSize > fileSize) return null

            if (fourcc == FOURCC_UUID) {
                if (atomSize < headerSize + UUID_FIELD_SIZE) return null
                val uuid = ByteArray(UUID_FIELD_SIZE.toInt())
                channel.position(pos + headerSize)
                readFully(channel, ByteBuffer.wrap(uuid))
                if (!uuid.contentEquals(MIKO_MANIFEST_UUID)) {
                    pos += atomSize
                    continue
                }
                val payloadSize = atomSize - headerSize - UUID_FIELD_SIZE
                if (payloadSize < 0 || payloadSize > MAX_MANIFEST_BYTES) return null
                val payload = ByteArray(payloadSize.toInt())
                readFully(channel, ByteBuffer.wrap(payload))
                return String(payload, Charsets.UTF_8)
            }

            pos += atomSize
        }
        return null
    }

    /** Fill [buffer] completely; EOF before that is a truncation. */
    private fun readFully(channel: SeekableByteChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw IOException("unexpected EOF")
        }
    }
}
