package com.purebrowser.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.purebrowser.app.MainActivity
import com.purebrowser.app.R

/**
 * Notifikasi status VPN buatan aplikasi sendiri (terpisah dari notifikasi
 * foreground service bawaan library WireGuard). Muncul selama tunnel UP:
 * "VPN aktif — <profil browser>" + nama config; hilang saat tunnel DOWN.
 * Di Android 13+ notifikasi hanya tampil bila user mengizinkan
 * POST_NOTIFICATIONS (diminta sekali saat aplikasi pertama dibuka).
 */
object VpnNotifier {
    private const val CHANNEL_ID = "vpn_status"
    private const val NOTIF_ID = 4201

    fun show(ctx: Context, profileName: String, configName: String) {
        val appCtx = ctx.applicationContext
        val nm = appCtx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Status VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val intent = Intent(appCtx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            appCtx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(appCtx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VPN aktif — $profileName")
            .setContentText("Terhubung ke $configName • hanya traffic PureBrowser")
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            nm.notify(NOTIF_ID, notif)
        } catch (_: SecurityException) {
            // Izin notifikasi belum diberikan: diam saja (ikon kunci sistem tetap tampil).
        }
    }

    fun clear(ctx: Context) {
        try {
            ctx.applicationContext.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        } catch (_: Exception) { }
    }
}
