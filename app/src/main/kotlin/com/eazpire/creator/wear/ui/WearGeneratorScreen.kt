package com.eazpire.creator.wear.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.api.CreatorPhoneUploadApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class UploadOverlay {
    data object Hidden : UploadOverlay()
    data object Loading : UploadOverlay()
    data class Qr(
        val sessionId: String,
        val primaryQrUrl: String,
        val fallbackQrUrl: String,
    ) : UploadOverlay()
    data class Error(val message: String) : UploadOverlay()
}

@Composable
fun WearGeneratorScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    onGenerationStarted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val uploadApi = remember { CreatorPhoneUploadApi() }
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var uploadImageUrl by remember { mutableStateOf<String?>(null) }
    var uploadOverlay by remember { mutableStateOf<UploadOverlay>(UploadOverlay.Hidden) }
    var qrModelUrl by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showGenStarted by remember { mutableStateOf(false) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrBlank()) prompt = text
        }
    }

    fun launchVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                translationStore.t("wear.speak_prompt", "Speak your prompt"),
            )
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            status = translationStore.t("wear.voice_unavailable", "Voice input unavailable")
        }
    }

    fun closeUploadOverlay() {
        pollJob?.cancel()
        pollJob = null
        uploadOverlay = UploadOverlay.Hidden
        qrModelUrl = null
    }

    fun startUploadQr() {
        if (ownerId.isBlank()) return
        pollJob?.cancel()
        scope.launch {
            uploadOverlay = UploadOverlay.Loading
            status = null
            try {
                val cfg = withContext(Dispatchers.IO) { uploadApi.getConfig() }
                if (!cfg.optBoolean("ok", false)) {
                    uploadOverlay = UploadOverlay.Error(
                        cfg.optString("message", cfg.optString("error", "not_configured")),
                    )
                    return@launch
                }
                val session = withContext(Dispatchers.IO) { uploadApi.createSession(ownerId) }
                if (!session.optBoolean("ok", false)) {
                    uploadOverlay = UploadOverlay.Error(
                        session.optString("error", session.optString("message", "session_failed")),
                    )
                    return@launch
                }
                val sid = session.optString("session_id", "").trim()
                val scanUrl = session.optString("scan_url", "").trim()
                if (sid.isBlank() || scanUrl.isBlank()) {
                    uploadOverlay = UploadOverlay.Error("session_failed")
                    return@launch
                }
                val primary = uploadApi.qrImageUrl(sid)
                val fallback = uploadApi.qrFallbackUrl(scanUrl)
                qrModelUrl = primary
                uploadOverlay = UploadOverlay.Qr(sid, primary, fallback)

                pollJob = scope.launch {
                    while (isActive) {
                        delay(2000)
                        val poll = withContext(Dispatchers.IO) {
                            uploadApi.pollSession(sid, ownerId)
                        }
                        if (!poll.optBoolean("ok", true)) {
                            val err = poll.optString("error", "")
                            if (err.isNotBlank()) {
                                uploadOverlay = UploadOverlay.Error(err)
                                break
                            }
                        }
                        val st = poll.optString("status", "")
                        if (st == "completed") {
                            val url = poll.optString("image_url", "").trim()
                            if (url.isNotBlank()) {
                                uploadImageUrl = url
                                closeUploadOverlay()
                                status = translationStore.t("wear.upload_ready", "Image ready")
                                break
                            }
                        }
                        if (st == "expired") {
                            uploadOverlay = UploadOverlay.Error(
                                translationStore.t("wear.upload_expired", "Upload expired"),
                            )
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                uploadOverlay = UploadOverlay.Error(e.message ?: "error")
            }
        }
    }

    fun generate() {
        if (ownerId.isBlank()) return
        generating = true
        status = null
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.wearGenerate(
                        ownerId = ownerId,
                        prompt = prompt.takeIf { it.isNotBlank() },
                        imageUrl = uploadImageUrl,
                    )
                }
                val accepted = res.optBoolean("ok", false) ||
                    res.optString("status") == "accepted" ||
                    res.has("job_id")
                if (accepted) {
                    showGenStarted = true
                    status = translationStore.t("wear.gen_started", "Generation started")
                    prompt = ""
                    delay(1400)
                    showGenStarted = false
                    onGenerationStarted()
                } else {
                    status = formatWearApiError(
                        translationStore,
                        res.optString("error", null),
                        res.optString("message", null),
                    )
                }
            } catch (e: Exception) {
                status = e.message ?: formatWearApiError(translationStore, null, null)
            } finally {
                generating = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pollJob?.cancel()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val overlay = uploadOverlay) {
            UploadOverlay.Hidden -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WearRoundIconButton(
                            onClick = { startUploadQr() },
                            contentDescription = translationStore.t("wear.upload", "Phone upload"),
                            selected = uploadImageUrl != null,
                        ) {
                            Text("📱", fontSize = 22.sp)
                        }
                        WearRoundIconButton(
                            onClick = { launchVoice() },
                            contentDescription = translationStore.t("wear.audio", "Audio"),
                            selected = prompt.isNotBlank(),
                        ) {
                            Text("🎤", fontSize = 22.sp)
                        }
                    }

                    if (prompt.isNotBlank()) {
                        Text(
                            text = prompt.take(60),
                            style = MaterialTheme.typography.caption2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            maxLines = 2,
                        )
                    }
                    uploadImageUrl?.let {
                        Text(
                            text = translationStore.t("wear.upload_ready", "Image ready"),
                            style = MaterialTheme.typography.caption2,
                            color = EazColors.Orange,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    WearPulsingGenerateButton(
                        onClick = { generate() },
                        enabled = !generating,
                        label = translationStore.t("wear.generate", "Generate"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                    )

                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    status?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.caption2,
                            color = EazColors.Orange,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            UploadOverlay.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is UploadOverlay.Qr -> {
                Column(
                    modifier = Modifier
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
                    val model = qrModelUrl ?: overlay.primaryQrUrl
                    AsyncImage(
                        model = model,
                        contentDescription = "Upload QR",
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                            .size(120.dp),
                        contentScale = ContentScale.Fit,
                        onError = {
                            if (qrModelUrl != overlay.fallbackQrUrl) {
                                qrModelUrl = overlay.fallbackQrUrl
                            }
                        },
                    )
                    Button(onClick = { closeUploadOverlay() }) {
                        Text(translationStore.t("wear.back", "Back"))
                    }
                }
            }
            is UploadOverlay.Error -> {
                Column(
                    modifier = Modifier
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
                        onClick = { closeUploadOverlay() },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(translationStore.t("wear.back", "Back"))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showGenStarted,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = translationStore.t("wear.gen_started", "Generation started"),
                    style = MaterialTheme.typography.title3,
                    color = EazColors.Orange,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
