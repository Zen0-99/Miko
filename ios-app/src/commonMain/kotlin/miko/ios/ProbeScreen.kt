package miko.ios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val PROBE_URL = "https://api.github.com/repos/Zen0-99/Miko"

@Composable
fun ProbeScreen() {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf("not fetched") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Miko on iOS — Phase 23 probe", style = MaterialTheme.typography.headlineSmall)
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                result = "fetching $PROBE_URL …"
                scope.launch {
                    result = try {
                        probeNetwork(PROBE_URL)
                    } catch (e: Exception) {
                        "ERROR: ${e.message ?: e::class.simpleName}"
                    }
                    busy = false
                }
            },
        ) {
            Text("Fetch api.github.com")
        }
        Text(result, style = MaterialTheme.typography.bodySmall)
    }
}
