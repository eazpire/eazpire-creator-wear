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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.eazpire.creator.core.api.CreatorApi
import com.eazpire.creator.core.api.CreatorPhoneUploadApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class InputActionTarget { Image, Prompt }

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
    val uploadController = remember(uploadApi, translationStore) {
        WearPhoneUploadController(uploadApi, translationStore)
    }
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var uploadImageUrl by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var showGenStarted by remember { mutableStateOf(false) }
    var inputActionTarget by remember { mutableStateOf<InputActionTarget?>(null) }
    var showGenConfirm by remember { mutableStateOf(false) }
    var confirmModel by remember { mutableStateOf<WearGenerateConfirmModel?>(null) }
    var confirmLoading by remember { mutableStateOf(false) }

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

    fun startUploadQr() {
        uploadController.start(ownerId, scope) { url ->
            uploadImageUrl = url
        }
    }

    fun runGenerate() {
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
                    prompt = ""
                    uploadImageUrl = null
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

    fun openGenerateConfirm() {
        if (ownerId.isBlank()) return
        showGenConfirm = true
        confirmLoading = true
        confirmModel = null
        scope.launch {
            confirmModel = loadWearGenerateConfirmModel(api, ownerId, translationStore)
            confirmLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (uploadController.state is WearPhoneUploadState.Hidden) {
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
                    val imageUrl = uploadImageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        WearImagePreviewSlot(
                            imageUrl = imageUrl,
                            onClick = { inputActionTarget = InputActionTarget.Image },
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        WearRoundIconButton(
                            onClick = { startUploadQr() },
                            contentDescription = translationStore.t("wear.upload", "Phone upload"),
                        ) {
                            Text("📱", fontSize = 22.sp)
                        }
                    }

                    if (prompt.isNotBlank()) {
                        WearTextPreviewSlot(
                            text = prompt.take(48),
                            onClick = { inputActionTarget = InputActionTarget.Prompt },
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        WearRoundIconButton(
                            onClick = { launchVoice() },
                            contentDescription = translationStore.t("wear.audio", "Audio"),
                        ) {
                            Text("🎤", fontSize = 22.sp)
                        }
                    }
                }

                WearPulsingGenerateButton(
                    onClick = { openGenerateConfirm() },
                    enabled = !generating,
                    label = translationStore.t("wear.generate", "Generate"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                )

                if (generating) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
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

        WearPhoneUploadOverlay(
            controller = uploadController,
            translationStore = translationStore,
            modifier = Modifier.fillMaxSize(),
        )

        inputActionTarget?.let { target ->
            WearInputActionMenu(
                translationStore = translationStore,
                onDelete = {
                    when (target) {
                        InputActionTarget.Image -> uploadImageUrl = null
                        InputActionTarget.Prompt -> prompt = ""
                    }
                    inputActionTarget = null
                },
                onReupload = {
                    inputActionTarget = null
                    when (target) {
                        InputActionTarget.Image -> startUploadQr()
                        InputActionTarget.Prompt -> launchVoice()
                    }
                },
                onBack = { inputActionTarget = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showGenConfirm) {
            WearGenerateConfirmOverlay(
                model = confirmModel ?: WearGenerateConfirmModel(
                    useEaz = false,
                    isFree = false,
                    canProceed = false,
                ),
                loading = confirmLoading,
                translationStore = translationStore,
                onConfirm = {
                    showGenConfirm = false
                    if (confirmModel?.canProceed == true) runGenerate()
                },
                onCancel = { showGenConfirm = false },
                modifier = Modifier.fillMaxSize(),
            )
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
