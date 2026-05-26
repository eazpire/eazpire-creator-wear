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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val economy = rememberWearEconomySnapshot(api, ownerId, refreshKey)
    val uploadApi = remember { CreatorPhoneUploadApi() }
    val pendingStore = remember(context) { WearPendingUploadStore(context) }
    val uploadController = remember(uploadApi, translationStore) {
        WearPhoneUploadController(uploadApi, translationStore)
    }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<WearCarouselItem>>(emptyList()) }
    var searchDraft by remember { mutableStateOf("") }
    var appliedSearch by remember { mutableStateOf("") }
    var activityFilter by remember { mutableStateOf("active") }
    var loadNonce by remember { mutableIntStateOf(0) }
    var carouselIndex by remember { mutableIntStateOf(0) }
    var pendingUpload by remember { mutableStateOf(pendingStore.load()) }
    var uploading by remember { mutableStateOf(false) }
    var selectedDesign by remember { mutableStateOf<WearCarouselItem?>(null) }
    var publishedCountByDesignId by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrBlank()) {
                searchDraft = text
                appliedSearch = text
            }
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
                val designId = o.optString("id", "")
                    .ifBlank { o.optString("design_id", "") }
                    .trim()
                    .takeIf { it.isNotBlank() }
                add(
                    WearCarouselItem(
                        imageUrl = preview.takeIf { it.isNotBlank() },
                        label = title.takeIf { it.isNotBlank() },
                        jobId = o.optString("job_id", "").takeIf { it.isNotBlank() },
                        designId = designId,
                        libraryStatus = "active",
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
                val designId = o.optString("design_id", "")
                    .ifBlank { o.optString("id", "") }
                    .trim()
                    .takeIf { it.isNotBlank() }
                add(
                    WearCarouselItem(
                        imageUrl = preview.takeIf { it.isNotBlank() },
                        label = title.takeIf { it.isNotBlank() },
                        jobId = o.optString("job_id", "").takeIf { it.isNotBlank() },
                        designId = designId,
                        libraryStatus = "inactive",
                    ),
                )
            }
        }
    }

    suspend fun loadPublishedSummaryMap(): Map<String, Int> {
        if (ownerId.isBlank()) return emptyMap()
        val res = api.getPublishedSummary(ownerId)
        if (!res.optBoolean("ok", false)) return emptyMap()
        val arr = res.optJSONArray("designs") ?: JSONArray()
        val map = mutableMapOf<String, Int>()
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val id = row.optString("design_id", "").trim()
            if (id.isBlank()) continue
            map[id] = row.optInt("products_count", 0)
        }
        return map
    }

    fun pendingCarouselItem(pending: WearPendingUploadStore.Pending): WearCarouselItem {
        return WearCarouselItem(
            imageUrl = pending.previewUrl,
            label = pending.label ?: translationStore.t("wear.upload_processing", "Processing…"),
            jobId = pending.jobId,
            libraryStatus = "inactive",
            isProcessing = true,
        )
    }

    suspend fun mergePendingIntoList(base: List<WearCarouselItem>): List<WearCarouselItem> {
        val pending = pendingUpload ?: return base
        val jobId = pending.jobId
        val existing = base.indexOfFirst { it.jobId == jobId }
        if (existing >= 0) {
            val row = base[existing]
            if (!row.designId.isNullOrBlank()) {
                pendingStore.clear()
                pendingUpload = null
                return base
            }
            return base.map {
                if (it.jobId == jobId) {
                    it.copy(
                        imageUrl = it.imageUrl ?: pending.previewUrl,
                        isProcessing = true,
                    )
                } else {
                    it
                }
            }
        }
        return listOf(pendingCarouselItem(pending)) + base
    }

    suspend fun loadDesignItems(): List<WearCarouselItem> {
        if (ownerId.isBlank()) return emptyList()
        val base = if (activityFilter == "inactive") {
            parseGeneratedItems(api.listInactiveDesigns(ownerId, limit = 40))
        } else {
            parseListItems(api.listSavedDesigns(ownerId, limit = 40))
        }
        if (activityFilter == "inactive") {
            return mergePendingIntoList(base)
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
            if (activityFilter == "active") {
                publishedCountByDesignId = withContext(Dispatchers.IO) { loadPublishedSummaryMap() }
            }
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    LaunchedEffect(pendingUpload?.jobId, ownerId) {
        val pending = pendingUpload ?: return@LaunchedEffect
        if (ownerId.isBlank()) return@LaunchedEffect
        var attempts = 0
        while (isActive && attempts < 120) {
            delay(2000)
            attempts++
            try {
                var preview = pending.previewUrl
                val jobsRes = withContext(Dispatchers.IO) { api.listJobs(ownerId, limit = 30) }
                if (jobsRes.optBoolean("ok", false)) {
                    val arr = jobsRes.optJSONArray("items") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("job_id") != pending.jobId) continue
                        val p = o.optString("preview_url", "")
                            .ifBlank { o.optJSONObject("result")?.optString("preview_url").orEmpty() }
                            .ifBlank { o.optString("image_url", "") }
                        if (p.isNotBlank()) preview = p
                        val done = o.optBoolean("done", false)
                        val saved = o.optBoolean("saved", false)
                        val saving = o.optBoolean("saving", false)
                        if (done && saved && !saving) {
                            pendingStore.clear()
                            pendingUpload = null
                            loadNonce++
                            return@LaunchedEffect
                        }
                        break
                    }
                }
                if (preview != pending.previewUrl) {
                    val next = pending.copy(previewUrl = preview)
                    pendingStore.save(next)
                    pendingUpload = next
                }
                val list = withContext(Dispatchers.IO) { loadDesignItems() }
                items = list
                val idx = list.indexOfFirst { it.jobId == pending.jobId }
                if (idx >= 0) carouselIndex = idx
                val row = list.getOrNull(idx)
                if (row != null && !row.designId.isNullOrBlank() && !row.isProcessing) {
                    pendingStore.clear()
                    pendingUpload = null
                    loadNonce++
                    return@LaunchedEffect
                }
            } catch (_: Exception) { /* ignore */ }
        }
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
                        val pending = WearPendingUploadStore.Pending(
                            jobId = jobId,
                            previewUrl = imageUrl,
                            label = translationStore.t("wear.upload_processing", "Processing…"),
                        )
                        pendingStore.save(pending)
                        pendingUpload = pending
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

    val showMainUi = uploadController.state is WearPhoneUploadState.Hidden

    Box(modifier = modifier.fillMaxSize()) {
        if (showMainUi) {
        WearCarouselScreen(
            items = items,
            loading = (loading || uploading) && items.none { it.isProcessing && !it.imageUrl.isNullOrBlank() },
            emptyText = if (activityFilter == "inactive") {
                translationStore.t("wear.no_inactive_designs", "No inactive designs")
            } else {
                translationStore.t("wear.no_designs", "No designs yet")
            },
            searchText = searchDraft,
            onSearchTextChange = { searchDraft = it },
            onSearchSubmit = { appliedSearch = searchDraft.trim() },
            filterQuery = appliedSearch,
            onVoiceSearch = { launchVoice() },
            onUploadClick = { startDesignUploadQr() },
            searchPlaceholder = translationStore.t("wear.search_short", "Search…"),
            showSearch = true,
            activityFilter = activityFilter,
            onActivityFilterChange = { activityFilter = it },
            activeLabel = translationStore.t("wear.designs_active", "Active"),
            inactiveLabel = translationStore.t("wear.designs_inactive", "Inactive"),
            initialCarouselIndex = carouselIndex,
            onItemClick = { item ->
                if (item.isProcessing) return@WearCarouselScreen
                if (!item.designId.isNullOrBlank() || !item.jobId.isNullOrBlank()) {
                    selectedDesign = item
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        }

        WearDesignLibraryActionsHost(
            item = selectedDesign,
            activityFilter = activityFilter,
            publishedCountByDesignId = publishedCountByDesignId,
            api = api,
            ownerId = ownerId,
            translationStore = translationStore,
            onDismiss = { selectedDesign = null },
            onCompleted = {
                selectedDesign = null
                loadNonce++
            },
            modifier = Modifier.fillMaxSize(),
        )

        WearPhoneUploadOverlay(
            controller = uploadController,
            translationStore = translationStore,
            economy = economy,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
