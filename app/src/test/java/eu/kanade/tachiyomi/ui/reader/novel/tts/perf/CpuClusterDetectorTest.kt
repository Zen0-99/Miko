package eu.kanade.tachiyomi.ui.reader.novel.tts.perf

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests for [CpuClusterDetector] against a fake sysfs cpufreq tree built
 * under @TempDir — [readClusters] takes an injectable root precisely so
 * this policy-dir layout can be exercised on the JVM.
 *
 * `detectPerfCoreCount()` itself is not called here: its logcat line
 * needs Android's Log backend, which unit tests don't have. The cluster
 * math it wraps (readFromSysfs + the 1..8 coercion) is covered directly.
 */
class CpuClusterDetectorTest {

    @TempDir
    lateinit var sysfs: File

    private fun policy(name: String, maxFreqKhz: Long, relatedCpus: String) {
        File(sysfs, name).apply {
            mkdirs()
            File(this, "cpuinfo_max_freq").writeText("$maxFreqKhz\n")
            File(this, "related_cpus").writeText(relatedCpus)
        }
    }

    @Test
    fun `two clusters count only the non min freq tier`() {
        policy("policy0", 2_400_000, "0 1") // perf cluster
        policy("policy4", 1_800_000, "2 3 4 5 6 7") // efficiency cluster

        // listFiles order is filesystem-dependent — compare as a set.
        CpuClusterDetector.readClusters(sysfs).toSet() shouldBe setOf(
            CpuClusterDetector.Cluster(2_400_000, 2),
            CpuClusterDetector.Cluster(1_800_000, 6),
        )
        CpuClusterDetector.readFromSysfs(sysfs) shouldBe 2
    }

    @Test
    fun `three clusters count both tiers above the minimum`() {
        policy("policy0", 3_000_000, "0") // prime
        policy("policy1", 2_500_000, "1 2 3") // perf
        policy("policy4", 1_800_000, "4 5 6 7") // efficiency

        CpuClusterDetector.readFromSysfs(sysfs) shouldBe 4
    }

    @Test
    fun `single cluster leaves one core for the scheduler`() {
        policy("policy0", 1_800_000, "0 1 2 3 4 5")

        CpuClusterDetector.readFromSysfs(sysfs) shouldBe 5
    }

    @Test
    fun `empty sysfs falls back to half of available processors`() {
        CpuClusterDetector.readClusters(sysfs).shouldBeEmpty()
        CpuClusterDetector.readFromSysfs(sysfs) shouldBe
            (Runtime.getRuntime().availableProcessors() + 1) / 2
    }

    @Test
    fun `missing sysfs root falls back to the estimate`() {
        val absent = File(sysfs, "does-not-exist")
        CpuClusterDetector.readClusters(absent).shouldBeEmpty()
        CpuClusterDetector.readFromSysfs(absent) shouldBe
            (Runtime.getRuntime().availableProcessors() + 1) / 2
    }

    @Test
    fun `policy missing attributes is skipped`() {
        File(sysfs, "policy0").mkdirs() // no attribute files at all
        policy("policy4", 2_000_000, "0 1 2 3")

        CpuClusterDetector.readClusters(sysfs) shouldBe
            listOf(CpuClusterDetector.Cluster(2_000_000, 4))
    }

    @Test
    fun `non policy directories are ignored`() {
        File(sysfs, "cpufreq").mkdirs()
        File(sysfs, "policyX").mkdirs() // doesn't parse, missing files anyway
        policy("policy0", 2_000_000, "0")

        CpuClusterDetector.readClusters(sysfs) shouldBe
            listOf(CpuClusterDetector.Cluster(2_000_000, 1))
    }
}
