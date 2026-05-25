package com.eazpire.creator.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: SecureTokenStore

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        WearOngoingSession.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreenWindow()
        tokenStore = SecureTokenStore(this)

        setContent {
            WearEazTheme {
                WearApp(
                    tokenStore = tokenStore,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ensureOngoingSession()
    }

    override fun onStop() {
        WearOngoingSession.stop(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyFullscreenWindow()
        if (!::tokenStore.isInitialized) return
        lifecycleScope.launch {
            bootstrapAuthFromPhone(this@MainActivity, tokenStore)
            if (tokenStore.isLoggedIn()) {
                sendBroadcast(
                    android.content.Intent(WearAuthListenerService.ACTION_AUTH_CHANGED)
                        .setPackage(packageName),
                )
            }
        }
    }

    private fun ensureOngoingSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        WearOngoingSession.start(this)
    }

    private fun applyFullscreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
