# Notification Interceptor / Android Notification Forwarder

Aplikasi Android generik yang meneruskan notifikasi sesuai package/rule ke endpoint HTTP. Versi kerja ini menambahkan antrean Room, WorkManager, identitas event/delivery, retry opt-in, dan riwayat delivery untuk kebutuhan seperti QRIS soundbox.

Listener meneruskan isi notifikasi; konfirmasi pembayaran, parser merchant, dashboard, dan audio dikerjakan oleh receiver terpisah. Rancangan soundbox tersedia pada workspace lokal terpisah di `../qris-soundbox/readme.md`.

## Fitur yang sudah dibuat

- Routing berdasarkan package aplikasi, endpoint, method, dan custom headers.
- Snapshot delivery disimpan sebelum HTTP: payload, endpoint, headers, dan setting retry tetap untuk delivery tersebut.
- WorkManager dengan network constraint, backoff, recovery saat app dibuka/listener connect, dan recovery periodik.
- Identitas tetap untuk replay/retry, claim atomik, lease, dan completion yang memeriksa token pemilik.
- Retry otomatis default mati; dapat diaktifkan per rule untuk notifikasi berikutnya.
- History menampilkan state, attempts, HTTP/error, serta retry manual pada FAILED. History lama ditampilkan terpisah.
- Catch-up notifikasi yang masih aktif saat reconnect serta request rebind.
- Migrasi Room v1/v2 ke v3 mempertahankan rule/history; tidak memakai destructive fallback.
- [GitHub Actions](.github/workflows/android-release.yml) untuk tes, lint, build, dan GitHub Release ketika tag versi dipush.

## Build dan install

Gunakan JDK 21, Android SDK Platform 36.1, Build Tools 36.0.0, serta Gradle wrapper repo. Compile SDK 36.1, target SDK 36, minimum Android API 24. Target bytecode Java 11 tidak berarti build cukup memakai JDK 11. Validasi lokal menggunakan JDK 21; hasil dan batas environment ada di [laporan reliabilitas](docs/reliability.md).

```bash
git clone https://github.com/karuhun-developer/android-notification-forwarder.git
cd android-notification-forwarder
bash gradlew :app:assembleDebug
bash gradlew :app:installDebug
```

Set `ANDROID_HOME` ke SDK lokal atau konfigurasi `sdk.dir` di `local.properties`. APK debug ada di `app/build/outputs/apk/debug/app-debug.apk`. `assembleRelease` menghasilkan APK unsigned karena keystore produksi belum dikonfigurasi.

## Setup listener dan rule

1. Buka aplikasi dan beri **Notification access** dari Settings. Akses yang diberikan dan status service connected adalah kondisi berbeda; periksa indikator aplikasi.
2. Pilih package aplikasi yang menerbitkan notifikasi penerimaan merchant. Jangan memilih aplikasi pembayar hanya karena wallet tersebut dipakai pelanggan.
3. Buat rule dengan URL receiver, method, dan custom headers. Soundbox dirancang memakai POST HTTPS dengan token ingestion.
4. Untuk dummy, tambahkan package `com.karuhundeveloper.notification`. Pada Android 13+ beri izin POST_NOTIFICATIONS lalu tekan **Send Dummy**. Izin ini untuk dummy, bukan pengganti Notification access.
5. Buka History rule untuk memeriksa delivery dan respons endpoint. HTTP 2xx hanya menyatakan endpoint menerima request.

Contoh custom headers untuk receiver yang menerima token:

```json
{ "Authorization": "Bearer <device-ingestion-token>" }
```

Endpoint `/api/v1/notifications` dalam dokumen soundbox masih rancangan; tidak disediakan oleh repo Android ini. Belum ada parser merchant DANA/OVO/ShopeePay yang terverifikasi.

## Kontrak request

POST, PUT, dan PATCH memakai body JSON dengan empat field lama:

```json
{
  "app_name": "Merchant App",
  "notification_title": "<judul asli>",
  "notification_text": "<teks asli>",
  "timestamp_ms": 1791939600000
}
```

GET mengirim field yang sama sebagai query parameters; DELETE tidak mengirim body. Custom headers tetap dilampirkan. Delivery dari antrean menambahkan:

| Header | Nilai |
| --- | --- |
| `X-Notification-Event-Id` | SHA-256 installation UUID, notification key, post time, title, text |
| `X-Delivery-Id` | UUID satu delivery ke satu rule |
| `X-Notification-Package` | Package sumber Android |

