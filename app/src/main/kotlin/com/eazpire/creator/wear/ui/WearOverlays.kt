package com.eazpire.creator.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil.compose.AsyncImage
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.EazColors

@Composable
fun WearInputPreviewSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(EazColors.Orange)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun WearImagePreviewSlot(
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WearInputPreviewSlot(onClick = onClick, modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Uploaded image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun WearTextPreviewSlot(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WearInputPreviewSlot(onClick = onClick, modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            color = EazColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
            fontSize = 10.sp,
        )
    }
}

@Composable
fun WearInputActionMenu(
    translationStore: WearTranslationStore,
    onDelete: () -> Unit,
    onReupload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EazColors.CreatorBg.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(translationStore.t("wear.action_delete", "Delete"))
            }
            Button(onClick = onReupload, modifier = Modifier.fillMaxWidth()) {
                Text(translationStore.t("wear.action_reupload", "Upload again"))
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(translationStore.t("wear.back", "Back"))
            }
        }
    }
}

data class WearGenerateConfirmModel(
    val useEaz: Boolean,
    val isFree: Boolean,
    val trialRemaining: Int? = null,
    val eazBalance: Double? = null,
    val eazCost: Double? = null,
    val canProceed: Boolean,
    val blockMessage: String? = null,
)

@Composable
fun WearGenerateConfirmOverlay(
    model: WearGenerateConfirmModel,
    loading: Boolean,
    translationStore: WearTranslationStore,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EazColors.CreatorBg.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (loading) {
                Text(
                    text = translationStore.t("wear.loading", "Loading…"),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
            } else if (!model.canProceed && !model.blockMessage.isNullOrBlank()) {
                Text(
                    text = model.blockMessage,
                    style = MaterialTheme.typography.caption2,
                    color = EazColors.Orange,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(translationStore.t("wear.cancel", "Cancel"))
                }
            } else {
                val body = when {
                    model.isFree -> translationStore.t(
                        "wear.gen_confirm_free",
                        "Generate for free?",
                    )
                    model.useEaz -> {
                        val cost = model.eazCost ?: 0.0
                        val bal = model.eazBalance ?: 0.0
                        translationStore.t(
                            "wear.gen_confirm_eaz",
                            "Balance: {{balance}} EAZ\nCost: {{cost}} EAZ\nGenerate?",
                        )
                            .replace("{{balance}}", formatWearEaz(bal))
                            .replace("{{cost}}", formatWearEaz(cost))
                    }
                    else -> {
                        val rem = model.trialRemaining ?: 0
                        translationStore.t(
                            "wear.gen_confirm_trial",
                            "Free generations left: {{count}}\nGenerate?",
                        ).replace("{{count}}", rem.toString())
                    }
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = translationStore.t("wear.gen_confirm_prompt", "Do you want to generate?"),
                    style = MaterialTheme.typography.caption2,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(translationStore.t("wear.confirm", "Confirm"))
                    }
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(translationStore.t("wear.cancel", "Cancel"))
                    }
                }
            }
        }
    }
}

internal fun formatWearEaz(value: Double): String {
    val v = kotlin.math.round(value * 100.0) / 100.0
    return if (v % 1.0 == 0.0) v.toInt().toString() else "%.2f".format(v)
}
