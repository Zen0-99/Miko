package miko.spike

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Mirrors a tachiyomi.domain-shaped model — Track A proves this exact
// shared-code pattern compiles to iOS targets.
data class SpikeEntry(
    val id: Long,
    val title: String,
    val url: String,
)

val sampleEntries = listOf(
    SpikeEntry(1, "Solo Leveling", "/manga/solo-leveling"),
    SpikeEntry(2, "Omniscient Reader", "/novel/omniscient-reader"),
    SpikeEntry(3, "Frieren", "/anime/frieren"),
)

@Composable
fun SpikeScreen() {
    LazyColumn(Modifier.fillMaxSize()) {
        items(sampleEntries, key = { it.id }) { entry ->
            BasicText(text = entry.title, modifier = Modifier.padding(16.dp))
        }
    }
}

// expect/actual seam proof — the split mechanism every platform port uses.
expect fun platformName(): String
