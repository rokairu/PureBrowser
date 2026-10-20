package com.purebrowser.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.WebResponse
import java.io.File
import java.util.UUID

/** Satu tab = satu GeckoSession. State-nya diamati Compose. */
class Tab(val session: GeckoSession, val isPrivate: Boolean, startUrl: String?) {
    val id: String = UUID.randomUUID().toString()
    var url by mutableStateOf(startUrl.orEmpty())
    var title by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    /** true = tampilkan halaman tab baru (native) di atas GeckoView. */
    var isNewTabPage by mutableStateOf(startUrl == null)
    /** URL yang dimuat begitu session menempel ke GeckoView (restore / tab latar). */
    var pendingUrl by mutableStateOf<String?>(startUrl)

    val label: String get() = title.ifBlank { hostOf(url).ifBlank { "Tab baru" } }
}

fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

fun shortUrl(url: String): String = hostOf(url).ifBlank { url }

private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?(/.*)?$")

/** Ubah teks di bilah alamat menjadi URL atau pencarian. */
fun resolveInput(raw: String, engine: SearchEngine): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    if (t.contains("://") || t.startsWith("about:")) return t
    if (!t.contains(' ')) {
        if (t.startsWith("localhost") || IPV4.matches(t)) return "http://$t"
        if (t.contains('.')) return "https://$t"
    }
    return engine.url + java.net.URLEncoder.encode(t, "UTF-8")
}

private val INTERNAL_SCHEMES = setOf(
    "http", "https", "about", "data", "blob", "javascript", "moz-extension",
    "resource", "view-source", "chrome", "file", "ws", "wss",
)

/**
 * Mesin browser: SATU GeckoRuntime, banyak tab. Profil dipisah dengan
 * contextId Gecko (cookie, storage, cache terpisah per profil), jadi ganti
 * profil cukup mengganti daftar tab — tanpa restart proses.
 */
object Engine {
    private var runtime: GeckoRuntime? = null
    private lateinit var app: Context

    lateinit var profiles: ProfileStore
        private set
    lateinit var history: EntryStore
        private set
    lateinit var bookmarks: EntryStore
        private set

    var profile by mutableStateOf(Profile("", "", 0))
        private set
    val tabs = mutableStateListOf<Tab>()
    var activeTabId by mutableStateOf<String?>(null)
        private set
    var fullScreen by mutableStateOf(false)
        private set

    val activeTab: Tab? get() = tabs.firstOrNull { it.id == activeTabId }

    // ---------------------------------------------------------------- start

    fun start(context: Context) {
        if (runtime == null) {
            app = context.applicationContext
            profiles = ProfileStore(app)
            val all = profiles.ensure()
            useProfile(all.firstOrNull { it.id == profiles.active } ?: all.first())

            val cb = ContentBlocking.Settings.Builder()
                .antiTracking(ContentBlocking.AntiTracking.STRICT)
                .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
                .strictSocialTrackingProtection(true)
                .build()
            val settings = GeckoRuntimeSettings.Builder()
                .contentBlocking(cb)
                .javaScriptEnabled(true)
                .build()
            val rt = GeckoRuntime.create(app, settings)
            runtime = rt
            Extensions.init(rt)
        }
        if (tabs.isEmpty() && !restoreSession()) newTab()
    }

    private fun useProfile(p: Profile) {
        profile = p
        profiles.active = p.id
        val dir = profiles.dir(p.id)
        history = EntryStore(File(dir, "history.json"), 500)
        bookmarks = EntryStore(File(dir, "bookmarks.json"), 2000)
    }

    // ----------------------------------------------------------------- tabs

    fun newTab(url: String? = null, isPrivate: Boolean = false, select: Boolean = true): Tab {
        val rt = requireNotNull(runtime) { "Engine belum start" }
        val sb = GeckoSessionSettings.Builder().usePrivateMode(isPrivate)
        // Identitas per profil: tiap profil punya User-Agent sendiri.
        if (profile.ua.isNotBlank()) sb.userAgentOverride(profile.ua)
        // Tab privat tidak memakai contextId (Gecko sudah mengisolasinya sendiri).
        if (!isPrivate) sb.contextId("p" + profile.id.replace("-", ""))
        val session = GeckoSession(sb.build())
        val tab = Tab(session, isPrivate, url)
        wire(tab)
        session.open(rt)
        tabs.add(tab)
        if (select) selectTab(tab.id)
        return tab
    }

    fun selectTab(id: String) {
        val t = tabs.firstOrNull { it.id == id } ?: return
        tabs.forEach { it.session.setActive(it === t) }
        activeTabId = id
        Extensions.setTabActive(t.session)
    }

