package eu.kanade.tachiyomi.ui.reader.novel.tts.pocket

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * JVM coverage for the manifest-driven state slot lifecycle. The ORT-
 * touching entry points (bindAsTensor / bindAsPinnedOutput /
 * updateStatesFromResult) need a real OrtEnvironment and are exercised
 * on-device; everything else — allocation sizing, fill policies,
 * snapshot/restore, reset-to-init — is pure ByteBuffer math.
 */
class PocketStateManagerTest {

    private fun spec(
        index: Int,
        dtype: PocketBundle.StateDtype,
        fill: PocketBundle.StateFill,
        shape: LongArray,
    ) = PocketBundle.StateSpec(
        index = index,
        inputName = "state_$index",
        outputName = "out_state_$index",
        dtype = dtype,
        fill = fill,
        shape = shape,
    )

    private fun floatsOf(snap: PocketStateSlot.Snapshot): FloatArray {
        val out = FloatArray(snap.bytes.size / 4)
        ByteBuffer.wrap(snap.bytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(out)
        return out
    }

    private fun longsOf(snap: PocketStateSlot.Snapshot): LongArray {
        val out = LongArray(snap.bytes.size / 8)
        ByteBuffer.wrap(snap.bytes).order(ByteOrder.nativeOrder()).asLongBuffer().get(out)
        return out
    }

    @Test
    fun `initStates creates one direct-buffer slot per manifest entry in order`() {
        val manifest = listOf(
            spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.NAN, longArrayOf(1, 32, 8, 128)),
            spec(1, PocketBundle.StateDtype.INT64, PocketBundle.StateFill.ZEROS, longArrayOf(1)),
            spec(2, PocketBundle.StateDtype.BOOL, PocketBundle.StateFill.ONES, longArrayOf(2)),
        )
        val states = initStates(manifest)
        states.keys.toList() shouldBe listOf("state_0", "state_1", "state_2")
        states["state_0"]!!.shape shouldBe longArrayOf(1, 32, 8, 128)
        states["state_1"]!!.shape shouldBe longArrayOf(1)
        states["state_2"]!!.shape shouldBe longArrayOf(2)
    }

    @Test
    fun `nan fill produces NaN floats`() {
        val states = initStates(
            listOf(spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.NAN, longArrayOf(4))),
        )
        val floats = floatsOf(states["state_0"]!!.snapshot())
        floats.size shouldBe 4
        floats.all { it.isNaN() } shouldBe true
    }

    @Test
    fun `zeros and ones fills land per dtype`() {
        val states = initStates(
            listOf(
                spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ZEROS, longArrayOf(3)),
                spec(1, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ONES, longArrayOf(2)),
                spec(2, PocketBundle.StateDtype.INT64, PocketBundle.StateFill.ZEROS, longArrayOf(2)),
                spec(3, PocketBundle.StateDtype.INT64, PocketBundle.StateFill.ONES, longArrayOf(2)),
                spec(4, PocketBundle.StateDtype.BOOL, PocketBundle.StateFill.ONES, longArrayOf(4)),
            ),
        )
        floatsOf(states["state_0"]!!.snapshot()).toList() shouldBe listOf(0f, 0f, 0f)
        floatsOf(states["state_1"]!!.snapshot()).toList() shouldBe listOf(1f, 1f)
        longsOf(states["state_2"]!!.snapshot()).toList() shouldBe listOf(0L, 0L)
        longsOf(states["state_3"]!!.snapshot()).toList() shouldBe listOf(1L, 1L)
        states["state_4"]!!.snapshot().bytes.toList() shouldBe listOf(1.toByte(), 1.toByte(), 1.toByte(), 1.toByte())
    }

    @Test
    fun `empty fill and zero-dim shape produce a zero-byte slot`() {
        val states = initStates(
            listOf(
                spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.EMPTY, longArrayOf(0)),
                spec(1, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.EMPTY, longArrayOf(1, 128, 0)),
            ),
        )
        states["state_0"]!!.snapshot().bytes.size shouldBe 0
        states["state_0"]!!.shape shouldBe longArrayOf(0)
        states["state_1"]!!.snapshot().bytes.size shouldBe 0
        states["state_1"]!!.shape shouldBe longArrayOf(1, 128, 0)
    }

    @Test
    fun `reset restores cold-start shape and fill after mutation`() {
        val manifest = listOf(
            spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ZEROS, longArrayOf(2)),
        )
        val states = initStates(manifest)
        val slot = states["state_0"]!!

        // Simulate a runtime-grown / dirty slot via a forged snapshot.
        val dirty = PocketStateSlot.Snapshot(
            shape = longArrayOf(3),
            bytes = ByteBuffer.allocate(12).order(ByteOrder.nativeOrder())
                .putFloat(9f).putFloat(8f).putFloat(7f).array(),
        )
        slot.restore(dirty)
        slot.shape shouldBe longArrayOf(3)
        floatsOf(slot.snapshot()).toList() shouldBe listOf(9f, 8f, 7f)

        resetStatesToInit(states, manifest)
        slot.shape shouldBe longArrayOf(2)
        floatsOf(slot.snapshot()).toList() shouldBe listOf(0f, 0f)
    }

    @Test
    fun `snapshotStates and restoreStates round-trip the whole map`() {
        val manifest = listOf(
            spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.NAN, longArrayOf(2)),
            spec(1, PocketBundle.StateDtype.INT64, PocketBundle.StateFill.ZEROS, longArrayOf(1)),
        )
        val states = initStates(manifest)
        val snap = snapshotStates(states)

        // Dirty every slot.
        states["state_0"]!!.restore(
            PocketStateSlot.Snapshot(
                shape = longArrayOf(1),
                bytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(42f).array(),
            ),
        )
        restoreStates(states, snap)

        floatsOf(states["state_0"]!!.snapshot()).toList() shouldBe listOf(Float.NaN, Float.NaN)
        longsOf(states["state_1"]!!.snapshot()).toList() shouldBe listOf(0L)
        states["state_0"]!!.shape shouldBe longArrayOf(2)
    }

    @Test
    fun `resetStatesToInit errors on a slot missing from the map`() {
        val states = initStates(
            listOf(spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ZEROS, longArrayOf(1))),
        )
        shouldThrow<IllegalStateException> {
            resetStatesToInit(
                states,
                listOf(spec(1, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ZEROS, longArrayOf(1))),
            )
        }
    }

    @Test
    fun `enableStatePinning errors on a slot missing from the map`() {
        val states = initStates(
            listOf(spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ZEROS, longArrayOf(1))),
        )
        shouldThrow<IllegalStateException> {
            enableStatePinning(
                states,
                listOf(spec(9, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ZEROS, longArrayOf(1))),
            )
        }
    }

    @Test
    fun `enablePinning is idempotent and does not disturb the input buffer`() {
        val states = initStates(
            listOf(spec(0, PocketBundle.StateDtype.FLOAT32, PocketBundle.StateFill.ONES, longArrayOf(2))),
        )
        val slot = states["state_0"]!!
        slot.enablePinning(PocketBundle.StateFill.ONES)
        slot.enablePinning(PocketBundle.StateFill.ONES)
        floatsOf(slot.snapshot()).toList() shouldBe listOf(1f, 1f)
    }
}
