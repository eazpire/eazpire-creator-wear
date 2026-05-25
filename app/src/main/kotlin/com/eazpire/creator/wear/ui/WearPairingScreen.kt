package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors

@Composable
fun WearPairingScreen(
    translationStore: WearTranslationStore,
    connectionStatus: String,
    onRetrySync: () -> Unit,
    onDemoPreview: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    ScalingLazyColumn(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        item {
            Text(
                text = translationStore.t("wear.pair_title", "Pair with phone"),
                style = MaterialTheme.typography.title2,
                color = EazColors.Orange,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = translationStore.t(
                    "wear.pair_body",
                    "Log in to the Eazpire app on your phone. Your session will sync to this watch.",
                ),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = connectionStatus,
                style = MaterialTheme.typography.caption1,
                color = EazColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                text = translationStore.t(
                    "wear.open_hint",
                    "Open the app: swipe up from the watch face or tap the app list on the emulator toolbar.",
                ),
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Chip(
                onClick = onRetrySync,
                label = {
                    Text(
                        translationStore.t("wear.retry_sync", "Retry sync"),
                        maxLines = 1,
                    )
                },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = EazColors.Orange,
                    contentColor = EazColors.TextPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onDemoPreview != null) {
            item {
                Chip(
                    onClick = onDemoPreview,
                    label = {
                        Text(
                            translationStore.t("wear.demo_preview", "Preview app (emulator)"),
                            maxLines = 2,
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = EazColors.CreatorSurface,
                        contentColor = EazColors.TextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
