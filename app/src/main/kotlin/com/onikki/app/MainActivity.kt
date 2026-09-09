package com.onikki.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.onikki.app.ui.navigation.OnIkkiNavHost
import com.onikki.app.ui.theme.OnIkkiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnIkkiTheme {
                OnIkkiNavHost()
            }
        }
    }
}
