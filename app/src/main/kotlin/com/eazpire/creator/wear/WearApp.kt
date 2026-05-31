package com.eazpire.creator.wear

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.Scaffold
import com.eazpire.creator.core.api.WearPairApi
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.auth.WearSessionGate
import com.eazpire.creator.core.device.WearDeviceId
import com.eazpire.creator.core.i18n.WearTranslationStore
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import com.eazpire.creator.wear.ui.WearMainShell
import com.eazpire.creator.wear.ui.WearPairingScreen
import com.eazpire.creator.wear.ui.WearSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val SPLASH_MIN_MS = 2000L

@Composable
fun WearApp(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val translationStore = remember { WearTranslationStore() }
    var showSplash by remember { mutableStateOf(true) }
    WearSessionGate.ensureSchema(context, tokenStore)
    var loggedIn by remember {
        mutableStateOf(WearSessionGate.isSessionReady(context, tokenStore))
    }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun refreshAuthState() {
        loggedIn = WearSessionGate.isSessionReady(context, tokenStore)
        if (loggedIn) refreshKey++
    }

    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        if (!WearSessionGate.isSessionReady(context, tokenStore)) {
            repeat(4) {
                bootstrapAuthFromPhone(context, tokenStore)
                refreshAuthState()
                if (WearSessionGate.isSessionReady(context, tokenStore)) return@repeat
                delay(750)
            }
        }
        val remaining = SPLASH_MIN_MS - (System.currentTimeMillis() - started)
        if (remaining > 0) delay(remaining)
        showSplash = false
    }

    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        val jwt = tokenStore.getJwt()?.trim().orEmpty() ?: return@LaunchedEffect
        val deviceId = WearDeviceId.get(context)
        val pairApi = WearPairApi()
        while (isActive) {
            delay(30_000)
            try {
                val res = withContext(Dispatchers.IO) { pairApi.deviceStatus(jwt, deviceId) }
                if (res.optBoolean("ok", false) && !res.optBoolean("active", true)) {
                    WearSessionGate.clearSession(context, tokenStore)
                    loggedIn = false
                    break
                }
            } catch (_: Exception) { /* ignore */ }
        }
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
                loggedIn -> WearMainShell(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    refreshKey = refreshKey,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> WearPairingScreen(
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    onPaired = { refreshAuthState() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
