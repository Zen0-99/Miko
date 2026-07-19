package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.i18n.MR
import androidx.compose.ui.platform.LocalContext
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Edit dialog matching Miko's pattern:
 * - Cover art shown at top (tappable to change).
 * - Text fields show only the field name as label (floats on focus).
 * - Current value shown as placeholder (doesn't float).
 * - Long-press a field to fill it with the current value.
 * - Status is a dropdown selection.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NovelEditDialog(
    novel: Novel,
    onDismiss: () -> Unit,
    onSave: (title: String, author: String, description: String, status: Long, tags: List<String>) -> Unit,
) {
    // Fields start empty — the current value is shown as placeholder.
    // Long-press fills the field with the current value.
    var title: String by remember { mutableStateOf("") }
    var author: String by remember { mutableStateOf("") }
    var description: String by remember { mutableStateOf("") }
    var statusIndex: Int by remember { mutableStateOf(novel.status.toInt().coerceIn(0, 5)) }
    var tagsText: String by remember { mutableStateOf("") }

    val statusOptions = listOf(
        stringResource(MR.strings.unknown) to 0L,
        stringResource(MR.strings.ongoing) to SNovel.ONGOING.toLong(),
        stringResource(MR.strings.completed) to SNovel.COMPLETED.toLong(),
        stringResource(MR.strings.licensed) to SNovel.LICENSED.toLong(),
        stringResource(MR.strings.publishing_finished) to SNovel.PUBLISHING_FINISHED.toLong(),
        stringResource(MR.strings.on_hiatus) to SNovel.ON_HIATUS.toLong(),
    )

    var statusExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(MR.strings.action_edit)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Cover art — shown at top like Miko
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(novel.asNovelCover())
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }

                // Title — label floats, placeholder shows current value
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(MR.strings.title)) },
                    placeholder = { Text(text = novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { title = novel.title },
                        ),
                )
                // Author — label floats, placeholder shows current value
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(text = stringResource(MR.strings.author)) },
                    placeholder = { Text(text = novel.author ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { author = novel.author ?: "" },
                        ),
                )
                // Description — label floats, placeholder shows truncated current value
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(text = "Description") },
                    placeholder = {
                        Text(
                            text = novel.description?.replace("\n", " ")?.take(60) ?: "",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { description = novel.description ?: "" },
                        ),
                )
                // Status — dropdown selection
                ExposedDropdownMenuBox(
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it },
                ) {
                    TextField(
                        value = statusOptions[statusIndex].first,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(text = stringResource(MR.strings.status)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false },
                    ) {
                        statusOptions.forEachIndexed { index, (label, _) ->
                            DropdownMenuItem(
                                text = { Text(text = label) },
                                onClick = {
                                    statusIndex = index
                                    statusExpanded = false
                                },
                            )
                        }
                    }
                }
                // Tags — comma-separated, placeholder shows current tags
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(text = "Tags") },
                    placeholder = {
                        Text(
                            text = novel.genre?.joinToString(", ") ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                tagsText = novel.genre?.joinToString(", ") ?: ""
                            },
                        ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Empty fields mean "keep current value"
                val finalTitle = title.ifEmpty { novel.title }
                val finalAuthor = author.ifEmpty { novel.author ?: "" }
                val finalDescription = description.ifEmpty { novel.description ?: "" }
                val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                onSave(finalTitle, finalAuthor, finalDescription, statusOptions[statusIndex].second, tags)
            }) {
                Text(text = stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
