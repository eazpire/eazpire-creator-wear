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
import org.json.JSONArray

data class WearJobRow(val title: String, val status: String)

@Composable
fun WearJobsScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    showTitle: Boolean = true,
    activeOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    var loading by remember { mutableStateOf(true) }
    var jobs by remember { mutableStateOf<List<WearJobRow>>(emptyList()) }

    LaunchedEffect(ownerId, refreshKey) {
        if (ownerId.isBlank()) {
            loading = false
            jobs = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val rows = withContext(Dispatchers.IO) {
                val res = api.listJobs(ownerId, limit = 10)
                if (!res.optBoolean("ok", false)) return@withContext emptyList()
                val arr: JSONArray = res.optJSONArray("items") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val type = o.optString("type", o.optString("job_type", "job"))
                        val status = o.optString("status", o.optString("state", "—"))
                        val done = o.optBoolean("done", false)
                        val progress = o.optInt("progress", -1)
                        if (activeOnly) {
                            val s = status.lowercase()
                            if (done || s == "done" || s == "completed" || s == "failed" || s == "error") continue
                        }
                        val prompt = o.optString("prompt", o.optString("summary", "")).take(40)
                        val title = if (prompt.isNotBlank()) prompt else type
                        val statusLine = if (progress in 0..100) "$status · $progress%" else status
                        add(WearJobRow(title, statusLine))
                    }
                }
            }
            jobs = rows
        } catch (_: Exception) {
            jobs = emptyList()
        }
        loading = false
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = WearRoundInsets.contentPadding,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        if (showTitle) {
            item {
                Text(
                    text = translationStore.t("creator.notifications.active_jobs", "Active Jobs"),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (loading) {
            item { CircularProgressIndicator() }
        } else if (jobs.isEmpty()) {
            item {
                Text(
                    text = "No active jobs",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(jobs.size) { i ->
                val job = jobs[i]
                Text(
                    text = "${job.title}\n${job.status}",
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
