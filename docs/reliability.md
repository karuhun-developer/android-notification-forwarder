# Laporan reliabilitas listener

Tanggal: 14 Oktober 2026. Baseline perubahan: `master` pada `2ca77b4c6595f47c4899e6c724af65bfc6f903ba`. Laporan ini mencatat validasi lokal sebelum tag serta hasil GitHub Actions untuk `v1.2.1`; build/test di bawah bukan hasil pembayaran nyata.

## Hasil eksekusi lokal sebelum tag

| Pemeriksaan | Hasil aktual |
| --- | --- |
| `assembleDebug` dan `assembleRelease` | Lulus; kedua APK dibuat |
| `lintDebug` | Lulus, 0 error dan 37 warning |
| EventIdentity 2, RetryPolicy 3, NotificationPusher 4, Example 1 pada JVM ARM native | 10 tes lulus |
| DatabaseMigration 2 dan DeliveryScheduler 2 pada JVM x86 melalui qemu | 4 tes lulus; juga lulus pada full run |
| Full suite JVM x86/qemu | 24 dijalankan, 13 gagal, 11 lulus |
| Penyebab 13 kegagalan full run | Seluruh failure XML menunjukkan `MockWebServer.shutdown`: `Gave up waiting for queue to shut down` |
| Workflow GitHub | actionlint 1.7.12 dan syntax 6 shell steps lulus; smoke lokal tag/assets/publish lulus |
| Android nyata / visual QA / audio / payment | Belum dijalankan; tidak ada perangkat atau emulator aktif |

**Full suite lokal melalui emulasi belum lulus.** Sepuluh tes integrasi Room + HTTP gagal pada teardown server, tiga tes HTTP lainnya gagal di shutdown yang sama. Failure XML sesudah fix tidak melaporkan assertion aplikasi; ini merupakan bukti parsial dan tetap tidak dihitung sebagai test pass. Test dan teardown tidak dihapus, dilemahkan, atau ditelan error-nya.

Host Linux aarch64 menggunakan JDK 21.0.12.1. Robolectric/native dependencies untuk pengujian Room di host ini memerlukan fallback JVM x86. Full run memakai JDK 17.0.20.1 lewat qemu 10.2.1 dengan `jdk.net.usePlainSocketImpl=true`; probe `ServerSocket` yang terpisah dari aplikasi juga tidak dapat menghentikan accept thread saat socket ditutup dalam emulasi. Akar masalah emulasi belum dipastikan. Hasil runner Linux x86 native dicatat berikutnya; workflow tetap menolak publishing bila suite gagal.

## Hasil GitHub Actions dan release v1.2.1

