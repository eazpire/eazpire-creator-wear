package com.eazpire.creator.wear

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.core.auth.WearSessionGate
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import kotlinx.coroutines.launch

/**
 * Wear OS 6 (API 36) expects targetSdk 36 + ambient registration for full-screen apps.
 * Without this, the system keeps the watch face visible and shows the app as a thin strip.
 */
class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: SecureTokenStore

    private val ambientObserver: AmbientLifecycleObserver by lazy {
        AmbientLifecycleObserver(
            this,
            object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                    // Keep full UI; Wear OS 6 dims the display but still uses the full window.
                }

                override fun onExitAmbient() {
                    applyFullscreenWindow()
                }
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreenWindow()
        tokenStore = SecureTokenStore(this)
        WearSessionGate.ensureSchema(this, tokenStore)
        lifecycle.addObserver(ambientObserver)

        setContent {
            WearEazTheme {
                WearApp(
                    tokenStore = tokenStore,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyFullscreenWindow()
        if (!::tokenStore.isInitialized) return
        lifecycleScope.launch {
            bootstrapAuthFromPhone(this@MainActivity, tokenStore)
            if (WearSessionGate.isSessionReady(this@MainActivity, tokenStore)) {
                sendBroadcast(
                    android.content.Intent(WearAuthListenerService.ACTION_AUTH_CHANGED)
                        .setPackage(packageName),
                )
            }
        }
    }

    private fun applyFullscreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
    }
}
