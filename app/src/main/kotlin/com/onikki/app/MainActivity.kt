package com.onikki.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.onikki.app.data.local.OnboardingStore
import com.onikki.app.ui.navigation.OnIkkiNavHost
import com.onikki.app.ui.onboarding.OnboardingRoute
import com.onikki.app.ui.theme.OnIkkiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnIkkiTheme {
                val onboardingStore = remember { OnboardingStore(applicationContext) }
                val isOnboardingComplete by onboardingStore.isComplete.collectAsState(initial = null)
                when (isOnboardingComplete) {
                    null -> Unit
                    false -> OnboardingRoute()
                    true -> OnIkkiNavHost()
                }
            }
        }
    }
}
