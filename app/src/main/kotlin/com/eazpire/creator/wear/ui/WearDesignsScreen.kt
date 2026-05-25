package com.eazpire.creator.wear.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.api.CreatorPhoneUploadApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun WearDesignsScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val uploadApi = remember { CreatorPhoneUploadApi() }
    val uploadController = remember(uploadApi, translationStore) {
        WearPhoneUploadController(uploadApi, translationStore)
    }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<WearCarouselItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var activityFilter by remember { mutableStateOf("active") }
    var loadNonce by remember { mutableIntStateOf(0) }
    var carouselIndex by remember { mutableIntStateOf(0) }
    var highlightJobId by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrBlank()) searchQuery = text
        }
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) { /* ignore */ }
    }

    fun parseListItems(res: JSONObject): List<WearCarouselItem> {
        if (!res.optBoolean("ok", false)) return emptyList()
        val arr: JSONArray = res.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val preview = o.optString("preview_url", "")
                    .ifBlank { o.optJSONObject("result")?.optString("preview_url").orEmpty() }
                    .ifBlank { o.optJSONObject("result")?.optString("image_url").orEmpty() }
                val title = o.optString("title", o.optString("name", "")).trim()
                if (preview.isBlank() && title.isBlank()) continue
                add(
                    WearCarouselItem(
                        imageUrl = preview.takeIf { it.isNotBlank() },
                        label = title.takeIf { it.isNotBlank() },
                        jobId = o.optString("job_id", "").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    fun parseGeneratedItems(res: JSONObject): List<WearCarouselItem> {
        if (!res.optBoolean("ok", false)) return emptyList()
        val arr: JSONArray = res.optJSONArray("items") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val preview = o.optString("preview_url", "")
                    .ifBlank { o.optJSONObject("result")?.optString("preview_url").orEmpty() }
                    .ifBlank { o.optJSONObject("result")?.optString("image_url").orEmpty() }
                val title = o.optString("title", o.optString("name", "")).trim()
                    .ifBlank { o.optString("prompt", "").take(32) }
                if (preview.isBlank() && title.isBlank()) continue
                add(
                    WearCarouselItem(
                        imageUrl = preview.takeIf { it.isNotBlank() },
                        label = title.takeIf { it.isNotBlank() },
                        jobId = o.optString("job_id", "").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    suspend fun loadDesignItems(): List<WearCarouselItem> {
        if (ownerId.isBlank()) return emptyList()
        val base = if (activityFilter == "inactive") {
            parseGeneratedItems(api.listInactiveDesigns(ownerId, limit = 40))
        } else {
            parseListItems(api.listSavedDesigns(ownerId, limit = 40))
        }
        val hid = highlightJobId?.trim().orEmpty()
        if (hid.isBlank() || base.any { it.jobId == hid }) return base
        val jobsRes = api.listJobs(ownerId, limit = 30)
        if (!jobsRes.optBoolean("ok", false)) return base
        val arr = jobsRes.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("job_id") != hid) continue
            val preview = o.optString("preview_url", "")
                .ifBlank { o.optJSONObject("result")?.optString("preview_url").orEmpty() }
                .ifBlank { o.optString("image_url", "") }
            if (preview.isBlank()) return base
            return listOf(
                WearCarouselItem(
                    imageUrl = preview,
                    label = translationStore.t("wear.upload_processing", "Uploading…"),
                    jobId = hid,
                ),
            ) + base
        }
        return base
    }

    LaunchedEffect(ownerId, refreshKey, activityFilter, loadNonce) {
        if (ownerId.isBlank()) {
            loading = false
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            items = withContext(Dispatchers.IO) { loadDesignItems() }
            carouselIndex = 0
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    LaunchedEffect(highlightJobId, activityFilter) {
        val hid = highlightJobId ?: return@LaunchedEffect
        if (activityFilter != "inactive") return@LaunchedEffect
        var attempts = 0
        while (isActive && attempts < 45) {
            delay(2000)
            attempts++
            try {
                val list = withContext(Dispatchers.IO) { loadDesignItems() }
                items = list
                val idx = list.indexOfFirst { it.jobId == hid }
                if (idx >= 0) {
                    carouselIndex = idx
                    if (!list[idx].imageUrl.isNullOrBlank()) {
                        highlightJobId = null
                        break
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }
        highlightJobId = null
    }

    fun startDesignUploadQr() {
        if (ownerId.isBlank()) return
        uploadController.start(ownerId, scope) { imageUrl ->
            uploading = true
            scope.launch {
                try {
                    val res = withContext(Dispatchers.IO) {
                        api.uploadDesignFromImageUrl(ownerId, imageUrl)
                    }
                    val jobId = res.optString("jobId", "")
                        .ifBlank { res.optString("job_id", "") }
                    if (res.optBoolean("ok", false) || jobId.isNotBlank()) {
                        activityFilter = "inactive"
                        highlightJobId = jobId.takeIf { it.isNotBlank() }
                        carouselIndex = 0
                        loadNonce++
                    } else {
                        formatWearApiError(
                            translationStore,
                            res.optString("error", null),
                            res.optString("message", null),
                        )
                    }
                } catch (e: Exception) {
                    e.message
                } finally {
                    uploading = false
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        WearCarouselScreen(
            items = items,
            loading = loading || uploading,
            emptyText = if (activityFilter == "inactive") {
                translationStore.t("wear.no_inactive_designs", "No inactive designs")
            } else {
                translationStore.t("wear.no_designs", "No designs yet")
            },
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onVoiceSearch = { launchVoice() },
            onUploadClick = { startDesignUploadQr() },
            searchPlaceholder = translationStore.t("wear.search_short", "Search…"),
            showSearch = true,
            activityFilter = activityFilter,
            onActivityFilterChange = { activityFilter = it },
            activeLabel = translationStore.t("wear.designs_active", "Active"),
            inactiveLabel = translationStore.t("wear.designs_inactive", "Inactive"),
            initialCarouselIndex = carouselIndex,
            modifier = Modifier.fillMaxSize(),
        )

        WearPhoneUploadOverlay(
            controller = uploadController,
            translationStore = translationStore,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
