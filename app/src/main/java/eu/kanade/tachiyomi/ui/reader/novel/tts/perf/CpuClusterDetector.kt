package eu.kanade.tachiyomi.ui.reader.novel.tts.perf

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.io.File

/**
 * Detects how many "performance" CPU cores are on the device.
 *
 * Modern phones use big.LITTLE-style heterogeneous CPUs:
 *   - 1 prime core (Cortex-X3/X4)
 *   - some perf cores (Cortex-A715/A720/A710)
 *   - some efficiency cores (Cortex-A510/A520)
 *
 * ONNX Runtime intra-op threading benefits from filling the prime + perf
 * cluster, but spilling onto efficiency cores often regresses matmul-heavy
 * workloads (efficiency cores are 2-4× slower at the same frequency and
 * thrash the shared cache). This detector counts cores in the *non-lowest*
 * frequency tier — the union of prime + perf, excluding efficiency.
 *
 * Reads `/sys/devices/system/cpu/cpufreq/policy*` on a sysfs that exposes
 * one policy directory per CPU cluster. Each policy lists:
 *   - `cpuinfo_max_freq` — top frequency for the cluster (kHz)
 *   - `related_cpus`     — whitespace-separated list of CPU indices
 *
 * Fallback when sysfs isn't readable (some manufacturers restrict it):
 * `(Runtime.availableProcessors() + 1) / 2`, capped at 8 by the caller.
 * Reasonable compromise for "most phones have roughly half big, half
 * little."
 *
 * The detection runs once at engine init and the result is cached for the
 * process lifetime — sysfs paths are stable across reads, and ARM
 * core-frequency tiers don't change at runtime.
 *
 * Ported from marmalade-tts-android `perf/CpuClusterDetector.kt` with one
 * change: [readClusters] (and [readFromSysfs]) take an injectable sysfs
 * root so JVM tests can feed a fake `policy*` tree via @TempDir.
 */
object CpuClusterDetector {

    private const val CPUFREQ_ROOT = "/sys/devices/system/cpu/cpufreq"

    /**
     * Returns the count of performance cores (prime + perf cluster) on
     * this device. Always returns at least 1; capped at 8 since ORT
     * gains diminish past that on consumer hardware.
     */
    fun detectPerfCoreCount(): Int {
        val detected = readFromSysfs(File(CPUFREQ_ROOT))
        val coerced = detected.coerceIn(1, 8)
        logcat(LogPriority.INFO) {
            "Perf cluster size: $coerced " +
                "(raw=$detected, total=${Runtime.getRuntime().availableProcessors()})"
        }
        return coerced
    }

    /** One CPU cluster as sysfs describes it: a top frequency and a core count. */
    data class Cluster(val maxFreqKhz: Long, val cpuCount: Int)

    /**
     * The device's CPU clusters, or an empty list when sysfs isn't readable.
     *
     * [root] is injectable for tests; production callers leave the default
     * [CPUFREQ_ROOT]. The sysfs layout knowledge (policy dirs, the two
     * attribute files) stays in this one place.
     */
    fun readClusters(root: File = File(CPUFREQ_ROOT)): List<Cluster> {
        val policies = root.listFiles { f -> f.isDirectory && f.name.startsWith("policy") }
            ?: return emptyList()
        return policies.mapNotNull { policy ->
            val maxFreq = readLong(File(policy, "cpuinfo_max_freq")) ?: return@mapNotNull null
            val relatedCpus = readText(File(policy, "related_cpus")) ?: return@mapNotNull null
            val cpus = relatedCpus.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (cpus.isEmpty()) return@mapNotNull null
            Cluster(maxFreq, cpus.size)
        }
    }

    /**
     * Perf-core count under [root]: cores in every cluster whose max
     * frequency is above the lowest tier; single-cluster devices get
     * `total - 1` (leaving a core for the OS scheduler); unreadable sysfs
     * falls back to the half-of-total estimate. Internal so JVM tests can
     * drive it against a fake sysfs without touching the logcat path.
     */
    internal fun readFromSysfs(root: File): Int {
        val clusters = readClusters(root)
        if (clusters.isEmpty()) return fallbackEstimate()

        val minFreq = clusters.minOf { it.maxFreqKhz }
        val perfCores = clusters.filter { it.maxFreqKhz > minFreq }.sumOf { it.cpuCount }
        if (perfCores > 0) return perfCores

        // Single-cluster device (rare on modern phones — old Snapdragon 4xx etc).
        // Use them all minus 1 to leave a core for the OS scheduler.
        val total = clusters.sumOf { it.cpuCount }
        return (total - 1).coerceAtLeast(1)
    }

    private fun fallbackEstimate(): Int {
        // sysfs unreadable. Estimate from /proc-style availableProcessors:
        // most modern phones have roughly half perf, half efficiency.
        return (Runtime.getRuntime().availableProcessors() + 1) / 2
    }

    private fun readLong(f: File): Long? =
        runCatching { f.readText().trim().toLong() }.getOrNull()

    private fun readText(f: File): String? =
        runCatching { f.readText() }.getOrNull()
}
