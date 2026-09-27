# Catatan Keuangan — UI Update

Pembaruan:
- Grafik batang pemasukan dan pengeluaran untuk 6 bulan.
- Transaksi menampilkan ikon kategori, bukan nama kategori.
- Target Java dan Kotlin disamakan ke JVM 17 untuk menghindari error `Inconsistent JVM-target compatibility`.

## Membuat APK
1. Ekstrak ZIP.
2. Buka folder proyek di Android Studio.
3. Tunggu Gradle Sync selesai.
4. Pilih **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK debug biasanya ada di `app/build/outputs/apk/debug/app-debug.apk`.

Catatan: ZIP ini adalah kode sumber. APK perlu dibangun oleh Android Studio atau workflow CI. Build belum diverifikasi di perangkat ini.
