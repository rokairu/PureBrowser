package com.purebrowser.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mozilla.geckoview.GeckoView
import java.io.File

private val Red = Color(0xFFF6AEA9)

@Composable
private fun Dim(text: String, size: Int = 12) {
    Text(
        text, fontSize = size.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}

// ------------------------------------------------------------------ tab

@Composable
fun TabsDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tab (${Engine.tabs.size})") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(Engine.tabs.toList(), key = { it.id }) { t ->
                    val selected = t.id == Engine.activeTabId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            .clickable { Engine.selectTab(t.id); onClose() }
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (t.isPrivate) "Privat · " else "") + t.label,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Dim(t.url.ifBlank { "Halaman baru" })
                        }
                        IconButton(onClick = { Engine.closeTab(t.id) }) { Icon(Icons.Filled.Close, "Tutup tab") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { Engine.newTab(); onClose() }) { Text("+ Tab baru") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

// --------------------------------------------------- bookmark & riwayat

@Composable
fun EntriesDialog(
    title: String,
    store: EntryStore,
    empty: String,
    onClear: (() -> Unit)?,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            if (store.items.isEmpty()) {
                Text(empty)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(store.items.toList(), key = { it.url + it.time }) { e ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { Engine.open(e.url); onClose() }.padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.title.ifBlank { shortUrl(e.url) }, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Dim(e.url)
                            }
                            IconButton(onClick = { store.remove(e.url) }) { Icon(Icons.Filled.Delete, "Hapus") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
        dismissButton = {
            if (onClear != null && store.items.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Hapus semua", color = Red) }
            }
        },
    )
}

// --------------------------------------------------------------- profil

@Composable
fun ProfilesDialog(onClose: () -> Unit) {
    var list by remember { mutableStateOf(Engine.profiles.list()) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    val activeId = Engine.profile.id

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Profil") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(list, key = { it.id }) { p ->
                    val active = p.id == activeId
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !active) { Engine.switchProfile(p.id); onClose() }
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                            val uaLabel = Regex("""Android (\d+)""").find(p.ua)?.let { "User-Agent Android ${it.groupValues[1]}" }
                            val sub = listOfNotNull(if (active) "Aktif" else null, uaLabel).joinToString(" • ")
                            if (sub.isNotEmpty()) {
                                Text(
                                    sub, fontSize = 12.sp,
                                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Filled.Edit, "Ubah") }
                        IconButton(onClick = { deleting = p }, enabled = !active && list.size > 1) {
                            Icon(Icons.Filled.Delete, "Hapus")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { adding = true }) { Text("+ Profil baru") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )

    if (adding) {
        ProfileEditor("Profil baru", Engine.profiles.nextName(), PALETTE[list.size % PALETTE.size], { adding = false }) { n, c ->
            Engine.profiles.add(n, c)
            list = Engine.profiles.list()
            adding = false
        }
    }
    editing?.let { p ->
        ProfileEditor("Ubah profil", p.name, p.color, { editing = null }) { n, c ->
            Engine.editProfile(p.id, n, c)
            list = Engine.profiles.list()
            editing = null
        }
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus profil?") },
            text = { Text("Profil \"${p.name}\" beserta riwayat dan bookmark-nya akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    Engine.profiles.delete(p.id)
                    list = Engine.profiles.list()
                    deleting = null
                }) { Text("Hapus", color = Red) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ProfileEditor(
    title: String,
    name0: String,
    color0: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
) {
    var name by remember { mutableStateOf(name0) }
    var color by remember { mutableStateOf(color0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Nama profil") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                PALETTE.chunked(4).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { c ->
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                                    .then(
                                        if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier,
                                    )
                                    .clickable { color = c },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }, enabled = name.isNotBlank()) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ----------------------------------------------------------- pengaturan

@Composable
fun SettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Pengaturan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section("Tema")
                ThemeMode.entries.forEach { m -> Choice(m.label, Prefs.theme == m) { Prefs.setTheme(ctx, m) } }
                Spacer(Modifier.height(12.dp))
                Section("Mesin pencari")
                SearchEngine.entries.forEach { e -> Choice(e.label, Prefs.engine == e) { Prefs.setEngine(ctx, e) } }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )
}

@Composable
private fun Section(label: String) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

// ------------------------------------------------------------- ekstensi

private data class Recommended(val name: String, val desc: String, val url: String)

private val RECOMMENDED = listOf(
    Recommended("uBlock Origin", "Pemblokir iklan & pelacak yang ringan",
        "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"),
    Recommended("Privacy Badger", "Blokir pelacak tersembunyi otomatis",
        "https://addons.mozilla.org/firefox/downloads/latest/privacy-badger17/latest.xpi"),
    Recommended("Dark Reader", "Mode gelap untuk semua situs",
        "https://addons.mozilla.org/firefox/downloads/latest/darkreader/latest.xpi"),
)

@Composable
fun ExtensionsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var copyError by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<org.mozilla.geckoview.WebExtension?>(null) }
    val err = copyError ?: Extensions.error

    // "*/*": banyak pemilih file tidak mengenali .xpi, sehingga filter tipe MIME membuatnya tak bisa dipilih.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val dest = File(ctx.cacheDir, "ext-${System.currentTimeMillis()}.xpi")
                val input = ctx.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Tidak bisa membaca file")
                input.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                if (dest.length() == 0L) { dest.delete(); throw java.io.IOException("File kosong") }
                copyError = null
                Extensions.installFromFile(dest)
            } catch (e: Exception) {
                copyError = "Gagal menyiapkan file: ${e.message ?: "kesalahan tidak diketahui"}"
            }
        }
    }
    LaunchedEffect(Unit) { Extensions.refresh() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Ekstensi (${Extensions.list.size})") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (err != null) {
                    Text(
                        "$err\n(ketuk untuk menutup)", fontSize = 13.sp, color = Red,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF5A1F1A).copy(alpha = 0.4f))
                            .clickable { copyError = null; Extensions.error = null }
                            .padding(10.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (Extensions.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Memasang ekstensi…", fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    if (Extensions.list.isEmpty()) {
                        item { Dim("Belum ada ekstensi terpasang.", 14) }
                    }
                    items(Extensions.list.toList(), key = { it.id }) { ext ->
                        val meta = ext.metaData
                        val enabled = meta?.enabled != false
                        val options = meta?.optionsPageUrl
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    meta?.name ?: ext.id, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Dim("v${meta?.version ?: "?"}")
                            }
                            if (enabled && Extensions.actions.containsKey(ext.id)) {
                                TextButton(onClick = { onClose(); Extensions.click(ext) }) { Text("Buka") }
                            }
                            if (!options.isNullOrBlank()) {
                                IconButton(onClick = { Engine.newTab(options); onClose() }) {
                                    Icon(Icons.Filled.Settings, "Pengaturan ekstensi")
                                }
                            }
                            Switch(checked = enabled, onCheckedChange = { Extensions.setEnabled(ext, it) })
                            IconButton(onClick = { confirmRemove = ext }) {
                                Icon(Icons.Filled.Delete, "Hapus ekstensi", tint = Red)
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Rekomendasi", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(RECOMMENDED) { rec ->
                        val installed = Extensions.list.any {
                            it.metaData?.name?.contains(rec.name, ignoreCase = true) == true
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !installed && !Extensions.busy) { Extensions.installFromUrl(rec.url) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(rec.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Dim(rec.desc)
                            }
                            Text(
                                if (installed) "Terpasang" else "Pasang", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Dim("Hanya ekstensi bertanda tangan Mozilla (addons.mozilla.org). Ekstensi berlaku untuk semua profil.")
            }
        },
        confirmButton = {
            TextButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !Extensions.busy) { Text("Dari file .xpi") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Tutup") } },
    )

    confirmRemove?.let { ext ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Hapus ekstensi?") },
            text = { Text("\"${ext.metaData?.name ?: ext.id}\" akan dihapus beserta datanya.") },
            confirmButton = {
                TextButton(onClick = { Extensions.uninstall(ext); confirmRemove = null }) { Text("Hapus", color = Red) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Batal") } },
        )
    }
}

/** Konfirmasi izin sebelum ekstensi dipasang (dulu otomatis disetujui tanpa bertanya). */
@Composable
fun InstallPromptDialog() {
    val p = Extensions.installPrompt ?: return
    fun answer(allow: Boolean) {
        p.result.complete(org.mozilla.geckoview.WebExtension.PermissionPromptResponse(allow, allow, allow))
        Extensions.installPrompt = null
    }
    AlertDialog(
        onDismissRequest = { answer(false) },
        title = { Text("Pasang \"${p.name}\"?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (p.permissions.isEmpty()) Text("Ekstensi ini tidak meminta izin khusus.")
                else {
                    Text("Ekstensi ini meminta izin:", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    p.permissions.take(12).forEach { Dim("• $it", 13) }
                    if (p.permissions.size > 12) Dim("… dan ${p.permissions.size - 12} lainnya", 13)
                }
            }
        },
        confirmButton = { TextButton(onClick = { answer(true) }) { Text("Pasang") } },
        dismissButton = { TextButton(onClick = { answer(false) }) { Text("Batal") } },
    )
}

/** Popup tombol toolbar ekstensi (mis. panel uBlock Origin). */
@Composable
fun ExtensionPopup() {
    val s = Extensions.popup ?: return
    Dialog(
        onDismissRequest = { Extensions.closePopup() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.94f).height(480.dp),
        ) {
            AndroidView(
                factory = { c -> GeckoView(c).also { v -> runCatching { v.setSession(s) } } },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
