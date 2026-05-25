package com.eazpire.creator.wear

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import com.eazpire.creator.wear.ui.WearMainShell
import com.eazpire.creator.wear.ui.WearPairingScreen
import com.eazpire.creator.wear.ui.WearSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPLASH_MIN_MS = 2000L

@Composable
fun WearApp(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val translationStore = remember { WearTranslationStore() }
    var showSplash by remember { mutableStateOf(true) }
    var bootstrapped by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }
    var demoPreview by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
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
        } else if (
            com.eazpire.creator.wear.BuildConfig.DEBUG &&
            result.connectedNodes == 0 &&
            !tokenStore.isLoggedIn()
        ) {
            // Emulator without paired phone: show demo UI instead of empty pairing-only flow.
            demoPreview = true
        }
        refreshAuthState()
        bootstrapped = true
    }

    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        applyBootstrapResult()
        val remaining = SPLASH_MIN_MS - (System.currentTimeMillis() - started)
        if (remaining > 0) delay(remaining)
        showSplash = false
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
        modifier = modifier.fillMaxSize(),
        timeText = { },
        vignette = { },
        positionIndicator = { },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EazColors.CreatorBg),
        ) {
            when {
                showSplash -> WearSplashScreen(modifier = Modifier.fillMaxSize())
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
                else -> WearMainShell(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    demoPreview = demoPreview && !loggedIn,
                    refreshKey = refreshKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
