package com.eazpire.creator.wear.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun WearDesignsScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<WearCarouselItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

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

    LaunchedEffect(ownerId, refreshKey) {
        if (ownerId.isBlank()) {
            loading = false
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val list = withContext(Dispatchers.IO) {
                val res = api.listR2Designs(ownerId, limit = 30)
                if (!res.optBoolean("ok", false)) return@withContext emptyList()
                val arr: JSONArray = res.optJSONArray("items") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val preview = o.optJSONObject("result")?.optString("preview_url")
                            ?: o.optJSONObject("result")?.optString("image_url")
                            ?: o.optString("preview_url")
                        val title = o.optString("title", o.optString("name", "")).trim()
                        add(
                            WearCarouselItem(
                                imageUrl = preview?.takeIf { it.isNotBlank() },
                                label = title.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            }
            items = list
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    WearCarouselScreen(
        items = items,
        loading = loading,
        emptyText = translationStore.t("wear.no_designs", "No designs yet"),
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        onVoiceSearch = { launchVoice() },
        searchPlaceholder = translationStore.t("wear.search_short", "Search…"),
        showSearch = true,
        modifier = modifier,
    )
}
