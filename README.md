# PureBrowser

Browser Android berbasis GeckoView (Firefox) dengan profil, ekstensi, dan VPN WireGuard.

- Satu proses, satu activity (`MainActivity`).
- Profil dipisah dengan `contextId` Gecko (cookie/storage terpisah); riwayat, bookmark, dan tab tersimpan per profil di `files/profiles/<id>/`.
- Ekstensi: hanya yang bertanda tangan Mozilla (addons.mozilla.org); berlaku untuk semua profil.
- Build: push ke `main` → GitHub Actions membuat `app-debug.apk`.