    fun closeTab(id: String) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        val tab = tabs.removeAt(i)
        runCatching { tab.session.close() }
        if (tabs.isEmpty()) newTab()
        else if (id == activeTabId) selectTab(tabs[i.coerceAtMost(tabs.lastIndex)].id)
    }

    fun load(tab: Tab, url: String) {
        tab.isNewTabPage = false
        tab.pendingUrl = null
        tab.url = url
        tab.session.loadUri(url)
    }

    /** Buka URL di tab aktif (atau tab baru bila belum ada). */
    fun open(url: String) = load(activeTab ?: newTab(), url)

    fun exitFullScreen() {
        runCatching { activeTab?.session?.exitFullScreen() }
        fullScreen = false
    }

    /** Dipakai tombol "Keluar": simpan tab lalu tutup semuanya. */
    fun exitCleanup() {
        saveSession()
        val all = tabs.toList()
        tabs.clear()
        activeTabId = null
        fullScreen = false
        all.forEach { runCatching { it.session.close() } }
    }

    // ------------------------------------------------------------- profiles

    fun switchProfile(id: String) {
        if (id == profile.id) return
        val target = profiles.list().firstOrNull { it.id == id } ?: return
        saveSession()
        val old = tabs.toList()
        tabs.clear()
        activeTabId = null
        fullScreen = false
        useProfile(target)
        if (!restoreSession()) newTab()
        old.forEach { runCatching { it.session.close() } }
    }

    fun editProfile(id: String, name: String, color: Int) {
        profiles.update(id, name, color)
        if (id == profile.id) profiles.list().firstOrNull { it.id == id }?.let { profile = it }
    }

    // -------------------------------------------------------------- session

    fun saveSession() {
        if (runtime == null) return
        try {
            val saved = tabs.filter { !it.isPrivate && it.url.isNotBlank() }
            val arr = JSONArray()
            saved.forEach { arr.put(it.url) }
            val active = saved.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
            File(profiles.dir(profile.id), "tabs.json")
                .writeText(JSONObject().put("tabs", arr).put("active", active).toString())
        } catch (_: Exception) { }
    }

    private fun restoreSession(): Boolean {
        try {
            val o = JSONObject(File(profiles.dir(profile.id), "tabs.json").readText())
            val arr = o.getJSONArray("tabs")
            if (arr.length() == 0) return false
            val active = o.optInt("active", 0).coerceIn(0, arr.length() - 1)
            for (i in 0 until arr.length()) newTab(arr.getString(i), select = i == active)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    // ------------------------------------------------------------ delegates

    private fun wire(tab: Tab) {
        val s = tab.session

        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (url == null || url == "about:blank") return
                tab.url = url
                tab.isNewTabPage = false
                if (!tab.isPrivate) history.visit(url)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            // Skema non-web (mailto:, tel:, intent:, market:, ...) dibuka di aplikasi lain.
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? = handleExternal(tab, request.uri)

            // Tautan target=_blank / window.open: tanpa ini klik tautan semacam itu tidak terjadi apa-apa.
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                newTab(uri, tab.isPrivate)
                return null
            }
        }

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.isLoading = true
                tab.progress = 0
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.isLoading = false
                tab.progress = 100
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress
            }
        }

        s.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
                if (!tab.isPrivate && tab.url.isNotBlank() && !title.isNullOrBlank()) {
                    history.setTitle(tab.url, title)
                }
            }

            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                download(response)
            }

            // Tombol layar penuh pada video.
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                this@Engine.fullScreen = fullScreen
            }
        }
    }

    // ------------------------------------------------------------- external

    private fun handleExternal(tab: Tab, uri: String): GeckoResult<AllowOrDeny>? {
        val scheme = uri.substringBefore(':', "").lowercase()
        if (scheme.isEmpty() || scheme in INTERNAL_SCHEMES) return null
        if (!launchExternal(uri) && uri.startsWith("intent:")) {
            fallbackUrl(uri)?.let { load(tab, it) }
        }
        return GeckoResult.fromValue(AllowOrDeny.DENY)
    }

    private fun launchExternal(uri: String): Boolean = try {
        val intent = if (uri.startsWith("intent:")) {
            Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private fun fallbackUrl(uri: String): String? = try {
        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    } catch (_: Exception) {
        null
    }

    private fun download(r: WebResponse) {
        try {
            val cd = r.headers.entries.firstOrNull { it.key.equals("content-disposition", true) }?.value
            val mime = r.headers.entries.firstOrNull { it.key.equals("content-type", true) }
                ?.value?.substringBefore(';')
            val name = URLUtil.guessFileName(r.uri, cd, mime)
            val req = DownloadManager.Request(Uri.parse(r.uri))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            (app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            Toast.makeText(app, "Mengunduh $name", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(app, "Unduhan gagal dimulai", Toast.LENGTH_SHORT).show()
        }
    }
}
