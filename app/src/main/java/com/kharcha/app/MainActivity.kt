package com.kharcha.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kharcha.app.ui.KharchaRoot

/**
 * The app's only Activity (Android's word for "a window"). Every screen is drawn inside it
 * with Jetpack Compose; moving between screens never opens another Activity.
 */
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as KharchaApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = container
        // Keep the splash screen until the data file has been read.
        splash.setKeepOnScreenCondition { !container.loaded.value }
        setContent {
            KharchaRoot(
                container = container,
                onThemeResolved = { dark ->
                    // Draw behind the status and navigation bars, with icons that contrast the theme.
                    val style = if (dark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        container.onForeground()
    }

    override fun onStop() {
        super.onStop()
        container.onBackground()
    }
}
