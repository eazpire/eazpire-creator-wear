package com.eazpire.creator.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun WearDashboardScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    var loading by remember { mutableStateOf(true) }
    var levelLine by remember { mutableStateOf("—") }
    var xpLine by remember { mutableStateOf("") }
    var journeyLine by remember { mutableStateOf("") }
    var journeyTasks by remember { mutableStateOf<List<String>>(emptyList()) }
    var designsGen by remember { mutableStateOf("—") }
    var productsOn by remember { mutableStateOf("—") }
    var salesCount by remember { mutableStateOf("—") }
    var balance by remember { mutableStateOf("—") }
    var totalProfit by remember { mutableStateOf("—") }

    LaunchedEffect(ownerId, refreshKey) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            withContext(Dispatchers.IO) {
                val level = api.getLevel(ownerId)
                if (level.optBoolean("ok", false)) {
                    val n = level.optInt("level", 1)
                    val name = level.optString("level_name", "Creator")
                    levelLine = "Lv $n · $name"
                    val cur = level.optInt("xp_current", 0)
                    val next = level.optInt("xp_next", 50)
                    xpLine = "$cur / $next XP"
                }
                val journey = api.getOnboardingProgress(ownerId)
                if (journey.optBoolean("ok", false)) {
                    val stats = journey.optJSONObject("stats")
                    val pct = stats?.optInt("progress_percent", 0) ?: 0
                    val done = stats?.optInt("completed_count", 0) ?: 0
                    val total = stats?.optInt("total_todos", 0) ?: 0
                    journeyLine = "$pct% · $done/$total"
                    val todos = journey.optJSONArray("todos") ?: JSONArray()
                    val open = mutableListOf<String>()
                    for (i in 0 until todos.length()) {
                        if (open.size >= 2) break
                        val o = todos.optJSONObject(i) ?: continue
                        if (o.optBoolean("completed", false)) continue
                        val title = o.optJSONObject("presentation")?.optString("title_shopify_key", "")
                            ?.takeIf { it.isNotBlank() }
                            ?: o.optString("id", "Task")
                        open.add(title.substringAfterLast('.'))
                    }
                    journeyTasks = open
                }
                val src = api.getDesignSourceCounts(ownerId)
                if (src.optBoolean("ok", false)) {
                    designsGen = src.optInt("generated", 0).toString()
                }
                val pub = api.getPublishStats(ownerId)
                if (pub.optBoolean("ok", false)) {
                    productsOn = pub.optJSONObject("products")?.optInt("online", 0)?.toString() ?: "0"
                }
                val sales = api.getCreatorSales(ownerId)
                if (sales.optBoolean("ok", false)) {
                    salesCount = sales.optInt("totalOrders", 0).toString()
                    totalProfit = "%.2f".format(sales.optDouble("totalCreatorEarnings", 0.0))
                }
                val payout = api.getCreatorPayoutOverview(ownerId, 90)
                if (payout.optBoolean("ok", false)) {
                    val amt = payout.optDouble("availableAmount", 0.0)
                    val cur = payout.optString("currency", "EUR")
                    balance = "%.2f $cur".format(amt, cur)
                }
            }
        } catch (_: Exception) { /* keep placeholders */ }
        loading = false
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = WearRoundInsets.contentPadding,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (loading) {
            item { CircularProgressIndicator() }
        } else {
            item { WearDashCard(title = translationStore.t("wear.level", "Level"), lines = listOf(levelLine, xpLine)) }
            item {
                WearDashCard(
                    title = translationStore.t("wear.journey", "Creator Journey"),
                    lines = listOf(journeyLine) + journeyTasks,
                )
            }
            item {
                WearDashCard(
                    title = translationStore.t("wear.stats", "Stats"),
                    lines = listOf(
                        "${translationStore.t("wear.designs_short", "Designs")}: $designsGen",
                        "${translationStore.t("wear.products_short", "Products")}: $productsOn",
                        "${translationStore.t("wear.sales", "Sales")}: $salesCount",
                    ),
                )
            }
            item {
                WearDashCard(
                    title = translationStore.t("wear.sales_stats", "Sales stats"),
                    lines = listOf(
                        "${translationStore.t("wear.balance", "Balance")}: $balance",
                        "${translationStore.t("wear.sales", "Sales")}: $salesCount",
                        "${translationStore.t("wear.total_profit", "Total profit")}: $totalProfit",
                    ),
                )
            }
        }
    }
}

@Composable
private fun WearDashCard(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EazColors.CreatorSurface.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption1.copy(
                fontWeight = FontWeight.SemiBold,
                color = EazColors.Orange,
                fontSize = 11.sp,
            ),
        )
        lines.filter { it.isNotBlank() }.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
