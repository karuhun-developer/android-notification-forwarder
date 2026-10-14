# Build Android dari tag GitHub

Workflow: [android-release.yml](../.github/workflows/android-release.yml). Trigger push tag `v*`, dengan format yang diterima `v1.2.3` atau `v1.2.3-beta.1`. Workflow harus sudah ada pada commit yang ditag.

## Menjalankan

Simpan dan push perubahan repo Android terlebih dahulu. Dari commit yang ingin dirilis:

```bash
git tag -a v1.2.0 -m "Release v1.2.0"
git push origin v1.2.0
```

Gunakan nomor versi baru pada release berikutnya. Buka tab Actions untuk log dan artifacts; setelah job publish berhasil, unduh APK dari GitHub Releases. Tag dengan suffix seperti `-beta.1` menghasilkan prerelease.

Jika run otomatis belum muncul atau perlu membangun ulang tag existing, gunakan **Run workflow** pada tab Actions dengan input `release_tag`, atau:

```bash
gh workflow run android-release.yml --ref master -f release_tag=v1.2.0
```

Run manual mengambil kode dari tag tersebut, memakai versi dan nama assets tag, serta menjalankan checks yang sama. Workflow pada branch `master` harus sudah memuat opsi manual ini. Trigger otomatis push tag tetap aktif.

## Pipeline

1. Checkout commit tag, siapkan Temurin JDK 21, validasi Gradle wrapper, dan cache Gradle.
2. Pasang SDK Platform 36.1 serta Build Tools 36.0.0 pada runner Ubuntu 24.04 x86.
3. Jalankan `lintDebug`, seluruh `testDebugUnitTest`, `assembleDebug`, dan `assembleRelease`. Kegagalan tes/lint/build menghentikan publishing.
4. Kemas kedua APK, buat SHA-256, dan upload artifact build. Laporan tes/lint dicoba diupload juga ketika job gagal.
5. Job publish memeriksa checksum, membuat draft release untuk tag existing, mengupload assets, lalu mempublikasikannya. Rerun dapat mengganti assets release yang sama.

`versionName` berasal dari tag tanpa `v`; `versionCode` memakai `GITHUB_RUN_NUMBER`. Rerun workflow yang sama mempertahankan code. Build lokal tanpa properties mempertahankan default `1.0` / `1`. Properties `releaseVersionName` dan `releaseVersionCode` dapat dipakai untuk memeriksa packaging lokal.

## Assets dan signing

| Asset | Penggunaan |
| --- | --- |
| `notification-forwarder-v1.0.0-debug.apk` | Bisa di-install untuk pengujian; ditandatangani debug key runner |
| `notification-forwarder-v1.0.0-release-unsigned.apk` | Build release tanpa signature; perlu keystore sendiri sebelum install/distribusi |
| `SHA256SUMS.txt` | Pemeriksaan integritas APK |

Workflow ini tidak memerlukan keystore secret atau personal access token. Debug key runner tidak dijanjikan tetap antar-run; APK debug hasil tag berbeda dapat gagal update di atas instalasi dengan key berbeda. Uninstall menghilangkan rules/queue/identity app, jadi untuk update yang mempertahankan data gunakan signing key tetap saat distribusi resmi.

Automatic signing produksi belum ditambahkan. Simpan keystore/password di GitHub Secrets saat signing diimplementasikan; jangan commit keystore atau menjadikan debug APK sebagai release produksi.

## Permissions dan artifacts

Job build memakai `contents: read`, checkout tanpa persist credentials, dan action dikunci ke commit SHA. Job publish memakai `contents: write` serta `GITHUB_TOKEN`. Jika kebijakan organisasi membatasi token atau Releases, job publish gagal dengan error dari GitHub; sesuaikan kebijakan repository yang berlaku.

Artifact APK/checksum disimpan 30 hari; artifact laporan 14 hari. Assets yang berhasil dipublikasikan melekat pada GitHub Release. Workflow tidak melakukan deploy backend, install perangkat, upload Play Store, atau mengirim notifikasi pembayaran.

## Validasi yang tersedia

Workflow diperiksa dengan actionlint serta pemeriksaan shell/tag/publishing lokal. Build Android dan hasil suite lokal dicatat di [reliability.md](reliability.md). Eksekusi GitHub Actions dan publish remote pertama masih harus diamati setelah perubahan masuk repo dan tag dipush; pengecekan lokal tidak membuktikan job hosted sudah berhasil.
