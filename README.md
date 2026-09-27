# TRXQ — Aplikasi Catatan Keuangan

Proyek Android untuk mencatat pemasukan/pengeluaran, melihat ringkasan dan grafik, serta ekspor/impor cadangan JSON.

## Membuat APK lewat GitHub Actions
1. Buat repository GitHub baru bernama `TRXQ`.
2. Unggah **isi folder ZIP ini** ke root repository (bukan file ZIP-nya saja).
3. Buka tab **Actions**, pilih workflow **Build APK TRXQ**, lalu jalankan **Run workflow** jika belum berjalan otomatis.
4. Setelah workflow selesai, buka hasil run dan unduh artifact `TRXQ-debug-apk`.
5. Ekstrak artifact dan pasang `app-debug.apk` di HP.

## Membuat APK di Android Studio
Buka folder proyek ini di Android Studio, tunggu Gradle Sync, lalu pilih Build > Build APK(s).

## Identitas
- Nama aplikasi: TRXQ
- Application ID: `com.trxq.finance`
- Minimum Android: Android 7.0 (API 24)

Catatan: source ini disiapkan untuk build; hasil APK belum diuji pada perangkat dalam sesi ini.
