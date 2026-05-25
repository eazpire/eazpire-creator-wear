package com.eazpire.creator.wear

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientLifecycleObserver
import com.eazpire.creator.core.auth.SecureTokenStore
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: SecureTokenStore
    private val isAmbientState = mutableStateOf(false)

    private val ambientObserver: AmbientLifecycleObserver by lazy {
        AmbientLifecycleObserver(this) { ambientState ->
            isAmbientState.value = ambientState.isAmbient
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        keepInteractiveForWear()
        tokenStore = SecureTokenStore(this)
        lifecycle.addObserver(ambientObserver)

        setContent {
            WearEazTheme {
                WearAmbientRoot(
                    tokenStore = tokenStore,
                    isAmbient = isAmbientState.value,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        keepInteractiveForWear()
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

    private fun keepInteractiveForWear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
