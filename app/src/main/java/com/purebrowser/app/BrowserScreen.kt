package com.purebrowser.app

import android.app.Activity
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.purebrowser.app.vpn.VpnController
import com.purebrowser.app.vpn.VpnDialog
import com.wireguard.android.backend.Tunnel
import org.mozilla.geckoview.GeckoView

private enum class Panel { TABS, BOOKMARKS, HISTORY, EXTENSIONS, VPN, SETTINGS, PROFILES }

@Composable
fun BrowserScreen(activity: Activity, geckoView: GeckoView, onExit: () -> Unit) {
    val tab = Engine.activeTab
    val profile = Engine.profile
    val fs = Engine.fullScreen
    var menu by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    val ink = MaterialTheme.colorScheme.onSurface

    // Warna ikon status bar mengikuti tema aplikasi; sembunyikan bar saat video layar penuh.
    val view = LocalView.current
    val darkBg = MaterialTheme.colorScheme.background.luminance() < 0.5f
    SideEffect {
        val c = WindowCompat.getInsetsController(activity.window, view)
        c.isAppearanceLightStatusBars = !darkBg
        c.isAppearanceLightNavigationBars = !darkBg
    }
    LaunchedEffect(fs) {
        val c = WindowCompat.getInsetsController(activity.window, view)
        if (fs) {
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            c.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Column(
        (if (fs) Modifier.fillMaxSize().background(Color.Black)
        else Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().imePadding().navigationBarsPadding()),
    ) {
        if (!fs) {
            // ---- Bilah atas: profil | alamat | jumlah tab | menu ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { panel = Panel.PROFILES }) { Avatar(profile, 32.dp) }
                AddressBar(tab, Modifier.weight(1f))
                IconButton(onClick = { panel = Panel.TABS }) {
                    Box(
                        Modifier.size(26.dp).border(2.dp, ink, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(Engine.tabs.size.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                val vpnState by VpnController.state.collectAsState()
                IconButton(onClick = { panel = Panel.VPN }) {
                    Icon(
                        Icons.Filled.Lock,
                        "VPN",
                        tint = if (vpnState == Tunnel.State.UP) Color(0xFF81C995) else ink,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val close = { menu = false }
                        MenuItem("Tab baru", close) { Engine.newTab() }
                        MenuItem("Tab privat baru", close) { Engine.newTab(isPrivate = true) }
                        MenuItem("Bookmark", close) { panel = Panel.BOOKMARKS }
                        MenuItem("Riwayat", close) { panel = Panel.HISTORY }
                        MenuItem("Unduhan", close) {
                            runCatching {
                                activity.startActivity(
                                    android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS),
                                )
                            }
                        }
                        MenuItem("Ekstensi", close) { panel = Panel.EXTENSIONS }
                        MenuItem("VPN", close) { panel = Panel.VPN }
                        MenuItem("Pengaturan", close) { panel = Panel.SETTINGS }
                        MenuItem("Ganti profil", close) { panel = Panel.PROFILES }
                        MenuItem("Keluar", close) { onExit() }
                    }
                }
            }
            if (tab?.isLoading == true) {
                LinearProgressIndicator(
                    progress = { tab.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                )
            }
        }

        // ---- Area web. GeckoView selalu ada di komposisi; halaman tab baru hanya overlay. ----
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    geckoView.also { v -> (v.parent as? ViewGroup)?.removeView(v) }
                },
                update = { v ->
                    if (tab != null) {
                        if (v.session !== tab.session) v.setSession(tab.session)
                        tab.pendingUrl?.let { u ->
                            tab.pendingUrl = null
                            tab.session.loadUri(u)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (tab != null && tab.isNewTabPage && !fs) NewTabPage(tab)
        }

        if (!fs) {
            // ---- Bilah bawah: navigasi ----
            val url = tab?.url.orEmpty()
            val marked = url.isNotBlank() && Engine.bookmarks.contains(url)
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { tab?.session?.goBack() }, enabled = tab?.canGoBack == true) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                }
                IconButton(onClick = { tab?.session?.goForward() }, enabled = tab?.canGoForward == true) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Maju")
                }
                IconButton(onClick = {
                    if (tab?.isLoading == true) tab.session.stop() else tab?.session?.reload()
                }) {
                    Icon(
                        if (tab?.isLoading == true) Icons.Filled.Close else Icons.Filled.Refresh,
                        "Muat ulang",
                    )
                }
                IconButton(onClick = {
                    if (url.isNotBlank()) Engine.bookmarks.toggle(url, tab?.title.orEmpty())
                }) {
                    Icon(
                        Icons.Filled.Star, "Bookmark",
                        tint = if (marked) Color(0xFFFDD663) else ink.copy(alpha = 0.55f),
                    )
                }
                IconButton(onClick = { Engine.newTab() }) { Icon(Icons.Filled.Add, "Tab baru") }
            }
        }
    }

    val closePanel = { panel = null }
    when (panel) {
        Panel.TABS -> TabsDialog(closePanel)
        Panel.BOOKMARKS -> EntriesDialog("Bookmark", Engine.bookmarks, "Belum ada bookmark. Ketuk bintang di bilah bawah.", null, closePanel)
        Panel.HISTORY -> EntriesDialog("Riwayat", Engine.history, "Riwayat kosong.", { Engine.history.clear() }, closePanel)
        Panel.EXTENSIONS -> ExtensionsDialog(closePanel)
        Panel.VPN -> VpnDialog(onClose = closePanel)
        Panel.SETTINGS -> SettingsDialog(closePanel)
        Panel.PROFILES -> ProfilesDialog(closePanel)
        null -> Unit
    }
    InstallPromptDialog()
    ExtensionPopup()
}

@Composable
private fun MenuItem(label: String, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = { close(); action() })
}

@Composable
fun Avatar(p: Profile, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(p.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            p.name.take(1).uppercase(),
            color = Color(0xFF10131A),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

@Composable
private fun AddressBar(tab: Tab?, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    val url = tab?.url.orEmpty()
    val ink = MaterialTheme.colorScheme.onSurface

    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (url.startsWith("https://") && !focused) {
            Icon(Icons.Filled.Lock, null, Modifier.size(13.dp), tint = ink.copy(alpha = 0.6f))
            Spacer(Modifier.width(6.dp))
        }
        BasicTextField(
            value = if (focused) field else TextFieldValue(shortUrl(url)),
            onValueChange = { field = it },
            singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 14.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                val target = resolveInput(field.text, Prefs.engine)
                if (target.isNotBlank()) Engine.load(tab ?: Engine.newTab(), target)
                focus.clearFocus()
            }),
            modifier = Modifier.weight(1f).onFocusChanged { st ->
                if (st.isFocused && !focused) field = TextFieldValue(url, TextRange(0, url.length))
                focused = st.isFocused
            },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (!focused && url.isBlank()) {
                        Text("Cari atau ketik alamat", fontSize = 14.sp, color = ink.copy(alpha = 0.5f))
                    }
                    inner()
                }
            },
        )
    }
}

private val SHORTCUTS = listOf(
    "Google" to "https://www.google.com",
    "YouTube" to "https://m.youtube.com",
    "Wikipedia" to "https://id.wikipedia.org",
    "GitHub" to "https://github.com",
    "KBBI" to "https://kbbi.web.id",
    "Detik" to "https://www.detik.com",
)

/** Halaman tab baru: akses cepat + terakhir dikunjungi. Native, tanpa memuat apa pun dari internet. */
@Composable
private fun NewTabPage(tab: Tab) {
    val ink = MaterialTheme.colorScheme.onBackground
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text("PureBrowser", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = ink)
        Text("Jelajah bebas, tanpa pelacak", fontSize = 14.sp, color = ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(32.dp))

        SHORTCUTS.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (name, url) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { Engine.load(tab, url) }.padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name.take(1), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(name, fontSize = 12.sp, color = ink, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        val recent = Engine.history.items.take(5)
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Terakhir dikunjungi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = ink.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            recent.forEach { e ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { Engine.load(tab, e.url) }.padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Text(
                        e.title.ifBlank { shortUrl(e.url) }, fontSize = 14.sp, color = ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        e.url, fontSize = 12.sp, color = ink.copy(alpha = 0.5f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
