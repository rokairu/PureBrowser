package com.purebrowser.app.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.purebrowser.app.Engine
import com.wireguard.android.backend.Tunnel

/**
 * Panel VPN. HARUS dibuka dari proses utama (MainActivity), karena
 * GoBackend + VpnService-nya hidup di proses itu.
 */
@Composable
fun VpnDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    var profiles by remember { mutableStateOf(VpnStore(appCtx).list()) }
    var browserProfiles by remember { mutableStateOf(Engine.profiles.list()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val state by VpnController.state.collectAsState()
    val active by VpnController.activeProfile.collectAsState()
    val vpnError by VpnController.error.collectAsState()
    val stats by VpnController.stats.collectAsState()

    fun refresh() { profiles = VpnStore(appCtx).list() }

    // Setelah user menyetujui dialog izin VPN sistem, lanjutkan konek
    var pendingConnect by remember { mutableStateOf<VpnProfile?>(null) }
    val prepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        pendingConnect?.let { VpnController.connect(appCtx, it); pendingConnect = null }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val name = queryDisplayName(context, uri)
                VpnStore(appCtx).import(uri, name)
                refresh(); errorMsg = null
            } catch (e: Exception) {
                errorMsg = e.message
            }
        }
    }

    fun toggle(p: VpnProfile) {
        if (state == Tunnel.State.UP && active?.fileName == p.fileName) {
            VpnController.disconnect(appCtx)
        } else {
            val intent = VpnController.prepareIntent(appCtx)
            if (intent != null) {
                pendingConnect = p
                prepareLauncher.launch(intent)
            } else {
                VpnController.connect(appCtx, p)
            }
        }
    }

    /** Tetapkan config untuk profil browser aktif + langsung terapkan. */
    fun assign(fileName: String) {
        Engine.setProfileVpn(fileName)
        browserProfiles = Engine.profiles.list()
        if (fileName.isBlank()) {
            VpnController.disconnect(appCtx)
        } else if (!(state == Tunnel.State.UP && active?.fileName == fileName)) {
            profiles.firstOrNull { it.fileName == fileName }?.let { toggle(it) }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = "VPN WireGuard") },
        text = {
            Column {
                Text(
                    text = "PureBrowser tidak menyediakan server VPN. Impor berkas .conf dari provider VPN legit pilihanmu (mis. Mullvad, ProtonVPN, IVPN, atau server pribadimu). " +
                        "Hanya traffic browser ini yang masuk tunnel.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(8.dp))
                if (state == Tunnel.State.UP && active != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B3A24),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF81C995))
                            Spacer(Modifier.padding(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(text = "Terhubung: ${active!!.name}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                stats?.let { (rx, tx) ->
                                    Text(
                                        text = "↓ ${formatBytes(rx)}  ↑ ${formatBytes(tx)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                (errorMsg ?: vpnError)?.let {
                    Text(text = it, color = Color(0xFFF6AEA9), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                val assignedFile = browserProfiles.firstOrNull { it.id == Engine.profile.id }?.vpn.orEmpty()
                Text(
                    text = "VPN untuk profil \"${Engine.profile.name}\"",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                Text(
                    text = "Tunnel mengikuti profil yang aktif: ganti profil = IP ikut config profil itu.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = assignedFile.isEmpty(), onClick = { assign("") })
                    Text("Tidak pakai VPN (IP asli)", fontSize = 13.sp)
                }
                profiles.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = assignedFile == p.fileName, onClick = { assign(p.fileName) })
                        Text(p.name, fontSize = 13.sp)
                    }
                }
                val others = browserProfiles.filter { it.id != Engine.profile.id }
                if (others.isNotEmpty()) {
                    Text(
                        text = others.joinToString("\n") { o ->
                            "Profil \"${o.name}\": " + when {
                                o.vpn.isBlank() -> "tanpa VPN"
                                else -> profiles.firstOrNull { it.fileName == o.vpn }?.name ?: "config terhapus (diabaikan)"
                            }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(profiles, key = { it.fileName }) { p ->
                        val isActive = state == Tunnel.State.UP && active?.fileName == p.fileName
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = p.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (isActive) "Terhubung" else "Terputus",
                                        fontSize = 12.sp,
                                        color = if (isActive) Color(0xFF81C995)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                Button(onClick = { toggle(p) }) {
                                    Text(text = if (isActive) "Putus" else "Sambung")
                                }
                                IconButton(onClick = {
                                    if (isActive) VpnController.disconnect(appCtx)
                                    VpnStore(appCtx).delete(p)
                                    Engine.clearVpnAssignments(p.fileName)
                                    browserProfiles = Engine.profiles.list()
                                    refresh()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(text = "Impor berkas .conf")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Kill switch: aktifkan \"Always-on VPN\" + \"Block connections without VPN\" di pengaturan VPN sistem agar traffic tidak bocor saat tunnel putus.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                TextButton(onClick = {
                    appCtx.startActivity(
                        Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text(text = "Buka pengaturan VPN sistem") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(text = "Tutup") } },
    )
}

private fun queryDisplayName(context: Context, uri: android.net.Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
        }
    } catch (_: Exception) { null }
}

private fun formatBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
