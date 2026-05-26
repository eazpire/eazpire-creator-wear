package com.eazpire.creator.wear.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors
import kotlin.math.abs

private const val PAGE_COUNT = 5
private const val PAGE_JOBS = 4
private const val TAB_SWIPE_THRESHOLD_PX = 36f

@Composable
fun WearMainShell(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var jobsRefreshNonce by remember { mutableIntStateOf(0) }

    val pageLabels = remember(translationStore) {
        listOf(
            translationStore.t("wear.dashboard", "Dashboard"),
            translationStore.t("wear.generator", "Generator"),
            translationStore.t("wear.designs", "Designs"),
            translationStore.t("wear.products", "Products"),
            translationStore.t("creator.notifications.active_jobs", "Active Jobs"),
        )
    }
    val currentLabel = pageLabels.getOrElse(currentPage) { "" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .wearRoundSafePadding()
            .pointerInput(currentPage) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag < -TAB_SWIPE_THRESHOLD_PX && currentPage < PAGE_COUNT - 1 ->
                                currentPage++
                            totalDrag > TAB_SWIPE_THRESHOLD_PX && currentPage > 0 ->
                                currentPage--
                        }
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f },
                )
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.caption1,
                color = EazColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp),
            )
            WearPageDots(
                pageCount = PAGE_COUNT,
                currentPage = currentPage,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Only mount the visible tab — avoids Designs polling + Products enrichment running together.
            when (currentPage) {
                0 -> WearDashboardScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> WearGeneratorScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    onGenerationStarted = {
                        jobsRefreshNonce++
                        currentPage = PAGE_JOBS
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> WearDesignsScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    modifier = Modifier.fillMaxSize(),
                )
                3 -> WearProductsScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> WearJobsScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey + jobsRefreshNonce,
                    activeOnly = true,
                    showTitle = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
