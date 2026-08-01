# CLAUDE.md — Aturan ChatFin

Kontrak kerja tiap sesi. Mengalahkan kebiasaan default. Kalau task mustahil tanpa
melanggar aturan di sini → berhenti, diskusikan, jangan langgar diam-diam.

## 1. Project

Android keuangan personal offline-first, asisten AI "Sakurajima Mai". Bahasa: Indonesia. Mata uang: IDR.

**Persona A (pemula):** tidak paham istilah keuangan, gampang menyerah kalau lihat jargon/error teknis. Alur utama (catat → lihat saldo) harus jalan tanpa dokumentasi, tanpa jargon, tanpa pesan error teknis.
**Persona B (melek finansial):** butuh akurasi angka, export rapi, budget per kategori, tren. Saldo dompet wajib selalu cocok dengan daftar transaksi.
→ Dilayani lewat progressive disclosure (Bagian 5.5).

**Stack:** Kotlin, Jetpack Compose + Material 3, MVVM (UI→ViewModel→Repository→DAO), Room (`chatfin_database`), Hilt, Coroutines+Flow, DataStore (non-sensitif) / EncryptedSharedPreferences (sensitif), Firebase Auth+Firestore (opsional), Gemini (opsional), Vico (grafik).

Package-by-feature: `feature/<domain>/{ui,data}/`, `core/{data,di,ui}/`, `ai/`.
Dependensi satu arah: Composable tidak boleh sentuh DAO/Repository langsung. ViewModel tidak boleh tahu Composable.

**PRINSIP UTAMA:** Aplikasi harus jalan penuh TANPA API key. AI = lapisan opsional, bukan fondasi.
- Transaksi, dompet, kategori, budget, analitik, export, backup wajib 100% offline.
- Insight Dashboard dihitung lokal (`DashboardViewModel.generateMaiInsight`) — jangan diganti API call.
- Fitur AI yang gagal → degradasi ke Mode Bot (`ai/BotModeHandler.kt`), bukan error buntu.
- Dilarang fitur baru yang hanya jalan dengan API key, kecuali fitur AI dengan fallback jelas.
- Perubahan yang menambah ketergantungan AI → tolak.

## 2. Aturan Kode (Tidak Bisa Ditawar)

1. **Dependency via version catalog.** Deklarasi di `gradle/libs.versions.toml`, pakai `libs.xxx`. Dilarang hardcode versi di `build.gradle.kts`. Cek dulu apakah dependency serupa sudah ada sebelum menambah baru.
2. **Uang = `Long` rupiah utuh.** Tidak pernah `Double`/`Float`/`BigDecimal` untuk nilai yang disimpan/dijumlah/dibandingkan. Float/Double hanya untuk presentasi non-akumulatif (progress bar %, sudut chart).
3. **Tidak ada Context di ViewModel.** Tidak boleh terima/simpan `Context`, `Activity`, `Fragment`, `View`, `Resources`. Repository pakai `@ApplicationContext` (Hilt), bukan Activity Context. String ke UI lewat resource ID/sealed class, resolve via `stringResource()` di Composable.
4. **Flow di UI pakai `collectAsStateWithLifecycle()`**, bukan `collectAsState()`. Collect di ViewModel selalu di `viewModelScope` — dilarang `GlobalScope` atau `CoroutineScope(...)` manual tanpa pemilik/pembatalan. Fungsi collect yang bisa dipanggil berkali-kali (ganti akun/tab/bulan) wajib membatalkan collector lama — pakai `flatMapLatest` atau simpan `Job` dan `cancel()` sebelum `launch` baru. Pernah menyebabkan kategori berkedip & angka budget berubah sendiri — bukan risiko teoretis.
5. **Semua I/O di `Dispatchers.IO`** (file I/O, serialisasi dataset besar, render PDF/Canvas, EncryptedSharedPreferences/Keystore, network non-suspend-safe). `viewModelScope` = `Main.immediate`; hanya DAO Room suspend/Flow yang otomatis pindah thread. Cek: operasi >16ms → pastikan bukan di Main.
6. **String user-facing wajib di `strings.xml`** (label, judul, placeholder, error, sukses, empty state, tombol, content description). Kode baru: wajib tanpa pengecualian. Kode lama: migrasikan kalau kebetulan disentuh. Kecuali: string non-user-facing (log tag, JSON key, kolom SQL, konstanta protokol).
7. **Perubahan schema Room wajib migration.** Dilarang `.fallbackToDestructiveMigration()` di release. Prosedur: naikkan `version` → tulis `Migration(n, n+1)` → daftarkan di `.addMigrations()` → commit schema JSON di `app/schemas/`. Migration tetap wajib walau isi schema identik (Room menolak buka DB tanpa migrasi terdaftar). Sebelum selesai: pastikan rantai migrasi 1→terbaru tanpa lubang.
8. **Tidak ada API key di source/BuildConfig** (tidak diobfuscate R8, gampang diambil dari APK). Satu-satunya sumber sah: input user → `SecureStorage` (EncryptedSharedPreferences). Dilarang commit `local.properties`/`google-services.json`. Dilarang log API key, respons AI, atau nilai finansial (`Log.*` ikut ke release build karena tidak di-strip proguard).
9. **Operasi uang atomik.** Tulis transaksi + mutasi saldo dompet wajib dalam satu `db.withTransaction {}`. Statement terpisah → saldo bisa permanen tidak cocok dengan transaksi (tidak ada rekonsiliasi di app ini).

## 3. Checklist Anti-Regresi (jawab sebelum menyatakan selesai)

