package com.purebrowser.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.mozilla.geckoview.GeckoView

/**
 * Satu-satunya activity. Mesin (Engine) hidup di proses ini; activity hanya
 * menempelkan GeckoView dan UI Compose di atasnya.
 */
class MainActivity : ComponentActivity() {

    private lateinit var geckoView: GeckoView

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        Engine.start(this)
        geckoView = GeckoView(this)
        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val tab = Engine.activeTab
                when {
                    Engine.fullScreen -> Engine.exitFullScreen()
                    tab != null && tab.canGoBack -> tab.session.goBack()
                    else -> moveTaskToBack(true)
                }
            }
        })

        setContent {
            PureBrowserTheme {
                BrowserScreen(
                    activity = this,
                    geckoView = geckoView,
                    onExit = {
                        Engine.exitCleanup()
                        finishAndRemoveTask()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        Engine.saveSession()
        super.onStop()
    }

    /** Tautan dari aplikasi lain (browser default) dibuka di tab baru. */
    private fun handleIntent(i: Intent?) {
        val url = if (i?.action == Intent.ACTION_VIEW) i?.dataString else null
        if (!url.isNullOrBlank()) Engine.newTab(url)
    }
}
