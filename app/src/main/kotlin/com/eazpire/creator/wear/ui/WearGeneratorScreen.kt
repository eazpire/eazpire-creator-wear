package com.eazpire.creator.wear.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WearGeneratorScreen(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    refreshKey: Int,
    modifier: Modifier = Modifier,
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var uploadImageUrl by remember { mutableStateOf<String?>(null) }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

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

    fun startUploadQr() {
        if (ownerId.isBlank()) return
        scope.launch {
            status = null
            try {
                val session = withContext(Dispatchers.IO) { api.createPhoneUploadSession(ownerId) }
                if (!session.optBoolean("ok", false)) {
                    status = session.optString("error", "upload_failed")
                    return@launch
                }
                val sid = session.optString("session_id", "").trim()
                if (sid.isBlank()) {
                    status = "no_session"
                    return@launch
                }
                qrUrl = api.phoneUploadQrUrl(sid)
                showQr = true
                repeat(60) {
                    delay(2000)
                    val poll = withContext(Dispatchers.IO) { api.pollPhoneUploadSession(sid, ownerId) }
                    val url = poll.optString("image_url", "").trim()
                    if (url.isNotBlank()) {
                        uploadImageUrl = url
                        showQr = false
                        status = translationStore.t("wear.upload_ready", "Image ready")
                        return@launch
                    }
                    if (poll.optString("status") == "expired") {
                        showQr = false
                        status = translationStore.t("wear.upload_expired", "Upload expired")
                        return@launch
                    }
                }
                showQr = false
            } catch (e: Exception) {
                status = e.message
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
                if (res.optBoolean("ok", false) || res.optString("status") == "accepted") {
                    status = translationStore.t("wear.gen_started", "Generation started")
                    prompt = ""
                } else {
                    status = res.optString("error", res.optString("message", "failed"))
                }
            } catch (e: Exception) {
                status = e.message
            } finally {
                generating = false
            }
        }
    }

    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    ScalingLazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = WearRoundInsets.contentPadding,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showQr && !qrUrl.isNullOrBlank()) {
            item {
                AsyncImage(
                    model = qrUrl,
                    contentDescription = "Upload QR",
                    modifier = Modifier.size(100.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            item {
                Text(
                    text = translationStore.t("wear.scan_upload", "Scan to upload"),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            item {
                Button(
                    onClick = { startUploadQr() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(translationStore.t("wear.upload", "Upload"))
                }
            }
            item {
                Button(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, translationStore.t("wear.speak_prompt", "Speak your prompt"))
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            status = translationStore.t("wear.voice_unavailable", "Voice input unavailable")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(translationStore.t("wear.audio", "Audio"))
                }
            }
            if (prompt.isNotBlank()) {
                item {
                    Text(
                        text = prompt.take(80),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Button(
                    onClick = { generate() },
                    enabled = !generating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(translationStore.t("wear.generate", "Generate"))
                }
            }
            if (generating) {
                item { CircularProgressIndicator() }
            }
            status?.let { msg ->
                item {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.caption2,
                        color = EazColors.Orange,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
