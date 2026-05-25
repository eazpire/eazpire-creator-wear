package com.eazpire.creator.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.eazpire.creator.core.auth.SecureTokenStore
import androidx.lifecycle.lifecycleScope
import com.eazpire.creator.wear.auth.WearAuthListenerService
import com.eazpire.creator.wear.auth.bootstrapAuthFromPhone
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var tokenStore: SecureTokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenStore = SecureTokenStore(this)
        setContent {
            WearEazTheme {
                WearApp(tokenStore = tokenStore)
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
}
