package eu.kanade.presentation.more.settings.widget

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

@Composable
internal fun AppThemePreferenceWidget(
    value: AppTheme,
    amoled: Boolean,
    isDarkTheme: Boolean,
    onItemClick: (AppTheme) -> Unit,
    recreateOnSelect: Boolean = true,
) {
    BasePreferenceWidget(
        subcomponent = {
            AppThemesList(
                currentTheme = value,
                amoled = amoled,
                isDarkTheme = isDarkTheme,
                onItemClick = onItemClick,
                recreateOnSelect = recreateOnSelect,
            )
        },
    )
}

@Composable
private fun AppThemesList(
    currentTheme: AppTheme,
    amoled: Boolean,
    isDarkTheme: Boolean,
    onItemClick: (AppTheme) -> Unit,
    recreateOnSelect: Boolean = true,
) {
    val context = LocalContext.current
    val appThemes = remember {
        AppTheme.entries
            .filterNot { it.titleRes == null || (it == AppTheme.MONET && !DeviceUtil.isDynamicColorAvailable) }
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = appThemes,
            key = { it.name },
        ) { appTheme ->
            Column(
                modifier = Modifier
                    .width(98.dp)
                    .padding(top = 8.dp),
            ) {
                TachiyomiTheme(
                    appTheme = appTheme,
                    amoled = amoled,
                    isDark = isDarkTheme,
                    animate = false,
                ) {
                    AppThemePreviewItem(
                        selected = currentTheme == appTheme,
                        onClick = {
                            onItemClick(appTheme)
                            if (recreateOnSelect) {
                                (context as? Activity)?.let { ActivityCompat.recreate(it) }
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(appTheme.titleRes!!),
                    modifier = Modifier
                        .fillMaxWidth()
                        .secondaryItemAlpha(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private const val SECONDARY_ITEM_ALPHA = 0.78f

@Composable
fun AppThemePreviewItem(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val selectedColor = if (selected) colorScheme.primary else Color.Transparent

    val outerCorner = 26.dp
    val innerCorner = outerCorner - 6.dp

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .height(140.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(outerCorner),
        border = BorderStroke(width = 4.dp, color = selectedColor),
    ) {
        OutlinedCard(
            modifier = Modifier
                .height(140.dp)
                .fillMaxWidth()
                .padding(6.dp),
            shape = RoundedCornerShape(innerCorner),
            colors = CardDefaults.outlinedCardColors(containerColor = colorScheme.background),
            border = BorderStroke(width = 1.dp, color = colorScheme.surfaceVariant),
        ) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .weight(0.7f)
                        .padding(start = 4.dp, end = 8.dp)
                        .background(
                            color = colorScheme.onSurface,
                            shape = RoundedCornerShape(6.dp),
                        ),
                )
                Box(
                    modifier = Modifier.weight(0.3f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = stringResource(MR.strings.selected),
                            tint = colorScheme.primary,
                        )
                    }
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .fillMaxWidth()
                        .background(
                            color = colorScheme.onSurface.copy(alpha = SECONDARY_ITEM_ALPHA),
                            shape = RoundedCornerShape(8.dp),
                        ),
                )
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .weight(0.8f)
                            .padding(end = 4.dp)
                            .background(
                                color = colorScheme.onSurface,
                                shape = RoundedCornerShape(6.dp),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .height(12.dp)
                            .weight(0.3f)
                            .background(
                                color = colorScheme.secondary,
                                shape = RoundedCornerShape(6.dp),
                            ),
                    )
                }
                Row(
                    modifier = Modifier
                        .height(12.dp)
                        .fillMaxWidth()
                        .padding(end = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.5f)
                            .padding(end = 4.dp)
                            .background(
                                color = colorScheme.onSurface,
                                shape = RoundedCornerShape(6.dp),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.6f)
                            .background(
                                color = colorScheme.onSurface,
                                shape = RoundedCornerShape(6.dp),
                            ),
                    )
                }
            }

            // Bottom navigation bar
            Surface(
                color = colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier
                        .height(24.dp)
                        .fillMaxWidth()
                        .padding(vertical = 2.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.2f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .background(
                                    color = colorScheme.onSurface.copy(alpha = SECONDARY_ITEM_ALPHA),
                                    shape = CircleShape,
                                ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.2f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .background(
                                    color = colorScheme.secondary,
                                    shape = CircleShape,
                                ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.2f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .background(
                                    color = colorScheme.onSurface.copy(alpha = SECONDARY_ITEM_ALPHA),
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppThemesListPreview() {
    var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
    Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
    TachiyomiTheme(appTheme = appTheme) {
        Surface {
            AppThemesList(
                currentTheme = appTheme,
                amoled = false,
                isDarkTheme = false,
                onItemClick = { appTheme = it },
            )
        }
    }
}
