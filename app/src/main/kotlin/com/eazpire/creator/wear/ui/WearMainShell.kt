package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors

private enum class WearMainTab { Dashboard, Jobs, Upload }

@Composable
fun WearMainShell(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    demoPreview: Boolean,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val labels = listOf(
        translationStore.t("wear.dashboard", "Dashboard"),
        translationStore.t("wear.jobs_short", "Jobs"),
        translationStore.t("wear.upload_short", "Upload"),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 12.dp, start = 10.dp, end = 10.dp, bottom = 8.dp),
    ) {
        if (demoPreview) {
            Text(
                text = translationStore.t("wear.demo_short", "Demo data"),
                style = MaterialTheme.typography.caption2,
                color = EazColors.Orange,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = selectedTab == index
                Chip(
                    onClick = { selectedTab = index },
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            style = MaterialTheme.typography.caption2,
                        )
                    },
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (selected) EazColors.Orange else EazColors.CreatorSurface,
                        contentColor = EazColors.TextPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            when (selectedTab) {
                0 -> WearDashboardScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    useDemoData = demoPreview,
                    showTitle = false,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> WearJobsScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    useDemoData = demoPreview,
                    showTitle = false,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> WearUploadScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    useDemoData = demoPreview,
                    showTitle = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
