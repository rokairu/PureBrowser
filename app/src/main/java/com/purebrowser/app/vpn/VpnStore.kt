package com.purebrowser.app.vpn

import android.content.Context
import android.net.Uri
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.File
import java.util.UUID

data class VpnProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fileName: String,
    val importedAt: Long = System.currentTimeMillis(),
)

/**
 * Penyimpanan file .conf WireGuard milik pengguna (di direktori privat aplikasi).
 * PureBrowser TIDAK menyediakan server VPN — pengguna mengimpor konfigurasi
 * dari provider VPN legit pilihannya sendiri (Mullvad, ProtonVPN, dsb).
 */
class VpnStore(private val appContext: Context) {

    private val dir = File(appContext.filesDir, "vpn").apply { mkdirs() }

    fun list(): List<VpnProfile> =
        dir.listFiles { f -> f.extension == "conf" }
            ?.sortedBy { it.lastModified() }
            ?.map { VpnProfile(name = it.nameWithoutExtension, fileName = it.name, importedAt = it.lastModified()) }
            ?: emptyList()

    /** Impor dari URI (SAF). Melempar jika bukan config WireGuard valid. */
    @Throws(Exception::class)
    fun import(uri: Uri, displayName: String?): VpnProfile {
        val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: throw IllegalArgumentException("Tidak bisa membaca berkas")
        // Validasi: harus bisa di-parse sebagai config WireGuard
        try {
            Config.parse(text.byteInputStream())
        } catch (e: BadConfigException) {
            throw IllegalArgumentException("Berkas ini bukan konfigurasi WireGuard yang valid")
        }
        val safeName = (displayName?.substringBeforeLast(".")?.ifBlank { null } ?: "vpn")
            .replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        val file = File(dir, "$safeName-${UUID.randomUUID().toString().take(8)}.conf")
        // Simpan apa adanya; IncludedApplications disuntik saat connect
        file.writeText(text.trim() + "\n")
        return VpnProfile(name = safeName, fileName = file.name)
    }

    fun readRaw(profile: VpnProfile): String =
        File(dir, profile.fileName).readText()

    fun delete(profile: VpnProfile): Boolean =
        File(dir, profile.fileName).delete()

    companion object {
        /**
         * Suntik IncludedApplications ke section [Interface] agar tunnel
         * HANYA me-routing traffic aplikasi ini (mekanisme resmi
         * wireguard-android, dipakai juga oleh aplikasi WireGuard resmi
         * untuk fitur "per-app VPN").
         */
        fun injectIncludedApp(rawConf: String, packageName: String): String {
            val lines = rawConf.lines().toMutableList()
            val idx = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
            if (idx == -1) return rawConf
            // Hapus baris IncludedApplications lama bila ada (hindari duplikat)
            var i = idx + 1
            while (i < lines.size && !lines[i].trim().startsWith("[")) {
                if (lines[i].trim().startsWith("IncludedApplications", ignoreCase = true)) {
                    lines.removeAt(i); continue
                }
                i++
            }
            lines.add(idx + 1, "IncludedApplications = $packageName")
            return lines.joinToString("\n")
        }
    }
}
