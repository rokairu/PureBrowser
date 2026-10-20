package com.purebrowser.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Warna profil (gaya pemilih profil Chrome). */
val PALETTE = listOf(
    0xFF8AB4F8.toInt(), 0xFF81C995.toInt(), 0xFFF6AEA9.toInt(), 0xFFFDD663.toInt(),
    0xFFC58AF9.toInt(), 0xFF78D9EC.toInt(), 0xFFFFB287.toInt(), 0xFF9AA0A6.toInt(),
)

data class Profile(val id: String, val name: String, val color: Int)

/**
 * Daftar profil (profiles.json) + folder data tiap profil
 * (filesDir/profiles/<id>/ berisi history, bookmark, tab).
 * Format profiles.json kompatibel dengan versi lama.
 */
class ProfileStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "profiles.json")
    private val prefs = ctx.getSharedPreferences("purebrowser", Context.MODE_PRIVATE)

    var active: String?
        get() = prefs.getString("active_profile", null)
        set(v) { prefs.edit().putString("active_profile", v).apply() }

    fun list(): List<Profile> = try {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Profile(o.getString("id"), o.getString("name"), o.getInt("colorArgb"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    /** Pastikan minimal ada satu profil. */
    fun ensure(): List<Profile> {
        val now = list()
        if (now.isNotEmpty()) return now
        val def = Profile(UUID.randomUUID().toString(), "Pribadi", PALETTE[0])
        save(listOf(def))
        return listOf(def)
    }

    fun add(name: String, color: Int): Profile {
        val p = Profile(UUID.randomUUID().toString(), name.trim().ifEmpty { "Profil" }, color)
        save(list() + p)
        return p
    }

    fun update(id: String, name: String, color: Int) {
        save(list().map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }, color = color) else it })
    }

    /** Hapus profil + datanya. Profil aktif dan profil terakhir tidak bisa dihapus. */
    fun delete(id: String): Boolean {
        val all = list()
        if (all.size <= 1 || id == active || all.none { it.id == id }) return false
        save(all.filter { it.id != id })
        File(ctx.filesDir, "profiles/$id").deleteRecursively()
        return true
    }

    fun dir(id: String): File {
        val d = File(ctx.filesDir, "profiles/$id").apply { mkdirs() }
        migrateLegacy(d)
        return d
    }

    /** Versi lama menyimpan data di profiles/<id>/files/. Pindahkan lalu bersihkan sisa profil Gecko lama. */
    private fun migrateLegacy(d: File) {
        val legacy = File(d, "files")
        if (!legacy.isDirectory) return
        var ok = true
        for (n in listOf("history.json", "bookmarks.json")) {
            val old = File(legacy, n)
            val dst = File(d, n)
            if (old.exists() && !dst.exists()) ok = ok && runCatching { old.copyTo(dst) }.isSuccess
        }
        if (ok) listOf("files", "cache", "code_cache", "no_backup").forEach { File(d, it).deleteRecursively() }
    }

    private fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("colorArgb", it.color)) }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}

data class Entry(val url: String, val title: String, val time: Long)

/**
 * Daftar URL (dipakai untuk riwayat dan bookmark). Disimpan di memori sebagai
 * state Compose (UI otomatis update), ditulis ke disk di thread terpisah.
 */
class EntryStore(private val file: File, private val max: Int) {
    val items = mutableStateListOf<Entry>()

    init {
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Entry(o.getString("url"), o.optString("title", ""), o.optLong("time", 0L)))
            }
        } catch (_: Exception) { }
    }

    fun contains(url: String): Boolean = items.any { it.url == url }

    /** Catat kunjungan: pindahkan ke paling atas, tanpa duplikat. */
    fun visit(url: String) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return
        if (items.firstOrNull()?.url == url) return
        val old = items.firstOrNull { it.url == url }
        items.removeAll { it.url == url }
        items.add(0, Entry(url, old?.title.orEmpty(), System.currentTimeMillis()))
        while (items.size > max) items.removeAt(items.lastIndex)
        save()
    }

    fun setTitle(url: String, title: String) {
        val i = items.indexOfFirst { it.url == url }
        if (i >= 0 && items[i].title != title) {
            items[i] = items[i].copy(title = title)
            save()
        }
    }

    /** @return true jika kini tersimpan, false jika dihapus. */
    fun toggle(url: String, title: String): Boolean {
        val i = items.indexOfFirst { it.url == url }
        return if (i >= 0) {
            items.removeAt(i); save(); false
        } else {
            items.add(0, Entry(url, title.ifBlank { url }, System.currentTimeMillis())); save(); true
        }
    }

    fun remove(url: String) {
        items.removeAll { it.url == url }
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() {
        val snapshot = items.toList()
        io.execute {
            try {
                val arr = JSONArray()
                snapshot.forEach {
                    arr.put(JSONObject().put("url", it.url).put("title", it.title).put("time", it.time))
                }
                file.parentFile?.mkdirs()
                val tmp = File(file.path + ".tmp")
                tmp.writeText(arr.toString())
                tmp.renameTo(file)
            } catch (_: Exception) { }
        }
    }

    private companion object {
        val io = Executors.newSingleThreadExecutor()
    }
}
