package com.eazpire.creator.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.wear.ui.WearSplashScreen

/**
 * Wear OS switches to ambient (low power) quickly; [Scaffold] then draws a tiny strip
 * and the watch face stays visible. In ambient we fill the full display with the logo.
 */
@Composable
fun WearAmbientRoot(tokenStore: SecureTokenStore) {
    val ambientManager = LocalAmbientModeManager.current
    val isAmbient = ambientManager?.currentAmbientMode is AmbientMode.Ambient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EazColors.CreatorBg),
    ) {
        if (isAmbient) {
            WearSplashScreen(modifier = Modifier.fillMaxSize())
        } else {
            WearApp(tokenStore = tokenStore)
        }
    }
}
