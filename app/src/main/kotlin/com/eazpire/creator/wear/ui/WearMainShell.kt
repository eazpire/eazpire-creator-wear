package com.eazpire.creator.wear.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors

private const val PAGE_COUNT = 5

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearMainShell(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val pageLabels = remember(translationStore) {
        listOf(
            translationStore.t("wear.dashboard", "Dashboard"),
            translationStore.t("wear.generator", "Generator"),
            translationStore.t("wear.designs", "Designs"),
            translationStore.t("wear.products", "Products"),
            translationStore.t("creator.notifications.active_jobs", "Active Jobs"),
        )
    }
    val currentLabel = pageLabels.getOrElse(pagerState.currentPage) { "" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .wearRoundSafePadding(),
    ) {
        Chip(
            onClick = { },
            enabled = false,
            label = {
                Text(
                    text = currentLabel,
                    maxLines = 1,
                    style = MaterialTheme.typography.caption1,
                )
            },
            colors = ChipDefaults.chipColors(
                backgroundColor = EazColors.Orange,
                contentColor = EazColors.TextPrimary,
                disabledBackgroundColor = EazColors.Orange,
                disabledContentColor = EazColors.TextPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        )

        WearPageDots(
            pageCount = PAGE_COUNT,
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 4.dp),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
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
                    4 -> WearJobsScreen(
                        tokenStore = tokenStore,
                        translationStore = translationStore,
                        refreshKey = refreshKey,
                        activeOnly = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
