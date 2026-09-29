package mihon.buildlogic

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Post-link RELRO end-alignment fix for 16 KB page-size compatibility.
 *
 * Pre-LLD-18 linkers emit PT_GNU_RELRO whose end is only 4 KB aligned. On a
 * 16 KB kernel Android shows the "app compatibility" dialog and (in strict
 * mode) refuses the library. A correctly linked ELF just pads the RELRO tail
 * to the max page boundary — which is what this does after the fact:
 *
 *  - Safe case: RELRO spills into pure zero padding (no other PT_LOAD and no
 *    writable section occupies [relroEnd, boundary)). Extending the RELRO
 *    p_memsz — and the containing LOAD's p_memsz — to the next 16 KB boundary
 *    is semantically identical to what the fixed linker emits: the added pages
 *    are mapped zero-filled and get mprotected RO at load time.
 *  - Unsafe case (e.g. libffmpegkit.so): real .data/.bss lives in the pad
 *    range, so extension would RO-protect writable data → crash. There the
 *    RELRO phdr is turned into PT_NULL instead: the lib simply loses RELRO
 *    hardening, which is what non-RELRO libraries ship with anyway.
 */
object Relro16kFix {

    private const val EI_CLASS = 4
    private const val ELFCLASS32 = 1
    private const val ELFCLASS64 = 2

    private const val PT_NULL = 0
    private const val PT_LOAD = 1L
    private const val PT_GNU_RELRO = 0x6474e552L
    private const val PAGE = 0x4000L

    class PatchResult(val patched: Boolean, val nulled: Boolean, val lib: String)

    fun patchTree(root: File): List<PatchResult> {
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "so" }
            .mapNotNull { patch(it) }
            .toList()
    }

    private class Phdr(
        val filePos: Long,
        val type: Long,
        val offset: Long,
        val vaddr: Long,
        val memsz: Long,
    ) {
        val end: Long get() = vaddr + memsz
    }

    private fun patch(f: File): PatchResult? = try {
        patchFile(f)
    } catch (e: Exception) {
        null
    }

    private fun patchFile(f: File): PatchResult? {
        val raf = RandomAccessFile(f, "rw")
        try {
            val eh = ByteArray(64)
            raf.readFully(eh)
            val bb = ByteBuffer.wrap(eh).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.getInt(0) != 0x464c457f) return null
            val is64 = when (eh[EI_CLASS].toInt()) {
                ELFCLASS64 -> true
                ELFCLASS32 -> false
                else -> return null
            }
            val phoff = if (is64) bb.getLong(32) else bb.getInt(28).toLong() and 0xffff_ffffL
            // e_phentsize/e_phnum: ELF64 at 54/56, ELF32 at 42/44
            val phentsize = (if (is64) bb.getShort(54) else bb.getShort(42)).toInt() and 0xffff
            val phnum = (if (is64) bb.getShort(56) else bb.getShort(44)).toInt() and 0xffff
            val minEntsize = if (is64) 56 else 32
            if (phentsize < minEntsize || phoff <= 0 || phnum <= 0) return null

            val loads = ArrayList<Phdr>()
            var relro: Phdr? = null
            for (i in 0 until phnum) {
                val pos = phoff + i.toLong() * phentsize
                val buf = ByteArray(phentsize)
                raf.seek(pos)
                raf.readFully(buf)
                val pb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                val type = pb.getInt(0).toLong() and 0xffff_ffffL
                if (is64) {
                    val off = pb.getLong(8)
                    val va = pb.getLong(16)
                    val ms = pb.getLong(40)
                    when (type) {
                        PT_LOAD -> loads += Phdr(pos, type, off, va, ms)
                        PT_GNU_RELRO -> relro = Phdr(pos, type, off, va, ms)
                    }
                } else {
                    val off = pb.getInt(4).toLong() and 0xffff_ffffL
                    val va = pb.getInt(8).toLong() and 0xffff_ffffL
                    val ms = pb.getInt(20).toLong() and 0xffff_ffffL
                    when (type) {
                        PT_LOAD -> loads += Phdr(pos, type, off, va, ms)
                        PT_GNU_RELRO -> relro = Phdr(pos, type, off, va, ms)
                    }
                }
            }

            val r = relro ?: return null
            if (r.end % PAGE == 0L) return null // already aligned

            val boundary = (r.end + PAGE - 1) / PAGE * PAGE
            val containing = loads
                .filter { it.vaddr <= r.vaddr && r.vaddr < it.end }
                .maxByOrNull { it.vaddr }

            // Extension is only sound when [relro.end, boundary) is pure padding:
            //  - suffix: relro ends at its LOAD's end → extension adds zero pages
            //  - spill:  relro already runs past its LOAD's end into the load gap
            //    → extending both keeps the relationship, gap pages are unused
            // A mid-LOAD relro (r.end < containing.end) or another LOAD claiming
            // the pad range means real writable data sits there → null it instead.
            val unsafe = containing == null ||
                r.end < containing.end ||
                loads.any { l -> l !== containing && l.vaddr < boundary && l.end > r.end }

            val name = f.name
            return if (unsafe) {
                // Can't grow RELRO without swallowing real data — drop the segment.
                writeInt(raf, r.filePos, PT_NULL)
                PatchResult(patched = true, nulled = true, lib = name)
            } else {
                // Extend RELRO and its containing LOAD to the boundary. The added
                // range is unmapped address space that becomes zero-filled pages,
                // exactly what a fixed linker emits.
                val newMemsz = boundary - containing!!.vaddr
                if (newMemsz > containing.memsz) {
                    writeLongAt(raf, containing.filePos, is64, newMemsz)
                }
                writeLongAt(raf, r.filePos, is64, boundary - r.vaddr)
                PatchResult(patched = true, nulled = false, lib = name)
            }
        } finally {
            raf.close()
        }
    }

    private fun writeInt(raf: RandomAccessFile, pos: Long, v: Int) {
        raf.seek(pos)
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
        raf.write(b.array())
    }

    private fun writeLongAt(raf: RandomAccessFile, phdrPos: Long, is64: Boolean, newValue: Long) {
        // p_memsz offset within the phdr: ELF64 = 40, ELF32 = 20
        val off = if (is64) 40L else 20L
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(newValue)
        raf.seek(phdrPos + off)
        if (is64) {
            raf.write(b.array())
        } else {
            raf.write(b.array(), 0, 4)
        }
    }
}
