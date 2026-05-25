package com.eazpire.creator.wear

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import com.eazpire.creator.wear.ui.WearDashboardScreen
import com.eazpire.creator.wear.ui.WearJobsScreen
import com.eazpire.creator.wear.ui.WearPairingScreen
import com.eazpire.creator.wear.ui.WearUploadScreen
import kotlinx.coroutines.launch

private enum class WearTab { Dashboard, Jobs, Upload }

@Composable
fun WearApp(tokenStore: SecureTokenStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val translationStore = remember { WearTranslationStore() }
    var bootstrapped by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }
    var demoPreview by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf(WearTab.Dashboard) }
    var connectionStatus by remember { mutableStateOf("") }

    fun refreshAuthState() {
        loggedIn = tokenStore.isLoggedIn()
        refreshKey++
    }

    suspend fun applyBootstrapResult() {
        val result = bootstrapAuthFromPhone(context, tokenStore)
        connectionStatus = when {
            result.loggedInAfter -> translationStore.t("wear.status_synced", "Session synced from phone")
            result.connectedNodes == 0 ->
                translationStore.t("wear.status_no_phone", "No phone connected to this watch")
            else ->
                translationStore.t(
                    "wear.status_phone_not_logged_in",
                    "Phone connected — log in on the Eazpire phone app",
                )
        }
        if (result.loggedInAfter) {
            demoPreview = false
        }
        refreshAuthState()
        bootstrapped = true
    }

    LaunchedEffect(Unit) {
        applyBootstrapResult()
    }

    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                refreshAuthState()
            }
        }
        val filter = android.content.IntentFilter(WearAuthListenerService.ACTION_AUTH_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(EazColors.CreatorBg),
        timeText = { if (bootstrapped) TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
    ) {
        when {
            !bootstrapped -> WearLoadingPane(translationStore)
            !loggedIn && !demoPreview -> WearPairingScreen(
                translationStore = translationStore,
                connectionStatus = connectionStatus,
                onRetrySync = { scope.launch { applyBootstrapResult() } },
                onDemoPreview = if (com.eazpire.creator.wear.BuildConfig.DEBUG) {
                    { demoPreview = true }
                } else {
                    null
                },
                modifier = Modifier.fillMaxSize(),
            )
            else -> WearMainContent(
                tokenStore = tokenStore,
                translationStore = translationStore,
                demoPreview = demoPreview && !loggedIn,
                tab = tab,
                onTab = { tab = it },
                refreshKey = refreshKey,
            )
        }
    }
}

@Composable
private fun WearLoadingPane(translationStore: WearTranslationStore) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                text = translationStore.t("wear.loading", "Loading…"),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WearMainContent(
    tokenStore: SecureTokenStore,
    translationStore: WearTranslationStore,
    demoPreview: Boolean,
    tab: WearTab,
    onTab: (WearTab) -> Unit,
    refreshKey: Int,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (demoPreview) {
            Text(
                text = translationStore.t(
                    "wear.demo_banner",
                    "Demo preview — log in on phone for live data",
                ),
                style = MaterialTheme.typography.caption2,
                color = EazColors.Orange,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            WearNavChip(
                label = translationStore.t("wear.dashboard", "Dashboard"),
                selected = tab == WearTab.Dashboard,
                onClick = { onTab(WearTab.Dashboard) },
            )
            WearNavChip(
                label = translationStore.t("creator.notifications.active_jobs", "Active Jobs"),
                selected = tab == WearTab.Jobs,
                onClick = { onTab(WearTab.Jobs) },
            )
            WearNavChip(
                label = translationStore.t("wear.upload", "Phone upload"),
                selected = tab == WearTab.Upload,
                onClick = { onTab(WearTab.Upload) },
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                WearTab.Dashboard -> WearDashboardScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    useDemoData = demoPreview,
                    modifier = Modifier.fillMaxSize(),
                )
                WearTab.Jobs -> WearJobsScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    useDemoData = demoPreview,
                    modifier = Modifier.fillMaxSize(),
                )
                WearTab.Upload -> WearUploadScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    useDemoData = demoPreview,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WearNavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        colors = ChipDefaults.chipColors(
            backgroundColor = if (selected) EazColors.Orange else MaterialTheme.colors.surface,
            contentColor = EazColors.TextPrimary,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    )
}
