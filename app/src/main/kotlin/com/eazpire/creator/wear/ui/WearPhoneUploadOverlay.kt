package com.eazpire.creator.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.eazpire.creator.core.api.CreatorPhoneUploadApi
import com.eazpire.creator.core.i18n.WearTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class WearPhoneUploadState {
    data object Hidden : WearPhoneUploadState()
    data object Loading : WearPhoneUploadState()
    data class Qr(
        val sessionId: String,
        val primaryQrUrl: String,
        val fallbackQrUrl: String,
    ) : WearPhoneUploadState()
    data class Error(val message: String) : WearPhoneUploadState()
}

class WearPhoneUploadController(
    private val uploadApi: CreatorPhoneUploadApi,
    private val translationStore: WearTranslationStore,
) {
    var state by mutableStateOf<WearPhoneUploadState>(WearPhoneUploadState.Hidden)
        private set
    var qrModelUrl by mutableStateOf<String?>(null)
    private var pollJob: Job? = null

    fun setQrFallback(url: String) {
        qrModelUrl = url
    }

    fun close() {
        pollJob?.cancel()
        pollJob = null
        state = WearPhoneUploadState.Hidden
        qrModelUrl = null
    }

    fun start(
        ownerId: String,
        scope: kotlinx.coroutines.CoroutineScope,
        onCompleted: (imageUrl: String) -> Unit,
    ) {
        if (ownerId.isBlank()) return
        pollJob?.cancel()
        scope.launch {
            state = WearPhoneUploadState.Loading
            try {
                val cfg = withContext(Dispatchers.IO) { uploadApi.getConfig() }
                if (!cfg.optBoolean("ok", false)) {
                    state = WearPhoneUploadState.Error(
                        cfg.optString("message", cfg.optString("error", "not_configured")),
                    )
                    return@launch
                }
                val session = withContext(Dispatchers.IO) { uploadApi.createSession(ownerId) }
                if (!session.optBoolean("ok", false)) {
                    state = WearPhoneUploadState.Error(
                        session.optString("error", session.optString("message", "session_failed")),
                    )
                    return@launch
                }
                val sid = session.optString("session_id", "").trim()
                val scanUrl = session.optString("scan_url", "").trim()
                if (sid.isBlank() || scanUrl.isBlank()) {
                    state = WearPhoneUploadState.Error("session_failed")
                    return@launch
                }
                val primary = uploadApi.qrImageUrl(sid)
                val fallback = uploadApi.qrFallbackUrl(scanUrl)
                qrModelUrl = primary
                state = WearPhoneUploadState.Qr(sid, primary, fallback)

                pollJob = scope.launch {
                    while (isActive) {
                        delay(2000)
                        val poll = withContext(Dispatchers.IO) {
                            uploadApi.pollSession(sid, ownerId)
                        }
                        if (!poll.optBoolean("ok", true)) {
                            val err = poll.optString("error", "")
                            if (err.isNotBlank()) {
                                state = WearPhoneUploadState.Error(err)
                                break
                            }
                        }
                        val st = poll.optString("status", "")
                        if (st == "completed") {
                            val url = poll.optString("image_url", "").trim()
                            if (url.isNotBlank()) {
                                close()
                                onCompleted(url)
                                break
                            }
                        }
                        if (st == "expired") {
                            state = WearPhoneUploadState.Error(
                                translationStore.t("wear.upload_expired", "Upload expired"),
                            )
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                state = WearPhoneUploadState.Error(e.message ?: "error")
            }
        }
    }

    fun dispose() {
        pollJob?.cancel()
    }
}

@Composable
fun WearPhoneUploadOverlay(
    controller: WearPhoneUploadController,
    translationStore: WearTranslationStore,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    when (val overlay = controller.state) {
        WearPhoneUploadState.Hidden -> Unit
        WearPhoneUploadState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is WearPhoneUploadState.Qr -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = translationStore.t("wear.upload_scan", "Scan with your phone"),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
                val model = controller.qrModelUrl ?: overlay.primaryQrUrl
                AsyncImage(
                    model = model,
                    contentDescription = "Upload QR",
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .size(120.dp),
                    contentScale = ContentScale.Fit,
                    onError = {
                        if (controller.qrModelUrl != overlay.fallbackQrUrl) {
                            controller.setQrFallback(overlay.fallbackQrUrl)
                        }
                    },
                )
                Button(onClick = { controller.close() }) {
                    Text(translationStore.t("wear.back", "Back"))
                }
            }
        }
        is WearPhoneUploadState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = translationStore.t("wear.upload_error", "Upload not available"),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = overlay.message,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(
                    onClick = { controller.close() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(translationStore.t("wear.back", "Back"))
                }
            }
        }
    }
}
