package eu.kanade.presentation.library.filter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BrowseGallery
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Group By options matching the old repo's LibraryGroup constants.
 */
enum class GroupByOption(val id: Int, val labelRes: dev.icerock.moko.resources.StringResource, val icon: ImageVector) {
    CATEGORIES(0, MR.strings.collections, Icons.Outlined.Label),
    TAG(1, MR.strings.tag, Icons.Outlined.LocalOffer),
    SOURCE(2, MR.strings.sources, Icons.Outlined.BrowseGallery),
    STATUS(3, MR.strings.status, Icons.Outlined.Book),
    AUTHOR(4, MR.strings.author, Icons.Outlined.Person),
    TRACKING_STATUS(5, MR.strings.tracking_status, Icons.Outlined.Sync),
    LANGUAGE(6, MR.strings.language, Icons.Outlined.Language),
    UNGROUPED(7, MR.strings.ungrouped, Icons.Outlined.ViewAgenda),
}

/**
 * Modal bottom sheet for selecting group-by mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupBySheet(
    currentGroup: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = stringResource(MR.strings.group_library_by),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            GroupByOption.entries.forEach { option ->
                GroupByOptionRow(
                    option = option,
                    isSelected = option.id == currentGroup,
                    onClick = {
                        onSelect(option.id)
                        onDismiss()
                    },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupByOptionRow(
    option: GroupByOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = option.icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(option.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