Header internal dipasang setelah custom headers agar tidak tertimpa. Unique event + rule mencegah replay notifikasi identik membuat delivery lain. Perubahan judul/teks/post time dapat membentuk event baru; event ID ini bukan referensi transaksi provider.

`timestamp_ms` adalah waktu capture pertama yang tersimpan untuk delivery. Android post time disimpan lokal tetapi tidak dikirim. Notifikasi lama yang masih aktif saat reconnect dapat memiliki capture timestamp baru; receiver tidak boleh menganggap timestamp ini bukti waktu terjadinya pembayaran.

## Antrean dan retry

| State | Arti |
| --- | --- |
| QUEUED | Sudah tersimpan, menunggu work/jaringan |
| SENDING | Sedang dimiliki worker melalui lease |
| RETRYING | Menunggu attempt berikutnya |
| DELIVERED | Endpoint mengembalikan HTTP 2xx |
| FAILED | Memerlukan pemeriksaan/manual retry |
| CANCELLED | Rule dihapus; delivery dihentikan |

Auto-retry **default mati**, termasuk rule hasil migrasi. Jika diaktifkan, network error dan HTTP 408/429/5xx dapat dicoba ulang sampai 10 attempts per siklus. Error konfigurasi, redirect, dan permanent 4xx berhenti. Backoff WorkManager eksponensial mulai 30 detik; scheduler Android dapat menundanya.

Client bawaan mematikan redirect serta retry koneksi implisit dan mencegah follow-up otomatis HTTP 503 dengan `Retry-After: 0`. Setiap attempt ditangani kebijakan delivery. Lease 120 detik dan total call timeout 45 detik. Client custom yang diinject perlu mempertahankan kebijakan yang sama.

Request yang terputus bisa sudah diterima receiver. Aktifkan retry hanya setelah receiver menyimpan event sebelum ACK serta mendeduplikasi identitas delivery. Retry manual mengantrekan ulang FAILED dengan ID/body tetap, mereset attempts, dan mempertahankan endpoint/settings snapshot. Klik stale tidak membatalkan work yang sudah antre.

Jika worker terputus saat SENDING, lease expired dipulihkan sebagai retry hanya bila opt-in dan budget tersedia. Selain itu hasil menjadi FAILED dengan outcome unknown. Periksa receiver sebelum retry manual. Menghapus rule menghentikan antrean tetapi tidak menarik request yang sudah diterima server.

## Data dan batas pemulihan

Antrean mulai tahan restart setelah commit Room berhasil. Process death sebelum commit, notifikasi yang tidak pernah terbit, notifikasi yang hilang saat listener mati, force-stop, dan pembatasan OEM tetap bisa menyebabkan kehilangan capture. Catch-up hanya membaca notifikasi yang masih outstanding, bukan riwayat transaksi.

Listener tidak berjalan sebelum unlock setelah reboot. WorkManager recovery periodik minimum 15 menit dan jadwalnya tidak exact. Belum ada QA perangkat untuk reboot, reconnect, Doze/OEM, maupun visual/font besar. Pengiriman maupun bunyi tepat satu kali tidak dijanjikan.

Clear history hanya menghapus history lama. Delivery dipertahankan untuk dedup; purge aman belum dibuat. Database dan installation identity dikecualikan dari backup/cloud/device transfer. Rules/history/queue tidak dipulihkan lewat backup; reinstall atau clear data menghilangkannya. Headers/token disimpan dalam database app lokal.

## Tes dan release

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Suite menggunakan Room, Robolectric, WorkManager test API, dan MockWebServer. Full suite pada GitHub Actions untuk `v1.2.1` lulus: 24 tes, tanpa failure/error/skip; lint 0 error dan 37 warning. [Laporan reliabilitas](docs/reliability.md) mencatat bukti hosted dan hasil lokal dengan keterbatasan emulasi host ARM.

Tag seperti `v1.0.0` atau `v1.1.0-beta.1` memicu workflow. Build harus lulus tes/lint sebelum APK dan checksum dipublikasikan. Detail trigger, artifacts, signing, dan cara menjalankan ada di [panduan CI/release](docs/ci-release.md).

[Release v1.2.1](https://github.com/karuhun-developer/android-notification-forwarder/releases/tag/v1.2.1) menyediakan APK debug untuk pengujian, APK release unsigned, dan checksum SHA-256. Push tag ini berhasil memicu build dan publish otomatis.

## License

```
MIT License — Copyright (c) 2026 karuhundeveloper

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is furnished
to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```