[Run 38521741596](https://github.com/karuhun-developer/android-notification-forwarder/actions/runs/38521741596) dipicu otomatis oleh push tag `v1.2.1`, checkout `13412e0c340be56319f417ba64f3756e2b0afb41`, pada Ubuntu 24.04 x86 dengan JDK 21. Build dan publish keduanya lulus.

| Pemeriksaan hosted | Hasil aktual |
| --- | --- |
| Full `testDebugUnitTest` | 24 tes lulus, 0 failure, 0 error, 0 skip dari tujuh XML suite |
| DeliveryIntegration / DatabaseMigration / DeliveryScheduler | 10 / 2 / 2 tes lulus |
| NotificationPusher / EventIdentity / RetryPolicy / Example | 4 / 2 / 3 / 1 tes lulus |
| `lintDebug` | 0 error, 37 warning |
| `assembleDebug` dan `assembleRelease` | Kedua APK berhasil dibuat |
| Publish | Release stabil, bukan draft; debug APK, unsigned APK, SHA256SUMS.txt tersedia |
| Assets yang diunduh | Kedua checksum cocok; debug signature terverifikasi; versionName `1.2.1`, versionCode `3` |

[Release v1.2.1](https://github.com/karuhun-developer/android-notification-forwarder/releases/tag/v1.2.1) terbit pada 14 Oktober 2026. XML tes dan laporan lint tersedia sebagai artifact `android-validation-v1.2.1` selama retention workflow. Hosted pass tidak mengubah hasil lokal emulasi atau menggantikan QA perangkat.

Percobaan manual `v1.2.0` pada run `38520035766` gagal sebelum tes karena `sdkmanager` tidak tersedia; command-line tools kemudian disiapkan eksplisit. Run `38520275689` menjalankan 24 tes dengan satu failure: retry kedua setelah respons hilang masih `RETRYING`. Repro HTTP dengan DNS localhost `[127.0.0.1, ::1]` dan server IPv4 menunjukkan percobaan kedua menuju IPv6 yang tidak mendengar; URL IPv4 eksplisit menghasilkan HTTP 200 dengan dua request. Fixture integrasi kini mengikat server dan URL ke `127.0.0.1`. Seluruh assertion, jumlah request, payload/identity, retry policy, dan teardown dipertahankan. Tag `v1.2.0` tidak diubah dan publishing-nya tertahan.

## Perubahan yang diuji

- [DeliveryRepository](../app/src/main/java/com/karuhundeveloper/notification/delivery/DeliveryRepository.kt) commit snapshot Room sebelum scheduling; unique event/rule menjaga replay.
- [DeliveryEngine](../app/src/main/java/com/karuhundeveloper/notification/delivery/DeliveryEngine.kt) claim lease atomik, pemeriksaan token sebelum HTTP, completion yang dibatasi token, dan recovery outcome unknown.
- [DeliveryScheduler](../app/src/main/java/com/karuhundeveloper/notification/delivery/DeliveryScheduler.kt) WorkManager KEEP, manual retry melalui FAILED-to-QUEUED CAS sebelum REPLACE, serta recovery capture yang sudah commit tanpa work.
- [NotificationPusher](../app/src/main/java/com/karuhundeveloper/notification/network/NotificationPusher.kt) mempertahankan kontrak body/query, header identitas, konfigurasi error yang dapat diperiksa, serta satu request per attempt pada HTTP 503 `Retry-After: 0`.
- [AppDatabase](../app/src/main/java/com/karuhundeveloper/notification/data/local/AppDatabase.kt) migrasi v1/v2 ke v3 tanpa destructive fallback.
- Listener catch-up, UI history/switch/retry, backup exclusions, dan guard permission dummy disertakan dalam APK. Lifecycle dan interaksi UI membutuhkan pengujian perangkat.

## Regression sebelum dan sesudah fix

| Regression | Sebelum | Sesudah |
| --- | --- | --- |
| URL malformed | `IllegalArgumentException` keluar dari pusher | `ConfigurationError`; tes native lulus |
| Stale manual retry | Work yang sudah QUEUED berubah CANCELLED | Work ID/state tetap ENQUEUED; scheduler test lulus |
| Delete rule setelah claim sebelum fetch | Expected 0 HTTP requests, terkirim 1 | Tidak ada assertion requestCount gagal; integrasi tetap gagal teardown emulasi |
| HTTP 503 `Retry-After: 0` | Pusher tidak mengembalikan 503 karena follow-up OkHttp | POST/GET mengembalikan 503 dengan satu request per push; tes native lulus |
| Dummy tanpa direct permission check | Lint `MissingPermission` error | Guard permission ditambahkan; lint 0 error |

Tidak ada incident produksi yang direproduksi. Ini hardening preventif dengan regression untuk mekanisme kegagalan yang ditemukan.

## Cakupan suite

[DeliveryIntegrationTest](../app/src/test/java/com/karuhundeveloper/notification/delivery/DeliveryIntegrationTest.kt) memakai database Room yang nyata untuk reopen/dedup, request diterima dengan response hilang, retry opt-in/manual, permanent HTTP, invalid config, claim paralel/stale completion, expired lease/budget, penghapusan rule/history, dan cancellation setelah claim. Kesepuluh tes integrasi lulus pada runner hosted native x86 untuk `v1.2.1`; hasil emulasi lokal tetap tercatat terpisah.

[DeliverySchedulerTest](../app/src/test/java/com/karuhundeveloper/notification/delivery/DeliverySchedulerTest.kt) menggunakan WorkManager test initialization dan Room untuk stale retry serta recovery gap commit-to-schedule. Ini tidak mensimulasikan Doze/OEM, reboot, atau real network constraint transition.

[DatabaseMigrationTest](../app/src/test/java/com/karuhundeveloper/notification/delivery/DatabaseMigrationTest.kt) memuat schema v1/v2, mempertahankan rule/history, dan memeriksa retry default mati pada v3. [NotificationPusherTest](../app/src/test/java/com/karuhundeveloper/notification/network/NotificationPusherTest.kt) memakai socket HTTP lokal untuk compatibility, redirect, malformed URL, dan 503 follow-up.

## Perintah yang dijalankan

Perintah standar pada Linux x86 dengan JDK 21 dan SDK sesuai repo:

```bash
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

Full run lokal dengan fallback emulasi, menghasilkan 24 tests / 13 failed:

```bash
ANDROID_HOME=/tmp/qris-listener-tools/sdk bash gradlew :app:testDebugUnitTest \
  -I /tmp/qris-listener-tools/x64-test.init.gradle \
  -Pandroid.aapt2FromMavenOverride=/tmp/qris-listener-tools/aapt2 \
  --no-configuration-cache --console=plain
```

Run terpisah `--tests '*DeliverySchedulerTest.*' --tests '*DatabaseMigrationTest.*'` dengan fallback yang sama menghasilkan 4 pass dan `BUILD SUCCESSFUL in 51s`.

Run native dengan build/lint, menghasilkan 10 pass dan `BUILD SUCCESSFUL in 1m 9s`:

```bash
ANDROID_HOME=/tmp/qris-listener-tools/sdk bash gradlew :app:lintDebug :app:testDebugUnitTest \
  --tests '*NotificationPusherTest.*' --tests '*EventIdentityTest.*' \
  --tests '*RetryPolicyTest.*' --tests '*ExampleUnitTest.*' \
  :app:assembleDebug :app:assembleRelease \
  -PreleaseVersionName=1.0.0-ci-check -PreleaseVersionCode=14 \
  -Pandroid.aapt2FromMavenOverride=/tmp/qris-listener-tools/aapt2 \
  --no-configuration-cache --console=plain
```

Tooling SDK/qemu/JVM fallback terisolasi di `/tmp/qris-listener-tools`; tidak mengubah system packages dan bukan dependency produksi. Path sementara dapat hilang saat environment restart.

## Artifacts lokal

- [APK debug](../app/build/outputs/apk/debug/app-debug.apk): certificate `CN=Android Debug`, verifikasi apksigner berhasil.
- [APK release unsigned](../app/build/outputs/apk/release/app-release-unsigned.apk): apksigner menolak karena belum ditandatangani, sesuai output yang dimaksud.
- Metadata kedua APK: versionName `1.0.0-ci-check`, versionCode `14`, membuktikan properties versi CI masuk packaging.
- [Report native 10 pass](../app/build/reports/tests/testDebugUnitTest/index.html).
- [Snapshot full run x86](../app/build/reports/validation/x86-full/testDebugUnitTest/index.html): 24 / 13 failures; XML disimpan di directory yang sama.
- [Lint report](../app/build/reports/lint-results-debug.html): 0 error / 37 warnings; Navigation custom lint checks lama tidak berjalan, version/dependency warnings, catalog suggestions, SDK check, dan SharedPreferences KTX suggestion tetap terlihat. Tidak ada baseline/suppression baru.

Artifacts di `app/build` merupakan hasil lokal yang diabaikan git dan dapat tertimpa build berikutnya. Release native library strip dan Compose API deprecation warnings juga masih tercatat pada build.

## Validasi workflow

[Workflow](../.github/workflows/android-release.yml) lolos actionlint dan `bash -n` untuk enam step shell. Tag `v1.2.3` dan `v1.2.3-beta.1` diterima; prefix salah, versi tidak lengkap, slash/path, dan string command substitution ditolak. Script packaging memakai APK yang benar-benar dibangun dan kedua checksum lolos pemeriksaan.

Script publish dijalankan lokal dengan fungsi `gh` pengganti untuk mencatat argumen. Fresh release, rerun, serta prerelease menghasilkan urutan/flags yang diharapkan. Upload gagal dengan code 17 menghentikan script sebelum publish draft. Smoke ini menguji kontrol alur; tidak mengakses GitHub Release API. Build dan publishing hosted kemudian lulus pada run `38521741596` untuk `v1.2.1`, termasuk verifikasi checksum dan release yang bukan draft. Lihat [panduan CI](ci-release.md).

## Gate perangkat dan soundbox berikutnya

Belum diuji: notification access grant/revoke, reconnect/catch-up, notifikasi terhapus selama listener mati, offline-to-online constraint, process kill sebelum/sesudah commit, reboot kemudian unlock, force-stop lalu buka app, Doze/autostart/OEM target, retention/ukuran queue, UI retry/history/switch dengan font besar dan aksesibilitas.

Belum dibuat atau diuji: backend idempotency, parser merchant, payment gateway/settlement, dashboard/browser TTS, lease audio, ESP32, serta pembayaran nyata. Capture timestamp bukan waktu pembayaran; capture lengkap dan suara tepat satu kali tidak dijanjikan. Auto-retry tetap opt-in dan perlu receiver idempoten.
