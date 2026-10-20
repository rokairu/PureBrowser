package com.purebrowser.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File

/** Permintaan izin saat memasang ekstensi; menunggu jawaban user. */
class InstallPrompt(
    val name: String,
    val permissions: List<String>,
    val result: GeckoResult<WebExtension.PermissionPromptResponse>,
)

private val mainHandler = Handler(Looper.getMainLooper())
private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
}

/**
 * Pengelola WebExtension (mesin yang sama dengan Firefox Android).
 * Hanya ekstensi bertanda tangan Mozilla (addons.mozilla.org) yang bisa dipasang.
 * Ekstensi berlaku untuk semua profil.
 */
object Extensions {
    private var controller: WebExtensionController? = null

    val list = mutableStateListOf<WebExtension>()
    /** Tombol toolbar tiap ekstensi (kunci = id ekstensi). */
    val actions = mutableStateMapOf<String, WebExtension.Action>()
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var installPrompt by mutableStateOf<InstallPrompt?>(null)
    /** Sesi popup ekstensi yang sedang ditampilkan (null = tidak ada). */
    var popup by mutableStateOf<GeckoSession?>(null)

    private val actionDelegate = object : WebExtension.ActionDelegate {
        override fun onBrowserAction(
            extension: WebExtension,
            session: GeckoSession?,
            action: WebExtension.Action,
        ) {
            if (session == null) actions[extension.id] = action
        }

        override fun onTogglePopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession>? = showPopup()

        override fun onOpenPopup(
            extension: WebExtension,
            action: WebExtension.Action,
        ): GeckoResult<GeckoSession>? = showPopup()
    }

    private fun showPopup(): GeckoResult<GeckoSession> {
        val s = GeckoSession()
        popup = s
        return GeckoResult.fromValue(s)
    }

    fun closePopup() {
        val s = popup
        popup = null
        if (s != null) runCatching { s.close() }
    }

    fun init(runtime: GeckoRuntime) {
        if (controller != null) return
        val c = runtime.webExtensionController
        controller = c

        c.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                installPrompt?.result?.complete(WebExtension.PermissionPromptResponse(false, false, false))
                installPrompt = InstallPrompt(
                    name = extension.metaData?.name ?: extension.id,
                    permissions = permissions.toList() + origins.toList(),
                    result = result,
                )
                return result
            }
        })

        c.setAddonManagerDelegate(object : WebExtensionController.AddonManagerDelegate {
            override fun onInstalled(extension: WebExtension) { extension.setActionDelegate(actionDelegate); refresh() }
            override fun onUninstalled(extension: WebExtension) { actions.remove(extension.id); refresh() }
            override fun onEnabled(extension: WebExtension) { extension.setActionDelegate(actionDelegate); refresh() }
            override fun onDisabled(extension: WebExtension) { refresh() }
            override fun onInstallationFailed(
                extension: WebExtension?,
                exception: WebExtension.InstallException,
            ) {
                busy = false
                error = describeInstallError(exception)
            }
        })

        c.setExtensionProcessDelegate(object : WebExtensionController.ExtensionProcessDelegate {
            override fun onDisabledProcessSpawning() {
                try { c.enableExtensionProcessSpawning() } catch (_: Exception) { }
            }
        })

        // Ekstensi yang memanggil tabs.create (mis. halaman dasbor uBlock) membuka tab biasa.
        c.setTabDelegate(object : WebExtensionController.TabDelegate {
            override fun onNewTab(
                source: WebExtension,
                createDetails: WebExtension.CreateTabDetails,
            ): GeckoResult<GeckoSession>? {
                createDetails.url?.let { Engine.newTab(it) }
                return null
            }
        })

        refresh()
    }

    fun refresh() {
        val c = controller ?: return
        c.list().accept(
            { exts -> onMain {
                val sorted = (exts ?: emptyList()).sortedBy { it.metaData?.name ?: it.id }
                sorted.forEach { it.setActionDelegate(actionDelegate) }
                list.clear()
                list.addAll(sorted)
            } },
            { err -> onMain { error = err?.message ?: "Gagal memuat daftar ekstensi" } },
        )
    }

    /** Pasang dari file .xpi lokal. File dihapus setelah selesai. */
    fun installFromFile(xpi: File) {
        val c = controller ?: run {
            error = "Pengelola ekstensi belum siap"
            runCatching { xpi.delete() }
            return
        }
        busy = true
        error = null
        val uri = "file://" + java.net.URLEncoder.encode(xpi.absolutePath, "UTF-8").replace("%2F", "/")
        c.install(uri, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { onMain { busy = false; runCatching { xpi.delete() }; refresh() } },
            { err -> onMain { busy = false; runCatching { xpi.delete() }; error = describeInstallError(err) } },
        )
    }

    /** Pasang langsung dari URL https (mis. tautan unduhan AMO). */
    fun installFromUrl(url: String) {
        val c = controller ?: run { error = "Pengelola ekstensi belum siap"; return }
        busy = true
        error = null
        c.install(url, WebExtensionController.INSTALLATION_METHOD_FROM_FILE).accept(
            { onMain { busy = false; refresh() } },
            { err -> onMain { busy = false; error = describeInstallError(err) } },
        )
    }

    fun setEnabled(ext: WebExtension, enable: Boolean) {
        val c = controller ?: return
        error = null
        val r = if (enable) c.enable(ext, WebExtensionController.EnableSource.USER)
        else c.disable(ext, WebExtensionController.EnableSource.USER)
        // WebExtension immutable: enable/disable mengembalikan instance baru.
        r.accept(
            { updated -> onMain {
                if (updated != null) {
                    updated.setActionDelegate(actionDelegate)
                    val i = list.indexOfFirst { it.id == updated.id }
                    if (i >= 0) list[i] = updated else refresh()
                } else refresh()
            } },
            { err -> onMain { error = err?.message ?: "Gagal mengubah status ekstensi" } },
        )
    }

    fun uninstall(ext: WebExtension) {
        val c = controller ?: return
        error = null
        c.uninstall(ext).accept(
            { onMain { actions.remove(ext.id); refresh() } },
            { err -> onMain { error = err?.message ?: "Gagal menghapus ekstensi" } },
        )
    }

    /** Ketuk tombol toolbar ekstensi (membuka popup bila ada). */
    fun click(ext: WebExtension) {
        runCatching { actions[ext.id]?.click() }
    }

    fun setTabActive(session: GeckoSession) {
        try { controller?.setTabActive(session, true) } catch (_: Exception) { }
    }

    private fun describeInstallError(err: Throwable?): String {
        if (err == null) return "Gagal memasang ekstensi (kesalahan tidak diketahui)"
        val ie = err as? WebExtension.InstallException
        return when (ie?.code) {
            WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED ->
                "Ekstensi harus ditandatangani Mozilla. Unduh dari addons.mozilla.org."
            WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE ->
                "Gagal mengunduh ekstensi. Periksa koneksi internet."
            WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE,
            WebExtension.InstallException.ErrorCodes.ERROR_INCORRECT_HASH ->
                "File .xpi rusak atau tidak valid."
            WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED,
            WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED ->
                "Ekstensi diblokir oleh Mozilla (blocklist)."
            WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE ->
                "Ekstensi tidak kompatibel dengan versi mesin ini."
            WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS ->
                "Tidak bisa membaca file .xpi."
            else -> "Gagal memasang ekstensi: ${err.message ?: err.javaClass.simpleName}"
        }
    }
}
