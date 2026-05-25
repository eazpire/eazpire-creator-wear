package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WearDashboardScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    useDemoData: Boolean = false,
    showTitle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    var loading by remember { mutableStateOf(true) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(ownerId, refreshKey, useDemoData) {
        if (useDemoData) {
            loading = false
            lines = WearDemo.dashboardLines
            return@LaunchedEffect
        }
        if (ownerId.isBlank()) {
            loading = false
            lines = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val out = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                val pub = api.getPublishStats(ownerId)
                if (pub.optBoolean("ok", false)) {
                    val prods = pub.optJSONObject("products")
                    if (prods != null) {
                        out += "${translationStore.t("wear.products_online", "Products online")}: ${prods.optInt("online", 0)}"
                        out += "${translationStore.t("wear.products_offline", "Products offline")}: ${prods.optInt("offline", 0)}"
                    }
                }
                val sales = api.getCreatorSales(ownerId)
                if (sales.optBoolean("ok", false)) {
                    out += "${translationStore.t("wear.sales", "Sales")}: ${sales.optInt("totalOrders", 0)}"
                }
                val src = api.getDesignSourceCounts(ownerId)
                if (src.optBoolean("ok", false)) {
                    out += "${translationStore.t("wear.designs_gen", "Designs generated")}: ${src.optInt("generated", 0)}"
                    out += "${translationStore.t("wear.designs_up", "Designs uploaded")}: ${src.optInt("uploaded", 0)}"
                }
                val payout = api.getCreatorPayoutOverview(ownerId, 90)
                if (payout.optBoolean("ok", false)) {
                    val amt = payout.optDouble("availableAmount", 0.0)
                    val cur = payout.optString("currency", "EUR")
                    out += "${translationStore.t("wear.payout", "Available payout")}: %.2f %s".format(amt, cur)
                }
            }
            lines = out
        } catch (_: Exception) {
            lines = listOf("—")
        }
        loading = false
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = modifier.padding(horizontal = 8.dp),
        state = listState,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        if (showTitle) {
            item {
                Text(
                    text = translationStore.t("wear.dashboard", "Dashboard"),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (loading) {
            item { CircularProgressIndicator() }
        } else {
            items(lines.size) { i ->
                Text(
                    text = lines[i],
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