**Coroutine:** scope lifecycle-aware? Collector lama dibatalkan kalau fungsi dipanggil ulang? Job field dibatalkan di `onCleared()`?
**Listener/resource:** listener/BroadcastReceiver/NetworkCallback/ContentObserver baru — di mana unregister-nya (`callbackFlow`+`awaitClose`)? Cursor/Stream/PdfDocument pakai `.use{}`/`close()` di finally?
**Exception:** ada operasi yang bisa `throw` tanpa try-catch (`LocalDate.parse`, `substring`, `List[idx]`, `toInt/toLong`, `JSONObject`, `!!`)? Data dari sumber tak terpercaya (Firestore sync, backup JSON, respons LLM) ditangani sebagai berpotensi rusak? Pesan error ke user bebas jargon/stack trace?
**Tanpa API key:** fitur tetap jalan kalau key kosong? Ada fallback (Mode Bot/lokal)? Bagian non-AI jadi bergantung AI? (tolak kalau ya)
**Data kosong/first launch:** aman untuk nol akun/dompet/kategori/transaksi/budget? `firstOrNull()` null tidak mematikan UI diam-diam? `remember`/`LaunchedEffect` key menyertakan data async (bukan cuma `Unit`)? Pembagian bisa div-by-zero?
**Migrasi:** entity berubah → version naik, migration ditulis+didaftarkan, schema JSON di-commit? Hapus data induk → data anak tertangani (tidak yatim)?

## 4. Komunikasi

- Task multi-file/ubah struktur data: jelaskan rencana (file, perubahan, alasan) dulu, tunggu konfirmasi. Perbaikan kecil/jelas: langsung kerjakan.
- Dua pendekatan dengan trade-off berbeda: sebutkan keduanya + rekomendasi dengan alasan. Jangan sembunyikan alternatif yang lebih baik karena lebih sulit.
- "Selesai" wajib disertai `file:line` yang berubah. Belum diverifikasi (belum di-build/run) → katakan eksplisit. Bagian gagal/tidak dikerjakan → sebutkan eksplisit.
- Permintaan yang melanggar Bagian 2, kontradiktif, berpotensi bug, atau mengorbankan "tanpa API key" → tolak + jelaskan. Kalau tetap diminta setelah dijelaskan → kerjakan, catat asumsi & risiko tertulis.
- Jangan melebarkan scope. Masalah lain di luar scope → laporkan, jangan sekalian diperbaiki.

## 5. UI/UX

- **Bahasa desain:** Material 3, `core/ui/theme/`, `Typography.kt`, palet ungu Mai. Dilarang redesign tanpa diminta — usulkan terpisah. Layar utama: `Scaffold`+`TopAppBar`; layar detail: tombol kembali.
- **3 state wajib tiap layar berdata:** Loading (indikator, bukan layar kosong berkedip) / Empty (mengedukasi, lihat bawah) / Error (pesan dimengerti Persona A + aksi pemulihan). Tidak boleh diam saat gagal.
- **Empty state:** formula "apa yang kosong → kenapa berguna → satu aksi konkret". Contoh: "Belum ada budget. Budget membantu batasi pengeluaran per kategori, misal maks Rp 1.000.000 untuk Makanan/bulan. [Buat Budget Pertama]" — bukan sekadar "Tidak ada data".
- **Format angka:** `NumberFormat.getNumberInstance(Locale("id","ID"))` + prefiks `Rp`, pemisah titik, tanpa desimal (`Rp 1.500.000`, `-Rp 250.000`). Singkatan (`1,5jt`) hanya di label grafik sempit, tidak pernah di saldo/total/konfirmasi. Pakai satu helper formatting bersama.
- **Progressive disclosure:** Lapis 1 (selalu terlihat: saldo, pemasukan/pengeluaran bulan ini, transaksi terbaru) → Lapis 2 (ringkas visual, tanpa jargon: grafik, donut, progress bar) → Lapis 3 (di balik tap, Persona B: rasio, perbandingan periode, breakdown lengkap). Maks 3 metrik numerik per kartu. Istilah teknis wajib penjelasan singkat saat pertama muncul. Default view = Lapis 1+2.
- **Jangan tampilkan fitur mati.** Toggle/tombol yang tidak berfungsi lebih merusak kepercayaan daripada fitur yang belum ada. Temukan di codebase → laporkan, usulkan implementasi atau sembunyikan.

## 6. Referensi Cepat

| Kebutuhan | File |
|---|---|
| DB & versi | `core/data/local/ChatFinDatabase.kt` |
| Migration | `core/di/DatabaseModule.kt` |
| Schema | `app/schemas/` |
| API key storage | `core/data/security/SecureStorage.kt` |
| Prompt Mai | `ai/SystemPromptBuilder.kt` |
| Fallback tanpa AI | `ai/BotModeHandler.kt` |
| Insight lokal | `feature/finance/dashboard/ui/DashboardViewModel.kt` |
| Pola collector benar | sama, cari `flatMapLatest` |
| Mutasi saldo dompet | `feature/finance/transaction/data/repository/TransactionRepository.kt` |
| Version catalog | `gradle/libs.versions.toml` |
| Tema | `core/ui/theme/` |

## 7. Ringkasan

Offline-first untuk dua persona: pemula dituntun, melek-finansial butuh akurasi. AI opsional di atas fondasi mandiri. Uang selalu `Long`. Dependency lewat version catalog. Schema selalu ada migration. I/O di `Dispatchers.IO`. Coroutine selalu punya pemilik & bisa dibatalkan. Tiap layar: loading/empty/error. Melanggar salah satu → tolak dan jelaskan, jangan tambal.
