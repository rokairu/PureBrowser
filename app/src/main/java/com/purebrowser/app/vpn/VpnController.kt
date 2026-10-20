package com.purebrowser.app.vpn

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "VpnController"

/** Implementasi Tunnel untuk backend WireGuard. */
class WgTunnel : Tunnel {
    var onChange: ((Tunnel.State) -> Unit)? = null
    override fun getName(): String = "purebrowser"
    override fun onStateChange(state: Tunnel.State) {
        onChange?.invoke(state)
    }
}

/**
 * Pengendali VPN. Tunnel-nya satu di level aplikasi (Android hanya
 * mengizinkan satu VPN aktif per aplikasi) dan dibatasi hanya untuk
 * traffic PureBrowser via IncludedApplications — tapi config yang dipakai
 * mengikuti profil browser yang aktif: tiap profil boleh punya config
 * sendiri, jadi IP publiknya mengikuti profil (lihat applyForProfile).
 */
object VpnController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backend: GoBackend? = null
    private val tunnel = WgTunnel().apply {
        onChange = { s ->
            _state.value = s
            notifyStatus()
        }
    }

    /** Dipanggil setiap status tunnel / config aktif berubah (buat notifikasi status). */
    var onStatusChanged: ((Tunnel.State, VpnProfile?) -> Unit)? = null

    private fun notifyStatus() {
        onStatusChanged?.invoke(_state.value, _active.value)
    }

    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state

    private val _active = MutableStateFlow<VpnProfile?>(null)
    val activeProfile: StateFlow<VpnProfile?> = _active

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _stats = MutableStateFlow<Pair<Long, Long>?>(null) // rx, tx bytes
    val stats: StateFlow<Pair<Long, Long>?> = _stats

    val isUp: Boolean get() = _state.value == Tunnel.State.UP

    private var statsJob: Job? = null

    private fun backendOf(ctx: Context): GoBackend {
        return backend ?: GoBackend(ctx.applicationContext).also { backend = it }
    }

    /** Perlu dipanggil dulu; null = izin sudah ada, non-null = butuh persetujuan user. */
    fun prepareIntent(ctx: Context) = VpnService.prepare(ctx)

    fun connect(ctx: Context, profile: VpnProfile) {
        _error.value = null
        scope.launch {
            try {
                val appCtx = ctx.applicationContext
                val raw = VpnStore(appCtx).readRaw(profile)
                val injected = VpnStore.injectIncludedApp(raw, appCtx.packageName)
                val config = Config.parse(injected.byteInputStream())
                val be = backendOf(appCtx)
                be.setState(tunnel, Tunnel.State.UP, config)
                _active.value = profile
                notifyStatus()
                startStats(appCtx)
                Log.i(TAG, "VPN terhubung: ${profile.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menghubungkan VPN", e)
                _error.value = e.message ?: "Gagal menghubungkan VPN"
                _state.value = Tunnel.State.DOWN
                notifyStatus()
            }
        }
    }

    fun disconnect(ctx: Context) {
        scope.launch {
            try {
                backendOf(ctx.applicationContext).setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                Log.e(TAG, "Gagal memutus VPN", e)
            } finally {
                _active.value = null
                _stats.value = null
                statsJob?.cancel()
                notifyStatus()
            }
        }
    }

    /**
     * Samakan tunnel dengan profil browser yang aktif: ada config terpilih ->
     * sambung config itu; kosong -> putus. Diam-diam (tanpa dialog izin sistem):
     * bila izin VPN belum diberikan, tunnel dibiarkan mati — user menyambung
     * manual dari dialog VPN yang memang menampilkan alur izinnya.
     */
    fun applyForProfile(ctx: Context, vpnFileName: String) {
        val appCtx = ctx.applicationContext
        if (vpnFileName.isBlank()) {
            if (isUp || _active.value != null) disconnect(appCtx)
            return
        }
        if (_state.value == Tunnel.State.UP && _active.value?.fileName == vpnFileName) return
        val target = VpnStore(appCtx).list().firstOrNull { it.fileName == vpnFileName }
        if (target == null || prepareIntent(appCtx) != null) {
            // Config sudah terhapus, atau izin VPN belum ada.
            if (isUp) disconnect(appCtx)
            return
        }
        connect(appCtx, target)
    }

    private fun startStats(appCtx: Context) {
        statsJob?.cancel()
        statsJob = scope.launch {
            val be = backendOf(appCtx)
            while (isUp) {
                try {
                    val s = be.getStatistics(tunnel)
                    _stats.value = s.totalRx() to s.totalTx()
                } catch (_: Exception) { }
                delay(2000)
            }
        }
    }
}
