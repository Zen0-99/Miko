package eu.kanade.tachiyomi.ui.reader.novel.dictionary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryBottomSheet(
    selectedText: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var entry by remember { mutableStateOf<DictionaryEntry?>(null) }

    LaunchedEffect(selectedText) {
        isLoading = true
        entry = withContext(Dispatchers.IO) {
            val manager = DictionaryManager.getInstance(context)
            manager.initialize()
            manager.lookup(selectedText)
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // No drag handle — sheet still drags but no visual handle bar.
        dragHandle = null,
    ) {
        // Use fillMaxHeight so the sheet takes most of the screen,
        // preventing content from being cut off.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Header row (replaces TopAppBar which caused layout issues).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                    Text(
                        text = entry?.word ?: selectedText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                IconButton(onClick = {
                    val query = "Define $selectedText"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                    onDismiss()
                }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search on Google")
                }
            }

            if (isLoading) {
                Text(
                    text = "Looking up...",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            val currentEntry = entry
            if (currentEntry == null) {
                Text(
                    text = "No definition found for '$selectedText'.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            if (!currentEntry.phonetic.isNullOrBlank()) {
                val ipa = arpabetToIpa(currentEntry.phonetic)
                Text(
                    text = "/$ipa/",
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val grouped = currentEntry.definitions.groupBy { it.pos }
            grouped.forEach { (pos, defs) ->
                Text(
                    text = posFullName(pos),
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                defs.forEachIndexed { index, def ->
                    Text(
                        text = "${index + 1}. ${def.meaning}",
                        modifier = Modifier.padding(vertical = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    def.examples.forEach { example ->
                        Text(
                            text = "\"$example\"",
                            modifier = Modifier.padding(start = 16.dp, top = 1.dp, bottom = 1.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun posFullName(pos: String): String {
    return when (pos) {
        "n" -> "Noun"
        "v" -> "Verb"
        "a" -> "Adjective"
        "r" -> "Adverb"
        "s" -> "Adjective"
        else -> pos.replaceFirstChar { it.uppercase() }
    }
}

private fun arpabetToIpa(arpabet: String): String {
    val mapping = mapOf(
        "AA" to "ɑ", "AA0" to "ɑ", "AA1" to "ɑ", "AA2" to "ɑ",
        "AE" to "æ", "AE0" to "æ", "AE1" to "æ", "AE2" to "æ",
        "AH" to "ʌ", "AH0" to "ə", "AH1" to "ʌ", "AH2" to "ʌ",
        "AO" to "ɔ", "AO0" to "ɔ", "AO1" to "ɔ", "AO2" to "ɔ",
        "AW" to "aʊ", "AW0" to "aʊ", "AW1" to "aʊ", "AW2" to "aʊ",
        "AY" to "aɪ", "AY0" to "aɪ", "AY1" to "aɪ", "AY2" to "aɪ",
        "B" to "b",
        "CH" to "tʃ",
        "D" to "d",
        "DH" to "ð",
        "EH" to "ɛ", "EH0" to "ɛ", "EH1" to "ɛ", "EH2" to "ɛ",
        "ER" to "ɜr", "ER0" to "ɚ", "ER1" to "ɜr", "ER2" to "ɜr",
        "EY" to "eɪ", "EY0" to "eɪ", "EY1" to "eɪ", "EY2" to "eɪ",
        "F" to "f",
        "G" to "g",
        "HH" to "h",
        "IH" to "ɪ", "IH0" to "ɪ", "IH1" to "ɪ", "IH2" to "ɪ",
        "IY" to "i", "IY0" to "i", "IY1" to "i", "IY2" to "i",
        "JH" to "dʒ",
        "K" to "k",
        "L" to "l",
        "M" to "m",
        "N" to "n",
        "NG" to "ŋ",
        "OW" to "oʊ", "OW0" to "oʊ", "OW1" to "oʊ", "OW2" to "oʊ",
        "OY" to "ɔɪ", "OY0" to "ɔɪ", "OY1" to "ɔɪ", "OY2" to "ɔɪ",
        "P" to "p",
        "R" to "r",
        "S" to "s",
        "SH" to "ʃ",
        "T" to "t",
        "TH" to "θ",
        "UH" to "ʊ", "UH0" to "ʊ", "UH1" to "ʊ", "UH2" to "ʊ",
        "UW" to "u", "UW0" to "u", "UW1" to "u", "UW2" to "u",
        "V" to "v",
        "W" to "w",
        "Y" to "j",
        "Z" to "z",
        "ZH" to "ʒ",
    )
    return arpabet.split(" ").joinToString("") { mapping[it] ?: it }
}
