package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import coil.compose.AsyncImage
import com.eazpire.creator.core.api.CreatorPhoneUploadApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private sealed class UploadUiState {
    data object Loading : UploadUiState()
    data class Qr(val sessionId: String, val qrUrl: String) : UploadUiState()
    data class Done(val imageUrl: String) : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}

@Composable
fun WearUploadScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    useDemoData: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val uploadApi = remember { CreatorPhoneUploadApi() }
    var state by remember(useDemoData) {
        mutableStateOf<UploadUiState>(
            if (useDemoData) {
                UploadUiState.Error(
                    translationStore.t(
                        "wear.upload_demo",
                        "Log in on phone to use QR upload",
                    ),
                )
            } else {
                UploadUiState.Loading
            },
        )
    }

    LaunchedEffect(ownerId, refreshKey, useDemoData) {
        if (useDemoData) return@LaunchedEffect
        if (ownerId.isBlank()) {
            state = UploadUiState.Error("Not logged in")
            return@LaunchedEffect
        }
        state = UploadUiState.Loading
        try {
            val cfg = withContext(Dispatchers.IO) { uploadApi.getConfig() }
            if (!cfg.optBoolean("ok", false)) {
                val msg = cfg.optString("message", cfg.optString("error", "not_configured"))
                state = UploadUiState.Error(msg)
                return@LaunchedEffect
            }
            val session = withContext(Dispatchers.IO) { uploadApi.createSession(ownerId) }
            if (!session.optBoolean("ok", false) || session.optString("session_id").isBlank()) {
                state = UploadUiState.Error(session.optString("error", "session_failed"))
                return@LaunchedEffect
            }
            val sessionId = session.optString("session_id")
            val qrUrl = uploadApi.qrImageUrl(sessionId)
            state = UploadUiState.Qr(sessionId, qrUrl)

            while (true) {
                delay(2000)
                val poll = withContext(Dispatchers.IO) { uploadApi.pollSession(sessionId, ownerId) }
                val status = poll.optString("status", "")
                if (status == "completed") {
                    val url = poll.optString("image_url", "")
                    if (url.isNotBlank()) {
                        state = UploadUiState.Done(url)
                    }
                    break
                }
                if (status == "expired") {
                    state = UploadUiState.Error("Session expired")
                    break
                }
            }
        } catch (e: Exception) {
            state = UploadUiState.Error(e.message ?: "error")
        }
    }

    ScalingLazyColumn(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = translationStore.t("wear.upload", "Phone upload"),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        when (val s = state) {
            UploadUiState.Loading -> item { CircularProgressIndicator() }
            is UploadUiState.Qr -> {
                item {
                    Text(
                        text = translationStore.t("wear.upload_scan", "Scan with your phone"),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    AsyncImage(
                        model = s.qrUrl,
                        contentDescription = "QR",
                        modifier = Modifier.size(140.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            is UploadUiState.Done -> {
                item {
                    Text(
                        text = translationStore.t("wear.upload_done", "Upload complete"),
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    AsyncImage(
                        model = s.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            is UploadUiState.Error -> item {
                Text(
                    text = translationStore.t("wear.upload_error", "Upload not available") + "\n" + s.message,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
