package com.purebrowser.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"), LIGHT("Terang"), DARK("Gelap")
}

enum class SearchEngine(val label: String, val url: String) {
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
}

/** Pengaturan global (tema + mesin pencari), disimpan di SharedPreferences. */
object Prefs {
    private const val FILE = "purebrowser"

    var theme by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var engine by mutableStateOf(SearchEngine.GOOGLE)
        private set

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        theme = runCatching { ThemeMode.valueOf(p.getString("theme_mode", "") ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        engine = runCatching { SearchEngine.valueOf(p.getString("search_engine", "") ?: "") }
            .getOrDefault(SearchEngine.GOOGLE)
    }

    fun setTheme(ctx: Context, m: ThemeMode) {
        theme = m
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("theme_mode", m.name).apply()
    }

    fun setEngine(ctx: Context, e: SearchEngine) {
        engine = e
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("search_engine", e.name).apply()
    }
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B0E14),
    background = Color(0xFF0B0E14),
    surface = Color(0xFF131722),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF1E2430),
    onSurfaceVariant = Color(0xFFBDC3CE),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE3E9F2),
    onSurfaceVariant = Color(0xFF44474E),
)

@Composable
fun PureBrowserTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
