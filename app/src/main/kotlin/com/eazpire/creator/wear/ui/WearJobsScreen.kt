package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
    val message: String,
    val previewUrl: String?,
    val done: Boolean,
    val saving: Boolean,
    val saved: Boolean,
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

internal fun wearJobDisplayTitle(o: org.json.JSONObject): String {
    fun clean(s: String): String {
        val t = s.trim()
        return if (t.isBlank() || t.equals("null", ignoreCase = true)) "" else t
    }
    val prompt = clean(o.optString("prompt", ""))
    val finalP = clean(o.optString("final_prompt", ""))
    val designPrompt = clean(o.optString("design_prompt", ""))
    val message = clean(o.optString("message", ""))
    val candidates = listOf(prompt, finalP, designPrompt, message).filter { it.isNotBlank() }
    if (candidates.isNotEmpty()) return candidates.first().take(40)
    val type = o.optString("type", o.optString("action", ""))
    return wearJobKindLabel(type, o.optString("action", ""))
}

internal fun wearJobDeviceLabel(o: org.json.JSONObject): String? {
    val device = o.optString("client_device", "").trim()
        .ifBlank { o.optString("source", "").trim() }
    return device.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

/** UI progress: save phase must not drop to 0 after generate (KV used to reset progress). */
internal fun wearJobUiProgress(progress: Int, saving: Boolean, done: Boolean): Int {
    val p = progress.coerceIn(0, 100)
    return when {
        saving -> maxOf(p, 90).coerceIn(0, 99)
        done && p < 100 -> maxOf(p, 90)
        else -> p
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
                    val saving = o.optBoolean("saving", false)
                    val saved = o.optBoolean("saved", false)
                    if (activeOnly && (done && !(saving && !saved))) continue
                    val message = o.optString("message", "").trim()
                    val progress = o.optInt("progress", -1).coerceIn(-1, 100)
                    val type = o.optString("type", o.optString("action", ""))
                    val title = wearJobDisplayTitle(o)
                    val device = wearJobDeviceLabel(o)
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
                            message = message,
                            previewUrl = preview.takeIf { it.isNotBlank() },
                            done = done,
                            saving = saving,
                            saved = saved,
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
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WearActiveJobCard(
                        title = "…",
                        progress = 0,
                    )
                }
            }
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
                val statusHint = when {
                    job.done && job.saving -> job.message.ifBlank { "Saving…" }
                    else -> job.message.ifBlank { "Processing…" }
                }
                val isError = job.done && !job.saving && !job.saved &&
                    (statusHint.contains("Fehler", ignoreCase = true) ||
                        statusHint.contains("error", ignoreCase = true))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    WearActiveJobCard(
                        title = job.title,
                        progress = wearJobUiProgress(job.progress, job.saving, job.done),
                        statusHint = statusHint,
                        isError = isError,
                        previewUrl = job.previewUrl,
                    )
                }
            }
        }
    }
}
