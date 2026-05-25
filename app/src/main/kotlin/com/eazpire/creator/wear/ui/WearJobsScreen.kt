package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import coil.compose.AsyncImage
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class WearJobRow(
    val jobId: String,
    val title: String,
    val jobKind: String,
    val device: String?,
    val statusLine: String,
    val progress: Int,
    val previewUrl: String?,
)

internal fun wearJobKindLabel(type: String, action: String): String {
    val t = type.ifBlank { action }.lowercase()
    return when {
        t.contains("upload") -> "Upload"
        t.contains("wear-generate") || t == "generate" -> "Generate"
        t.contains("save") -> "Save"
        t.contains("publish") -> "Publish"
        t.contains("hero") -> "Hero"
        t.contains("video") -> "Video"
        t.contains("merge") -> "Merge"
        else -> type.ifBlank { action }.ifBlank { "Job" }
    }
}

internal fun wearJobStatusLine(
    done: Boolean,
    saving: Boolean,
    message: String,
    progress: Int,
): String {
    return when {
        saving && !done -> "Saving…"
        progress in 0..100 && message.isNotBlank() -> "$message · $progress%"
        progress in 0..100 -> "$progress%"
        message.isNotBlank() -> message
        done -> "Done"
        else -> "Processing…"
    }
}

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

    suspend fun loadJobs() {
        if (ownerId.isBlank()) {
            jobs = emptyList()
            return
        }
        val rows = withContext(Dispatchers.IO) {
            val res = api.listJobs(ownerId, limit = 30)
            if (!res.optBoolean("ok", false)) return@withContext emptyList()
            val arr: JSONArray = res.optJSONArray("items") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val done = o.optBoolean("done", false)
                    if (activeOnly && done) continue
                    val message = o.optString("message", "").trim()
                    val progress = o.optInt("progress", -1).coerceIn(-1, 100)
                    val saving = o.optBoolean("saving", false)
                    val type = o.optString("type", o.optString("action", ""))
                    val prompt = o.optString("prompt", o.optString("final_prompt", "")).take(40)
                    val title = if (prompt.isNotBlank()) prompt else wearJobKindLabel(type, o.optString("action", ""))
                    val device = o.optString("client_device", "").trim()
                        .ifBlank { o.optString("source", "").trim() }
                        .takeIf { it.isNotBlank() }
                    val preview = o.optString("preview_url", "")
                        .ifBlank { o.optJSONObject("result")?.optString("preview_url").orEmpty() }
                        .ifBlank { o.optString("image_url", "") }
                    add(
                        WearJobRow(
                            jobId = o.optString("job_id", ""),
                            title = title,
                            jobKind = wearJobKindLabel(type, o.optString("action", "")),
                            device = device,
                            statusLine = wearJobStatusLine(done, saving, message, progress),
                            progress = if (progress >= 0) progress else 0,
                            previewUrl = preview.takeIf { it.isNotBlank() },
                        ),
                    )
                }
            }
        }
        jobs = rows
    }

    LaunchedEffect(ownerId, refreshKey, activeOnly) {
        loading = true
        try {
            loadJobs()
        } catch (_: Exception) {
            jobs = emptyList()
        }
        loading = false
        if (activeOnly) {
            while (isActive) {
                delay(4000)
                try {
                    loadJobs()
                } catch (_: Exception) { /* ignore */ }
            }
        }
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = WearRoundInsets.contentPadding,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
        if (loading && jobs.isEmpty()) {
            item { CircularProgressIndicator() }
        } else if (jobs.isEmpty()) {
            item {
                Text(
                    text = "No active jobs",
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(jobs.size) { i ->
                val job = jobs[i]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!job.previewUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = job.previewUrl,
                            contentDescription = job.title,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        text = buildString {
                            append(job.title)
                            append("\n")
                            append(job.jobKind)
                            if (!job.device.isNullOrBlank()) {
                                append(" · ")
                                append(job.device)
                            }
                            append("\n")
                            append(job.statusLine)
                        },
                        style = MaterialTheme.typography.caption2,
                        color = EazColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
